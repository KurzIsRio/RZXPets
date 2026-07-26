package com.rzxpets.rzx;

import org.bukkit.Bukkit;

public class RZXHookManager {
    public static void detectHooks() {
        RZXPerformanceTracker.track("Hook Detection", () -> {
            checkHook("PlaceholderAPI", "Placeholder expansion integration active.");
            checkHook("WorldGuard", "Region safety and protection bounds active.");
            checkHook("ExcellentEconomy", "zGems currency ecosystem integration active.");
            checkHook("LuckPerms", "Storage limits & permission node sync active.");
            checkHook("Oraxen", "Custom resource pack model data active.");
            checkHook("ItemsAdder", "Custom item texture stack active.");
            checkHook("Vault", "Economy & permission provider integration checked.");
        });
    }

    private static void checkHook(String pluginName, String successDetails) {
        if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            RZXLoggerService.success("Hook Loaded: " + pluginName + " - " + successDetails);
        } else {
            RZXLoggerService.warning("Soft Hook Soft-Skipped: " + pluginName + " is not installed or enabled.");
        }
    }
}
