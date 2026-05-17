package org.benski.evolv3.commands;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.IslandManager;
import org.benski.evolv3.core.managers.IslandManager.IslandData;
import org.benski.evolv3.core.managers.IslandWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Admin subcommand for managing islands.
 *
 * <ul>
 *   <li>{@code /e island list}
 *   <li>{@code /e island delete <playerName>}
 *   <li>{@code /e island teleport <playerName>}
 *   <li>{@code /e island reload}
 * </ul>
 */
public class IslandAdminCommand extends BaseCommand {
  public IslandAdminCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "island-admin";
  }

  @Override
  public @Nullable String getDescription() {
    return "Manage player islands";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.admin";
  }

  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    Player player = (Player) sender;
    if (args.length == 0) {
      sendUsage(player);
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "list" -> handleList(player);
      case "delete" -> handleDelete(player, args);
      case "teleport" -> handleTeleport(player, args);
      case "reload" -> handleReload(player);
      default -> sendUsage(player);
    }
    return true;
  }

  private void handleList(Player player) {
    var all = IslandManager.getInstance().getAll();
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#5555FF&l◼ Islands");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    if (all.isEmpty()) {
      Messageable.sendRaw(player, "  &#888888No islands yet.");
    } else {
      for (IslandData data : all) {
        Messageable.sendRaw(
            player,
            "  &#FFFFFF"
                + data.ownerName()
                + " &#888888grid=("
                + data.gridX()
                + ","
                + data.gridZ()
                + ")"
                + " parcels="
                + data.purchasedSlots().size()
                + " locked="
                + data.locked()
                + " visits="
                + data.openForVisits());
      }
    }
    Messageable.sendRaw(player, "");
  }

  private void handleDelete(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e island delete <playerName>");
      return;
    }
    IslandData data = findIsland(player, args[1]);
    if (data == null) return;

    Bukkit.getScheduler()
        .runTaskAsynchronously(
            plugin,
            () -> {
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        IslandWorldManager.getInstance().deleteIsland(data);
                        IslandManager.getInstance().removeIsland(data.ownerUuid());
                        Messageable.sendSuccess(
                            player,
                            "Island belonging to &#FFFFFF"
                                + data.ownerName()
                                + " &#00FFFFhas been deleted and the land has been cleared.");
                        Player owner = Bukkit.getPlayer(data.ownerUuid());
                        if (owner != null) {
                          Messageable.sendError(owner, "Your island has been deleted by an admin.");
                        }
                      });
            });
  }

  private void handleTeleport(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e island teleport <playerName>");
      return;
    }
    IslandData data = findIsland(player, args[1]);
    if (data == null) return;

    Location loc = IslandWorldManager.getInstance().getSpawnLocation(data);
    if (loc == null) {
      sendError(player, "Island world is not loaded.");
      return;
    }
    player.teleport(loc);
    Messageable.sendSuccess(
        player, "Teleported to &#FFFFFF" + data.ownerName() + "&#00FFFF's island.");
  }

  private void handleReload(Player player) {
    IslandManager.getInstance().loadFromDisk();
    Messageable.sendSuccess(player, "Island data reloaded from disk.");
  }

  @SuppressWarnings("deprecation")
  private @Nullable IslandData findIsland(Player sender, String name) {
    Player target = Bukkit.getPlayerExact(name);
    if (target != null) {
      IslandData data = IslandManager.getInstance().get(target.getUniqueId());
      if (data == null) {
        sendError(sender, "&#FFFFFF" + name + " &cdoes not have an island.");
        return null;
      }
      return data;
    }
    var offline = Bukkit.getOfflinePlayer(name);
    if (!offline.hasPlayedBefore()) {
      sendError(sender, "Player &#FFFFFF" + name + " &cnot found.");
      return null;
    }
    IslandData data = IslandManager.getInstance().get(offline.getUniqueId());
    if (data == null) {
      sendError(sender, "&#FFFFFF" + name + " &cdoes not have an island.");
      return null;
    }
    return data;
  }

  private void sendUsage(Player player) {
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#5555FF&l◼ Island Admin Commands");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    Messageable.sendRaw(player, "  &#888888/e island list &#444444» &#CCCCCCList all islands");
    Messageable.sendRaw(
        player,
        "  &#888888/e island delete <player> &#444444» &#CCCCCCDelete island and clear land");
    Messageable.sendRaw(
        player,
        "  &#888888/e island teleport <player> &#444444» &#CCCCCCTeleport to a player's island");
    Messageable.sendRaw(
        player, "  &#888888/e island reload &#444444» &#CCCCCCReload island data from disk");
    Messageable.sendRaw(player, "");
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) return List.of("list", "delete", "teleport", "reload");
    if (args.length == 2
        && !args[0].equalsIgnoreCase("list")
        && !args[0].equalsIgnoreCase("reload")) {
      return IslandManager.getInstance().getAll().stream()
          .map(IslandData::ownerName)
          .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
          .collect(Collectors.toList());
    }
    return List.of();
  }
}
