package org.benski.evolv3.dialogs.player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.dialogs.BasePlayerDialog;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DialogRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Form dialog for experimental preferences. */
@SuppressWarnings("UnstableApiUsage")
public class ExperimentalPreferencesDialog extends BasePlayerDialog {

  @Override
  public String getId() {
    return "preferences_experimental";
  }

  @Override
  public Dialog build(Player player) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    PlayerProfile profile = getProfile(player);
    PlayerPreferences prefs = profile != null ? profile.preferences : new PlayerPreferences();

    DialogInput pickaxeAnim =
        DialogInput.bool(
            "pickaxeMenuAnimation",
            fromLegacy(
                cfg.getString(
                    ConfigPaths.Menus.ExperimentalMenu.PICKAXE_ANIM_NAME,
                    "&#FF8800Pickaxe Menu Animation")),
            prefs.pickaxeMenuAnimation,
            "Enabled",
            "Disabled");

    ActionButton saveButton =
        ActionButton.builder(Component.text("Save").color(NamedTextColor.GREEN))
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (!(audience instanceof Player p)) return;
                      PlayerProfile prof = getProfile(p);
                      if (prof == null) return;

                      Boolean anim = response.getBoolean("pickaxeMenuAnimation");
                      if (anim != null) prof.preferences.pickaxeMenuAnimation = anim;

                      save(p, prof);
                      DialogRegistry.getInstance().open(p, "preferences");
                    },
                    SINGLE_USE))
            .build();

    ActionButton backButton =
        ActionButton.builder(
                fromLegacy(
                    cfg.getString(ConfigPaths.Menus.AnimationsMenu.BACK_NAME, "&#AAAAAA← Back")))
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
                                    ConfigPaths.Menus.ExperimentalMenu.TITLE,
                                    "&8P &7» &8Experimental")))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text(
                                            "Experimental features may change or be removed.")
                                        .color(NamedTextColor.YELLOW))))
                        .inputs(List.of(pickaxeAnim))
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(saveButton, backButton), null, 2)));
  }
}
