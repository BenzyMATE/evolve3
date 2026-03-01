package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class InteractListener extends BaseListener
{
    private final MenuRegistry menuRegistry = MenuRegistry.getInstance();

    /**
     * Constructor for BaseListener.
     *
     * @param plugin The plugin instance.
     */
    public InteractListener(JavaPlugin plugin) {
        super(plugin);
    }

    /**
     * When a player right-clicks while holding a pickaxe.
     *
     * @param event The event that holds player data.
     */
    @EventHandler
    public void openPickaxeMenu(PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction().isLeftClick()) return;

        var player = event.getPlayer();

        if (player.getInventory().getItemInMainHand().getType().toString().contains("PICKAXE"))
        {
            var menu = menuRegistry.get("pickaxe");
            if (menu != null) {
                menu.openMenu(player);
            }
        }
    }
}
