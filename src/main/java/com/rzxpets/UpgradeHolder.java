package com.rzxpets;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class UpgradeHolder implements InventoryHolder {
    private final String petId;

    public UpgradeHolder() {
        this.petId = null;
    }

    public UpgradeHolder(String petId) {
        this.petId = petId;
    }

    public String getPetId() {
        return petId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
