package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.enchants.AlchemyEnchant;
import org.benski.evolv3.enchants.FrostbiteEnchant;
import org.benski.evolv3.enchants.FurnaceEnchant;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * Listener for handling frozen block (Frostbite) restoration and drop modification.
 *
 * <p>When a block frozen by Frostbite is broken, this listener:</p>
 * <ul>
 *   <li>Restores the original material for drop calculation</li>
 *   <li>Clears the frostbite metadata after processing</li>
 *   <li>Handles drop modifications from Alchemy and Furnace enchants</li>
 * </ul>
 *
 * <p>This listener runs at HIGHEST priority to process after all enchants
 * have had a chance to modify the drops.</p>
 */
public class FrostbiteListener extends BaseListener
{
    public FrostbiteListener(JavaPlugin plugin)
    {
        super(plugin);
    }

    /**
     * Handles the block break event for frozen blocks and enchant drop modifications.
     * Runs at HIGHEST priority to process after all enchants.
     *
     * @param event the block break event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Block block = event.getBlock();

        boolean hasFrostbite = block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY);
        boolean hasAlchemy = block.hasMetadata(AlchemyEnchant.ALCHEMY_RESULT_KEY);
        boolean hasFurnace = block.hasMetadata(FurnaceEnchant.FURNACE_RESULT_KEY);

        if (!hasFrostbite && !hasAlchemy && !hasFurnace) {
            return;
        }

        Material finalDrop = determineFinalDrop(block, hasFrostbite, hasAlchemy, hasFurnace);

        if (finalDrop != null && finalDrop != Material.AIR) {
            event.setDropItems(false);

            ItemStack dropItem = new ItemStack(finalDrop, 1);
            block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
        }

        cleanupMetadata(block, hasFrostbite, hasAlchemy, hasFurnace);
    }

    /**
     * Determines the final drop material based on enchant modifications.
     * Priority: Furnace > Alchemy > Frostbite Original > Default
     *
     * @param block        the block being broken
     * @param hasFrostbite whether frostbite metadata is present
     * @param hasAlchemy   whether alchemy metadata is present
     * @param hasFurnace   whether furnace metadata is present
     * @return the final material to drop
     */
    private Material determineFinalDrop(Block block, boolean hasFrostbite, boolean hasAlchemy, boolean hasFurnace)
    {
        if (hasFurnace) {
            try {
                String furnaceResult = block.getMetadata(FurnaceEnchant.FURNACE_RESULT_KEY).get(0).asString();
                return Material.valueOf(furnaceResult);
            } catch (Exception e) {
            }
        }

        if (hasAlchemy) {
            try {
                String alchemyResult = block.getMetadata(AlchemyEnchant.ALCHEMY_RESULT_KEY).get(0).asString();
                return Material.valueOf(alchemyResult);
            } catch (Exception e) {
            }
        }

        if (hasFrostbite) {
            try {
                String originalMaterial = block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY).get(0).asString();
                return Material.valueOf(originalMaterial);
            } catch (Exception e) {
            }
        }

        Collection<ItemStack> drops = block.getDrops();
        if (!drops.isEmpty()) {
            return drops.iterator().next().getType();
        }

        return block.getType();
    }

    /**
     * Cleans up enchant metadata from the block.
     *
     * @param block        the block to clean
     * @param hasFrostbite whether frostbite metadata is present
     * @param hasAlchemy   whether alchemy metadata is present
     * @param hasFurnace   whether furnace metadata is present
     */
    private void cleanupMetadata(Block block, boolean hasFrostbite, boolean hasAlchemy, boolean hasFurnace)
    {
        if (hasFrostbite) {
            block.removeMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY, plugin);
        }
        if (hasAlchemy) {
            block.removeMetadata(AlchemyEnchant.ALCHEMY_RESULT_KEY, plugin);
        }
        if (hasFurnace) {
            block.removeMetadata(FurnaceEnchant.FURNACE_RESULT_KEY, plugin);
        }
    }
}
