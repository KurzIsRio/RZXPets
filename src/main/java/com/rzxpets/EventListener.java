package com.rzxpets;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.entity.LivingEntity;

public class EventListener implements Listener {
    private final RZXPets plugin;

    public EventListener(RZXPets plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && plugin.getPetManager().isPet(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityMount(EntityMountEvent event) {
        if (event.getMount() instanceof Player && (plugin.getPetManager().isPet(event.getEntity()) || event.getEntity() instanceof org.bukkit.entity.Parrot)) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Sanitize inventory on join to purge any leaked GUI items
        sanitizePlayerInventory(player);

        // Remove any parrots currently on the player's shoulder to fix duplicates
        if (player.getShoulderEntityLeft() != null) {
            player.setShoulderEntityLeft(null);
        }
        if (player.getShoulderEntityRight() != null) {
            player.setShoulderEntityRight(null);
        }

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        
        if (data.getActivePet() != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getPetManager().summonPet(player, data.getActivePet());
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPetManager().despawnPet(player);
        plugin.unloadPlayerData(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data.getActivePet() != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getPetManager().summonPet(player, data.getActivePet());
                }
            }, 10L);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data.getActivePet() != null) {
            plugin.getPetManager().summonPet(player, data.getActivePet());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();

        RZXPets.debug(2, "[Debug-Mechanics] BlockBreakEvent fired for player " + player.getName() + " on block " + material + " (Event Cancelled: " + event.isCancelled() + ")");

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        String activeId = data.getActivePet();
        if (activeId == null) {
            RZXPets.debug(2, "[Debug-Mechanics] BlockBreak aborted: Player has no active companion pet equipped.");
            return;
        }

        PetData petData = data.getPet(activeId);
        if (petData == null) {
            RZXPets.debug(2, "[Debug-Mechanics] BlockBreak aborted: Active pet data for ID " + activeId + " is null.");
            return;
        }
        if (petData.getType() == null) {
            RZXPets.debug(2, "[Debug-Mechanics] BlockBreak aborted: PetType definition for active ID " + activeId + " is null.");
            return;
        }

        RZXPets.debug(2, "[Debug-Mechanics] Processing " + petData.getType().getMechanics().size() + " mechanics on pet " + activeId);
        for (PetMechanic mechanic : petData.getType().getMechanics()) {
            if (petData.getLevel() >= mechanic.getMinLevel()) {
                RZXPets.debug(2, "[Debug-Mechanics] Triggering trigger BLOCK_BREAK / DOUBLE_DROPS for mechanic " + mechanic.getName());
                mechanic.onTrigger(player, petData, "BLOCK_BREAK", material);
                mechanic.onTrigger(player, petData, "DOUBLE_DROPS", event);
            } else {
                RZXPets.debug(2, "[Debug-Mechanics] Mechanic " + mechanic.getName() + " not triggered: Pet Level " + petData.getLevel() + " is below min-level " + mechanic.getMinLevel());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerData data = plugin.getPlayerData(killer.getUniqueId());
        String activeId = data.getActivePet();
        if (activeId == null) {
            return;
        }

        PetData petData = data.getPet(activeId);
        if (petData == null || petData.getType() == null) {
            return;
        }

        for (PetMechanic mechanic : petData.getType().getMechanics()) {
            if (petData.getLevel() >= mechanic.getMinLevel()) {
                mechanic.onTrigger(killer, petData, "MOB_KILL", event.getEntity().getType());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player player = null;
        if (event.getDamager() instanceof Player) {
            player = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                player = (Player) proj.getShooter();
            }
        }

        if (player != null) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            String activeId = data.getActivePet();
            if (activeId != null) {
                PetData petData = data.getPet(activeId);
                if (petData != null && petData.getType() != null) {
                    double[] dmg = { event.getDamage() };
                    for (PetMechanic mech : petData.getType().getMechanics()) {
                        if (petData.getLevel() >= mech.getMinLevel()) {
                            mech.onTrigger(player, petData, "DEAL_DAMAGE", dmg);
                        }
                    }
                    event.setDamage(dmg[0]);
                }
            }

            if (event.getEntity() instanceof LivingEntity) {
                plugin.getPetManager().handlePlayerCombat(player, (LivingEntity) event.getEntity());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        String activeId = data.getActivePet();
        if (activeId != null) {
            PetData petData = data.getPet(activeId);
            if (petData != null && petData.getType() != null) {
                double[] dmg = { event.getDamage() };
                for (PetMechanic mech : petData.getType().getMechanics()) {
                    if (petData.getLevel() >= mech.getMinLevel()) {
                        mech.onTrigger(player, petData, "TAKE_DAMAGE", dmg);
                    }
                }
                event.setDamage(dmg[0]);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        String activeId = data.getActivePet();
        if (activeId != null) {
            PetData petData = data.getPet(activeId);
            if (petData != null && petData.getType() != null) {
                int[] xp = { event.getAmount() };
                for (PetMechanic mech : petData.getType().getMechanics()) {
                    if (petData.getLevel() >= mech.getMinLevel()) {
                        mech.onTrigger(player, petData, "XP_GAIN", xp);
                    }
                }
                event.setAmount(xp[0]);
            }
        }
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
                org.bukkit.persistence.PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                boolean isGuiItem = container.has(actionKey, PersistentDataType.STRING) ||
                                    container.has(priceKey, PersistentDataType.INTEGER) ||
                                    container.has(currencyKey, PersistentDataType.STRING);

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

    private boolean isCustomGui(org.bukkit.inventory.InventoryView view) {
        if (view == null || view.getTopInventory() == null) return false;
        
        org.bukkit.inventory.InventoryHolder holder = view.getTopInventory().getHolder();
        if (holder instanceof ShopHolder || holder instanceof StorageHolder || holder instanceof UpgradeHolder) {
            return true;
        }
        
        String rawTitle = view.getTitle();
        if (rawTitle != null) {
            String title = org.bukkit.ChatColor.stripColor(rawTitle);
            if (title.contains("PET STORAGE SYSTEM") || 
                title.contains("PET ADOPTION SHOP") || 
                title.contains("YOUR PET STORAGE") || 
                title.contains("SELECT PET TO UPGRADE") || 
                title.contains("UPGRADE:")) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (isCustomGui(event.getView())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.setItemOnCursor(null);
                sanitizePlayerInventory(player);
                plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (isCustomGui(event.getView())) {
            if (event.getPlayer() instanceof Player player) {
                player.setItemOnCursor(null);
                sanitizePlayerInventory(player);
                plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // 1. Prevent errors if the player clicks outside the chest window
        if (event.getClickedInventory() == null) {
            return;
        }

        // 2. Check if the top inventory belongs to our custom holder/GUI
        if (!isCustomGui(event.getView())) {
            return;
        }

        // 3. CRITICAL: Cancel the event so players cannot take buttons/borders out
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        Player player = (Player) event.getWhoClicked();

        // Immediately clear cursor item to prevent double-click or hotbar-swap collecting GUI items into cursor
        if (player.getItemOnCursor() != null && player.getItemOnCursor().getType() != Material.AIR) {
            player.setItemOnCursor(null);
        }

        // Purge any leaked GUI items from player inventory
        sanitizePlayerInventory(player);
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);

        // 4. Ensure they clicked the GUI itself, not their own inventory at the bottom
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        // Also prevent shift-clicks, hotbar keys etc. from doing any actions in the GUI
        if (event.getClick().isShiftClick() || 
            event.getClick().isKeyboardClick() ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_MOVE_AND_READD) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return;
        }

        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey priceKey = new NamespacedKey(plugin, "price");
        NamespacedKey currencyKey = new NamespacedKey(plugin, "currency");

        String action = clickedItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isEmpty()) {
            return;
        }

        int price = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(priceKey, PersistentDataType.INTEGER, 0);
        String currency = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(currencyKey, PersistentDataType.STRING, "free");

        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());

        String[] parts = action.split(" ");
        String subAction = parts[0].toLowerCase();

        if (subAction.equals("open") && parts.length >= 2 && "upgrade".equalsIgnoreCase(parts[1])) {
            boolean ownsPet = false;
            for (String petId : PetType.values().stream().map(PetType::getId).toList()) {
                if (playerData.hasPet(petId)) {
                    ownsPet = true;
                    break;
                }
            }
            if (!ownsPet && !player.hasPermission("rzxpets.perms.admin") && !player.hasPermission("rzxpets.admin")) {
                player.sendMessage("§cYou must adopt a pet first to access upgrades!");
                return;
            }
        }

        if (subAction.equals("buy")) {
            if (parts.length < 2) return;
            String petId = parts[1].toLowerCase();
            int limit = playerData.getMaxLimit(player);
            if (playerData.getPets().size() >= limit && !playerData.hasPet(petId)) {
                player.sendMessage("§cYou have reached your maximum owned pets limit of " + limit + "!");
                return;
            }
            if (playerData.hasPet(petId)) {
                player.sendMessage("§cYou already own this pet!");
                return;
            }
        } else if (subAction.equals("addxp")) {
            if (parts.length < 2) return;
            String targetPetId = parts[1].toLowerCase();
            if (targetPetId.equals("active")) {
                String activeId = playerData.getActivePet();
                if (activeId == null) {
                    player.sendMessage("§cYou do not have an active pet summoned.");
                    return;
                }
            } else {
                if (!playerData.hasPet(targetPetId)) {
                    player.sendMessage("§cYou do not own this pet.");
                    return;
                }
            }
        }

        // Process currency deduction
        if (price > 0 && ("zgems".equalsIgnoreCase(currency) || "ZGems".equalsIgnoreCase(currency))) {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                player.sendMessage("§cPlaceholderAPI is missing. Purchase cannot be completed.");
                return;
            }

            double balance = PlaceholderHook.getExcellentEconomyBalance(player, currency);
            if (balance < price) {
                player.sendMessage("§cYou do not have enough zGems! (Need " + price + ")");
                return;
            }

            // Deduct ExcellentEconomy currency silently
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "zgems take " + player.getName() + " " + price + " -s");
        }

        if (price > 0 && "item".equalsIgnoreCase(currency)) {
            NamespacedKey itemIdKey = new NamespacedKey(plugin, "item_id");
            String itemId = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(itemIdKey, PersistentDataType.STRING, "");
            if (itemId.isEmpty()) {
                player.sendMessage("§cError: Custom item ID is missing in configuration.");
                return;
            }

            ItemStack matchItem = getCustomItemStack(itemId);
            String itemName = matchItem != null && matchItem.hasItemMeta() && matchItem.getItemMeta().hasDisplayName()
                    ? matchItem.getItemMeta().getDisplayName()
                    : (matchItem != null ? matchItem.getType().name().replace("_", " ") : itemId);

            if (!hasItem(player, itemId, price)) {
                player.sendMessage(PlaceholderHook.color("&cYou do not have the required items! (Need " + price + "x " + itemName + "&c)"));
                return;
            }

            takeItem(player, itemId, price);
        }

        // Execute action
        switch (subAction) {
            case "buy": {
                String petId = parts[1].toLowerCase();
                playerData.addPet(petId);
                plugin.getDataManager().savePlayerData(playerData);
                PetType type = PetType.getById(petId);
                String displayName = type != null ? type.getDisplayName() : petId;
                player.sendMessage(PlaceholderHook.color("&aAdopted " + displayName + " &apet! Open the storage menu to summon it."));
                com.rzxpets.rzx.RZXAuditService.log(player.getName(), "Purchased pet: " + petId);
                com.rzxpets.rzx.RZXBus.publish(playerData);
                break;
            }

            case "addxp": {
                String targetPetId = parts[1].toLowerCase();
                int xpAmount = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1000;
                
                String targetId = targetPetId.equals("active") ? playerData.getActivePet() : targetPetId;
                if (targetId != null) {
                    PetData petData = playerData.getPet(targetId);
                    if (petData != null) {
                        int oldLevel = petData.getLevel();
                        petData.addXp(xpAmount);
                        plugin.getDataManager().savePlayerData(playerData);
                        
                        PetType type = PetType.getById(targetId);
                        String displayName = type != null ? type.getDisplayName() : targetId;
                        player.sendMessage(PlaceholderHook.color("&aAdded +" + xpAmount + " XP to " + displayName + "&a."));
                        
                        if (petData.getLevel() > oldLevel) {
                            player.sendMessage(PlaceholderHook.color("&aYour " + displayName + " &apet has leveled up! &7[Lvl " + oldLevel + " -> " + petData.getLevel() + "]"));
                        }
                    }
                }
                break;
            }

            case "select_upgrade": {
                String petId = parts[1].toLowerCase();
                plugin.getShopGui().openPetUpgradeDetailMenu(player, petId);
                return;
            }

            case "buy_level": {
                String petId = parts[1].toLowerCase();
                PlayerData data = plugin.getPlayerData(player.getUniqueId());
                PetData petData = data.getPet(petId);
                if (petData == null) {
                    // Initialize pet data if they own it but it's not saved in the DB yet
                    data.addPet(petId);
                    petData = data.getPet(petId);
                }

                int currentLevel = petData != null ? petData.getLevel() : 1;
                if (currentLevel >= 100) {
                    player.sendMessage(PlaceholderHook.color("&c&l[RZXPets] &cYour companion is already at the maximum level (100)!"));
                    break;
                }
                int cost = 50 + (currentLevel * 15);
                double balance = PlaceholderHook.getExcellentEconomyBalance(player, "zgems");
                if (balance < cost) {
                    player.sendMessage(PlaceholderHook.color("&c&l[RZXPets] &cYou do not have enough zGems! (Need " + cost + " zGems)"));
                    break;
                }
                
                if (petData != null) {
                    // Deduct cost
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "zgems take " + player.getName() + " " + cost + " -s");
                    
                    int nextLvl = petData.getLevel() + 1;
                    petData.setLevel(nextLvl);
                    plugin.getDataManager().savePlayerData(data);
                    
                    PetType type = PetType.getById(petId);
                    String displayName = type != null ? type.getDisplayName() : petId;
                    player.sendMessage(PlaceholderHook.color("&a&l[RZXPets] &aYour " + displayName + " &apet leveled up to &b&l" + nextLvl + "&a!"));
                    com.rzxpets.rzx.RZXAuditService.log(player.getName(), "Upgraded pet " + petId + " to level " + nextLvl + " (Cost: " + cost + " zGems)");
                    com.rzxpets.rzx.RZXBus.publish(petData);
                    
                    // Play level up sound (ting)
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    
                    // Re-summon if this pet is currently active to refresh level tag instantly
                    if (petId.equalsIgnoreCase(data.getActivePet())) {
                        plugin.getPetManager().summonPet(player, petId);
                    }
                    
                    // Refresh menu on next tick
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getShopGui().openPetUpgradeDetailMenu(player, petId));
                }
                break;
            }

            case "upgrade_limit": {
                int targetLimit = Integer.parseInt(parts[1]);
                int cost = parts.length >= 3 ? Integer.parseInt(parts[2]) : 200;
                
                int currentLimit = playerData.getMaxLimit(player);
                if (currentLimit >= targetLimit) {
                    player.sendMessage(PlaceholderHook.color("&c&l[RZXPets] &cYou have already unlocked this storage limit (or higher)!"));
                    break;
                }
                
                double balance = PlaceholderHook.getExcellentEconomyBalance(player, "zgems");
                if (balance < cost) {
                    player.sendMessage(PlaceholderHook.color("&c&l[RZXPets] &cYou do not have enough zGems! (Need " + cost + " zGems)"));
                    break;
                }
                
                // Deduct cost
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "zgems take " + player.getName() + " " + cost + " -s");
                
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " permission set rzxpets.limit." + targetLimit + " true");
                player.sendMessage(PlaceholderHook.color("&a&l[RZXPets] &aUpgraded! You can now store up to " + targetLimit + " pets."));
                com.rzxpets.rzx.RZXAuditService.log(player.getName(), "Upgraded storage limit to " + targetLimit + " (Cost: " + cost + " zGems)");
                
                // Play level up sound (ting)
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                
                // Refresh menu on next tick
                String active = playerData.getActivePet();
                if (active != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getShopGui().openPetUpgradeDetailMenu(player, active));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getShopGui().openUpgradeSelectPetMenu(player));
                }
                break;
            }

            case "dismiss": {
                plugin.getPetManager().despawnPet(player);
                player.sendMessage(PlaceholderHook.color("&cYou dismissed your active pet."));
                com.rzxpets.rzx.RZXAuditService.log(player.getName(), "Dismissed active pet.");
                break;
            }

            case "summon": {
                String petId = parts[1].toLowerCase();
                if (!playerData.hasPet(petId)) {
                    player.sendMessage(PlaceholderHook.color("&cYou do not own this pet yet!"));
                    break;
                }
                plugin.getPetManager().summonPet(player, petId);
                PetType type = PetType.getById(petId);
                String displayName = type != null ? type.getDisplayName() : petId;
                player.sendMessage(PlaceholderHook.color("&aYou summoned your " + displayName + " &acompanion!"));
                com.rzxpets.rzx.RZXAuditService.log(player.getName(), "Summoned pet: " + petId);
                break;
            }

            case "open": {
                if (parts.length < 2) break;
                String targetSection = parts[1].toLowerCase();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (targetSection.equalsIgnoreCase("storage")) {
                        plugin.getShopGui().openStorageMenu(player);
                    } else if (targetSection.equalsIgnoreCase("upgrade") || targetSection.equalsIgnoreCase("upgrade_select")) {
                        plugin.getShopGui().openUpgradeSelectPetMenu(player);
                    } else {
                        plugin.getShopGui().openMenu(player, targetSection);
                    }
                });
                return;
            }
        }

        // Refresh inventory GUI
        if (event.getInventory().getHolder() instanceof StorageHolder) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getShopGui().openStorageMenu(player);
            });
        } else if (event.getInventory().getHolder() instanceof UpgradeHolder) {
            UpgradeHolder uHolder = (UpgradeHolder) event.getInventory().getHolder();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (uHolder.getPetId() != null) {
                    plugin.getShopGui().openPetUpgradeDetailMenu(player, uHolder.getPetId());
                } else {
                    plugin.getShopGui().openUpgradeSelectPetMenu(player);
                }
            });
        } else if (event.getInventory().getHolder() instanceof ShopHolder) {
            ShopHolder sHolder = (ShopHolder) event.getInventory().getHolder();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getShopGui().openMenu(player, sHolder.getSectionName());
            });
        }
    }

    public boolean hasItem(Player player, String itemId, int amount) {
        if (itemId == null || itemId.isEmpty()) return false;
        
        ItemStack matchItem = getCustomItemStack(itemId);
        if (matchItem == null) return false;
        
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isSimilar(item, matchItem)) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    public boolean takeItem(Player player, String itemId, int amount) {
        if (!hasItem(player, itemId, amount)) return false;
        
        ItemStack matchItem = getCustomItemStack(itemId);
        if (matchItem == null) return false;
        
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isSimilar(item, matchItem)) {
                if (item.getAmount() > left) {
                    item.setAmount(item.getAmount() - left);
                    left = 0;
                    break;
                } else {
                    left -= item.getAmount();
                    contents[i] = null;
                }
            }
        }
        player.getInventory().setContents(contents);
        player.updateInventory();
        return left == 0;
    }

    private boolean isSimilar(ItemStack item1, ItemStack item2) {
        if (item1.getType() != item2.getType()) return false;
        if (item1.hasItemMeta() && item2.hasItemMeta()) {
            if (item1.getItemMeta().hasCustomModelData() && item2.getItemMeta().hasCustomModelData()) {
                if (item1.getItemMeta().getCustomModelData() != item2.getItemMeta().getCustomModelData()) {
                    return false;
                }
            }
        }
        return true;
    }

    public ItemStack getCustomItemStack(String itemId) {
        String[] parts = itemId.split(":", 2);
        if (parts.length < 2) {
            Material material = Material.getMaterial(itemId.toUpperCase());
            return material != null ? new ItemStack(material) : null;
        }
        
        String prefix = parts[0].toLowerCase();
        String id = parts[1];
        
        if (prefix.equals("vanilla")) {
            Material material = Material.getMaterial(id.toUpperCase());
            return material != null ? new ItemStack(material) : null;
        }
        
        if (prefix.equals("oraxen") && Bukkit.getPluginManager().getPlugin("Oraxen") != null) {
            try {
                Object item = Class.forName("io.thana.oraxen.api.OraxenItems")
                                   .getMethod("getItemById", String.class)
                                   .invoke(null, id);
                if (item instanceof ItemStack) {
                    return (ItemStack) item;
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (prefix.equals("itemsadder") && Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Object stack = Class.forName("dev.lone.itemsadder.api.CustomStack")
                                    .getMethod("getInstance", String.class)
                                    .invoke(null, id);
                if (stack != null) {
                    Object itemStack = stack.getClass().getMethod("getItemStack").invoke(stack);
                    if (itemStack instanceof ItemStack) {
                        return (ItemStack) itemStack;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        Material material = Material.getMaterial(id.toUpperCase());
        return material != null ? new ItemStack(material) : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (plugin.getPetManager().isPet(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
