package org.benski.evolv3.enchants.mining;

import java.util.UUID;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Helper for Evolution Stones socketed into the Frostbite enchant.
 *
 * <ul>
 *   <li><b>Common</b> (Cold Snap) — +1 to freeze radius; inline in applyFreezingEffect
 *   <li><b>Uncommon</b> (Frost Vein) — +3% to Frostbite trigger chance; inline in onBlockBreak
 *   <li><b>Rare</b> (Ice Pick) — breaks a small radius of frozen blocks in one hit; inline in
 *       onFrozenBlockBreak
 *   <li><b>Epic</b> (Cryo Atom) — 5% chance to discover an atom when mining a frozen block; inline
 *       in onFrozenBlockBreak
 *   <li><b>Legendary</b> (Blizzard) — 10% chance on each Frostbite proc to instantly shatter all
 *       frozen blocks; inline in applyFreezingEffect
 * </ul>
 */
public final class FrostbiteStoneHandler {
  /** Creates a new FrostbiteStoneHandler instance. */
  private FrostbiteStoneHandler() {}

  /** Returns {@code true} if the given enchant has the specified rarity stone socketed. */
  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }

  /** Clears all per-player state for the given UUID. Called on player quit. */
  public static void cleanup(UUID uuid) {}
}
