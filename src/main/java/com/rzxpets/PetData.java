package com.rzxpets;

public class PetData {
    private final String typeId;
    private int level;
    private int xp;
    private String name; // Custom name, nullable

    public PetData(String typeId) {
        this(typeId, 1, 0, null);
    }

    public PetData(String typeId, int level, int xp) {
        this(typeId, level, xp, null);
    }

    public PetData(String typeId, int level, int xp, String name) {
        this.typeId = typeId.toLowerCase();
        this.level = level;
        this.xp = xp;
        this.name = name;
    }

    public String getTypeId() {
        return typeId;
    }

    public int getLevel() {
        return level;
    }

    public int getXp() {
        return xp;
    }

    public String getName() {
        return name;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addXp(int amount) {
        this.xp += amount;
        checkLevelUp();
    }

    private void checkLevelUp() {
        while (xp >= getRequiredXp(level) && level < 100) {
            xp -= getRequiredXp(level);
            level++;
        }
    }

    public static int getRequiredXp(int currentLevel) {
        return currentLevel * 500;
    }

    public PetType getType() {
        return PetType.getById(typeId);
    }
}
