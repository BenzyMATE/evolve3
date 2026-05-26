package org.benski.evolv3.enchants.passive;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Stone handler for the Radioactive enchantment.
 *
 * <p>Tracks the Uncommon (Pinpoint) proc counter — every 5th active block break guarantees an atom
 * discovery for that player.
 */
public final class RadioactiveStoneHandler {
  /** Per-player block-break counter for the Uncommon (Pinpoint) stone. */
  private static final Map<UUID, Integer> pinpointCounters = new ConcurrentHashMap<>();

  /** Creates a new RadioactiveStoneHandler instance. */
  private RadioactiveStoneHandler() {}

  /** Returns {@code true} if the given enchant has the specified rarity stone socketed. */
  public static boolean hasStone(EnchantData enchantData, String rarity) {
    if (enchantData == null || enchantData.socketStones == null || rarity == null) return false;
    for (String s : enchantData.socketStones) {
      if (rarity.equals(s)) return true;
    }
    return false;
  }

  /**
   * Increments the Pinpoint counter for the player and returns {@code true} on every 5th increment,
   * resetting the counter.
   */
  public static boolean incrementAndCheckPinpoint(UUID uuid) {
    int count = pinpointCounters.merge(uuid, 1, Integer::sum);
    if (count >= 5) {
      pinpointCounters.put(uuid, 0);
      return true;
    }
    return false;
  }

  /** Clears all per-player state for the given UUID. Called on player quit. */
  public static void cleanup(UUID uuid) {
    pinpointCounters.remove(uuid);
  }
}
