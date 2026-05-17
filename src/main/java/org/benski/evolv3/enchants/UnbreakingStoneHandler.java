package org.benski.evolv3.enchants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Handles runtime effects for Evolution Stones socketed into the Unbreaking enchant.
 *
 * <ul>
 *   <li><b>Common</b> — Passive: +5% negate chance (applied directly in BreakListener)
 *   <li><b>Uncommon</b> — 20% chance on negate to grant 1–10 pickaxe XP
 *   <li><b>Rare</b> — Passive: second 50% negate roll if first fails (2× slower drain)
 *   <li><b>Epic</b> — Every 50 negate procs fully restores durability
 *   <li><b>Legendary</b> — Passive: pickaxe never takes durability damage (applied in
 *       BreakListener)
 * </ul>
 */
public final class UnbreakingStoneHandler {
  private static final UnbreakingStoneHandler INSTANCE = new UnbreakingStoneHandler();

  public static UnbreakingStoneHandler getInstance() {
    return INSTANCE;
  }

  private static final int EPIC_THRESHOLD = 50;
  private static final double UNCOMMON_XP_CHANCE = 0.50;

  private final Map<UUID, Integer> negateCounts = new ConcurrentHashMap<>();

  private UnbreakingStoneHandler() {}

  /**
   * Call whenever Unbreaking successfully negates a durability hit. Handles Uncommon XP chance and
   * Epic durability restore counter.
   *
   * @param player the player whose pickaxe negated damage
   * @param unbreaking the Unbreaking EnchantData
   * @param item the pickaxe item
   */
  public void onNegate(Player player, EnchantData unbreaking, ItemStack item) {
    if (unbreaking.hasStone("Uncommon")
        && ThreadLocalRandom.current().nextDouble() < UNCOMMON_XP_CHANCE) {
      int xp = ThreadLocalRandom.current().nextInt(1, 11);
      PickaxeManager.getInstance().addXp(player, xp);
    }

    if (unbreaking.hasStone("Epic")) {
      int count = negateCounts.merge(player.getUniqueId(), 1, Integer::sum);
      if (count >= EPIC_THRESHOLD) {
        negateCounts.put(player.getUniqueId(), 0);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
          damageable.setDamage(0);
          item.setItemMeta(meta);
        }
      }
    }
  }

  /** Clears per-player state on quit to prevent memory leaks. */
  public void cleanup(UUID uuid) {
    negateCounts.remove(uuid);
  }
}
