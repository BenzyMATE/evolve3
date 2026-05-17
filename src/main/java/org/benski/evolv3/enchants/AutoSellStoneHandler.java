package org.benski.evolv3.enchants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.Main;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles runtime effects for Evolution Stones socketed into the AutoSell enchant.
 *
 * <ul>
 *   <li><b>Common</b> (Commission) — +10% coin value per auto-sell; inline
 *   <li><b>Uncommon</b> (Rapid Sale) — +5% to proc chance; inline
 *   <li><b>Rare</b> (Side Income) — 10% of earnings also awarded as essence; inline
 *   <li><b>Epic</b> (Gold Rush) — Activates a 15s 1.5x coin multiplier on each proc
 *   <li><b>Legendary</b> (Full Liquidation) — Auto-sells when backpack fills regardless of proc
 *       chance; inline
 * </ul>
 */
public final class AutoSellStoneHandler {
  private static final AutoSellStoneHandler INSTANCE = new AutoSellStoneHandler();

  public static AutoSellStoneHandler getInstance() {
    return INSTANCE;
  }

  /** Duration of the Gold Rush multiplier in ticks (15 seconds). */
  private static final long GOLD_RUSH_TICKS = 300L;

  /** Coin multiplier applied during an active Gold Rush. */
  public static final double GOLD_RUSH_MULTIPLIER = 1.5;

  private final Map<UUID, BukkitTask> goldRushTasks = new ConcurrentHashMap<>();

  private AutoSellStoneHandler() {}

  /**
   * Activates (or refreshes) the Gold Rush multiplier for the given player. The multiplier lasts 15
   * seconds; calling this again resets the timer.
   *
   * @param uuid the player's UUID
   */
  public void activateGoldRush(UUID uuid) {
    BukkitTask existing = goldRushTasks.remove(uuid);
    if (existing != null) existing.cancel();

    BukkitTask task =
        Main.getInstance()
            .getServer()
            .getScheduler()
            .runTaskLater(Main.getInstance(), () -> goldRushTasks.remove(uuid), GOLD_RUSH_TICKS);
    goldRushTasks.put(uuid, task);
  }

  /**
   * Returns {@code true} if the player currently has an active Gold Rush multiplier.
   *
   * @param uuid the player's UUID
   */
  public boolean isGoldRushActive(UUID uuid) {
    return goldRushTasks.containsKey(uuid);
  }

  /** Clears all per-player state on quit to prevent memory leaks. */
  public void cleanup(UUID uuid) {
    BukkitTask task = goldRushTasks.remove(uuid);
    if (task != null) task.cancel();
  }

  public static boolean hasStone(EnchantData enchant, String rarity) {
    for (String stone : enchant.socketStones) {
      if (rarity.equals(stone)) return true;
    }
    return false;
  }
}
