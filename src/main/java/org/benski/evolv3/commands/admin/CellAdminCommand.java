package org.benski.evolv3.commands.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.cell.CellBlockBackupManager;
import org.benski.evolv3.core.managers.cell.CellHologramManager;
import org.benski.evolv3.core.managers.cell.CellManager;
import org.benski.evolv3.core.managers.cell.CellManager.CellDef;
import org.benski.evolv3.core.managers.cell.CellRentManager;
import org.benski.evolv3.core.managers.cell.CellWorldGuardManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Admin subcommand for creating and managing cell definitions.
 *
 * <ul>
 *   <li>{@code /e cell create <name> [wallMat]}
 *   <li>{@code /e cell remove <name>}
 *   <li>{@code /e cell setprice <name> <price>}
 *   <li>{@code /e cell setcurrency <name> coins|essence}
 *   <li>{@code /e cell setspawn <name>}
 *   <li>{@code /e cell setlimit <name> <max>}
 *   <li>{@code /e cell setdirection <name> N|S|E|W}
 *   <li>{@code /e cell setwall <name> <material>}
 *   <li>{@code /e cell reset <name>}
 *   <li>{@code /e cell hologram <name> <yoffset|line2|color|scale|billboard|reset> [value]}
 *   <li>{@code /e cell list}
 *   <li>{@code /e cell reload}
 * </ul>
 */
public class CellAdminCommand extends BaseCommand {
  private static final List<String> DIRECTIONS = List.of("N", "S", "E", "W");
  private static final List<String> CURRENCIES = List.of("coins", "essence");
  private static final List<String> HOLO_SUBS =
      List.of("yoffset", "line2", "scale", "billboard", "setfacing", "setpos", "clearpos", "reset");

  /** Creates and registers the CellAdminCommand. @param plugin the owning plugin */
  public CellAdminCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** Returns the command name. */
  @Override
  public String getCommandName() {
    return "cell-admin";
  }

  @Override
  public @Nullable String getDescription() {
    return "Manage cell definitions";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.admin";
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
      sendUsage(player);
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "create" -> handleCreate(player, args);
      case "remove" -> handleRemove(player, args);
      case "price" -> handleSetPrice(player, args);
      case "currency" -> handleSetCurrency(player, args);
      case "spawn" -> handleSetSpawn(player, args);
      case "limit" -> handleSetLimit(player, args);
      case "direction" -> handleSetDirection(player, args);
      case "wall" -> handleSetWall(player, args);
      case "reset" -> handleReset(player, args);
      case "hologram" -> handleHologram(player, args);
      case "list" -> handleList(player);
      case "reload" -> handleReload(player);
      default -> sendUsage(player);
    }
    return true;
  }

  /** Handles create. */
  private void handleCreate(Player player, String[] args) {
    if (args.length < 4) {
      sendError(player, "Usage: &#FFFFFF/e cell create <name> <N|S|E|W> <material>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (CellManager.getInstance().exists(name)) {
      sendError(player, "A cell named &#FFFFFF" + name + " &calready exists.");
      return;
    }

    String dir = args[2].toUpperCase(Locale.ROOT);
    if (!DIRECTIONS.contains(dir)) {
      sendError(player, "Direction must be &#FFFFFF N, S, E, or W&#c.");
      return;
    }

    String wallMat = args[3].toUpperCase(Locale.ROOT);
    if (Material.matchMaterial(wallMat) == null) {
      sendError(player, "Unknown material: &#FFFFFF" + wallMat);
      return;
    }

    int[] bounds = getWorldEditBounds(player);
    if (bounds == null) {
      sendError(player, "Make a WorldEdit selection first, then run this command.");
      return;
    }

    FileConfiguration cellsCfg = ConfigRegistry.getInstance().get("cells");
    int maxExp = 10;
    double price = cellsCfg != null ? cellsCfg.getDouble("defaults.base-price", 500.0) : 500.0;
    String cur = cellsCfg != null ? cellsCfg.getString("defaults.currency", "coins") : "coins";

    int minX = Math.min(bounds[0], bounds[3]), maxX = Math.max(bounds[0], bounds[3]);
    int minY = Math.min(bounds[1], bounds[4]);
    int minZ = Math.min(bounds[2], bounds[5]), maxZ = Math.max(bounds[2], bounds[5]);
    double centerX = (minX + maxX) / 2.0 + 0.5;
    double centerZ = (minZ + maxZ) / 2.0 + 0.5;
    double spawnY = minY + 1.0;
    double[] spawnInfo =
        switch (dir) {
          case "E" -> new double[] {(minX - 2) + 0.5, spawnY, centerZ, 270f};
          case "W" -> new double[] {(maxX + 2) + 0.5, spawnY, centerZ, 90f};
          case "N" -> new double[] {centerX, spawnY, (maxZ + 2) + 0.5, 180f};
          case "S" -> new double[] {centerX, spawnY, (minZ - 2) + 0.5, 0f};
          default -> new double[] {player.getX(), player.getY(), player.getZ(), player.getYaw()};
        };

    CellDef def =
        new CellDef(
            name,
            player.getWorld().getName(),
            bounds[0],
            bounds[1],
            bounds[2],
            bounds[3],
            bounds[4],
            bounds[5],
            spawnInfo[0],
            spawnInfo[1],
            spawnInfo[2],
            (float) spawnInfo[3],
            0f,
            price,
            cur,
            maxExp,
            dir,
            wallMat);

    if (CellManager.getInstance().create(def)) {
      placeExpansionWall(def);
      CellHologramManager.getInstance().refresh(name);
      Messageable.sendSuccess(player, "Cell &#FFFFFF" + name + " &#00FFFFcreated.");
    } else {
      sendError(player, "Failed to create cell.");
    }
  }

  /** Handles remove. */
  private void handleRemove(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e cell remove <name>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (CellRentManager.getInstance().isRented(name)) {
      sendError(player, "Cell &#FFFFFF" + name + " &cis currently rented. Reset it first.");
      return;
    }
    if (CellManager.getInstance().remove(name)) {
      Messageable.sendSuccess(player, "Cell &#FFFFFF" + name + " &#00FFFFremoved.");
    } else {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
    }
  }

  /** Handles set price. */
  private void handleSetPrice(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e cell price <name> <price>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    CellDef def = CellManager.getInstance().get(name);
    if (def == null) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    try {
      double price = Double.parseDouble(args[2]);
      if (price < 0) {
        sendError(player, "Price must be non-negative.");
        return;
      }
      CellManager.getInstance().update(def.withPrice(price));
      Messageable.sendSuccess(
          player, "Cell &#FFFFFF" + name + " &#00FFFFprice set to &#FFFFFF" + price + "&#00FFFF.");
    } catch (NumberFormatException e) {
      sendError(player, "Invalid price.");
    }
  }

  /** Handles set currency. */
  private void handleSetCurrency(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e cell currency <name> <coins|essence>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    String cur = args[2].toLowerCase(Locale.ROOT);
    CellDef def = CellManager.getInstance().get(name);
    if (def == null) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    if (!CURRENCIES.contains(cur)) {
      sendError(player, "Currency must be &#FFFFFFcoins &cor &#FFFFFFessence&c.");
      return;
    }
    CellManager.getInstance().update(def.withCurrency(cur));
    Messageable.sendSuccess(
        player, "Cell &#FFFFFF" + name + " &#00FFFFcurrency set to &#FFFFFF" + cur + "&#00FFFF.");
  }

  /** Handles set spawn. */
  private void handleSetSpawn(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e cell spawn <name>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    CellDef def = CellManager.getInstance().get(name);
    if (def == null) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    CellManager.getInstance()
        .update(
            def.withSpawn(
                player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()));
    CellHologramManager.getInstance().refresh(name);
    Messageable.sendSuccess(
        player, "Cell &#FFFFFF" + name + " &#00FFFFspawn set to your location.");
  }

  /** Handles set limit. */
  private void handleSetLimit(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e cell limit <name> <max>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    CellDef def = CellManager.getInstance().get(name);
    if (def == null) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    try {
      int max = Integer.parseInt(args[2]);
      if (max < 1) {
        sendError(player, "Max expansions must be at least 1.");
        return;
      }
      CellManager.getInstance().update(def.withMaxExpansions(max));
      Messageable.sendSuccess(
          player,
          "Cell &#FFFFFF" + name + " &#00FFFFmax expansions set to &#FFFFFF" + max + "&#00FFFF.");
    } catch (NumberFormatException e) {
      sendError(player, "Invalid number.");
    }
  }

  /** Handles set direction. */
  private void handleSetDirection(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e cell direction <name> <N|S|E|W>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    String dir = args[2].toUpperCase(Locale.ROOT);
    CellDef def = CellManager.getInstance().get(name);
    if (def == null) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    if (!DIRECTIONS.contains(dir)) {
      sendError(player, "Direction must be &#FFFFFF N, S, E, or W&#c.");
      return;
    }
    CellManager.getInstance().update(def.withDirection(dir));
    Messageable.sendSuccess(
        player,
        "Cell &#FFFFFF"
            + name
            + " &#00FFFFexpansion direction set to &#FFFFFF"
            + dir
            + "&#00FFFF.");
  }

  /** Handles set wall. */
  private void handleSetWall(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e cell wall <name> <material>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    String mat = args[2].toUpperCase(Locale.ROOT);
    CellDef def = CellManager.getInstance().get(name);
    if (def == null) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    if (Material.matchMaterial(mat) == null) {
      sendError(player, "Unknown material: &#FFFFFF" + mat + "&#c.");
      return;
    }
    CellManager.getInstance().update(def.withWallMaterial(mat));
    Messageable.sendSuccess(
        player,
        "Cell &#FFFFFF" + name + " &#00FFFFwall material set to &#FFFFFF" + mat + "&#00FFFF.");
  }

  /** Handles reset. */
  private void handleReset(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e cell reset <name>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (!CellManager.getInstance().exists(name)) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    CellRentManager.CellRent rent = CellRentManager.getInstance().getRent(name);
    CellBlockBackupManager.getInstance().restore(name);
    CellDef restored = CellManager.getInstance().get(name);
    if (restored != null) placeExpansionWall(restored);
    CellWorldGuardManager.getInstance().deleteRegion(name);
    if (rent != null) {
      CellRentManager.getInstance().removeRent(name);
      Player tenant = Bukkit.getPlayer(rent.ownerUuid());
      if (tenant != null)
        Messageable.sendError(tenant, "Your cell &#FFFFFF" + name + " &cwas reset by an admin.");
    }
    CellHologramManager.getInstance().refresh(name);
    Messageable.sendSuccess(player, "Cell &#FFFFFF" + name + " &#00FFFFhas been reset.");
  }

  /** Handles hologram. */
  private void handleHologram(Player player, String[] args) {
    if (args.length < 3) {
      sendHoloUsage(player);
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (!CellManager.getInstance().exists(name)) {
      sendError(player, "No cell named &#FFFFFF" + name + "&#c.");
      return;
    }
    String sub = args[2].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "yoffset" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e cell hologram <name> yoffset <value>");
          return;
        }
        try {
          double v = Double.parseDouble(args[3]);
          CellHologramManager.getInstance().setYOffset(name, v);
          Messageable.sendSuccess(
              player,
              "Hologram y-offset for &#FFFFFF"
                  + name
                  + " &#00FFFFset to &#FFFFFF"
                  + v
                  + "&#00FFFF.");
        } catch (NumberFormatException e) {
          sendError(player, "Invalid number.");
        }
      }
      case "line2" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e cell hologram <name> line2 <text>");
          return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        CellHologramManager.getInstance().setLine2(name, text);
        Messageable.sendSuccess(player, "Hologram line2 for &#FFFFFF" + name + " &#00FFFFset.");
      }
      case "scale" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e cell hologram <name> scale <value>");
          return;
        }
        try {
          float s = Float.parseFloat(args[3]);
          if (s <= 0) {
            sendError(player, "Scale must be positive.");
            return;
          }
          CellHologramManager.getInstance().setScale(name, s);
          Messageable.sendSuccess(
              player,
              "Hologram scale for &#FFFFFF" + name + " &#00FFFFset to &#FFFFFF" + s + "&#00FFFF.");
        } catch (NumberFormatException e) {
          sendError(player, "Invalid number.");
        }
      }
      case "billboard" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e cell hologram <name> billboard <true|false>");
          return;
        }
        boolean faces = Boolean.parseBoolean(args[3]);
        CellHologramManager.getInstance().setBillboard(name, faces);
        Messageable.sendSuccess(
            player,
            "Hologram billboard for &#FFFFFF"
                + name
                + (faces
                    ? " &#00FFFFset to CENTER (faces player)."
                    : " &#00FFFFset to FIXED (static)."));
      }
      case "setfacing" -> {
        float yaw = (player.getLocation().getYaw() + 180f) % 360f;
        CellHologramManager.getInstance().setFacing(name, yaw);
        Messageable.sendSuccess(
            player,
            "Hologram facing for &#FFFFFF"
                + name
                + " &#00FFFFset from your look direction. &#888888(billboard false only)");
      }
      case "setpos" -> {
        CellHologramManager.getInstance()
            .setPos(name, player.getX(), player.getY(), player.getZ(), player.getWorld().getName());
        Messageable.sendSuccess(
            player,
            "Hologram position for &#FFFFFF"
                + name
                + " &#00FFFFset to your location &#FFFFFF("
                + String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ())
                + ")&#00FFFF.");
      }
      case "clearpos" -> {
        CellHologramManager.getInstance().clearPos(name);
        Messageable.sendSuccess(
            player,
            "Hologram position for &#FFFFFF" + name + " &#00FFFFcleared — using spawn + yoffset.");
      }
      case "reset" -> {
        CellHologramManager.getInstance().resetConfig(name);
        Messageable.sendSuccess(
            player, "Hologram config for &#FFFFFF" + name + " &#00FFFFreset to defaults.");
      }
      default -> sendHoloUsage(player);
    }
  }

  /** Handles list. */
  private void handleList(Player player) {
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#5555FF&l◼ Cells");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    var all = CellManager.getInstance().getAll();
    if (all.isEmpty()) {
      Messageable.sendRaw(player, "  &#888888No cells configured.");
    } else {
      for (CellDef def : all) {
        boolean rented = CellRentManager.getInstance().isRented(def.name());
        String status = rented ? "&#FF5555rented" : "&#55FF55available";
        Messageable.sendRaw(
            player,
            "  &#FFFFFF"
                + def.name()
                + " &#888888"
                + status
                + " &#888888["
                + def.world()
                + " "
                + def.x1()
                + ","
                + def.y1()
                + ","
                + def.z1()
                + " → "
                + def.x2()
                + ","
                + def.y2()
                + ","
                + def.z2()
                + "]"
                + " &#888888price="
                + def.basePrice()
                + " "
                + def.currency()
                + " dir="
                + def.upgradeDirection()
                + " max="
                + def.maxExpansions()
                + "]");
      }
    }
    Messageable.sendRaw(player, "");
  }

  /** Handles reload. */
  private void handleReload(Player player) {
    CellManager.getInstance().loadFromDisk();
    CellRentManager.getInstance().loadFromDisk();
    Messageable.sendSuccess(player, "Cell data reloaded from disk.");
  }

  /** Places the wall material on the single expansion face (upgradeDirection only). */
  private void placeExpansionWall(@NotNull CellDef def) {
    World world = Bukkit.getWorld(def.world());
    if (world == null) return;
    Material mat = Material.matchMaterial(def.wallMaterial());
    if (mat == null) mat = Material.STONE_BRICKS;

    int x1 = Math.min(def.x1(), def.x2()), x2 = Math.max(def.x1(), def.x2());
    int y1 = Math.min(def.y1(), def.y2()), y2 = Math.max(def.y1(), def.y2());
    int z1 = Math.min(def.z1(), def.z2()), z2 = Math.max(def.z1(), def.z2());

    switch (def.upgradeDirection()) {
      case "N" -> {
        for (int x = x1; x <= x2; x++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x, y, z1 - 1).setType(mat, false);
      }
      case "S" -> {
        for (int x = x1; x <= x2; x++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x, y, z2 + 1).setType(mat, false);
      }
      case "E" -> {
        for (int z = z1; z <= z2; z++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x2 + 1, y, z).setType(mat, false);
      }
      case "W" -> {
        for (int z = z1; z <= z2; z++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x1 - 1, y, z).setType(mat, false);
      }
    }
  }

  private @Nullable int[] getWorldEditBounds(Player player) {
    if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
      sendError(player, "WorldEdit is not installed.");
      return null;
    }
    try {
      com.sk89q.worldedit.WorldEdit we = com.sk89q.worldedit.WorldEdit.getInstance();
      com.sk89q.worldedit.bukkit.BukkitPlayer wePlayer =
          com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player);
      com.sk89q.worldedit.LocalSession session = we.getSessionManager().get(wePlayer);
      com.sk89q.worldedit.regions.Region selection = session.getSelection(wePlayer.getWorld());
      com.sk89q.worldedit.math.BlockVector3 min = selection.getMinimumPoint();
      com.sk89q.worldedit.math.BlockVector3 max = selection.getMaximumPoint();
      return new int[] {min.x(), min.y(), min.z(), max.x(), max.y(), max.z()};
    } catch (com.sk89q.worldedit.IncompleteRegionException e) {
      sendError(player, "No WorldEdit selection. Use the wand (//wand) to select a region.");
      return null;
    } catch (NoClassDefFoundError | Exception e) {
      sendError(player, "Failed to read WorldEdit selection: " + e.getMessage());
      return null;
    }
  }

  /** Sends usage. */
  private void sendUsage(Player player) {
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#5555FF&l◼ Cell Admin Commands");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    Messageable.sendRaw(
        player,
        "  &#888888/e cell create <name> <N|S|E|W> <material> &#444444» &#CCCCCCCreate from WE selection");
    Messageable.sendRaw(player, "  &#888888/e cell remove <name> &#444444» &#CCCCCCRemove cell");
    Messageable.sendRaw(
        player, "  &#888888/e cell price <name> <n> &#444444» &#CCCCCCSet daily rent price");
    Messageable.sendRaw(
        player, "  &#888888/e cell currency <name> <coins|essence> &#444444» &#CCCCCCSet currency");
    Messageable.sendRaw(
        player, "  &#888888/e cell spawn <name> &#444444» &#CCCCCCSet spawn to your location");
    Messageable.sendRaw(
        player, "  &#888888/e cell limit <name> <n> &#444444» &#CCCCCCSet max expansions");
    Messageable.sendRaw(
        player,
        "  &#888888/e cell direction <name> N|S|E|W &#444444» &#CCCCCCSet expansion direction");
    Messageable.sendRaw(
        player, "  &#888888/e cell wall <name> <material> &#444444» &#CCCCCCSet wall material");
    Messageable.sendRaw(
        player, "  &#888888/e cell reset <name> &#444444» &#CCCCCCForce reset cell");
    Messageable.sendRaw(
        player, "  &#888888/e cell hologram <name> <sub> [value] &#444444» &#CCCCCCEdit hologram");
    Messageable.sendRaw(player, "  &#888888/e cell list &#444444» &#CCCCCCList all cells");
    Messageable.sendRaw(player, "  &#888888/e cell reload &#444444» &#CCCCCCReload from disk");
    Messageable.sendRaw(player, "");
  }

  /** Sends holo usage. */
  private void sendHoloUsage(Player player) {
    Messageable.sendRaw(
        player, "  &#888888/e cell hologram <name> yoffset <n> &#444444» &#CCCCCCVertical offset");
    Messageable.sendRaw(
        player,
        "  &#888888/e cell hologram <name> line2 <text> &#444444» &#CCCCCCCustom second line");
    Messageable.sendRaw(
        player, "  &#888888/e cell hologram <name> scale <n> &#444444» &#CCCCCCText scale");
    Messageable.sendRaw(
        player,
        "  &#888888/e cell hologram <name> billboard <true|false> &#444444» &#CCCCCCToggle player-facing rotation");
    Messageable.sendRaw(
        player,
        "  &#888888/e cell hologram <name> setfacing &#444444» &#CCCCCCSet facing from your look direction &#888888(billboard false only)");
    Messageable.sendRaw(
        player,
        "  &#888888/e cell hologram <name> setpos &#444444» &#CCCCCCMove hologram to your location");
    Messageable.sendRaw(
        player,
        "  &#888888/e cell hologram <name> clearpos &#444444» &#CCCCCCReturn to spawn + yoffset");
    Messageable.sendRaw(
        player, "  &#888888/e cell hologram <name> reset &#444444» &#CCCCCCReset to defaults");
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1)
      return List.of(
          "create",
          "remove",
          "price",
          "currency",
          "spawn",
          "limit",
          "direction",
          "wall",
          "reset",
          "hologram",
          "list",
          "reload");
    String sub = args[0].toLowerCase(Locale.ROOT);
    List<String> names =
        CellManager.getInstance().getAll().stream().map(CellDef::name).collect(Collectors.toList());
    if (args.length == 2 && !sub.equals("list") && !sub.equals("reload") && !sub.equals("create"))
      return names.stream()
          .filter(n -> n.startsWith(args[1].toLowerCase()))
          .collect(Collectors.toList());
    if (args.length == 3) {
      if (sub.equals("create")) return DIRECTIONS;
      if (sub.equals("currency")) return CURRENCIES;
      if (sub.equals("direction")) return DIRECTIONS;
      if (sub.equals("price")) return List.of("100", "500", "1000", "2000");
      if (sub.equals("limit")) return List.of("5", "10", "15", "20");
      if (sub.equals("hologram")) return HOLO_SUBS;
    }
    if (args.length == 4) {
      if (sub.equals("create"))
        return List.of("STONE_BRICKS", "MAGENTA_WOOL", "OAK_PLANKS", "GLASS", "IRON_BLOCK");
    }
    if (args.length == 4 && sub.equals("hologram")) {
      String holoSub = args[2].toLowerCase(Locale.ROOT);
      if (holoSub.equals("billboard")) return List.of("true", "false");
      if (holoSub.equals("scale")) return List.of("0.5", "1.0", "1.5", "2.0");
      if (holoSub.equals("yoffset")) return List.of("1.0", "2.5", "3.0", "5.0");
    }
    return List.of();
  }
}
