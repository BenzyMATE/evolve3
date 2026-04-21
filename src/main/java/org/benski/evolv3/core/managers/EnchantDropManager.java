package org.benski.evolv3.core.managers;

import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared registry for the final drop material produced by enchant listeners.
 *
 * <p>Enchant listeners (Alchemy, Furnace) write their result here during the
 * BlockBreakEvent pipeline. BreakListener reads and clears the entry at
 * HIGHEST priority after all enchants have had a chance to run.</p>
 *
 * <p>Priority chain: Alchemy (NORMAL) sets result → Furnace (HIGH) may override
 * it → BreakListener (HIGHEST) consumes it.</p>
 */
public final class EnchantDropManager
{
    private static final Map<UUID, Material> pendingResults = new ConcurrentHashMap<>();

    private EnchantDropManager() {}

    /**
     * Sets the pending enchant-drop result for a player.
     * Furnace can call this after Alchemy to override/chain the result.
     *
     * @param playerUuid the player's UUID
     * @param material   the final drop material
     */
    public static void set(UUID playerUuid, Material material) {
        pendingResults.put(playerUuid, material);
    }

    /**
     * Returns the pending result without removing it.
     * Used by Furnace to read Alchemy's result for chaining.
     *
     * @param playerUuid the player's UUID
     * @return the pending material, or null if not set
     */
    public static Material get(UUID playerUuid) {
        return pendingResults.get(playerUuid);
    }

    /**
     * Returns and removes the pending result.
     * Called by BreakListener when resolving the final drop.
     *
     * @param playerUuid the player's UUID
     * @return the pending material, or null if not set
     */
    public static Material pop(UUID playerUuid) {
        return pendingResults.remove(playerUuid);
    }
}
