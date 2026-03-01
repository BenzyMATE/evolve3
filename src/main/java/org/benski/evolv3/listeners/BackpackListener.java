package org.benski.evolv3.listeners;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Handles backpack item placement and F-key interaction.
 * Sets a custom model data stick in the player's offhand on join
 * and opens the backpack menu when the player presses F (swap hand).
 */
public class BackpackListener extends BaseListener
{
    private static final NamespacedKey BACKPACK_KEY = new NamespacedKey(Main.getInstance(), "backpack");

    /**
     * Constructor for BackpackListener.
     *
     * @param plugin The plugin instance
     */
    public BackpackListener(JavaPlugin plugin)
    {
        super(plugin);
    }

    /**
     * Sets the backpack item in the player's offhand on join.
     * Uses a short delay to ensure the player's inventory is ready.
     *
     * @param event The player join event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                setBackpackItem(player);
            }
        }, 5L);
    }

    /**
     * Intercepts the F-key press (swap hand items) to open the backpack menu.
     * Cancels the swap event and opens the backpack if the offhand holds a backpack item.
     *
     * @param event The swap hand items event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event)
    {
        Player player = event.getPlayer();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        if (!isBackpackItem(offHandItem)) {
            return;
        }

        event.setCancelled(true);

        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);

        Menu backpackMenu = MenuRegistry.getInstance().get("backpack");
        if (backpackMenu != null) {
            backpackMenu.openMenu(player);
        }
    }

    /**
     * Creates and places the backpack item in the player's offhand.
     * The item is a stick with custom model data and a PDC tag for identification.
     *
     * @param player The player to give the backpack item to
     */
    private void setBackpackItem(Player player)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);

        String name = config.getString(ConfigPaths.Default.Backpack.NAME, "&8Backpack");
        List<String> lore = config.getStringList(ConfigPaths.Default.Backpack.LORE);
        int customModelData = config.getInt(ConfigPaths.Default.Backpack.CUSTOM_MODEL_DATA, 1);

        String[] formattedLore = lore.stream()
                .map(line -> formatMessage(player, line))
                .toArray(String[]::new);

        ItemStack backpackItem = new ItemBuilder(Material.STICK)
                .name(formatMessage(player, name))
                .lore(formattedLore)
                .customModelData(customModelData)
                .pdc(container -> container.set(BACKPACK_KEY, PersistentDataType.BYTE, (byte) 1))
                .build();

        player.getInventory().setItemInOffHand(backpackItem);
    }

    /**
     * Checks whether an item is a backpack item by verifying the PDC tag.
     *
     * @param item The item to check
     * @return true if the item is a backpack item
     */
    private boolean isBackpackItem(ItemStack item)
    {
        if (item == null || item.getType() != Material.STICK) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(BACKPACK_KEY, PersistentDataType.BYTE);
    }
}
