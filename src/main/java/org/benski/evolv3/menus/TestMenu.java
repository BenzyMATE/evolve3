package org.benski.evolv3.menus;

import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.builders.MessageBuilder;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
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
    private final FileConfiguration menusCfg = ConfigRegistry.getInstance().get("menus");

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
        ItemStack infoItem = new ItemBuilder(Material.BOOK)
                .name(menusCfg.getString(""))
                .lore(
                        "&7Click for information",
                        "&7about this menu"
                )
                .build();
        inventory.setItem(11, infoItem);

        ItemStack actionItem = new ItemBuilder(Material.DIAMOND)
                .name("&a&lAction Button")
                .lore(
                        "&7Click to perform",
                        "&7an action"
                )
                .build();
        inventory.setItem(13, actionItem);

        ItemStack closeItem = new ItemBuilder(Material.BARRIER)
                .name("&c&lClose Menu")
                .lore("&7Click to close")
                .build();
        inventory.setItem(15, closeItem);

        ItemStack borderItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem(i + 18, borderItem);
        }
        inventory.setItem(9, borderItem);
        inventory.setItem(17, borderItem);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 11:
                new MessageBuilder()
                        .line("&eThis is an example menu!")
                        .line("&eYou can create menus like this easily.")
                        .sendTo(player);
                return true;

            case 13:
                sendSuccess(player, "Action performed successfully!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                refresh(player);
                return true;

            case 15:
                closeMenu(player);
                sendInfo(player, "Menu closed!");
                return true;

            default:
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
