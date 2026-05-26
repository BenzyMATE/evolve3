package org.benski.evolv3.enchants.mining;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Stone handler for the Vortex enchantment.
 *
 * <p>Tracks the Epic (Black Hole) counter — every 5th Vortex proc pulls all item entities in a
 * massive radius instead of just breaking blocks.
 */
public final class VortexStoneHandler {
  /** Per-player proc counters for the Epic (Black Hole) stone. */
  private static final Map<UUID, Integer> blackHoleCounters = new ConcurrentHashMap<>();

  /** Creates a new VortexStoneHandler instance. */
  private VortexStoneHandler() {}

  /** Returns {@code true} if the given enchant has the specified rarity stone socketed. */
  public static boolean hasStone(EnchantData enchantData, String rarity) {
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
  public static boolean incrementAndCheckBlackHole(UUID uuid) {
    int count = blackHoleCounters.merge(uuid, 1, Integer::sum);
    if (count >= 5) {
      blackHoleCounters.put(uuid, 0);
      return true;
    }
    return false;
  }

  /** Clears all per-player state for the given UUID. Called on player quit. */
  public static void cleanup(UUID uuid) {
    blackHoleCounters.remove(uuid);
  }
}
