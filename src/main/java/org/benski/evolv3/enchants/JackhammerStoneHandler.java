package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Stone handler for the Jackhammer enchantment.
 * No per-player state is needed — all effects are applied inline during the layer smash.
 */
public final class JackhammerStoneHandler
{
    private JackhammerStoneHandler() {}

    /**
     * Returns true if the given enchant has the specified rarity stone socketed.
     */
    public static boolean hasStone(EnchantData enchant, String rarity) {
        for (String stone : enchant.socketStones) {
            if (rarity.equals(stone)) return true;
        }
        return false;
    }
}
