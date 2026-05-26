package org.benski.evolv3.enchants.mining;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Explosive enchant. All stone effects are resolved
 * inline inside {@link ExplosiveEnchant}.
 *
 * <ul>
 *   <li><b>Common</b> (Crater) — +1 to explosion radius; inline
 *   <li><b>Uncommon</b> (Instability) — +0.5% proc chance per missed block (max +15%), resets on
 *       proc; inline
 *   <li><b>Rare</b> (Cluster Bomb)— fires 2 additional smaller blasts nearby; inline
 *   <li><b>Epic</b> (Resonance) — each proc adds +1 to next explosion radius, stacks up to +3 then
 *       resets; inline
 *   <li><b>Legendary</b> (Supernova) — every 5th proc doubles the explosion radius; inline
 * </ul>
 */
public final class ExplosiveStoneHandler {
  /** Creates a new ExplosiveStoneHandler instance. */
  private ExplosiveStoneHandler() {}

  private static final Map<UUID, Integer> missCounters = new ConcurrentHashMap<>();
  private static final double MISS_BONUS_PER_BLOCK = 0.5;
  private static final double MISS_BONUS_CAP = 15.0;

  private static final Map<UUID, Integer> resonanceStack = new ConcurrentHashMap<>();
  private static final int RESONANCE_MAX = 3;

  private static final Map<UUID, Integer> procCounts = new ConcurrentHashMap<>();
  private static final int SUPERNOVA_INTERVAL = 5;

  /** Returns {@code true} if the given rarity stone is socketed in the enchant. */
  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }

  /** Returns the current miss-based bonus chance for the given player (0 → 15%). */
  public static double getMissBonus(UUID uuid) {
    return Math.min(missCounters.getOrDefault(uuid, 0) * MISS_BONUS_PER_BLOCK, MISS_BONUS_CAP);
  }

  /** Called when Explosive did NOT proc — increments the miss counter. */
  public static void incrementMiss(UUID uuid) {
    missCounters.merge(uuid, 1, Integer::sum);
  }

  /** Called when Explosive procs — resets the miss counter. */
  public static void resetMiss(UUID uuid) {
    missCounters.put(uuid, 0);
  }

  /**
   * Returns the current resonance radius bonus then advances the stack. Stack cycles 0 → 1 → 2 → 3
   * → 0.
   */
  public static int consumeResonance(UUID uuid) {
    int current = resonanceStack.getOrDefault(uuid, 0);
    resonanceStack.put(uuid, current >= RESONANCE_MAX ? 0 : current + 1);
    return current;
  }

  /** Returns true every {@value #SUPERNOVA_INTERVAL}th call (resets counter when triggered). */
  public static boolean incrementAndCheckSupernova(UUID uuid) {
    int count = procCounts.merge(uuid, 1, Integer::sum);
    if (count >= SUPERNOVA_INTERVAL) {
      procCounts.put(uuid, 0);
      return true;
    }
    return false;
  }

  /** Clears all per-player state for the given UUID. Called on player quit. */
  public static void cleanup(UUID uuid) {
    missCounters.remove(uuid);
    resonanceStack.remove(uuid);
    procCounts.remove(uuid);
  }
}
