package com.rzxpets;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final Map<String, PetData> pets;
    private String activePet;
    private int playtimeSeconds;
    private int afkPlaytimeSeconds;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.pets = new HashMap<>();
        this.activePet = null;
        this.playtimeSeconds = 0;
        this.afkPlaytimeSeconds = 0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Map<String, PetData> getPets() {
        return pets;
    }

    public String getActivePet() {
        return activePet;
    }

    public void setActivePet(String activePet) {
        this.activePet = activePet;
    }

    public boolean hasPet(String typeId) {
        if (typeId == null) return false;
        return pets.containsKey(typeId.toLowerCase());
    }

    public PetData getPet(String typeId) {
        if (typeId == null) return null;
        return pets.get(typeId.toLowerCase());
    }

    public void addPet(String typeId) {
        if (typeId == null) return;
        String lowerId = typeId.toLowerCase();
        if (!hasPet(lowerId)) {
            pets.put(lowerId, new PetData(lowerId));
        }
    }

    public int getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    public void setPlaytimeSeconds(int playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
    }

    public int getAfkPlaytimeSeconds() {
        return afkPlaytimeSeconds;
    }

    public void setAfkPlaytimeSeconds(int afkPlaytimeSeconds) {
        this.afkPlaytimeSeconds = afkPlaytimeSeconds;
    }

    public int getMaxLimit(Player player) {
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("rzxpets.limit." + i)) {
                return i;
            }
        }
        try {
            org.bukkit.plugin.Plugin p = org.bukkit.Bukkit.getPluginManager().getPlugin("RZXPets");
            if (p != null) {
                return p.getConfig().getInt("storage.default-limit", 1);
            }
        } catch (Exception e) {
            // Ignored
        }
        return 1;
    }
}
