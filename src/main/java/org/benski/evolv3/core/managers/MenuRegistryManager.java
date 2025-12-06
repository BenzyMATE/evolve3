package org.benski.evolv3.core.managers;

import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Core manager that handles menu registration, tracking, and event handling.
 * Automatically tracks which menus are open for which players.
 */
public class MenuRegistryManager implements Listener
{
    private final JavaPlugin plugin;
    private final Map<MenuType, Menu> menuMap = new HashMap<>();
    private final Map<UUID, MenuType> openMenus = new HashMap<>();
    private final List<Class<? extends Menu>> menuClasses = new ArrayList<>();

    public MenuRegistryManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a menu class to be initialized later.
     *
     * @param menuClass the menu class to register
     */
    public void registerMenu(Class<? extends Menu> menuClass) {
        menuClasses.add(menuClass);
    }

    /**
     * Initializes all registered menus.
     * Creates instances and registers event listeners.
     */
    public void initializeMenus() {
        for (Class<? extends Menu> menuClass : menuClasses) {
            try {
                // Create instance using constructor with JavaPlugin parameter
                Constructor<? extends Menu> constructor = menuClass.getConstructor(JavaPlugin.class);
                Menu menu = constructor.newInstance(plugin);

                // Get menu type from the instance
                MenuType menuType = menu.getMenuType();

                // Register in our map
                menuMap.put(menuType, menu);

                plugin.getLogger().info("Registered menu: " + menuType.toString());

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register menu " + menuClass.getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens a menu for a player.
     *
     * @param player the player to open the menu for
     * @param menuType the type of menu to open
     */
    public void openMenu(Player player, MenuType menuType) {
        Menu menu = menuMap.get(menuType);
        if (menu == null) {
            player.sendMessage("§cMenu not found: " + menuType.toString());
            plugin.getLogger().warning("Attempted to open unregistered menu: " + menuType.toString());
            return;
        }

        // Clean up any existing menu first
        cleanupPlayer(player);

        // Open the new menu
        menu.openMenu(player);

        // Track that this player has this menu open
        openMenus.put(player.getUniqueId(), menuType);
    }

    /**
     * Opens a menu with pagination and optional admin viewing.
     *
     * @param player the player to open the menu for
     * @param menuType the type of menu to open
     * @param page the page number to display
     * @param showPlayer the player to show the menu to
     */
    public void openMenu(Player player, MenuType menuType, int page, Player showPlayer) {
        Menu menu = menuMap.get(menuType);
        if (menu == null) {
            showPlayer.sendMessage("§cMenu not found: " + menuType.toString());
            plugin.getLogger().warning("Attempted to open unregistered menu: " + menuType.toString());
            return;
        }

        // Clean up any existing menu first
        cleanupPlayer(showPlayer);

        // Open the new menu with pagination
        menu.openMenu(player, page, showPlayer);

        // Track that this player has this menu open
        openMenus.put(showPlayer.getUniqueId(), menuType);
    }

    /**
     * Refreshes a player's currently open menu.
     *
     * @param player the player whose menu should be refreshed
     */
    public void refreshMenu(Player player) {
        MenuType menuType = openMenus.get(player.getUniqueId());
        if (menuType == null) {
            return; // No menu open
        }

        Menu menu = menuMap.get(menuType);
        if (menu != null) {
            menu.refresh(player);
        }
    }

    /**
     * Cleans up a player's menu session.
     *
     * @param player the player to clean up
     */
    public void cleanupPlayer(Player player) {
        MenuType menuType = openMenus.remove(player.getUniqueId());
        if (menuType != null) {
            Menu menu = menuMap.get(menuType);
            if (menu != null) {
                menu.cleanup(player);
            }
        }
    }

    /**
     * Checks if a player has a menu open.
     *
     * @param player the player to check
     * @return true if the player has a menu open
     */
    public boolean hasMenuOpen(Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }

    /**
     * Gets the type of menu a player currently has open.
     *
     * @param player the player to check
     * @return the MenuType, or null if no menu is open
     */
    public MenuType getOpenMenuType(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    /**
     * Gets a menu instance by type.
     *
     * @param menuType the menu type
     * @return the menu instance, or null if not found
     */
    public Menu getMenu(MenuType menuType) {
        return menuMap.get(menuType);
    }

    /**
     * Checks if a menu type is registered.
     *
     * @param menuType the menu type
     * @return true if registered
     */
    public boolean isMenuRegistered(MenuType menuType) {
        return menuMap.containsKey(menuType);
    }

    // Event handlers

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();

            // Only cleanup if the player actually has a menu open
            MenuType menuType = openMenus.get(player.getUniqueId());
            if (menuType != null) {
                Menu menu = menuMap.get(menuType);
                if (menu != null && !menu.isMenuOpen(player)) {
                    // Menu is closed, cleanup
                    cleanupPlayer(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cleanup when player disconnects
        cleanupPlayer(event.getPlayer());
    }

    /**
     * Cleanup all registered menus and tracked data.
     */
    public void cleanup() {
        // Cleanup all open menus
        for (UUID uuid : new ArrayList<>(openMenus.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                cleanupPlayer(player);
            }
        }

        openMenus.clear();
        menuMap.clear();
        menuClasses.clear();
    }
}