package org.benski.evolv3.commands.player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Showcases the Paper 26 Dialog API. Usage: /test [confirmation|notice|multi|form|item] */
@SuppressWarnings("UnstableApiUsage")
public class TestCommand extends BaseCommand {

  private static final ClickCallback.Options SINGLE_USE =
      ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();

  /** Creates and registers the TestCommand. @param plugin the owning plugin */
  public TestCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public String getCommandName() {
    return "test";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getDescription() {
    return "Showcase the Paper 26 Dialog API";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getUsage() {
    return "/test [confirmation|notice|multi|form|item]";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getPermission() {
    return "evolv3.admin";
  }

  /** {@inheritDoc} */
  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    Player player = (Player) sender;

    String type = args.length > 0 ? args[0].toLowerCase() : "notice";
    Dialog dialog =
        switch (type) {
          case "confirmation" -> buildConfirmation();
          case "multi" -> buildMultiAction();
          case "form" -> buildForm();
          case "item" -> buildItem(player);
          case "notice" -> buildNotice();
          default -> {
            sendError(sender, "Unknown type. Try: confirmation, notice, multi, form, item");
            yield null;
          }
        };

    if (dialog == null) return true;

    openDialog(player, dialog);
    return true;
  }

  @SuppressWarnings({"all"})
  private static void openDialog(Player player, Dialog dialog) {
    try {
      Method method = player.getClass().getMethod("showDialog", DialogLike.class);
      method.invoke(player, dialog);
    } catch (ReflectiveOperationException e) {
      player.sendMessage(Component.text("Dialog API unavailable.").color(NamedTextColor.RED));
    }
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      String input = args[0].toLowerCase();
      return List.of("confirmation", "notice", "multi", "form", "item").stream()
          .filter(s -> s.startsWith(input))
          .toList();
    }
    return List.of();
  }

  private static Dialog buildConfirmation() {
    ActionButton yes =
        ActionButton.builder(Component.text("Yes").color(NamedTextColor.GREEN))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("You confirmed!").color(NamedTextColor.GREEN)),
                    SINGLE_USE))
            .build();

    ActionButton no =
        ActionButton.builder(Component.text("No").color(NamedTextColor.RED))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("You declined.").color(NamedTextColor.RED)),
                    SINGLE_USE))
            .build();

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(Component.text("Confirmation Dialog"))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text("Are you sure you want to do this?"))))
                        .inputs(List.of())
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.confirmation(yes, no)));
  }

  private static Dialog buildNotice() {
    ActionButton dismiss =
        ActionButton.builder(Component.text("Got it!"))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("Dialog dismissed.").color(NamedTextColor.GRAY)),
                    SINGLE_USE))
            .build();

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(Component.text("Notice Dialog"))
                        .body(
                            List.of(
                                DialogBody.plainMessage(Component.text("This is a Notice dialog.")),
                                DialogBody.plainMessage(
                                    Component.text(
                                        "It shows information with a single dismiss button."))))
                        .inputs(List.of())
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.notice(dismiss)));
  }

  private static Dialog buildMultiAction() {
    ActionButton easy =
        ActionButton.builder(Component.text("Easy").color(NamedTextColor.GREEN))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("You chose: Easy").color(NamedTextColor.GREEN)),
                    SINGLE_USE))
            .build();

    ActionButton normal =
        ActionButton.builder(Component.text("Normal").color(NamedTextColor.YELLOW))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("You chose: Normal").color(NamedTextColor.YELLOW)),
                    SINGLE_USE))
            .build();

    ActionButton hard =
        ActionButton.builder(Component.text("Hard").color(NamedTextColor.RED))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("You chose: Hard").color(NamedTextColor.RED)),
                    SINGLE_USE))
            .build();

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(Component.text("Multi-Action Dialog"))
                        .body(
                            List.of(DialogBody.plainMessage(Component.text("Pick a difficulty:"))))
                        .inputs(List.of())
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(easy, normal, hard), null, 3)));
  }

  private static ItemStack buildDemoPickaxe() {
    ItemStack item = ItemStack.of(Material.DIAMOND_PICKAXE);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
        Component.text("Demo Pickaxe")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
    meta.lore(
        List.of(
            Component.text("This is example lore.")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Hold an item and try /test item!")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
    item.setItemMeta(meta);
    return item;
  }

  private static Dialog buildItem(Player player) {
    ItemStack held = player.getInventory().getItemInMainHand();
    final ItemStack item = held.getType().isAir() ? buildDemoPickaxe() : held;

    PlainMessageDialogBody description =
        DialogBody.plainMessage(
            Component.text("Hover the item to see its full tooltip.").color(NamedTextColor.GRAY));

    ActionButton close =
        ActionButton.builder(Component.text("Close").color(NamedTextColor.GRAY))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("Closed item dialog.").color(NamedTextColor.GRAY)),
                    SINGLE_USE))
            .build();

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(Component.text("Item Body Demo"))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text(
                                        "This dialog shows a Minecraft item — including its lore"
                                            + " and enchantments.")),
                                DialogBody.item(item)
                                    .description(description)
                                    .showDecorations(true)
                                    .showTooltip(true)
                                    .width(128)
                                    .height(128)
                                    .build()))
                        .inputs(List.of())
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.notice(close)));
  }

  private static Dialog buildForm() {
    ActionButton submit =
        ActionButton.builder(Component.text("Submit").color(NamedTextColor.GREEN))
            .action(
                DialogAction.customClick(
                    (response, audience) -> {
                      String name = response.getText("name");
                      Boolean notifs = response.getBoolean("notifications");
                      Float volume = response.getFloat("volume");
                      String theme = response.getText("theme");
                      int vol = volume != null ? Math.round(volume) : 50;
                      audience.sendMessage(
                          Component.text("Form result:\n")
                              .append(
                                  Component.text("  Name: " + name + "\n")
                                      .color(NamedTextColor.AQUA))
                              .append(
                                  Component.text("  Notifications: " + notifs + "\n")
                                      .color(NamedTextColor.YELLOW))
                              .append(
                                  Component.text("  Volume: " + vol + "%\n")
                                      .color(NamedTextColor.GREEN))
                              .append(
                                  Component.text("  Theme: " + theme)
                                      .color(NamedTextColor.LIGHT_PURPLE)));
                    },
                    SINGLE_USE))
            .build();

    ActionButton cancel =
        ActionButton.builder(Component.text("Cancel").color(NamedTextColor.RED))
            .action(
                DialogAction.customClick(
                    (response, audience) ->
                        audience.sendMessage(
                            Component.text("Cancelled.").color(NamedTextColor.GRAY)),
                    SINGLE_USE))
            .build();

    return Dialog.create(
        f ->
            f.empty()
                .base(
                    DialogBase.builder(Component.text("Form Dialog — All Input Types"))
                        .body(
                            List.of(
                                DialogBody.plainMessage(
                                    Component.text("Text  •  Checkbox  •  Slider  •  Radio"))))
                        .inputs(
                            List.of(
                                DialogInput.text("name", Component.text("Your Name"))
                                    .width(200)
                                    .labelVisible(true)
                                    .initial("")
                                    .maxLength(32)
                                    .build(),
                                DialogInput.bool(
                                    "notifications",
                                    Component.text("Enable Notifications"),
                                    true,
                                    "true",
                                    "false"),
                                DialogInput.numberRange(
                                        "volume", Component.text("Volume"), 0f, 100f)
                                    .width(200)
                                    .initial(50f)
                                    .labelFormat("%s: %s%%")
                                    .build(),
                                DialogInput.singleOption(
                                        "theme",
                                        Component.text("Theme"),
                                        List.of(
                                            OptionEntry.create(
                                                "light", Component.text("Light"), true),
                                            OptionEntry.create(
                                                "dark", Component.text("Dark"), false),
                                            OptionEntry.create(
                                                "system", Component.text("System"), false)))
                                    .width(200)
                                    .labelVisible(true)
                                    .build()))
                        .canCloseWithEscape(true)
                        .afterAction(DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(submit, cancel), null, 2)));
  }
}
