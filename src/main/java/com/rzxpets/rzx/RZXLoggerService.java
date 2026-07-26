package com.rzxpets.rzx;

import org.bukkit.Bukkit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RZXLoggerService {
    private static final String PLUGIN_NAME = "RZXPets";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(String severity, String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String thread = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%s] [%s] [%s] - %s", PLUGIN_NAME, timestamp, severity.toUpperCase(), thread, message);

        Bukkit.getConsoleSender().sendMessage(colorSeverity(severity, formatted));

        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    private static String colorSeverity(String severity, String text) {
        return switch (severity.toUpperCase()) {
            case "TRACE" -> "§8" + text;
            case "DEBUG" -> "§7" + text;
            case "SUCCESS" -> "§a" + text;
            case "INFO" -> "§b" + text;
            case "WARNING" -> "§e" + text;
            case "ERROR" -> "§c" + text;
            case "FATAL" -> "§4§l" + text;
            default -> text;
        };
    }

    public static void trace(String message) { log("TRACE", message, null); }
    public static void debug(String message) { log("DEBUG", message, null); }
    public static void info(String message) { log("INFO", message, null); }
    public static void success(String message) { log("SUCCESS", message, null); }
    public static void warning(String message) { log("WARNING", message, null); }
    public static void error(String message) { log("ERROR", message, null); }
    public static void error(String message, Throwable t) { log("ERROR", message, t); }
    public static void fatal(String message) { log("FATAL", message, null); }
    public static void fatal(String message, Throwable t) { log("FATAL", message, t); }
}
