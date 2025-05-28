package dev.aurelium.auramobs.entities;

import dev.aurelium.auramobs.AuraMobs;
import dev.aurelium.auramobs.util.ColorUtils;
import dev.aurelium.auramobs.util.MessageUtils;
import dev.aurelium.auramobs.util.SmallCapsUtil;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class AureliumMob {

    public AureliumMob(LivingEntity mob, int level, AuraMobs plugin) {
        if (mob instanceof Zombie) {
            mob.getAttribute(Attribute.MAX_HEALTH).getModifiers().clear();
        }

        int level1;
        if (level > 0) {
            level1 = level;
        }
        else {
            level1 = 1;
        }
        Location mobloc = mob.getLocation();
        Location spawnpoint = mob.getWorld().getSpawnLocation();
        double distance = mobloc.distance(spawnpoint);
        double startDamage = mob instanceof EnderDragon ? 0 : BigDecimal.valueOf(mob.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue()).setScale(2, RoundingMode.CEILING).doubleValue();
        double startHealth = BigDecimal.valueOf(mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).setScale(2, RoundingMode.CEILING).doubleValue();
        String prefix = plugin.isBossMob(mob) ? "bosses." : "mob_defaults.";
        String damageFormula = MessageUtils.setPlaceholders(null, plugin.optionString(prefix + "damage.formula")
                .replace("{mob_damage}", String.valueOf(startDamage))
                .replace("{level}", String.valueOf(level1))
                .replace("{distance}", Double.toString(distance))
                .replace("{location_y}", Double.toString(mobloc.getY()))
                .replace("{location_x}", Double.toString(mobloc.getX()))
                .replace("{location_z}", Double.toString(mobloc.getZ()))
        );

        double worldMultiplier = 1.0;
        String worldName = mob.getWorld().getName().toLowerCase();
        if (worldName.contains("nether")) {
            worldMultiplier = 1.15;
        } else if (worldName.contains("end")) {
            worldMultiplier = 1.3;
        }
        String healthFormula = MessageUtils.setPlaceholders(null, plugin.optionString(prefix + "health.formula")
                .replace("{mob_health}", String.valueOf(startHealth))
                .replace("{level}", Integer.toString(level1))
                .replace("{distance}", Double.toString(distance))
                // Adds a placeholder to calculate depth level. Can be used to modify level scaling based on cave depth
                .replace("{location_y}", Double.toString(mobloc.getY()))
                // adds another placeholder to take into account the world, can be used to add some modifications to mobs based on the world they are in
                .replace("{world_multiplier}", Double.toString(worldMultiplier))

        );

        ExpressionBuilder baseBuilder = new ExpressionBuilder(damageFormula)
                .function(new Function("max", 2) {
                    @Override
                    public double apply(double... args) {
                        return Math.max(args[0], args[1]);
                    }
                })
                .function(new Function("min", 2) {
                    @Override
                    public double apply(double... args) {
                        return Math.min(args[0], args[1]);
                    }
                });
        Expression resDamage = baseBuilder.build();

        ExpressionBuilder healthBuilder = new ExpressionBuilder(healthFormula)
                .function(new Function("max", 2) {
                    @Override
                    public double apply(double... args) {
                        return Math.max(args[0], args[1]);
                    }
                })
                .function(new Function("min", 2) {
                    @Override
                    public double apply(double... args) {
                        return Math.min(args[0], args[1]);
                    }
                });
        Expression resHealth = healthBuilder.build();

        double damage = BigDecimal.valueOf(resDamage.evaluate()).setScale(2, RoundingMode.CEILING).doubleValue();
        double health = resHealth.evaluate();

        String optDamageMax = plugin.optionString(prefix + "damage.max");
        if (optDamageMax != null && !optDamageMax.isEmpty()) {
            String damageMax = MessageUtils.setPlaceholders(null, optDamageMax
                    .replace("{mob_damage}", String.valueOf(startDamage))
                    .replace("{level}", String.valueOf(level1))
                    .replace("{distance}", Double.toString(distance))
            );
            Expression resMaxDamage = new ExpressionBuilder(damageMax).build();
            double maxDamage = BigDecimal.valueOf(resMaxDamage.evaluate()).setScale(2, RoundingMode.CEILING).doubleValue();
            damage = Math.min(maxDamage, damage);
        }
        String optHealthMax = plugin.optionString(prefix + "health.max");
        if (optHealthMax != null && !optHealthMax.isEmpty()) {
            String healthMax = MessageUtils.setPlaceholders(null, optHealthMax
                    .replace("{mob_health}", String.valueOf(startHealth))
                    .replace("{level}", String.valueOf(level1))
                    .replace("{distance}", Double.toString(distance))
            );
            Expression resMaxHealth = new ExpressionBuilder(healthMax).build();
            double maxHealth = resMaxHealth.evaluate();
            health = Math.min(maxHealth, health);
        }

        String formattedHealth = plugin.getFormatter().format(health);
        if (health > plugin.getMaxHealth()) {
            health = plugin.getMaxHealth();
        }
        if (damage > plugin.getMaxDamage()) {
            damage = plugin.getMaxDamage();
        }

        if (!(mob instanceof EnderDragon)) mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(damage);

        AttributeInstance healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr == null) return;
        healthAttr.setBaseValue(health);

        double maxValue = healthAttr.getValue();
        mob.setHealth(Math.min(health, maxValue));

        mob.getPersistentDataContainer().set(plugin.getMobKey(), PersistentDataType.INTEGER, level1);
        if (plugin.isNamesEnabled()) {
            String mobName = plugin.getMsg("mobs." + mob.getType().name().toLowerCase(Locale.ROOT));
            if (plugin.isSmallCapsEnabled()) {
                mobName = SmallCapsUtil.toSmallCaps(mobName);
            }
            mob.setCustomName(ColorUtils.colorMessage(plugin.optionString("custom_name.format")
                    .replace("{mob}", mobName)
                    .replace("{lvl}", String.valueOf(level1))
                    .replace("{health}", formattedHealth)
                    .replace("{maxhealth}", formattedHealth)
                    .replace("{distance}", Double.toString(distance))
            ));
            mob.setCustomNameVisible(false);
        }

        if (plugin.getScaleManager().hasScaleAttribute()) {
            plugin.getScaleManager().applyScale(mob, level1);
        }
    }

}
