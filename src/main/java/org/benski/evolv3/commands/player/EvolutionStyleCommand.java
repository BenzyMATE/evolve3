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
 * Command to switch the visual style of the pickaxe evolution animation.
 *
 * <p>Usage: - /evolutionstyle - Shows current style and available options - /evolutionstyle list -
 * Lists all available styles - /evolutionstyle set <style> - Sets the style - /evolutionstyle next
 * - Cycles to the other style
 */
public class EvolutionStyleCommand extends BaseCommand {
  private static final List<PreferenceType.EvolutionStyle> SELECTABLE_STYLES =
      List.of(
          PreferenceType.EvolutionStyle.CRYSTAL_ASCENSION,
          PreferenceType.EvolutionStyle.SUPERNOVA,
          PreferenceType.EvolutionStyle.GENESIS_HELIX,
          PreferenceType.EvolutionStyle.TITANS_FORGE,
          PreferenceType.EvolutionStyle.STARLIGHT_CORONATION,
          PreferenceType.EvolutionStyle.VOID_COLLAPSE,
          PreferenceType.EvolutionStyle.PHOENIX_REBIRTH,
          PreferenceType.EvolutionStyle.RUNIC_SIGIL,
          PreferenceType.EvolutionStyle.ELEMENTAL_CONVERGENCE,
          PreferenceType.EvolutionStyle.STORM_VORTEX,
          PreferenceType.EvolutionStyle.SLOT_MACHINE);

  /** Creates and registers the EvolutionStyleCommand. @param plugin the owning plugin */
  public EvolutionStyleCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public String getCommandName() {
    return "evolutionstyle";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getDescription() {
    return "Switch the pickaxe evolution animation style";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getUsage() {
    return "/evolutionstyle [list|set <style>|next]";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getPermission() {
    return "evolv3.command.evolutionstyle";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getAliases() {
    return List.of("evostyle");
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
                  ConfigPaths.Messages.EvolutionStyle.USAGE_SET,
                  "&cUsage: /evolutionstyle set"
                      + " <ascension|supernova|helix|forge|coronation|void|phoenix|runic|elemental|storm|slots>");
          player.sendMessage(formatMessage(player, usage));
          return true;
        }
        setStyle(player, messages, args[1]);
      }
      case "next", "cycle" -> cycleStyle(player, messages);
      default -> {
        PreferenceType.EvolutionStyle parsed = parseStyle(subCommand);
        if (parsed != null) {
          setStyle(player, messages, subCommand);
        } else {
          String unknown =
              messages.getString(
                  ConfigPaths.Messages.EvolutionStyle.UNKNOWN_SUBCOMMAND,
                  "&cUnknown option. Use: list, set <style>, or next");
          player.sendMessage(formatMessage(player, unknown));
        }
      }
    }

    return true;
  }

  /** Shows current style to the player. */
  private void showCurrentStyle(Player player, FileConfiguration messages) {
    PreferenceType.EvolutionStyle current = getPlayerStyle(player);

    String header =
        messages.getString(
            ConfigPaths.Messages.EvolutionStyle.CURRENT_HEADER, "&#FFD700&l[Evolution Style]");
    String currentLine =
        messages.getString(
            ConfigPaths.Messages.EvolutionStyle.CURRENT,
            "&#CCCCCCCurrent: &#FFD700%name% &#888888- &#CCCCCC%description%");
    currentLine =
        currentLine
            .replace("%name%", current.getDisplayName())
            .replace("%description%", current.getDescription());

    player.sendMessage(formatMessage(player, header));
    player.sendMessage(formatMessage(player, currentLine));

    String hint =
        messages.getString(
            ConfigPaths.Messages.EvolutionStyle.HINT,
            "&#CCCCCCUse &#FFD700/evolutionstyle list &#CCCCCCto see all options, or"
                + " &#FFD700/evolutionstyle next &#CCCCCCto cycle.");
    player.sendMessage(formatMessage(player, hint));
  }

  /** Shows style list to the player. */
  private void showStyleList(Player player, FileConfiguration messages) {
    String header =
        messages.getString(
            ConfigPaths.Messages.EvolutionStyle.LIST_HEADER,
            "&#FFD700&l[Available Evolution Styles]");
    player.sendMessage(formatMessage(player, header));

    PreferenceType.EvolutionStyle current = getPlayerStyle(player);

    for (PreferenceType.EvolutionStyle style : SELECTABLE_STYLES) {
      boolean isCurrent = style == current;

      String format;
      if (isCurrent) {
        format =
            messages.getString(
                ConfigPaths.Messages.EvolutionStyle.LIST_ITEM_SELECTED,
                " &#FFD700> &#FFFFFF%name% &#888888- &#CCCCCC%description% &#FFD700(selected)");
      } else {
        format =
            messages.getString(
                ConfigPaths.Messages.EvolutionStyle.LIST_ITEM,
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
            ConfigPaths.Messages.EvolutionStyle.LIST_FOOTER,
            "&#CCCCCCUse &#FFD700/evolutionstyle set <name> &#CCCCCCto select a style.");
    player.sendMessage(formatMessage(player, footer));
  }

  /** Sets the style. */
  private void setStyle(Player player, FileConfiguration messages, String styleName) {
    PreferenceType.EvolutionStyle style = parseStyle(styleName);
    if (style == null || !applyStyle(player, style)) {
      String error =
          messages.getString(
              ConfigPaths.Messages.EvolutionStyle.INVALID_STYLE,
              "&cInvalid style: %name%. Use /evolutionstyle list to see available options.");
      error = error.replace("%name%", styleName);
      player.sendMessage(formatMessage(player, error));
      return;
    }

    String success =
        messages.getString(
            ConfigPaths.Messages.EvolutionStyle.SET_SUCCESS,
            "&#FFD700Evolution style set to: &#FFFFFF%name%");
    success = success.replace("%name%", style.getDisplayName());
    player.sendMessage(formatMessage(player, success));
  }

  /** Cycle style. */
  private void cycleStyle(Player player, FileConfiguration messages) {
    PreferenceType.EvolutionStyle current = getPlayerStyle(player);
    int currentIndex = SELECTABLE_STYLES.indexOf(current);
    PreferenceType.EvolutionStyle next =
        SELECTABLE_STYLES.get((Math.max(currentIndex, 0) + 1) % SELECTABLE_STYLES.size());

    applyStyle(player, next);

    String cycled =
        messages.getString(
            ConfigPaths.Messages.EvolutionStyle.CYCLED,
            "&#FFD700Switched to: &#FFFFFF%name% &#888888- &#CCCCCC%description%");
    cycled =
        cycled
            .replace("%name%", next.getDisplayName())
            .replace("%description%", next.getDescription());

    player.sendMessage(formatMessage(player, cycled));
  }

  /** Returns the player's current evolution style preference, defaulting to the slot machine. */
  private PreferenceType.EvolutionStyle getPlayerStyle(Player player) {
    PlayerProfile profile = getProfile(player);
    return profile != null
        ? profile.preferences.evolutionStyle
        : PreferenceType.EvolutionStyle.SLOT_MACHINE;
  }

  /** Persists the given style to the player's profile. Returns {@code false} if no profile. */
  private boolean applyStyle(Player player, PreferenceType.EvolutionStyle style) {
    PlayerProfile profile = getProfile(player);
    if (profile == null) return false;
    profile.preferences.evolutionStyle = style;
    profile.save();
    return true;
  }

  /** Returns the player's profile, or {@code null} if unavailable. */
  private PlayerProfile getProfile(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    return pdc != null ? pdc.getLocal(player) : null;
  }

  /**
   * Parses a style name to one of the user-selectable {@link PreferenceType.EvolutionStyle} values.
   */
  private @Nullable PreferenceType.EvolutionStyle parseStyle(String name) {
    return switch (name.toLowerCase()) {
      case "ascension", "crystal", "classic" -> PreferenceType.EvolutionStyle.CRYSTAL_ASCENSION;
      case "supernova", "epic" -> PreferenceType.EvolutionStyle.SUPERNOVA;
      case "helix", "genesis" -> PreferenceType.EvolutionStyle.GENESIS_HELIX;
      case "forge", "titans" -> PreferenceType.EvolutionStyle.TITANS_FORGE;
      case "coronation", "starlight" -> PreferenceType.EvolutionStyle.STARLIGHT_CORONATION;
      case "void", "singularity" -> PreferenceType.EvolutionStyle.VOID_COLLAPSE;
      case "phoenix", "fire" -> PreferenceType.EvolutionStyle.PHOENIX_REBIRTH;
      case "runic", "sigil" -> PreferenceType.EvolutionStyle.RUNIC_SIGIL;
      case "elemental", "elements" -> PreferenceType.EvolutionStyle.ELEMENTAL_CONVERGENCE;
      case "storm", "vortex" -> PreferenceType.EvolutionStyle.STORM_VORTEX;
      case "slots", "slot", "jackpot", "reels" -> PreferenceType.EvolutionStyle.SLOT_MACHINE;
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
      List<String> options =
          new ArrayList<>(
              List.of(
                  "list",
                  "set",
                  "next",
                  "ascension",
                  "supernova",
                  "helix",
                  "forge",
                  "coronation",
                  "void",
                  "phoenix",
                  "runic",
                  "elemental",
                  "storm",
                  "slots"));
      String prefix = args[0].toLowerCase();
      completions.addAll(
          options.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList()));
    } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
      String prefix = args[1].toLowerCase();
      completions.addAll(
          List.of(
                  "ascension",
                  "supernova",
                  "helix",
                  "forge",
                  "coronation",
                  "void",
                  "phoenix",
                  "runic",
                  "elemental",
                  "storm",
                  "slots")
              .stream()
              .filter(s -> s.startsWith(prefix))
              .collect(Collectors.toList()));
    }

    return completions;
  }
}
