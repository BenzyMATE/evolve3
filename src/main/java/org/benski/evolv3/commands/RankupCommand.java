package org.benski.evolv3.commands;

import java.util.List;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.managers.RankManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles the prison rank commands:
 *
 * <ul>
 *   <li>{@code /rankup} — rank up one step (player only)
 *   <li>{@code /setrank <player> <rank>} — set a player's rank (admin)
 *   <li>{@code /setstaff <player> <rank|clear>} — set/clear a player's staff rank (admin)
 * </ul>
 */
public class RankupCommand extends BaseCommand {
  public RankupCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "rankup";
  }

  @Override
  public @Nullable String getDescription() {
    return "Rank up to the next prison block";
  }

  @Override
  public @Nullable String getUsage() {
    return "/rankup";
  }

  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    Player player = (Player) sender;

    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) {
      sendError(sender, "Player data is not available. Please try again.");
      return true;
    }

    PlayerProfile profile = dc.getLocal(player);
    if (profile == null) {
      sendError(sender, "Your profile is still loading. Please wait a moment.");
      return true;
    }

    RankManager.getInstance().rankUp(player, profile);
    return true;
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    return List.of();
  }
}
