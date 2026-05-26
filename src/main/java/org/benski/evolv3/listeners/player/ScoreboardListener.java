package org.benski.evolv3.listeners.player;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.player.EvolveScoreboardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages scoreboard lifecycle: sets up the sidebar on join and tears it down on quit. Also
 * initialises {@link EvolveScoreboardManager} and starts its update task.
 */
public class ScoreboardListener extends BaseListener {
  private final EvolveScoreboardManager scoreboardManager = EvolveScoreboardManager.getInstance();

  /** Creates and registers the ScoreboardListener. @param plugin the owning plugin */
  public ScoreboardListener(JavaPlugin plugin) {
    super(plugin);
  }

  /** Initializes this component. */
  @Override
  public void initialize() {
    scoreboardManager.initialize(plugin);
  }

  /** Releases resources held by this component. */
  @Override
  public void cleanup() {
    scoreboardManager.cleanup();
  }

  /** Called when join occurs. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    plugin
        .getServer()
        .getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (event.getPlayer().isOnline()) {
                scoreboardManager.setup(event.getPlayer());
                scoreboardManager.update(event.getPlayer());
              }
            },
            1L);
  }

  /** Called when quit occurs. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    scoreboardManager.teardown(event.getPlayer());
  }
}
