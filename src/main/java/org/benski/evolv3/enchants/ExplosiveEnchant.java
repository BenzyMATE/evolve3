package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for the Explosive custom enchantment.
 * Creates an explosion when breaking blocks with tools that have the Explosive enchantment.
 * Higher levels create larger explosions.
 * <p>
 * The enchantment itself is registered in Bootstrap during server bootstrap.
 */
@ExtensionMethod(ItemStackExtensions.class)
public class ExplosiveEnchant implements Listener
{
    /**
     * Handles the block break event to create explosions.
     *
     * @param event The block break event
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if the item has this enchantment
        if (item.getType() == Material.AIR) {
            return;
        }

        // Use extension method to get the custom enchantment level
        int level = item.getCustomEnchantmentLevel("explosive");
        if (level <= 0) {
            return;
        }

        // Create an explosion at the broken block location
        Location location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);

        // Explosion power increases with enchantment level
        //  1 = 1.0, Level 2 = 1.5, Level 3 = 2.0
        float power = 0.5f + (level * 0.5f);

        // Create explosion without fire and without breaking blocks (just visual/knock back)
        location.getWorld().createExplosion(
            location,
            power,
            false,  // Don't set fire
            false   // Don't break blocks
        );

        // Send a message to the player
        player.sendMessage("§6§l[Explosive] §eYour tool triggered a level " + level + " explosion!");
    }
}
