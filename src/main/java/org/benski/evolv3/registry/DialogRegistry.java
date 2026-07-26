package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.registry.DialogRegistryManager;
import org.benski.evolv3.dialogs.BasePlayerDialog;
import org.benski.evolv3.dialogs.player.AnimationsPreferencesDialog;
import org.benski.evolv3.dialogs.player.ExperimentalPreferencesDialog;
import org.benski.evolv3.dialogs.player.PreferencesDialog;
import org.bukkit.entity.Player;

/** Registry for all player-facing Paper 26 dialogs. */
public class DialogRegistry implements Registry<BasePlayerDialog> {

  @Getter private static final DialogRegistry instance = new DialogRegistry();

  private final DialogRegistryManager manager = new DialogRegistryManager(Main.getInstance());

  private final List<BasePlayerDialog> dialogs =
      List.of(
          new PreferencesDialog(),
          new AnimationsPreferencesDialog(),
          new ExperimentalPreferencesDialog());

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    dialogs.forEach(manager::register);
    String ids =
        dialogs.stream()
            .map(BasePlayerDialog::getId)
            .reduce((a, b) -> a + ", " + b)
            .orElse("(none)");
    Main.getInstance().getLogger().info("Dialogs: " + ids);
  }

  /** {@inheritDoc} */
  @Override
  public BasePlayerDialog get(String id) {
    return manager.get(id);
  }

  /**
   * Opens the dialog with the given ID for the player.
   *
   * @param player the player
   * @param id the dialog ID
   */
  public void open(Player player, String id) {
    manager.open(player, id);
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    manager.cleanup();
  }
}
