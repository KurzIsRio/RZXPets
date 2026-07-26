package com.rzxpets.rzx;

import org.bukkit.plugin.Plugin;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RZXAuditService {
    private static File auditFile;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void init(Plugin plugin) {
        auditFile = new File(plugin.getDataFolder(), "audit.log");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }

    public static synchronized void log(String actor, String action) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String entry = String.format("[%s] [AUDIT] [%s] %s", timestamp, actor, action);
        
        RZXLoggerService.info("[AUDIT] " + actor + " - " + action);

        if (auditFile != null) {
            try (PrintWriter out = new PrintWriter(new FileWriter(auditFile, true))) {
                out.println(entry);
            } catch (Exception e) {
                RZXLoggerService.error("Failed to write to audit.log", e);
            }
        }
    }
}
