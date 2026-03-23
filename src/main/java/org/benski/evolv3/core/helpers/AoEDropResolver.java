package org.benski.evolv3.core.helpers;

import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.managers.FrostbiteBlockManager;
import org.benski.evolv3.enchants.AlchemyEnchant;
import org.benski.evolv3.enchants.ExtractionEnchant;
import org.benski.evolv3.enchants.FrostbiteEnchant;
import org.benski.evolv3.enchants.FurnaceEnchant;
import org.benski.evolv3.enchants.MerchantEnchant;
import org.benski.evolv3.enchants.RadioactiveEnchant;
import org.benski.evolv3.enchants.ScavengerEnchant;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

/**
 * Resolves the final drop material for a block broken by an AoE enchant.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Frostbite frozen blocks: restore original material from metadata and clean up.</li>
 *   <li>Use {@link Block#getDrops(ItemStack)} for the correct vanilla drop
 *       (e.g. stone → cobblestone, iron ore → raw iron).</li>
 *   <li>Apply Alchemy upgrade chance if the player has that enchant.</li>
 *   <li>Apply Furnace smelt chance after alchemy (so alchemy + furnace can chain).</li>
 * </ol>
 */
public final class AoEDropResolver
{
    private AoEDropResolver() {}

    /**
     * Resolves the final material to add to the backpack for a block broken by an AoE enchant.
     *
     * @param block          the block about to be set to AIR (must still be intact at call time)
     * @param tool           the player's held tool (used for correct drop computation)
     * @param plugin         the plugin instance (needed to remove Frostbite metadata)
     * @param pickaxeProfile the player's pickaxe profile (for Alchemy/Furnace enchant checks)
     * @return the final {@link Material} — never AIR for a breakable, non-frostbite-stale block
     */
    public static Material resolve(Block block, ItemStack tool, JavaPlugin plugin,
                                   PickaxeProfile pickaxeProfile)
    {
        Material mat = resolveBaseDrop(block, tool, plugin);
        mat = applyAlchemy(mat, pickaxeProfile);
        mat = applyFurnace(mat, pickaxeProfile);
        return mat;
    }

    /**
     * Rolls per-block side-effect enchants (Scavenger, Merchant) for a block broken by an AoE
     * enchant. Call this once per block, after the drop material has been resolved and the block
     * set to AIR, but before moving on to the next block.
     *
     * @param player         the player who triggered the AoE enchant
     * @param block          the block that was just broken (already AIR at this point)
     * @param pickaxeProfile the player's pickaxe profile
     * @param playerProfile  the player's player profile
     */
    public static void rollSideEffects(Player player, Block block,
                                       PickaxeProfile pickaxeProfile, PlayerProfile playerProfile)
    {
        ScavengerEnchant scavenger = ScavengerEnchant.getInstance();
        if (scavenger != null) scavenger.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);

        MerchantEnchant merchant = MerchantEnchant.getInstance();
        if (merchant != null) merchant.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);

        ExtractionEnchant extraction = ExtractionEnchant.getInstance();
        if (extraction != null) extraction.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);

        RadioactiveEnchant radioactive = RadioactiveEnchant.getInstance();
        if (radioactive != null) radioactive.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static Material resolveBaseDrop(Block block, ItemStack tool, JavaPlugin plugin)
    {
        if (block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) {
            List<MetadataValue> meta = block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY);
            block.removeMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY, plugin);
            FrostbiteBlockManager.getInstance().removeFrozenBlock(block.getLocation());
            if (!meta.isEmpty()) {
                try { return Material.valueOf(meta.get(0).asString()); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        Collection<ItemStack> drops = block.getDrops(tool);
        if (!drops.isEmpty()) {
            return drops.iterator().next().getType();
        }
        return block.getType();
    }

    private static Material applyAlchemy(Material mat, PickaxeProfile pickaxeProfile)
    {
        if (pickaxeProfile == null || pickaxeProfile.enchants == null) return mat;
        EnchantData data = pickaxeProfile.enchants.getEnchant(Enchants.ALCHEMY);
        if (data == null || !data.enabled || data.level <= 0) return mat;
        if (!Utils.chance(data.getCurrentChance())) return mat;
        AlchemyEnchant alchemy = AlchemyEnchant.getInstance();
        if (alchemy == null) return mat;
        Material upgraded = alchemy.getUpgradeResult(mat);
        return upgraded != null ? upgraded : mat;
    }

    private static Material applyFurnace(Material mat, PickaxeProfile pickaxeProfile)
    {
        if (pickaxeProfile == null || pickaxeProfile.enchants == null) return mat;
        EnchantData data = pickaxeProfile.enchants.getEnchant(Enchants.FURNACE);
        if (data == null || !data.enabled || data.level <= 0) return mat;
        if (!Utils.chance(data.getCurrentChance())) return mat;
        FurnaceEnchant furnace = FurnaceEnchant.getInstance();
        if (furnace == null) return mat;
        Material smelted = furnace.getSmeltResult(mat);
        return smelted != null ? smelted : mat;
    }
}
