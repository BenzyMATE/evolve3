package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stone handler for the Thor enchantment.
 *
 * <p>Tracks the Epic (Mjolnir) counter — every 10th Thor proc fires a
 * mega-strike that hits a 3×3 column area instead of a single column.</p>
 */
public final class ThorStoneHandler
{
    /** Per-player proc counters for the Epic (Mjolnir) stone. */
    private static final Map<UUID, Integer> mjolnirCounters = new ConcurrentHashMap<>();

    private ThorStoneHandler() {}

    public static boolean hasStone(EnchantData enchantData, String rarity)
    {
        if (enchantData == null || enchantData.socketStones == null || rarity == null) return false;
        for (String s : enchantData.socketStones) {
            if (rarity.equals(s)) return true;
        }
        return false;
    }

    /**
     * Increments the Mjolnir proc counter for this player.
     *
     * @return {@code true} on every 10th call (resets counter to 0)
     */
    public static boolean incrementAndCheckMjolnir(UUID uuid)
    {
        int count = mjolnirCounters.merge(uuid, 1, Integer::sum);
        if (count >= 10) {
            mjolnirCounters.put(uuid, 0);
            return true;
        }
        return false;
    }

    public static void cleanup(UUID uuid)
    {
        mjolnirCounters.remove(uuid);
    }
}
