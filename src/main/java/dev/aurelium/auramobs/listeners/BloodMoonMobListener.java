package dev.aurelium.auramobs.listeners;

import dev.aurelium.auramobs.AuraMobs;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Illusioner;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;

public class BloodMoonMobListener implements Listener {

    private final AuraMobs plugin;
    private final Random random = new Random();
    private static final int MAX_MOBS_FOR_EVENT_END = 200;

    // Armor trims require Minecraft 1.20+
    private final TrimMaterial[] trimMaterials;
    private final TrimPattern[] trimPatterns;

    private final Map<Enchantment, Integer> ARMOR_ENCHANTMENTS = new HashMap<>();
    private final Map<Enchantment, Integer> WEAPON_ENCHANTMENTS = new HashMap<>();
    private final Map<Enchantment, Integer> BOW_ENCHANTMENTS = new HashMap<>();

    public BloodMoonMobListener(AuraMobs plugin) {
        this.plugin = plugin;
        // Define possible enchantments and their max levels
        ARMOR_ENCHANTMENTS.put(Enchantment.PROTECTION, 3);
        ARMOR_ENCHANTMENTS.put(Enchantment.UNBREAKING, 3);
        ARMOR_ENCHANTMENTS.put(Enchantment.MENDING, 1);

        WEAPON_ENCHANTMENTS.put(Enchantment.SHARPNESS, 3);
        WEAPON_ENCHANTMENTS.put(Enchantment.UNBREAKING, 3);
        WEAPON_ENCHANTMENTS.put(Enchantment.MENDING, 1);

        BOW_ENCHANTMENTS.put(Enchantment.POWER, 3);
        BOW_ENCHANTMENTS.put(Enchantment.UNBREAKING, 3);
        BOW_ENCHANTMENTS.put(Enchantment.MENDING, 1);
        BOW_ENCHANTMENTS.put(Enchantment.FLAME, 1);
        BOW_ENCHANTMENTS.put(Enchantment.PUNCH, 1);

        // Initialize armor trim materials and patterns
        this.trimMaterials = new TrimMaterial[] {
            TrimMaterial.DIAMOND,
            TrimMaterial.IRON,
            TrimMaterial.GOLD,
            TrimMaterial.EMERALD,
            TrimMaterial.REDSTONE,
            TrimMaterial.LAPIS,
            TrimMaterial.AMETHYST,
            TrimMaterial.GOLD,
            TrimMaterial.IRON,
            TrimMaterial.DIAMOND,
            TrimMaterial.NETHERITE,
            TrimMaterial.QUARTZ,
            TrimMaterial.RESIN
        };

         this.trimPatterns = new TrimPattern[]{
            TrimPattern.SHAPER,
            TrimPattern.WAYFINDER,
            TrimPattern.RIB,
            TrimPattern.SNOUT,
            TrimPattern.COAST
         };

    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!plugin.getBloodMoonManager().isEventActive()) return;
        if (event.getEntity() == null) return;

        // 2% chance to replace any hostile mob with an Illusioner
        if (event.getEntity() instanceof Monster && random.nextDouble() < 0.02) {
            Location loc = event.getEntity().getLocation();
            event.getEntity().remove();
            Illusioner illusioner = loc.getWorld().spawn(loc, Illusioner.class);
            plugin.getBloodMoonManager().markBloodMoonMob(illusioner);
            return;
        }

        // 10% chance to replace any hostile mob with a Chicken Jockey
        if (event.getEntity() instanceof Skeleton || event.getEntity() instanceof Zombie || event.getEntity() instanceof WitherSkeleton || event.getEntity() instanceof Spider) {
            if (random.nextDouble() < 0.1) {
                spawnChickenJockey(event.getEntity().getLocation());
                event.getEntity().remove();
                return;
            }
        }

        LivingEntity entity = null;
        if (event.getEntity() instanceof Skeleton skeleton) {
            entity = skeleton;
            applySkeletonGear(skeleton);
        } else if (event.getEntity() instanceof Zombie zombie) {
            entity = zombie;
            applyZombieGear(zombie);
        } else if (event.getEntity() instanceof WitherSkeleton witherSkeleton) {
            entity = witherSkeleton;
            applyWitherSkeletonGear(witherSkeleton);
        } else if (event.getEntity() instanceof Pillager pillager) {
            entity = pillager;
            // Optionally: apply custom gear/effects here
        } else if (event.getEntity() instanceof Vindicator vindicator) {
            entity = vindicator;
            // Optionally: apply custom gear/effects here (can wear armor)
        } else if (event.getEntity() instanceof Chicken) {
            // Special handling for Chicken Jockey (legacy, now handled above)
            return;
        } else if (event.getEntity() instanceof Spider spider) {
            // Special handling for Spider Jockey
            if (random.nextDouble() < 0.1) {
                spawnSpiderJockey(spider);
            }
            return;
        }

        if (entity != null) {
            // mark the mob as a blood moon mob for kill tracking
            entity.getPersistentDataContainer().set(plugin.getMobKey(), PersistentDataType.INTEGER, 1);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getBloodMoonManager().isEventActive()) return;
        if (event.getEntity() == null) return;

        LivingEntity entity = event.getEntity();

        // Only count mobs with the 'bloodmoon_mob' key
        NamespacedKey bloodMoonKey = new NamespacedKey(plugin, "bloodmoon_mob");
        if (entity.getPersistentDataContainer().has(bloodMoonKey, PersistentDataType.BYTE)) {
            plugin.getBloodMoonManager().addMobKill();
            if (plugin.getBloodMoonManager().getMobsKilled() >= MAX_MOBS_FOR_EVENT_END) {
                plugin.getBloodMoonManager().endEvent();
            }
        }
    }

    private void applySkeletonGear(Skeleton skeleton) {
        EntityEquipment equipment = skeleton.getEquipment();
        if (equipment == null) return;

        // Chainmail armor
        ItemStack helmet = createEnchantedArmor(Material.CHAINMAIL_HELMET);
        ItemStack chestplate = createEnchantedArmor(Material.CHAINMAIL_CHESTPLATE);
        ItemStack leggings = createEnchantedArmor(Material.CHAINMAIL_LEGGINGS);
        ItemStack boots = createEnchantedArmor(Material.CHAINMAIL_BOOTS);

        applyRandomArmorTrim(helmet);
        applyRandomArmorTrim(chestplate);
        applyRandomArmorTrim(leggings);
        applyRandomArmorTrim(boots);

        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setLeggings(leggings);
        equipment.setBoots(boots);

        // Carved pumpkin head
        equipment.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
    }

    private void applyZombieGear(Zombie zombie) {
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment == null) return;

        // Iron armor
        ItemStack helmet = createEnchantedArmor(Material.IRON_HELMET);
        ItemStack chestplate = createEnchantedArmor(Material.IRON_CHESTPLATE);
        ItemStack leggings = createEnchantedArmor(Material.IRON_LEGGINGS);
        ItemStack boots = createEnchantedArmor(Material.IRON_BOOTS);

        applyRandomArmorTrim(helmet);
        applyRandomArmorTrim(chestplate);
        applyRandomArmorTrim(leggings);
        applyRandomArmorTrim(boots);

        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setLeggings(leggings);
        equipment.setBoots(boots);

        // Iron axe
        equipment.setItemInMainHand(createEnchantedItem(Material.IRON_AXE));
    }

    private void applyWitherSkeletonGear(WitherSkeleton witherSkeleton) {
        EntityEquipment equipment = witherSkeleton.getEquipment();
        if (equipment == null) return;

        // Bow (Wither Skeletons normally use stone swords, giving them bows is custom)
        equipment.setItemInMainHand(createEnchantedItem(Material.BOW));

        // Apply armor trims (if you want armor on Wither Skeletons - assuming full set)
        ItemStack helmet = createEnchantedArmor(Material.IRON_HELMET); // Example: Iron armor
        ItemStack chestplate = createEnchantedArmor(Material.IRON_CHESTPLATE);
        ItemStack leggings = createEnchantedArmor(Material.IRON_LEGGINGS);
        ItemStack boots = createEnchantedArmor(Material.IRON_BOOTS);

        applyRandomArmorTrim(helmet);
        applyRandomArmorTrim(chestplate);
        applyRandomArmorTrim(leggings);
        applyRandomArmorTrim(boots);

        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setLeggings(leggings);
        equipment.setBoots(boots);
    }

    private void spawnChickenJockey(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Chicken chicken = world.spawn(loc, Chicken.class, c -> c.setAdult());
        Zombie jockey = world.spawn(loc, Zombie.class, z -> z.setBaby());
        applyZombieGear(jockey);
        chicken.addPassenger(jockey);
        plugin.getBloodMoonManager().markBloodMoonMob(jockey);
        plugin.getBloodMoonManager().markBloodMoonMob(chicken);
    }

    private void spawnSpiderJockey(Spider spider) {
        // Spawn a skeleton with chainmail armor and carved pumpkin
        Skeleton jockey = spider.getWorld().spawn(spider.getLocation(), Skeleton.class);
        applySkeletonGear(jockey); // Apply gear to the skeleton jockey
        spider.addPassenger(jockey);
         // Mark the jockey as an event mob, also for kill tracking
        jockey.getPersistentDataContainer().set(plugin.getMobKey(), PersistentDataType.INTEGER, 1);
    }

    private ItemStack createEnchantedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        // Apply random armor enchantments
        for (Map.Entry<Enchantment, Integer> entry : ARMOR_ENCHANTMENTS.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int maxLevel = entry.getValue();
            // 50% chance to apply each enchantment
            if (random.nextDouble() < 0.5) {
                int level = random.nextInt(maxLevel) + 1; // Random level from 1 to maxLevel
                item.addEnchantment(enchantment, level);
            }
        }
        return item;
    }

    private ItemStack createEnchantedItem(Material material) {
        ItemStack item = new ItemStack(material);
        Map<Enchantment, Integer> enchantments = null;
        if (material == Material.BOW) {
            enchantments = BOW_ENCHANTMENTS;
        } else if (material.toString().endsWith("_AXE")) { // Simple check for axes
            enchantments = WEAPON_ENCHANTMENTS;
        }

        if (enchantments != null) {
             // Apply random weapon/tool enchantments
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                Enchantment enchantment = entry.getKey();
                int maxLevel = entry.getValue();
                 // 50% chance to apply each enchantment
                if (random.nextDouble() < 0.5) {
                    int level = random.nextInt(maxLevel) + 1; // Random level from 1 to maxLevel
                    item.addEnchantment(enchantment, level);
                }
            }
        }

        return item;
    }

    // Implement applyRandomArmorTrim method
    private void applyRandomArmorTrim(ItemStack item) {
       if (item.getItemMeta() instanceof ArmorMeta armorMeta) {
           if (trimMaterials != null && trimMaterials.length > 0 && trimPatterns != null && trimPatterns.length > 0) {
               TrimMaterial randomMaterial = trimMaterials[random.nextInt(trimMaterials.length)];
               TrimPattern randomPattern = trimPatterns[random.nextInt(trimPatterns.length)];
               armorMeta.setTrim(new ArmorTrim(randomMaterial, randomPattern));
               item.setItemMeta(armorMeta);
           }
       }
    }
} 