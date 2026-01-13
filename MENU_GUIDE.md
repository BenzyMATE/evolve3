# ConfigurableMenu System Guide

## Overview
The `ConfigurableMenu` system allows you to create menus with minimal code by defining the menu structure in `menus.yml`. You only need to write Java code for custom actions.

## Quick Start

### 1. Create Your Menu Class

```java
public class MyMenu extends ConfigurableMenu {
    private static final MenuType MENU_TYPE = MenuType.register("my_menu");

    public MyMenu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String getMenuId() {
        return "my_menu_config";  // Must match key in menus.yml
    }

    @Override
    protected void handleCustomAction(Player player, String action, int slot, InventoryClickEvent event) {
        switch (action) {
            case "my_action":
                player.sendMessage("Action executed!");
                break;
        }
    }

    @Override
    public MenuType getMenuType() {
        return MENU_TYPE;
    }
}
```

### 2. Configure in menus.yml

```yaml
my_menu_config:
  title: "&b&lMy Menu"
  size: 27

  items:
    13:
      material: DIAMOND
      name: "&aClick Me"
      lore:
        - "&7This is a button"
      action: "custom:my_action"

  fill:
    enabled: true
    material: GRAY_STAINED_GLASS_PANE
    name: " "
    slots: "border"
```

### 3. Register in MenuRegistry

```java
private final List<Class<? extends Menu>> menus = List.of(
    MyMenu.class,
    // ... other menus
);
```

### 4. Open the Menu

```java
MenuRegistry.getInstance().get("my_menu").openMenu(player);
```

## Configuration Reference

### Menu Properties

| Property | Required | Description | Example |
|----------|----------|-------------|---------|
| `title` | Yes | Menu title with color codes | `"&b&lMy Menu"` |
| `size` | Yes | Number of slots (9, 18, 27, 36, 45, 54) | `27` |
| `items` | No | Map of slot number to item config | See below |
| `fill` | No | Background/border fill settings | See below |

### Item Properties

| Property | Required | Description | Example |
|----------|----------|-------------|---------|
| `material` | Yes | Bukkit material name | `DIAMOND` |
| `name` | No | Display name with color codes | `"&aClick Me"` |
| `lore` | No | List of lore lines | `["&7Line 1", "&7Line 2"]` |
| `amount` | No | Item stack size (default: 1) | `5` |
| `glow` | No | Add enchant glow (default: false) | `true` |
| `action` | No | Action to execute on click | `"custom:my_action"` |

### Fill Properties

| Property | Required | Description | Example |
|----------|----------|-------------|---------|
| `enabled` | Yes | Enable fill items | `true` |
| `material` | Yes | Material for fill items | `GRAY_STAINED_GLASS_PANE` |
| `name` | No | Name for fill items | `" "` |
| `slots` | Yes | Which slots to fill | See slot patterns below |

### Slot Patterns

- `"border"` - All edge slots
- `"all"` - All slots in the menu
- `"0-8"` - Range from slot 0 to 8
- `"0,4,8"` - Specific slots (comma-separated)
- `"0-8,18-26"` - Multiple ranges/slots

### Built-in Actions

| Action | Description | Example |
|--------|-------------|---------|
| `close` | Closes the menu | `action: "close"` |
| `sound:NAME` | Plays a sound | `action: "sound:ENTITY_PLAYER_LEVELUP"` |
| `message:TEXT` | Sends a message | `action: "message:&aHello!"` |
| `command:CMD` | Makes player run command | `action: "command:help"` |
| `custom:NAME` | Calls handleCustomAction() | `action: "custom:my_action"` |

## Advanced Features

### Dynamic Items

Override `populateCustomItems()` to add dynamic items:

```java
@Override
protected void populateCustomItems(Player player, Inventory inventory) {
    ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
        .name("&e" + player.getName())
        .lore("&7Level: &f" + player.getLevel())
        .build();
    inventory.setItem(4, head);
}
```

### Refresh Menu

Call `refresh(player)` to update a menu while it's open:

```java
@Override
protected void handleCustomAction(Player player, String action, int slot, InventoryClickEvent event) {
    if (action.equals("upgrade")) {
        // Do upgrade logic
        refresh(player);  // Update the menu
    }
}
```

### Access to Config

The `menusConfig` field is available in your menu class:

```java
String customValue = menusConfig.getString(getMenuId() + ".custom_setting");
```

## Examples

See these example menus:
- `ExampleConfigMenu.java` - Simple config-based menu
- `ProfileMenu.java` - Combines config and dynamic items
- `TestMenu.java` - Traditional BaseMenu approach

## Best Practices

1. **Keep it Simple** - Use config for static layout, code for logic
2. **Meaningful Actions** - Name custom actions descriptively
3. **Error Handling** - Always handle unknown actions gracefully
4. **Player Feedback** - Use `sendSuccess()`, `sendError()`, `sendInfo()`
5. **Refresh Wisely** - Only refresh when menu content changes

## Troubleshooting

**Menu won't open:**
- Check menu ID matches between Java and YAML
- Verify menu is registered in MenuRegistry
- Check for errors in server console

**Items not showing:**
- Verify material names are correct (uppercase)
- Check slot numbers are within menu size
- Ensure YAML indentation is correct

**Actions not working:**
- Check action format (e.g., `custom:action_name`)
- Verify handleCustomAction() handles the action
- Check for typos in action names
