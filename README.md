# Spellbreak Plugin

A fully custom Minecraft ability system built for **Paper 1.21+**, featuring 6 unique spell classes, 47 abilities, player levels, daily missions, team/party system, and a full scoreboard HUD.

## Features

- **7 Spell Classes** – each with 6 unique abilities
- **47 Custom Abilities** – with cooldowns, mana costs, particle effects, and configurable stats
- **Mana System** – per-player mana pool with regeneration
- **Player & Spell Level System** – gain XP and level up to unlock stat bonuses
- **Daily Mission System** *(WIP)* – rotating daily objectives
- **Team / Party System** – group players to prevent friendly fire
- **Scoreboard HUD** – live display of health, mana, level, class, and all bound abilities with cooldowns
- **Custom Death Messages** – unique kill messages per ability

---

## Requirements

This plugin requires the following plugins to be installed on your server:

| Dependency | Version | Download |
|---|---|---|
| [Paper](https://papermc.io/downloads/paper) | 1.21+ | https://papermc.io/downloads |
| [MythicMobs](https://mythicmobs.net/) | 5.8+ | https://mythicmobs.net |
| [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) | 5.3+ | https://www.spigotmc.org/resources/1997/ |
| [LibsDisguises](https://www.spigotmc.org/resources/libs-disguises.32453/) | 11.0+ | https://www.spigotmc.org/resources/32453/ |
| [PacketEvents](https://github.com/retrooper/packetevents) | 2.8+ | https://github.com/retrooper/packetevents/releases |

---

## Installation

1. Install all dependencies listed above.
2. Drop `spellbreak-1.0.jar` into your server's `plugins/` folder.
3. Start (or restart) your server.
4. The plugin will generate a `plugins/Spellbreak/config.yml` on first run.

---

## Building from Source

**Requirements:** Java 21, Maven

```bash
git clone https://github.com/Ratatamakata/Spellbreak-Plugin.git
cd Spellbreak-Plugin
mvn clean package
```

The compiled jar will be at `target/spellbreak-1.0.jar`. Drop it into your server's `plugins/` folder.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/class <ClassName>` | Set your spell class | *(none – all players)* |
| `/bind <slot 1-9> <ability>` | Bind an ability to a hotbar slot | *(none – all players)* |
| `/preset create <name>` | Save your current ability binds as a preset | *(none)* |
| `/preset bind <name>` | Load a saved preset | *(none)* |
| `/party <create\|invite\|join\|leave\|info>` | Manage your team/party | *(none)* |
| `/level` | View your player level and stats | `spellbreak.level` |
| `/level stats [player]` | View detailed level stats | `spellbreak.level` |
| `/level spells` | View your spell levels | `spellbreak.level` |
| `/dailies` | Open the daily missions GUI | `spellbreak.dailies` |
| `/spellbreakcooldownreset [player]` | Reset a player's ability cooldowns | `spellbreak.command.cooldownreset` (OP) |
| `/reload` | Reload the plugin config | `spellbreak.reload` (OP) |

**Aliases:** `/party` = `/team` = `/p`

---

## Classes & Abilities

### 🦴 Necromancer
| Ability | Description |
|---|---|
| LifeDrain | Drain life from nearby enemies |
| PlagueCloud | Release a toxic cloud that poisons enemies |
| MistDash | Dash forward as shadow mist |
| Tentacles | Summon tentacles to root enemies |
| BoneChoir | Raise undead allies from fallen mobs |
| UndyingPact | Cheat death once with a brief invincibility window |

### 🌿 Archdruid
| Ability | Description |
|---|---|
| NatureStep | Leave a trail of nature effects as you walk |
| AmbushSlash | Ambush strike that deals bonus damage from stealth |
| SporeBlossom | Spawn a blossom that releases spore clouds |
| CanopyCrash | Crash down from above dealing AoE impact damage |
| QuillflareSurge | Launch a burst of quill projectiles |
| IronwoodShell | Encase yourself in bark armor |

### ☀️ Lightbringer
| Ability | Description |
|---|---|
| LightCage | Trap enemies in a cage of light |
| Consecration | Consecrate the ground beneath you, damaging enemies |
| PurifyingPrism | Launch a prism that purifies and deals damage |
| RadiantPhase | Phase through blocks briefly |
| RadiantDash | Dash in a burst of radiant light |
| BeaconOfClarity | Emit a beacon that heals and buffs allies |

### 🧠 Mindshaper
| Ability | Description |
|---|---|
| EchoPulse | Send out a mental shockwave |
| PhantomEcho | Create a phantom copy to confuse enemies |
| NeuralTrap | Set an invisible mental trap |
| Dreamwalker | Enter a dreamlike state with enhanced movement |
| ShadowCreatures | Summon shadow beasts to fight for you |
| CloneSwarm | Unleash a swarm of clones |

### ⚡ Elementalist
| Ability | Description |
|---|---|
| Tidepool | Create a pool that slows and damages enemies |
| Emberstorm | Ignite the area in a storm of embers |
| GaleVortex | Summon a vortex of wind to push enemies |
| EarthShards | Erupt shards of earth from the ground |
| Avalanche | Trigger a devastating avalanche |
| ThunderSlam | Slam down with thunder force |

### ⚙️ Runesmith
| Ability | Description |
|---|---|
| WardingSigil | Place a warding sigil that protects an area |
| Runecarver | Carve a rune that creates lasting effects |
| RunicJumpPad | Place a jump pad rune |
| RunicTurret | Deploy an autonomous runic turret |
| BladeSpin | Spin your blade in a deadly arc |
| SwarmSigil | Release a swarm from a placed sigil |

### ⭐ Starcaller
| Ability | Description |
|---|---|
| MeteorLash | Lash forward with a meteor strike |
| BlackHole | Create a miniature black hole that pulls enemies |
| StarPhase | Phase between dimensions using starlight |
| PhotonBeam | Charge and fire a beam of photon energy |
| QuantumAnchor | Anchor yourself in a quantum state to teleport back |
| SolarLance | Hurl a lance of solar energy |

---

## Configuration

The plugin generates `plugins/Spellbreak/config.yml` on first start. You can edit ability values (damage, cooldowns, mana costs) directly in this file. Use `/reload` (OP) or restart the server after making changes.

Player data (class selection, bound abilities, levels) is stored per-player under `plugins/Spellbreak/playerdata/<UUID>.yml`.

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `spellbreak.command.cooldownreset` | Use `/spellbreakcooldownreset` | OP |
| `spellbreak.reload` | Use `/reload` to reload config | OP |
| `spellbreak.level` | View level stats and commands | All players |
| `spellbreak.dailies` | Access daily missions | All players |

---

## Notes

- **Starcaller** is the newest class (added after initial release).
- Daily Missions are work-in-progress and may have limited functionality.
