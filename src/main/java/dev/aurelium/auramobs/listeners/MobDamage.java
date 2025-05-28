package dev.aurelium.auramobs.listeners;

import dev.aurelium.auramobs.AuraMobs;
import dev.aurelium.auramobs.util.ColorUtils;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

public class MobDamage implements Listener {

    private final AuraMobs plugin;

    public MobDamage(AuraMobs plugin){
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMobDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        if (!plugin.isAuraMob(entity)) {
            return;
        }

        // Nametag update logic
        if (plugin.isNamesEnabled()) { // Only update if custom names are enabled
            int level = entity.getPersistentDataContainer().getOrDefault(plugin.getMobKey(), PersistentDataType.INTEGER, 1);
            double resHealth = entity.getHealth() - e.getFinalDamage();
            resHealth = Math.max(resHealth, 0.0);
            String formattedHealth = plugin.getFormatter().format(resHealth);

            // Construct the nametag string with legacy color codes
            String nametagString;
            try {
                nametagString = plugin.optionString("custom_name.format")
                        .replace("{mob}", plugin.getMsg("mobs." + entity.getType().name().toLowerCase(Locale.ROOT)))
                        .replace("{lvl}", Integer.toString(level))
                        .replace("{health}", formattedHealth)
                        .replace("{maxhealth}", plugin.getFormatter().format(entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
            } catch (NullPointerException ex){
                // Fallback if config option is missing
                nametagString = plugin.optionString("custom_name.format")
                        .replace("{mob}", entity.getType().name())
                        .replace("{lvl}", Integer.toString(level))
                        .replace("{health}", formattedHealth)
                        .replace("{maxhealth}", plugin.getFormatter().format(entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
            }

            // Convert the legacy color code string to an Adventure Component
            Component nametagComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(ColorUtils.colorMessage(nametagString));

            // Set the custom name using the Adventure API
            entity.customName(nametagComponent);
            entity.setCustomNameVisible(true); // Ensure nametag is visible
            // Prepend Blood Moon emoji if this is a Blood Moon mob
            NamespacedKey bloodMoonKey = new NamespacedKey(plugin, "bloodmoon_mob");
            if (plugin.getBloodMoonManager().isEventActive() &&
                entity.getPersistentDataContainer().has(bloodMoonKey, PersistentDataType.BYTE)) {
                // Unicode U+E014 for LumaLyte moon logo, colored red
                Component bloodMoonPrefix = Component.text("\uE014 ").color(NamedTextColor.RED);
                entity.customName(bloodMoonPrefix.append(entity.customName() != null ? entity.customName() : Component.empty()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Projectile p)) {
            return;
        }

        if (!(p.getShooter() instanceof LivingEntity entity)) {
            return;
        }

        if (!plugin.isAuraMob(entity)) {
            return;
        }

        e.setDamage(entity.getAttribute(Attribute.ATTACK_DAMAGE).getValue());
    }

    // Damage scaling by level logic
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMobDealDamage(EntityDamageByEntityEvent event) {
        LivingEntity damager = null;
        if (event.getDamager() instanceof LivingEntity) {
            damager = (LivingEntity) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity) {
            damager = (LivingEntity) projectile.getShooter();
        }
        if (damager == null) return;
        if (!plugin.isAuraMob(damager)) return;

        int level = damager.getPersistentDataContainer().getOrDefault(plugin.getMobKey(), PersistentDataType.INTEGER, 1);

        double baseDamage = event.getDamage();

        double scale = 1.0 + plugin.getDamageScalingPerLevel() * (level - 1);
        event.setDamage(baseDamage * scale);
    }

}
