package com.rzxpets;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

public abstract class PetMechanic {
    private final int minLevel;
    private final String name;
    private final List<String> description;
    private String customMessage;
    private String customCommand;

    public PetMechanic(int minLevel, String name, List<String> description) {
        this.minLevel = minLevel;
        this.name = name;
        this.description = description != null ? description : new java.util.ArrayList<>();
    }

    public String getCustomMessage() {
        return customMessage;
    }

    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }

    public String getCustomCommand() {
        return customCommand;
    }

    public void setCustomCommand(String customCommand) {
        this.customCommand = customCommand;
    }

    protected boolean handleCustomAction(Player player, java.util.Map<String, String> placeholders) {
        boolean customRun = false;
        if (customCommand != null && !customCommand.isEmpty()) {
            String cmd = customCommand;
            for (java.util.Map.Entry<String, String> entry : placeholders.entrySet()) {
                cmd = cmd.replace(entry.getKey(), entry.getValue());
            }
            cmd = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            customRun = true;
        }
        if (customMessage != null && !customMessage.isEmpty()) {
            String msg = customMessage;
            for (java.util.Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace(entry.getKey(), entry.getValue());
            }
            msg = msg.replace("%player%", player.getName());
            player.sendMessage(PlaceholderHook.setPlaceholders(player, msg));
            customRun = true;
        }
        return customRun;
    }

    public static boolean matchBlock(Material material, String target) {
        if (material == null || target == null) return false;
        String name = material.name();
        if (name.equalsIgnoreCase(target)) return true;
        
        // Deepslate matching
        if (name.equalsIgnoreCase("DEEPSLATE_" + target)) return true;
        if (target.toUpperCase().startsWith("DEEPSLATE_") && name.equalsIgnoreCase(target.substring(10))) return true;
        
        // General match logic (e.g. support ore suffix)
        if (target.equalsIgnoreCase("ORES") && name.endsWith("_ORE")) return true;
        
        return false;
    }

    public static boolean matchMob(EntityType entityType, String target) {
        if (entityType == null || target == null) return false;
        String name = entityType.name();
        if (name.equalsIgnoreCase(target)) return true;
        
        // Zombie sub-types
        if (target.equalsIgnoreCase("ZOMBIE")) {
            if (name.equalsIgnoreCase("HUSK") || name.equalsIgnoreCase("DROWNED") || name.equalsIgnoreCase("ZOMBIE_VILLAGER")) return true;
        }
        // Skeleton sub-types
        if (target.equalsIgnoreCase("SKELETON")) {
            if (name.equalsIgnoreCase("STRAY") || name.equalsIgnoreCase("WITHER_SKELETON")) return true;
        }
        
        return false;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description;
    }

    public abstract void onTick(Player player, PetData data);
    public abstract void onTrigger(Player player, PetData data, String triggerType, Object context);
    public abstract void onDismiss(Player player);

    // ==========================================
    // Progressive Trigger Reward Model
    // ==========================================
    public static class ProgressiveTriggerReward {
        private final double chance;
        private final String command;
        private final String message;

        public ProgressiveTriggerReward(double chance, String command, String message) {
            this.chance = chance;
            this.command = command;
            this.message = message;
        }

        public double getChance() {
            return chance;
        }

        public String getCommand() {
            return command;
        }

        public String getMessage() {
            return message;
        }
    }

    // ==========================================
    // Mechanic: Potion Effect
    // ==========================================
    public static class PotionMechanic extends PetMechanic {
        private final PotionEffectType effect;
        private final int amplifier;
        private final java.util.Map<Integer, Integer> progressiveAmps;

        public PotionMechanic(int minLevel, String name, List<String> description, PotionEffectType effect, int amplifier, java.util.Map<Integer, Integer> progressiveAmps) {
            super(minLevel, name != null ? name : "Potion Effect: " + effect.getName(), description);
            this.effect = effect;
            this.amplifier = amplifier;
            this.progressiveAmps = progressiveAmps;
        }

        public PotionEffectType getEffect() {
            return effect;
        }

        public int getAmplifier() {
            return amplifier;
        }

        public java.util.Map<Integer, Integer> getProgressiveAmps() {
            return progressiveAmps;
        }

        @Override
        public void onTick(Player player, PetData data) {
            int activeAmp = amplifier;
            for (java.util.Map.Entry<Integer, Integer> entry : progressiveAmps.entrySet()) {
                if (data.getLevel() >= entry.getKey()) {
                    activeAmp = entry.getValue();
                }
            }
            player.addPotionEffect(new PotionEffect(effect, 40, activeAmp, false, false, true));
        }

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {}

        @Override
        public void onDismiss(Player player) {
            player.removePotionEffect(effect);
        }
    }

    // ==========================================
    // Mechanic: Flight
    // ==========================================
    public static class FlightMechanic extends PetMechanic {
        public FlightMechanic(int minLevel, String name, List<String> description) {
            super(minLevel, name != null ? name : "Creative Flight", description);
        }

        @Override
        public void onTick(Player player, PetData data) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                return;
            }
            if (data.getLevel() >= getMinLevel()) {
                if (!player.getAllowFlight()) {
                    player.setAllowFlight(true);
                }
            }
        }

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {}

        @Override
        public void onDismiss(Player player) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                return;
            }
            if (player.hasPermission("rzxpets.admin") || player.hasPermission("rzxpets.bypass.flight")) {
                return;
            }
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    // ==========================================
    // Mechanic: Command Trigger
    // ==========================================
    public static class CommandTriggerMechanic extends PetMechanic {
        private final String trigger;
        private final double chance;
        private final double levelModifier;
        private final String command;
        private final String message;
        private final List<String> targetList;
        private final java.util.Map<Integer, ProgressiveTriggerReward> progressiveRewards;
        private final double basePercent;
        private final double percentLevelModifier;
        private final double baseAdditional;
        private final double additionalLevelModifier;
        private final double baseMultiplier;
        private final double multiplierLevelModifier;
        
        private int accumulateCount = 1;
        private final java.util.Map<UUID, Integer> playerCounts = new java.util.HashMap<>();
        private final java.util.Map<UUID, Double> playerAccumulators = new java.util.HashMap<>();

        public CommandTriggerMechanic(int minLevel, String name, List<String> description, String trigger, double chance, double levelModifier, String command, String message, List<String> targetList, java.util.Map<Integer, ProgressiveTriggerReward> progressiveRewards, double basePercent, double percentLevelModifier, double baseAdditional, double additionalLevelModifier, double baseMultiplier, double multiplierLevelModifier) {
            super(minLevel, name != null ? name : getDefaultName(trigger), description);
            this.trigger = trigger;
            this.chance = chance;
            this.levelModifier = levelModifier;
            this.command = command;
            this.message = message;
            this.targetList = targetList;
            this.progressiveRewards = progressiveRewards;
            this.basePercent = basePercent;
            this.percentLevelModifier = percentLevelModifier;
            this.baseAdditional = baseAdditional;
            this.additionalLevelModifier = additionalLevelModifier;
            this.baseMultiplier = baseMultiplier;
            this.multiplierLevelModifier = multiplierLevelModifier;
        }

        public int getAccumulateCount() {
            return accumulateCount;
        }

        public void setAccumulateCount(int count) {
            this.accumulateCount = count;
        }

        public double getChance() { return chance; }
        public double getLevelModifier() { return levelModifier; }
        public java.util.Map<Integer, ProgressiveTriggerReward> getProgressiveRewards() { return progressiveRewards; }
        public double getBasePercent() { return basePercent; }
        public double getPercentLevelModifier() { return percentLevelModifier; }
        public double getBaseAdditional() { return baseAdditional; }
        public double getAdditionalLevelModifier() { return additionalLevelModifier; }
        public double getBaseMultiplier() { return baseMultiplier; }
        public double getMultiplierLevelModifier() { return multiplierLevelModifier; }

        private static String getDefaultName(String trigger) {
            if ("BLOCK_BREAK".equalsIgnoreCase(trigger)) return "Mining Booster";
            if ("MOB_KILL".equalsIgnoreCase(trigger)) return "Combat Booster";
            if ("SUMMON".equalsIgnoreCase(trigger)) return "Summon Effect";
            if ("DISMISS".equalsIgnoreCase(trigger)) return "Dismiss Effect";
            return "Custom Booster";
        }

        public String getTrigger() {
            return trigger;
        }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {
            if (!this.trigger.equalsIgnoreCase(triggerType)) return;

            RZXPets.debug(2, "[Debug-Mechanics] CommandTriggerMechanic.onTrigger called for " + player.getName() + " with trigger " + triggerType);

            if ("BLOCK_BREAK".equalsIgnoreCase(triggerType) && context instanceof Material) {
                Material material = (Material) context;
                if (targetList != null && !targetList.isEmpty()) {
                    boolean matched = false;
                    for (String t : targetList) {
                        if (matchBlock(material, t)) {
                            matched = true;
                            break;
                        }
                    }
                    RZXPets.debug(2, "[Debug-Mechanics] Block match evaluation for " + material.name() + ": " + matched + " (TargetList: " + targetList + ")");
                    if (!matched) return;
                } else {
                    RZXPets.debug(2, "[Debug-Mechanics] Block match evaluation: targetList is empty/null, triggering automatically.");
                }
            }

            if ("MOB_KILL".equalsIgnoreCase(triggerType) && context instanceof EntityType) {
                EntityType entityType = (EntityType) context;
                if (targetList != null && !targetList.isEmpty()) {
                    boolean matched = false;
                    for (String t : targetList) {
                        if (matchMob(entityType, t)) {
                            matched = true;
                            break;
                        }
                    }
                    RZXPets.debug(2, "[Debug-Mechanics] Mob match evaluation for " + entityType.name() + ": " + matched + " (TargetList: " + targetList + ")");
                    if (!matched) return;
                } else {
                    RZXPets.debug(2, "[Debug-Mechanics] Mob match evaluation: targetList is empty/null, triggering automatically.");
                }
            }

            double baseCh = chance;
            String activeCmd = command;
            String activeMsg = message;

            for (java.util.Map.Entry<Integer, ProgressiveTriggerReward> entry : progressiveRewards.entrySet()) {
                if (data.getLevel() >= entry.getKey()) {
                    baseCh = entry.getValue().getChance();
                    activeCmd = entry.getValue().getCommand();
                    activeMsg = entry.getValue().getMessage();
                }
            }

            double activeChance = Math.min(1.0, baseCh + (data.getLevel() * levelModifier));
            double roll = Math.random();
            RZXPets.debug(2, "[Debug-Mechanics] Active chance for " + player.getName() + ": " + activeChance + " | Roll: " + roll);
            if (roll >= activeChance) {
                RZXPets.debug(2, "[Debug-Mechanics] Chance roll failed.");
                return;
            }

            double baseAdd = baseAdditional + (data.getLevel() * additionalLevelModifier);
            double pctVal = basePercent + (data.getLevel() * percentLevelModifier);
            double multVal = baseMultiplier + (data.getLevel() * multiplierLevelModifier);
            if (baseMultiplier == 0.0 && multiplierLevelModifier == 0.0) {
                multVal = 1.0;
            }
            double finalAdd = baseAdd * multVal;

            UUID uuid = player.getUniqueId();
            if (accumulateCount > 1) {
                int currentCount = playerCounts.getOrDefault(uuid, 0) + 1;
                double currentAcc = playerAccumulators.getOrDefault(uuid, 0.0) + finalAdd;
                
                if (currentCount < accumulateCount) {
                    playerCounts.put(uuid, currentCount);
                    playerAccumulators.put(uuid, currentAcc);
                    return; // Accumulating...
                }
                
                playerCounts.remove(uuid);
                playerAccumulators.remove(uuid);
                finalAdd = currentAcc;
            }

            String addStr = String.valueOf((int) Math.round(finalAdd));
            String pctStr = (pctVal == (int) pctVal) ? String.valueOf((int) pctVal) : String.format("%.2f", pctVal);
            String multStr = (multVal == (int) multVal) ? String.valueOf((int) multVal) : String.format("%.2f", multVal);
            String chanceStr = String.format("%.1f", activeChance * 100);
            String petName = data.getType() != null ? data.getType().getDisplayName() : "";
            String countStr = String.valueOf(accumulateCount > 1 ? accumulateCount : 1);

            if (activeCmd != null && !activeCmd.isEmpty()) {
                String parsedCommand = activeCmd
                    .replace("%player%", player.getName())
                    .replace("%name%", getName())
                    .replace("%pet_name%", petName)
                    .replace("%chance%", chanceStr)
                    .replace("%percent%", pctStr)
                    .replace("%additional%", addStr)
                    .replace("%multiplier%", multStr)
                    .replace("%count%", countStr)
                    .replace("%player_pet_level_percent%", pctStr)
                    .replace("%player_pet_level_additional%", addStr)
                    .replace("%player_pet_level_multiplier%", multStr)
                    .replace("%player_pet_level_multiply%", multStr);
                RZXPets.debug(2, "[Debug-Mechanics] Dispatching console command: " + parsedCommand);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
            }

            if (activeMsg != null && !activeMsg.isEmpty()) {
                String parsedMsg = activeMsg
                    .replace("%player%", player.getName())
                    .replace("%name%", getName())
                    .replace("%pet_name%", petName)
                    .replace("%chance%", chanceStr)
                    .replace("%percent%", pctStr)
                    .replace("%additional%", addStr)
                    .replace("%multiplier%", multStr)
                    .replace("%count%", countStr)
                    .replace("%player_pet_level_percent%", pctStr)
                    .replace("%player_pet_level_additional%", addStr)
                    .replace("%player_pet_level_multiplier%", multStr)
                    .replace("%player_pet_level_multiply%", multStr);
                RZXPets.debug(2, "[Debug-Mechanics] Sending player message: " + parsedMsg);
                player.sendMessage(PlaceholderHook.setPlaceholders(player, parsedMsg));
            }
        }

        public void flushAccumulator(Player player) {
            UUID uuid = player.getUniqueId();
            if (!playerCounts.containsKey(uuid)) return;
            
            int count = playerCounts.remove(uuid);
            double accumulated = playerAccumulators.remove(uuid);
            if (count <= 0 || accumulated <= 0.0) return;

            RZXPets plugin = RZXPets.getInstance();
            if (plugin == null) return;
            PlayerData data = plugin.getPlayerData(uuid);
            if (data == null) return;
            String activeId = data.getActivePet();
            if (activeId == null) return;
            PetData petData = data.getPet(activeId);
            if (petData == null) return;

            double baseCh = chance;
            String activeCmd = command;
            String activeMsg = message;

            for (java.util.Map.Entry<Integer, ProgressiveTriggerReward> entry : progressiveRewards.entrySet()) {
                if (petData.getLevel() >= entry.getKey()) {
                    baseCh = entry.getValue().getChance();
                    activeCmd = entry.getValue().getCommand();
                    activeMsg = entry.getValue().getMessage();
                }
            }

            double activeChance = Math.min(1.0, baseCh + (petData.getLevel() * levelModifier));
            double pctVal = basePercent + (petData.getLevel() * percentLevelModifier);
            
            String addStr = String.valueOf((int) Math.round(accumulated));
            String pctStr = (pctVal == (int) pctVal) ? String.valueOf((int) pctVal) : String.format("%.2f", pctVal);
            String chanceStr = String.format("%.1f", activeChance * 100);
            String petName = petData.getType() != null ? petData.getType().getDisplayName() : "";
            String countStr = String.valueOf(count);

            if (activeCmd != null && !activeCmd.isEmpty()) {
                String parsedCommand = activeCmd
                    .replace("%player%", player.getName())
                    .replace("%name%", getName())
                    .replace("%pet_name%", petName)
                    .replace("%chance%", chanceStr)
                    .replace("%percent%", pctStr)
                    .replace("%additional%", addStr)
                    .replace("%count%", countStr)
                    .replace("%player_pet_level_percent%", pctStr)
                    .replace("%player_pet_level_additional%", addStr);
                RZXPets.debug(2, "[Debug-Mechanics] Dispatching console command (Flush): " + parsedCommand);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
            }

            if (activeMsg != null && !activeMsg.isEmpty()) {
                String parsedMsg = activeMsg
                    .replace("%player%", player.getName())
                    .replace("%name%", getName())
                    .replace("%pet_name%", petName)
                    .replace("%chance%", chanceStr)
                    .replace("%percent%", pctStr)
                    .replace("%additional%", addStr)
                    .replace("%count%", countStr)
                    .replace("%player_pet_level_percent%", pctStr)
                    .replace("%player_pet_level_additional%", addStr);
                RZXPets.debug(2, "[Debug-Mechanics] Sending player message (Flush): " + parsedMsg);
                player.sendMessage(PlaceholderHook.setPlaceholders(player, parsedMsg));
            }
        }

        @Override
        public void onDismiss(Player player) {
            flushAccumulator(player);
        }
    }

    // ==========================================
    // Mechanic: Mining Gems
    // ==========================================
    public static class MiningGemsMechanic extends PetMechanic {
        private final double baseChance;
        private final double levelModifier;
        private final int baseAmount;
        private final List<String> blocks;

        public MiningGemsMechanic(int minLevel, String name, List<String> description, double baseChance, double levelModifier, int baseAmount, List<String> blocks) {
            super(minLevel, name != null ? name : "Mining Gems Booster", description);
            this.baseChance = baseChance;
            this.levelModifier = levelModifier;
            this.baseAmount = baseAmount;
            this.blocks = blocks;
        }

        public double getBaseChance() { return baseChance; }
        public double getLevelModifier() { return levelModifier; }
        public int getBaseAmount() { return baseAmount; }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {
            if (!"BLOCK_BREAK".equalsIgnoreCase(triggerType) || !(context instanceof Material)) return;
            Material material = (Material) context;

            if (blocks != null && !blocks.isEmpty()) {
                boolean matched = false;
                for (String b : blocks) {
                    if (matchBlock(material, b)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return;
            }

            double chance = Math.min(0.35, baseChance + (data.getLevel() * levelModifier));
            if (Math.random() < chance) {
                int amount = baseAmount + (data.getLevel() / 30);
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%chance%", String.format("%.1f", chance * 100));
                placeholders.put("%amount%", String.valueOf(amount));
                
                if (handleCustomAction(player, placeholders)) return;
                
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "zgems give " + player.getName() + " " + amount + " -s");
                player.sendMessage(PlaceholderHook.color(getName() + " &7» &fYour companion found &a" + amount + " zGems&f!"));
            }
        }

        @Override
        public void onDismiss(Player player) {}
    }

    // ==========================================
    // Mechanic: Combat Gems
    // ==========================================
    public static class CombatGemsMechanic extends PetMechanic {
        private final double baseChance;
        private final double levelModifier;
        private final int baseAmount;
        private final List<String> mobs;

        public CombatGemsMechanic(int minLevel, String name, List<String> description, double baseChance, double levelModifier, int baseAmount, List<String> mobs) {
            super(minLevel, name != null ? name : "Combat Gems Booster", description);
            this.baseChance = baseChance;
            this.levelModifier = levelModifier;
            this.baseAmount = baseAmount;
            this.mobs = mobs;
        }

        public double getBaseChance() { return baseChance; }
        public double getLevelModifier() { return levelModifier; }
        public int getBaseAmount() { return baseAmount; }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {
            if (!"MOB_KILL".equalsIgnoreCase(triggerType) || !(context instanceof EntityType)) return;
            EntityType entityType = (EntityType) context;

            if (mobs != null && !mobs.isEmpty()) {
                boolean matched = false;
                for (String m : mobs) {
                    if (matchMob(entityType, m)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return;
            }

            double chance = Math.min(0.35, baseChance + (data.getLevel() * levelModifier));
            if (Math.random() < chance) {
                int amount = baseAmount + (data.getLevel() / 30);
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%chance%", String.format("%.1f", chance * 100));
                placeholders.put("%amount%", String.valueOf(amount));
                
                if (handleCustomAction(player, placeholders)) return;
                
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "zgems give " + player.getName() + " " + amount + " -s");
                player.sendMessage(PlaceholderHook.color(getName() + " &7» &fYour companion found &a" + amount + " zGems&f!"));
            }
        }

        @Override
        public void onDismiss(Player player) {}
    }

    // ==========================================
    // Mechanic: XP Booster
    // ==========================================
    public static class XpBoosterMechanic extends PetMechanic {
        private final double baseMultiplier;
        private final double levelModifier;
        private final java.util.Map<UUID, Long> lastMessageTime = new java.util.HashMap<>();

        public XpBoosterMechanic(int minLevel, String name, List<String> description, double baseMultiplier, double levelModifier) {
            super(minLevel, name != null ? name : "XP Booster", description);
            this.baseMultiplier = baseMultiplier;
            this.levelModifier = levelModifier;
        }

        public double getBaseMultiplier() { return baseMultiplier; }
        public double getLevelModifier() { return levelModifier; }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {
            if (!"XP_GAIN".equalsIgnoreCase(triggerType) || !(context instanceof int[])) return;
            int[] xpArr = (int[]) context;
            double mult = baseMultiplier + (data.getLevel() * levelModifier);
            int originalXp = xpArr[0];
            xpArr[0] = (int) Math.round(xpArr[0] * mult);

            if (xpArr[0] > originalXp) {
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%multiplier%", String.format("%.0f", (mult - 1.0) * 100));
                
                if (handleCustomAction(player, placeholders)) return;

                long now = System.currentTimeMillis();
                long lastMsg = lastMessageTime.getOrDefault(player.getUniqueId(), 0L);
                if (now - lastMsg >= 5000L) { // 5 second rate limit
                    lastMessageTime.put(player.getUniqueId(), now);
                    player.sendMessage(PlaceholderHook.color(getName() + " &7» &fYour companion boosted your XP gain by &a" + String.format("%.0f", (mult - 1.0) * 100) + "%&f!"));
                }
            }
        }

        @Override
        public void onDismiss(Player player) {
            lastMessageTime.remove(player.getUniqueId());
        }
    }

    // ==========================================
    // Mechanic: Shield Mitigation
    // ==========================================
    public static class ShieldMechanic extends PetMechanic {
        private final double baseChance;
        private final double levelModifier;
        private final double baseMitigation;
        private final double mitigationModifier;

        public ShieldMechanic(int minLevel, String name, List<String> description, double baseChance, double levelModifier, double baseMitigation, double mitigationModifier) {
            super(minLevel, name != null ? name : "Mitigation Shield", description);
            this.baseChance = baseChance;
            this.levelModifier = levelModifier;
            this.baseMitigation = baseMitigation;
            this.mitigationModifier = mitigationModifier;
        }

        public double getBaseChance() { return baseChance; }
        public double getLevelModifier() { return levelModifier; }
        public double getBaseMitigation() { return baseMitigation; }
        public double getMitigationModifier() { return mitigationModifier; }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {
            if (!"TAKE_DAMAGE".equalsIgnoreCase(triggerType) || !(context instanceof double[])) return;
            double[] damageArr = (double[]) context;

            double chance = Math.min(0.25, baseChance + (data.getLevel() * levelModifier));
            if (Math.random() < chance) {
                double pct = Math.min(0.50, baseMitigation + (data.getLevel() * mitigationModifier));
                damageArr[0] = damageArr[0] * (1.0 - pct);
                
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%chance%", String.format("%.1f", chance * 100));
                placeholders.put("%mitigation%", String.format("%.0f", pct * 100));
                
                if (handleCustomAction(player, placeholders)) return;

                player.getWorld().spawnParticle(org.bukkit.Particle.valueOf("BARRIER"), player.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.0);
                player.sendMessage(PlaceholderHook.color(getName() + " &7» &fYour companion blocked &a" + (int)(pct * 100) + "% &fof incoming damage!"));
            }
        }

        @Override
        public void onDismiss(Player player) {}
    }

    // ==========================================
    // Mechanic: Lifesteal
    // ==========================================
    public static class LifestealMechanic extends PetMechanic {
        private final double baseChance;
        private final double levelModifier;
        private final double basePercent;
        private final double percentModifier;
        private final java.util.Map<UUID, Long> lastMessageTime = new java.util.HashMap<>();

        public LifestealMechanic(int minLevel, String name, List<String> description, double baseChance, double levelModifier, double basePercent, double percentModifier) {
            super(minLevel, name != null ? name : "Vampiric Lifesteal", description);
            this.baseChance = baseChance;
            this.levelModifier = levelModifier;
            this.basePercent = basePercent;
            this.percentModifier = percentModifier;
        }

        public double getBaseChance() { return baseChance; }
        public double getLevelModifier() { return levelModifier; }
        public double getBasePercent() { return basePercent; }
        public double getPercentModifier() { return percentModifier; }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {
            if (!"DEAL_DAMAGE".equalsIgnoreCase(triggerType) || !(context instanceof double[])) return;
            double damageDealt = ((double[]) context)[0];

            double chance = Math.min(0.25, baseChance + (data.getLevel() * levelModifier));
            if (Math.random() < chance) {
                double pct = Math.min(0.20, basePercent + (data.getLevel() * percentModifier));
                double healAmount = damageDealt * pct;
                double newHealth = Math.min(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue(), player.getHealth() + healAmount);
                player.setHealth(newHealth);
                player.getWorld().spawnParticle(org.bukkit.Particle.valueOf("HEART"), player.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0.0);

                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%chance%", String.format("%.1f", chance * 100));
                placeholders.put("%percent%", String.format("%.0f", pct * 100));

                if (handleCustomAction(player, placeholders)) return;

                long now = System.currentTimeMillis();
                long lastMsg = lastMessageTime.getOrDefault(player.getUniqueId(), 0L);
                if (now - lastMsg >= 3000L) { // 3 second rate limit
                    lastMessageTime.put(player.getUniqueId(), now);
                    player.sendMessage(PlaceholderHook.color(getName() + " &7» &fYour companion healed you for &a" + String.format("%.1f", healAmount) + " HP&f!"));
                }
            }
        }

        @Override
        public void onDismiss(Player player) {
            lastMessageTime.remove(player.getUniqueId());
        }
    }

    // ==========================================
    // Mechanic: Double Drops
    // ==========================================
    public static class DoubleDropsMechanic extends PetMechanic {
        private final double baseChance;
        private final double levelModifier;
        private final List<String> blocks;
        private final java.util.Map<UUID, Long> lastMessageTime = new java.util.HashMap<>();

        public DoubleDropsMechanic(int minLevel, String name, List<String> description, double baseChance, double levelModifier, List<String> blocks) {
            super(minLevel, name != null ? name : "Double Harvest", description);
            this.baseChance = baseChance;
            this.levelModifier = levelModifier;
            this.blocks = blocks;
        }

        public double getBaseChance() { return baseChance; }
        public double getLevelModifier() { return levelModifier; }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {
            if (!"DOUBLE_DROPS".equalsIgnoreCase(triggerType) || !(context instanceof org.bukkit.event.block.BlockBreakEvent)) return;
            org.bukkit.event.block.BlockBreakEvent event = (org.bukkit.event.block.BlockBreakEvent) context;
            Material material = event.getBlock().getType();

            if (blocks != null && !blocks.isEmpty()) {
                boolean matched = false;
                for (String b : blocks) {
                    if (matchBlock(material, b)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return;
            }

            double chance = Math.min(0.40, baseChance + (data.getLevel() * levelModifier));
            if (Math.random() < chance) {
                // Drop items again
                for (ItemStack drop : event.getBlock().getDrops(player.getInventory().getItemInMainHand())) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
                }
                player.getWorld().spawnParticle(org.bukkit.Particle.valueOf("VILLAGER_HAPPY"), event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.0);

                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%chance%", String.format("%.1f", chance * 100));

                if (handleCustomAction(player, placeholders)) return;

                long now = System.currentTimeMillis();
                long lastMsg = lastMessageTime.getOrDefault(player.getUniqueId(), 0L);
                if (now - lastMsg >= 2000L) { // 2 second rate limit
                    lastMessageTime.put(player.getUniqueId(), now);
                    player.sendMessage(PlaceholderHook.color(getName() + " &7» &fYour companion doubled the block drops!"));
                }
            }
        }

        @Override
        public void onDismiss(Player player) {
            lastMessageTime.remove(player.getUniqueId());
        }
    }

    // ==========================================
    // Mechanic: Combat Attack
    // ==========================================
    public static class CombatAttackMechanic extends PetMechanic {
        private final double baseDamage;
        private final double damageModifier;
        private final double baseCooldown;
        private final double cooldownModifier;
        private final String particle;
        private final String sound;

        public CombatAttackMechanic(int minLevel, String name, List<String> description, double baseDamage, double damageModifier, double baseCooldown, double cooldownModifier, String particle, String sound) {
            super(minLevel, name != null ? name : "Companion Attack", description);
            this.baseDamage = baseDamage;
            this.damageModifier = damageModifier;
            this.baseCooldown = baseCooldown;
            this.cooldownModifier = cooldownModifier;
            this.particle = particle != null ? particle : "CRIT";
            this.sound = sound != null ? sound : "ENTITY_GENERIC_ATTACK";
        }

        public double getBaseDamage() { return baseDamage; }
        public double getDamageModifier() { return damageModifier; }
        public double getBaseCooldown() { return baseCooldown; }
        public double getCooldownModifier() { return cooldownModifier; }

        public double getDamage(int level) {
            return baseDamage + (level * damageModifier);
        }

        public int getCooldownTicks(int level) {
            double seconds = Math.max(5.0, baseCooldown - (level * cooldownModifier));
            return (int) (seconds * 20);
        }

        public String getParticle() {
            return particle;
        }

        public String getSound() {
            return sound;
        }

        @Override
        public void onTick(Player player, PetData data) {}

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {}

        @Override
        public void onDismiss(Player player) {}
    }

    // ==========================================
    // Mechanic: Area Heal / Regeneration
    // ==========================================
    public static class AreaHealMechanic extends PetMechanic {
        private final double baseHeal;
        private final double healModifier;
        private final double radius;
        private final double baseCooldown;
        private final double cooldownModifier;
        private final java.util.Map<UUID, Long> lastHealTime = new java.util.HashMap<>();

        public AreaHealMechanic(int minLevel, String name, List<String> description, double baseHeal, double healModifier, double radius, double baseCooldown, double cooldownModifier) {
            super(minLevel, name != null ? name : "Area Rejuvenation", description);
            this.baseHeal = baseHeal;
            this.healModifier = healModifier;
            this.radius = radius;
            this.baseCooldown = baseCooldown;
            this.cooldownModifier = cooldownModifier;
        }

        public double getBaseHeal() { return baseHeal; }
        public double getHealModifier() { return healModifier; }
        public double getRadius() { return radius; }
        public double getBaseCooldown() { return baseCooldown; }
        public double getCooldownModifier() { return cooldownModifier; }

        @Override
        public void onTick(Player player, PetData data) {
            long now = System.currentTimeMillis();
            long cooldownMs = (long) (Math.max(15.0, baseCooldown - (data.getLevel() * cooldownModifier)) * 1000);
            long last = lastHealTime.getOrDefault(player.getUniqueId(), 0L);
            if (now - last >= cooldownMs) {
                lastHealTime.put(player.getUniqueId(), now);
                
                double amount = baseHeal + (data.getLevel() * healModifier);
                double rad = radius + (data.getLevel() * 0.03);
                double cooldownSec = Math.max(15.0, baseCooldown - (data.getLevel() * cooldownModifier));

                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%heal%", String.format("%.1f", amount));
                placeholders.put("%radius%", String.format("%.1f", rad));
                placeholders.put("%cooldown%", String.format("%.1f", cooldownSec));

                if (handleCustomAction(player, placeholders)) {
                    // Heal player and allies silently
                    healEntity(player, amount);
                    player.getWorld().spawnParticle(org.bukkit.Particle.valueOf("HEART"), player.getLocation().add(0, 1.0, 0), 4, 0.3, 0.3, 0.3, 0.05);
                    for (Entity entity : player.getNearbyEntities(rad, rad, rad)) {
                        if (entity instanceof Player) {
                            Player p = (Player) entity;
                            healEntity(p, amount);
                            p.getWorld().spawnParticle(org.bukkit.Particle.valueOf("HEART"), p.getLocation().add(0, 1.0, 0), 4, 0.3, 0.3, 0.3, 0.05);
                        }
                    }
                    return;
                }
                
                // Heal player
                healEntity(player, amount);
                player.getWorld().spawnParticle(org.bukkit.Particle.valueOf("HEART"), player.getLocation().add(0, 1.0, 0), 4, 0.3, 0.3, 0.3, 0.05);
                player.sendMessage(PlaceholderHook.color(getName() + " &7» &fYour companion healed you for &a" + String.format("%.1f", amount) + " HP!"));
                
                // Heal nearby allies/players
                for (Entity entity : player.getNearbyEntities(rad, rad, rad)) {
                    if (entity instanceof Player) {
                        Player p = (Player) entity;
                        healEntity(p, amount);
                        p.getWorld().spawnParticle(org.bukkit.Particle.valueOf("HEART"), p.getLocation().add(0, 1.0, 0), 4, 0.3, 0.3, 0.3, 0.05);
                        p.sendMessage(PlaceholderHook.color(getName() + " &7» &fYou were healed for &a" + String.format("%.1f", amount) + " HP &fby " + player.getName() + "'s companion!"));
                    }
                }
            }
        }

        private void healEntity(Player p, double amount) {
            double max = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + amount));
        }

        @Override
        public void onTrigger(Player player, PetData data, String triggerType, Object context) {}

        @Override
        public void onDismiss(Player player) {}
    }
}
