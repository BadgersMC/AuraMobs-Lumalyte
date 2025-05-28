package dev.aurelium.auramobs.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.kingdoms.constants.land.Land;
import org.kingdoms.constants.land.location.SimpleChunkLocation;
import org.kingdoms.constants.land.structures.Structure;
import org.kingdoms.constants.land.turrets.Turret;
import org.kingdoms.server.location.BlockVector3;

import dev.aurelium.auramobs.AuraMobs;

import java.util.*;
import java.util.concurrent.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MobAttractionManager implements Listener {

	private final AuraMobs plugin;
	private final int MAX_MOBS_PER_TICK = 5;
	private final int STRUCTURE_SEARCH_RADIUS = 48; // 3 chunks
	private final int CREEPER_EXPLOSION_RADIUS = 7;
	private final double STRUCTURE_MOVE_SPEED = 1.5;
	private final double NORMAL_MOVE_SPEED = 1.2;
	private final double LURE_ATTRACTION_RADIUS = 32.0;
	private final int LIGHT_LEVEL_THRESHOLD = 14; // Increased from 12 to 14
	private final int LIGHT_SEARCH_RADIUS = 16; // Reduced from 24 to 16
	private final long SCAN_INTERVAL_MS = 2000; // Reduced from 4000 to 2000 for more frequent updates
	private final long BATCH_INTERVAL_TICKS = 20L; // Process a batch every second instead of every 5 seconds

	// Cache for structure locations to reduce chunk loading
	private final Map<String, Set<Location>> structureCache = new ConcurrentHashMap<>();
	private final Map<UUID, Location> mobTargets = new ConcurrentHashMap<>();
	private final Map<UUID, Long> lastScan = new ConcurrentHashMap<>();
	private final Queue<LivingEntity> trackedMobs = new ConcurrentLinkedQueue<>();
	private final Map<UUID, Integer> mobPriorities = new ConcurrentHashMap<>(); // Track mob priorities

	private boolean debug = false; // Set via config

	public MobAttractionManager(AuraMobs plugin, boolean debug) {
		this.plugin = plugin;
		this.debug = debug;
		startBatchScanTask();
		startStructureCacheTask();
	}

	@EventHandler
	public void onEntitySpawn(EntitySpawnEvent event) {
		if (event == null || event.getEntity() == null) return;
		Entity entity = event.getEntity();
		
		// Register all hostile mobs
		if (entity instanceof Monster || 
			entity instanceof Slime || 
			entity instanceof Phantom || 
			entity instanceof Ghast || 
			entity instanceof Shulker) {
			registerMob((LivingEntity) entity);
		}
	}

	private void startStructureCacheTask() {
		// Update structure cache every 30 seconds
		Bukkit.getScheduler().runTaskTimer(plugin, this::updateStructureCache, 0L, 600L);
	}

	private void updateStructureCache() {
		structureCache.clear();
		for (World world : Bukkit.getWorlds()) {
			Set<Location> structures = new HashSet<>();
			int centerX = 0, centerZ = 0;
			int chunkRadius = STRUCTURE_SEARCH_RADIUS >> 4;

			for (int cx = centerX - chunkRadius; cx <= centerX + chunkRadius; cx++) {
				for (int cz = centerZ - chunkRadius; cz <= centerZ + chunkRadius; cz++) {
					if (!world.isChunkLoaded(cx, cz)) continue;
					SimpleChunkLocation chunk = new SimpleChunkLocation(world.getName(), cx, cz);
					Land land = chunk.getLand();
					if (land == null || !land.isClaimed()) continue;

					// Cache structures
					for (Map.Entry<BlockVector3, Structure> entry : land.getStructures().entrySet()) {
						if (entry == null || entry.getKey() == null) continue;
						BlockVector3 sLoc = entry.getKey();
						structures.add(new Location(world, sLoc.getX() + 0.5, sLoc.getY(), sLoc.getZ() + 0.5));
					}

					// Cache turrets
					for (Map.Entry<BlockVector3, Turret> entry : land.getTurrets().entrySet()) {
						if (entry == null || entry.getKey() == null) continue;
						BlockVector3 sLoc = entry.getKey();
						structures.add(new Location(world, sLoc.getX() + 0.5, sLoc.getY(), sLoc.getZ() + 0.5));
					}
				}
			}
			structureCache.put(world.getName(), structures);
		}
	}

	public void registerMob(LivingEntity mob) {
		if (mob == null || mob.isDead() || !mob.isValid()) return;
		// Register any living entity that isn't a player, villager, or other friendly mob
		if (!(mob instanceof Player) && 
			!(mob instanceof Villager) && 
			!(mob instanceof IronGolem) && 
			!(mob instanceof Snowman) && 
			!(mob instanceof Animals)) {
			trackedMobs.add(mob);
		}
	}

	private void startBatchScanTask() {
		// Process a batch every second
		Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			// Start async processing
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				List<LivingEntity> priorityMobs = new ArrayList<>();
				Iterator<LivingEntity> it = trackedMobs.iterator();
				
				// Collect and prioritize mobs async
				while (it.hasNext()) {
					LivingEntity mob = it.next();
					if (mob == null || mob.isDead() || !mob.isValid()) {
						it.remove();
						mobTargets.remove(mob == null ? null : mob.getUniqueId());
						mobPriorities.remove(mob == null ? null : mob.getUniqueId());
						continue;
					}
					
					// Update priority based on distance to nearest structure
					Location nearestStructure = findNearestStructure(mob.getLocation());
					if (nearestStructure != null) {
						double distance = mob.getLocation().distance(nearestStructure);
						// Higher priority for mobs closer to structures
						int priority = (int) (100 - (distance * 2));
						mobPriorities.put(mob.getUniqueId(), priority);
					}
					
					priorityMobs.add(mob);
				}
				
				// Sort by priority
				priorityMobs.sort((a, b) -> {
					int priorityA = mobPriorities.getOrDefault(a.getUniqueId(), 0);
					int priorityB = mobPriorities.getOrDefault(b.getUniqueId(), 0);
					return Integer.compare(priorityB, priorityA); // Higher priority first
				});
				
				// Process mobs in batches of MAX_MOBS_PER_TICK
				int totalMobs = priorityMobs.size();
				for (int i = 0; i < totalMobs; i += MAX_MOBS_PER_TICK) {
					final int start = i;
					final int end = Math.min(i + MAX_MOBS_PER_TICK, totalMobs);
					
					// Process each batch on the main thread
					Bukkit.getScheduler().runTask(plugin, () -> {
						int processed = 0;
						for (int j = start; j < end; j++) {
							LivingEntity mob = priorityMobs.get(j);
							if (shouldScan(mob)) {
								scanAndPathfindAsync(mob);
								processed++;
							}
						}
					});
					
					// Add a small delay between batches to prevent server lag
					try {
						Thread.sleep(50); // 50ms delay between batches
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			});
		}, 0L, BATCH_INTERVAL_TICKS);
	}

	private boolean shouldScan(LivingEntity mob) {
		if (mob == null) return false;
		long now = System.currentTimeMillis();
		return lastScan.getOrDefault(mob.getUniqueId(), 0L) + SCAN_INTERVAL_MS < now;
	}

	private void scanAndPathfindAsync(LivingEntity mob) {
		if (mob == null || mob.isDead() || !mob.isValid()) return;
		lastScan.put(mob.getUniqueId(), System.currentTimeMillis());
		Location origin = mob.getLocation();
		if (origin == null || origin.getWorld() == null) return;

		// 1. Look for lure first (async)
		Villager lure = MobLureUtil.findNearestLure(origin, plugin, LURE_ATTRACTION_RADIUS);
		if (lure != null) {
			Location lureLoc = lure.getLocation();
			handleMobPathToTargetAsync(mob, lureLoc, true);
			return;
		}

		// 2. Otherwise try kingdom structures (async)
		Location kingdomStructure = findNearestStructure(origin);
		if (kingdomStructure != null) {
			handleMobPathToTargetAsync(mob, kingdomStructure, false);
			return;
		}

		// 3. Otherwise try light sources (async)
		Location lightSource = findNearestLightSource(origin);
		if (lightSource != null) {
			handleMobPathToTargetAsync(mob, lightSource, false);
		}
	}

	private void handleMobPathToTargetAsync(LivingEntity mob, Location target, boolean isLure) {
		if (target == null || !mob.isValid() || mob.isDead() || !(mob instanceof Mob mobEntity)) return;
		mobTargets.put(mob.getUniqueId(), target);
		com.destroystokyo.paper.entity.Pathfinder pathfinder = mobEntity.getPathfinder();
		if (pathfinder == null) return;

		com.destroystokyo.paper.entity.Pathfinder.PathResult path = pathfinder.findPath(target);
		if (mobEntity.isDead() || !mobEntity.isValid()) return;

		// Handle creeper explosion logic
		if (mob instanceof Creeper creeper) {
			double distance = mob.getLocation().distance(target);
			if (distance <= CREEPER_EXPLOSION_RADIUS) {
				if (path == null || !path.canReachFinalPoint()) {
					double scale = 1.0;
					if (plugin.getScaleManager().hasScaleAttribute()) {
						AttributeInstance scaleAttr = creeper.getAttribute(plugin.getScaleManager().getScaleAttribute());
						if (scaleAttr != null) {
							scale = scaleAttr.getValue();
						}
					}
					int explosionRadius = (int) Math.min(6, Math.max(1, Math.round(3 * scale)));
					creeper.setExplosionRadius(explosionRadius);
					creeper.explode();
					return;
				}
			}
		}

		if (path != null && path.canReachFinalPoint()) {
			double speed = isStructureTarget(target) ? STRUCTURE_MOVE_SPEED : NORMAL_MOVE_SPEED;
			pathfinder.moveTo(path, speed);
		}
	}

	private boolean isStructureTarget(Location target) {
		String worldName = target.getWorld().getName();
		Set<Location> structures = structureCache.get(worldName);
		if (structures == null) return false;
		
		return structures.stream()
				.anyMatch(loc -> loc.distanceSquared(target) < 1.0);
	}

	private Location findNearestStructure(Location origin) {
		if (origin == null || origin.getWorld() == null) return null;
		
		Set<Location> structures = structureCache.get(origin.getWorld().getName());
		if (structures == null) return null;

		return structures.stream()
				.filter(loc -> loc.distanceSquared(origin) <= STRUCTURE_SEARCH_RADIUS * STRUCTURE_SEARCH_RADIUS)
				.min(Comparator.comparingDouble(loc -> loc.distanceSquared(origin)))
				.orElse(null);
	}

	// Scan for light sources based on light level, async-safe
	private Location findNearestLightSource(Location origin) {
		if (origin == null) return null;
		World world = origin.getWorld();
		if (world == null) return null;
		int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();
		Location best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -LIGHT_SEARCH_RADIUS; dx <= LIGHT_SEARCH_RADIUS; dx++) {
			for (int dz = -LIGHT_SEARCH_RADIUS; dz <= LIGHT_SEARCH_RADIUS; dz++) {
				int x = ox + dx, z = oz + dz;
				if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
				for (int dy = -4; dy <= 4; dy++) {
					int y = oy + dy;
					if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
					// Attract to blocks with light level > threshold
					if (world.getBlockAt(x, y, z).getLightFromBlocks() > LIGHT_LEVEL_THRESHOLD) {
						Location candidate = new Location(world, x, y, z);
						double dist = origin.distanceSquared(candidate);
						if (dist < bestDist) {
							bestDist = dist;
							best = new Location(world, x + 0.5, y, z + 0.5);
						}
					}
				}
			}
		}
		return best;
	}

	// Clean up when a mob dies
	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		if (event == null || event.getEntity() == null) return;
		unregisterMob(event.getEntity());
	}

	public void unregisterMob(LivingEntity mob) {
		if (mob == null) return;
		trackedMobs.remove(mob);
		mobTargets.remove(mob.getUniqueId());
		lastScan.remove(mob.getUniqueId());
		mobPriorities.remove(mob.getUniqueId());
	}
}
