package com.rzxpets;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PetManager {
    private final RZXPets plugin;
    private final Map<UUID, Entity> activeEntities;
    private final Map<UUID, AttackAnimation> activeAnimations;
    private final Map<UUID, Long> combatCooldowns;
    private boolean tasksStarted = false;

    public PetManager(RZXPets plugin) {
        this.plugin = plugin;
        this.activeEntities = new HashMap<>();
        this.activeAnimations = new HashMap<>();
        this.combatCooldowns = new HashMap<>();
    }

    public void startTasks() {
        if (tasksStarted || !plugin.isEnabled()) return;
        tasksStarted = true;
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupGhostNameplates, 40L);
        startPetFollowTask();
    }

    public boolean isPet(Entity entity) {
        return activeEntities.containsValue(entity);
    }

    private void cleanupGhostNameplates() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand) {
                    if (entity.getScoreboardTags().contains("rzxpets_nameplate") || "Armor Stand".equals(entity.getCustomName())) {
                        entity.remove();
                    }
                }
            }
        }
    }

    public void summonPet(Player player, String typeId) {
        if (!plugin.arePetsAllowedAt(player)) {
            String blockedMsg = plugin.getHooksConfig().getString("worldguard.messages.summon-blocked", "&c&l[RZXPets] &cYou cannot summon pets in this region!");
            player.sendMessage(PlaceholderHook.color(blockedMsg));
            return;
        }

        despawnPet(player);

        PetType type = PetType.getById(typeId);
        if (type == null) return;

        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());
        playerData.setActivePet(typeId.toLowerCase());

        Location spawnLoc = player.getLocation().add(0.5, 0.5, 0.5);
        Entity pet = null;
        try {
            pet = player.getWorld().spawn(spawnLoc, (Class<Entity>) type.getEntityType().getEntityClass(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {});
        } catch (Throwable t) {
            try {
                pet = player.getWorld().spawnEntity(spawnLoc, type.getEntityType());
            } catch (IllegalStateException e) {
                com.rzxpets.rzx.RZXLoggerService.warning("Could not spawn entity " + type.getEntityType() + ". Summoning physical entity was skipped, but boosters remain active.");
            }
        }

        if (pet != null) {
            pet.setPersistent(true);
            pet.setGravity(false); // Float/hover at shoulder height
            pet.setInvulnerable(true);
            pet.setSilent(true);

            // Enforce custom nametag directly on the pet entity (support custom renamed name)
            PetData petData = playerData.getPet(typeId);
            int petLvl = petData != null ? petData.getLevel() : 1;
            String baseName;
            if (petData != null && petData.getName() != null && !petData.getName().isEmpty()) {
                String colorPrefix = PlaceholderHook.getColorPrefix(type.getDisplayName());
                baseName = colorPrefix + petData.getName();
            } else {
                baseName = type.getDisplayName();
            }
            String nameStr = PlaceholderHook.color(baseName + " &7[Lvl " + petLvl + "]");
            pet.setCustomName(nameStr);
            pet.setCustomNameVisible(true);

            if (pet instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) pet;
                le.setRemoveWhenFarAway(false);
                le.setCanPickupItems(false);
                le.setCollidable(false);
                if (pet instanceof org.bukkit.entity.Mob) {
                    ((org.bukkit.entity.Mob) pet).setAware(false);
                }
            }

            // Enforce flying state for bats
            if (pet instanceof org.bukkit.entity.Bat) {
                ((org.bukkit.entity.Bat) pet).setAwake(true);
            }

            // Apply static color variant for parrots so they don't randomly morph
            if (pet instanceof org.bukkit.entity.Parrot) {
                org.bukkit.entity.Parrot parrot = (org.bukkit.entity.Parrot) pet;
                String varName = type.getVariant();
                org.bukkit.entity.Parrot.Variant variant = org.bukkit.entity.Parrot.Variant.CYAN;
                if (varName != null && !varName.isEmpty()) {
                    try {
                        variant = org.bukkit.entity.Parrot.Variant.valueOf(varName.toUpperCase());
                    } catch (Exception e) {
                        // ignore and use default cyan
                    }
                }
                parrot.setVariant(variant);
            }

            activeEntities.put(player.getUniqueId(), pet);
        }

        // Clean up nearby nameplate ArmorStands to prevent stacking duplicates
        for (Entity nearby : player.getNearbyEntities(5, 5, 5)) {
            if (nearby instanceof ArmorStand && (nearby.getScoreboardTags().contains("rzxpets_nameplate") || "Armor Stand".equals(nearby.getCustomName()))) {
                nearby.remove();
            }
        }

        // Run SUMMON command triggers
        PetData petData = playerData.getPet(typeId);
        int petLvl = petData != null ? petData.getLevel() : 1;
        if (petData != null) {
            for (PetMechanic mechanic : type.getMechanics()) {
                if (petLvl >= mechanic.getMinLevel()) {
                    mechanic.onTrigger(player, petData, "SUMMON", null);
                }
            }
        }
    }

    public void despawnPet(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData playerData = plugin.getPlayerData(uuid);
        String activeId = playerData.getActivePet();

        if (activeId != null) {
            PetType type = PetType.getById(activeId);
            if (type != null) {
                for (PetMechanic mechanic : type.getMechanics()) {
                    mechanic.onDismiss(player);
                    PetData petData = playerData.getPet(activeId);
                    if (petData != null && petData.getLevel() >= mechanic.getMinLevel()) {
                        mechanic.onTrigger(player, petData, "DISMISS", null);
                    }
                }
            }
        }

        playerData.setActivePet(null);

        if (player.getShoulderEntityLeft() != null) {
            player.setShoulderEntityLeft(null);
        }
        if (player.getShoulderEntityRight() != null) {
            player.setShoulderEntityRight(null);
        }

        Entity pet = activeEntities.remove(uuid);
        if (pet != null && pet.isValid()) {
            pet.remove();
        }
        activeAnimations.remove(uuid);

        // Clean up creative flight capability when companion is unequipped
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            if (!player.hasPermission("rzxpets.admin") && !player.hasPermission("rzxpets.bypass.flight")) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    public void despawnAll() {
        for (Entity entity : activeEntities.values()) {
            if (entity.isValid()) entity.remove();
        }
        activeEntities.clear();
    }

    private void startPetFollowTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        UUID uuid = player.getUniqueId();
                        PlayerData playerData = plugin.getPlayerData(uuid);
                        String activeId = playerData != null ? playerData.getActivePet() : null;
                        if (activeId == null) continue;

                        PetType petType = PetType.getById(activeId);
                        if (petType == null) continue;

                        if (!plugin.arePetsAllowedAt(player)) {
                            despawnPet(player);
                            String blockedMsg = plugin.getHooksConfig().getString("worldguard.messages.summon-blocked", "&c&l[RZXPets] &cYou cannot summon pets in this region!");
                            player.sendMessage(PlaceholderHook.color(blockedMsg));
                            continue;
                        }

                        Entity pet = activeEntities.get(uuid);
                        // Auto-respawn invalidated or unspawned entities
                        if (pet == null || !pet.isValid()) {
                            if (player.isOnline() && !player.isDead()) {
                                summonPet(player, activeId);
                                pet = activeEntities.get(uuid);
                            }
                            if (pet == null || !pet.isValid()) continue;
                        }

                        PetData petData = playerData.getPet(activeId);
                        
                        // Apply continuous potion or flight effects (onTick)
                        if (petData != null) {
                            for (PetMechanic mechanic : petType.getMechanics()) {
                                if (petData.getLevel() >= mechanic.getMinLevel()) {
                                    mechanic.onTick(player, petData);
                                }
                            }
                            // Level 100 active companions grant player creative flight capability
                            if (petData.getLevel() >= 100) {
                                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                                    if (!player.getAllowFlight()) {
                                        player.setAllowFlight(true);
                                    }
                                }
                            }
                        }

                        // Float pet at a stable shoulder offset (matching EcoPets Display mechanics)
                        Location playerLoc = player.getLocation();
                        org.bukkit.util.Vector dir = playerLoc.getDirection().clone();
                        dir.setY(0);
                        if (dir.lengthSquared() < 0.0001) {
                            double yawRad = Math.toRadians(playerLoc.getYaw());
                            dir = new org.bukkit.util.Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
                        } else {
                            dir.normalize();
                        }
                        
                        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-dir.getZ(), 0, dir.getX());
                        if (right.lengthSquared() > 0) {
                            right.normalize();
                        }

                        // Anchor: 0.75 blocks behind, 0.75 blocks to the right
                        org.bukkit.util.Vector offset = dir.clone().multiply(-0.75).add(right.clone().multiply(0.75));
                        Location targetLoc = playerLoc.clone().add(offset).add(0, 1.2, 0);
                        targetLoc.setYaw(playerLoc.getYaw());
                        targetLoc.setPitch(playerLoc.getPitch());

                        // Keep in sync
                        if (!player.getWorld().equals(pet.getWorld())) {
                            pet.teleport(targetLoc);
                            activeAnimations.remove(uuid);
                        } else {
                            AttackAnimation anim = activeAnimations.get(uuid);
                            if (anim != null && anim.getTargetEntity().isValid() && anim.getTargetEntity().getWorld().equals(pet.getWorld())) {
                                anim.incrementTick();
                                int ticks = anim.getTickCount();
                                Location enemyLoc = anim.getTargetEntity().getLocation().add(0, 0.5, 0);

                                if (ticks <= 10) {
                                    // Slide to enemy
                                    double ratio = ticks / 10.0;
                                    Location nextLoc = anim.getStartLocation().clone().add(enemyLoc.clone().subtract(anim.getStartLocation()).multiply(ratio));
                                    pet.teleport(nextLoc);

                                    if (ticks == 10 && !anim.isDamageDealt()) {
                                        anim.setDamageDealt(true);
                                        anim.getTargetEntity().damage(anim.getDamage(), player);
                                        try {
                                            anim.getTargetEntity().getWorld().spawnParticle(
                                                org.bukkit.Particle.valueOf(anim.getParticle().toUpperCase()),
                                                enemyLoc,
                                                8, 0.2, 0.2, 0.2, 0.1
                                            );
                                        } catch (Exception ignored) {}
                                        try {
                                            anim.getTargetEntity().getWorld().playSound(
                                                enemyLoc,
                                                org.bukkit.Sound.valueOf(anim.getSound().toUpperCase()),
                                                1.0f, 1.0f
                                            );
                                        } catch (Exception ignored) {}
                                        
                                        player.sendMessage(PlaceholderHook.color("&6&l[Companion Attack] &fYour companion attacked &c" + anim.getTargetEntity().getName() + " &ffor &a" + String.format("%.1f", anim.getDamage()) + " damage!"));
                                    }
                                } else if (ticks <= 20) {
                                    // Return back to player shoulder
                                    double ratio = (ticks - 10) / 10.0;
                                    Location nextLoc = enemyLoc.clone().add(targetLoc.clone().subtract(enemyLoc).multiply(ratio));
                                    pet.teleport(nextLoc);
                                } else {
                                    activeAnimations.remove(uuid);
                                    pet.teleport(targetLoc);
                                }
                            } else {
                                activeAnimations.remove(uuid);
                                double distance = targetLoc.distance(pet.getLocation());
                                double maxDist = plugin.getConfig().getDouble("follow.teleport-distance", 15.0);
                                if (distance > maxDist) {
                                    pet.teleport(targetLoc);
                                } else {
                                    pet.teleport(targetLoc);
                                }
                            }
                        }

                        // Enforce flying state for bats
                        if (pet instanceof org.bukkit.entity.Bat) {
                            ((org.bukkit.entity.Bat) pet).setAwake(true);
                        }
                    } catch (Exception e) {
                        com.rzxpets.rzx.RZXExceptionHandler.handle("Pet Follow Task for player " + player.getName(), player, null, e);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // Run every tick for smooth teleport follow
    }

    public void handlePlayerCombat(Player player, LivingEntity target) {
        UUID uuid = player.getUniqueId();
        Entity pet = activeEntities.get(uuid);
        if (pet == null || !pet.isValid()) return;

        PlayerData playerData = plugin.getPlayerData(uuid);
        String activeId = playerData.getActivePet();
        if (activeId == null) return;
        PetType petType = PetType.getById(activeId);
        if (petType == null) return;

        PetData petData = playerData.getPet(activeId);
        if (petData == null) return;

        PetMechanic.CombatAttackMechanic attackMech = null;
        for (PetMechanic mech : petType.getMechanics()) {
            if (mech instanceof PetMechanic.CombatAttackMechanic && petData.getLevel() >= mech.getMinLevel()) {
                attackMech = (PetMechanic.CombatAttackMechanic) mech;
                break;
            }
        }
        if (attackMech == null) return;

        long now = System.currentTimeMillis();
        long lastUsed = combatCooldowns.getOrDefault(uuid, 0L);
        long cooldownMs = (long) (attackMech.getCooldownTicks(petData.getLevel()) * 50);
        if (now - lastUsed < cooldownMs) return;

        combatCooldowns.put(uuid, now);
        activeAnimations.put(uuid, new AttackAnimation(
            target,
            pet.getLocation().clone(),
            attackMech.getDamage(petData.getLevel()),
            attackMech.getParticle(),
            attackMech.getSound()
        ));
    }

    private static class AttackAnimation {
        private final LivingEntity targetEntity;
        private final Location startLocation;
        private final double damage;
        private final String particle;
        private final String sound;
        private int tickCount = 0;
        private boolean damageDealt = false;

        public AttackAnimation(LivingEntity targetEntity, Location startLocation, double damage, String particle, String sound) {
            this.targetEntity = targetEntity;
            this.startLocation = startLocation;
            this.damage = damage;
            this.particle = particle;
            this.sound = sound;
        }

        public LivingEntity getTargetEntity() {
            return targetEntity;
        }

        public Location getStartLocation() {
            return startLocation;
        }

        public double getDamage() {
            return damage;
        }

        public String getParticle() {
            return particle;
        }

        public String getSound() {
            return sound;
        }

        public int getTickCount() {
            return tickCount;
        }

        public void incrementTick() {
            this.tickCount++;
        }

        public boolean isDamageDealt() {
            return damageDealt;
        }

        public void setDamageDealt(boolean damageDealt) {
            this.damageDealt = damageDealt;
        }
    }
}
