package org.benski.evolv3.enchants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Fortune enchant. Stone effects are resolved inline
 * in {@link FortuneEnchant} and {@code BreakListener}.
 *
 * <ul>
 *   <li><b>Common</b> (Raised Cap) — +1 to drop multiplier at all levels; inline
 *   <li><b>Uncommon</b> (Stack) — every 20 Fortune procs grants +1 bonus block; counter here
 *   <li><b>Rare</b> (No Waste) — overflow blocks sell as coins; flag → BreakListener
 *   <li><b>Epic</b> (Wild Strike) — 10% chance to triple the multiplier; inline random
 *   <li><b>Legendary</b> (Atomic Fortune) — bonus blocks have 10% chance to become atoms; flag →
 *       BreakListener
 * </ul>
 */
public final class FortuneStoneHandler {
  private FortuneStoneHandler() {}

  /** Uncommon: Fortune procs since last +1 bonus block (resets every 20). */
  private static final Map<UUID, Integer> procCounters = new ConcurrentHashMap<>();

  private static final int STACK_INTERVAL = 20;

  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }

  /**
   * Increments the Fortune proc counter and returns {@code true} every 20th call, resetting the
   * counter. Called in {@link FortuneEnchant#onBlockBreak}.
   */
  public static boolean incrementAndCheckStack(UUID uuid) {
    int count = procCounters.merge(uuid, 1, Integer::sum);
    if (count >= STACK_INTERVAL) {
      procCounters.put(uuid, 0);
      return true;
    }
    return false;
  }

  public static void cleanup(UUID uuid) {
    procCounters.remove(uuid);
  }
}
