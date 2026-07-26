# 🐾 RZXPets

<p align="center">
  <a href="https://github.com/KurzIsRio/RZXPets">
    <img src="https://img.shields.io/github/v/release/KurzIsRio/RZXPets?style=for-the-badge&color=orange&logo=github" alt="Release Version">
  </a>
  <a href="https://github.com/KurzIsRio/RZXPets/releases">
    <img src="https://img.shields.io/github/downloads/KurzIsRio/RZXPets/total?style=for-the-badge&color=purple&logo=github" alt="Downloads">
  </a>
  <a href="https://github.com/KurzIsRio/RZXPets/stargazers">
    <img src="https://img.shields.io/github/stars/KurzIsRio/RZXPets?style=for-the-badge&color=yellow&logo=github" alt="Stars">
  </a>
  <a href="https://github.com/KurzIsRio/RZXPets/network/members">
    <img src="https://img.shields.io/github/forks/KurzIsRio/RZXPets?style=for-the-badge&color=blue&logo=github" alt="Forks">
  </a>
  <a href="https://github.com/KurzIsRio/RZXPets/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/KurzIsRio/RZXPets?style=for-the-badge&color=green" alt="License">
  </a>
</p>

---

**RZXPets** is a premium companion pet plugin designed for Paper and Spigot Minecraft servers. It adds interactive, floating companion pets that hover beside players. These pets gain experience, level up, and grant active combat support alongside highly customizable level-scaling passive mechanics.

---

## ✨ Features

*   **Hovering Companion Entities:** Companions spawn as physical entities (e.g. Parrots, Blazes, Bats) that float behind and hover over the player's shoulder.
*   **XP & Level Progression:** Companions gain experience and level up (up to Lvl 100), boosting passive traits and activation triggers.
*   **Active Dash Combat:** Companions slide-dash towards enemy entities during combat, striking them for dynamic damage and returning to the player.
*   **10 Built-In Progressive Mechanics:**
    *   **Potion Effects:** Passive buffs (Speed, Strength, Haste, etc.).
    *   **Creative Flight:** Fly in survival mode (unlocked at Lvl 100).
    *   **Mining & Combat zGems Boosters:** Find zGems currencies while mining or slaying monsters.
    *   **XP Multiplier:** Boost vanilla XP gains.
    *   **Mitigation Shield:** Dynamic chance to absorb incoming damage.
    *   **Vampiric Lifesteal:** Returns a percentage of melee damage as health.
    *   **Double Harvest:** Chance to double crop yields or mined resources.
    *   **Area Rejuvenation:** Heals the owner and nearby allies in a configurable radius.
*   **Shop, Storage, & Upgrade GUIs:** Interactive inventories allowing players to purchase companions, summon/dismiss them, and upgrade their properties.

---

## 🛠️ Requirements & Integrations

*   **Spigot / Paper 1.20.4+**
*   **PlaceholderAPI** (Required for balance rendering and hooks)
*   **ExcellentEconomy** (Supported zGems currency ecosystem)
*   **LuckPerms** (Automatic pet storage limit adjustments)
*   **WorldGuard** (Region-based pet summoning protection)

---

## 💻 Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/pets` | Opens the main companion GUI selection menu. | `rzxpets.use` |
| `/pets summon <id>` | Summons a companion by its unique identifier. | `rzxpets.summon` |
| `/pets dismiss` | Despawns currently active companion. | `rzxpets.dismiss` |
| `/pets admin give <player> <id>` | Grants a companion to a player. | `rzxpets.admin` |
| `/pets admin addxp <player> <amount>`| Adds XP to a player's active pet. | `rzxpets.admin` |
