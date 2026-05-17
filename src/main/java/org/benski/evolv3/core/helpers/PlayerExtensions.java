package org.benski.evolv3.core.helpers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Extension methods for Player to provide cleaner API for common operations. Use with
 * Lombok's @ExtensionMethod(PlayerExtensions.class) annotation.
 */
public class PlayerExtensions {
  /**
   * Gets the ID of the item the player is holding in their main hand.
   *
   * @param player The player
   * @return The item ID, or null if the item has no ID or player is holding nothing
   */
  @Nullable
  public static String getItemId(Player player) {
    if (player == null) return null;

    ItemStack item = player.getInventory().getItemInMainHand();
    if (item.getType() == Material.AIR) return null;

    String id = ItemStackExtensions.getData(item, "id");
    return (id == null || id.isEmpty()) ? null : id;
  }
}
