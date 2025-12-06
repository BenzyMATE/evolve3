package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.core.interfaces.Menu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for menu implementations.
 * Provides common functionality for inventory-based menus.
 * Implements InventoryHolder to allow menus to be identified by their inventory.
 */
public abstract class BaseMenu implements Menu, InventoryHolder, Listener
{
    protected final JavaPlugin plugin;
    protected final Map<UUID, Inventory> openInventories = new HashMap<>();

    public BaseMenu(JavaPlugin plugin) {
        this.plugin = plugin;
        // Register as event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Gets the menu title for the specified player.
     * Override to provide dynamic titles.
     *
     * @param player the player viewing the menu
     * @return the menu title
     */
    protected abstract String getMenuTitle(Player player);

    /**
     * Gets the menu size (number of slots).
     * Must be a multiple of 9, between 9 and 54.
     *
     * @return the menu size
     */
    protected abstract int getMenuSize();

    /**
     * Populates the menu inventory with items.
     * Called when the menu is opened or refreshed.
     *
     * @param player the player viewing the menu
     * @param inventory the inventory to populate
     */
    protected abstract void populateMenu(Player player, Inventory inventory);

    /**
     * Handles click events within the menu.
     * Return true to cancel the click event.
     *
     * @param player the player who clicked
     * @param slot the slot that was clicked
     * @param event the click event
     * @return true to cancel the event
     */
    protected abstract boolean handleClick(Player player, int slot, InventoryClickEvent event);

    @Override
    public void openMenu(Player player) {
        // Create inventory
        Inventory inventory = Bukkit.createInventory(this, getMenuSize(), getMenuTitle(player));

        // Populate with items
        populateMenu(player, inventory);

        // Track the inventory
        openInventories.put(player.getUniqueId(), inventory);

        // Open for player
        player.openInventory(inventory);
    }

    @Override
    public void refresh(Player player) {
        Inventory inventory = openInventories.get(player.getUniqueId());
        if (inventory != null) {
            // Clear and repopulate
            inventory.clear();
            populateMenu(player, inventory);

            // Update the player's view
            player.updateInventory();
        } else {
            // Menu not open, just open it
            openMenu(player);
        }
    }

    @Override
    public boolean isMenuOpen(Player player) {
        return openInventories.containsKey(player.getUniqueId());
    }

    @Override
    public void cleanup(Player player) {
        openInventories.remove(player.getUniqueId());
    }

    @Override
    public Inventory getInventory() {
        // Required by InventoryHolder, but not used for player-specific inventories
        return null;
    }

    /**
     * Event handler for inventory clicks.
     * Routes clicks to the appropriate menu implementation.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if this click is in one of our menus
        if (event.getClickedInventory() == null) {
            return;
        }

        if (!(event.getClickedInventory().getHolder() instanceof BaseMenu)) {
            return;
        }

        BaseMenu menu = (BaseMenu) event.getClickedInventory().getHolder();

        // Only handle if it's this menu instance
        if (menu != this) {
            return;
        }

        // Check if player has this menu open
        if (!openInventories.containsKey(player.getUniqueId())) {
            return;
        }

        // Always cancel the event initially
        event.setCancelled(true);

        // Let the implementation handle the click
        try {
            handleClick(player, event.getSlot(), event);
        } catch (Exception e) {
            player.sendMessage("§cAn error occurred while handling your click!");
            plugin.getLogger().severe("Error handling menu click: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Utility methods for menu creation

    /**
     * Sends a success message to the player.
     *
     * @param player the player
     * @param message the message
     */
    protected void sendSuccess(Player player, String message) {
        player.sendMessage("§a" + message);
    }

    /**
     * Sends an error message to the player.
     *
     * @param player the player
     * @param message the message
     */
    protected void sendError(Player player, String message) {
        player.sendMessage("§c" + message);
    }

    /**
     * Sends an info message to the player.
     *
     * @param player the player
     * @param message the message
     */
    protected void sendInfo(Player player, String message) {
        player.sendMessage("§e" + message);
    }

    /**
     * Closes the menu for a player.
     *
     * @param player the player
     */
    protected void closeMenu(Player player) {
        player.closeInventory();
        cleanup(player);
    }
}