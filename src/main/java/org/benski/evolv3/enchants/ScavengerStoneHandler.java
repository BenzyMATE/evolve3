package org.benski.evolv3.enchants;

import org.benski.evolv3.models.pickaxe.EnchantData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stone handler for the Scavenger enchantment.
 *
 * <p>Tracks the Epic (Double Haul) window — slot gains are doubled for 10 seconds
 * after each Scavenger proc.</p>
 */
public final class ScavengerStoneHandler
{
    private static final long DOUBLE_HAUL_DURATION_MS = 10_000L;

    /** Per-player expiry timestamps for the Epic (Double Haul) window. */
    private static final Map<UUID, Long> doubleHaulExpiry = new ConcurrentHashMap<>();

    private ScavengerStoneHandler() {}

    public static boolean hasStone(EnchantData enchantData, String rarity)
    {
        if (enchantData == null || enchantData.socketStones == null || rarity == null) return false;
        for (String s : enchantData.socketStones) {
            if (rarity.equals(s)) return true;
        }
        return false;
    }

    /** Returns {@code true} if the Double Haul window is currently active for this player. */
    public static boolean isDoubleHaulActive(UUID uuid)
    {
        Long expiry = doubleHaulExpiry.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /** Starts (or refreshes) the 10-second Double Haul window for this player. */
    public static void activateDoubleHaul(UUID uuid)
    {
        doubleHaulExpiry.put(uuid, System.currentTimeMillis() + DOUBLE_HAUL_DURATION_MS);
    }

    public static void cleanup(UUID uuid)
    {
        doubleHaulExpiry.remove(uuid);
    }
}
