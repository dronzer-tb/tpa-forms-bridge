# GeyserMenu Button Integration - Technical Documentation

## Overview

This document explains how menu buttons from companion plugins (like TPAFormsBridge) are integrated into the GeyserMenu extension's main menu that appears when Bedrock players double-click their inventory.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           GEYSER (Extension Side)                           │
│  ┌─────────────────┐    ┌──────────────────┐    ┌───────────────────────┐  │
│  │ GeyserMenuExt   │───▶│ MenuServer       │───▶│ ClientHandler         │  │
│  │                 │    │ (TCP Server)     │    │ (handles packets)     │  │
│  └─────────────────┘    └──────────────────┘    └───────────────────────┘  │
│           │                     │                         │                 │
│           ▼                     ▼                         ▼                 │
│  ┌─────────────────┐    ┌──────────────────┐    ┌───────────────────────┐  │
│  │ MainMenu        │◀───│ ButtonManager    │◀───│ REGISTER_BUTTONS      │  │
│  │ (shows buttons) │    │ (stores buttons) │    │ (packet handler)      │  │
│  └─────────────────┘    └──────────────────┘    └───────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │ TCP Connection (Netty)
                                    │ JSON Packets
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SPIGOT (Companion Side)                           │
│  ┌─────────────────┐    ┌──────────────────┐    ┌───────────────────────┐  │
│  │ TPAFormsBridge  │───▶│ GeyserMenuAPI    │───▶│ SpigotGeyserMenuAPI   │  │
│  │ (registers btn) │    │ (public API)     │    │ (implementation)      │  │
│  └─────────────────┘    └──────────────────┘    └───────────────────────┘  │
│                                                           │                 │
│                                                           ▼                 │
│                                                 ┌───────────────────────┐  │
│                                                 │ MenuClient            │  │
│                                                 │ (TCP Client)          │  │
│                                                 │ - sendButtons()       │  │
│                                                 │ - onButtonClick()     │  │
│                                                 └───────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Protocol: Packet Types

The communication uses JSON packets over TCP. Key packet types for button integration:

| Packet Type | Direction | Purpose |
|-------------|-----------|---------|
| `REGISTER_BUTTONS` | Companion → Extension | Send list of buttons to display in main menu |
| `BUTTON_CLICKED` | Extension → Companion | Notify when a player clicks a registered button |
| `REQUEST_BUTTONS` | Extension → Companion | Request companion to resend its buttons |

## Data Classes

### ButtonData (shared between both sides)
```java
public class ButtonData {
    private String id;          // Unique button identifier (e.g., "tpa-forms-main")
    private String text;        // Display text (e.g., "TPA")
    private String imageUrl;    // URL to button icon (external URL)
    private String imagePath;   // Path to button icon (resource pack path)
    private int priority;       // Sort order (lower = appears first)
}
```

### ButtonData.ButtonList (wrapper for transmission)
```java
public static class ButtonList {
    private List<ButtonData> buttons;
}
```

### ButtonData.ButtonClick (click event)
```java
public static class ButtonClick {
    private String buttonId;
    private String playerUuid;
    private String playerName;
    private String xuid;
}
```

## Implementation Details

### 1. Button Registration (Companion Plugin Side)

When a plugin like TPAFormsBridge registers a button:

```java
// In TPAFormsBridge's MenuRegistrar.java
MenuButton tpaButton = new MenuButton("tpa-forms-main")
    .text("TPA")
    .imageUrl("https://mc-heads.net/head/endereye")
    .priority(10)
    .onClick((player, session) -> {
        // Handle button click - show TPA menu
        TpaMainMenu.show(player.getUuid());
    });

GeyserMenuAPI.get().registerButton(tpaButton);
```

### 2. Storing and Syncing (SpigotGeyserMenuAPI)

```java
// SpigotGeyserMenuAPI.java
@Override
public void registerButton(MenuButton button) {
    registeredButtons.put(button.getId(), button);
    plugin.getLogger().info("Registered menu button: " + button.getId());
    
    // Sync to extension immediately
    syncButtonsToExtension();
}

private void syncButtonsToExtension() {
    if (plugin.getMenuClient() == null || !plugin.getMenuClient().isAuthenticated()) {
        // Not connected yet - buttons will be synced after connection
        return;
    }
    
    // Convert MenuButton to ButtonData for transmission
    List<ButtonData> buttonDataList = new ArrayList<>();
    for (MenuButton button : registeredButtons.values()) {
        ButtonData data = new ButtonData(
            button.getId(),
            button.getText(),
            button.getImageUrl(),
            button.getImagePath(),
            button.getPriority()
        );
        buttonDataList.add(data);
    }
    
    // Send over TCP
    plugin.getMenuClient().sendButtons(buttonDataList);
}
```

### 3. Sending Buttons Over TCP (MenuClient)

```java
// MenuClient.java
public void sendButtons(List<ButtonData> buttons) {
    if (!authenticated) {
        return;
    }
    
    ButtonData.ButtonList buttonList = new ButtonData.ButtonList(buttons);
    Packet packet = new Packet(Packet.PacketType.REGISTER_BUTTONS, GSON.toJson(buttonList));
    sendPacket(packet);
}
```

### 4. Receiving Buttons (Extension - ClientHandler)

```java
// ClientHandler.java
private void handleRegisterButtons(Packet packet) {
    if (!authenticated) {
        sendError("Not authenticated");
        return;
    }

    ButtonData.ButtonList buttonList = GSON.fromJson(packet.getPayload(), ButtonData.ButtonList.class);
    
    // Store buttons in ButtonManager
    server.getButtonManager().registerButtons(clientIdentifier, buttonList.getButtons());
    extension.logger().info("Registered " + buttonList.getButtons().size() + " buttons from client: " + clientIdentifier);
}
```

### 5. Storing Buttons (ButtonManager)

```java
// ButtonManager.java
public class ButtonManager {
    // Map of client ID -> list of buttons from that client
    private final Map<String, List<ButtonData>> clientButtons = new ConcurrentHashMap<>();
    
    public void registerButtons(String clientIdentifier, List<ButtonData> buttons) {
        clientButtons.put(clientIdentifier, new ArrayList<>(buttons));
    }
    
    public List<ButtonData> getAllButtons() {
        List<ButtonData> allButtons = new ArrayList<>();
        for (List<ButtonData> buttons : clientButtons.values()) {
            allButtons.addAll(buttons);
        }
        // Sort by priority
        allButtons.sort(Comparator.comparingInt(ButtonData::getPriority));
        return allButtons;
    }
    
    public String getClientForButton(String buttonId) {
        // Find which client registered this button
        for (Map.Entry<String, List<ButtonData>> entry : clientButtons.entrySet()) {
            for (ButtonData button : entry.getValue()) {
                if (button.getId().equals(buttonId)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
```

### 6. Displaying Buttons in Main Menu (MainMenu)

```java
// MainMenu.java
@Override
protected void buildMenu() {
    // Get all registered buttons from companion plugins
    ButtonManager buttonManager = extension.getMenuServer().getButtonManager();
    List<ButtonData> registeredButtons = buttonManager.getAllButtons();
    
    // Add each button to the form
    for (ButtonData buttonData : registeredButtons) {
        String image = (buttonData.getImageUrl() != null) ? buttonData.getImageUrl() : buttonData.getImagePath();
        final String buttonId = buttonData.getId();
        
        addButton(buttonData.getText(), image, () -> {
            handleButtonClick(buttonId);
        });
    }
    
    // Add default Settings button at the end
    addButton("Settings", () -> {
        new SettingsMenu(extension).send(connection);
    });
}

private void handleButtonClick(String buttonId) {
    // Find which client registered this button
    ButtonManager buttonManager = extension.getMenuServer().getButtonManager();
    String clientId = buttonManager.getClientForButton(buttonId);
    
    // Get the client handler
    ClientHandler client = extension.getMenuServer().getClient(clientId);
    
    // Send BUTTON_CLICKED packet back to companion
    client.sendButtonClick(buttonId, connection.javaUuid(), connection.bedrockUsername(), connection.xuid());
}
```

### 7. Handling Button Clicks (Companion Side)

```java
// GeyserMenuSpigot.java - during connection setup
menuClient.onButtonClick(click -> {
    UUID playerUuid = UUID.fromString(click.getPlayerUuid());
    BedrockPlayer player = new BedrockPlayer(playerUuid, click.getXuid(), click.getPlayerName());
    api.handleButtonClick(click.getButtonId(), player, null);
});

// SpigotGeyserMenuAPI.java
public void handleButtonClick(String buttonId, BedrockPlayer player, Object session) {
    MenuButton button = registeredButtons.get(buttonId);
    if (button == null) return;

    // Execute command if set
    if (button.getCommand() != null) {
        Player bukkitPlayer = Bukkit.getPlayer(player.getUuid());
        if (bukkitPlayer != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                bukkitPlayer.performCommand(button.getCommand());
            });
        }
    }

    // Execute onClick handler if set
    if (button.getOnClick() != null) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            button.getOnClick().accept(player, session);
        });
    }
}
```

## Sync Timing

Buttons are synced at these times:

1. **On button registration**: When `registerButton()` is called
2. **On button unregistration**: When `unregisterButton()` is called
3. **On successful connection**: Via `resyncButtons()` after authentication succeeds

This ensures buttons registered before the connection is established are still synced.

## File Locations

### GeyserMenu Extension (geyser-menu)
- `src/main/java/com/geysermenu/extension/network/protocol/Packet.java` - Packet types
- `src/main/java/com/geysermenu/extension/network/protocol/ButtonData.java` - Button data class
- `src/main/java/com/geysermenu/extension/network/ButtonManager.java` - Button storage
- `src/main/java/com/geysermenu/extension/network/ClientHandler.java` - Packet handling
- `src/main/java/com/geysermenu/extension/forms/MainMenu.java` - Menu display

### GeyserMenu Companion (geyser-menu-companion)
- `common/src/main/java/com/geysermenu/companion/protocol/Packet.java` - Packet types
- `common/src/main/java/com/geysermenu/companion/protocol/ButtonData.java` - Button data class
- `common/src/main/java/com/geysermenu/companion/network/MenuClient.java` - TCP client
- `spigot/src/main/java/com/geysermenu/companion/spigot/api/SpigotGeyserMenuAPI.java` - API implementation
- `spigot/src/main/java/com/geysermenu/companion/spigot/GeyserMenuSpigot.java` - Main plugin

### TPAFormsBridge (tpa-forms-bridge-v2)
- `src/main/java/com/geysermc/tpaforms/hook/MenuRegistrar.java` - Button registration

## Summary

The integration works by:
1. Companion plugins register buttons via `GeyserMenuAPI.registerButton()`
2. The API converts buttons to `ButtonData` and sends them over TCP to the extension
3. The extension stores buttons in `ButtonManager` keyed by client ID
4. When the main menu is opened, it fetches all buttons and displays them
5. When a button is clicked, a `BUTTON_CLICKED` packet is sent back to the companion
6. The companion looks up the button's onClick handler and executes it

This design allows any companion plugin to add buttons to the GeyserMenu without modifying the extension itself.
