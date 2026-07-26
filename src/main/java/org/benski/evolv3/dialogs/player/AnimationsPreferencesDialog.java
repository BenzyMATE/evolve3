package org.benski.evolv3.dialogs.player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.PreferenceType.EnchantSpeed;
import org.benski.evolv3.core.types.PreferenceType.PickupEffect;
import org.benski.evolv3.dialogs.BasePlayerDialog;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DialogRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Form dialog for all animation-related preferences.
 *
 * <p>Radio inputs (Pickup Effect, Enchant Speed) are saved atomically when Save is clicked. FX
 * effect toggles are immediate-apply action buttons arranged in a 2-column grid: left column =
 * Atom, right column = Essence, 4 rows each. Clicking a toggle also persists the current radio
 * input state so no pending changes are lost.
 */
@SuppressWarnings("UnstableApiUsage")
public class AnimationsPreferencesDialog extends BasePlayerDialog {

  /** Pixel width for each half-width button (two per row in a standard form dialog). */
  private static final int HALF_WIDTH = 150;

  @Override
  public String getId() {
    return "preferences_animations";
  }

  @Override
  public Dialog build(Player player) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    PlayerProfile profile = getProfile(player);
    PlayerPreferences prefs = profile != null ? profile.preferences : new PlayerPreferences();

    ActionButton saveButton =
        ActionButton.builder(Component.text("Save").color(NamedTextColor.GREEN))
            .width(HALF_WIDTH)
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (!(audience instanceof Player p)) return;
                      PlayerProfile prof = getProfile(p);
                      if (prof == null) return;
                      PlayerPreferences pref = prof.preferences;

                      String pu = response.getText("pickupEffect");
                      if (pu != null) pref.pickupEffect = PickupEffect.valueOf(pu);
                      String sp = response.getText("enchantSpeed");
                      if (sp != null) pref.enchantAnimationSpeed = EnchantSpeed.valueOf(sp);

                      save(p, prof);
                      DialogRegistry.getInstance().open(p, "preferences");
                    },
                    SINGLE_USE))
            .build();

    ActionButton backButton =
        ActionButton.builder(
                fromLegacy(
                    cfg.getString(ConfigPaths.Menus.AnimationsMenu.BACK_NAME, "&#AAAAAA← Back")))
            .width(HALF_WIDTH)
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (audience instanceof Player p)
                        DialogRegistry.getInstance().open(p, "preferences");
                    },
                    SINGLE_USE))
            .build();

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(
                            fromLegacy(
                                cfg.getString(
                                    ConfigPaths.Menus.AnimationsMenu.TITLE,
                                    "&8P &7» &8Animations")))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text(
                                            "Configure animation and visual effect preferences.")
                                        .color(NamedTextColor.GRAY))))
                        .inputs(List.of(buildPickupInput(cfg, prefs), buildSpeedInput(cfg, prefs)))
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(
                    DialogType.multiAction(
                        List.of(
                            // Rows 1-4: [Atom | Essence] paired columns
                            toggle(
                                cfg,
                                prefs.atomSound,
                                ConfigPaths.Menus.AnimationsMenu.ATOM_SOUND_NAME,
                                "&#FFEE00⚛ Atom: Sound",
                                pref -> pref.atomSound = !pref.atomSound),
                            toggle(
                                cfg,
                                prefs.essenceSound,
                                ConfigPaths.Menus.AnimationsMenu.ESSENCE_SOUND_NAME,
                                "&#B366D9🔥 Essence: Sound",
                                pref -> pref.essenceSound = !pref.essenceSound),
                            toggle(
                                cfg,
                                prefs.atomParticles,
                                ConfigPaths.Menus.AnimationsMenu.ATOM_PARTICLES_NAME,
                                "&#FFEE00⚛ Atom: Particles",
                                pref -> pref.atomParticles = !pref.atomParticles),
                            toggle(
                                cfg,
                                prefs.essenceParticles,
                                ConfigPaths.Menus.AnimationsMenu.ESSENCE_PARTICLES_NAME,
                                "&#B366D9🔥 Essence: Particles",
                                pref -> pref.essenceParticles = !pref.essenceParticles),
                            toggle(
                                cfg,
                                prefs.atomHologram,
                                ConfigPaths.Menus.AnimationsMenu.ATOM_HOLOGRAM_NAME,
                                "&#FFEE00⚛ Atom: Hologram",
                                pref -> pref.atomHologram = !pref.atomHologram),
                            toggle(
                                cfg,
                                prefs.essenceHologram,
                                ConfigPaths.Menus.AnimationsMenu.ESSENCE_HOLOGRAM_NAME,
                                "&#B366D9🔥 Essence: Hologram",
                                pref -> pref.essenceHologram = !pref.essenceHologram),
                            toggle(
                                cfg,
                                prefs.atomGlow,
                                ConfigPaths.Menus.AnimationsMenu.ATOM_GLOW_NAME,
                                "&#FFEE00⚛ Atom: Glow",
                                pref -> pref.atomGlow = !pref.atomGlow),
                            toggle(
                                cfg,
                                prefs.essenceGlow,
                                ConfigPaths.Menus.AnimationsMenu.ESSENCE_GLOW_NAME,
                                "&#B366D9🔥 Essence: Glow",
                                pref -> pref.essenceGlow = !pref.essenceGlow),
                            // Row 5: [Save | Back]
                            saveButton,
                            backButton),
                        null,
                        2)));
  }

  /**
   * Builds an immediate-apply toggle button for a single boolean FX preference. Clicking the button
   * also persists the current radio input state so no pending pickup/speed changes are lost.
   */
  private ActionButton toggle(
      FileConfiguration cfg,
      boolean state,
      String configPath,
      String defaultLabel,
      Consumer<PlayerPreferences> toggler) {
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
                  PlayerPreferences pref = prof.preferences;

                  String pu = response.getText("pickupEffect");
                  if (pu != null) pref.pickupEffect = PickupEffect.valueOf(pu);
                  String sp = response.getText("enchantSpeed");
                  if (sp != null) pref.enchantAnimationSpeed = EnchantSpeed.valueOf(sp);

                  toggler.accept(pref);
                  save(p, prof);
                  DialogRegistry.getInstance().open(p, getId());
                },
                SINGLE_USE))
        .build();
  }

  private DialogInput buildPickupInput(FileConfiguration cfg, PlayerPreferences prefs) {
    return DialogInput.singleOption(
            "pickupEffect",
            fromLegacy(
                cfg.getString(
                    ConfigPaths.Menus.AnimationsMenu.PICKUP_LABEL_NAME, "&#00FFFF⬡ Pickup Effect")),
            List.of(
                OptionEntry.create(
                    PickupEffect.DEFAULT.name(),
                    fromLegacy(
                        cfg.getString(
                            ConfigPaths.Menus.AnimationsMenu.PICKUP_DEFAULT_NAME, "Default")),
                    prefs.pickupEffect == PickupEffect.DEFAULT),
                OptionEntry.create(
                    PickupEffect.SIMPLE.name(),
                    fromLegacy(
                        cfg.getString(
                            ConfigPaths.Menus.AnimationsMenu.PICKUP_SIMPLE_NAME, "Simple")),
                    prefs.pickupEffect == PickupEffect.SIMPLE),
                OptionEntry.create(
                    PickupEffect.DISABLED.name(),
                    fromLegacy(
                        cfg.getString(
                            ConfigPaths.Menus.AnimationsMenu.PICKUP_DISABLED_NAME, "Disabled")),
                    prefs.pickupEffect == PickupEffect.DISABLED)))
        .labelVisible(true)
        .build();
  }

  private DialogInput buildSpeedInput(FileConfiguration cfg, PlayerPreferences prefs) {
    return DialogInput.singleOption(
            "enchantSpeed",
            fromLegacy(
                cfg.getString(
                    ConfigPaths.Menus.AnimationsMenu.SPEED_LABEL_NAME, "&#FFEE00⏱ Enchant Speed")),
            List.of(
                OptionEntry.create(
                    EnchantSpeed.VERY_SLOW.name(),
                    Component.text(EnchantSpeed.VERY_SLOW.getDisplayName()),
                    prefs.enchantAnimationSpeed == EnchantSpeed.VERY_SLOW),
                OptionEntry.create(
                    EnchantSpeed.SLOW.name(),
                    Component.text(EnchantSpeed.SLOW.getDisplayName()),
                    prefs.enchantAnimationSpeed == EnchantSpeed.SLOW),
                OptionEntry.create(
                    EnchantSpeed.NORMAL.name(),
                    Component.text(EnchantSpeed.NORMAL.getDisplayName()),
                    prefs.enchantAnimationSpeed == EnchantSpeed.NORMAL),
                OptionEntry.create(
                    EnchantSpeed.FAST.name(),
                    Component.text(EnchantSpeed.FAST.getDisplayName()),
                    prefs.enchantAnimationSpeed == EnchantSpeed.FAST),
                OptionEntry.create(
                    EnchantSpeed.VERY_FAST.name(),
                    Component.text(EnchantSpeed.VERY_FAST.getDisplayName()),
                    prefs.enchantAnimationSpeed == EnchantSpeed.VERY_FAST)))
        .labelVisible(true)
        .build();
  }
}
