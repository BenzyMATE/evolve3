package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;

public class BreakListener extends BaseListener
{
    private final PickupAnimationManager pickupAnimationManager = PickupAnimationManager.getInstance();

    /**
     * Constructor for BaseListener.
     *
     * @param plugin The plugin instance.
     */
    public BreakListener(JavaPlugin plugin) {
        super(plugin);
    }

    /**
     * When a player breaks a block this occurs.
     * @param event The event that holds player data.
     */
    @EventHandler
    public void OnBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        Collection<ItemStack> drops = event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player);


        pickaxeDataCenter.get(player, pickaxeProfile ->
        {
            pickupAnimationManager.playPickupAnimation(
                    player,
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    new ArrayList<>(drops),
                    event.getBlock().getType()
            );

            pickaxeProfile.xp += 2;
            pickaxeProfile.save();
            pickaxeProfile.owner.sendMessage(String.valueOf(pickaxeProfile.xp));
        });
    }
}
