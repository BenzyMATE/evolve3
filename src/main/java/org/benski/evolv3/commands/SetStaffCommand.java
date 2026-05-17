package org.benski.evolv3.commands;

import java.util.ArrayList;
import java.util.List;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.RankManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Admin command to assign or clear a player's staff rank.
 *
 * <p>Usage: {@code /setstaff <player> <rank|clear>}
 *
 * <p>Requires the {@code evolv3.rank.setstaff} permission.
 */
public class SetStaffCommand extends BaseCommand {
  public SetStaffCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "setstaff";
  }

  @Override
  public @Nullable String getDescription() {
    return "Set or clear a player's staff rank";
  }

  @Override
  public @Nullable String getUsage() {
    return "/setstaff <player> <rank|clear>";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.rank.setstaff";
  }

  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    if (!requireArgs(sender, args, 2, "/setstaff <player> <rank|clear>")) return true;

    Player admin = (Player) sender;
    Player target = Bukkit.getPlayer(args[0]);
    if (target == null) {
      sendError(admin, "Player '" + args[0] + "' is not online.");
      return true;
    }

    String rankArg = args[1].toLowerCase();
    String rankId;

    if (rankArg.equals("clear") || rankArg.equals("none") || rankArg.equals("remove")) {
      rankId = null;
    } else {
      FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
      List<String> staffOrder = ranksCfg.getStringList(ConfigPaths.Ranks.STAFF_ORDER);
      if (!staffOrder.contains(rankArg)) {
        sendError(
            admin,
            "Unknown staff rank '"
                + rankArg
                + "'. Valid staff ranks: "
                + String.join(", ", staffOrder)
                + " (or 'clear' to remove)");
        return true;
      }
      rankId = rankArg;
    }

    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) {
      sendError(admin, "Player data is not available.");
      return true;
    }

    PlayerProfile profile = dc.getLocal(target);
    if (profile == null) {
      sendError(admin, target.getName() + "'s profile is not loaded.");
      return true;
    }

    RankManager.getInstance().setStaffRank(target, profile, rankId, admin);
    return true;
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      String current = args[0].toLowerCase();
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase().startsWith(current))
          .toList();
    }
    if (args.length == 2) {
      FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
      List<String> staffOrder = ranksCfg.getStringList(ConfigPaths.Ranks.STAFF_ORDER);
      String current = args[1].toLowerCase();
      List<String> suggestions =
          new ArrayList<>(staffOrder.stream().filter(r -> r.startsWith(current)).toList());
      if ("clear".startsWith(current)) suggestions.add("clear");
      return suggestions;
    }
    return List.of();
  }
}
