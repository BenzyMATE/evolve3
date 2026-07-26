package org.benski.evolv3.core.managers.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.benski.evolv3.dialogs.BasePlayerDialog;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stores and dispatches {@link BasePlayerDialog} instances keyed by their ID. Dialogs are
 * registered once at startup and shared across all players; per-player state is contained within
 * the {@link io.papermc.paper.dialog.Dialog} objects built on demand by each dialog's {@code
 * build()} method.
 */
public class DialogRegistryManager {

  private final JavaPlugin plugin;
  private final Map<String, BasePlayerDialog> dialogs = new HashMap<>();

  /** Creates the DialogRegistryManager. @param plugin the owning plugin */
  public DialogRegistryManager(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Registers a dialog.
   *
   * @param dialog the dialog to register
   */
  public void register(BasePlayerDialog dialog) {
    dialogs.put(dialog.getId(), dialog);
  }

  /**
   * Returns the dialog for the given ID, or {@code null} if not registered.
   *
   * @param id the dialog ID
   * @return the dialog, or null
   */
  public BasePlayerDialog get(String id) {
    return dialogs.get(id);
  }

  /**
   * Opens the dialog with the given ID for the player. Logs a warning if the ID is not registered.
   *
   * @param player the player
   * @param id the dialog ID
   */
  public void open(Player player, String id) {
    BasePlayerDialog dialog = dialogs.get(id);
    if (dialog != null) {
      dialog.open(player);
    } else {
      plugin.getLogger().warning("Attempted to open unregistered dialog: " + id);
    }
  }

  /** Returns all registered dialog instances. */
  public Collection<BasePlayerDialog> all() {
    return dialogs.values();
  }

  /** Clears all registered dialogs. */
  public void cleanup() {
    dialogs.clear();
  }
}
