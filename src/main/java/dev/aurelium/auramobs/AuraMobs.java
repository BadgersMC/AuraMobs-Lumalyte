package dev.aurelium.auramobs;

import co.aikar.commands.PaperCommandManager;
import com.archyx.polyglot.Polyglot;
import com.archyx.polyglot.PolyglotProvider;
import com.archyx.polyglot.config.PolyglotConfig;
import com.archyx.polyglot.config.PolyglotConfigBuilder;
import com.archyx.polyglot.lang.MessageKey;
import dev.aurelium.auramobs.api.AuraMobsAPI;
import dev.aurelium.auramobs.api.WorldGuardHook;
import dev.aurelium.auramobs.commands.AuraMobsCommand;
import dev.aurelium.auramobs.config.ConfigManager;
import dev.aurelium.auramobs.config.OptionKey;
import dev.aurelium.auramobs.config.OptionValue;
import dev.aurelium.auramobs.listeners.*;
import dev.aurelium.auramobs.util.Formatter;
import dev.aurelium.auramobs.entities.ScaleManager;
import dev.aurelium.auramobs.util.MobAttractionManager;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.aurelium.auramobs.event.BloodMoonManager;
import org.bukkit.World;

public class AuraMobs extends JavaPlugin implements PolyglotProvider {

    private static final int bstatsId = 22266;
    private NamespacedKey mobKey;
    private WorldGuardHook worldGuard;
    private AuraSkillsApi auraSkills;
    private double maxHealth;
    private double maxDamage;
    private boolean namesEnabled;
    private int globalLevel;
    private Formatter formatter;
    private ConfigManager configManager;
    private Polyglot polyglot;
    private Locale language;
    private ScaleManager scaleManager;
    private List<String> enabledWorlds;
    private boolean worldWhitelist;
    private boolean placeholderAPIEnabled;
    private boolean mythicMobsEnabled;
    private boolean ignoreMythicMobs;
    private boolean smallCapsEnabled;
    private Set<String> spawnReasons;
    private MobAttractionManager mobAttractionManager;
    private BloodMoonManager bloodMoonManager;
    private String bloodMoonResourcePackUrl;
    private boolean debug;

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuard = new WorldGuardHook(true);
        }
    }

    @Override
    public void onEnable() {
        AuraMobsAPI.setPlugin(this);
        // Set Aurelium Skills instance
        auraSkills = AuraSkillsApi.get();

        globalLevel = 0;
        // Load config
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        // Load messages
        PolyglotConfig polyglotConfig = new PolyglotConfigBuilder()
                .defaultLanguage("en")
                .providedLanguages(new String[] {"en", "es", "de", "nl"})
                .messageDirectory("messages")
                .messageFileName("messages_{language}.yml").build();
        polyglot = new Polyglot(this, polyglotConfig);
        polyglot.getMessageManager().loadMessages();

        for (Player player: this.getServer().getOnlinePlayers()) {
            globalLevel += getLevel(player);
        }
        language = new Locale(optionString("language"));

        mobKey = new NamespacedKey(this, "isAureliumMob");

        namesEnabled = optionBoolean("custom_name.enabled");

        scaleManager = new ScaleManager(this);
        scaleManager.loadConfiguration();

        ignoreMythicMobs = optionBoolean("custom_name.ignore_mythic_mobs");

        boolean displayByRange = optionBoolean("custom_name.display_by_range");

        this.getServer().getPluginManager().registerEvents(new MobSpawn(this), this);
        this.getServer().getPluginManager().registerEvents(new EntityXpGainListener(this), this);
        debug = optionBoolean("debug");
        mobAttractionManager = new MobAttractionManager(this, debug);
        this.getServer().getPluginManager().registerEvents(mobAttractionManager, this);
        this.getServer().getPluginManager().registerEvents(new StructureLureListener(this), this);

        // Register BloodMoonMobListener
        this.getServer().getPluginManager().registerEvents(new BloodMoonMobListener(this), this);

        if (namesEnabled) {
            this.getServer().getPluginManager().registerEvents(new MobDamage(this), this);
            this.getServer().getPluginManager().registerEvents(new MobTransform(this), this);
            this.getServer().getPluginManager().registerEvents(new PlayerJoinLeave(this), this);
            if (displayByRange) {
                this.getServer().getPluginManager().registerEvents(new MoveEvent(this), this);
            }
        }

        this.getServer().getPluginManager().registerEvents(new MobDeath(this), this);

        new Metrics(this, bstatsId);

        registerCommands();
        loadWorlds(); // Assuming loadWorlds handles missing world options internally

        // Safely load max health and max damage, default to Bukkit/Spigot defaults if options not found
        maxHealth = Bukkit.spigot().getConfig().getDouble("settings.attribute.maxHealth.max", 20.0); // Default to 20.0
        maxDamage = Bukkit.spigot().getConfig().getDouble("settings.attribute.attackDamage.max", 2.0); // Default to 2.0

        int healthRoundingPlaces = optionInt("custom_name.health_rounding_places");
        formatter = new Formatter(healthRoundingPlaces);

        // Check for PlaceholderAPI
        placeholderAPIEnabled = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        mythicMobsEnabled = getServer().getPluginManager().isPluginEnabled("MythicMobs");

        spawnReasons = new HashSet<>(optionList("spawn_reasons"));

        // Initialize BloodMoonManager and schedule task
        bloodMoonManager = new BloodMoonManager(this, debug);
        scheduleMoonPhaseTask();

        // Load Blood Moon resource pack URL (optional)
        bloodMoonResourcePackUrl = optionString("blood_moon.resource_pack_url");

    }

    @Override
    public void onDisable() {
    }

    public void loadWorlds() {
        enabledWorlds = optionList("worlds.list");
        worldWhitelist = optionString("worlds.type").equalsIgnoreCase("whitelist");
    }

    public void registerCommands() {
        PaperCommandManager manager = new PaperCommandManager(this);
        manager.registerCommand(new AuraMobsCommand(this));
    }

    public AuraSkillsApi getAuraSkills() {
        return auraSkills;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Polyglot getPolyglot() {
        return polyglot;
    }

    public ScaleManager getScaleManager() {
        return scaleManager;
    }

    public boolean isNamesEnabled() {
        return namesEnabled;
    }

    public boolean isSmallCapsEnabled() {
        return smallCapsEnabled;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getMaxDamage() {
        return maxDamage;
    }

    public double getDamageScalingPerLevel() {
        return optionDouble("damage-scaling-per-level");
    }

    public int getSumLevel(Player player) {
        int sum = 0;

        SkillsUser user = auraSkills.getUser(player.getUniqueId());
        for (Skill skill : getEnabledSkills()) {
            sum += user.getSkillLevel(skill);
        }

        return sum;
    }

    public int getAverageLevel(Player p) {
        int enabled = getEnabledSkills().size();
        if (enabled == 0) {
            enabled = Skills.values().length;
        }
        return getSumLevel(p) / enabled;
    }

    private List<Skill> getEnabledSkills() {
        return auraSkills.getGlobalRegistry().getSkills().stream().filter(Skill::isEnabled).toList();
    }

    public int getGlobalLevel() {
        return globalLevel;
    }

    public void setGlobalLevel(int globalLevel) {
        this.globalLevel = globalLevel;
    }

    public int getLevel(Player p) {
        SkillsUser user = auraSkills.getUser(p.getUniqueId());
        List<Skill> skills = getEnabledSkills();

        String formula = optionString("player_level.formula");

        formula = formula.replace("{sumall}", Integer.toString(getSumLevel(p)))
                .replace("{average}", Integer.toString(getAverageLevel(p)))
                .replace("{skillcount}", Integer.toString(skills.size()));

        for (Skill skill : skills) {
            String replace = "{" + skill.name().toLowerCase(Locale.ROOT) + "}";
            formula = formula.replace(replace, Integer.toString(user.getSkillLevel(skill)));
        }

        try {
            return (int) Math.round(new ExpressionBuilder(formula)
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
                    })
                    .build().evaluate());
        } catch (Exception e) {
            // Log an error if the formula evaluation fails and return a default level
            getLogger().severe("Error evaluating player level formula: " + formula);
            e.printStackTrace();
            return getAverageLevel(p); // Return average level as a fallback
        }
    }

    public boolean isAuraMob(LivingEntity m) {
        return m.getPersistentDataContainer().has(mobKey, PersistentDataType.INTEGER);
    }

    public boolean isInvalidEntity(Entity entity) {
        if (!(entity instanceof LivingEntity mob)) return true;
        if (entity instanceof Hoglin || entity instanceof Slime) return false; // This seems to mark Hoglins/Slimes as VALID?
        return !(entity instanceof Monster) && !isBossMob(mob); // Invalid if not a Monster AND not a BossMob
    }

    public boolean isBossMob(LivingEntity entity) {
        return entity instanceof Boss || entity instanceof ElderGuardian;
    }

    public Formatter getFormatter() {
        return formatter;
    }

    public List<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public boolean isWorldWhitelist() {
        return worldWhitelist;
    }

    public boolean ignoreMythicMobs() {
        return optionBoolean("custom_name.ignore_mythic_mobs");
    }

    public WorldGuardHook getWorldGuard() {
        return worldGuard;
    }

    public NamespacedKey getMobKey() {
        return mobKey;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    public boolean isMythicMobsEnabled() {
        return mythicMobsEnabled;
    }

    public Set<String> getSpawnReasons() {
        return spawnReasons;
    }

    public MobAttractionManager getMobAttractionManager() {
        return mobAttractionManager;
    }

    public BloodMoonManager getBloodMoonManager() {
        return bloodMoonManager;
    }

    public String getBloodMoonResourcePackUrl() {
        return bloodMoonResourcePackUrl;
    }

    public String getMsg(String key) {
        // Safely get message, return key if not found
        MessageKey messageKey = MessageKey.of(key);
        String message = polyglot.getMessageManager().get(language, messageKey);
        return (message != null) ? message : key;
    }

    public Locale getLanguage() {
        return language;
    }

    public void setLanguage(Locale language) {
        this.language = language;
    }

    public OptionValue option(String key) {
         // Safely get option, return null if not found
        try {
            return configManager.getOption(new OptionKey(key));
        } catch (Exception e) {
            // Catching a general exception assuming getOption throws it when key is not found
            // Log a warning and return null to indicate the option was not found.
            getLogger().warning("Configuration option '" + key + "' not found or could not be retrieved.");
            return null;
        }
    }

    public String optionString(String key) {
        OptionValue option = option(key);
        return (option != null && option.asString() != null && !option.asString().isEmpty()) ? option.asString() : null;
    }

    public int optionInt(String key) {
        OptionValue option = option(key);
        // Explicitly check for null on the Integer object returned by asInt()
        Integer value = option != null ? option.asInt() : null;
        return (value != null) ? value : 0; // Default to 0 for int
    }

    public double optionDouble(String key) {
        OptionValue option = option(key);
        // Explicitly check for null on the Double object returned by asDouble()
        Double value = option != null ? option.asDouble() : null;
        return (value != null) ? value : 0.0; // Default to 0.0 for double
    }

    public boolean optionBoolean(String key) {
        OptionValue option = option(key);
        // Explicitly check for null on the Boolean object returned by asBoolean()
        Boolean value = option != null ? option.asBoolean() : null;
        return (value != null) ? value : false; // Default to false for boolean
    }

    public List<String> optionList(String key) {
        OptionValue option = option(key);
        return (option != null && option.asList() != null) ? option.asList() : new ArrayList<>(); // Default to empty list
    }

    @Override
    public void logInfo(String message) {
        getLogger().info(message);
    }

    @Override
    public void logWarn(String message) {
        getLogger().warning(message);
    }

    @Override
    public void logSevere(String message) {
        getLogger().severe(message);
    }

    private void scheduleMoonPhaseTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // We'll check the time in the overworld (world with name "world")
            World overworld = Bukkit.getWorlds().get(0); // Assumes the first world is the overworld
            if (overworld == null) return;

            long time = overworld.getTime(); // 0 is sunrise, 6000 is noon, 12000 is sunset, 18000 is midnight

            // A full moon occurs around 0 or 24000 ticks (start of the day) and 12000 ticks (sunset/start of night) after a new moon
            // A full moon phase lasts for a while, so we can check within a range
            boolean isFullMoon = time >= 0 && time < 100; // Around dawn after a full moon night

            // A new moon occurs around 12000 ticks (sunset/start of night) after a full moon
            boolean isNewMoon = time >= 12000 && time < 12100; // Around dusk after a new moon day

            if (!bloodMoonManager.isEventActive()) {
                // Check to start the event
                if (isFullMoon && bloodMoonManager.isNextFullMoonStartsEvent()) {
                    // Check if enough time has passed since the last full moon to avoid starting multiple times in one cycle
                    if (bloodMoonManager.getLastFullMoonTick() == -1 || (time - bloodMoonManager.getLastFullMoonTick() > 24000)) { // 24000 ticks is one full day
                        bloodMoonManager.startEvent();
                        bloodMoonManager.setNextFullMoonStartsEvent(false); // Next full moon will not start the event
                        bloodMoonManager.setLastFullMoonTick(time);
                    }
                } else if (isFullMoon && !bloodMoonManager.isNextFullMoonStartsEvent()) {
                    // If it's a full moon but we are skipping this cycle
                     if (bloodMoonManager.getLastFullMoonTick() == -1 || (time - bloodMoonManager.getLastFullMoonTick() > 24000)) {
                         bloodMoonManager.setNextFullMoonStartsEvent(true); // The next full moon will start the event
                         bloodMoonManager.setLastFullMoonTick(time);
                     }
                }
            } else {
                // Check to end the event
                // Safely check mobs killed to avoid NullPointerException if getMobsKilled was removed
                int mobsKilled = 0; // Default to 0 if method not available or returns null
                try {
                    mobsKilled = bloodMoonManager.getMobsKilled();
                } catch (Exception e) {
                    // Ignore exception, assume 0 mobs killed if method is missing
                }
                if (isNewMoon || mobsKilled >= 200) {
                    bloodMoonManager.endEvent();
                    // Safely reset mobs killed to avoid NullPointerException if resetMobsKilled was removed
                    try {
                        bloodMoonManager.resetMobsKilled();
                    } catch (Exception e) {
                        // Ignore exception
                    }
                    // The flag for the next full moon starting the event is already set to false when the event starts
                }
            }

        }, 0L, 20L); // Check every 20 ticks (every second)
    }

    public boolean isDebug() {
        return debug;
    }
}
