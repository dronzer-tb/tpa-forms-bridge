# TPA Forms Bridge v2

> A self-contained TPA (Teleport Ask) plugin for **Folia** and Paper. Java players get
> chat commands with clickable accept/deny; Bedrock players get native forms through
> [GeyserMenu](https://github.com/dronzer-tb/geyser-menu). **No Essentials required.**

![Version](https://img.shields.io/badge/version-2.1.0--folia-22c55e?style=flat-square)
![Folia](https://img.shields.io/badge/Folia-26.2-8b5cf6?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-1.20.4+-22c55e?style=flat-square)
![Java](https://img.shields.io/badge/Java-21+-22c55e?style=flat-square)
![License](https://img.shields.io/badge/License-GPLv3-22c55e?style=flat-square)

---

## ✨ Features

| | Feature | Description |
|---|---|---|
| 👁️ | **TPA Button in GeyserMenu** | Adds a TPA button with Ender Eye icon to the main GeyserMenu |
| 👥 | **Player List Form** | Shows online players with their skins to send TPA requests |
| ⚡ | **Auto-Accept TPA** | Optional per-player setting to automatically accept incoming TPA requests |
| 💬 | **Forms Delivery Toggle** | Switch between form-based or chat-based TPA request notifications |
| 🦴 | **SkinsRestorer Support** | Shows player skins even on cracked servers |
| 📱 | **Bedrock Forms** | Incoming requests pop a native form for Bedrock players |
| 🧵 | **Folia-Native** | Region-threaded; uses `teleportAsync` and the Folia schedulers |
| 🧩 | **Self-Contained** | Owns the whole TPA flow — no Essentials dependency |
| ⏳ | **Warmup & Cooldown** | Optional, with movement and damage cancellation |

---

## 📸 Screenshots

<div align="center">

| TPA Menu | TPA Settings |
|:---:|:---:|
| <img src="https://github.com/dronzer-tb/tpa-forms-bridge/raw/master/assests/Screenshot%20From%202026-03-15%2021-32-32.png" width="320" /> | <img src="https://github.com/dronzer-tb/tpa-forms-bridge/raw/master/assests/Screenshot%20From%202026-03-15%2021-32-43.png" width="320" /> |

| Teleport Menu | Player Action |
|:---:|:---:|
| <img src="https://github.com/dronzer-tb/tpa-forms-bridge/raw/master/assests/Screenshot%20From%202026-03-15%2021-32-59.png" width="320" /> | <img src="https://github.com/dronzer-tb/tpa-forms-bridge/raw/master/assests/Screenshot%20From%202026-03-15%2021-33-07.png" width="320" /> |

| TPA Accept Menu |
|:---:|
| <img src="https://github.com/dronzer-tb/tpa-forms-bridge/raw/master/assests/Screenshot%20From%202026-03-15%2021-42-51.png" width="320" /> |

</div>

---

## 📋 Requirements

| Plugin | Status | Notes |
|---|---|---|
| [Floodgate](https://geysermc.org/download#floodgate) | Optional | Needed only for Bedrock detection |
| [GeyserMenuCompanion](https://github.com/dronzer-tb/geyser-menu-companion) | Optional | Needed only for Bedrock forms |
| [SkinsRestorer](https://skinsrestorer.net/) | Optional | Player skins on the list form |
| EssentialsX | **Not required** | Removed in 2.1.0 — see below |

- **Folia 26.2+**, or Paper/Spigot **1.20.4+**
- Java **21+**

> **Why Essentials was dropped.** Until 2.1.0 this plugin shelled out to Essentials and
> listened for `net.ess3.api.events.TPARequestEvent` to intercept *incoming* requests.
> EssentialsX does not run on Folia at all, and Folia-compatible alternatives such as
> EssentialsC do not fire that event — so incoming requests could never be handled and
> the Bedrock form flow was dead. 2.1.0 implements TPA itself.
>
> If another plugin also registers `/tpa`, disable its copy or the two will fight over
> the command name.

---

## 🚀 Installation

1. Install all required plugins — EssentialsX, Floodgate, GeyserMenuCompanion
2. Download `TPAFormsBridge-2.0.0-SNAPSHOT.jar`
3. Place it in your `plugins/` folder
4. Restart your server — config generates on first run

---

## ⚙️ Configuration
```yaml
# config.yml

# Default settings for new players
defaults:
  auto-accept-tpa: false    # Auto-accept TPA requests (not TPAHere)
  forms-delivery: true      # Show TPA requests as forms (vs chat)

# Messages
messages:
  tpa-sent:      "&aTPA request sent to %player%"
  tpa-received:  "&e%player% wants to teleport to you"
  auto-accepted: "&aTPA request auto-accepted"
```

---

## 🔄 How It Works
```
Bedrock player opens inventory
        ↓
  GeyserMenu appears
        ↓
  Player clicks TPA
        ↓
  Player list form opens
        ↓
  Player selects target → TPA sent via EssentialsX
        ↓
  Target receives form (forms-delivery) or chat message
```

### Player Settings

Each player can individually configure:

- **Auto-Accept TPA** — automatically accept TPA requests (TPAHere excluded for safety)
- **Forms Delivery** — receive TPA requests as Bedrock forms instead of chat messages

---

## 🔑 Permissions

| Permission | Description | Default |
|---|---|---|
| `tpaforms.use` | Access TPA forms / Bedrock menu button | `true` |
| `tpaforms.settings` | Access settings menu | `true` |
| `tpaforms.command.tpa` | `/tpa <player>` | `true` |
| `tpaforms.command.tpahere` | `/tpahere <player>` | `true` |
| `tpaforms.command.tpaccept` | `/tpaccept [player]` | `true` |
| `tpaforms.command.tpdeny` | `/tpdeny [player]` | `true` |
| `tpaforms.command.tpcancel` | `/tpcancel [player]` | `true` |
| `tpaforms.command.tpatoggle` | `/tpatoggle` | `true` |
| `tpaforms.command.tpaignore` | `/tpaignore <player>` | `true` |
| `tpaforms.command.tpaqueue` | `/tpaqueue` | `true` |
| `tpaforms.command.*` | All of the above | — |
| `tpaforms.bypass.warmup` | Skip teleport warmup | `op` |
| `tpaforms.bypass.cooldown` | Skip teleport cooldown | `op` |
| `tpaforms.admin` | Everything | `op` |

### Commands

| Command | Aliases | Description |
|---|---|---|
| `/tpa <player>` | — | Ask to teleport **to** a player |
| `/tpahere <player>` | — | Ask a player to teleport **to you** |
| `/tpaccept [player]` | `tpyes` | Accept the newest, or a named player's, request |
| `/tpdeny [player]` | `tpno` | Deny a request |
| `/tpcancel [player]` | — | Cancel a request you sent |
| `/tpatoggle` | — | Stop/allow receiving requests |
| `/tpaignore <player>` | — | Ignore a specific player (online only) |
| `/tpaqueue` | — | List your pending incoming requests |

---

## 🔨 Building
```bash
./gradlew build
```

Output: `build/libs/TPAFormsBridge-2.1.0-folia.jar`

Requires JDK 25 to build (Gradle 9.7.1); the produced bytecode targets Java 21.

---

## 📦 Dependencies

| Dependency | Purpose |
|---|---|
| [GeyserMenu](https://github.com/dronzer-tb/geyser-menu) | Geyser extension for form menus |
| [GeyserMenuCompanion](https://github.com/dronzer-tb/geyser-menu-companion) | Spigot-side API |
| [Floodgate](https://geysermc.org/download#floodgate) | Bedrock player detection |
| [folia-api](https://papermc.io/software/folia) | `26.2.build.5-beta` — compiled against, emits Java 21 bytecode |
| [SkinsRestorer API](https://skinsrestorer.net/) | `15.12.5` |

---

## 📄 License

Distributed under the **GNU General Public License v3.0**. See [`LICENSE`](./LICENSE) for details.

---

## 🤖 AI Disclaimer

Parts of this project were written with the assistance of AI (Anthropic's Claude) —
specifically the Folia port and the self-contained TPA engine that replaced the Essentials bridge, along with sections of this README.

Every AI-authored change was compiled and statically verified against the target
server API before release: the built jars were scanned to confirm there are zero `BukkitScheduler`/`BukkitRunnable` references and zero synchronous `Player#teleport` calls.
That is **not** the same as being play-tested. Treat this as reviewed-but-unproven
code, and please open an issue if you hit a bug.
