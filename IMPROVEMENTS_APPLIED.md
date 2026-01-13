# Improvements Applied to Evolv3

This document summarizes all the improvements and new utilities applied to the codebase.

## Summary

All new builders, managers, and utilities have been applied throughout the existing codebase to improve code quality, readability, and maintainability.

---

## 1. ItemBuilder Applied

### TestMenu.java
**Before:**
```java
ItemStack infoItem = Utils.createItem(
    Material.BOOK,
    "§e§lInformation",
    "§7Click for information",
    "§7about this menu"
);
```

**After:**
```java
ItemStack infoItem = new ItemBuilder(Material.BOOK)
    .name("&e&lInformation")
    .lore(
        "&7Click for information",
        "&7about this menu"
    )
    .build();
```

**Benefits:**
- Cleaner, more readable syntax
- Support for enchantments, flags, NBT data, and more
- Consistent with builder pattern throughout codebase

### Utils.java
- `createItem()` method now uses ItemBuilder internally
- Marked as `@Deprecated` with recommendation to use ItemBuilder directly
- Maintains backward compatibility for existing code

---

## 2. TaskManager Applied

### BaseDataCenter.java
**Before:**
```java
.thenAcceptAsync(profile -> {
    // ...
}, runnable -> Bukkit.getScheduler().runTask(Main.getInstance(), runnable))
```

**After:**
```java
.thenAcceptAsync(profile -> {
    // ...
}, TaskManager.getInstance()::runSync)
```

**Benefits:**
- Cleaner syntax with method reference
- Automatic task tracking
- All tasks auto-cancelled on shutdown
- Consistent task management across plugin

### Main.java
- Added `TaskManager.getInstance().cancelAllTasks()` to `onDisable()`
- Ensures all scheduled tasks are properly cancelled on shutdown
- Prevents memory leaks and dangling tasks

---

## 3. MessageBuilder Applied

### TestMenu.java
**Before:**
```java
sendInfo(player, "This is an example menu!");
sendInfo(player, "You can create menus like this easily.");
```

**After:**
```java
new MessageBuilder()
    .line("&eThis is an example menu!")
    .line("&eYou can create menus like this easily.")
    .sendTo(player);
```

**Benefits:**
- Single message builder for multi-line messages
- Support for placeholders, centering, prefixes
- Cleaner than multiple sendMessage calls

---

## 4. Constants Applied

### Main.java
**Before:**
```java
var defaultConfig = ConfigRegistry.getInstance().get("default");
MongoManager.getInstance().initConnection(defaultConfig.getString("Database.MongoDB"));
```

**After:**
```java
var defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
MongoManager.getInstance().initConnection(defaultConfig.getString(ConfigPaths.Default.DATABASE_MONGODB));
```

**Benefits:**
- Type-safe config references
- Autocomplete support in IDE
- Prevents typos in config paths
- Easy to refactor

### BaseDataCenter.java
**Before:**
```java
messagesConfig = storageManager.getConfig("Messages");
```

**After:**
```java
messagesConfig = storageManager.getConfig(ConfigNames.MESSAGES);
```

---

## New Tools Available

### ItemBuilder
Create items with fluent API:
```java
new ItemBuilder(Material.DIAMOND_SWORD)
    .name("&cLegendary Blade")
    .lore("&7A powerful weapon", "&7+50 Damage")
    .enchant(Enchantment.DAMAGE_ALL, 5)
    .unbreakable(true)
    .hideAll()
    .build();
```

### InventoryBuilder
Create GUIs easily:
```java
new InventoryBuilder("&6Shop", 27)
    .item(13, diamondItem)
    .fillBorder(glassPane)
    .build();
```

### MessageBuilder
Format complex messages:
```java
new MessageBuilder()
    .prefix("&8[&6Shop&8]")
    .line("&7Welcome!")
    .line("&7You have &a{coins} &7coins")
    .placeholder("coins", playerCoins)
    .sendTo(player);

// Static utilities
MessageBuilder.centerMessage("&6&lTITLE");
MessageBuilder.colorize("&aSuccess!");
```

### TaskManager
Clean task scheduling:
```java
// Async task
TaskManager.getInstance().runAsync(() -> {
    // Heavy database operation
});

// Sync task
TaskManager.getInstance().runSync(() -> {
    // Bukkit API operation
});

// Delayed task
TaskManager.getInstance().runLater(() -> {
    // After 5 seconds
}, 5, TimeUnit.SECONDS);

// Repeating task
TaskManager.getInstance().runTimer(() -> {
    // Every 10 seconds
}, 0, 10, TimeUnit.SECONDS);
```

### Config Constants
Type-safe config paths:
```java
ConfigNames.DEFAULT
ConfigNames.MESSAGES

ConfigPaths.Default.DATABASE_MONGODB
ConfigPaths.Messages.* // Add your paths here
```

---

## Files Modified

1. **TestMenu.java** - Now uses ItemBuilder and MessageBuilder
2. **Utils.java** - Deprecated createItem(), now uses ItemBuilder internally
3. **BaseDataCenter.java** - Now uses TaskManager instead of direct scheduler calls
4. **Main.java** - Added TaskManager cleanup in onDisable()
5. **Main.java** - Uses ConfigNames and ConfigPaths constants

---

## Architecture Improvements

### Before
- Scattered ItemStack creation logic
- Direct Bukkit scheduler calls
- Hardcoded config strings
- Multiple sendMessage calls for multi-line messages
- No automatic task cleanup

### After
- Centralized ItemBuilder for all item creation
- Managed task scheduling with automatic cleanup
- Type-safe config constants
- Clean MessageBuilder for complex messages
- Proper shutdown sequence with task cancellation

---

## Next Steps

As you continue development:

1. **Use ItemBuilder** for all new item creation
2. **Use TaskManager** for all async/sync tasks
3. **Use MessageBuilder** for multi-line or complex messages
4. **Use Constants** for all config references
5. **Use InventoryBuilder** for simple one-off GUIs

All existing code works with these improvements while new code benefits from the cleaner APIs!
