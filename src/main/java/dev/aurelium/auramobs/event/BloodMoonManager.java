package dev.aurelium.auramobs.event;

import dev.aurelium.auramobs.AuraMobs;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.GameRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;

public class BloodMoonManager {

    private final AuraMobs plugin;
    private boolean isEventActive;
    private int mobsKilled;
    private long lastFullMoonTick;
    private boolean nextFullMoonStartsEvent;
    private String resourcePackUrl;
    public static final int MAX_MOBS_FOR_EVENT_END = 200;
    private boolean debug;

    public BloodMoonManager(AuraMobs plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;
        this.isEventActive = false;
        this.mobsKilled = 0;
        this.lastFullMoonTick = -1;
        this.nextFullMoonStartsEvent = true; // Start on the first full moon
        this.resourcePackUrl = plugin.getBloodMoonResourcePackUrl(); // Get URL from plugin
        scheduleMoonPhaseCheckTask();
    }

    public boolean isEventActive() {
        return isEventActive;
    }

    public void setEventActive(boolean eventActive) {
        isEventActive = eventActive;
    }

    public int getMobsKilled() {
        return mobsKilled;
    }

    public void addMobKill() {
        this.mobsKilled++;
        if (isEventActive && mobsKilled % 50 == 0 && mobsKilled < MAX_MOBS_FOR_EVENT_END) {
            int left = Math.max(0, MAX_MOBS_FOR_EVENT_END - mobsKilled);
            org.bukkit.Bukkit.broadcast(
                net.kyori.adventure.text.Component.text("Blood Moon: ")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                    .append(net.kyori.adventure.text.Component.text(left + " mobs left to end the event!").color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
            );
        }
    }

    public void resetMobsKilled() {
        this.mobsKilled = 0;
    }

    public long getLastFullMoonTick() {
        return lastFullMoonTick;
    }

    public void setLastFullMoonTick(long lastFullMoonTick) {
        this.lastFullMoonTick = lastFullMoonTick;
    }

    public boolean isNextFullMoonStartsEvent() {
        return nextFullMoonStartsEvent;
    }

    public void setNextFullMoonStartsEvent(boolean nextFullMoonStartsEvent) {
        this.nextFullMoonStartsEvent = nextFullMoonStartsEvent;
    }

    public String getResourcePackUrl() {
        return resourcePackUrl;
    }

    // Method to start the event
    public void startEvent() {
        if (!isEventActive) {
            isEventActive = true;
            mobsKilled = 0;
            plugin.getLogger().info("Blood Moon Festival has started!");
            // Always send a chat message
            Bukkit.broadcast(Component.text("A Blood Moon has begun!")
                .color(NamedTextColor.RED));
            // Send resource pack prompt if configured
            if (resourcePackUrl != null && !resourcePackUrl.isEmpty()) {
                Component message = Component.text("A Blood Moon is rising! Click ")
                        .color(NamedTextColor.RED)
                        .append(Component.text("[HERE]")
                                .color(NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/auramobs applybloodmoonpack"))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to apply Blood Moon resource pack"))))
                        .append(Component.text(" to load the resource pack.")
                                .color(NamedTextColor.RED));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }
            }
        }
    }

    // Method to end the event
    public void endEvent() {
        if (isEventActive) {
            isEventActive = false;
            plugin.getLogger().info("Blood Moon Festival has ended.");
            // Resource pack handled server-side, no reversion needed by plugin.
        }
    }

    private void scheduleMoonPhaseCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                // Skip worlds with disabled day-night cycle
                if (Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))) {
                    continue;
                }
                long days = world.getFullTime() / 24000;
                int moonPhase = (int) (days % 8);
                // Check for full moon (phase 0)
                if (moonPhase == 0) {
                    if (nextFullMoonStartsEvent) {
                        startEvent();
                        nextFullMoonStartsEvent = false;
                    }
                // Check for new moon (phase 4)
                } else if (moonPhase == 4) {
                    nextFullMoonStartsEvent = true;
                }
            }
        }, 0L, 24000L); // Run every Minecraft day
    }

    public void markBloodMoonMob(LivingEntity entity) {
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "bloodmoon_mob"), PersistentDataType.BYTE, (byte) 1);
    }
}