package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener that handles player interactions during enchant selection animations. Detects
 * left-clicks to confirm the highlighted enchant choice.
 */
public class EnchantListener extends BaseListener {
  public EnchantListener(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    plugin.getLogger().info("Enchant Selection Listener initialized");
  }

  /**
   * Handles player interaction to confirm enchant selection. Left-click (air or block) confirms the
   * currently highlighted book.
   *
   * @param event the player interact event
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();

    Action action = event.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    EnchantSelectionManager manager = EnchantSelectionManager.getInstance();
    if (!manager.hasActiveSelection(player)) {
      return;
    }

    if (manager.confirmSelection(player)) {
      event.setCancelled(true);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    plugin.getLogger().info("Enchant Selection Listener cleaned up");
  }
}
