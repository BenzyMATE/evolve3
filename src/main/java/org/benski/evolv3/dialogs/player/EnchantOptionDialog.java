package org.benski.evolv3.dialogs.player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.dialogs.BasePlayerDialog;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DialogRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Per-enchant preference sub-dialog. Shows 5 checkbox inputs (Message, Title, Sound, Hologram,
 * Particles) for one enchant, committed together by a single Save button — same
 * checkboxes-then-Save/Back pattern as {@link AnimationsPreferencesDialog} and {@link
 * ExperimentalPreferencesDialog}, rather than immediate-apply buttons. Immediate-apply toggles have
 * to close and re-show the whole dialog on every click (Paper's dialog API has no in-place update),
 * which was recentering the player's cursor each time — checkbox inputs update locally and only
 * trigger one dialog refresh, on Save.
 */
@SuppressWarnings("UnstableApiUsage")
public class EnchantOptionDialog extends BasePlayerDialog {

  /** Pixel width for each half-width button (two per row in a standard form dialog). */
  private static final int HALF_WIDTH = 150;

  /**
   * Per-player selected enchant display name. Dialog instances are long-lived singletons (see
   * {@link DialogRegistry}) and {@link DialogRegistry#open(Player, String)} takes no parameters, so
   * which enchant to show has to live here — same reason {@code EnchantOptionMenu} (its chest-GUI
   * predecessor) kept the same kind of map.
   */
  private final Map<UUID, String> selectedEnchant = new HashMap<>();

  @Override
  public String getId() {
    return "preferences_enchant_option";
  }

  /**
   * Opens this dialog configured for a specific enchant.
   *
   * @param player the player
   * @param enchantName the enchant display name (e.g. "Furnace")
   */
  public void openForEnchant(Player player, String enchantName) {
    selectedEnchant.put(player.getUniqueId(), enchantName);
    open(player);
  }

  @Override
  public Dialog build(Player player) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String enchantName = selectedEnchant.getOrDefault(player.getUniqueId(), "Enchant");

    PlayerProfile profile = getProfile(player);
    EnchantPreference pref =
        profile != null
            ? profile.preferences.getOrCreateEnchantPref(enchantName)
            : new EnchantPreference();

    DialogInput messageInput =
        checkbox(
            "message",
            cfg.getString(ConfigPaths.Menus.EnchantOptionMenu.MESSAGE_NAME, "Message"),
            pref.message);
    DialogInput titleInput =
        checkbox(
            "title",
            cfg.getString(ConfigPaths.Menus.EnchantOptionMenu.TITLE_NAME, "Title"),
            pref.title);
    DialogInput soundInput =
        checkbox(
            "sound",
            cfg.getString(ConfigPaths.Menus.EnchantOptionMenu.SOUND_NAME, "Sound"),
            pref.sound);
    DialogInput hologramInput =
        checkbox(
            "hologram",
            cfg.getString(ConfigPaths.Menus.EnchantOptionMenu.HOLOGRAM_NAME, "Hologram"),
            pref.hologram);
    DialogInput particlesInput =
        checkbox(
            "particles",
            cfg.getString(ConfigPaths.Menus.EnchantOptionMenu.PARTICLES_NAME, "Particles"),
            pref.particles);

    ActionButton saveButton =
        ActionButton.builder(Component.text("Save").color(NamedTextColor.GREEN))
            .width(HALF_WIDTH)
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (!(audience instanceof Player p)) return;
                      PlayerProfile prof = getProfile(p);
                      if (prof == null) return;

                      EnchantPreference toSave =
                          prof.preferences.getOrCreateEnchantPref(enchantName);
                      Boolean message = response.getBoolean("message");
                      if (message != null) toSave.message = message;
                      Boolean title = response.getBoolean("title");
                      if (title != null) toSave.title = title;
                      Boolean sound = response.getBoolean("sound");
                      if (sound != null) toSave.sound = sound;
                      Boolean hologram = response.getBoolean("hologram");
                      if (hologram != null) toSave.hologram = hologram;
                      Boolean particles = response.getBoolean("particles");
                      if (particles != null) toSave.particles = particles;

                      save(p, prof);
                      DialogRegistry.getInstance().open(p, "preferences_enchants");
                    },
                    SINGLE_USE))
            .build();

    ActionButton backButton =
        ActionButton.builder(
                fromLegacy(
                    cfg.getString(ConfigPaths.Menus.EnchantOptionMenu.BACK_NAME, "&7← Back")))
            .width(HALF_WIDTH)
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (audience instanceof Player p)
                        DialogRegistry.getInstance().open(p, "preferences_enchants");
                    },
                    SINGLE_USE))
            .build();

    String title =
        cfg.getString(ConfigPaths.Menus.EnchantOptionMenu.MENU_TITLE, "&f&l%enchant%")
            .replace("%enchant%", enchantName);

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(fromLegacy(title))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text("Choose which notifications to receive.")
                                        .color(NamedTextColor.GRAY))))
                        .inputs(
                            List.of(
                                messageInput,
                                titleInput,
                                soundInput,
                                hologramInput,
                                particlesInput))
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(saveButton, backButton), null, 2)));
  }

  /** Builds a checkbox input for one boolean enchant-notification field. */
  private DialogInput checkbox(String key, String label, boolean value) {
    return DialogInput.bool(key, fromLegacy(label), value, "Enabled", "Disabled");
  }
}
