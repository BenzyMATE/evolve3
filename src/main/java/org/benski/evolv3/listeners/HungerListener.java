package org.benski.evolv3.listeners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Custom hunger system.
 *
 * <ul>
 *   <li>Vanilla hunger drain and food restore are fully cancelled.
 *   <li>Hunger only decreases while a player is actively mining blocks inside a mine region — one
 *       point is deducted every {@code Hunger.BlocksPerDrain} blocks broken (default 50).
 *   <li>Food items consumed from the hand restore hunger via {@link PlayerItemConsumeEvent} — the
 *       item is removed from the inventory and hunger is restored according to {@code
 *       Hunger.FoodRestore} config.
 *   <li>Food items in the backpack are consumed via {@link org.benski.evolv3.menus.BackpackMenu}
 *       right-click handling.
 * </ul>
 */
public class HungerListener extends BaseListener {
  /** Blocks broken since last hunger drain, per player UUID. */
  private final Map<UUID, Integer> blockCounter = new ConcurrentHashMap<>();

  public HungerListener(JavaPlugin plugin) {
    super(plugin);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void onFoodLevelChange(FoodLevelChangeEvent event) {
    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();

    if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) {
      return;
    }

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int threshold = config.getInt(ConfigPaths.Default.Hunger.BLOCKS_PER_DRAIN, 50);

    UUID uuid = player.getUniqueId();
    int count = blockCounter.merge(uuid, 1, Integer::sum);

    if (count >= threshold) {
      blockCounter.put(uuid, 0);
      int current = player.getFoodLevel();
      if (current > 0) {
        player.setFoodLevel(Math.max(0, current - 1));
        player.setSaturation(0f);
      }
    }
  }

  /**
   * Intercepts vanilla eating. Cancels it, removes the item from inventory, and applies the
   * configured hunger restore amount instead.
   */
  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onItemConsume(PlayerItemConsumeEvent event) {
    if (!event.getItem().getType().isEdible()) return;

    event.setCancelled(true);

    Player player = event.getPlayer();
    org.bukkit.Material food = event.getItem().getType();

    org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getAmount() > 1) {
      held.setAmount(held.getAmount() - 1);
    } else {
      player.getInventory().setItemInMainHand(null);
    }

    applyFoodRestore(player, food);
  }

  /**
   * Restores hunger for the given food material. Reads restore amount from {@code
   * Hunger.FoodRestore.MATERIAL_NAME} (fallback: {@code Hunger.FoodRestore.DEFAULT}). Sends a
   * configurable consume message.
   *
   * @param player the player eating
   * @param food the food material consumed
   */
  public static void applyFoodRestore(Player player, org.bukkit.Material food) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

    int specific =
        config.getInt(ConfigPaths.Default.Hunger.FOOD_RESTORE_PREFIX + "." + food.name(), -1);
    int restore =
        specific >= 0
            ? specific
            : config.getInt(ConfigPaths.Default.Hunger.FOOD_RESTORE_PREFIX + ".DEFAULT", 2);

    int current = player.getFoodLevel();
    player.setFoodLevel(Math.min(20, current + restore));

    String msg =
        messages.getString(
            ConfigPaths.Messages.Hunger.CONSUME_MESSAGE,
            "%evolv3_prefix% &#FF8C00Consumed &#FFFFFF%food_name% &#FF8C00(&#FFFFFF+%hunger% &#FF8C00hunger)");
    msg =
        msg.replace("%food_name%", Utils.formatMaterialName(food))
            .replace("%hunger%", String.valueOf(restore));
    player.sendMessage(Utils.formatMessage(player, msg));

    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_BURP, 0.5f, 1.0f);
  }

  @Override
  public void cleanup() {
    blockCounter.clear();
  }
}
