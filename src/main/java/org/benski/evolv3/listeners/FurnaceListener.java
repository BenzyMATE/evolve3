package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.menus.FurnaceSmeltMenu;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Opens the custom furnace-smelting menu when a player right-clicks a furnace block.
 */
public class FurnaceListener extends BaseListener
{
    public FurnaceListener(JavaPlugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onFurnaceInteract(PlayerInteractEvent event) {
        // Main hand only, right-click only
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FURNACE) return;

        // Cancel the vanilla furnace GUI
        event.setCancelled(true);

        var menu = (FurnaceSmeltMenu) MenuRegistry.getInstance().get("furnace-smelt");
        if (menu != null) {
            menu.openMenu(event.getPlayer(), block);
        }
    }
}
