# TPA Forms Bridge v2

A Spigot plugin that integrates TPA (Teleport Ask) functionality with [GeyserMenu](https://github.com/yourusername/geyser-menu), providing a beautiful form-based TPA experience for Bedrock players.

## Features

- **TPA Button in GeyserMenu**: Adds a TPA button with Ender Eye icon to the main GeyserMenu
- **Player List Form**: Shows online players with their skins to send TPA requests
- **Auto-Accept TPA**: Optional setting to automatically accept incoming TPA requests
- **Forms Delivery**: Toggle between form-based or chat-based TPA request notifications
- **SkinsRestorer Support**: Shows player skins even on cracked servers
- **Bedrock-Only**: Only shows to Bedrock players via Floodgate detection

## Requirements

- Paper/Spigot 1.20.4+
- Java 21+
- EssentialsX (for TPA functionality)
- Floodgate (for Bedrock player detection)
- GeyserMenuCompanion (for menu integration)
- Optional: SkinsRestorer (for player skin display)

## Installation

1. Install all required plugins (EssentialsX, Floodgate, GeyserMenuCompanion)
2. Download `TPAFormsBridge-2.0.0-SNAPSHOT.jar`
3. Place in your `plugins/` folder
4. Restart your server

## Configuration

```yaml
# config.yml
# Default settings for new players
defaults:
  auto-accept-tpa: false    # Auto-accept TPA requests (not TPAHere)
  forms-delivery: true       # Show TPA requests as forms (vs chat)

# Messages
messages:
  tpa-sent: "&aTPA request sent to %player%"
  tpa-received: "&e%player% wants to teleport to you"
  auto-accepted: "&aTPA request auto-accepted"
```

## How It Works

1. Bedrock player double-clicks inventory → GeyserMenu opens
2. Player clicks "TPA" button → Player list form appears
3. Player selects a player → TPA request sent via EssentialsX
4. Target player receives form (if forms-delivery enabled) or chat message

### Player Settings

Each player can configure:
- **Auto-Accept TPA**: Automatically accept TPA requests (not TPAHere for safety)
- **Forms Delivery**: Receive TPA requests as forms instead of chat messages

## Permissions

| Permission | Description |
|------------|-------------|
| `tpaforms.use` | Access TPA forms (default: true) |
| `tpaforms.settings` | Access settings menu (default: true) |

## Building

```bash
./gradlew build
```

Output: `build/libs/TPAFormsBridge-2.0.0-SNAPSHOT.jar`

## Dependencies

- [GeyserMenu](https://github.com/yourusername/geyser-menu) - The Geyser extension
- [GeyserMenuCompanion](https://github.com/yourusername/geyser-menu-companion) - Spigot-side API
- [EssentialsX](https://essentialsx.net/) - TPA functionality
- [Floodgate](https://geysermc.org/download#floodgate) - Bedrock player detection

## License

MIT License
