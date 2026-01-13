package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for menus that are configured via YAML files.
 * Handles automatic loading of menu structure, items, and basic actions from configuration.
 *
 * To create a new menu:
 * 1. Create a config section in menus.yml under your menu ID
 * 2. Extend this class and implement getMenuId() and handleCustomAction()
 * 3. Register in MenuRegistry
 *
 * Example menus.yml structure:
 * <pre>
 * example_menu:
 *   title: "&b&lExample Menu"
 *   size: 27
 *   items:
 *     0:
 *       material: DIAMOND
 *       name: "&a&lClick Me"
 *       lore:
 *         - "&7This is a test item"
 *       action: "custom:my_action"
 *     8:
 *       material: BARRIER
 *       name: "&c&lClose"
 *       action: "close"
 *   fill:
 *     enabled: true
 *     material: GRAY_STAINED_GLASS_PANE
 *     name: " "
 *     slots: "0-8,18-26"  # or "border" for all edges
 * </pre>
 */
public abstract class ConfigurableMenu extends BaseMenu
{
    protected final FileConfiguration menusConfig;
    protected final Map<Integer, String> slotActions = new HashMap<>();

    public ConfigurableMenu(JavaPlugin plugin) {
        super(plugin);
        this.menusConfig = ConfigRegistry.getInstance().get("menus");
    }

    /**
     * Gets the menu ID used to lookup configuration.
     * This should match the key in menus.yml.
     *
     * @return the menu ID
     */
    protected abstract String getMenuId();

    /**
     * Handles custom actions defined in the config.
     * Called when an item with action "custom:something" is clicked.
     *
     * @param player the player who clicked
     * @param action the action name (without "custom:" prefix)
     * @param slot the slot that was clicked
     * @param event the click event
     */
    protected void handleCustomAction(Player player, String action, int slot, InventoryClickEvent event) {
        // Default implementation does nothing
        // Override in your menu class to handle custom actions
    }

    @Override
    protected String getMenuTitle(Player player) {
        String title = menusConfig.getString(getMenuId() + ".title", "&7Menu");
        return title.replace("&", "§");
    }

    @Override
    protected int getMenuSize() {
        return menusConfig.getInt(getMenuId() + ".size", 27);
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory) {
        slotActions.clear();

        // Load fill items first (so they're in the background)
        loadFillItems(inventory);

        // Load configured items
        loadConfiguredItems(player, inventory);

        // Allow custom population
        populateCustomItems(player, inventory);
    }

    /**
     * Override this to add custom items not defined in config.
     * Called after config items are loaded.
     *
     * @param player the player viewing the menu
     * @param inventory the inventory to populate
     */
    protected void populateCustomItems(Player player, Inventory inventory) {
        // Default implementation does nothing
        // Override to add dynamic/custom items
    }

    /**
     * Loads fill/background items from config.
     */
    private void loadFillItems(Inventory inventory) {
        ConfigurationSection fillSection = menusConfig.getConfigurationSection(getMenuId() + ".fill");
        if (fillSection == null || !fillSection.getBoolean("enabled", false)) {
            return;
        }

        String materialName = fillSection.getString("material", "GRAY_STAINED_GLASS_PANE");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;

        String name = fillSection.getString("name", " ");
        ItemStack fillItem = new ItemBuilder(material)
                .name(name.replace("&", "§"))
                .build();

        String slotsConfig = fillSection.getString("slots", "border");
        List<Integer> slots = parseSlots(slotsConfig, getMenuSize());

        for (int slot : slots) {
            inventory.setItem(slot, fillItem);
        }
    }

    /**
     * Loads items defined in the config.
     */
    private void loadConfiguredItems(Player player, Inventory inventory) {
        ConfigurationSection itemsSection = menusConfig.getConfigurationSection(getMenuId() + ".items");
        if (itemsSection == null) {
            return;
        }

        for (String slotKey : itemsSection.getKeys(false)) {
            try {
                int slot = Integer.parseInt(slotKey);
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(slotKey);
                if (itemSection == null) continue;

                ItemStack item = buildItemFromConfig(itemSection, player);
                if (item != null) {
                    inventory.setItem(slot, item);

                    // Store action if defined
                    String action = itemSection.getString("action");
                    if (action != null && !action.isEmpty()) {
                        slotActions.put(slot, action);
                    }
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid slot number in menu config: " + slotKey);
            }
        }
    }

    /**
     * Builds an ItemStack from a config section.
     */
    private ItemStack buildItemFromConfig(ConfigurationSection section, Player player) {
        String materialName = section.getString("material");
        if (materialName == null) return null;

        Material material = Material.getMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material in menu config: " + materialName);
            return null;
        }

        ItemBuilder builder = new ItemBuilder(material);

        // Name
        String name = section.getString("name");
        if (name != null) {
            builder.name(name.replace("&", "§"));
        }

        // Lore
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            String[] loreArray = lore.stream()
                    .map(line -> line.replace("&", "§"))
                    .toArray(String[]::new);
            builder.lore(loreArray);
        }

        // Amount
        int amount = section.getInt("amount", 1);
        if (amount > 1) {
            builder.amount(amount);
        }

        // Glow
        boolean glow = section.getBoolean("glow", false);
        if (glow) {
            builder.glow();
        }

        return builder.build();
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        String action = slotActions.get(slot);
        if (action == null) {
            return true; // No action, just cancel
        }

        // Handle built-in actions
        if (action.equalsIgnoreCase("close")) {
            closeMenu(player);
            return true;
        }

        if (action.startsWith("sound:")) {
            String soundName = action.substring(6);
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound in menu config: " + soundName);
            }
            return true;
        }

        if (action.startsWith("message:")) {
            String message = action.substring(8).replace("&", "§");
            player.sendMessage(message);
            return true;
        }

        if (action.startsWith("command:")) {
            String command = action.substring(8);
            player.performCommand(command);
            return true;
        }

        if (action.startsWith("custom:")) {
            String customAction = action.substring(7);
            handleCustomAction(player, customAction, slot, event);
            return true;
        }

        // Unknown action
        plugin.getLogger().warning("Unknown menu action: " + action);
        return true;
    }

    /**
     * Parses slot configuration into a list of slot numbers.
     * Supports:
     * - "border" for edge slots
     * - "all" for all slots
     * - "0,1,2" for specific slots
     * - "0-8" for ranges
     * - "0-8,18-26" for multiple ranges
     */
    private List<Integer> parseSlots(String config, int menuSize) {
        List<Integer> slots = new java.util.ArrayList<>();

        if (config.equalsIgnoreCase("border")) {
            int rows = menuSize / 9;
            // Top row
            for (int i = 0; i < 9; i++) {
                slots.add(i);
            }
            // Bottom row
            for (int i = menuSize - 9; i < menuSize; i++) {
                slots.add(i);
            }
            // Sides
            for (int i = 1; i < rows - 1; i++) {
                slots.add(i * 9); // Left
                slots.add(i * 9 + 8); // Right
            }
            return slots;
        }

        if (config.equalsIgnoreCase("all")) {
            for (int i = 0; i < menuSize; i++) {
                slots.add(i);
            }
            return slots;
        }

        // Parse ranges and individual slots
        String[] parts = config.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                // Range
                String[] range = part.split("-");
                try {
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    for (int i = start; i <= end && i < menuSize; i++) {
                        slots.add(i);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid slot range: " + part);
                }
            } else {
                // Single slot
                try {
                    int slot = Integer.parseInt(part);
                    if (slot < menuSize) {
                        slots.add(slot);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid slot number: " + part);
                }
            }
        }

        return slots;
    }
}
