package org.benski.evolv3.enchants.mining;

import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Drill enchant. All stone effects are resolved
 * inline inside {@link DrillEnchant}.
 *
 * <ul>
 *   <li><b>Common</b> (Deep Reach) — +1 to tunnel depth; inline
 *   <li><b>Uncommon</b> (Broad Bore) — drill always mines 3×2 instead of 2×2; inline
 *   <li><b>Rare</b> (Tuned Head) — +5% to proc chance; inline
 *   <li><b>Epic</b> (Parallel Strike) — fires a second drill 3 blocks to the side; inline
 *   <li><b>Legendary</b> (Overcharge) — +5 to tunnel depth; inline
 * </ul>
 */
public final class DrillStoneHandler {
  /** Creates a new DrillStoneHandler instance. */
  private DrillStoneHandler() {}

  /** Returns {@code true} if the given enchant has the specified rarity stone socketed. */
  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }
}
