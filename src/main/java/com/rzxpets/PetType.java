package com.rzxpets;

import org.bukkit.entity.EntityType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PetType {
    private static final Map<String, PetType> REGISTRY = new HashMap<>();

    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final int customModelData;
    private final List<PetMechanic> mechanics;
    private final String variant;
    private final List<String> description;

    public PetType(String id, String displayName, EntityType entityType, int customModelData, List<PetMechanic> mechanics, String variant, List<String> description) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.entityType = entityType;
        this.customModelData = customModelData;
        this.mechanics = mechanics;
        this.variant = variant;
        this.description = description != null ? description : new java.util.ArrayList<>();
    }

    public PetType(String id, String displayName, EntityType entityType, int customModelData, List<PetMechanic> mechanics, String variant) {
        this(id, displayName, entityType, customModelData, mechanics, variant, null);
    }

    public PetType(String id, String displayName, EntityType entityType, int customModelData, List<PetMechanic> mechanics) {
        this(id, displayName, entityType, customModelData, mechanics, null, null);
    }

    public String getVariant() {
        return variant;
    }

    public List<String> getDescription() {
        return description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public List<PetMechanic> getMechanics() {
        return mechanics;
    }

    public static void register(PetType type) {
        REGISTRY.put(type.getId(), type);
    }

    public static PetType getById(String id) {
        if (id == null) return null;
        return REGISTRY.get(id.toLowerCase());
    }

    public static Collection<PetType> values() {
        return REGISTRY.values();
    }

    public static void clear() {
        REGISTRY.clear();
    }
}
