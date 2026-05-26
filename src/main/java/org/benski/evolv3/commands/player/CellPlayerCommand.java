package org.benski.evolv3.commands.player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.cell.CellBlockBackupManager;
import org.benski.evolv3.core.managers.cell.CellManager;
import org.benski.evolv3.core.managers.cell.CellManager.CellDef;
import org.benski.evolv3.core.managers.cell.CellRentManager;
import org.benski.evolv3.core.managers.cell.CellRentManager.CellRent;
import org.benski.evolv3.core.managers.cell.CellWorldGuardManager;
import org.benski.evolv3.menus.cell.CellMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Player command for cell rental and management.
 *
 * <ul>
 *   <li>{@code /cell} — open cell menu
 *   <li>{@code /cell leave} — abandon/leave cell
 *   <li>{@code /cell member add <name>} — add member (owner only)
 *   <li>{@code /cell member remove <name>} — remove member (owner only)
 * </ul>
 */
public class CellPlayerCommand extends BaseCommand {
  /** Creates and registers the CellPlayerCommand. @param plugin the owning plugin */
  public CellPlayerCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** Returns the command name. */
  @Override
  public String getCommandName() {
    return "cell";
  }

  @Override
  public @Nullable String getDescription() {
    return "Manage your rented cell";
  }

  @Override
  public @Nullable String getPermission() {
    return null;
  }

  /** Execute. */
  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    Player player = (Player) sender;

    if (args.length == 0) {
      handleOpenMenu(player);
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "leave" -> handleLeave(player);
      case "member" -> handleMember(player, args);
      default -> handleOpenMenu(player);
    }
    return true;
  }

  /** Handles open menu. */
  private void handleOpenMenu(Player player) {
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      Messageable.sendError(player, "You don't have a rented cell.");
      return;
    }
    CellMenu.getInstance().openMenu(player);
  }

  /** Handles leave. */
  private void handleLeave(Player player) {
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      sendError(player, "You don't have a rented cell.");
      return;
    }

    boolean isOwner = rent.ownerUuid().equals(player.getUniqueId());
    if (isOwner) {
      CellBlockBackupManager.getInstance().restore(rent.cellName());
      CellWorldGuardManager.getInstance().deleteRegion(rent.cellName());
      CellRentManager.getInstance().removeRent(rent.cellName());
      Messageable.sendSuccess(
          player, "You have abandoned your cell &#FFFFFF" + rent.cellName() + "&#00FFFF.");
    } else {
      CellRentManager.getInstance().removeMember(rent.cellName(), player.getUniqueId());
      Messageable.sendSuccess(
          player, "You have left the cell &#FFFFFF" + rent.cellName() + "&#00FFFF.");
    }
  }

  /** Handles member. */
  private void handleMember(Player player, String[] args) {
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      sendError(player, "You don't have a rented cell.");
      return;
    }
    if (!rent.ownerUuid().equals(player.getUniqueId())) {
      sendError(player, "Only the cell owner can manage members.");
      return;
    }
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/cell member <add|remove> <player>");
      return;
    }
    String action = args[1].toLowerCase(Locale.ROOT);
    Player target = Bukkit.getPlayer(args[2]);
    if (target == null) {
      sendError(player, "Player not found or offline.");
      return;
    }

    CellDef def = CellManager.getInstance().get(rent.cellName());
    var world = def != null ? Bukkit.getWorld(def.world()) : null;

    if (action.equals("add")) {
      if (CellRentManager.getInstance().addMember(rent.cellName(), target.getUniqueId())) {
        if (world != null)
          CellWorldGuardManager.getInstance()
              .addMember(rent.cellName(), world, target.getUniqueId());
        Messageable.sendSuccess(
            player, "&#FFFFFF" + target.getName() + " &#00FFFFadded to your cell.");
        Messageable.sendSuccess(
            target, "You have been added to &#FFFFFF" + player.getName() + "&#00FFFF's cell.");
      } else {
        sendError(player, "&#FFFFFF" + target.getName() + " &cis already a member.");
      }
    } else if (action.equals("remove")) {
      if (CellRentManager.getInstance().removeMember(rent.cellName(), target.getUniqueId())) {
        if (world != null)
          CellWorldGuardManager.getInstance()
              .removeMember(rent.cellName(), world, target.getUniqueId());
        Messageable.sendSuccess(
            player, "&#FFFFFF" + target.getName() + " &#00FFFFremoved from your cell.");
      } else {
        sendError(player, "&#FFFFFF" + target.getName() + " &cis not a member.");
      }
    } else {
      sendError(player, "Usage: &#FFFFFF/cell member <add|remove> <player>");
    }
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) return List.of("leave", "member");
    String sub = args[0].toLowerCase(Locale.ROOT);
    if (args.length == 2 && sub.equals("member")) return List.of("add", "remove");
    if (args.length == 3 && sub.equals("member"))
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
          .collect(Collectors.toList());
    return List.of();
  }
}
