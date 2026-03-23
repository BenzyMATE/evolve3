package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for the Fortune custom enchantment.
 * Passively increases the number of blocks received per block broken.
 *
 * <p>Drop amount scales with enchantment level in tiers of 20:</p>
 * <ul>
 *   <li>Level  1–19: 2 blocks</li>
 *   <li>Level 20–39: 3 blocks</li>
 *   <li>Level 40–59: 4 blocks</li>
 *   <li>Level 60–79: 5 blocks</li>
 *   <li>Level 80–99: 6 blocks</li>
 *   <li>Level   100: 7 blocks</li>
 * </ul>
 *
 * <p>At NORMAL priority, writes the fortune drop amount to {@link #pendingDropAmount}
 * so {@code BreakListener} (HIGHEST) can use it as the final drop count.
 * Stacks with Alchemy and Furnace — Fortune controls quantity, those control type.</p>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class FortuneEnchant implements Listener
{
    /** Pending fortune drop amount per player, consumed by BreakListener at HIGHEST priority. */
    private static final Map<UUID, Integer> pendingDropAmount = new ConcurrentHashMap<>();

    /**
     * Returns and removes the pending fortune drop amount for a player.
     * Called by {@code BreakListener} after all NORMAL/HIGH enchant listeners have fired.
     *
     * @param uuid the player's UUID
     * @return the fortune drop amount, or -1 if Fortune did not apply
     */
    public static int popDropAmount(UUID uuid)
    {
        Integer amount = pendingDropAmount.remove(uuid);
        return amount != null ? amount : -1;
    }

    /**
     * Computes the drop amount for a given Fortune level.
     *
     * @param level the current enchant level
     * @return blocks per break (2 at level 1, +1 per 20 levels, max 7 at level 100)
     */
    public static int getDropAmount(int level)
    {
        return 2 + (level / 20);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!Utils.isPickaxe(item.getType())) {
            return;
        }

        if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) {
            return;
        }

        String blockName = event.getBlock().getType().name();
        if (blockName.endsWith("_LOG") || blockName.endsWith("_STEM")) {
            return;
        }

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) return;

            EnchantData enchant = profile.enchants.getEnchant(Enchants.FORTUNE);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

            pendingDropAmount.put(player.getUniqueId(), getDropAmount(enchant.level));
        });
    }
}
