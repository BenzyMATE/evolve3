package org.benski.evolv3.enchants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.models.pickaxe.EnchantData;

/**
 * Stone handler for the Merchant enchantment.
 *
 * <p>Tracks the Golden Window (Rare stone) — a 15-second window activated on each Merchant proc
 * that applies a 1.5× coin multiplier to all procs fired within it.
 */
public final class MerchantStoneHandler {
  /** Duration of the Golden Window in milliseconds. */
  private static final long GOLDEN_WINDOW_DURATION_MS = 15_000L;

  private static final Map<UUID, Long> goldenWindowExpiry = new ConcurrentHashMap<>();

  private MerchantStoneHandler() {}

  /** Returns true if the given enchant has the specified rarity stone socketed. */
  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }

  /** Returns true if the player's Golden Window is currently active. */
  public static boolean isGoldenWindowActive(UUID uuid) {
    Long expiry = goldenWindowExpiry.get(uuid);
    if (expiry == null) return false;
    if (System.currentTimeMillis() > expiry) {
      goldenWindowExpiry.remove(uuid);
      return false;
    }
    return true;
  }

  /** Starts or refreshes the Golden Window for the given player. */
  public static void activateGoldenWindow(UUID uuid) {
    goldenWindowExpiry.put(uuid, System.currentTimeMillis() + GOLDEN_WINDOW_DURATION_MS);
  }

  /** Removes all state for a player (call on quit). */
  public static void cleanup(UUID uuid) {
    goldenWindowExpiry.remove(uuid);
  }
}
