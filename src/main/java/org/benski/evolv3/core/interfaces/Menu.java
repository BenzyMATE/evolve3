package org.benski.evolv3.core.interfaces;

import org.benski.evolv3.core.types.MenuType;
import org.bukkit.entity.Player;

/**
 * Interface for all menu implementations in the plugin. Provides a standard structure for creating
 * interactive GUI menus.
 */
public interface Menu {
  /**
   * Opens the menu for the specified player. This method should initialize and display the menu GUI
   * to the player.
   *
   * @param player the player to open the menu for
   */
  void openMenu(Player player);

  /**
   * Opens the menu for the specified player with a specific page number. This method is useful for
   * menus that support pagination. If the menu doesn't support pagination, this can default to
   * calling openMenu(Player).
   *
   * @param player the player to open the menu for
   * @param page the page number to display (0-based)
   * @param showPlayer the player to show the menu to (useful for admin viewing)
   */
  default void openMenu(Player player, int page, Player showPlayer) {
    openMenu(player);
  }

  /**
   * Handles cleanup when a menu is closed or when a player disconnects. This method should be
   * called to clean up any resources, stop tasks, or clear temporary data associated with the
   * player's menu session.
   *
   * @param player the player whose menu session should be cleaned up
   */
  default void cleanup(Player player) {}

  /**
   * Refreshes the menu content for the specified player. This method should update the menu display
   * with current data without closing and reopening the menu.
   *
   * @param player the player whose menu should be refreshed
   */
  default void refresh(Player player) {
    openMenu(player);
  }

  /**
   * Checks if the menu is currently open for the specified player. This can be used to determine if
   * menu-specific actions should be performed.
   *
   * @param player the player to check
   * @return true if the menu is open for the player, false otherwise
   */
  default boolean isMenuOpen(Player player) {
    return false;
  }

  /**
   * Gets the type of this menu for identification purposes.
   *
   * @return the MenuType enum value for this menu
   */
  MenuType getMenuType();
}
