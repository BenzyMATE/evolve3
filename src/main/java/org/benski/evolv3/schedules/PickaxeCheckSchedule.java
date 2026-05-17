package org.benski.evolv3.schedules;

import java.util.UUID;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseSchedule;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.PlayerExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Schedule that checks if players are holding registered pickaxes. Runs every X seconds to validate
 * pickaxe items. Interval is configurable in default.yml
 */
@ExtensionMethod({ItemStackExtensions.class, PlayerExtensions.class})
public class PickaxeCheckSchedule extends BaseSchedule {
  private final PickaxeDataCenter pickaxeDataCenter;

  public PickaxeCheckSchedule(JavaPlugin plugin) {
    super(plugin);
    this.pickaxeDataCenter = (PickaxeDataCenter) DataCenterRegistry.getInstance().get("pickaxe");
  }

  /**
   * {@inheritDoc} Default: 40 ticks (2 seconds). Matches the default.yml configured value.
   * BaseSchedule reads from plugin.getConfig() which doesn't contain this key, so the fallback here
   * must match the intended default.
   */
  @Override
  public long getIntervalTicks() {
    return 40;
  }

  /** {@inheritDoc} */
  @Override
  public String getIntervalConfigPath() {
    return ConfigPaths.Default.Schedules.PICKAXE_CHECK_INTERVAL;
  }

  /** {@inheritDoc} Starts after 1 second delay. */
  @Override
  public long getDelayTicks() {
    return 20;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      checkPlayerPickaxe(player);
      updatePlayerPickaxe(player);
    }
  }

  /**
   * Refreshes the lore on a player's held pickaxe. Skipped if the player broke a block within the
   * last 2 seconds to avoid sending an equipment update mid-swing and interrupting the mining
   * animation.
   *
   * @param player The player to update
   */
  public void updatePlayerPickaxe(Player player) {
    if (PickaxeManager.getInstance().isActiveMining(player.getUniqueId())) return;

    ItemStack item = player.getInventory().getItemInMainHand();
    String itemId = player.getItemId();
    if (itemId == null || !pickaxeDataCenter.exists(itemId)) return;

    PickaxeProfile pickaxeProfile = pickaxeDataCenter.getLocal(player);
    if (pickaxeProfile == null) return;

    item.updateLore(pickaxeProfile);
  }

  /**
   * Checks if a player is holding a pickaxe and if it's registered.
   *
   * @param player the player to check
   */
  private void checkPlayerPickaxe(Player player) {
    ItemStack mainHand = player.getInventory().getItemInMainHand();

    if (!isPickaxe(mainHand)) {
      return;
    }

    String pickaxeId = mainHand.getData("id");

    if (pickaxeId == null || pickaxeId.isEmpty()) {
      String newId = UUID.randomUUID().toString();
      mainHand.setData("id", newId);
      pickaxeDataCenter.loadOrCreate(player, newId);
      updatePlayerPickaxe(player);
      return;
    }

    pickaxeDataCenter.loadOrCreate(player, pickaxeId);
  }

  private boolean isPickaxe(ItemStack item) {
    return item != null && Utils.isPickaxe(item.getType());
  }

  /** {@inheritDoc} */
  @Override
  public void onStart() {
    plugin
        .getLogger()
        .info(
            () ->
                "Pickaxe check schedule started - checking every "
                    + (getActualInterval() / 20)
                    + " seconds");
  }

  /** {@inheritDoc} */
  @Override
  public void onStop() {
    plugin.getLogger().info("Pickaxe check schedule stopped");
  }
}
