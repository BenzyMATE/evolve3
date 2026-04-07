package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stone handler for the Vortex enchantment.
 *
 * <p>Tracks the Epic (Black Hole) counter — every 5th Vortex proc pulls all
 * item entities in a massive radius instead of just breaking blocks.</p>
 */
public final class VortexStoneHandler
{
    /** Per-player proc counters for the Epic (Black Hole) stone. */
    private static final Map<UUID, Integer> blackHoleCounters = new ConcurrentHashMap<>();

    private VortexStoneHandler() {}

    public static boolean hasStone(EnchantData enchantData, String rarity)
    {
        if (enchantData == null || enchantData.socketStones == null || rarity == null) return false;
        for (String s : enchantData.socketStones) {
            if (rarity.equals(s)) return true;
        }
        return false;
    }

    /**
     * Increments the Black Hole proc counter for this player.
     *
     * @return {@code true} on every 5th call (resets counter to 0)
     */
    public static boolean incrementAndCheckBlackHole(UUID uuid)
    {
        int count = blackHoleCounters.merge(uuid, 1, Integer::sum);
        if (count >= 5) {
            blackHoleCounters.put(uuid, 0);
            return true;
        }
        return false;
    }

    public static void cleanup(UUID uuid)
    {
        blackHoleCounters.remove(uuid);
    }
}
