package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

import java.util.UUID;

/**
 * Helper for Evolution Stones socketed into the Frostbite enchant.
 *
 * <ul>
 *   <li><b>Common</b>    (Cold Snap)  — +1 to freeze radius; inline in applyFreezingEffect</li>
 *   <li><b>Uncommon</b>  (Frost Vein) — +3% to Frostbite trigger chance; inline in onBlockBreak</li>
 *   <li><b>Rare</b>      (Ice Pick)   — breaks a small radius of frozen blocks in one hit; inline in onFrozenBlockBreak</li>
 *   <li><b>Epic</b>      (Cryo Atom)  — 5% chance to discover an atom when mining a frozen block; inline in onFrozenBlockBreak</li>
 *   <li><b>Legendary</b> (Blizzard)   — 10% chance on each Frostbite proc to instantly shatter all frozen blocks; inline in applyFreezingEffect</li>
 * </ul>
 */
public final class FrostbiteStoneHandler
{
    private FrostbiteStoneHandler() {}

    // -------------------------------------------------------------------------

    public static boolean hasStone(EnchantData enchant, String rarity) {
        for (String stone : enchant.socketStones) {
            if (rarity.equals(stone)) return true;
        }
        return false;
    }

    // ---- Cleanup ----

    public static void cleanup(UUID uuid) {
        // no per-player state currently; kept for future use
    }
}
