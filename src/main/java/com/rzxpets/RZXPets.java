package com.rzxpets;

import com.rzxpets.rzx.RZXAuditService;
import com.rzxpets.rzx.RZXBus;
import com.rzxpets.rzx.RZXExceptionHandler;
import com.rzxpets.rzx.RZXHookManager;
import com.rzxpets.rzx.RZXLoggerService;
import com.rzxpets.rzx.RZXPerformanceTracker;
import me.rzx.core.api.RZXPlugin;
import me.rzx.core.api.RZXPluginMeta;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

@RZXPluginMeta(name = "RZXPets", version = "1.0.0", api = 1, authors = {"Antigravity"})
public class RZXPets extends JavaPlugin implements RZXPlugin, CommandExecutor {
    private static RZXPets instance;

    public static RZXPets getInstance() {
        return instance;
    }

    public static void debug(int level, String msg) {
        if (instance != null) {
            int currentLevel = instance.getConfig().getInt("debug-level", 0);
            if (currentLevel >= level) {
                com.rzxpets.rzx.RZXLoggerService.info(msg);
            }
        }
    }

    private DataManager dataManager;
    private PetManager petManager;
    private ShopGui shopGui;
    private Map<UUID, PlayerData> playerDataMap;
    private Map<String, YamlConfiguration> customMechanics = new HashMap<>();
    private FileConfiguration hooksConfig;
    private File hooksFile;

    public Map<String, YamlConfiguration> getCustomMechanics() {
        return customMechanics;
    }
    private boolean startupCompleted = false;
    private boolean tasksStarted = false;

    @Override
    public void onEnable() {
        instance = this;

        // Environment Validation Phase (Fail-Safe Startup)
        if (!validateEnvironment()) {
            RZXLoggerService.fatal("Environment validation failed! Disabling RZXPets cleanly.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Execute startup sequence if not already executed by RZXCore
        startup();

        // Register commands & listeners when Bukkit enables RZXPets
        registerCommands();
        
        try {
            getServer().getPluginManager().registerEvents(new EventListener(this), this);
            RZXLoggerService.success("Registered EventListener successfully inside onEnable!");
        } catch (Exception e) {
            RZXLoggerService.fatal("Failed to register EventListener: " + e.getMessage());
            e.printStackTrace();
        }

        // Safely start Bukkit scheduler tasks now that the plugin is enabled in Bukkit
        startTasks();

        // Initialize bStats Metrics
        try {
            int pluginId = 32852;
            new org.bstats.bukkit.Metrics(this, pluginId);
            RZXLoggerService.info("bStats metrics system initialized successfully with ID 32852.");
        } catch (Exception e) {
            RZXLoggerService.warning("Could not initialize bStats: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        shutdown();
        instance = null;
    }

    private boolean validateEnvironment() {
        // 1. RZXCore Presence & State Check
        if (!Bukkit.getPluginManager().isPluginEnabled("RZXCore")) {
            RZXLoggerService.fatal("RZXCore dependency is missing or disabled! RZXPets requires RZXCore to be active.");
            return false;
        }

        // 2. RZXCore API Version Compatibility Check
        org.bukkit.plugin.Plugin rzxCore = Bukkit.getPluginManager().getPlugin("RZXCore");
        if (rzxCore == null || !rzxCore.getDescription().getVersion().startsWith("1.")) {
            String ver = rzxCore != null ? rzxCore.getDescription().getVersion() : "Unknown";
            RZXLoggerService.fatal("Incompatible RZXCore API version found: " + ver + "! Expected major version 1.x.x.");
            return false;
        }

        // 3. Java Version Validation (Java 17+)
        String javaVersionStr = System.getProperty("java.version");
        int majorJavaVersion = parseJavaVersion(javaVersionStr);
        if (majorJavaVersion < 17) {
            RZXLoggerService.fatal("Unsupported Java runtime version: " + javaVersionStr + "! RZXPets requires Java 17 or higher.");
            return false;
        }

        // 4. Paper Platform Check
        if (!checkPaperPlatform()) {
            RZXLoggerService.fatal("Server platform validation failed! RZXPets requires Paper or a Paper-fork server.");
            return false;
        }

        return true;
    }

    private int parseJavaVersion(String version) {
        try {
            if (version.startsWith("1.")) {
                return Integer.parseInt(version.substring(2, 3));
            }
            int dot = version.indexOf(".");
            if (dot != -1) {
                return Integer.parseInt(version.substring(0, dot));
            }
            int dash = version.indexOf("-");
            if (dash != -1) {
                return Integer.parseInt(version.substring(0, dash));
            }
            return Integer.parseInt(version);
        } catch (Exception e) {
            return 17; // Fallback
        }
    }

    private boolean checkPaperPlatform() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException e1) {
            try {
                Class.forName("io.papermc.paper.configuration.Configuration");
                return true;
            } catch (ClassNotFoundException e2) {
                try {
                    Class.forName("com.destroystokyo.paper.ParticleBuilder");
                    return true;
                } catch (ClassNotFoundException e3) {
                    return Bukkit.getName().toLowerCase().contains("paper") || Bukkit.getVersion().toLowerCase().contains("paper");
                }
            }
        }
    }

    // =====================================================================
    // RZX Lifecycle Implementations
    // =====================================================================

    @Override
    public void startup() {
        if (startupCompleted) return;
        startupCompleted = true;

        RZXPerformanceTracker.track("RZXPets Startup Sequence", () -> {
            RZXAuditService.init(this);

            saveDefaultConfig();
            updateConfig("config.yml", new File(getDataFolder(), "config.yml"));

            loadHooksConfig();
            RZXHookManager.detectHooks();

            this.playerDataMap = new HashMap<>();

            loadCustomMechanics();
            loadPets();
            loadShopConfigs();

            this.dataManager = new DataManager(this);
            this.petManager = new PetManager(this);
            this.shopGui = new ShopGui(this);

            for (Player player : Bukkit.getOnlinePlayers()) {
                getPlayerData(player.getUniqueId());
            }

            registerCommands();

            // Safely attempt starting tasks (if already enabled in Bukkit)
            startTasks();

            // Register RZXPets into Bukkit ServicesManager & RZXCore PluginService
            Bukkit.getServicesManager().register(RZXPets.class, this, this, org.bukkit.plugin.ServicePriority.Normal);

            try {
                if (me.rzx.core.api.RZXCoreAPI.isReady()) {
                    me.rzx.core.plugin.PluginService pluginService = me.rzx.core.api.RZXCoreAPI.getRegistry().get(me.rzx.core.plugin.PluginService.class);
                    if (pluginService != null) {
                        me.rzx.core.plugin.PluginRecord record = new me.rzx.core.plugin.PluginRecord(
                            getName(),
                            getDescription().getVersion(),
                            1,
                            new String[]{"Antigravity"},
                            0L,
                            List.of("RZXPetsService"),
                            List.of("rzxpets", "pets", "petshop", "petstorage"),
                            List.of("PlaceholderAPI", "WorldGuard", "ExcellentEconomy", "LuckPerms"),
                            me.rzx.core.lifecycle.PluginState.ENABLED,
                            new String[]{"RZXCore"}
                        );
                        pluginService.registerPlugin(record);
                    }
                    
                    me.rzx.core.api.RZXCoreAPI.getBus().publish(
                        new me.rzx.core.event.lifecycle.PluginLoadedEvent(getName(), getDescription().getVersion(), 1, 0L)
                    );
                    
                    RZXLoggerService.success("CONNECTED & REGISTERED: RZXPets is live on RZXCore central hub!");
                }
            } catch (Throwable t) {
                RZXLoggerService.warning("RZXCore Registration Notice: " + t.getMessage());
            }

            RZXLoggerService.success("RZXPets initialized successfully under RZXCore specifications!");
        });
    }

    public void registerCommands() {
        registerCommandSafely("rzxpets", this);
        registerCommandSafely("pets", this);
        registerCommandSafely("petshop", this);
        registerCommandSafely("petstorage", this);
    }

    private void registerCommandSafely(String name, CommandExecutor executor) {
        try {
            org.bukkit.command.PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(executor);
            }
        } catch (Exception ignored) {}
    }

    public void startTasks() {
        if (tasksStarted || !isEnabled()) return;
        tasksStarted = true;

        if (petManager != null) {
            petManager.startTasks();
        }
        startPlaytimeTask();
    }

    @Override
    public void reload() {
        RZXPerformanceTracker.track("RZXPets Reload Sequence", () -> {
            reloadConfig();
            updateConfig("config.yml", new File(getDataFolder(), "config.yml"));
            loadHooksConfig();
            loadCustomMechanics();
            loadPets();
            loadShopConfigs();
            RZXAuditService.log("Console/Admin", "Executed RZXPets configuration reload.");
            RZXLoggerService.success("RZXPets configuration and shop caches reloaded in RAM.");
            RZXBus.publish(this);
        });
    }

    public void onDashboardReload() {
        reload();
    }

    @Override
    public void shutdown() {
        RZXPerformanceTracker.track("RZXPets Shutdown Sequence", () -> {
            if (petManager != null) {
                petManager.despawnAll();
            }

            if (dataManager != null) {
                if (playerDataMap != null) {
                    for (PlayerData data : playerDataMap.values()) {
                        dataManager.savePlayerData(data);
                    }
                }
                dataManager.closeConnection();
            }
            if (playerDataMap != null) {
                playerDataMap.clear();
            }

            RZXLoggerService.info("RZXPets disabled cleanly.");
        });
    }

    public PlayerData getPlayerData(UUID uuid) {
        if (!playerDataMap.containsKey(uuid)) {
            PlayerData data = dataManager.loadPlayerData(uuid);
            playerDataMap.put(uuid, data);
        }
        return playerDataMap.get(uuid);
    }

    public void savePlayerData(UUID uuid) {
        if (playerDataMap.containsKey(uuid)) {
            dataManager.savePlayerData(playerDataMap.get(uuid));
        }
    }

    public void unloadPlayerData(UUID uuid) {
        if (playerDataMap.containsKey(uuid)) {
            dataManager.savePlayerData(playerDataMap.get(uuid));
            playerDataMap.remove(uuid);
        }
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public PetManager getPetManager() {
        return petManager;
    }

    public ShopGui getShopGui() {
        return shopGui;
    }

    public void updateConfig(String resourceName, File targetFile) {
        if (!targetFile.exists()) {
            saveResource(resourceName, false);
            return;
        }

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(targetFile);
        InputStream resourceStream = getResource(resourceName);
        if (resourceStream == null) return;

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
        boolean modified = false;

        for (String key : defaultConfig.getKeys(true)) {
            if (!currentConfig.contains(key)) {
                currentConfig.set(key, defaultConfig.get(key));
                modified = true;
            }
        }

        if (modified) {
            try {
                currentConfig.save(targetFile);
                RZXLoggerService.info("Updated " + targetFile.getName() + " with missing configuration keys.");
            } catch (IOException e) {
                RZXLoggerService.error("Failed to auto-update " + targetFile.getName(), e);
            }
        }
    }

    public void loadCustomMechanics() {
        customMechanics = new HashMap<>();
        File mechanicsFolder = new File(getDataFolder(), "mechanics");
        if (!mechanicsFolder.exists()) {
            mechanicsFolder.mkdirs();
        }
        
        String[] defaults = {
            "health.yml", "flight.yml", "mining_gems.yml", "combat_gems.yml",
            "xp_booster.yml", "shield.yml", "lifesteal.yml", "double_drops.yml",
            "combat_attack.yml", "area_heal.yml"
        };
        for (String file : defaults) {
            File f = new File(mechanicsFolder, file);
            if (!f.exists()) {
                saveResource("mechanics/" + file, false);
            }
        }
        
        File[] files = mechanicsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().replace(".yml", "").replace(".yaml", "").toLowerCase();
                try {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    customMechanics.put(id, config);
                } catch (Exception e) {
                    RZXLoggerService.warning("Failed to load custom mechanic configuration: " + file.getName());
                }
            }
        }
    }

    private void loadShopConfigs() {
        File shopFolder = new File(getDataFolder(), "shop/sections");
        if (!shopFolder.exists()) {
            shopFolder.mkdirs();
        }
        
        String[] defaults = {"main.yml", "shop.yml", "upgrade.yml", "example_section.yml"};
        for (String file : defaults) {
            File f = new File(shopFolder, file);
            if (!f.exists()) {
                saveResource("shop/sections/" + file, false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void loadPets() {
        PetType.clear();

        File petsFolder = new File(getDataFolder(), "pets");
        if (!petsFolder.exists()) {
            petsFolder.mkdirs();
        }

        String[] defaults = {"example_pet.yml", "majestic_bat.yml", "swift_parrot.yml", "farmer_allay.yml", "wealthy_blaze.yml", "legendary_dragon.yml"};
        for (String file : defaults) {
            File f = new File(petsFolder, file);
            if (!f.exists()) {
                saveResource("pets/" + file, false);
            }
        }

        File[] petFiles = petsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (petFiles != null) {
            for (File file : petFiles) {
                String name = file.getName();
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                boolean enabled = config.getBoolean("enabled", true);
                if (!enabled) {
                    continue;
                }
                String displayName = config.getString("name", file.getName().replace(".yml", ""));
                String entityTypeStr = config.getString("entity-type", "CHICKEN");
                int customModelData = config.getInt("custom-model-data", 0);

                org.bukkit.entity.EntityType entityType;
                try {
                    entityType = org.bukkit.entity.EntityType.valueOf(entityTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    RZXLoggerService.warning("Invalid EntityType '" + entityTypeStr + "' in pet config: " + name + ". Defaulting to CHICKEN.");
                    entityType = org.bukkit.entity.EntityType.CHICKEN;
                }

                List<PetMechanic> mechanics = new java.util.ArrayList<>();
                if (config.isList("mechanics")) {
                    for (Map<?, ?> rawMechanic : config.getMapList("mechanics")) {
                        try {
                            parseAndAddMechanic(rawMechanic, mechanics, name);
                        } catch (Exception e) {
                            RZXLoggerService.warning("Failed to parse a mechanic in pet config: " + name + ". Error: " + e.getMessage());
                        }
                    }
                }

                String variant = config.getString("variant");
                List<String> description = config.getStringList("description");
                String id = file.getName().replace(".yml", "").replace(".yaml", "").toLowerCase();
                PetType.register(new PetType(id, displayName, entityType, customModelData, mechanics, variant, description));
                RZXLoggerService.info("Loaded pet companion configuration: '" + id + "' with " + mechanics.size() + " mechanics.");
            }
        }
    }

    private void parseAndAddMechanic(Map<?, ?> rawMechanic, List<PetMechanic> mechanics, String petFileName) {
        String type = (String) rawMechanic.get("type");
        int minLevel = rawMechanic.containsKey("min-level") ? ((Number) rawMechanic.get("min-level")).intValue() : 1;
        String mName = (String) rawMechanic.get("name");
        List<String> mDesc = (List<String>) rawMechanic.get("description");

        String customMsg = (String) rawMechanic.get("message");
        String customCmd = (String) rawMechanic.get("command");

        PetMechanic mechanic = null;

        if ("potion".equalsIgnoreCase(type)) {
            String effectStr = (String) rawMechanic.get("effect");
            if (effectStr == null) return;
            org.bukkit.potion.PotionEffectType effect = org.bukkit.potion.PotionEffectType.getByName(effectStr.toUpperCase());
            int amp = rawMechanic.containsKey("amplifier") ? ((Number) rawMechanic.get("amplifier")).intValue() : 0;

            java.util.Map<Integer, Integer> progressiveAmps = new java.util.TreeMap<>();
            if (rawMechanic.containsKey("progressive-levels")) {
                java.util.Map<?, ?> progMap = (java.util.Map<?, ?>) rawMechanic.get("progressive-levels");
                for (java.util.Map.Entry<?, ?> entry : progMap.entrySet()) {
                    int lvl = Integer.parseInt(entry.getKey().toString());
                    java.util.Map<?, ?> lvlData = (java.util.Map<?, ?>) entry.getValue();
                    int progAmp = lvlData.containsKey("amplifier") ? ((Number) lvlData.get("amplifier")).intValue() : amp;
                    progressiveAmps.put(lvl, progAmp);
                }
            }

            if (effect != null) {
                mechanic = new PetMechanic.PotionMechanic(minLevel, mName, mDesc, effect, amp, progressiveAmps);
            } else {
                RZXLoggerService.warning("Invalid PotionEffectType '" + effectStr + "' in pet config: " + petFileName);
            }
        } else if ("flight".equalsIgnoreCase(type)) {
            mechanic = new PetMechanic.FlightMechanic(minLevel, mName, mDesc);
        } else if ("command-trigger".equalsIgnoreCase(type)) {
            String trigger = (String) rawMechanic.get("trigger");
            double chance = rawMechanic.containsKey("chance") ? ((Number) rawMechanic.get("chance")).doubleValue() : 1.0;
            double levelModifier = rawMechanic.containsKey("level-modifier") ? ((Number) rawMechanic.get("level-modifier")).doubleValue() : 0.0;
            String cmd = (String) rawMechanic.get("command");
            String msg = (String) rawMechanic.get("message");
            List<String> targets = (List<String>) rawMechanic.get("blocks");
            if (targets == null) {
                targets = (List<String>) rawMechanic.get("mobs");
            }

            double basePercent = rawMechanic.containsKey("base-percent") ? ((Number) rawMechanic.get("base-percent")).doubleValue() : 0.0;
            double percentLevelModifier = rawMechanic.containsKey("percent-level-modifier") ? ((Number) rawMechanic.get("percent-level-modifier")).doubleValue() : 0.0;
            double baseAdditional = rawMechanic.containsKey("base-additional") ? ((Number) rawMechanic.get("base-additional")).doubleValue() : 0.0;
            double additionalLevelModifier = rawMechanic.containsKey("additional-level-modifier") ? ((Number) rawMechanic.get("additional-level-modifier")).doubleValue() : 0.0;

            double baseMultiplier = 0.0;
            if (rawMechanic.containsKey("base-multiplier")) {
                baseMultiplier = ((Number) rawMechanic.get("base-multiplier")).doubleValue();
            } else if (rawMechanic.containsKey("base-multiply")) {
                baseMultiplier = ((Number) rawMechanic.get("base-multiply")).doubleValue();
            }
            double multiplierLevelModifier = 0.0;
            if (rawMechanic.containsKey("multiplier-level-modifier")) {
                multiplierLevelModifier = ((Number) rawMechanic.get("multiplier-level-modifier")).doubleValue();
            } else if (rawMechanic.containsKey("multiply-level-modifier")) {
                multiplierLevelModifier = ((Number) rawMechanic.get("multiply-level-modifier")).doubleValue();
            }

            java.util.Map<Integer, PetMechanic.ProgressiveTriggerReward> progressiveRewards = new java.util.TreeMap<>();
            if (rawMechanic.containsKey("progressive-levels")) {
                java.util.Map<?, ?> progMap = (java.util.Map<?, ?>) rawMechanic.get("progressive-levels");
                for (java.util.Map.Entry<?, ?> entry : progMap.entrySet()) {
                    int lvl = Integer.parseInt(entry.getKey().toString());
                    java.util.Map<?, ?> lvlData = (java.util.Map<?, ?>) entry.getValue();

                    double progChance = lvlData.containsKey("chance") ? ((Number) lvlData.get("chance")).doubleValue() : chance;
                    String progCmd = lvlData.containsKey("command") ? (String) lvlData.get("command") : cmd;
                    String progMsg = lvlData.containsKey("message") ? (String) lvlData.get("message") : msg;

                    progressiveRewards.put(lvl, new PetMechanic.ProgressiveTriggerReward(progChance, progCmd, progMsg));
                }
            }

            mechanic = new PetMechanic.CommandTriggerMechanic(minLevel, mName, mDesc, trigger, chance, levelModifier, cmd, msg, targets, progressiveRewards, basePercent, percentLevelModifier, baseAdditional, additionalLevelModifier, baseMultiplier, multiplierLevelModifier);
        } else if ("custom".equalsIgnoreCase(type)) {
            String mechanicId = (String) rawMechanic.get("mechanic-id");
            if (mechanicId != null) {
                YamlConfiguration mechConfig = customMechanics.get(mechanicId.toLowerCase());
                if (mechConfig != null) {
                    Map<String, Object> combinedMap = convertToMap(mechConfig);
                    for (Map.Entry<?, ?> entry : rawMechanic.entrySet()) {
                        String keyStr = entry.getKey().toString();
                        if ("type".equalsIgnoreCase(keyStr) || "mechanic-id".equalsIgnoreCase(keyStr)) {
                            continue;
                        }
                        Object val = entry.getValue();
                        if (val instanceof Map) {
                            combinedMap.put(keyStr, deepCopyMap((Map<?, ?>) val));
                        } else if (val instanceof ConfigurationSection) {
                            combinedMap.put(keyStr, convertToMap((ConfigurationSection) val));
                        } else {
                            combinedMap.put(keyStr, val);
                        }
                    }
                    parseAndAddMechanic(combinedMap, mechanics, petFileName);
                } else {
                    RZXLoggerService.warning("Custom mechanic '" + mechanicId + "' not found for pet config: " + petFileName);
                }
            }
            return;
        } else if ("mining-gems".equalsIgnoreCase(type)) {
            double chance = rawMechanic.containsKey("base-chance") ? ((Number) rawMechanic.get("base-chance")).doubleValue() : 0.05;
            double modifier = rawMechanic.containsKey("level-modifier") ? ((Number) rawMechanic.get("level-modifier")).doubleValue() : 0.003;
            int amount = rawMechanic.containsKey("base-amount") ? ((Number) rawMechanic.get("base-amount")).intValue() : 1;
            List<String> blocks = (List<String>) rawMechanic.get("blocks");
            mechanic = new PetMechanic.MiningGemsMechanic(minLevel, mName, mDesc, chance, modifier, amount, blocks);
        } else if ("combat-gems".equalsIgnoreCase(type)) {
            double chance = rawMechanic.containsKey("base-chance") ? ((Number) rawMechanic.get("base-chance")).doubleValue() : 0.05;
            double modifier = rawMechanic.containsKey("level-modifier") ? ((Number) rawMechanic.get("level-modifier")).doubleValue() : 0.003;
            int amount = rawMechanic.containsKey("base-amount") ? ((Number) rawMechanic.get("base-amount")).intValue() : 1;
            List<String> mobs = (List<String>) rawMechanic.get("mobs");
            mechanic = new PetMechanic.CombatGemsMechanic(minLevel, mName, mDesc, chance, modifier, amount, mobs);
        } else if ("xp-booster".equalsIgnoreCase(type)) {
            double multiplier = rawMechanic.containsKey("base-multiplier") ? ((Number) rawMechanic.get("base-multiplier")).doubleValue() : 1.0;
            double modifier = rawMechanic.containsKey("level-modifier") ? ((Number) rawMechanic.get("level-modifier")).doubleValue() : 0.01;
            mechanic = new PetMechanic.XpBoosterMechanic(minLevel, mName, mDesc, multiplier, modifier);
        } else if ("shield".equalsIgnoreCase(type)) {
            double chance = rawMechanic.containsKey("base-chance") ? ((Number) rawMechanic.get("base-chance")).doubleValue() : 0.05;
            double modifier = rawMechanic.containsKey("level-modifier") ? ((Number) rawMechanic.get("level-modifier")).doubleValue() : 0.002;
            double baseMitigation = rawMechanic.containsKey("base-mitigation") ? ((Number) rawMechanic.get("base-mitigation")).doubleValue() : 0.10;
            double mitigationModifier = rawMechanic.containsKey("mitigation-modifier") ? ((Number) rawMechanic.get("mitigation-modifier")).doubleValue() : 0.004;
            mechanic = new PetMechanic.ShieldMechanic(minLevel, mName, mDesc, chance, modifier, baseMitigation, mitigationModifier);
        } else if ("lifesteal".equalsIgnoreCase(type)) {
            double chance = rawMechanic.containsKey("base-chance") ? ((Number) rawMechanic.get("base-chance")).doubleValue() : 0.05;
            double modifier = rawMechanic.containsKey("level-modifier") ? ((Number) rawMechanic.get("level-modifier")).doubleValue() : 0.002;
            double basePercent = rawMechanic.containsKey("base-percent") ? ((Number) rawMechanic.get("base-percent")).doubleValue() : 0.05;
            double percentModifier = rawMechanic.containsKey("percent-modifier") ? ((Number) rawMechanic.get("percent-modifier")).doubleValue() : 0.0015;
            mechanic = new PetMechanic.LifestealMechanic(minLevel, mName, mDesc, chance, modifier, basePercent, percentModifier);
        } else if ("double-drops".equalsIgnoreCase(type)) {
            double chance = rawMechanic.containsKey("base-chance") ? ((Number) rawMechanic.get("base-chance")).doubleValue() : 0.05;
            double modifier = rawMechanic.containsKey("level-modifier") ? ((Number) rawMechanic.get("level-modifier")).doubleValue() : 0.0035;
            List<String> blocks = (List<String>) rawMechanic.get("blocks");
            mechanic = new PetMechanic.DoubleDropsMechanic(minLevel, mName, mDesc, chance, modifier, blocks);
        } else if ("combat-attack".equalsIgnoreCase(type)) {
            double baseDamage = rawMechanic.containsKey("base-damage") ? ((Number) rawMechanic.get("base-damage")).doubleValue() : 2.0;
            double damageModifier = rawMechanic.containsKey("damage-modifier") ? ((Number) rawMechanic.get("damage-modifier")).doubleValue() : 0.08;
            double baseCooldown = rawMechanic.containsKey("base-cooldown") ? ((Number) rawMechanic.get("base-cooldown")).doubleValue() : 15.0;
            double cooldownModifier = rawMechanic.containsKey("cooldown-modifier") ? ((Number) rawMechanic.get("cooldown-modifier")).doubleValue() : 0.1;
            String particle = (String) rawMechanic.get("particle");
            String sound = (String) rawMechanic.get("sound");
            mechanic = new PetMechanic.CombatAttackMechanic(minLevel, mName, mDesc, baseDamage, damageModifier, baseCooldown, cooldownModifier, particle, sound);
        } else if ("area-heal".equalsIgnoreCase(type)) {
            double baseHeal = rawMechanic.containsKey("base-heal") ? ((Number) rawMechanic.get("base-heal")).doubleValue() : 1.0;
            double healModifier = rawMechanic.containsKey("heal-modifier") ? ((Number) rawMechanic.get("heal-modifier")).doubleValue() : 0.04;
            double radius = rawMechanic.containsKey("radius") ? ((Number) rawMechanic.get("radius")).doubleValue() : 3.0;
            double baseCooldown = rawMechanic.containsKey("base-cooldown") ? ((Number) rawMechanic.get("base-cooldown")).doubleValue() : 30.0;
            double cooldownModifier = rawMechanic.containsKey("cooldown-modifier") ? ((Number) rawMechanic.get("cooldown-modifier")).doubleValue() : 0.15;
            mechanic = new PetMechanic.AreaHealMechanic(minLevel, mName, mDesc, baseHeal, healModifier, radius, baseCooldown, cooldownModifier);
        }

        if (mechanic != null) {
            mechanic.setCustomMessage(customMsg);
            mechanic.setCustomCommand(customCmd);
            if (mechanic instanceof PetMechanic.CommandTriggerMechanic) {
                PetMechanic.CommandTriggerMechanic ctm = (PetMechanic.CommandTriggerMechanic) mechanic;
                if (rawMechanic.containsKey("accumulate-count")) {
                    int acc = ((Number) rawMechanic.get("accumulate-count")).intValue();
                    ctm.setAccumulateCount(acc);
                } else {
                    if ("BLOCK_BREAK".equalsIgnoreCase(ctm.getTrigger()) && petFileName != null && petFileName.toLowerCase().contains("allay")) {
                        ctm.setAccumulateCount(50);
                    }
                }
            }
            mechanics.add(mechanic);
        }
    }

    private Map<String, Object> convertToMap(ConfigurationSection section) {
        Map<String, Object> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            Object val = section.get(key);
            if (val instanceof ConfigurationSection) {
                map.put(key, convertToMap((ConfigurationSection) val));
            } else if (val instanceof List) {
                List<?> list = (List<?>) val;
                List<Object> mappedList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof ConfigurationSection) {
                        mappedList.add(convertToMap((ConfigurationSection) item));
                    } else if (item instanceof Map) {
                        mappedList.add(deepCopyMap((Map<?, ?>) item));
                    } else {
                        mappedList.add(item);
                    }
                }
                map.put(key, mappedList);
            } else {
                map.put(key, val);
            }
        }
        return map;
    }

    private Map<String, Object> deepCopyMap(Map<?, ?> rawMap) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection) {
                copy.put(key, convertToMap((ConfigurationSection) value));
            } else if (value instanceof Map) {
                copy.put(key, deepCopyMap((Map<?, ?>) value));
            } else {
                copy.put(key, value);
            }
        }
        return copy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String cmdName = command.getName().toLowerCase();
            if (cmdName.equals("pets") || cmdName.equals("petshop") || cmdName.equals("petstorage")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can open the pet menus.");
                    return true;
                }

                Player player = (Player) sender;
                if (args.length >= 1 && args[0].equalsIgnoreCase("rename")) {
                    if (args.length < 2) {
                        player.sendMessage(PlaceholderHook.color("&cUsage: /pets rename <new_name>"));
                        return true;
                    }
                    
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        sb.append(args[i]).append(" ");
                    }
                    String newName = sb.toString().trim();
                    
                    if (newName.contains("&")) {
                        player.sendMessage(PlaceholderHook.color("&c&l[RZXPets] &cYour custom name cannot contain the '&' character!"));
                        return true;
                    }
                    
                    int maxLength = getConfig().getInt("renaming.max-length", 32);
                    if (newName.length() > maxLength) {
                        player.sendMessage(PlaceholderHook.color("&c&l[RZXPets] &cName cannot exceed " + maxLength + " characters!"));
                        return true;
                    }
                    
                    PlayerData data = getPlayerData(player.getUniqueId());
                    String activeId = data.getActivePet();
                    if (activeId == null) {
                        player.sendMessage(PlaceholderHook.color("&c&l[RZXPets] &cYou must have an active summoned pet to rename it!"));
                        return true;
                    }
                    
                    PetData petData = data.getPet(activeId);
                    if (petData != null) {
                        petData.setName(newName);
                        dataManager.savePlayerData(data);
                        
                        petManager.summonPet(player, activeId);
                        player.sendMessage(PlaceholderHook.color("&a&l[RZXPets] &aYour active pet has been renamed to: " + newName));
                        RZXAuditService.log(player.getName(), "Renamed active pet " + activeId + " to: " + newName);
                    }
                    return true;
                }

                if (cmdName.equals("petstorage")) {
                    shopGui.openStorageMenu(player);
                } else {
                    shopGui.openMenu(player, "main");
                }
                return true;
            }

            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            String sub = args[0].toLowerCase();

            if (sub.equals("reload")) {
                if (!sender.hasPermission("rzxpets.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                reload();
                sender.sendMessage("§aRZXPets configuration reloaded!");
                return true;
            }

            if ((sub.equals("give") || sub.equals("remove") || sub.equals("buy")) && args.length >= 3 && args[1].equalsIgnoreCase("all")) {
                if (!sender.hasPermission("rzxpets.admin")) {
                    sender.sendMessage("§cNo permission.");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage("§cPlayer " + args[2] + " is not online.");
                    return true;
                }
                
                UUID uuid = target.getUniqueId();
                PlayerData playerData = getPlayerData(uuid);
                
                if (sub.equals("remove")) {
                    petManager.despawnPet(target);
                    playerData.getPets().clear();
                    dataManager.savePlayerData(playerData);
                    sender.sendMessage(PlaceholderHook.color("&cRemoved all pet companions from " + target.getName() + "."));
                    target.sendMessage(PlaceholderHook.color("&cAll your pet companions have been removed."));
                    RZXAuditService.log(sender.getName(), "Removed ALL pets from player " + target.getName());
                } else {
                    for (PetType petType : PetType.values()) {
                        playerData.addPet(petType.getId());
                    }
                    dataManager.savePlayerData(playerData);
                    sender.sendMessage(PlaceholderHook.color("&aGranted all pet companions to " + target.getName() + "."));
                    target.sendMessage(PlaceholderHook.color("&aYou have received all pet companions! Open &b/pets&a to summon them."));
                    RZXAuditService.log(sender.getName(), "Granted ALL pets to player " + target.getName());
                }
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /rzxpets <" + sub + "> <player> [parameters]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage("§cPlayer " + args[1] + " is not online.");
                return true;
            }

            UUID uuid = target.getUniqueId();
            PlayerData playerData = getPlayerData(uuid);

            switch (sub) {
                case "rename": {
                    if (!sender.hasPermission("rzxpets.admin")) {
                        sender.sendMessage("§cNo permission.");
                        return true;
                    }
                    if (args.length < 4) {
                        sender.sendMessage(PlaceholderHook.color("&cUsage: /rzxpets rename <player> <pet_type> <new_name>"));
                        return true;
                    }
                    String typeId = args[2].toLowerCase();
                    if (PetType.getById(typeId) == null) {
                        sender.sendMessage(PlaceholderHook.color("&cInvalid pet type: " + args[2]));
                        return true;
                    }
                    if (!playerData.hasPet(typeId)) {
                        sender.sendMessage(PlaceholderHook.color("&c" + target.getName() + " does not own a " + typeId + " pet."));
                        return true;
                    }
                    
                    StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        sb.append(args[i]).append(" ");
                    }
                    String newName = sb.toString().trim();
                    
                    PetData petData = playerData.getPet(typeId);
                    if (petData != null) {
                        petData.setName(newName);
                        dataManager.savePlayerData(playerData);
                        
                        sender.sendMessage(PlaceholderHook.color("&aRenamed " + target.getName() + "'s " + typeId + " pet to: " + newName));
                        RZXAuditService.log(sender.getName(), "Admin renamed " + target.getName() + "'s pet (" + typeId + ") to: " + newName);
                        
                        if (typeId.equalsIgnoreCase(playerData.getActivePet())) {
                            petManager.summonPet(target, typeId);
                        }
                    }
                    break;
                }

                case "give":
                case "buy": {
                    if (!sender.hasPermission("rzxpets.admin")) {
                        sender.sendMessage("§cNo permission.");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(PlaceholderHook.color("&cUsage: /rzxpets give <player> <pet_type>"));
                        return true;
                    }
                    String typeId = args[2].toLowerCase();

                    if (typeId.equalsIgnoreCase("all")) {
                        for (PetType petType : PetType.values()) {
                            playerData.addPet(petType.getId());
                        }
                        dataManager.savePlayerData(playerData);
                        sender.sendMessage(PlaceholderHook.color("&aGranted all pet companions to " + target.getName() + "."));
                        target.sendMessage(PlaceholderHook.color("&aYou have received all pet companions! Open &b/pets&a to summon them."));
                        RZXAuditService.log(sender.getName(), "Granted ALL pets to player " + target.getName());
                        break;
                    }

                    PetType type = PetType.getById(typeId);
                    if (type == null) {
                        sender.sendMessage(PlaceholderHook.color("&cInvalid pet type: " + args[2]));
                        return true;
                    }

                    int limit = playerData.getMaxLimit(target);
                    if (playerData.getPets().size() >= limit && !playerData.hasPet(typeId)) {
                        sender.sendMessage(PlaceholderHook.color("&c" + target.getName() + " has reached their owned pets limit of " + limit + "!"));
                        target.sendMessage(PlaceholderHook.color("&cYou have reached your maximum owned pets limit of " + limit + "!"));
                        return true;
                    }

                    playerData.addPet(typeId);
                    dataManager.savePlayerData(playerData);
                    sender.sendMessage(PlaceholderHook.color("&aGranted " + type.getDisplayName() + " &apet to " + target.getName() + "."));
                    target.sendMessage(PlaceholderHook.color("&aYou have received a new pet: " + type.getDisplayName() + "&a! Open &b/pets&a to summon it."));
                    RZXAuditService.log(sender.getName(), "Granted pet " + typeId + " to player " + target.getName());
                    break;
                }

                case "remove": {
                    if (!sender.hasPermission("rzxpets.admin")) {
                        sender.sendMessage("§cNo permission.");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(PlaceholderHook.color("&cUsage: /rzxpets remove <player> <pet_type>"));
                        return true;
                    }
                    String typeId = args[2].toLowerCase();

                    if (typeId.equalsIgnoreCase("all")) {
                        petManager.despawnPet(target);
                        playerData.getPets().clear();
                        dataManager.savePlayerData(playerData);
                        sender.sendMessage(PlaceholderHook.color("&cRemoved all pet companions from " + target.getName() + "."));
                        target.sendMessage(PlaceholderHook.color("&cAll your pet companions have been removed."));
                        RZXAuditService.log(sender.getName(), "Removed ALL pets from player " + target.getName());
                        break;
                    }

                    if (!playerData.hasPet(typeId)) {
                        sender.sendMessage(PlaceholderHook.color("&c" + target.getName() + " does not own a " + typeId + " pet."));
                        return true;
                    }

                    if (typeId.equalsIgnoreCase(playerData.getActivePet())) {
                        petManager.despawnPet(target);
                    }

                    playerData.getPets().remove(typeId);
                    dataManager.savePlayerData(playerData);

                    PetType type = PetType.getById(typeId);
                    String displayName = type != null ? type.getDisplayName() : typeId;
                    sender.sendMessage(PlaceholderHook.color("&cRemoved " + displayName + " &cpet from " + target.getName() + "."));
                    target.sendMessage(PlaceholderHook.color("&cYour " + displayName + " &cpet was removed by an administrator."));
                    RZXAuditService.log(sender.getName(), "Removed pet " + typeId + " from player " + target.getName());
                    break;
                }

                case "addxp": {
                    if (!sender.hasPermission("rzxpets.admin")) {
                        sender.sendMessage(PlaceholderHook.color("&cNo permission."));
                        return true;
                    }
                    if (args.length < 4) {
                        sender.sendMessage(PlaceholderHook.color("&cUsage: /rzxpets addxp <player> <pet_type> <amount>"));
                        return true;
                    }
                    String typeId;
                    if (args[2].equalsIgnoreCase("active")) {
                        typeId = playerData.getActivePet();
                        if (typeId == null) {
                            sender.sendMessage(PlaceholderHook.color("&c" + target.getName() + " does not have an active pet summoned."));
                            return true;
                        }
                    } else {
                        typeId = args[2].toLowerCase();
                        if (PetType.getById(typeId) == null) {
                            sender.sendMessage(PlaceholderHook.color("&cInvalid pet type: " + args[2]));
                            return true;
                        }
                    }
                    int amount;
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(PlaceholderHook.color("&cAmount must be an integer."));
                        return true;
                    }

                    if (!playerData.hasPet(typeId)) {
                        sender.sendMessage(PlaceholderHook.color("&c" + target.getName() + " does not own a " + typeId + " pet."));
                        return true;
                    }

                    PetData petData = playerData.getPet(typeId);
                    int oldLevel = petData.getLevel();
                    petData.addXp(amount);
                    dataManager.savePlayerData(playerData);

                    PetType type = petData.getType();
                    String displayName = type != null ? type.getDisplayName() : typeId;
                    sender.sendMessage(PlaceholderHook.color("&aAdded " + amount + " XP to " + target.getName() + "'s " + displayName + "&a."));
                    RZXAuditService.log(sender.getName(), "Added " + amount + " XP to " + target.getName() + "'s pet (" + typeId + ")");
                    
                    if (playerData.getActivePet() != null && playerData.getActivePet().equalsIgnoreCase(typeId)) {
                        if (petData.getLevel() > oldLevel) {
                            target.sendMessage(PlaceholderHook.color("&aYour pet has leveled up! &7[Lvl " + oldLevel + " -> " + petData.getLevel() + "]"));
                        }
                        petManager.summonPet(target, typeId);
                    } else {
                        if (petData.getLevel() > oldLevel) {
                            target.sendMessage(PlaceholderHook.color("&aYour " + displayName + " &apet has leveled up! &7[Lvl " + oldLevel + " -> " + petData.getLevel() + "]"));
                        }
                    }
                    break;
                }

                case "summon": {
                    if (args.length < 3) {
                        sender.sendMessage(PlaceholderHook.color("&cUsage: /rzxpets summon <player> <pet_type>"));
                        return true;
                    }
                    String typeId = args[2].toLowerCase();
                    if (!playerData.hasPet(typeId)) {
                        sender.sendMessage(PlaceholderHook.color("&c" + target.getName() + " does not own a " + typeId + " pet."));
                        return true;
                    }
                    petManager.summonPet(target, typeId);
                    sender.sendMessage(PlaceholderHook.color("&aForce-summoned " + typeId + " pet for " + target.getName() + "."));
                    RZXAuditService.log(sender.getName(), "Force-summoned pet " + typeId + " for player " + target.getName());
                    break;
                }

                case "dismiss": {
                    petManager.despawnPet(target);
                    sender.sendMessage(PlaceholderHook.color("&aForce-dismissed active pet for " + target.getName() + "."));
                    RZXAuditService.log(sender.getName(), "Force-dismissed active pet for player " + target.getName());
                    break;
                }

                default:
                    sendHelp(sender);
                    break;
            }
        } catch (Exception e) {
            RZXExceptionHandler.handle("Command Execution /" + label, sender instanceof Player ? (Player) sender : null, args, e);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§l=== RZXPets Commands ===");
        sender.sendMessage("§b/rzxpets give <player> <type|all> §7- Adopt companion(s).");
        sender.sendMessage("§b/rzxpets give all <player> §7- Adopt all companions.");
        sender.sendMessage("§b/rzxpets remove <player> <type|all> §7- Revoke companion(s).");
        sender.sendMessage("§b/rzxpets remove all <player> §7- Revoke all companions.");
        sender.sendMessage("§b/rzxpets addxp <player> <type> <amount> §7- Grant XP.");
        sender.sendMessage("§b/rzxpets summon <player> <type> §7- Summon companion.");
        sender.sendMessage("§b/rzxpets dismiss <player> §7- Dismiss companion.");
        sender.sendMessage("§b/rzxpets reload §7- Reload configuration.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new java.util.ArrayList<>();
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("rzxpets") || cmdName.equals("rzxpet")) {
            if (!sender.hasPermission("rzxpets.admin")) {
                return completions;
            }

            if (args.length == 1) {
                String input = args[0].toLowerCase();
                List<String> subcommands = List.of("give", "remove", "addxp", "summon", "dismiss", "reload");
                for (String sub : subcommands) {
                    if (sub.startsWith(input)) {
                        completions.add(sub);
                    }
                }
            } else if (args.length == 2) {
                String input = args[1].toLowerCase();
                String sub = args[0].toLowerCase();
                if (sub.equals("give") || sub.equals("remove")) {
                    if ("all".startsWith(input)) {
                        completions.add("all");
                    }
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            } else if (args.length == 3) {
                String input = args[2].toLowerCase();
                String sub = args[0].toLowerCase();
                if (sub.equals("give") || sub.equals("remove") || sub.equals("summon") || sub.equals("addxp")) {
                    if (sub.equals("addxp") && "active".startsWith(input)) {
                        completions.add("active");
                    }
                    if (sub.equals("give") || sub.equals("remove")) {
                        if ("all".startsWith(input)) {
                            completions.add("all");
                        }
                    }
                    for (String petId : PetType.values().stream().map(PetType::getId).toList()) {
                        if (petId.startsWith(input)) {
                            completions.add(petId);
                        }
                    }
                    if (sub.equals("give") || sub.equals("remove")) {
                        if (args[1].equalsIgnoreCase("all")) {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.getName().toLowerCase().startsWith(input)) {
                                    completions.add(player.getName());
                                }
                            }
                        }
                    }
                }
            } else if (args.length == 4) {
                String input = args[3].toLowerCase();
                String sub = args[0].toLowerCase();
                if (sub.equals("addxp")) {
                    List<String> suggestions = List.of("1000", "5000", "10000");
                    for (String sug : suggestions) {
                        if (sug.startsWith(input)) {
                            completions.add(sug);
                        }
                    }
                }
            }
        } else if (cmdName.equals("pets") || cmdName.equals("pet")) {
            if (args.length == 1) {
                String input = args[0].toLowerCase();
                if ("rename".startsWith(input)) {
                    completions.add("rename");
                }
            }
        }

        return completions;
    }

    public boolean isPlayerInRegion(Player player, String regionName) {
        if (regionName == null || regionName.isEmpty()) return false;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return false;
        }
        try {
            com.sk89q.worldedit.util.Location loc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.getLocation());
            com.sk89q.worldguard.protection.regions.RegionQuery query = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldguard.protection.ApplicableRegionSet set = query.getApplicableRegions(loc);
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : set) {
                if (region.getId().equalsIgnoreCase(regionName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignored
        }
        return false;
    }

    private void startPlaytimeTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                boolean normalEnabled = getConfig().getBoolean("playtime-rewards.enabled", true);
                long normalIntervalMinutes = getConfig().getLong("playtime-rewards.interval-minutes", 20);
                long normalTargetSeconds = normalIntervalMinutes * 60;
                String normalCommand = getConfig().getString("playtime-rewards.command", "zgems give %player% 2 -s");
                String normalMessage = getConfig().getString("playtime-rewards.message", "&aYou received &22 zGems &afor 20 minutes of playtime!");

                boolean afkEnabled = getConfig().getBoolean("afk-arena-rewards.enabled", true);
                String afkRegion = getConfig().getString("afk-arena-rewards.region-name", "afk_arena");
                long afkIntervalMinutes = getConfig().getLong("afk-arena-rewards.interval-minutes", 40);
                long afkTargetSeconds = afkIntervalMinutes * 60;
                String afkCommand = getConfig().getString("afk-arena-rewards.command", "zgems give %player% 2 -s");
                String afkMessage = getConfig().getString("afk-arena-rewards.message", "&aYou received &22 zGems &afor staying in the AFK Arena!");

                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = getPlayerData(player.getUniqueId());
                    boolean inAfkArena = isPlayerInRegion(player, afkRegion);

                    if (inAfkArena) {
                        if (afkEnabled) {
                            data.setAfkPlaytimeSeconds(data.getAfkPlaytimeSeconds() + 1);
                            if (data.getAfkPlaytimeSeconds() >= afkTargetSeconds) {
                                data.setAfkPlaytimeSeconds(0);
                                if (afkCommand != null && !afkCommand.isEmpty()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), afkCommand.replace("%player%", player.getName()));
                                }
                                if (afkMessage != null && !afkMessage.isEmpty()) {
                                    player.sendMessage(PlaceholderHook.color(afkMessage));
                                }
                            }
                        }
                    } else {
                        if (normalEnabled) {
                            data.setPlaytimeSeconds(data.getPlaytimeSeconds() + 1);
                            if (data.getPlaytimeSeconds() >= normalTargetSeconds) {
                                data.setPlaytimeSeconds(0);
                                if (normalCommand != null && !normalCommand.isEmpty()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalCommand.replace("%player%", player.getName()));
                                }
                                if (normalMessage != null && !normalMessage.isEmpty()) {
                                    player.sendMessage(PlaceholderHook.color(normalMessage));
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(RZXPets.this, 20L, 20L);
    }

    public void loadHooksConfig() {
        hooksFile = new File(getDataFolder(), "hooks.yml");
        if (!hooksFile.exists()) {
            saveResource("hooks.yml", false);
        }
        hooksConfig = YamlConfiguration.loadConfiguration(hooksFile);
    }

    public FileConfiguration getHooksConfig() {
        if (hooksConfig == null) {
            loadHooksConfig();
        }
        return hooksConfig;
    }

    public boolean arePetsAllowedAt(Player player) {
        FileConfiguration hooks = getHooksConfig();
        if (hooks == null || !hooks.getBoolean("worldguard.enabled", true)) {
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return true;
        }

        try {
            com.sk89q.worldedit.util.Location loc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.getLocation());
            com.sk89q.worldguard.protection.regions.RegionQuery query = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldguard.protection.ApplicableRegionSet set = query.getApplicableRegions(loc);

            boolean inAnyRegion = false;
            boolean isAllowed = hooks.getBoolean("worldguard.default-allow-pets", true);

            List<String> blocked = hooks.getStringList("worldguard.blocked-regions");
            List<String> allowed = hooks.getStringList("worldguard.allowed-regions");

            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : set) {
                inAnyRegion = true;
                String id = region.getId().toLowerCase();

                for (String b : blocked) {
                    if (id.equalsIgnoreCase(b)) {
                        return false;
                    }
                }

                for (String a : allowed) {
                    if (id.equalsIgnoreCase(a)) {
                        return true;
                    }
                }
            }

            if (!inAnyRegion) {
                return hooks.getBoolean("worldguard.default-allow-pets", true);
            }

            return isAllowed;
        } catch (Exception e) {
            return true;
        }
    }
}
