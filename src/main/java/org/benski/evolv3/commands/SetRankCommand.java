package org.benski.evolv3.commands;

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
 * Admin command to set a player's prison rank directly.
 *
 * <p>Usage: {@code /setrank <player> <rank>}
 *
 * <p>Requires the {@code evolv3.rank.setrank} permission.
 */
public class SetRankCommand extends BaseCommand {
  public SetRankCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "setrank";
  }

  @Override
  public @Nullable String getDescription() {
    return "Set a player's prison rank";
  }

  @Override
  public @Nullable String getUsage() {
    return "/setrank <player> <rank>";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.rank.setrank";
  }

  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    if (!requireArgs(sender, args, 2, "/setrank <player> <rank>")) return true;

    Player admin = (Player) sender;
    Player target = Bukkit.getPlayer(args[0]);
    if (target == null) {
      sendError(admin, "Player '" + args[0] + "' is not online.");
      return true;
    }

    String rankId = args[1].toUpperCase();

    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
    if (!order.contains(rankId)) {
      sendError(admin, "Unknown rank '" + rankId + "'. Valid ranks: " + String.join(", ", order));
      return true;
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

    RankManager.getInstance().setRank(target, profile, rankId, admin);
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
      List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
      String current = args[1].toUpperCase();
      return order.stream().filter(r -> r.startsWith(current)).toList();
    }
    return List.of();
  }
}
