package dev.aurelium.auramobs.listeners;

import dev.aurelium.auramobs.util.MobLureUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.kingdoms.constants.land.abstraction.KingdomBuilding;
import org.kingdoms.constants.land.location.SimpleLocation;
import org.kingdoms.constants.land.location.SimpleChunkLocation;
import org.kingdoms.events.items.KingdomBuildingFinishEvent;
import org.kingdoms.events.items.KingdomItemBreakEvent;
import org.kingdoms.server.location.BlockVector3;
import org.kingdoms.constants.land.Land;
import org.kingdoms.constants.land.structures.Structure;
import org.kingdoms.constants.land.turrets.Turret;

import java.util.Map;

public class StructureLureListener implements Listener {

	private final Plugin plugin;

	public StructureLureListener(Plugin plugin) {
		this.plugin = plugin;
		plugin.getLogger().info("[StructureLureListener] Initialized");
	}

	@EventHandler
	public void onStructureBuild(KingdomBuildingFinishEvent<?> event) {
		plugin.getLogger().info("[StructureLureListener] Structure build event fired");
		KingdomBuilding<?> building = event.getBuilding();
		if (building == null) {
			plugin.getLogger().warning("[StructureLureListener] Building is null");
			return;
		}

		// Get the land and structure location
		Land land = building.getLand();
		if (land == null) {
			plugin.getLogger().warning("[StructureLureListener] Land is null");
			return;
		}

		// First cleanup any existing lures at this location
		BlockVector3 blockLoc = null;
		boolean isTurret = building instanceof Turret;
		boolean isStructure = building instanceof Structure;

		if (isTurret) {
			for (Map.Entry<BlockVector3, Turret> entry : land.getTurrets().entrySet()) {
				if (entry.getValue() == building) {
					blockLoc = entry.getKey();
					break;
				}
			}
		} else if (isStructure) {
			for (Map.Entry<BlockVector3, Structure> entry : land.getStructures().entrySet()) {
				if (entry.getValue() == building) {
					blockLoc = entry.getKey();
					break;
				}
			}
		}

		if (blockLoc == null) {
			plugin.getLogger().warning("[StructureLureListener] Could not find structure location in land");
			return;
		}

		SimpleChunkLocation chunkLoc = land.getLocation();
		World world = Bukkit.getWorld(chunkLoc.getWorld());
		if (world == null) {
			plugin.getLogger().warning("[StructureLureListener] World is null: " + chunkLoc.getWorld());
			return;
		}

		Location loc = new Location(world, blockLoc.getX() + 0.5, blockLoc.getY(), blockLoc.getZ() + 0.5);
		
		// Cleanup any existing lures first
		plugin.getLogger().info("[StructureLureListener] Cleaning up existing lures at: " + loc);
		MobLureUtil.cleanupLure(loc, plugin);
		
		// Then spawn the new lure
		plugin.getLogger().info("[StructureLureListener] Attempting to spawn lure at: " + loc);
		MobLureUtil.spawnLure(loc, plugin);
	}

	@EventHandler
	public void onStructureRemove(KingdomItemBreakEvent<?> event) {
		plugin.getLogger().info("[StructureLureListener] Structure remove event fired");
		Object kingdomBlock = event.getKingdomBlock();
		if (kingdomBlock == null) {
			plugin.getLogger().warning("[StructureLureListener] KingdomBlock is null");
			return;
		}

		// Get the land and structure location
		Land land = null;
		try {
			land = (Land) kingdomBlock.getClass().getMethod("getLand").invoke(kingdomBlock);
		} catch (Exception e) {
			plugin.getLogger().warning("[StructureLureListener] Failed to get land: " + e.getMessage());
			return;
		}

		if (land == null) {
			plugin.getLogger().warning("[StructureLureListener] Land is null");
			return;
		}

		// Get the structure's location from the land's structures map
		BlockVector3 blockLoc = null;
		boolean isTurret = kingdomBlock instanceof Turret;
		boolean isStructure = kingdomBlock instanceof Structure;

		if (isTurret) {
			for (Map.Entry<BlockVector3, Turret> entry : land.getTurrets().entrySet()) {
				if (entry.getValue() == kingdomBlock) {
					blockLoc = entry.getKey();
					break;
				}
			}
		} else if (isStructure) {
			for (Map.Entry<BlockVector3, Structure> entry : land.getStructures().entrySet()) {
				if (entry.getValue() == kingdomBlock) {
					blockLoc = entry.getKey();
					break;
				}
			}
		}

		if (blockLoc == null) {
			plugin.getLogger().warning("[StructureLureListener] Could not find structure location in land for removal");
			return;
		}

		SimpleChunkLocation chunkLoc = land.getLocation();
		World world = Bukkit.getWorld(chunkLoc.getWorld());
		if (world == null) {
			plugin.getLogger().warning("[StructureLureListener] World is null for removal: " + chunkLoc.getWorld());
			return;
		}

		Location loc = new Location(world, blockLoc.getX() + 0.5, blockLoc.getY(), blockLoc.getZ() + 0.5);
		plugin.getLogger().info("[StructureLureListener] Attempting to cleanup lure at: " + loc);
		MobLureUtil.cleanupLure(loc, plugin);
	}
}
