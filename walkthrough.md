# Walkthrough - RZXCore Integration & GUI Refresh Fixes

We successfully declared `RZXCore` as a hard required dependency, resolved the unresolved GUI inventory holder classes (`ShopHolder`, `StorageHolder`, and `UpgradeHolder`) by creating them as new standalone classes, and implemented a compatibility/bridge package `me.rzx.core` to ensure clean compatibility with the plugin's boot hooks.

---

## 🛠️ Issues Found & Fixed (Integration Phase)

### 1. RZXCore Dependency Declarations (`pom.xml` & `plugin.yml`)
*   Declared the provided compile dependency `com.rzx:core:1.0.0-SNAPSHOT` in `pom.xml`.
*   Verified that `depend: [RZXCore]` is declared in `plugin.yml`.

### 2. Missing GUI Holder Types (`ShopHolder.java`, `StorageHolder.java`, `UpgradeHolder.java`)
*   Created [ShopHolder.java](file:///c:/Users/Hi/.gemini/antigravity/scratch/zcompanions-project/src/main/java/com/rzxpets/ShopHolder.java) to hold custom shop UI categories.
*   Created [StorageHolder.java](file:///c:/Users/Hi/.gemini/antigravity/scratch/zcompanions-project/src/main/java/com/rzxpets/StorageHolder.java) to manage pet storage menu state.
*   Created [UpgradeHolder.java](file:///c:/Users/Hi/.gemini/antigravity/scratch/zcompanions-project/src/main/java/com/rzxpets/UpgradeHolder.java) supporting no-arg and `petId` parameterized constructors to isolate upgrade select and upgrade detail navigation paths.

### 3. RZXCore Compatibility Package (`me.rzx.core`)
*   Created standalone compatibility classes and interfaces under the `me.rzx.core` package in the `zcompanions-project` codebase to bridge API and event bus calls cleanly at compile/runtime without modifying existing plugin boot files.

---

## 🛠️ Issues Found & Fixed

### 1. Pet Entity Rendering & Despawn Recovery (`PetManager.java`)
*   **The Issues:**
    1.  **AI Freeze on Bat Entities:** Previously, `le.setAI(false)` was called on all `LivingEntity` instances. On Paper 1.20+, setting AI to `false` on a `Bat` overrides its `isAwake()` state back to `false` (sleeping/upside-down) every tick, causing bats to enter a frozen state or render invisibly in mid-air.
    2.  **Server Difficulty & Mob Filter Despawning:** Spawning mob entities via `spawnEntity(spawnLoc, type)` caused Paper/Spigot to despawn hostile mob types (like `BAT` or `BLAZE`) on Peaceful difficulty or in restricted regions.
    3.  **Invalid Entity Despawn Recovery:** If Bukkit invalidated a pet entity during chunk unloads or world changes, `pet.isValid()` returned `false` and the follow task permanently skipped the pet without re-spawning it.
*   **The Fixes:**
    1.  Changed entity spawning to `CreatureSpawnEvent.SpawnReason.CUSTOM`, bypassing difficulty despawning.
    2.  Kept AI active for `Bat` entities so wing-flapping animation runs continuously while `setAwake(true)` keeps them flying smoothly.
    3.  Added auto-respawn recovery in `startPetFollowTask()`: if `pet == null || !pet.isValid()`, it automatically re-summons the active companion.

### 2. GUI Menu Overrides & Upgrade Navigation (`UpgradeHolder.java` & `EventListener.java`)
*   **The Issues:**
    1.  `openUpgradeSelectPetMenu` and `openPetUpgradeDetailMenu` were using `StorageHolder`. When a player clicked items in upgrade menus, `onInventoryClick` fallthrough executed delayed menu refresh calls that reopened `main.yml` or `storage.yml`, overriding navigation choices.
    2.  `select_upgrade` and `open` action handlers lacked early returns, causing clicks in custom shop sections to get immediately overwritten by the scheduler.
*   **The Fixes:**
    1.  Created `UpgradeHolder.java` to isolate Upgrade menus from Storage and Shop menus.
    2.  Added early returns on `select_upgrade` and `open` actions.
    3.  Structured holder-based inventory refreshing: `StorageHolder` refreshes storage, `UpgradeHolder` refreshes upgrade menus, and `ShopHolder` refreshes shop sections.

---

## 🧪 Verification & Build Results

*   **Compilation Status:** **BUILD SUCCESS**!
*   **Updated Binary:** [RZXPets.jar](file:///C:/Users/Hi\.gemini\antigravity\scratch\pet-shop-configs\RZXPets.jar)
