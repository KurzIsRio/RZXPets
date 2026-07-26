package com.rzxpets;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderHook {

    /**
     * Translates standard colour codes (&) and hex RGB codes (&#ffffff).
     */
    public static String color(String text) {
        if (text == null) return null;
        
        // Translate hex colors: &#ff0000 -> §x§f§f§0§0§0§0
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /**
     * Extracts leading color/style codes from pet display name.
     */
    public static String getColorPrefix(String text) {
        if (text == null) return "";
        StringBuilder prefix = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '#') {
                    if (i + 7 < text.length()) {
                        prefix.append(text.substring(i, i + 8));
                        i += 8;
                        continue;
                    }
                } else if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(next) != -1) {
                    prefix.append('&').append(next);
                    i += 2;
                    continue;
                }
            } else if (c == '§' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                prefix.append('§').append(next);
                i += 2;
                continue;
            }
            break;
        }
        return prefix.toString();
    }

    /**
     * Set placeholders via PlaceholderAPI using direct class references.
     */
    public static String setPlaceholders(Player player, String text) {
        if (text == null) return null;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return color(text);
        }
        try {
            String processed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            return color(processed);
        } catch (Throwable t) {
            return color(text);
        }
    }

    public static double getExcellentEconomyBalance(Player player, String currencyId) {
        if (currencyId == null || currencyId.isEmpty()) return 0;

        // Try standard raw balance placeholder first (e.g. %excellenteconomy_balance_raw_zgems%)
        String mainRawPlaceholder = "%excellenteconomy_balance_raw_" + currencyId.toLowerCase() + "%";
        String balanceStr = setPlaceholders(player, mainRawPlaceholder);

        // If returned text equals the placeholder, try casing variations for raw placeholder
        if (balanceStr.equals(mainRawPlaceholder) || balanceStr.contains("%")) {
            mainRawPlaceholder = "%excellenteconomy_balance_raw_" + currencyId + "%";
            balanceStr = setPlaceholders(player, mainRawPlaceholder);
        }

        // If raw placeholders failed, fall back to formatted balance placeholders
        if (balanceStr.contains("%")) {
            String formattedPlaceholder = "%excellenteconomy_balance_" + currencyId.toLowerCase() + "%";
            balanceStr = setPlaceholders(player, formattedPlaceholder);

            if (balanceStr.equals(formattedPlaceholder) || balanceStr.contains("%")) {
                formattedPlaceholder = "%excellenteconomy_balance_" + currencyId + "%";
                balanceStr = setPlaceholders(player, formattedPlaceholder);
            }
        }

        // If all placeholder attempts failed, return 0
        if (balanceStr.contains("%")) {
            return 0;
        }

        // Clean up the string to be parsed as a double
        // If it contains both ',' and '.', format it to use '.' as decimal and remove ','
        if (balanceStr.contains(",") && balanceStr.contains(".")) {
            if (balanceStr.indexOf(",") > balanceStr.indexOf(".")) {
                // e.g. 1.000,00 -> 1000.00 (European/German)
                balanceStr = balanceStr.replace(".", "").replace(",", ".");
            } else {
                // e.g. 1,000.00 -> 1000.00 (US/UK)
                balanceStr = balanceStr.replace(",", "");
            }
        } else if (balanceStr.contains(",")) {
            // If it only contains ',', check if it looks like a decimal separator (e.g. 500,00)
            // or a thousand separator (e.g. 1,000)
            int lastCommaIndex = balanceStr.lastIndexOf(",");
            int charsAfterComma = balanceStr.length() - 1 - lastCommaIndex;
            if (charsAfterComma == 3) {
                // e.g. 1,000 -> 1000
                balanceStr = balanceStr.replace(",", "");
            } else {
                // e.g. 500,00 -> 500.00
                balanceStr = balanceStr.replace(",", ".");
            }
        }

        // Clean up: strip everything except numbers and decimals (removes "$", " zGems", etc.)
        String cleaned = balanceStr.replaceAll("[^0-9.]", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
