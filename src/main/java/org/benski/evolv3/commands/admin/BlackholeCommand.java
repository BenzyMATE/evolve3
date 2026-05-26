package org.benski.evolv3.commands.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.world.BlackholeHologramManager;
import org.benski.evolv3.core.managers.world.BlackholeManager;
import org.benski.evolv3.core.managers.world.BlackholeManager.Blackhole;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Admin subcommand for creating and managing blackhole points.
 *
 * <ul>
 *   <li>{@code /e blackhole create <name> [teleportRadius]} — creates at your location
 *   <li>{@code /e blackhole remove <name>} — removes (and unlinks partner)
 *   <li>{@code /e blackhole link <a> <b>} — links two blackholes
 *   <li>{@code /e blackhole unlink <name>} — removes link
 *   <li>{@code /e blackhole setteleport <name> <r>} — sets inner teleport radius
 *   <li>{@code /e blackhole seteffect <name> <r>} — sets outer effect radius
 *   <li>{@code /e blackhole setgroundlevel <name> [y]} — sets Y (defaults to your feet)
 *   <li>{@code /e blackhole list} — lists all blackholes
 *   <li>{@code /e blackhole reload} — reloads from disk
 * </ul>
 */
public class BlackholeCommand extends BaseCommand {
  private static final double DEFAULT_TELEPORT_RADIUS = 8.0;
  private static final double DEFAULT_EFFECT_RADIUS = 30.0;

  /** Creates and registers the BlackholeCommand. @param plugin the owning plugin */
  public BlackholeCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** Returns the command name. */
  @Override
  public String getCommandName() {
    return "blackhole";
  }

  @Override
  public @Nullable String getDescription() {
    return "Manage blackhole points";
  }

  @Override
  public @Nullable String getUsage() {
    return "/e blackhole <create|remove|link|unlink|setteleport|seteffect|hologram|list|reload>";
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
      case "link" -> handleLink(player, args);
      case "unlink" -> handleUnlink(player, args);
      case "setteleport" -> handleSetTeleport(player, args);
      case "seteffect" -> handleSetEffect(player, args);
      case "setgroundlevel" -> handleSetGroundLevel(player, args);
      case "hologram" -> handleHologram(player, args);
      case "list" -> handleList(player);
      case "reload" -> handleReload(player);
      default -> sendUsage(player);
    }
    return true;
  }

  /** Handles create. */
  private void handleCreate(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e blackhole create <name> [teleportRadius]");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (BlackholeManager.getInstance().exists(name)) {
      sendError(player, "A blackhole named &#FFFFFF" + name + " &calready exists.");
      return;
    }

    double tpr = DEFAULT_TELEPORT_RADIUS;
    if (args.length >= 3) {
      try {
        tpr = Double.parseDouble(args[2]);
      } catch (NumberFormatException e) {
        sendError(player, "Invalid radius — must be a number.");
        return;
      }
      if (tpr <= 0) {
        sendError(player, "Radius must be greater than 0.");
        return;
      }
    }

    double effectR = tpr * 4.0;
    var loc = player.getLocation();

    boolean ok =
        BlackholeManager.getInstance()
            .create(
                name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), effectR, tpr);

    if (ok) {
      Blackhole created = BlackholeManager.getInstance().get(name);
      if (created != null) BlackholeHologramManager.getInstance().spawnHologram(created);
      Messageable.sendSuccess(
          player,
          "Blackhole &#FFFFFF"
              + name
              + " &#00FFFFcreated &#888888(teleport: &#FFFFFF"
              + fmt(tpr)
              + " &#888888blocks, effect: &#FFFFFF"
              + fmt(effectR)
              + " &#888888blocks). Link it with &#FFFFFF/e blackhole link "
              + name
              + " <other>&#888888.");
    } else {
      sendError(player, "Failed to create blackhole &#FFFFFF" + name + "&#c.");
    }
  }

  /** Handles remove. */
  private void handleRemove(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e blackhole remove <name>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (BlackholeManager.getInstance().remove(name)) {
      BlackholeHologramManager.getInstance().removeHologram(name);
      Messageable.sendSuccess(player, "Blackhole &#FFFFFF" + name + " &#00FFFFremoved.");
    } else {
      sendError(player, "No blackhole named &#FFFFFF" + name + "&#c.");
    }
  }

  /** Handles link. */
  private void handleLink(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e blackhole link <name1> <name2>");
      return;
    }
    String a = args[1].toLowerCase(Locale.ROOT), b = args[2].toLowerCase(Locale.ROOT);
    if (BlackholeManager.getInstance().link(a, b)) {
      Messageable.sendSuccess(
          player, "&#FFFFFF" + a + " &#00FFFF↔ &#FFFFFF" + b + " &#00FFFFlinked.");
    } else {
      sendError(player, "Could not link — ensure both names exist and are different.");
    }
  }

  /** Handles unlink. */
  private void handleUnlink(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e blackhole unlink <name>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (BlackholeManager.getInstance().unlink(name)) {
      Messageable.sendSuccess(player, "&#FFFFFF" + name + " &#00FFFFunlinked.");
    } else {
      sendError(player, "&#FFFFFF" + name + " &cdoesn't exist or has no link.");
    }
  }

  /** Handles set teleport. */
  private void handleSetTeleport(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e blackhole setteleport <name> <radius>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    double r;
    try {
      r = Double.parseDouble(args[2]);
    } catch (NumberFormatException e) {
      sendError(player, "Invalid radius — must be a number.");
      return;
    }
    if (r <= 0) {
      sendError(player, "Radius must be greater than 0.");
      return;
    }

    if (BlackholeManager.getInstance().setTeleportRadius(name, r)) {
      Messageable.sendSuccess(
          player,
          "&#FFFFFF"
              + name
              + " &#00FFFFteleport radius set to &#FFFFFF"
              + fmt(r)
              + " &#00FFFFblocks.");
    } else {
      sendError(player, "No blackhole named &#FFFFFF" + name + "&#c.");
    }
  }

  /** Handles set ground level. */
  private void handleSetGroundLevel(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e blackhole setgroundlevel <name> [y]");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);

    double y;
    if (args.length >= 3) {
      try {
        y = Double.parseDouble(args[2]);
      } catch (NumberFormatException e) {
        sendError(player, "Invalid Y — must be a number.");
        return;
      }
    } else {
      y = player.getLocation().getY();
    }

    if (BlackholeManager.getInstance().setGroundLevel(name, y)) {
      BlackholeHologramManager.getInstance().refresh(name);
      Messageable.sendSuccess(
          player,
          "&#FFFFFF" + name + " &#00FFFFground level set to Y=&#FFFFFF" + fmt(y) + "&#00FFFF.");
    } else {
      sendError(player, "No blackhole named &#FFFFFF" + name + "&#c.");
    }
  }

  /** Handles set effect. */
  private void handleSetEffect(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e blackhole seteffect <name> <radius>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    double r;
    try {
      r = Double.parseDouble(args[2]);
    } catch (NumberFormatException e) {
      sendError(player, "Invalid radius — must be a number.");
      return;
    }
    if (r <= 0) {
      sendError(player, "Radius must be greater than 0.");
      return;
    }

    if (BlackholeManager.getInstance().setEffectRadius(name, r)) {
      Messageable.sendSuccess(
          player,
          "&#FFFFFF"
              + name
              + " &#00FFFFeffect radius set to &#FFFFFF"
              + fmt(r)
              + " &#00FFFFblocks.");
    } else {
      sendError(player, "No blackhole named &#FFFFFF" + name + "&#c.");
    }
  }

  private static final List<String> HOLO_COLORS =
      List.of("purple", "yellow", "green", "red", "blue", "white", "cyan");

  /** Handles hologram. */
  private void handleHologram(Player player, String[] args) {
    if (args.length < 3) {
      sendError(
          player,
          "Usage: &#FFFFFF/e blackhole hologram <name> <setheight|setline2|setcolor|setscale|reset>");
      return;
    }
    String name = args[1].toLowerCase(Locale.ROOT);
    if (!BlackholeManager.getInstance().exists(name)) {
      sendError(player, "No blackhole named &#FFFFFF" + name + "&#c.");
      return;
    }
    switch (args[2].toLowerCase(Locale.ROOT)) {
      case "setheight" -> handleHoloSetHeight(player, name, args);
      case "setline2" -> handleHoloSetLine2(player, name, args);
      case "setcolor" -> handleHoloSetColor(player, name, args);
      case "setscale" -> handleHoloSetScale(player, name, args);
      case "reset" -> handleHoloReset(player, name);
      default ->
          sendError(
              player,
              "Unknown hologram subcommand. Use: setheight, setline2, setcolor, setscale, reset");
    }
  }

  /** Handles holo set height. */
  private void handleHoloSetHeight(Player player, String name, String[] args) {
    if (args.length < 4) {
      sendError(player, "Usage: &#FFFFFF/e blackhole hologram " + name + " setheight <offset>");
      return;
    }
    double offset;
    try {
      offset = Double.parseDouble(args[3]);
    } catch (NumberFormatException e) {
      sendError(player, "Invalid height — must be a number.");
      return;
    }
    BlackholeHologramManager.getInstance().setYOffset(name, offset);
    Messageable.sendSuccess(
        player,
        "&#FFFFFF" + name + " &#00FFFFhologram height set to &#FFFFFF" + fmt(offset) + "&#00FFFF.");
  }

  /** Handles holo set line2. */
  private void handleHoloSetLine2(Player player, String name, String[] args) {
    if (args.length < 4) {
      sendError(player, "Usage: &#FFFFFF/e blackhole hologram " + name + " setline2 <text>");
      return;
    }
    String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
    BlackholeHologramManager.getInstance().setLine2(name, text);
    Messageable.sendSuccess(
        player,
        "&#FFFFFF" + name + " &#00FFFFhologram line 2 set to &#FFFFFF" + text + "&#00FFFF.");
  }

  /** Handles holo set color. */
  private void handleHoloSetColor(Player player, String name, String[] args) {
    if (args.length < 4) {
      sendError(
          player,
          "Usage: &#FFFFFF/e blackhole hologram "
              + name
              + " setcolor <"
              + String.join("|", HOLO_COLORS)
              + ">");
      return;
    }
    String color = args[3].toLowerCase(Locale.ROOT);
    if (!HOLO_COLORS.contains(color)) {
      sendError(
          player,
          "Invalid color. Choose from: &#FFFFFF"
              + String.join("&#c, &#FFFFFF", HOLO_COLORS)
              + "&#c.");
      return;
    }
    BlackholeHologramManager.getInstance().setColor(name, color);
    Messageable.sendSuccess(
        player,
        "&#FFFFFF" + name + " &#00FFFFhologram color set to &#FFFFFF" + color + "&#00FFFF.");
  }

  /** Handles holo set scale. */
  private void handleHoloSetScale(Player player, String name, String[] args) {
    if (args.length < 4) {
      sendError(player, "Usage: &#FFFFFF/e blackhole hologram " + name + " setscale <scale>");
      return;
    }
    float scale;
    try {
      scale = Float.parseFloat(args[3]);
    } catch (NumberFormatException e) {
      sendError(player, "Invalid scale — must be a number.");
      return;
    }
    if (scale <= 0) {
      sendError(player, "Scale must be greater than 0.");
      return;
    }
    BlackholeHologramManager.getInstance().setScale(name, scale);
    Messageable.sendSuccess(
        player,
        "&#FFFFFF" + name + " &#00FFFFhologram scale set to &#FFFFFF" + fmt(scale) + "&#00FFFF.");
  }

  /** Handles holo reset. */
  private void handleHoloReset(Player player, String name) {
    BlackholeHologramManager.getInstance().resetConfig(name);
    Messageable.sendSuccess(player, "&#FFFFFF" + name + " &#00FFFFhologram reset to defaults.");
  }

  /** Handles list. */
  private void handleList(Player player) {
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#555555&l⚫ Blackholes");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    var all = BlackholeManager.getInstance().getAll();
    if (all.isEmpty()) {
      Messageable.sendRaw(player, "  &#888888No blackholes configured.");
    } else {
      for (Blackhole bh : all) {
        String link =
            bh.linkedTo() != null ? " &#444444↔ &#FFFFFF" + bh.linkedTo() : " &#888888(unlinked)";
        Messageable.sendRaw(
            player,
            "  &#FFFFFF"
                + bh.name()
                + link
                + " &#888888["
                + bh.world()
                + " "
                + fmt(bh.cx())
                + ","
                + fmt(bh.cy())
                + ","
                + fmt(bh.cz())
                + " tpr="
                + fmt(bh.teleportRadius())
                + " er="
                + fmt(bh.effectRadius())
                + "]");
      }
    }
    Messageable.sendRaw(player, "");
  }

  /** Handles reload. */
  private void handleReload(Player player) {
    BlackholeManager.getInstance().loadFromDisk();
    Messageable.sendSuccess(player, "Blackholes reloaded.");
  }

  /** Fmt. */
  private static String fmt(double v) {
    return String.format(Locale.US, "%.1f", v);
  }

  /** Sends usage. */
  private void sendUsage(Player player) {
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#555555&l⚫ Blackhole Commands");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole create <name> [r] &#444444» &#CCCCCCCreate at your location (r = teleport radius)");
    Messageable.sendRaw(
        player, "  &#888888/e blackhole remove <name> &#444444» &#CCCCCCRemove a blackhole");
    Messageable.sendRaw(
        player, "  &#888888/e blackhole link <a> <b> &#444444» &#CCCCCCLink two blackholes");
    Messageable.sendRaw(
        player, "  &#888888/e blackhole unlink <name> &#444444» &#CCCCCCBreak the link");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole setteleport <name> <r> &#444444» &#CCCCCCInner pull+teleport radius");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole seteffect <name> <r> &#444444» &#CCCCCCOuter effect/atmosphere radius");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole setgroundlevel <name> [y] &#444444» &#CCCCCCSet Y level (defaults to your feet)");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole hologram <name> setheight <n> &#444444» &#CCCCCCSet hologram Y offset");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole hologram <name> setline2 <text> &#444444» &#CCCCCCSet hologram second line");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole hologram <name> setcolor <color> &#444444» &#CCCCCCSet hologram title color");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole hologram <name> setscale <n> &#444444» &#CCCCCCSet hologram text scale");
    Messageable.sendRaw(
        player,
        "  &#888888/e blackhole hologram <name> reset &#444444» &#CCCCCCReset hologram to defaults");
    Messageable.sendRaw(
        player, "  &#888888/e blackhole list &#444444» &#CCCCCCList all blackholes");
    Messageable.sendRaw(player, "  &#888888/e blackhole reload &#444444» &#CCCCCCReload from disk");
    Messageable.sendRaw(player, "");
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
          "link",
          "unlink",
          "setteleport",
          "seteffect",
          "setgroundlevel",
          "hologram",
          "list",
          "reload");

    String sub = args[0].toLowerCase(Locale.ROOT);
    List<String> names =
        BlackholeManager.getInstance().getAll().stream()
            .map(Blackhole::name)
            .collect(Collectors.toList());

    if (args.length == 2
        && (sub.equals("remove")
            || sub.equals("link")
            || sub.equals("unlink")
            || sub.equals("setteleport")
            || sub.equals("seteffect")
            || sub.equals("setgroundlevel")
            || sub.equals("hologram"))) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return names.stream().filter(n -> n.startsWith(prefix)).collect(Collectors.toList());
    }

    if (args.length == 3 && sub.equals("link")) {
      String a = args[1].toLowerCase(Locale.ROOT), prefix = args[2].toLowerCase(Locale.ROOT);
      return names.stream()
          .filter(n -> !n.equals(a) && n.startsWith(prefix))
          .collect(Collectors.toList());
    }

    if (args.length == 3 && (sub.equals("setteleport") || sub.equals("seteffect")))
      return List.of("5", "8", "10", "15", "20", "30");

    if (args.length == 3 && sub.equals("hologram"))
      return List.of("setheight", "setline2", "setcolor", "setscale", "reset");

    if (args.length == 4 && sub.equals("hologram")) {
      String holoSub = args[2].toLowerCase(Locale.ROOT);
      if (holoSub.equals("setcolor")) return HOLO_COLORS;
      if (holoSub.equals("setscale")) return List.of("1.0", "1.5", "2.0", "2.5", "3.0");
      if (holoSub.equals("setheight")) return List.of("3", "5", "7", "10", "15");
    }

    return List.of();
  }
}
