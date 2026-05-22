package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base class for all event listeners. Provides shared access to the {@link PlayerDataCenter} which
 * is the single source of truth for all player and pickaxe data.
 */
public abstract class BaseListener implements Listener {
  protected final JavaPlugin plugin;
  protected final PlayerDataCenter playerDataCenter;

  /**
   * @param plugin the plugin instance
   */
  public BaseListener(JavaPlugin plugin) {
    this.plugin = plugin;
    this.playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
  }

  /** Called when the listener is registered. Override to perform initialization logic. */
  public void initialize() {}

  /** Called when the plugin is being disabled. Override to perform cleanup logic. */
  public void cleanup() {}

  /** Returns the plugin instance. */
  public JavaPlugin getPlugin() {
    return plugin;
  }
}
