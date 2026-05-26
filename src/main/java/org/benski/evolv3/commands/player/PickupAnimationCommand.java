package org.benski.evolv3.commands.player;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.player.PickupAnimationManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Command to switch between different pickup animations. Allows players to test and select their
 * preferred animation style.
 *
 * <p>Usage: - /pickupanimation - Shows current animation and available options - /pickupanimation
 * list - Lists all available animations - /pickupanimation set <animation> - Sets the animation -
 * /pickupanimation next - Cycles to the next animation
 */
public class PickupAnimationCommand extends BaseCommand {
  /** Creates and registers the PickupAnimationCommand. @param plugin the owning plugin */
  public PickupAnimationCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public String getCommandName() {
    return "pickupanimation";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getDescription() {
    return "Switch between pickup animations";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getUsage() {
    return "/pickupanimation [list|set <animation>|next]";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getPermission() {
    return "evolv3.command.pickupanimation";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getAliases() {
    return List.of("pa", "pickup", "panim");
  }

  /** {@inheritDoc} */
  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) {
      return true;
    }

    Player player = (Player) sender;
    PickupAnimationManager manager = PickupAnimationManager.getInstance();
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

    if (args.length == 0) {
      showCurrentAnimation(player, manager, messages);
      return true;
    }

    String subCommand = args[0].toLowerCase();

    switch (subCommand) {
      case "list" -> showAnimationList(player, manager, messages);
      case "set" -> {
        if (args.length < 2) {
          String usage =
              messages.getString(
                  ConfigPaths.Messages.PickupAnimation.USAGE_SET,
                  "&cUsage: /pickupanimation set <animation>");
          player.sendMessage(formatMessage(player, usage));
          return true;
        }
        setAnimation(player, manager, messages, args[1]);
      }
      case "next", "cycle" -> cycleAnimation(player, manager, messages);
      case "others", "toggle" -> toggleOthersAnimations(player, manager, messages);
      default -> {
        if (manager.isValidAnimation(subCommand)) {
          setAnimation(player, manager, messages, subCommand);
        } else {
          String unknown =
              messages.getString(
                  ConfigPaths.Messages.PickupAnimation.UNKNOWN_SUBCOMMAND,
                  "&cUnknown option. Use: list, set <animation>, next, or others");
          player.sendMessage(formatMessage(player, unknown));
        }
      }
    }

    return true;
  }

  /** Shows current animation to the player. */
  private void showCurrentAnimation(
      Player player, PickupAnimationManager manager, FileConfiguration messages) {
    String currentId = manager.getPlayerAnimation(player);
    String[] info = manager.getAnimationInfo(currentId);

    String header =
        messages.getString(
            ConfigPaths.Messages.PickupAnimation.CURRENT_HEADER, "&#00FFFF&l[Pickup Animation]");
    String current =
        messages.getString(
            ConfigPaths.Messages.PickupAnimation.CURRENT,
            "&#CCCCCCCurrent: &#00FFFF%name% &#888888- &#CCCCCC%description%");

    current =
        current
            .replace("%name%", info != null ? info[0] : currentId)
            .replace("%description%", info != null ? info[1] : "Unknown");

    player.sendMessage(formatMessage(player, header));
    player.sendMessage(formatMessage(player, current));

    String hint =
        messages.getString(
            ConfigPaths.Messages.PickupAnimation.HINT,
            "&#CCCCCCUse &#00FFFF/pa list &#CCCCCCto see all options, or &#00FFFF/pa next &#CCCCCCto cycle.");
    player.sendMessage(formatMessage(player, hint));
  }

  /** Shows animation list to the player. */
  private void showAnimationList(
      Player player, PickupAnimationManager manager, FileConfiguration messages) {
    String header =
        messages.getString(
            ConfigPaths.Messages.PickupAnimation.LIST_HEADER,
            "&#00FFFF&l[Available Pickup Animations]");
    player.sendMessage(formatMessage(player, header));

    String currentId = manager.getPlayerAnimation(player);

    for (String id : manager.getAnimationIds()) {
      String[] info = manager.getAnimationInfo(id);
      boolean isCurrent = id.equalsIgnoreCase(currentId);

      String format;
      if (isCurrent) {
        format =
            messages.getString(
                ConfigPaths.Messages.PickupAnimation.LIST_ITEM_SELECTED,
                " &#00FFFF> &#FFFFFF%name% &#888888- &#CCCCCC%description% &#00FFFF(selected)");
      } else {
        format =
            messages.getString(
                ConfigPaths.Messages.PickupAnimation.LIST_ITEM,
                " &#888888> &#CCCCCC%name% &#888888- &#CCCCCC%description%");
      }

      format =
          format
              .replace("%name%", info != null ? info[0] : id)
              .replace("%id%", id)
              .replace("%description%", info != null ? info[1] : "Unknown");

      player.sendMessage(formatMessage(player, format));
    }

    String footer =
        messages.getString(
            ConfigPaths.Messages.PickupAnimation.LIST_FOOTER,
            "&#CCCCCCUse &#00FFFF/pa set <name> &#CCCCCCto select an animation.");
    player.sendMessage(formatMessage(player, footer));
  }

  /** Sets the animation. */
  private void setAnimation(
      Player player,
      PickupAnimationManager manager,
      FileConfiguration messages,
      String animationId) {
    if (manager.setPlayerAnimation(player, animationId)) {
      String[] info = manager.getAnimationInfo(animationId.toLowerCase());
      String success =
          messages.getString(
              ConfigPaths.Messages.PickupAnimation.SET_SUCCESS,
              "&#00FFFFPickup animation set to: &#FFFFFF%name%");
      success =
          success
              .replace("%name%", info != null ? info[0] : animationId)
              .replace("%id%", animationId);
      player.sendMessage(formatMessage(player, success));
    } else {
      String error =
          messages.getString(
              ConfigPaths.Messages.PickupAnimation.INVALID_ANIMATION,
              "&cInvalid animation: %name%. Use /pa list to see available options.");
      error = error.replace("%name%", animationId);
      player.sendMessage(formatMessage(player, error));
    }
  }

  /** Cycle animation. */
  private void cycleAnimation(
      Player player, PickupAnimationManager manager, FileConfiguration messages) {
    String nextId = manager.cycleNextAnimation(player);
    String[] info = manager.getAnimationInfo(nextId);

    String cycled =
        messages.getString(
            ConfigPaths.Messages.PickupAnimation.CYCLED,
            "&#00FFFFSwitched to: &#FFFFFF%name% &#888888- &#CCCCCC%description%");
    cycled =
        cycled
            .replace("%name%", info != null ? info[0] : nextId)
            .replace("%id%", nextId)
            .replace("%description%", info != null ? info[1] : "Unknown");

    player.sendMessage(formatMessage(player, cycled));
  }

  /** Toggle others animations. */
  private void toggleOthersAnimations(
      Player player, PickupAnimationManager manager, FileConfiguration messages) {
    boolean newState = manager.toggleShowOthersAnimations(player);

    String msgKey =
        newState
            ? ConfigPaths.Messages.PickupAnimation.OTHERS_ENABLED
            : ConfigPaths.Messages.PickupAnimation.OTHERS_DISABLED;
    String msg =
        messages.getString(
            msgKey,
            newState
                ? "&#00FFFFYou can now see other players' pickup animations."
                : "&#CCCCCCYou will no longer see other players' pickup animations.");

    player.sendMessage(formatMessage(player, msg));
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      List<String> options = new ArrayList<>(List.of("list", "set", "next", "others"));
      options.addAll(PickupAnimationManager.getInstance().getAnimationIds());

      String prefix = args[0].toLowerCase();
      completions.addAll(
          options.stream()
              .filter(s -> s.toLowerCase().startsWith(prefix))
              .collect(Collectors.toList()));
    } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
      String prefix = args[1].toLowerCase();
      completions.addAll(
          PickupAnimationManager.getInstance().getAnimationIds().stream()
              .filter(s -> s.startsWith(prefix))
              .collect(Collectors.toList()));
    }

    return completions;
  }
}
