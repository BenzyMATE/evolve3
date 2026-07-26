package org.benski.evolv3.dialogs.player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.dialogs.BasePlayerDialog;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DialogRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Top-level preferences navigation dialog. Presents the four preference categories and routes
 * clicks to the appropriate sub-dialog or legacy menu.
 */
@SuppressWarnings("UnstableApiUsage")
public class PreferencesDialog extends BasePlayerDialog {

  @Override
  public String getId() {
    return "preferences";
  }

  @Override
  public Dialog build(Player player) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

    ActionButton animations =
        ActionButton.builder(
                fromLegacy(
                    cfg.getString(
                        ConfigPaths.Menus.PreferencesMenu.ANIMATIONS_NAME, "&#00FFFF⚡ Animations")))
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (audience instanceof Player p)
                        DialogRegistry.getInstance().open(p, "preferences_animations");
                    },
                    SINGLE_USE))
            .build();

    ActionButton enchants =
        ActionButton.builder(
                fromLegacy(
                    cfg.getString(
                        ConfigPaths.Menus.PreferencesMenu.ENCHANTS_NAME, "&#00FFFF✶ Enchants")))
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (audience instanceof Player p) {
                        var menu = MenuRegistry.getInstance().get("preferences_enchants");
                        if (menu != null) menu.openMenu(p);
                      }
                    },
                    SINGLE_USE))
            .build();

    ActionButton messages =
        ActionButton.builder(
                fromLegacy(
                    cfg.getString(
                        ConfigPaths.Menus.PreferencesMenu.MESSAGES_NAME, "&#888888✉ Messages")))
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (audience instanceof Player p)
                        p.sendMessage(
                            Component.text("Messages preferences — coming soon.")
                                .color(NamedTextColor.GRAY));
                    },
                    SINGLE_USE))
            .build();

    ActionButton experimental =
        ActionButton.builder(
                fromLegacy(
                    cfg.getString(
                        ConfigPaths.Menus.PreferencesMenu.EXPERIMENTAL_NAME,
                        "&#FF8800⚗ Experimental")))
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (audience instanceof Player p)
                        DialogRegistry.getInstance().open(p, "preferences_experimental");
                    },
                    SINGLE_USE))
            .build();

    ActionButton close =
        ActionButton.builder(
                fromLegacy(cfg.getString(ConfigPaths.Menus.PreferencesMenu.CLOSE_NAME, "&cClose")))
            .action(DialogAction.customClick((response, audience) -> {}, SINGLE_USE))
            .build();

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(
                            fromLegacy(
                                cfg.getString(
                                    ConfigPaths.Menus.PreferencesMenu.TITLE, "&8Preferences")))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text("Select a category to configure.")
                                        .color(NamedTextColor.GRAY))))
                        .inputs(List.of())
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(
                    DialogType.multiAction(
                        List.of(animations, enchants, messages, experimental, close), null, 2)));
  }
}
