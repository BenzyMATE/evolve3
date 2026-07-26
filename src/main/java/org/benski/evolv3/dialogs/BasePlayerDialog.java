package org.benski.evolv3.dialogs;

import io.papermc.paper.dialog.Dialog;
import java.lang.reflect.Method;
import java.time.Duration;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Abstract base for all player-facing Paper 26 dialogs. Subclasses declare {@link #getId()} and
 * {@link #build(Player)} — everything else (opening, profile access, persistence) is handled here.
 */
@SuppressWarnings("UnstableApiUsage")
public abstract class BasePlayerDialog {

  private static final LegacyComponentSerializer LEGACY =
      LegacyComponentSerializer.legacyAmpersand();

  /** Single-use callback options — expires after one press or 10 minutes. */
  protected static final ClickCallback.Options SINGLE_USE =
      ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();

  /** Returns the unique registry ID for this dialog. */
  public abstract String getId();

  /**
   * Builds a fresh {@link Dialog} populated with this player's current state. Called every time
   * {@link #open(Player)} is invoked.
   *
   * @param player the player who will see the dialog
   * @return the constructed dialog
   */
  public abstract Dialog build(Player player);

  /**
   * Opens this dialog for the player.
   *
   * @param player the player
   */
  public final void open(Player player) {
    Dialog dialog = build(player);
    show(player, dialog);
  }

  /**
   * Shows a dialog to the player via reflection. {@code showDialog} lives on the runtime
   * CraftPlayer class, not the Bukkit {@code Player} interface.
   *
   * @param player the player
   * @param dialog the dialog to show
   */
  @SuppressWarnings("all")
  protected void show(Player player, Dialog dialog) {
    try {
      Method method = player.getClass().getMethod("showDialog", DialogLike.class);
      method.invoke(player, dialog);
    } catch (ReflectiveOperationException e) {
      player.sendMessage(
          Component.text("Dialogs require a 1.21.4+ client.").color(NamedTextColor.RED));
    }
  }

  /**
   * Returns the player's loaded profile, or {@code null} if unavailable.
   *
   * @param player the player
   * @return the profile, or null
   */
  protected PlayerProfile getProfile(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    return pdc != null ? pdc.getLocal(player) : null;
  }

  /**
   * Persists the profile and plays a confirmation sound.
   *
   * @param player the player
   * @param profile the profile to save
   */
  protected void save(Player player, PlayerProfile profile) {
    profile.save();
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
  }

  /**
   * Deserialises a legacy-formatted config string to an Adventure {@link Component} with italic
   * stripped.
   *
   * @param text the legacy-formatted string (ampersand codes)
   * @return the Adventure component
   */
  protected static Component fromLegacy(String text) {
    return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
  }
}
