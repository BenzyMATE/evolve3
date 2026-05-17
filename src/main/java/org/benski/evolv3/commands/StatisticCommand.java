package org.benski.evolv3.commands;

import java.util.List;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Admin command to directly set a statistic on a player's pickaxe or player profile.
 *
 * <p>Usage: {@code /e statistic <pickaxe|player> <stat> <player> <amount>}
 *
 * <p>Pickaxe stats: xp, level, prestige, essence, atoms, evolution, enchantrolls, evolutionrolls
 *
 * <p>Player stats: essence, coins, pickaxelimit
 */
public class StatisticCommand extends BaseCommand {
  private static final List<String> PICKAXE_STATS =
      List.of(
          "xp",
          "level",
          "prestige",
          "essence",
          "atoms",
          "evolution",
          "enchantrolls",
          "evolutionrolls");

  private static final List<String> PLAYER_STATS = List.of("essence", "coins", "pickaxelimit");

  public StatisticCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "statistic";
  }

  @Override
  public @Nullable String getDescription() {
    return "Set a statistic on a player or pickaxe";
  }

  @Override
  public @Nullable String getUsage() {
    return "/e statistic <pickaxe|player> <stat> <player> <amount>";
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
    if (args.length < 4) {
      sendInfo(sender, "Usage: &#FFFFFF/e statistic <pickaxe|player> <stat> <player> <amount>");
      return true;
    }

    String type = args[0].toLowerCase();
    String stat = args[1].toLowerCase();
    String name = args[2];
    String rawAmt = args[3];

    Player target = Bukkit.getPlayer(name);
    if (target == null) {
      sendError(sender, "Player not found or not online: &#FFFFFF" + name);
      return true;
    }

    switch (type) {
      case "pickaxe" -> handlePickaxe(sender, target, stat, rawAmt);
      case "player" -> handlePlayer(sender, target, stat, rawAmt);
      default ->
          sendError(
              sender,
              "Unknown type &#FFFFFF"
                  + type
                  + " &c— use &#FFFFFFpickaxe &#CCCCCCor &#FFFFFFplayer&#CCCCCC.");
    }
    return true;
  }

  private void handlePickaxe(CommandSender sender, Player target, String stat, String rawAmt) {
    PickaxeDataCenter dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    if (dc == null) {
      sendError(sender, "Pickaxe data center not available.");
      return;
    }

    PickaxeProfile profile = dc.getLocal(target);
    if (profile == null) {
      sendError(sender, "&#FFFFFF" + target.getName() + " &cdoes not have a pickaxe loaded.");
      return;
    }

    int amount;
    try {
      amount = Integer.parseInt(rawAmt);
    } catch (NumberFormatException e) {
      sendError(sender, "Amount must be an integer: &#FFFFFF" + rawAmt);
      return;
    }

    switch (stat) {
      case "xp" -> profile.xp = amount;
      case "level" -> profile.level = amount;
      case "prestige" -> profile.prestige = amount;
      case "essence" -> profile.currentEssence = amount;
      case "atoms" -> profile.evolutionAtoms = amount;
      case "evolution" -> profile.evolution = amount;
      case "enchantrolls" -> profile.enchantRolls = amount;
      case "evolutionrolls" -> profile.evolutionRolls = amount;
      default -> {
        sendError(
            sender,
            "Unknown pickaxe stat &#FFFFFF"
                + stat
                + " &c— valid: &#FFFFFF"
                + String.join(", ", PICKAXE_STATS));
        return;
      }
    }

    profile.save(null);
    PickaxeManager.getInstance().updateLore(target, null);
    sendSuccess(
        sender,
        "Set &#FFFFFF"
            + target.getName()
            + "&#00FFFF's pickaxe &#FFFFFF"
            + stat
            + " &#00FFFFto &#FFFFFF"
            + amount
            + "&#00FFFF.");
  }

  private void handlePlayer(CommandSender sender, Player target, String stat, String rawAmt) {
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) {
      sendError(sender, "Player data center not available.");
      return;
    }

    PlayerProfile profile = dc.getLocal(target);
    if (profile == null) {
      sendError(sender, "&#FFFFFF" + target.getName() + " &cdoes not have a profile loaded.");
      return;
    }

    switch (stat) {
      case "essence" -> {
        int amount = parseInteger(sender, rawAmt);
        if (amount < 0) return;
        profile.essence = amount;
        profile.save(null);
        sendSuccess(
            sender,
            "Set &#FFFFFF"
                + target.getName()
                + "&#00FFFF's essence to &#FFFFFF"
                + amount
                + "&#00FFFF.");
      }
      case "coins" -> {
        double amount = parseDouble(sender, rawAmt);
        if (amount < 0) return;
        profile.coins = amount;
        profile.save(null);
        sendSuccess(
            sender,
            "Set &#FFFFFF"
                + target.getName()
                + "&#00FFFF's coins to &#FFFFFF"
                + amount
                + "&#00FFFF.");
      }
      case "pickaxelimit" -> {
        int amount = parseInteger(sender, rawAmt);
        if (amount < 0) return;
        profile.pickaxeLimit = amount;
        profile.save(null);
        sendSuccess(
            sender,
            "Set &#FFFFFF"
                + target.getName()
                + "&#00FFFF's pickaxe limit to &#FFFFFF"
                + amount
                + "&#00FFFF.");
      }
      default ->
          sendError(
              sender,
              "Unknown player stat &#FFFFFF"
                  + stat
                  + " &c— valid: &#FFFFFF"
                  + String.join(", ", PLAYER_STATS));
    }
  }

  private int parseInteger(CommandSender sender, String raw) {
    try {
      int v = Integer.parseInt(raw);
      if (v < 0) {
        sendError(sender, "Amount must be non-negative.");
        return -1;
      }
      return v;
    } catch (NumberFormatException e) {
      sendError(sender, "Amount must be an integer: &#FFFFFF" + raw);
      return -1;
    }
  }

  private double parseDouble(CommandSender sender, String raw) {
    try {
      double v = Double.parseDouble(raw);
      if (v < 0) {
        sendError(sender, "Amount must be non-negative.");
        return -1;
      }
      return v;
    } catch (NumberFormatException e) {
      sendError(sender, "Amount must be a number: &#FFFFFF" + raw);
      return -1;
    }
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (args.length == 1) return prefix(List.of("pickaxe", "player"), args[0]);

    if (args.length == 2) {
      String type = args[0].toLowerCase();
      List<String> stats =
          switch (type) {
            case "pickaxe" -> PICKAXE_STATS;
            case "player" -> PLAYER_STATS;
            default -> List.of();
          };
      return prefix(stats, args[1]);
    }

    if (args.length == 3) {
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
          .toList();
    }

    if (args.length == 4) {
      return prefix(List.of("1", "10", "100", "1000"), args[3]);
    }

    return List.of();
  }

  private static List<String> prefix(List<String> options, String input) {
    String lc = input.toLowerCase();
    return options.stream().filter(o -> o.toLowerCase().startsWith(lc)).toList();
  }
}
