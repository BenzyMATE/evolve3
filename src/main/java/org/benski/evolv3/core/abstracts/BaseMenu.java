package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for menu implementations.
 * Provides common functionality for inventory-based menus.
 * Implements InventoryHolder to allow menus to be identified by their inventory.
 * Supports both static and dynamic titles with configurable refresh intervals.
 */
public abstract class BaseMenu implements Menu, InventoryHolder, Listener
{
    protected final JavaPlugin plugin;
    protected final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Map<UUID, BukkitTask> titleUpdateTasks = new HashMap<>();

    public BaseMenu(JavaPlugin plugin) {
        this.plugin = plugin;
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

    /**
     * Determines whether this menu uses a dynamic title.
     * Override and return true to enable dynamic title updates.
     *
     * @return true if the title should update dynamically, false for static title
     */
    protected boolean isDynamicTitle() {
        return false;
    }

    /**
     * Gets the interval in ticks between title updates.
     * Only used when {@link #isDynamicTitle()} returns true.
     * Default is 20 ticks (1 second).
     *
     * @return the title update interval in ticks
     */
    protected int getTitleUpdateInterval() {
        return 20;
    }

    /**
     * Called when the menu is opened for a player.
     * Override to play sounds or run other on-open logic.
     *
     * @param player the player opening the menu
     */
    protected void onOpen(Player player) {}

    /**
     * Called when the menu is closed for a player.
     * Override to play sounds or run other on-close logic.
     *
     * @param player the player closing the menu
     */
    protected void onClose(Player player) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(this, getMenuSize(), formatMessage(player, getMenuTitle(player)));

        populateMenu(player, inventory);

        openInventories.put(player.getUniqueId(), inventory);

        player.openInventory(inventory);

        onOpen(player);

        if (isDynamicTitle()) {
            startTitleUpdateTask(player);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh(Player player) {
        Inventory inventory = openInventories.get(player.getUniqueId());
        if (inventory != null) {
            inventory.clear();
            populateMenu(player, inventory);

            player.updateInventory();
        } else {
            openMenu(player);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMenuOpen(Player player) {
        return openInventories.containsKey(player.getUniqueId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup(Player player) {
        openInventories.remove(player.getUniqueId());
        stopTitleUpdateTask(player);
    }

    /**
     * Starts the dynamic title update task for a player.
     *
     * @param player the player to start updates for
     */
    private void startTitleUpdateTask(Player player) {
        stopTitleUpdateTask(player);

        int interval = getTitleUpdateInterval();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !isMenuOpen(player)) {
                stopTitleUpdateTask(player);
                return;
            }

            updateTitle(player);
        }, interval, interval);

        titleUpdateTasks.put(player.getUniqueId(), task);
    }

    /**
     * Stops the dynamic title update task for a player.
     *
     * @param player the player to stop updates for
     */
    private void stopTitleUpdateTask(Player player) {
        BukkitTask task = titleUpdateTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Updates the title of the menu for a player.
     * Uses Paper's InventoryView.setTitle() method.
     *
     * @param player the player whose menu title should be updated
     */
    protected void updateTitle(Player player) {
        Inventory inventory = openInventories.get(player.getUniqueId());
        if (inventory == null) {
            return;
        }

        if (player.getOpenInventory().getTopInventory() != inventory) {
            return;
        }

        String newTitle = formatMessage(player, getMenuTitle(player));

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            plugin.getLogger().warning("Error setting title: " + e.getMessage());
        }
    }

    /**
     * Not supported — this menu manages per-player inventories.
     * Use {@link #openInventories} to access a specific player's inventory.
     */
    @Override
    public Inventory getInventory() {
        throw new UnsupportedOperationException(
                "BaseMenu manages per-player inventories. Use openInventories.get(playerUuid) instead.");
    }

    /**
     * Event handler for inventory close.
     * Cleans up resources when a player closes the menu.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof BaseMenu menu)) {
            return;
        }

        if (menu != this) {
            return;
        }

        onClose(player);
        cleanup(player);
    }

    /**
     * Event handler for inventory clicks.
     * Routes clicks to the appropriate menu implementation.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        if (!(event.getClickedInventory().getHolder() instanceof BaseMenu menu)) {
            return;
        }

        if (menu != this) {
            return;
        }

        if (!openInventories.containsKey(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        try {
            handleClick(player, event.getSlot(), event);
        } catch (Exception e) {
            String msg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Errors.INTERNAL_ERROR,
                            "&cSomething went wrong. Please try again.");
            player.sendMessage(formatMessage(player, msg));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error handling menu click", e);
        }
    }

    /**
     * Sends a success message to the player.
     *
     * @param player the player
     * @param message the message
     */
    protected void sendSuccess(Player player, String message) {
        Messageable.sendSuccess(player, message);
    }

    /**
     * Sends an error message to the player.
     *
     * @param player the player
     * @param message the message
     */
    protected void sendError(Player player, String message) {
        Messageable.sendError(player, message);
    }

    /**
     * Sends an info message to the player.
     *
     * @param player the player
     * @param message the message
     */
    protected void sendInfo(Player player, String message) {
        Messageable.sendInfo(player, message);
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
