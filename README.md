# TPA Forms Bridge v2

> A Spigot plugin that integrates TPA (Teleport Ask) functionality with [GeyserMenu](https://github.com/dronzer-tb/geyser-menu), providing a beautiful form-based TPA experience for Bedrock players.

![Version](https://img.shields.io/badge/version-2.0.0--SNAPSHOT-22c55e?style=flat-square)
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
| 📱 | **Bedrock-Only** | Only shows to Bedrock players via Floodgate detection |

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

| Plugin | Status |
|---|---|
| [EssentialsX](https://essentialsx.net/) | Required |
| [Floodgate](https://geysermc.org/download#floodgate) | Required |
| [GeyserMenuCompanion](https://github.com/dronzer-tb/geyser-menu-companion) | Required |
| [SkinsRestorer](https://skinsrestorer.net/) | Optional |

- Paper/Spigot **1.20.4+**
- Java **21+**

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
| `tpaforms.use` | Access TPA forms | `true` |
| `tpaforms.settings` | Access settings menu | `true` |

---

## 🔨 Building
```bash
./gradlew build
```

Output: `build/libs/TPAFormsBridge-2.0.0-SNAPSHOT.jar`

---

## 📦 Dependencies

| Dependency | Purpose |
|---|---|
| [GeyserMenu](https://github.com/dronzer-tb/geyser-menu) | Geyser extension for form menus |
| [GeyserMenuCompanion](https://github.com/dronzer-tb/geyser-menu-companion) | Spigot-side API |
| [EssentialsX](https://essentialsx.net/) | TPA functionality |
| [Floodgate](https://geysermc.org/download#floodgate) | Bedrock player detection |

---

## 📄 License

Distributed under the **GNU General Public License v3.0**. See [`LICENSE`](./LICENSE) for details.
