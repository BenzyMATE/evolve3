package org.benski.evolv3.enchants.passive;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles runtime effects for Evolution Stones socketed into the Efficiency enchant.
 *
 * <ul>
 *   <li><b>Common</b> — Passive: +50% to base speed bonus (handled in {@link EfficiencyEnchant})
 *   <li><b>Uncommon</b> — 10-block streak → 3s speed burst (doubles speed temporarily)
 *   <li><b>Rare</b> — 5-block streak → 5s speed burst
 *   <li><b>Epic</b> — Atom pickup → 8s speed burst
 *   <li><b>Legendary</b> — Passive: 2x speed per level (handled in {@link EfficiencyEnchant})
 * </ul>
 */
public final class EfficiencyStoneHandler {
  private static final EfficiencyStoneHandler INSTANCE = new EfficiencyStoneHandler();

  /** Returns the singleton instance of EfficiencyStoneHandler. */
  public static EfficiencyStoneHandler getInstance() {
    return INSTANCE;
  }

  private static final String BURST_KEY = "evolv3_efficiency_burst";
  private static final long STREAK_RESET_MS = 3_000L;
  private static final int UNCOMMON_STREAK = 10;
  private static final long UNCOMMON_TICKS = 60L;
  private static final int RARE_STREAK = 5;
  private static final long RARE_TICKS = 100L;
  private static final long EPIC_TICKS = 160L;

  private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastBreakMs = new ConcurrentHashMap<>();
  private final Map<UUID, BukkitTask> activeBursts = new ConcurrentHashMap<>();

  /** Creates a new EfficiencyStoneHandler instance. */
  private EfficiencyStoneHandler() {}

  /**
   * Call on every block break. Advances the streak counter and triggers the Uncommon / Rare burst
   * when the threshold is reached.
   *
   * @param player the player who broke the block
   */
  public void onBlockBreak(Player player) {
    EnchantData efficiency = getEfficiency(player);
    if (efficiency == null || !efficiency.enabled) return;

    boolean hasUncommon = efficiency.hasStone("Uncommon");
    boolean hasRare = efficiency.hasStone("Rare");
    if (!hasUncommon && !hasRare) return;

    UUID uuid = player.getUniqueId();
    long now = System.currentTimeMillis();
    Long last = lastBreakMs.get(uuid);

    if (last != null && (now - last) > STREAK_RESET_MS) {
      streaks.remove(uuid);
    }
    lastBreakMs.put(uuid, now);

    int streak = streaks.merge(uuid, 1, Integer::sum);
    ItemStack held = player.getInventory().getItemInMainHand();

    if (hasRare && streak % RARE_STREAK == 0) {
      applyBurst(player, efficiency, held, RARE_TICKS);
    } else if (hasUncommon && streak % UNCOMMON_STREAK == 0) {
      applyBurst(player, efficiency, held, UNCOMMON_TICKS);
    }
  }

  /**
   * Call when the player picks up an atom. Triggers the Epic burst if socketed.
   *
   * @param player the player who received the atom
   */
  public void onAtomPickup(Player player) {
    EnchantData efficiency = getEfficiency(player);
    if (efficiency == null || !efficiency.enabled) return;
    if (!efficiency.hasStone("Epic")) return;

    applyBurst(player, efficiency, player.getInventory().getItemInMainHand(), EPIC_TICKS);
  }

  /** Clears all per-player state. Call on player quit to prevent memory leaks. */
  public void cleanup(UUID uuid) {
    BukkitTask task = activeBursts.remove(uuid);
    if (task != null) task.cancel();
    streaks.remove(uuid);
    lastBreakMs.remove(uuid);
  }

  /** Applies burst. */
  private void applyBurst(
      Player player, EnchantData efficiency, ItemStack held, long durationTicks) {
    UUID uuid = player.getUniqueId();

    BukkitTask existing = activeBursts.remove(uuid);
    if (existing != null) existing.cancel();

    ItemMeta meta = held.getItemMeta();
    if (meta == null) return;

    removeBurst(meta);

    double burstAmount = getBurstAmount(efficiency);
    meta.addAttributeModifier(
        Attribute.BLOCK_BREAK_SPEED,
        new AttributeModifier(
            Objects.requireNonNull(NamespacedKey.fromString(BURST_KEY)),
            burstAmount,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1,
            EquipmentSlotGroup.MAINHAND));
    held.setItemMeta(meta);

    BukkitTask task =
        Main.getInstance()
            .getServer()
            .getScheduler()
            .runTaskLater(
                Main.getInstance(),
                () -> {
                  activeBursts.remove(uuid);
                  if (!player.isOnline()) return;
                  ItemStack current = player.getInventory().getItemInMainHand();
                  ItemMeta m = current.getItemMeta();
                  if (m != null) {
                    removeBurst(m);
                    current.setItemMeta(m);
                  }
                },
                durationTicks);
    activeBursts.put(uuid, task);
  }

  /** Returns the burst amount. */
  private static double getBurstAmount(EnchantData efficiency) {
    double speedPerLevel =
        ConfigRegistry.getInstance()
            .get(ConfigNames.ENCHANTS)
            .getDouble(ConfigPaths.Enchants.Efficiency.SPEED_PER_LEVEL, 0.08);
    return efficiency.level * speedPerLevel;
  }

  /** Remove burst. */
  private static void removeBurst(ItemMeta meta) {
    if (!meta.hasAttributeModifiers()) return;
    var mods = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
    if (mods == null) return;
    for (AttributeModifier m : mods) {
      if (BURST_KEY.equals(m.getKey().getKey())) {
        meta.removeAttributeModifier(Attribute.BLOCK_BREAK_SPEED, m);
        return;
      }
    }
  }

  /** Returns the efficiency. */
  private static EnchantData getEfficiency(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return null;
    Pickaxe profile = pdc.getPickaxeForPlayer(player);
    if (profile == null) return null;
    return profile.enchants.getEnchant("efficiency");
  }
}
