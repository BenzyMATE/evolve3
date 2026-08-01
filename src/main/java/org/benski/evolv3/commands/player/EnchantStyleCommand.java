package org.benski.evolv3.commands.player;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Command to switch the visual style of the enchant-roll animation. Only {@code classic} and {@code
 * rift} are user-selectable — {@link PreferenceType.EnchantStyle#GHOST_WISPS} exists in the
 * preference enum for future use but has no animation implementation yet.
 *
 * <p>Usage: - /enchantstyle - Shows current style and available options - /enchantstyle list -
 * Lists all available styles - /enchantstyle set <style> - Sets the style - /enchantstyle next -
 * Cycles to the other style
 */
public class EnchantStyleCommand extends BaseCommand {
  private static final List<PreferenceType.EnchantStyle> SELECTABLE_STYLES =
      List.of(PreferenceType.EnchantStyle.CLASSIC, PreferenceType.EnchantStyle.PORTAL_RIFT);

  /** Creates and registers the EnchantStyleCommand. @param plugin the owning plugin */
  public EnchantStyleCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public String getCommandName() {
    return "enchantstyle";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getDescription() {
    return "Switch the enchant roll animation style";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getUsage() {
    return "/enchantstyle [list|set <style>|next]";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getPermission() {
    return "evolv3.command.enchantstyle";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getAliases() {
    return List.of("estyle");
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
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

    if (args.length == 0) {
      showCurrentStyle(player, messages);
      return true;
    }

    String subCommand = args[0].toLowerCase();

    switch (subCommand) {
      case "list" -> showStyleList(player, messages);
      case "set" -> {
        if (args.length < 2) {
          String usage =
              messages.getString(
                  ConfigPaths.Messages.EnchantStyle.USAGE_SET,
                  "&cUsage: /enchantstyle set <classic|rift>");
          player.sendMessage(formatMessage(player, usage));
          return true;
        }
        setStyle(player, messages, args[1]);
      }
      case "next", "cycle" -> cycleStyle(player, messages);
      default -> {
        PreferenceType.EnchantStyle parsed = parseStyle(subCommand);
        if (parsed != null) {
          setStyle(player, messages, subCommand);
        } else {
          String unknown =
              messages.getString(
                  ConfigPaths.Messages.EnchantStyle.UNKNOWN_SUBCOMMAND,
                  "&cUnknown option. Use: list, set <style>, or next");
          player.sendMessage(formatMessage(player, unknown));
        }
      }
    }

    return true;
  }

  /** Shows current style to the player. */
  private void showCurrentStyle(Player player, FileConfiguration messages) {
    PreferenceType.EnchantStyle current = getPlayerStyle(player);

    String header =
        messages.getString(
            ConfigPaths.Messages.EnchantStyle.CURRENT_HEADER, "&#B366D9&l[Enchant Style]");
    String currentLine =
        messages.getString(
            ConfigPaths.Messages.EnchantStyle.CURRENT,
            "&#CCCCCCCurrent: &#B366D9%name% &#888888- &#CCCCCC%description%");
    currentLine =
        currentLine
            .replace("%name%", current.getDisplayName())
            .replace("%description%", current.getDescription());

    player.sendMessage(formatMessage(player, header));
    player.sendMessage(formatMessage(player, currentLine));

    String hint =
        messages.getString(
            ConfigPaths.Messages.EnchantStyle.HINT,
            "&#CCCCCCUse &#B366D9/enchantstyle list &#CCCCCCto see all options, or"
                + " &#B366D9/enchantstyle next &#CCCCCCto cycle.");
    player.sendMessage(formatMessage(player, hint));
  }

  /** Shows style list to the player. */
  private void showStyleList(Player player, FileConfiguration messages) {
    String header =
        messages.getString(
            ConfigPaths.Messages.EnchantStyle.LIST_HEADER, "&#B366D9&l[Available Enchant Styles]");
    player.sendMessage(formatMessage(player, header));

    PreferenceType.EnchantStyle current = getPlayerStyle(player);

    for (PreferenceType.EnchantStyle style : SELECTABLE_STYLES) {
      boolean isCurrent = style == current;

      String format;
      if (isCurrent) {
        format =
            messages.getString(
                ConfigPaths.Messages.EnchantStyle.LIST_ITEM_SELECTED,
                " &#B366D9> &#FFFFFF%name% &#888888- &#CCCCCC%description% &#B366D9(selected)");
      } else {
        format =
            messages.getString(
                ConfigPaths.Messages.EnchantStyle.LIST_ITEM,
                " &#888888> &#CCCCCC%name% &#888888- &#CCCCCC%description%");
      }

      format =
          format
              .replace("%name%", style.getDisplayName())
              .replace("%description%", style.getDescription());

      player.sendMessage(formatMessage(player, format));
    }

    String footer =
        messages.getString(
            ConfigPaths.Messages.EnchantStyle.LIST_FOOTER,
            "&#CCCCCCUse &#B366D9/enchantstyle set <name> &#CCCCCCto select a style.");
    player.sendMessage(formatMessage(player, footer));
  }

  /** Sets the style. */
  private void setStyle(Player player, FileConfiguration messages, String styleName) {
    PreferenceType.EnchantStyle style = parseStyle(styleName);
    if (style == null || !applyStyle(player, style)) {
      String error =
          messages.getString(
              ConfigPaths.Messages.EnchantStyle.INVALID_STYLE,
              "&cInvalid style: %name%. Use /enchantstyle list to see available options.");
      error = error.replace("%name%", styleName);
      player.sendMessage(formatMessage(player, error));
      return;
    }

    String success =
        messages.getString(
            ConfigPaths.Messages.EnchantStyle.SET_SUCCESS,
            "&#B366D9Enchant style set to: &#FFFFFF%name%");
    success = success.replace("%name%", style.getDisplayName());
    player.sendMessage(formatMessage(player, success));
  }

  /** Cycle style. */
  private void cycleStyle(Player player, FileConfiguration messages) {
    PreferenceType.EnchantStyle current = getPlayerStyle(player);
    int currentIndex = SELECTABLE_STYLES.indexOf(current);
    PreferenceType.EnchantStyle next =
        SELECTABLE_STYLES.get((Math.max(currentIndex, 0) + 1) % SELECTABLE_STYLES.size());

    applyStyle(player, next);

    String cycled =
        messages.getString(
            ConfigPaths.Messages.EnchantStyle.CYCLED,
            "&#B366D9Switched to: &#FFFFFF%name% &#888888- &#CCCCCC%description%");
    cycled =
        cycled
            .replace("%name%", next.getDisplayName())
            .replace("%description%", next.getDescription());

    player.sendMessage(formatMessage(player, cycled));
  }

  /** Returns the player's current enchant style preference, defaulting to classic. */
  private PreferenceType.EnchantStyle getPlayerStyle(Player player) {
    PlayerProfile profile = getProfile(player);
    return profile != null ? profile.preferences.enchantStyle : PreferenceType.EnchantStyle.CLASSIC;
  }

  /** Persists the given style to the player's profile. Returns {@code false} if no profile. */
  private boolean applyStyle(Player player, PreferenceType.EnchantStyle style) {
    PlayerProfile profile = getProfile(player);
    if (profile == null) return false;
    profile.preferences.enchantStyle = style;
    profile.save();
    return true;
  }

  /** Returns the player's profile, or {@code null} if unavailable. */
  private PlayerProfile getProfile(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    return pdc != null ? pdc.getLocal(player) : null;
  }

  /**
   * Parses a style name to one of the user-selectable {@link PreferenceType.EnchantStyle} values.
   */
  private @Nullable PreferenceType.EnchantStyle parseStyle(String name) {
    return switch (name.toLowerCase()) {
      case "classic" -> PreferenceType.EnchantStyle.CLASSIC;
      case "rift", "portal", "portal_rift", "portalrift" -> PreferenceType.EnchantStyle.PORTAL_RIFT;
      default -> null;
    };
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
      List<String> options = new ArrayList<>(List.of("list", "set", "next", "classic", "rift"));
      String prefix = args[0].toLowerCase();
      completions.addAll(
          options.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList()));
    } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
      String prefix = args[1].toLowerCase();
      completions.addAll(
          List.of("classic", "rift").stream()
              .filter(s -> s.startsWith(prefix))
              .collect(Collectors.toList()));
    }

    return completions;
  }
}
