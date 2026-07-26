package com.rzxpets;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

public class DataManager {
    private final RZXPets plugin;
    private final File dataFolder;
    private Connection connection;

    public DataManager(RZXPets plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        setupTables();
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        String type = plugin.getConfig().getString("database.type", "SQLite").toUpperCase();

        if (type.equals("MYSQL")) {
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfig().getString("database.mysql.database", "rzxpets");
            String username = plugin.getConfig().getString("database.mysql.username", "root");
            String password = plugin.getConfig().getString("database.mysql.password", "");
            boolean ssl = plugin.getConfig().getBoolean("database.mysql.ssl", false);

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&autoReconnect=true";
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {}
            connection = DriverManager.getConnection(url, username, password);
        } else {
            // Default SQLite
            String fileName = plugin.getConfig().getString("database.sqlite.file", "rzxpets.db");
            File file = new File(plugin.getDataFolder(), fileName);
            String url = "jdbc:sqlite:" + file.getAbsolutePath();
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {}
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    public void setupTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
             
            // Create players table
            stmt.execute("CREATE TABLE IF NOT EXISTS rzxpets_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "active_pet VARCHAR(64), " +
                    "playtime_seconds INT DEFAULT 0, " +
                    "afk_playtime_seconds INT DEFAULT 0" +
                    ")");

            // Create player pets table with name column
            stmt.execute("CREATE TABLE IF NOT EXISTS rzxpets_player_pets (" +
                    "uuid VARCHAR(36), " +
                    "pet_id VARCHAR(64), " +
                    "level INT DEFAULT 1, " +
                    "xp INT DEFAULT 0, " +
                    "name VARCHAR(128) DEFAULT NULL, " +
                    "PRIMARY KEY (uuid, pet_id)" +
                    ")");

            // Migration check: add custom name column dynamically if migrating from an existing table
            try {
                stmt.execute("ALTER TABLE rzxpets_player_pets ADD COLUMN name VARCHAR(128) DEFAULT NULL");
            } catch (SQLException ignored) {
                // Column already exists
            }
                     
        } catch (SQLException e) {
            com.rzxpets.rzx.RZXLoggerService.error("Could not initialize database tables!", e);
        }
    }

    public PlayerData loadPlayerData(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        
        try (Connection conn = getConnection()) {
            // 1. Load player profile
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM rzxpets_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String active = rs.getString("active_pet");
                        if (active != null) {
                            data.setActivePet(active.toLowerCase());
                        }
                        data.setPlaytimeSeconds(rs.getInt("playtime_seconds"));
                        data.setAfkPlaytimeSeconds(rs.getInt("afk_playtime_seconds"));
                    } else {
                        // Migration Check: If legacy YAML exists, load and save to SQL
                        File file = new File(dataFolder, uuid.toString() + ".yml");
                        if (file.exists()) {
                            com.rzxpets.rzx.RZXLoggerService.info("Migrating legacy YAML data to SQLite/MySQL database for: " + uuid);
                            PlayerData legacyData = loadLegacyYamlData(uuid);
                            savePlayerData(legacyData);
                            return legacyData;
                        }
                    }
                }
            }
            
            // 2. Load owned pets
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM rzxpets_player_pets WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String petId = rs.getString("pet_id").toLowerCase();
                        int level = rs.getInt("level");
                        int xp = rs.getInt("xp");
                        String name = rs.getString("name");
                        data.getPets().put(petId, new PetData(petId, level, xp, name));
                    }
                }
            }
        } catch (SQLException e) {
            com.rzxpets.rzx.RZXLoggerService.error("Could not load player data for UUID: " + uuid, e);
        }
        return data;
    }

    private PlayerData loadLegacyYamlData(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        PlayerData data = new PlayerData(uuid);
        if (!file.exists()) {
            return data;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String active = config.getString("active");
        if (active != null) {
            data.setActivePet(active.toLowerCase());
        }
        data.setPlaytimeSeconds(config.getInt("playtime-seconds", 0));
        data.setAfkPlaytimeSeconds(config.getInt("afk-playtime-seconds", 0));

        String sectionKey = config.isConfigurationSection("pets") ? "pets" : "companions";
        if (config.isConfigurationSection(sectionKey)) {
            for (String key : config.getConfigurationSection(sectionKey).getKeys(false)) {
                int level = config.getInt(sectionKey + "." + key + ".level", 1);
                int xp = config.getInt(sectionKey + "." + key + ".xp", 0);
                String name = config.getString(sectionKey + "." + key + ".name", null);
                data.getPets().put(key.toLowerCase(), new PetData(key.toLowerCase(), level, xp, name));
            }
        }
        return data;
    }

    public void savePlayerData(PlayerData data) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            
            String type = plugin.getConfig().getString("database.type", "SQLite").toUpperCase();
            String playerSql;
            if (type.equals("MYSQL")) {
                playerSql = "INSERT INTO rzxpets_players (uuid, active_pet, playtime_seconds, afk_playtime_seconds) " +
                            "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                            "active_pet = VALUES(active_pet), playtime_seconds = VALUES(playtime_seconds), afk_playtime_seconds = VALUES(afk_playtime_seconds)";
            } else {
                playerSql = "INSERT INTO rzxpets_players (uuid, active_pet, playtime_seconds, afk_playtime_seconds) " +
                            "VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET " +
                            "active_pet = excluded.active_pet, playtime_seconds = excluded.playtime_seconds, afk_playtime_seconds = excluded.afk_playtime_seconds";
            }
            
            try (PreparedStatement ps = conn.prepareStatement(playerSql)) {
                ps.setString(1, data.getUuid().toString());
                ps.setString(2, data.getActivePet());
                ps.setInt(3, data.getPlaytimeSeconds());
                ps.setInt(4, data.getAfkPlaytimeSeconds());
                ps.executeUpdate();
            }

            // Clean up removed pet entries from database inside current transaction
            try (PreparedStatement psDelete = conn.prepareStatement("DELETE FROM rzxpets_player_pets WHERE uuid = ?")) {
                psDelete.setString(1, data.getUuid().toString());
                psDelete.executeUpdate();
            }

            String petSql;
            if (type.equals("MYSQL")) {
                petSql = "INSERT INTO rzxpets_player_pets (uuid, pet_id, level, xp, name) VALUES (?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp), name = VALUES(name)";
            } else {
                petSql = "INSERT INTO rzxpets_player_pets (uuid, pet_id, level, xp, name) VALUES (?, ?, ?, ?, ?) " +
                         "ON CONFLICT(uuid, pet_id) DO UPDATE SET level = excluded.level, xp = excluded.xp, name = excluded.name";
            }

            try (PreparedStatement psPet = conn.prepareStatement(petSql)) {
                for (Map.Entry<String, PetData> entry : data.getPets().entrySet()) {
                    psPet.setString(1, data.getUuid().toString());
                    psPet.setString(2, entry.getKey());
                    psPet.setInt(3, entry.getValue().getLevel());
                    psPet.setInt(4, entry.getValue().getXp());
                    psPet.setString(5, entry.getValue().getName());
                    psPet.executeUpdate();
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            com.rzxpets.rzx.RZXLoggerService.error("Could not save player data for UUID: " + data.getUuid(), e);
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
