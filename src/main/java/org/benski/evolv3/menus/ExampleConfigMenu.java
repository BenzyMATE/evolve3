package org.benski.evolv3.menus;

import org.benski.evolv3.core.abstracts.ConfigurableMenu;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Example menu that demonstrates ConfigurableMenu usage.
 * Most of the menu structure is defined in menus.yml under "example_config_menu".
 * Only custom actions need to be handled in code.
 */
public class ExampleConfigMenu extends ConfigurableMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("example_config");

    public ExampleConfigMenu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String getMenuId() {
        return "example_config_menu";
    }

    @Override
    protected void handleCustomAction(Player player, String action, int slot, InventoryClickEvent event) {
        switch (action) {
            case "info":
                sendInfo(player, "This menu is configured via menus.yml!");
                break;

            case "action":
                sendSuccess(player, "Custom action performed!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                refresh(player);
                break;

            case "give_diamond":
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND));
                sendSuccess(player, "Gave you a diamond!");
                break;

            default:
                sendError(player, "Unknown action: " + action);
                break;
        }
    }

    @Override
    public MenuType getMenuType() {
        return MENU_TYPE;
    }
}
