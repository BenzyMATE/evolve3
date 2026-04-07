package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stone handler for the Lazer enchantment.
 *
 * <p>Tracks the Rare (Iron Fist) proc counter — every 10th Lazer proc triggers
 * a Jackhammer layer break at the point of impact.</p>
 */
public final class LazerStoneHandler
{
    /** Per-player Lazer proc count for the Rare (Iron Fist) stone. */
    private static final Map<UUID, Integer> ironFistCounters = new ConcurrentHashMap<>();

    private LazerStoneHandler() {}

    public static boolean hasStone(EnchantData enchant, String rarity) {
        for (String stone : enchant.socketStones) {
            if (rarity.equals(stone)) return true;
        }
        return false;
    }

    /**
     * Increments the Lazer proc counter for this player and returns true
     * if the 10th proc has been reached (resets to 0 on trigger).
     */
    public static boolean incrementAndCheckIronFist(UUID uuid) {
        int count = ironFistCounters.merge(uuid, 1, Integer::sum);
        if (count >= 10) {
            ironFistCounters.put(uuid, 0);
            return true;
        }
        return false;
    }

    public static void cleanup(UUID uuid) {
        ironFistCounters.remove(uuid);
    }
}
