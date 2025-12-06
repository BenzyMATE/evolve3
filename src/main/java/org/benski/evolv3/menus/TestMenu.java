package org.benski.evolv3.menus;

import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test menu to test how menus work within evolve
 */
public class TestMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("profile");

    public TestMenu(JavaPlugin plugin) {
        super(plugin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getMenuTitle(Player player) {
        return "&bTest Menu";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getMenuSize() {
        return 27;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateMenu(Player player, Inventory inventory)
    {
        // Info item
        ItemStack infoItem = Utils.createItem(
                Material.BOOK,
                "§e§lInformation",
                "§7Click for information",
                "§7about this menu"
        );
        inventory.setItem(11, infoItem);

        // Action item
        ItemStack actionItem = Utils.createItem(
                Material.DIAMOND,
                "§a§lAction Button",
                "§7Click to perform",
                "§7an action"
        );
        inventory.setItem(13, actionItem);

        // Close item
        ItemStack closeItem = Utils.createItem(
                Material.BARRIER,
                "§c§lClose Menu",
                "§7Click to close"
        );
        inventory.setItem(15, closeItem);

        // Fill borders with glass panes
        ItemStack borderItem = Utils.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem); // Top row
            inventory.setItem(i + 18, borderItem); // Bottom row
        }
        inventory.setItem(9, borderItem); // Left side
        inventory.setItem(17, borderItem); // Right side
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 11: // Info item
                sendInfo(player, "This is an example menu!");
                sendInfo(player, "You can create menus like this easily.");
                return true;

            case 13: // Action item
                sendSuccess(player, "Action performed successfully!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // Refresh the menu after action
                refresh(player);
                return true;

            case 15: // Close item
                closeMenu(player);
                sendInfo(player, "Menu closed!");
                return true;

            default:
                // Clicked on border or empty slot
                return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuType getMenuType() {
        return MENU_TYPE;
    }
}