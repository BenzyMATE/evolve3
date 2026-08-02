package org.benski.evolv3.dialogs.player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
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
 * Per-enchant preference sub-dialog. Shows 5 immediate-apply toggles (Message, Title, Sound,
 * Hologram, Particles) for one enchant, mirroring {@link AnimationsPreferencesDialog}'s toggle
 * pattern.
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
                        .inputs(List.of())
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(
                    DialogType.multiAction(
                        List.of(
                            toggle(
                                cfg,
                                enchantName,
                                pref.message,
                                ConfigPaths.Menus.EnchantOptionMenu.MESSAGE_NAME,
                                "Message",
                                p -> p.message = !p.message),
                            toggle(
                                cfg,
                                enchantName,
                                pref.title,
                                ConfigPaths.Menus.EnchantOptionMenu.TITLE_NAME,
                                "Title",
                                p -> p.title = !p.title),
                            toggle(
                                cfg,
                                enchantName,
                                pref.sound,
                                ConfigPaths.Menus.EnchantOptionMenu.SOUND_NAME,
                                "Sound",
                                p -> p.sound = !p.sound),
                            toggle(
                                cfg,
                                enchantName,
                                pref.hologram,
                                ConfigPaths.Menus.EnchantOptionMenu.HOLOGRAM_NAME,
                                "Hologram",
                                p -> p.hologram = !p.hologram),
                            toggle(
                                cfg,
                                enchantName,
                                pref.particles,
                                ConfigPaths.Menus.EnchantOptionMenu.PARTICLES_NAME,
                                "Particles",
                                p -> p.particles = !p.particles),
                            backButton),
                        null,
                        2)));
  }

  /** Builds an immediate-apply toggle button for a single boolean enchant-notification field. */
  private ActionButton toggle(
      FileConfiguration cfg,
      String enchantName,
      boolean state,
      String configPath,
      String defaultLabel,
      Consumer<EnchantPreference> toggler) {
    Component stateIcon =
        state
            ? Component.text("✔ ").color(NamedTextColor.GREEN)
            : Component.text("✖ ").color(NamedTextColor.RED);
    Component label = stateIcon.append(fromLegacy(cfg.getString(configPath, defaultLabel)));

    return ActionButton.builder(label)
        .width(HALF_WIDTH)
        .action(
            DialogAction.customClick(
                (response, audience) -> {
                  if (!(audience instanceof Player p)) return;
                  PlayerProfile prof = getProfile(p);
                  if (prof == null) return;

                  toggler.accept(prof.preferences.getOrCreateEnchantPref(enchantName));
                  save(p, prof);
                  openForEnchant(p, enchantName);
                },
                SINGLE_USE))
        .build();
  }
}
