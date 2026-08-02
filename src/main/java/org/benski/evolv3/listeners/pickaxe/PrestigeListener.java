package org.benski.evolv3.listeners.pickaxe;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.pickaxe.PrestigeSelectionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener that handles player interactions during the prestige ascension animation. Detects
 * left-clicks to confirm the highlighted stone choice, mirroring {@link EnchantListener}.
 */
public class PrestigeListener extends BaseListener {
  /** Creates and registers the PrestigeListener. @param plugin the owning plugin */
  public PrestigeListener(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    plugin.getLogger().info("Prestige Selection Listener initialized");
  }

  /**
   * Handles player interaction to confirm a prestige stone selection. Left-click (air or block)
   * confirms the currently highlighted stone.
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

    PrestigeSelectionManager manager = PrestigeSelectionManager.getInstance();
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
    plugin.getLogger().info("Prestige Selection Listener cleaned up");
  }
}
