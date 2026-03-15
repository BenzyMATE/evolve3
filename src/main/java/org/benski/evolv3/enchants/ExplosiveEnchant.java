package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.animations.ExplosionAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Listener for the Explosive custom enchantment.
 * Breaks blocks in a radius around the mined block and creates
 * an animated explosion effect with blocks flying outward.
 *
 * <p>Higher levels increase the explosion radius:</p>
 * <ul>
 *   <li>Level 1: 1 block radius (3x3x3 = up to 27 blocks)</li>
 *   <li>Level 2: 2 block radius (5x5x5 = up to 125 blocks)</li>
 *   <li>Level 3: 3 block radius (7x7x7 = up to 343 blocks)</li>
 * </ul>
 *
 * <p>The enchantment itself is registered in Bootstrap during server bootstrap.</p>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class ExplosiveEnchant implements Listener
{
    private final FileConfiguration messagesConfig;
    private final JavaPlugin plugin;

    private static final Set<Material> PROTECTED_MATERIALS = Set.of(
        Material.BEDROCK,
        Material.BARRIER,
        Material.COMMAND_BLOCK,
        Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK,
        Material.STRUCTURE_BLOCK,
        Material.STRUCTURE_VOID,
        Material.JIGSAW,
        Material.END_PORTAL_FRAME,
        Material.END_PORTAL,
        Material.NETHER_PORTAL,
        Material.SPAWNER
    );

    public ExplosiveEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.messagesConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
    }

    /**
     * Handles the block break event to create explosion effects.
     *
     * @param event the block break event
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            return;
        }

        int level = item.getCustomEnchantmentLevel("explosive");
        if (level <= 0) {
            return;
        }

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC != null) {
            org.benski.evolv3.models.player.PlayerProfile pp = playerDC.getLocal(player);
            if (pp != null && pp.backpack.isFull()) return;
        }

        Block centerBlock = event.getBlock();
        Location center = centerBlock.getLocation().add(0.5, 0.5, 0.5);

        List<Block> blocksToBreak = getBlocksInRadius(centerBlock, level);

        List<Material> materials = new ArrayList<>();
        materials.add(centerBlock.getType());

        for (Block block : blocksToBreak) {
            if (block.equals(centerBlock)) continue;

            Material material = block.getType();
            if (!material.isAir()) {
                materials.add(material);
                block.setType(Material.AIR);
            }
        }

        if (!materials.isEmpty()) {
            ExplosionAnimation animation = new ExplosionAnimation(plugin, materials, level);

            if (AnimationRegistry.getInstance().canPlay(animation)) {
                AnimationRegistry.getInstance().play(animation, center);
            }
        }

        String message = messagesConfig.getString("enchants.explosive.triggered",
            "&6&l[Explosive] &eLevel %level% explosion! Broke %blocks% blocks!");
        message = message.replace("%level%", String.valueOf(level));
        message = message.replace("%blocks%", String.valueOf(blocksToBreak.size() + 1));
        player.sendMessage(formatMessage(player, message));
    }

    /**
     * Gets all breakable blocks within a radius of the center block.
     *
     * @param center the center block
     * @param level the explosion level (determines radius)
     * @return list of blocks to break
     */
    private List<Block> getBlocksInRadius(Block center, int level) {
        List<Block> blocks = new ArrayList<>();
        int radius = level;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, y, z);

                    if (isBreakable(block)) {
                        blocks.add(block);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Checks if a block can be broken by the explosion.
     *
     * @param block the block to check
     * @return true if the block can be broken
     */
    private boolean isBreakable(Block block) {
        Material material = block.getType();

        if (material.isAir() || material == Material.WATER || material == Material.LAVA) {
            return false;
        }

        if (PROTECTED_MATERIALS.contains(material)) {
            return false;
        }

        if (material.getHardness() < 0) {
            return false;
        }

        return true;
    }
}
