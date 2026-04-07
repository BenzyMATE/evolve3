package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Stone handler for the Nuclear Shower enchantment.
 *
 * <p>All stone effects are implemented inline in {@link NuclearShowerEnchant}.
 * This class exists solely to provide the shared {@code hasStone} helper.</p>
 */
public final class NuclearShowerStoneHandler
{
    private NuclearShowerStoneHandler() {}

    /**
     * Returns {@code true} if the given {@link EnchantData} has a socket stone
     * matching the requested rarity name (e.g. {@code "Common"}).
     */
    public static boolean hasStone(EnchantData enchantData, String rarity)
    {
        if (enchantData == null || enchantData.socketStones == null || rarity == null) return false;
        for (String s : enchantData.socketStones) {
            if (rarity.equals(s)) return true;
        }
        return false;
    }
}
