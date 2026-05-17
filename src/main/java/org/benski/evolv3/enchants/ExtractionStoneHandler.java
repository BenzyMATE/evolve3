package org.benski.evolv3.enchants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Extraction enchant. All stone effects are resolved
 * inline inside {@link ExtractionEnchant}.
 *
 * <ul>
 *   <li><b>Common</b> (Harvest) — +15% to extraction bonus; inline
 *   <li><b>Uncommon</b> (Surge) — every 10th block, bonus is tripled; inline
 *   <li><b>Rare</b> (Lucid Shard) — essence discovered is doubled; via pendingEssenceMultiplier
 *   <li><b>Epic</b> (Surge Capacitor) — essence discovered is tripled; via pendingEssenceMultiplier
 *   <li><b>Legendary</b> (Fortune's Favour) — every 5th essence discovery awards 5× the amount;
 *       counter in BreakListener
 * </ul>
 */
public final class ExtractionStoneHandler {
  private ExtractionStoneHandler() {}

  /** Uncommon: blocks mined since last surge (resets every 10). */
  private static final Map<UUID, Integer> blockCounters = new ConcurrentHashMap<>();

  /** Legendary: essence discoveries since last 5× proc (resets every 5). */
  private static final Map<UUID, Integer> essenceCounters = new ConcurrentHashMap<>();

  private static final int SURGE_INTERVAL = 10;
  private static final int LEGENDARY_INTERVAL = 5;

  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }

  /**
   * Increments the block counter and returns {@code true} if this is the Nth block (triggering the
   * surge). Resets counter when triggered.
   */
  public static boolean incrementAndCheckSurge(UUID uuid) {
    int count = blockCounters.merge(uuid, 1, Integer::sum);
    if (count >= SURGE_INTERVAL) {
      blockCounters.put(uuid, 0);
      return true;
    }
    return false;
  }

  /**
   * Increments the essence discovery counter and returns {@code true} on every 5th hit, resetting
   * the counter. Called by BreakListener only when essence is actually awarded.
   */
  public static boolean incrementAndCheckLegendary(UUID uuid) {
    int count = essenceCounters.merge(uuid, 1, Integer::sum);
    if (count >= LEGENDARY_INTERVAL) {
      essenceCounters.put(uuid, 0);
      return true;
    }
    return false;
  }

  public static void cleanup(UUID uuid) {
    blockCounters.remove(uuid);
    essenceCounters.remove(uuid);
  }
}
