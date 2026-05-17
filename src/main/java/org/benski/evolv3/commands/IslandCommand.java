package org.benski.evolv3.commands;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.IslandManager;
import org.benski.evolv3.core.managers.IslandManager.IslandData;
import org.benski.evolv3.core.managers.IslandWorldManager;
import org.benski.evolv3.menus.IslandMenu;
import org.benski.evolv3.menus.IslandsMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Player command for island management.
 *
 * <ul>
 *   <li>{@code /island} — open island management menu
 *   <li>{@code /island tp} — teleport to own island
 *   <li>{@code /island lock} — toggle island lock
 *   <li>{@code /island visits} — toggle open for visits
 *   <li>{@code /island member add <name>} — add member
 *   <li>{@code /island member remove <name>} — remove member
 *   <li>{@code /islands} — browse open islands
 * </ul>
 */
public class IslandCommand extends BaseCommand {
  public IslandCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "island";
  }

  @Override
  public @Nullable String getDescription() {
    return "Manage your island";
  }

  @Override
  public @Nullable List<String> getAliases() {
    return List.of("islands");
  }

  @Override
  public @Nullable String getPermission() {
    return null;
  }

  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    Player player = (Player) sender;

    if (label.equalsIgnoreCase("islands")
        || (args.length > 0 && args[0].equalsIgnoreCase("browse"))) {
      IslandsMenu.getInstance().openMenu(player);
      return true;
    }

    if (args.length == 0) {
      handleOpenMenu(player);
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "tp" -> handleTp(player);
      case "lock" -> handleLock(player);
      case "visits" -> handleVisits(player);
      case "member" -> handleMember(player, args);
      default -> handleOpenMenu(player);
    }
    return true;
  }

  private void handleOpenMenu(Player player) {
    if (!IslandManager.getInstance().hasIsland(player.getUniqueId())) {
      sendError(player, "You don't have an island yet. Evolve your cell to earn one!");
      return;
    }
    IslandMenu.getInstance().openMenu(player);
  }

  private void handleTp(Player player) {
    IslandData island = IslandManager.getInstance().get(player.getUniqueId());
    if (island == null) {
      sendError(player, "You don't have an island.");
      return;
    }
    Location loc = IslandWorldManager.getInstance().getSpawnLocation(island);
    if (loc == null) {
      sendError(player, "Island world is not loaded.");
      return;
    }
    player.teleport(loc);
    IslandWorldManager.getInstance().setPlayerBorder(player, island);
    Messageable.sendSuccess(player, "Teleported to your island.");
  }

  private void handleLock(Player player) {
    IslandData island = IslandManager.getInstance().get(player.getUniqueId());
    if (island == null) {
      sendError(player, "You don't have an island.");
      return;
    }
    IslandData updated = island.withLocked(!island.locked());
    IslandManager.getInstance().update(updated);
    Messageable.sendSuccess(
        player,
        "Island " + (updated.locked() ? "&#FF5555locked" : "&#55FF55unlocked") + "&#00FFFF.");
  }

  private void handleVisits(Player player) {
    IslandData island = IslandManager.getInstance().get(player.getUniqueId());
    if (island == null) {
      sendError(player, "You don't have an island.");
      return;
    }
    IslandData updated = island.withOpenForVisits(!island.openForVisits());
    IslandManager.getInstance().update(updated);
    Messageable.sendSuccess(
        player,
        "Island is now "
            + (updated.openForVisits() ? "&#55FF55open for visits" : "&#888888closed to visitors")
            + "&#00FFFF.");
  }

  private void handleMember(Player player, String[] args) {
    IslandData island = IslandManager.getInstance().get(player.getUniqueId());
    if (island == null) {
      sendError(player, "You don't have an island.");
      return;
    }
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/island member <add|remove> <player>");
      return;
    }
    String action = args[1].toLowerCase(Locale.ROOT);
    Player target = Bukkit.getPlayer(args[2]);
    if (target == null) {
      sendError(player, "Player not found or offline.");
      return;
    }

    java.util.List<java.util.UUID> members = new java.util.ArrayList<>(island.members());
    if (action.equals("add")) {
      if (members.contains(target.getUniqueId())) {
        sendError(player, "&#FFFFFF" + target.getName() + " &cis already a member.");
        return;
      }
      members.add(target.getUniqueId());
      IslandManager.getInstance().update(island.withMembers(members));
      Messageable.sendSuccess(
          player, "&#FFFFFF" + target.getName() + " &#00FFFFadded to your island.");
      Messageable.sendSuccess(
          target, "You were added to &#FFFFFF" + player.getName() + "&#00FFFF's island.");
    } else if (action.equals("remove")) {
      if (!members.remove(target.getUniqueId())) {
        sendError(player, "&#FFFFFF" + target.getName() + " &cis not a member.");
        return;
      }
      IslandManager.getInstance().update(island.withMembers(members));
      Messageable.sendSuccess(
          player, "&#FFFFFF" + target.getName() + " &#00FFFFremoved from your island.");
    } else {
      sendError(player, "Usage: &#FFFFFF/island member <add|remove> <player>");
    }
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) return List.of("tp", "lock", "visits", "member");
    if (args.length == 2 && args[0].equalsIgnoreCase("member")) return List.of("add", "remove");
    if (args.length == 3 && args[0].equalsIgnoreCase("member"))
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
          .collect(Collectors.toList());
    return List.of();
  }
}
