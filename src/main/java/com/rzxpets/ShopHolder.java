package com.rzxpets;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopHolder implements InventoryHolder {
    private final String sectionName;

    public ShopHolder(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getSectionName() {
        return sectionName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
