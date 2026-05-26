package org.benski.evolv3.commands.admin;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.player.RankManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Admin command for managing permissions attached to ranks in-game.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>{@code /rankperm add <rank> <permission>} â€” add a permission to a rank
 *   <li>{@code /rankperm remove <rank> <permission>} â€” remove a permission from a rank
 *   <li>{@code /rankperm list <rank>} â€” list permissions for a rank
 * </ul>
 *
 * <p>Works for both prison ranks (A1â€“F4) and staff ranks (helper, moderator, admin, owner). The
 * rank type is auto-detected. Changes are saved to {@code ranks.yml} immediately and applied to all
 * online players of that rank without a reload.
 */
public class RankPermCommand extends BaseCommand {
  /** Creates and registers the RankPermCommand. @param plugin the owning plugin */
  public RankPermCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** Returns the command name. */
  @Override
  public String getCommandName() {
    return "rankperm";
  }

  @Override
  public @Nullable String getDescription() {
    return "Manage permissions for ranks in-game";
  }

  @Override
  public @Nullable String getUsage() {
    return "/rankperm <add|remove|list> <rank> [permission]";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.rank.perm";
  }

  /** Execute. */
  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (args.length < 2) {
      sendUsage(sender);
      return true;
    }

    String sub = args[0].toLowerCase();
    String rank = args[1];

    RankManager rm = RankManager.getInstance();

    boolean isPrison = rm.isPrisonRank(rank);
    boolean isStaff = !isPrison && rm.isStaffRank(rank.toLowerCase());

    if (!isPrison && !isStaff) {
      sendError(sender, "Unknown rank '" + rank + "'. Use a prison rank (A1â€“F4) or staff rank.");
      return true;
    }

    String rankId = isPrison ? rank.toUpperCase() : rank.toLowerCase();

    switch (sub) {
      case "add" -> handleAdd(sender, rm, rankId, !isPrison, args);
      case "remove", "rem" -> handleRemove(sender, rm, rankId, !isPrison, args);
      case "list" -> handleList(sender, rm, rankId, !isPrison);
      default -> sendUsage(sender);
    }

    return true;
  }

  /** Handles add. */
  private void handleAdd(
      CommandSender sender, RankManager rm, String rankId, boolean isStaff, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Â§cUsage: /rankperm add <rank> <permission>");
      return;
    }

    String permission = args[2];
    boolean added = rm.addPermission(rankId, isStaff, permission);

    if (added) {
      String type = isStaff ? "staff rank" : "rank";
      String prefix = isStaff ? rm.getStaffPrefix(rankId) : rm.getRankPrefix(rankId);
      String displayRank =
          LegacyComponentSerializer.legacySection()
              .serialize(
                  LegacyComponentSerializer.legacySection()
                      .deserialize(MatrixColorAPI.process(prefix)));
      sendSuccess(
          sender, "Added Â§f" + permission + " Â§ato " + type + " Â§f" + displayRank + "Â§a.");
    } else {
      sendError(sender, "Â§f" + permission + " Â§cis already on that rank.");
    }
  }

  /** Handles remove. */
  private void handleRemove(
      CommandSender sender, RankManager rm, String rankId, boolean isStaff, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Â§cUsage: /rankperm remove <rank> <permission>");
      return;
    }

    String permission = args[2];
    boolean removed = rm.removePermission(rankId, isStaff, permission);

    if (removed) {
      String type = isStaff ? "staff rank" : "rank";
      String prefix = isStaff ? rm.getStaffPrefix(rankId) : rm.getRankPrefix(rankId);
      String displayRank =
          LegacyComponentSerializer.legacySection()
              .serialize(
                  LegacyComponentSerializer.legacySection()
                      .deserialize(MatrixColorAPI.process(prefix)));
      sendSuccess(
          sender, "Removed Â§f" + permission + " Â§afrom " + type + " Â§f" + displayRank + "Â§a.");
    } else {
      sendError(sender, "Â§f" + permission + " Â§cwas not found on that rank.");
    }
  }

  /** Handles list. */
  private void handleList(CommandSender sender, RankManager rm, String rankId, boolean isStaff) {
    List<String> perms = rm.getPermissions(rankId, isStaff);

    String type = isStaff ? "staff rank" : "rank";
    String prefix = isStaff ? rm.getStaffPrefix(rankId) : rm.getRankPrefix(rankId);
    String displayRank =
        LegacyComponentSerializer.legacySection()
            .serialize(
                LegacyComponentSerializer.legacySection()
                    .deserialize(MatrixColorAPI.process(prefix)));

    sender.sendMessage(
        LegacyComponentSerializer.legacySection()
            .deserialize(
                MatrixColorAPI.process(
                    "&#888888Permissions for "
                        + type
                        + " "
                        + prefix
                        + " &#888888("
                        + perms.size()
                        + "):")));

    if (perms.isEmpty()) {
      sender.sendMessage("Â§7  (none)");
    } else {
      for (String perm : perms) {
        sender.sendMessage("Â§7  - Â§f" + perm);
      }
    }
  }

  /** Sends usage. */
  private void sendUsage(CommandSender sender) {
    sender.sendMessage("Â§cUsage: /rankperm <add|remove|list> <rank> [permission]");
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      return filterPrefix(List.of("add", "remove", "list"), args[0].toLowerCase());
    }

    if (args.length == 2) {
      FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
      List<String> allRanks = new ArrayList<>(cfg.getStringList(ConfigPaths.Ranks.ORDER));
      allRanks.addAll(cfg.getStringList(ConfigPaths.Ranks.STAFF_ORDER));
      return filterPrefix(allRanks, args[1]);
    }

    if (args.length == 3
        && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("rem"))) {
      String rank = args[1];
      RankManager rm = RankManager.getInstance();
      boolean isPrison = rm.isPrisonRank(rank);
      boolean isStaff = !isPrison && rm.isStaffRank(rank.toLowerCase());
      if (isPrison || isStaff) {
        String rankId = isPrison ? rank.toUpperCase() : rank.toLowerCase();
        return filterPrefix(rm.getPermissions(rankId, isStaff), args[2]);
      }
    }

    return List.of();
  }

  /** Filter prefix. */
  private static List<String> filterPrefix(List<String> options, String prefix) {
    String lc = prefix.toLowerCase();
    return options.stream().filter(o -> o.toLowerCase().startsWith(lc)).toList();
  }
}
