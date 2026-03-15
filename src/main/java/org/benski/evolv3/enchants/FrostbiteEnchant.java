package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.FrostbiteBlockManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Listener for the Frostbite custom enchantment.
 * Freezes blocks in a radius around the mined block, converting them to PACKED_ICE.
 * Original block material is stored in metadata for restoration.
 *
 * <p>Trigger chance scales with enchantment level:</p>
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance</li>
 *   <li>Example: Level 50/100 with maxChance 15 = 7.5% chance</li>
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class FrostbiteEnchant implements Listener
{
    private final FileConfiguration enchantsConfig;
    private final FileConfiguration messagesConfig;
    private final JavaPlugin plugin;

    /**
     * Metadata key for storing original block material.
     */
    public static final String FROSTBITE_METADATA_KEY = "frostbite_original";

    public FrostbiteEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
        this.messagesConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
    }

    /**
     * Handles the block break event to trigger frostbite effect.
     *
     * @param event the block break event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!item.getType().name().contains("PICKAXE")) {
            return;
        }

        if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) {
            return;
        }

        String blockName = event.getBlock().getType().name();
        if (blockName.endsWith("_LOG") || blockName.endsWith("_STEM")) {
            return;
        }

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC != null) {
            org.benski.evolv3.models.player.PlayerProfile pp = playerDC.getLocal(player);
            if (pp != null && pp.backpack.isFull()) return;
        }

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) {
                return;
            }

            EnchantData enchant = profile.enchants.getEnchant(Enchants.FROSTBITE);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) {
                return;
            }

            Block block = event.getBlock();
            if (block.getType() == Material.PACKED_ICE || block.hasMetadata(FROSTBITE_METADATA_KEY)) {
                return;
            }

            double triggerChance = enchant.getCurrentChance();
            if (!Utils.chance(triggerChance)) {
                return;
            }

            applyFreezingEffect(block, player);
        });
    }

    /**
     * Applies the freezing effect to blocks around the center block.
     *
     * @param centerBlock the block that was mined
     * @param player      the player who triggered the effect
     */
    private void applyFreezingEffect(Block centerBlock, Player player)
    {
        EnchantPreference pref = getEnchantPref(player);
        World world = centerBlock.getWorld();
        int radius = enchantsConfig.getInt(ConfigPaths.Enchants.Frostbite.RADIUS, 1);

        List<Block> blocksToFreeze = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = centerBlock.getRelative(x, y, z);

                    if (isFreezeable(block)
                            && WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) {
                        blocksToFreeze.add(block);
                    }
                }
            }
        }

        if (blocksToFreeze.isEmpty()) {
            return;
        }

        Location center = centerBlock.getLocation();
        blocksToFreeze.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(center)));

        if (pref.sound) {
            world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        }

        final int totalBlocks = blocksToFreeze.size();
        new BukkitRunnable()
        {
            int index = 0;

            @Override
            public void run()
            {
                if (index >= blocksToFreeze.size()) {
                    this.cancel();

                    if (pref.hologram && enchantsConfig.getBoolean(ConfigPaths.Enchants.Frostbite.HOLOGRAM_ENABLED, true)) {
                        String hologramText = enchantsConfig.getString(
                                        ConfigPaths.Enchants.Frostbite.HOLOGRAM_MESSAGE,
                                        "&#00BFFF&l[Frostbite] &#CCCCCCFroze %blocks% blocks!")
                                .replace("%blocks%", String.valueOf(totalBlocks));
                        Location hologramLoc = centerBlock.getLocation().add(0.5, 1.0, 0.5);
                        AnimationRegistry.getInstance().play(
                                new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
                    }

                    if (pref.message) {
                        String message = messagesConfig.getString(ConfigPaths.Messages.Enchants.FROSTBITE_TRIGGERED);
                        if (message != null && !message.isEmpty()) {
                            message = message.replace("%blocks%", String.valueOf(totalBlocks));
                            player.sendMessage(Utils.formatMessage(player, message));
                        }
                    }

                    if (pref.title) {
                        String title    = messagesConfig.getString(ConfigPaths.Messages.Enchants.FROSTBITE_TRIGGERED_TITLE, "&#00BFFF&lFrostbite");
                        String subtitle = messagesConfig.getString(ConfigPaths.Messages.Enchants.FROSTBITE_TRIGGERED_SUBTITLE, "&#CCCCCCArea frozen!")
                                .replace("%blocks%", String.valueOf(totalBlocks));
                        player.sendTitle(
                                Utils.formatMessage(player, title),
                                Utils.formatMessage(player, subtitle),
                                5, 30, 10);
                    }
                    return;
                }

                Block block = blocksToFreeze.get(index);

                // Capture block type (e.g. COAL_ORE) for persistence/restoration,
                // and the drop material (e.g. COAL) for BreakListener metadata.
                Material originalBlockType = block.getType();
                Material dropMaterial      = getDropMaterial(block);
                block.setMetadata(FROSTBITE_METADATA_KEY,
                        new FixedMetadataValue(Main.getInstance(), dropMaterial.name()));

                block.setType(Material.PACKED_ICE);
                FrostbiteBlockManager.getInstance().addFrozenBlock(block.getLocation(), originalBlockType);

                if (pref.particles) {
                    Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                    world.spawnParticle(Particle.SNOWFLAKE, loc, 5, 0.2, 0.2, 0.2, 0.02);
                }

                index++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private EnchantPreference getEnchantPref(Player player) {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return new EnchantPreference();
        org.benski.evolv3.models.player.PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return new EnchantPreference();
        String enchantName      = enchantsConfig.getString(ConfigPaths.Enchants.Frostbite.NAME, "Frostbite");
        boolean defaultHologram = enchantsConfig.getBoolean(ConfigPaths.Enchants.Frostbite.HOLOGRAM_DEFAULT_ENABLED, false);
        return profile.preferences.getOrCreateEnchantPref(enchantName, defaultHologram);
    }

    /**
     * Checks if a block can be frozen.
     *
     * @param block the block to check
     * @return true if the block can be frozen
     */
    private boolean isFreezeable(Block block)
    {
        Material material = block.getType();

        if (material.isAir() || material == Material.WATER || material == Material.LAVA) {
            return false;
        }

        if (material == Material.PACKED_ICE || block.hasMetadata(FROSTBITE_METADATA_KEY)) {
            return false;
        }

        if (material == Material.BEDROCK || material.getHardness() < 0) {
            return false;
        }

        return true;
    }

    /**
     * Gets the material that would drop from a block.
     * Falls back to block type if no drops.
     *
     * @param block the block to check
     * @return the drop material
     */
    private Material getDropMaterial(Block block)
    {
        return block.getDrops().stream()
                .filter(i -> i != null && !i.getType().isAir())
                .findFirst()
                .map(ItemStack::getType)
                .orElse(block.getType());
    }
}
