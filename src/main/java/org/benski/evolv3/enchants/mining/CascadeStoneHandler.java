package org.benski.evolv3.enchants.mining;

import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Cascade enchant. All stone effects are resolved
 * inline inside {@link CascadeEnchant}.
 *
 * <ul>
 *   <li><b>Common</b> (Extension) — +2 blocks added to cascade chain length; inline
 *   <li><b>Uncommon</b> (Depth Seeker) — Chain direction is biased downward; inline
 *   <li><b>Rare</b> (Aftershock) — Second half-length cascade fires from the last link; inline
 *   <li><b>Epic</b> (Time Warp) — All chain links break simultaneously in one tick; inline
 *   <li><b>Legendary</b> (Infinite Loop)— Chain length is tripled (capped at 60); inline
 * </ul>
 */
public final class CascadeStoneHandler {
  /** Creates a new CascadeStoneHandler instance. */
  private CascadeStoneHandler() {}

  /** Returns {@code true} if the given enchant has the specified rarity stone socketed. */
  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }
}
