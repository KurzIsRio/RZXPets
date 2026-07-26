# RZXPets Developer Documentation

Welcome to the companion pet customization guide for RZXPets. This document details how to configure GUI shop sections, custom level-scaling mechanics, and default currency rules.

---

## 1. Shop GUI Sections & Currency Configurations

You can define GUI shop sections under the `shop/sections/` folder (e.g. `main.yml`, `shop.yml`).

### Section-Level Default Currency
To make currency configurations editable through sections easily, you can define a default section currency at the configuration root level. If an item in `items` doesn't specify a `currency`, it inherits this default.

```yaml
# shop/sections/upgrade.yml
title: "&e&lUpgrade Menu"
size: 45
currency: "zgems"  # Default section currency: zgems, vault, or item

items:
  upgrade_tier_1:
    slot: 12
    material: GOLDEN_APPLE
    name: "&aUpgrade Health"
    price: 150
    # inherits "zgems" default currency since it's not specified
  upgrade_tier_2:
    slot: 14
    material: NETHERITE_INGOT
    name: "&bSpecial Token Upgrade"
    price: 50
    currency: "item"  # overrides section default to require physical item
    item-id: "TOKEN"
```

---

## 2. Reusable Custom Mechanics Folder (`mechanics/`)

You can create standalone, reusable mechanics files in the `plugins/RZXPets/mechanics/` directory.

### Example: `health.yml`
```yaml
enabled: true
name: "&c&lVitality Link"
description:
  - "&7Grants bonus max health while equipped."
  - "&7Current Bonus: &a+%amplifier% Hearts"
type: "potion"
effect: "HEALTH_BOOST"
amplifier: 0
min-level: 1
progressive-levels:
  20:
    amplifier: 1  # Grants +4 health (2 hearts)
  40:
    amplifier: 2  # Grants +6 health (3 hearts)
  60:
    amplifier: 3  # Grants +8 health (4 hearts)
  80:
    amplifier: 4  # Grants +10 health (5 hearts)
  100:
    amplifier: 5  # Grants +12 health (6 hearts)
```

### Reference Custom Mechanics in Pets Config
Pets can load these custom mechanics by specifying their `type: custom` and matching the file name in `mechanic-id`:
```yaml
# pets/wealthy_blaze.yml
name: "&6&lWealthy Blaze"
entity-type: "BLAZE"
mechanics:
  - type: custom
    mechanic-id: health
    min-level: 10  # This skill only activates when pet reaches Level 10
```

---

## 3. 10 Built-In Level-Scaling Mechanics

RZXPets provides 10 built-in mechanics that scale values, multipliers, and activation chances dynamically based on the pet's current level.

### 3.1 Potion Effect (`potion`)
Grants permanent potion effects while companion is summoned.
- **Fields**:
  - `effect`: PotionEffectType (e.g. `SPEED`, `FAST_DIGGING`, `INCREASE_DAMAGE`)
  - `amplifier`: Base amplifier (0 for Tier I)
  - `progressive-levels`: Level to amplifier map. If omitted, amplifier scales as `level / 25`.

### 3.2 Creative Flight (`flight`)
Enables creative flight for the player.
- **Unlock level**: Recommended `min-level: 100`

### 3.3 Mining zGems Booster (`mining-gems`)
Spawns zGems when mining block types.
- **Formula**:
  - Chance: `base-chance + (level * level-modifier)` (Max `35%`)
  - Yield: `base-amount + (level / 30)`
- **Fields**:
  - `base-chance` (default `0.05`)
  - `level-modifier` (default `0.003`)
  - `base-amount` (default `1`)
  - `blocks`: Material names filter (e.g., `STONE`, `COAL_ORE`, `DIAMOND_ORE`)

### 3.4 Combat zGems Booster (`combat-gems`)
Gives zGems when defeating mobs.
- **Formula**:
  - Chance: `base-chance + (level * level-modifier)` (Max `35%`)
  - Yield: `base-amount + (level / 30)`
- **Fields**:
  - `base-chance` (default `0.05`)
  - `level-modifier` (default `0.003`)
  - `base-amount` (default `1`)
  - `mobs`: Entity type filter list.

### 3.5 Experience Booster (`xp-booster`)
Multiplies gained vanilla XP.
- **Formula**:
  - XP Multiplier: `base-multiplier + (level * level-modifier)` (At level 100 with default, double XP `2.0x`)
- **Fields**:
  - `base-multiplier` (default `1.0`)
  - `level-modifier` (default `0.01`)

### 3.6 Damage Mitigation Shield (`shield`)
Chance to block incoming damage.
- **Formula**:
  - Chance: `base-chance + (level * level-modifier)` (Max `25%`)
  - Reduction: `base-mitigation + (level * mitigation-modifier)` (Max `50%` reduction)
- **Fields**:
  - `base-chance` (default `0.05`)
  - `level-modifier` (default `0.002`)
  - `base-mitigation` (default `0.10`)
  - `mitigation-modifier` (default `0.004`)

### 3.7 Vampiric Lifesteal (`lifesteal`)
Heals player on dealing melee damage.
- **Formula**:
  - Chance: `base-chance + (level * level-modifier)` (Max `25%`)
  - Percent: `base-percent + (level * percent-modifier)` (Max `20%` of damage returned as health)

### 3.8 Double Harvest Drops (`double-drops`)
Chance to double drops when mining blocks or harvesting crops.
- **Formula**:
  - Double Drop Chance: `base-chance + (level * level-modifier)` (Max `40%`)
- **Fields**:
  - `base-chance` (default `0.05`)
  - `level-modifier` (default `0.0035`)
  - `blocks`: Material filter list.

### 3.9 Companion Combat Attack (`combat-attack`)
Launches companion entity to dash out, strike targets, and glide back.
- **Formula**:
  - Damage: `base-damage + (level * damage-modifier)` (Max `10.0` damage)
  - Cooldown: `base-cooldown - (level * cooldown-modifier)` seconds (Min `5.0` seconds)
- **Fields**:
  - `base-damage` (default `2.0`)
  - `damage-modifier` (default `0.08`)
  - `base-cooldown` (default `15.0`)
  - `cooldown-modifier` (default `0.1`)
  - `particle` (default `CRIT`)
  - `sound` (default `ENTITY_GENERIC_ATTACK`)

### 3.10 Area Regeneration Healing (`area-heal`)
Periodically heals player and nearby allies in a circular radius.
- **Formula**:
  - Heal Amount: `base-heal + (level * heal-modifier)`
  - Radius: `radius + (level * 0.03)` blocks
  - Cooldown: `base-cooldown - (level * cooldown-modifier)` seconds
- **Fields**:
  - `base-heal` (default `1.0`)
  - `heal-modifier` (default `0.04`)
  - `radius` (default `3.0`)
  - `base-cooldown` (default `30.0`)
  - `cooldown-modifier` (default `0.15`)
