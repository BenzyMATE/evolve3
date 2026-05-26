package org.benski.evolv3.enchants.passive;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Furnace enchant.
 *
 * <ul>
 *   <li><b>Common</b> (Hot Batch) — +20% chance smelted output is doubled; pending map consumed in
 *       BreakListener
 *   <li><b>Uncommon</b> (Forge XP) — smelting grants bonus pickaxe XP per proc; inline in
 *       onBlockBreak
 *   <li><b>Rare</b> (Alchemical Forge) — 20% chance to also apply Alchemy on the smelted result;
 *       inline in onBlockBreak
 *   <li><b>Epic</b> (Blast Furnace) — smelt all blocks in 3x3 around mined block; inline in
 *       onBlockBreak
 *   <li><b>Legendary</b> (Universal Smelt) — any block type can be smelted regardless of recipe
 *       list; inline in onBlockBreak
 * </ul>
 */
public final class FurnaceStoneHandler {
  /** Creates a new FurnaceStoneHandler instance. */
  private FurnaceStoneHandler() {}

  /** Common: pending double-smelt flag consumed in BreakListener at HIGHEST priority. */
  private static final Map<UUID, Boolean> pendingDoubleSmelt = new ConcurrentHashMap<>();

  /** Returns {@code true} if the given enchant has the specified rarity stone socketed. */
  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }

  /**
   * Sets the pending double-smelt flag for the given player; consumed at HIGHEST priority by
   * BreakListener.
   */
  public static void setDoubleSmelt(UUID uuid) {
    pendingDoubleSmelt.put(uuid, Boolean.TRUE);
  }

  /** Returns {@code true} and clears the flag if a double-smelt is pending for this player. */
  public static boolean popDoubleSmelt(UUID uuid) {
    return Boolean.TRUE.equals(pendingDoubleSmelt.remove(uuid));
  }

  /** Clears all per-player state for the given UUID. Called on player quit. */
  public static void cleanup(UUID uuid) {
    pendingDoubleSmelt.remove(uuid);
  }
}
