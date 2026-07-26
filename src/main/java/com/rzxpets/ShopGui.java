package com.rzxpets;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ShopGui {
    private final RZXPets plugin;

    public ShopGui(RZXPets plugin) {
        this.plugin = plugin;
    }

    public void sanitizePlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return;
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey priceKey = new NamespacedKey(plugin, "price");
        NamespacedKey currencyKey = new NamespacedKey(plugin, "currency");

        boolean modified = false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.hasItemMeta()) {
                PersistentDataType<String, String> stringType = PersistentDataType.STRING;
                PersistentDataType<Integer, Integer> intType = PersistentDataType.INTEGER;
                var container = item.getItemMeta().getPersistentDataContainer();
                boolean isGuiItem = container.has(actionKey, stringType) ||
                                    container.has(priceKey, intType) ||
                                    container.has(currencyKey, stringType);

                if (!isGuiItem && item.getItemMeta().hasLore()) {
                    for (String line : item.getItemMeta().getLore()) {
                        if (line.contains("Companion Skills:") || line.contains("Click to summon pet") || line.contains("Click to dismiss pet") || line.contains("ALREADY ADOPTED")) {
                            isGuiItem = true;
                            break;
                        }
                    }
                }

                if (isGuiItem) {
                    contents[i] = null;
                    modified = true;
                }
            }
        }
        if (modified) {
            player.getInventory().setContents(contents);
            player.updateInventory();
        }
    }

    public void openMenu(Player player, String sectionName) {
        player.setItemOnCursor(null);
        sanitizePlayerInventory(player);

        File file = new File(plugin.getDataFolder(), "shop/sections/" + sectionName + ".yml");
        if (!file.exists()) {
            player.sendMessage("§cShop menu '" + sectionName + "' configuration not found.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean enabled = config.getBoolean("enabled", true);
        if (!enabled) {
            player.sendMessage("§cThis menu section is currently disabled.");
            return;
        }

        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());
        String title = config.getString("title", "Shop Menu");
        int size = config.getInt("size", 27);

        // Translate colors and placeholders safely
        title = PlaceholderHook.setPlaceholders(player, title);

        Inventory inv = Bukkit.createInventory(new ShopHolder(sectionName), size, title);

        // Inject high-end filler border glass panes for premium visual styling
        if (config.getBoolean("filler.enabled", true)) {
            String borderMat = config.getString("filler.border-material", "BLACK_STAINED_GLASS_PANE");
            String emptyMat = config.getString("filler.empty-material", "GRAY_STAINED_GLASS_PANE");

            Material bMat = Material.getMaterial(borderMat.toUpperCase());
            Material eMat = Material.getMaterial(emptyMat.toUpperCase());
            if (bMat == null) bMat = Material.BLACK_STAINED_GLASS_PANE;
            if (eMat == null) eMat = Material.GRAY_STAINED_GLASS_PANE;

            ItemStack borderItem = new ItemStack(bMat);
            ItemMeta bMeta = borderItem.getItemMeta();
            if (bMeta != null) {
                bMeta.setDisplayName(" ");
                borderItem.setItemMeta(bMeta);
            }

            ItemStack emptyItem = new ItemStack(eMat);
            ItemMeta eMeta = emptyItem.getItemMeta();
            if (eMeta != null) {
                eMeta.setDisplayName(" ");
                emptyItem.setItemMeta(eMeta);
            }

            for (int i = 0; i < size; i++) {
                if (isBorderSlot(i, size)) {
                    inv.setItem(i, borderItem);
                } else {
                    inv.setItem(i, emptyItem);
                }
            }
        }

        if (sectionName.equalsIgnoreCase("main")) {
            // Dynamically construct main storage GUI from other section config attributes
            File sectionsFolder = new File(plugin.getDataFolder(), "shop/sections");
            File[] files = sectionsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
            if (files != null) {
                for (File secFile : files) {
                    if (secFile.getName().equalsIgnoreCase("main.yml")) continue;

                    YamlConfiguration secConfig = YamlConfiguration.loadConfiguration(secFile);
                    if (!secConfig.getBoolean("enabled", true)) continue;
                    if (!secConfig.getBoolean("main-menu.enabled", false)) continue;

                    ConfigurationSection mSec = secConfig.getConfigurationSection("main-menu");
                    if (mSec == null) continue;

                    int slot = mSec.getInt("slot", 0);
                    String matStr = mSec.getString("material", "STONE");
                    String name = mSec.getString("name", "");
                    List<String> lore = mSec.getStringList("lore");
                    int customModelData = mSec.getInt("custom-model-data", 0);

                    String sectionId = secFile.getName().replace(".yml", "").replace(".yaml", "").toLowerCase();
                    String action = "open " + sectionId;

                    Material material = Material.getMaterial(matStr.toUpperCase());
                    if (material == null) material = Material.STONE;

                    ItemStack item = new ItemStack(material);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (!name.isEmpty()) {
                            String parsedName = PlaceholderHook.setPlaceholders(player, name);
                            meta.setDisplayName(parsedName);
                        }

                        List<String> parsedLore = new ArrayList<>();
                        for (String line : lore) {
                            String parsedLine = PlaceholderHook.setPlaceholders(player, line);
                            parsedLore.add(parsedLine);
                        }
                        meta.setLore(parsedLore);

                        if (customModelData > 0) {
                            meta.setCustomModelData(customModelData);
                        }

                        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
                        item.setItemMeta(meta);
                    }

                    if (slot >= 0 && slot < size) {
                        inv.setItem(slot, item);
                    }
                }
            }
        } else {
            String sectionCurrency = config.getString("currency", "free");
            ConfigurationSection itemsSec = config.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String key : itemsSec.getKeys(false)) {
                    ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
                    if (itemSec == null) continue;

                    int slot = itemSec.getInt("slot", 0);
                    String matStr = itemSec.getString("material", "STONE");
                    String name = itemSec.getString("name", "");
                    List<String> lore = itemSec.getStringList("lore");
                    int customModelData = itemSec.getInt("custom-model-data", 0);
                    
                    String action = itemSec.getString("action", "");
                    int price = itemSec.getInt("price", 0);
                    String currency = itemSec.getString("currency", sectionCurrency);
                    String itemId = itemSec.getString("item-id", "");
                    String permission = itemSec.getString("permission", "");

                    // Check if player already owns this pet
                    boolean isBought = false;
                    String[] actionParts = action.split(" ");
                    if (actionParts.length >= 2 && actionParts[0].equalsIgnoreCase("buy")) {
                        String petId = actionParts[1].toLowerCase();
                        if (playerData.hasPet(petId)) {
                            isBought = true;
                        }
                    }

                    Material material = Material.getMaterial(matStr.toUpperCase());
                    if (material == null) {
                        material = Material.STONE;
                    }

                    ItemStack item = new ItemStack(material);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (!name.isEmpty()) {
                            String displayName = name;
                            if (isBought) {
                                displayName = displayName + " &a[Adopted]";
                            }
                            String parsedName = PlaceholderHook.setPlaceholders(player, displayName);
                            meta.setDisplayName(parsedName);
                        }

                        List<String> parsedLore = new ArrayList<>();
                        for (String line : lore) {
                            String parsedLine = PlaceholderHook.setPlaceholders(player, line);
                            if (isBought && (parsedLine.contains("Click to adopt") || parsedLine.contains("adopt pet"))) {
                                parsedLine = PlaceholderHook.color("&a&l● ALREADY ADOPTED");
                            }
                            parsedLore.add(parsedLine);
                        }
                        meta.setLore(parsedLore);

                        if (customModelData > 0) {
                            meta.setCustomModelData(customModelData);
                        }

                        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                        NamespacedKey priceKey = new NamespacedKey(plugin, "price");
                        NamespacedKey currencyKey = new NamespacedKey(plugin, "currency");
                        NamespacedKey itemIdKey = new NamespacedKey(plugin, "item_id");
                        NamespacedKey permissionKey = new NamespacedKey(plugin, "permission");

                        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
                        meta.getPersistentDataContainer().set(priceKey, PersistentDataType.INTEGER, price);
                        meta.getPersistentDataContainer().set(currencyKey, PersistentDataType.STRING, currency);
                        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);
                        meta.getPersistentDataContainer().set(permissionKey, PersistentDataType.STRING, permission);

                        item.setItemMeta(meta);
                    }

                    if (slot >= 0 && slot < size) {
                        inv.setItem(slot, item);
                    }
                }
            }
        }

        player.openInventory(inv);
        player.updateInventory();
    }

    /**
     * Opens the dynamic Pet Storage GUI showing the player's currently owned pets
     * and allowing them to summon/dismiss them on click.
     */
    public void openStorageMenu(Player player) {
        player.setItemOnCursor(null);
        sanitizePlayerInventory(player);

        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());

        String title = PlaceholderHook.color("&8&lYOUR PET STORAGE");
        int size = 36;

        Inventory inv = Bukkit.createInventory(new StorageHolder(), size, title);

        // Fill background borders
        String borderMat = "BLACK_STAINED_GLASS_PANE";
        String emptyMat = "GRAY_STAINED_GLASS_PANE";

        Material bMat = Material.getMaterial(borderMat);
        Material eMat = Material.getMaterial(emptyMat);
        if (bMat == null) bMat = Material.BLACK_STAINED_GLASS_PANE;
        if (eMat == null) eMat = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack borderItem = new ItemStack(bMat);
        ItemMeta bMeta = borderItem.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(" ");
            borderItem.setItemMeta(bMeta);
        }

        ItemStack emptyItem = new ItemStack(eMat);
        ItemMeta eMeta = emptyItem.getItemMeta();
        if (eMeta != null) {
            eMeta.setDisplayName(" ");
            emptyItem.setItemMeta(eMeta);
        }

        // Initialize all slots as empty filler
        for (int i = 0; i < size; i++) {
            if (isBorderSlot(i, size)) {
                inv.setItem(i, borderItem);
            } else {
                inv.setItem(i, emptyItem);
            }
        }

        // Map owned pets into center slots
        int[] availableSlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25 };
        int slotIndex = 0;

        boolean hasAnyPet = false;
        for (String petId : PetType.values().stream().map(PetType::getId).sorted().toList()) {
            if (playerData.hasPet(petId)) {
                hasAnyPet = true;
                if (slotIndex >= availableSlots.length) break;

                PetType type = PetType.getById(petId);
                if (type == null) continue;

                PetData pData = playerData.getPet(petId);
                int level = pData != null ? pData.getLevel() : 1;
                int xp = pData != null ? pData.getXp() : 0;
                int reqXp = pData != null ? PetData.getRequiredXp(level) : 100;

                Material mat = Material.getMaterial(type.getEntityType().name() + "_SPAWN_EGG");
                if (mat == null) mat = Material.CHICKEN_SPAWN_EGG;

                ItemStack petItem = new ItemStack(mat);
                ItemMeta meta = petItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(PlaceholderHook.color(type.getDisplayName()));
                    List<String> lore = new ArrayList<>();
                    lore.add(PlaceholderHook.color("&eCompanion Skills:"));
                    for (PetMechanic mech : type.getMechanics()) {
                        boolean unlocked = level >= mech.getMinLevel();
                        String status = unlocked ? "&a●" : "&c○";
                        lore.add(PlaceholderHook.color(" " + status + " &f" + mech.getName() + " &7(Lvl " + mech.getMinLevel() + ")"));
                        if (mech.getDescription() != null) {
                            for (String descLine : mech.getDescription()) {
                                String formattedLine = formatMechanicDescriptionLine(descLine, mech, level);
                                lore.add(PlaceholderHook.color("    &7" + formattedLine));
                            }
                        }
                    }
                    lore.add("");
                    lore.add(PlaceholderHook.color("&7Level: &b" + level + " &7/ &b100"));
                    lore.add(PlaceholderHook.color("&7XP: &f" + xp + " &7/ &f" + reqXp));
                    lore.add("");

                    boolean isActive = petId.equals(playerData.getActivePet());
                    if (isActive) {
                        lore.add(PlaceholderHook.color("&a&l● SUMMONED"));
                        lore.add(PlaceholderHook.color("&7Click to dismiss pet."));
                    } else {
                        lore.add(PlaceholderHook.color("&c&l○ DISMISSED"));
                        lore.add(PlaceholderHook.color("&eClick to summon pet!"));
                    }
                    meta.setLore(lore);

                    if (type.getCustomModelData() > 0) {
                        meta.setCustomModelData(type.getCustomModelData());
                    }

                    NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                    String action = isActive ? "dismiss" : "summon " + petId;
                    meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);

                    petItem.setItemMeta(meta);
                }

                inv.setItem(availableSlots[slotIndex++], petItem);
            }
        }

        // If they own no pets, display a warning placeholder button
        if (!hasAnyPet) {
            ItemStack warning = new ItemStack(Material.BARRIER);
            ItemMeta wMeta = warning.getItemMeta();
            if (wMeta != null) {
                wMeta.setDisplayName(PlaceholderHook.color("&c&lNo Pets Adopted"));
                List<String> wLore = new ArrayList<>();
                wLore.add(PlaceholderHook.color("&7You do not own any pets yet."));
                wLore.add(PlaceholderHook.color("&7Go to the Pet Adoption Shop to buy one!"));
                wMeta.setLore(wLore);
                warning.setItemMeta(wMeta);
            }
            inv.setItem(22, warning); // Middle of bottom center
        }

        player.openInventory(inv);
        player.updateInventory();
    }

    private boolean isBorderSlot(int slot, int size) {
        int rows = size / 9;
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == (rows - 1) || col == 0 || col == 8;
    }

    public void openUpgradeSelectPetMenu(Player player) {
        player.setItemOnCursor(null);
        sanitizePlayerInventory(player);

        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());
        String title = PlaceholderHook.color("&#FFAA00&l« &f&lSELECT PET TO UPGRADE &#FFAA00&l»");
        int size = 45;

        Inventory inv = Bukkit.createInventory(new UpgradeHolder(), size, title);

        // Fill background borders
        String borderMat = "BLACK_STAINED_GLASS_PANE";
        String emptyMat = "ORANGE_STAINED_GLASS_PANE";

        Material bMat = Material.getMaterial(borderMat);
        Material eMat = Material.getMaterial(emptyMat);
        if (bMat == null) bMat = Material.BLACK_STAINED_GLASS_PANE;
        if (eMat == null) eMat = Material.ORANGE_STAINED_GLASS_PANE;

        ItemStack borderItem = new ItemStack(bMat);
        ItemMeta bMeta = borderItem.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(" ");
            borderItem.setItemMeta(bMeta);
        }

        ItemStack emptyItem = new ItemStack(eMat);
        ItemMeta eMeta = emptyItem.getItemMeta();
        if (eMeta != null) {
            eMeta.setDisplayName(" ");
            emptyItem.setItemMeta(eMeta);
        }

        for (int i = 0; i < size; i++) {
            if (isBorderSlot(i, size)) {
                inv.setItem(i, borderItem);
            } else {
                inv.setItem(i, emptyItem);
            }
        }

        int[] availableSlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25 };
        int slotIndex = 0;

        boolean hasAnyPet = false;
        for (String petId : PetType.values().stream().map(PetType::getId).sorted().toList()) {
            if (playerData.hasPet(petId)) {
                hasAnyPet = true;
                if (slotIndex >= availableSlots.length) break;

                PetType type = PetType.getById(petId);
                if (type == null) continue;

                PetData pData = playerData.getPet(petId);
                int level = pData != null ? pData.getLevel() : 1;

                Material mat = Material.getMaterial(type.getEntityType().name() + "_SPAWN_EGG");
                if (mat == null) mat = Material.CHICKEN_SPAWN_EGG;

                ItemStack petItem = new ItemStack(mat);
                ItemMeta meta = petItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(PlaceholderHook.color(type.getDisplayName() + " &7[Lvl " + level + "]"));
                    List<String> lore = new ArrayList<>();
                    lore.add(PlaceholderHook.color("&eCompanion Skills:"));
                    for (PetMechanic mech : type.getMechanics()) {
                        boolean unlocked = level >= mech.getMinLevel();
                        String status = unlocked ? "&a●" : "&c○";
                        lore.add(PlaceholderHook.color(" " + status + " &f" + mech.getName() + " &7(Lvl " + mech.getMinLevel() + ")"));
                        if (mech.getDescription() != null) {
                            for (String descLine : mech.getDescription()) {
                                String formattedLine = formatMechanicDescriptionLine(descLine, mech, level);
                                lore.add(PlaceholderHook.color("    &7" + formattedLine));
                            }
                        }
                    }
                    lore.add("");
                    lore.add(PlaceholderHook.color("&7Click to open upgrade panel"));
                    lore.add(PlaceholderHook.color("&7for this companion."));
                    meta.setLore(lore);

                    if (type.getCustomModelData() > 0) {
                        meta.setCustomModelData(type.getCustomModelData());
                    }

                    NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                    meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select_upgrade " + petId);
                    petItem.setItemMeta(meta);
                }

                inv.setItem(availableSlots[slotIndex++], petItem);
            }
        }

        if (!hasAnyPet) {
            ItemStack warning = new ItemStack(Material.BARRIER);
            ItemMeta wMeta = warning.getItemMeta();
            if (wMeta != null) {
                wMeta.setDisplayName(PlaceholderHook.color("&c&lNo Pets Adopted"));
                List<String> wLore = new ArrayList<>();
                wLore.add(PlaceholderHook.color("&7You must own at least one pet"));
                wLore.add(PlaceholderHook.color("&7before you can upgrade companion skills."));
                wMeta.setLore(wLore);
                warning.setItemMeta(wMeta);
            }
            inv.setItem(22, warning);
        }

        // Navigation back button
        ItemStack backItem = new ItemStack(Material.PAPER);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(PlaceholderHook.color("&c&l« Go Back"));
            List<String> bLore = new ArrayList<>();
            bLore.add(PlaceholderHook.color("&7Return to main pet menu."));
            backMeta.setLore(bLore);
            NamespacedKey actionKey = new NamespacedKey(plugin, "action");
            backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open main");
            backItem.setItemMeta(backMeta);
        }
        inv.setItem(40, backItem);

        player.openInventory(inv);
        player.updateInventory();
    }

    public void openPetUpgradeDetailMenu(Player player, String petId) {
        player.setItemOnCursor(null);
        sanitizePlayerInventory(player);

        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());
        PetType type = PetType.getById(petId);
        if (type == null) return;

        PetData pData = playerData.getPet(petId);
        int level = pData != null ? pData.getLevel() : 1;
        int xp = pData != null ? pData.getXp() : 0;
        int reqXp = PetData.getRequiredXp(level);

        String title = PlaceholderHook.color("&#FFAA00&l« &f&lUPGRADE: " + type.getDisplayName() + " &7[Lvl " + level + "] &#FFAA00&l»");
        int size = 45;

        Inventory inv = Bukkit.createInventory(new UpgradeHolder(petId), size, title);

        // Fill background borders
        String borderMat = "BLACK_STAINED_GLASS_PANE";
        String emptyMat = "ORANGE_STAINED_GLASS_PANE";

        Material bMat = Material.getMaterial(borderMat);
        Material eMat = Material.getMaterial(emptyMat);
        if (bMat == null) bMat = Material.BLACK_STAINED_GLASS_PANE;
        if (eMat == null) eMat = Material.ORANGE_STAINED_GLASS_PANE;

        ItemStack borderItem = new ItemStack(bMat);
        ItemMeta bMeta = borderItem.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(" ");
            borderItem.setItemMeta(bMeta);
        }

        ItemStack emptyItem = new ItemStack(eMat);
        ItemMeta eMeta = emptyItem.getItemMeta();
        if (eMeta != null) {
            eMeta.setDisplayName(" ");
            emptyItem.setItemMeta(eMeta);
        }

        for (int i = 0; i < size; i++) {
            if (isBorderSlot(i, size)) {
                inv.setItem(i, borderItem);
            } else {
                inv.setItem(i, emptyItem);
            }
        }

        // Center Slot 13: The Pet Egg Info Card
        Material eggMat = Material.getMaterial(type.getEntityType().name() + "_SPAWN_EGG");
        if (eggMat == null) eggMat = Material.CHICKEN_SPAWN_EGG;
        ItemStack infoEgg = new ItemStack(eggMat);
        ItemMeta eggMeta = infoEgg.getItemMeta();
        if (eggMeta != null) {
            eggMeta.setDisplayName(PlaceholderHook.color(type.getDisplayName() + " &7[Lvl " + level + "]"));
            List<String> lore = new ArrayList<>();
            lore.add(PlaceholderHook.color("&7Level Progression: &b" + level + " &7/ &b100"));
            lore.add(PlaceholderHook.color("&7XP: &f" + xp + " &7/ &f" + reqXp));
            lore.add("");
            lore.add(PlaceholderHook.color("&eCompanion Skills:"));
            for (PetMechanic mech : type.getMechanics()) {
                boolean unlocked = level >= mech.getMinLevel();
                String status = unlocked ? "&a&l[UNLOCKED]" : "&c&l[LOCKED - Lvl " + mech.getMinLevel() + "]";
                lore.add(PlaceholderHook.color(" &7● " + status + " &b" + mech.getName()));
                if (mech.getDescription() != null) {
                    for (String descLine : mech.getDescription()) {
                        String formattedLine = formatMechanicDescriptionLine(descLine, mech, level);
                        lore.add(PlaceholderHook.color("    &7" + formattedLine));
                    }
                }
            }
            eggMeta.setLore(lore);
            if (type.getCustomModelData() > 0) {
                eggMeta.setCustomModelData(type.getCustomModelData());
            }
            infoEgg.setItemMeta(eggMeta);
        }
        inv.setItem(13, infoEgg);

        // Slot 29: Progressive Level Upgrade Button
        ItemStack levelUpBtn;
        if (level >= 100) {
            levelUpBtn = new ItemStack(Material.BARRIER);
            ItemMeta btnMeta = levelUpBtn.getItemMeta();
            if (btnMeta != null) {
                btnMeta.setDisplayName(PlaceholderHook.color("&6&lLevel MAXED OUT"));
                List<String> btnLore = new ArrayList<>();
                btnLore.add(PlaceholderHook.color("&7Your companion has reached maximum level."));
                btnMeta.setLore(btnLore);
                levelUpBtn.setItemMeta(btnMeta);
            }
        } else {
            levelUpBtn = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta btnMeta = levelUpBtn.getItemMeta();
            if (btnMeta != null) {
                int nextLevel = level + 1;
                int cost = 50 + (level * 15);
                btnMeta.setDisplayName(PlaceholderHook.color("&e&lUpgrade to Level &b&l" + nextLevel));
                List<String> btnLore = new ArrayList<>();
                btnLore.add(PlaceholderHook.color("&7Instantly level up this pet."));
                btnLore.add(PlaceholderHook.color("&8&m-------------------------------------"));
                btnLore.add(PlaceholderHook.color("&7Price: &a" + cost + " zGems"));
                btnLore.add(PlaceholderHook.color("&7Your Balance: &f%excellenteconomy_balance_zgems%"));
                btnLore.add(PlaceholderHook.color("&8&m-------------------------------------"));
                btnLore.add(PlaceholderHook.color("&eClick to buy Level Up!"));
                btnMeta.setLore(btnLore);
                NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                btnMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "buy_level " + petId + " " + cost);
                levelUpBtn.setItemMeta(btnMeta);
            }
        }
        inv.setItem(29, levelUpBtn);

        // Slot 31: Progressive Storage Expansion Button
        int currentLimit = playerData.getMaxLimit(player);
        ItemStack storageBtn;
        if (currentLimit >= 5) {
            storageBtn = new ItemStack(Material.BARRIER);
            ItemMeta sMeta = storageBtn.getItemMeta();
            if (sMeta != null) {
                sMeta.setDisplayName(PlaceholderHook.color("&c&lStorage Capacity Maxed &7[5 Pets]"));
                List<String> sLore = new ArrayList<>();
                sLore.add(PlaceholderHook.color("&7You have reached the maximum storage capacity."));
                sMeta.setLore(sLore);
                storageBtn.setItemMeta(sMeta);
            }
        } else {
            int nextLimit = currentLimit + 1;
            Material sMat = Material.LEAD;
            int cost = 200;
            if (nextLimit == 3) { sMat = Material.SADDLE; cost = 400; }
            else if (nextLimit == 4) { sMat = Material.CHEST; cost = 800; }
            else if (nextLimit == 5) { sMat = Material.NETHER_STAR; cost = 1600; }

            storageBtn = new ItemStack(sMat);
            ItemMeta sMeta = storageBtn.getItemMeta();
            if (sMeta != null) {
                sMeta.setDisplayName(PlaceholderHook.color("&6&lStorage Expansion &7[" + nextLimit + " Pets]"));
                List<String> sLore = new ArrayList<>();
                sLore.add(PlaceholderHook.color("&7Expands your pet storage capacity."));
                sLore.add(PlaceholderHook.color("&8&m-------------------------------------"));
                sLore.add(PlaceholderHook.color("&7Price: &a" + cost + " zGems"));
                sLore.add(PlaceholderHook.color("&7Your Balance: &f%excellenteconomy_balance_zgems%"));
                sLore.add(PlaceholderHook.color("&8&m-------------------------------------"));
                sLore.add(PlaceholderHook.color("&eClick to purchase upgrade!"));
                sMeta.setLore(sLore);
                NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                sMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "upgrade_limit " + nextLimit + " " + cost);
                storageBtn.setItemMeta(sMeta);
            }
        }
        inv.setItem(31, storageBtn);

        // Slot 40: Navigation back button (returns to select pet menu)
        ItemStack backItem = new ItemStack(Material.PAPER);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(PlaceholderHook.color("&c&l« Go Back"));
            List<String> bLore = new ArrayList<>();
            bLore.add(PlaceholderHook.color("&7Return to select pet menu."));
            backMeta.setLore(bLore);
            NamespacedKey actionKey = new NamespacedKey(plugin, "action");
            backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open upgrade_select");
            backItem.setItemMeta(backMeta);
        }
        inv.setItem(40, backItem);

        // Pre-parse placeholders (e.g. %excellenteconomy_balance_zgems%) on items
        for (int i = 0; i < size; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasLore()) {
                    List<String> parsedLore = new ArrayList<>();
                    for (String line : meta.getLore()) {
                        parsedLore.add(PlaceholderHook.setPlaceholders(player, line));
                    }
                    meta.setLore(parsedLore);
                    item.setItemMeta(meta);
                }
            }
        }

        player.openInventory(inv);
        player.updateInventory();
    }

    private String formatMechanicDescriptionLine(String line, PetMechanic mech, int petLevel) {
        if (line == null) return "";
        
        // Command Trigger
        if (mech instanceof PetMechanic.CommandTriggerMechanic) {
            PetMechanic.CommandTriggerMechanic cmdTrigger = (PetMechanic.CommandTriggerMechanic) mech;
            double baseCh = cmdTrigger.getChance();
            for (java.util.Map.Entry<Integer, PetMechanic.ProgressiveTriggerReward> entry : cmdTrigger.getProgressiveRewards().entrySet()) {
                if (petLevel >= entry.getKey()) {
                    baseCh = entry.getValue().getChance();
                }
            }
            double chance = Math.min(1.0, baseCh + (petLevel * cmdTrigger.getLevelModifier()));
            
            double baseAdd = cmdTrigger.getBaseAdditional() + (petLevel * cmdTrigger.getAdditionalLevelModifier());
            double pctVal = cmdTrigger.getBasePercent() + (petLevel * cmdTrigger.getPercentLevelModifier());
            double multVal = cmdTrigger.getBaseMultiplier() + (petLevel * cmdTrigger.getMultiplierLevelModifier());
            if (cmdTrigger.getBaseMultiplier() == 0.0 && cmdTrigger.getMultiplierLevelModifier() == 0.0) {
                multVal = 1.0;
            }
            double finalAdd = baseAdd * multVal;

            String addStr = (finalAdd == (int) finalAdd) ? String.valueOf((int) finalAdd) : String.format("%.2f", finalAdd);
            String pctStr = (pctVal == (int) pctVal) ? String.valueOf((int) pctVal) : String.format("%.2f", pctVal);
            String multStr = (multVal == (int) multVal) ? String.valueOf((int) multVal) : String.format("%.2f", multVal);

            line = line.replace("%chance%", String.format("%.1f", chance * 100))
                       .replace("%player_pet_level_percent%", pctStr)
                       .replace("%player_pet_level_additional%", addStr)
                       .replace("%player_pet_level_multiplier%", multStr)
                       .replace("%player_pet_level_multiply%", multStr);
        }

        // Potion
        if (mech instanceof PetMechanic.PotionMechanic) {
            PetMechanic.PotionMechanic potion = (PetMechanic.PotionMechanic) mech;
            int amp = potion.getAmplifier();
            for (java.util.Map.Entry<Integer, Integer> entry : potion.getProgressiveAmps().entrySet()) {
                if (petLevel >= entry.getKey()) {
                    amp = entry.getValue();
                }
            }
            line = line.replace("%amplifier%", String.valueOf(amp + 1));
        }
        
        // Mining Gems
        if (mech instanceof PetMechanic.MiningGemsMechanic) {
            PetMechanic.MiningGemsMechanic mining = (PetMechanic.MiningGemsMechanic) mech;
            double chance = Math.min(0.35, mining.getBaseChance() + (petLevel * mining.getLevelModifier()));
            int amount = mining.getBaseAmount() + (petLevel / 30);
            line = line.replace("%chance%", String.format("%.1f", chance * 100))
                       .replace("%amount%", String.valueOf(amount));
        }

        // Combat Gems
        if (mech instanceof PetMechanic.CombatGemsMechanic) {
            PetMechanic.CombatGemsMechanic combat = (PetMechanic.CombatGemsMechanic) mech;
            double chance = Math.min(0.35, combat.getBaseChance() + (petLevel * combat.getLevelModifier()));
            int amount = combat.getBaseAmount() + (petLevel / 30);
            line = line.replace("%chance%", String.format("%.1f", chance * 100))
                       .replace("%amount%", String.valueOf(amount));
        }

        // XP Booster
        if (mech instanceof PetMechanic.XpBoosterMechanic) {
            PetMechanic.XpBoosterMechanic xp = (PetMechanic.XpBoosterMechanic) mech;
            double mult = xp.getBaseMultiplier() + (petLevel * xp.getLevelModifier());
            line = line.replace("%multiplier%", String.format("%.0f", (mult - 1.0) * 100));
        }

        // Shield
        if (mech instanceof PetMechanic.ShieldMechanic) {
            PetMechanic.ShieldMechanic shield = (PetMechanic.ShieldMechanic) mech;
            double chance = Math.min(0.25, shield.getBaseChance() + (petLevel * shield.getLevelModifier()));
            double pct = Math.min(0.50, shield.getBaseMitigation() + (petLevel * shield.getMitigationModifier()));
            line = line.replace("%chance%", String.format("%.1f", chance * 100))
                       .replace("%mitigation%", String.format("%.0f", pct * 100));
        }

        // Lifesteal
        if (mech instanceof PetMechanic.LifestealMechanic) {
            PetMechanic.LifestealMechanic lifesteal = (PetMechanic.LifestealMechanic) mech;
            double chance = Math.min(0.25, lifesteal.getBaseChance() + (petLevel * lifesteal.getLevelModifier()));
            double pct = Math.min(0.20, lifesteal.getBasePercent() + (petLevel * lifesteal.getPercentModifier()));
            line = line.replace("%chance%", String.format("%.1f", chance * 100))
                       .replace("%percent%", String.format("%.0f", pct * 100));
        }

        // Double Drops
        if (mech instanceof PetMechanic.DoubleDropsMechanic) {
            PetMechanic.DoubleDropsMechanic doubleDrops = (PetMechanic.DoubleDropsMechanic) mech;
            double chance = Math.min(0.40, doubleDrops.getBaseChance() + (petLevel * doubleDrops.getLevelModifier()));
            line = line.replace("%chance%", String.format("%.1f", chance * 100));
        }

        // Combat Attack
        if (mech instanceof PetMechanic.CombatAttackMechanic) {
            PetMechanic.CombatAttackMechanic attack = (PetMechanic.CombatAttackMechanic) mech;
            double dmg = attack.getDamage(petLevel);
            double cd = Math.max(5.0, attack.getBaseCooldown() - (petLevel * attack.getCooldownModifier()));
            line = line.replace("%damage%", String.format("%.1f", dmg))
                       .replace("%cooldown%", String.format("%.1f", cd));
        }

        // Area Heal
        if (mech instanceof PetMechanic.AreaHealMechanic) {
            PetMechanic.AreaHealMechanic heal = (PetMechanic.AreaHealMechanic) mech;
            double amount = heal.getBaseHeal() + (petLevel * heal.getHealModifier());
            double rad = heal.getRadius() + (petLevel * 0.03);
            double cd = Math.max(15.0, heal.getBaseCooldown() - (petLevel * heal.getCooldownModifier()));
            line = line.replace("%heal%", String.format("%.1f", amount))
                       .replace("%radius%", String.format("%.1f", rad))
                       .replace("%cooldown%", String.format("%.1f", cd));
        }

        return line;
    }
}
