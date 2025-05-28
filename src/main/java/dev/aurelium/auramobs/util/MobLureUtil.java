package dev.aurelium.auramobs.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MobLureUtil {

	public static Villager spawnLure(Location loc, Plugin plugin) {
		if (loc == null || loc.getWorld() == null) {
			plugin.getLogger().warning("[MobLureUtil] Invalid location for lure spawn");
			return null;
		}
		World world = loc.getWorld();

		try {
			// Store the block coordinates before adding offset
			int blockX = loc.getBlockX();
			int blockY = loc.getBlockY();
			int blockZ = loc.getBlockZ();
			
			Villager villager = (Villager) world.spawnEntity(loc.add(0.5, 0, 0.5), EntityType.VILLAGER);
			villager.setSilent(true);
			villager.setInvisible(true);
			villager.setAI(false);
			villager.setInvulnerable(true);
			villager.setGravity(false);
			villager.setRemoveWhenFarAway(false);
			villager.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
			villager.setCustomNameVisible(false);

			// Identify for cleanup using block coordinates
			villager.getPersistentDataContainer().set(new NamespacedKey(plugin, "mob_lure"), PersistentDataType.BYTE, (byte) 1);
			villager.getPersistentDataContainer().set(new NamespacedKey(plugin, "lure_x"), PersistentDataType.INTEGER, blockX);
			villager.getPersistentDataContainer().set(new NamespacedKey(plugin, "lure_y"), PersistentDataType.INTEGER, blockY);
			villager.getPersistentDataContainer().set(new NamespacedKey(plugin, "lure_z"), PersistentDataType.INTEGER, blockZ);
			villager.getPersistentDataContainer().set(new NamespacedKey(plugin, "lure_world"), PersistentDataType.STRING, world.getName());

			return villager;
		} catch (Exception e) {
			plugin.getLogger().severe("[MobLureUtil] Failed to spawn lure: " + e.getMessage());
			return null;
		}
	}

	public static boolean isMobLure(Entity entity, Plugin plugin) {
		if (!(entity instanceof Villager)) return false;
		Byte tag = entity.getPersistentDataContainer()
				.get(new NamespacedKey(plugin, "mob_lure"), PersistentDataType.BYTE);
		return tag != null && tag == 1;
	}

	public static Villager findNearestLure(Location origin, Plugin plugin, double radius) {
		if (origin == null || origin.getWorld() == null) {
			return null;
		}
		
		Villager nearest = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity entity : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
			if (isMobLure(entity, plugin)) {
				double dist = origin.distanceSquared(entity.getLocation());
				if (dist < bestDist) {
					bestDist = dist;
					nearest = (Villager) entity;
				}
			}
		}
		
		if (nearest != null) {
			// plugin.getLogger().info("[MobLureUtil] Found nearest lure at distance: " + Math.sqrt(bestDist));
		}
		return nearest;
	}

	public static void cleanupLure(Location loc, Plugin plugin) {
		if (loc == null || loc.getWorld() == null) {
			plugin.getLogger().warning("[MobLureUtil] Invalid location for lure cleanup");
			return;
		}

		int removed = 0;
		
		// Use block coordinates for cleanup
		int blockX = loc.getBlockX();
		int blockY = loc.getBlockY();
		int blockZ = loc.getBlockZ();
		String worldName = loc.getWorld().getName();
		
		// Search in a 2x2x2 area around the block to account for any slight offsets
		for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
			if (!(entity instanceof Villager)) continue;
			if (!isMobLure(entity, plugin)) continue;

			// Match block coordinates and world
			var pdc = entity.getPersistentDataContainer();
			int x = pdc.get(new NamespacedKey(plugin, "lure_x"), PersistentDataType.INTEGER);
			int y = pdc.get(new NamespacedKey(plugin, "lure_y"), PersistentDataType.INTEGER);
			int z = pdc.get(new NamespacedKey(plugin, "lure_z"), PersistentDataType.INTEGER);
			String world = pdc.get(new NamespacedKey(plugin, "lure_world"), PersistentDataType.STRING);

			if (x == blockX && y == blockY && z == blockZ
					&& world != null && world.equals(worldName)) {
				entity.remove();
				removed++;
			}
		}
		
		plugin.getLogger().info("[MobLureUtil] Cleaned up " + removed + " lures");
	}

}
