package org.benski.evolv3.dialogs.player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.dialogs.BasePlayerDialog;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DialogRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Lists every configurable enchant (excluding passive and preferences-hidden ones) as a button;
 * clicking one opens {@link EnchantOptionDialog} for its per-enchant Message/Title/Sound/Hologram/
 * Particles toggles.
 */
@SuppressWarnings("UnstableApiUsage")
public class EnchantsPreferencesDialog extends BasePlayerDialog {

  @Override
  public String getId() {
    return "preferences_enchants";
  }

  @Override
  public Dialog build(Player player) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

    List<String> enchants =
        EnchantAnimationConfig.getEnchantOrder().stream()
            .filter(
                name ->
                    !EnchantAnimationConfig.isPassive(name)
                        && !EnchantAnimationConfig.isPreferencesHidden(name))
            .toList();

    List<ActionButton> buttons = new ArrayList<>(enchants.size() + 1);
    for (String enchant : enchants) {
      buttons.add(
          ActionButton.builder(Component.text(enchant).color(NamedTextColor.WHITE))
              .action(
                  DialogAction.customClick(
                      (response, audience) -> {
                        if (!(audience instanceof Player p)) return;
                        BasePlayerDialog optionDialog =
                            DialogRegistry.getInstance().get("preferences_enchant_option");
                        if (optionDialog instanceof EnchantOptionDialog eod) {
                          eod.openForEnchant(p, enchant);
                        }
                      },
                      SINGLE_USE))
              .build());
    }

    ActionButton backButton =
        ActionButton.builder(
                fromLegacy(cfg.getString(ConfigPaths.Menus.EnchantsMenu.BACK_NAME, "&7← Back")))
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      if (audience instanceof Player p)
                        DialogRegistry.getInstance().open(p, "preferences");
                    },
                    SINGLE_USE))
            .build();
    buttons.add(backButton);

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(
                            fromLegacy(
                                cfg.getString(
                                    ConfigPaths.Menus.EnchantsMenu.TITLE, "&f&lEnchants")))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text(
                                            "Choose an enchant to configure its notifications.")
                                        .color(NamedTextColor.GRAY))))
                        .inputs(List.of())
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(buttons, null, 2)));
  }
}
