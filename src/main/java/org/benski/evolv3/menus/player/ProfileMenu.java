package org.benski.evolv3.menus.player;

import org.benski.evolv3.core.abstracts.ConfigurableMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Profile menu that combines config-based and dynamic items. Static menu structure is defined in
 * menus.yml under "profile_menu". Dynamic player stats are added programmatically.
 */
public class ProfileMenu extends ConfigurableMenu {
  private static final MenuType MENU_TYPE = MenuType.register("profile");

  /** Creates and registers the ProfileMenu. @param plugin the owning plugin */
  public ProfileMenu(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  protected String getMenuId() {
    return "profile_menu";
  }

  /** {@inheritDoc} */
  @Override
  protected void populateCustomItems(Player player, Inventory inventory) {
    ItemStack playerHead =
        new ItemBuilder(Material.PLAYER_HEAD)
            .name("&e&l" + player.getName())
            .lore(
                "&7UUID: &f" + player.getUniqueId().toString().substring(0, 8) + "...",
                "&7Health: &c" + String.format("%.1f", player.getHealth()) + "/20.0",
                "&7Food Level: &6" + player.getFoodLevel() + "/20",
                "&7Experience Level: &a" + player.getLevel())
            .build();
    inventory.setItem(4, playerHead);

    ItemStack statsItem =
        new ItemBuilder(Material.DIAMOND_SWORD)
            .name("&b&lYour Statistics")
            .lore(
                "&7Total Playtime: &f" + formatPlaytime(player),
                "&7Current World: &f" + player.getWorld().getName(),
                "&7Game Mode: &f" + player.getGameMode().name())
            .build();
    inventory.setItem(20, statsItem);
  }

  /** {@inheritDoc} */
  @Override
  protected void handleCustomAction(
      Player player, String action, int slot, InventoryClickEvent event) {
    switch (action) {
      case "view_stats":
        sendInfo(player, "Opening detailed statistics...");
        break;

      case "combat_stats":
        sendInfo(player, "Combat stats would appear here!");
        sendInfo(player, "This is just an example.");
        break;

      case "open_settings":
        sendInfo(player, "Settings menu would open here!");
        closeMenu(player);
        break;

      default:
        sendError(player, "Unknown profile action: " + action);
        break;
    }
  }

  /** {@inheritDoc} */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  /** Formats player playtime into a readable string. */
  private String formatPlaytime(Player player) {
    long ticks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
    long seconds = ticks / 20;
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;

    if (hours > 0) {
      return hours + "h " + minutes + "m";
    } else {
      return minutes + "m";
    }
  }
}
