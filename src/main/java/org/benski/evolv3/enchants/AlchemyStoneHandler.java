package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Alchemy enchant.
 * All stone effects are resolved inline inside {@link AlchemyEnchant#onBlockBreak}.
 *
 * <ul>
 *   <li><b>Common</b>    (Amplifier)          — +10% trigger chance; inline</li>
 *   <li><b>Uncommon</b>  (Overflow)           — +10% trigger chance; inline</li>
 *   <li><b>Rare</b>      (Shortcut)           — 20% chance to skip one tier; inline</li>
 *   <li><b>Epic</b>      (Transcend)          — 30% chance to land within 2 tiers of the max; inline</li>
 *   <li><b>Legendary</b> (Philosopher's Touch)— Always proc; inline</li>
 * </ul>
 */
public final class AlchemyStoneHandler
{
    private AlchemyStoneHandler() {}

    /** Returns {@code true} if the given enchant has the specified rarity stone socketed. */
    public static boolean hasStone(EnchantData enchant, String rarity) {
        for (String stone : enchant.socketStones) {
            if (rarity.equals(stone)) return true;
        }
        return false;
    }
}
