package com.rzxpets.rzx;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class RZXExceptionHandler {
    public static void handle(String context, Throwable throwable) {
        handle(context, null, null, throwable);
    }

    public static void handle(String context, Player player, String[] args, Throwable throwable) {
        StringBuilder details = new StringBuilder();
        details.append("Context: ").append(context);
        details.append(" | Server Ticks: ").append(Bukkit.getCurrentTick());
        
        if (player != null) {
            details.append(" | Player: ").append(player.getName()).append(" (").append(player.getUniqueId()).append(")");
        }
        if (args != null && args.length > 0) {
            details.append(" | Args: ").append(String.join(" ", args));
        }

        RZXLoggerService.fatal("Unhandled RZX Exception - " + details.toString(), throwable);
    }
}
