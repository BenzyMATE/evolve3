package org.benski.evolv3.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.BeamManager;
import org.benski.evolv3.core.managers.SpawnerManager;
import org.benski.evolv3.core.managers.SpawnerManager.Spawner;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Subcommand handler for {@code /e spawner ...}.
 *
 * <ul>
 *   <li>{@code setcontent <mob> [interval] [chance]} — tags the held SPAWNER item
 *   <li>{@code give <mob> [interval] [chance]} — gives a configured SPAWNER item
 *   <li>{@code list} — lists all active spawners
 *   <li>{@code remove} — removes the looked-at spawner
 *   <li>{@code reload} — reloads spawner data from disk
 *   <li>{@code info <id>} — shows details of one spawner
 * </ul>
 *
 * <p>This class is registered as a delegate of {@code /e} via {@link
 * org.benski.evolv3.commands.Evolv3Command}.
 */
public class SpawnerCommand extends BaseCommand {
  /** PDC keys for tagging spawner item stacks. */
  private static final NamespacedKey KEY_MOB = new NamespacedKey("evolv3", "spawner_mob");

  private static final NamespacedKey KEY_INTERVAL = new NamespacedKey("evolv3", "spawner_interval");
  private static final NamespacedKey KEY_CHANCE = new NamespacedKey("evolv3", "spawner_chance");
  private static final NamespacedKey KEY_NAME = new NamespacedKey("evolv3", "spawner_name");

  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  /** Mob types offered for tab completion — hostile and passive. */
  private static final List<String> COMMON_MOBS =
      List.of(
          "ZOMBIE",
          "SKELETON",
          "SPIDER",
          "CREEPER",
          "ENDERMAN",
          "BLAZE",
          "PIGLIN",
          "WITHER_SKELETON",
          "CAVE_SPIDER",
          "SILVERFISH",
          "WITCH",
          "GUARDIAN",
          "ELDER_GUARDIAN",
          "SHULKER",
          "PHANTOM",
          "DROWNED",
          "HUSK",
          "STRAY",
          "VINDICATOR",
          "PILLAGER",
          "RAVAGER",
          "PIG",
          "COW",
          "CHICKEN",
          "SHEEP",
          "MOOSHROOM",
          "RABBIT",
          "HORSE",
          "DONKEY",
          "MULE",
          "LLAMA",
          "TURTLE",
          "FOX",
          "PANDA",
          "BEE",
          "GOAT",
          "FROG",
          "SNIFFER",
          "ARMADILLO",
          "WOLF",
          "CAT",
          "OCELOT",
          "POLAR_BEAR",
          "DOLPHIN",
          "IRON_GOLEM");

  public SpawnerCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "spawner";
  }

  @Override
  public @Nullable String getDescription() {
    return "Manage custom spawners";
  }

  @Override
  public @Nullable String getUsage() {
    return "/e spawner <setcontent|give|list|remove|reload|info>";
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
      sendHelp(player);
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "setcontent" -> handleSetContent(player, args);
      case "give" -> handleGive(player, args);
      case "set" -> handleSet(player, args);
      case "hologram" -> handleHologram(player, args);
      case "list" -> handleList(player);
      case "remove" -> handleRemove(player);
      case "reload" -> handleReload(player);
      case "info" -> handleInfo(player, args);
      default -> sendHelp(player);
    }
    return true;
  }

  private void handleSetContent(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e spawner setcontent <mob> <name> [interval] [chance]");
      return;
    }

    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType() != Material.SPAWNER) {
      sendError(player, "You must be holding a &#FFFFFFSPAWNER&#c to use this command.");
      return;
    }

    EntityType type = parseEntityType(player, args[1]);
    if (type == null) return;

    String name = args[2].replace('_', ' ');
    int interval = parseIntOr(args, 3, 30, player);
    double chance = parseDoubleOr(args, 4, 80.0, player);

    applyTagsToItem(held, type.name(), name, interval, chance);
    player.getInventory().setItemInMainHand(held);
    Messageable.sendRaw(
        player,
        "  &#00FFFFSpawner configured: &#FFFFFF"
            + name
            + " &#888888("
            + formatMobName(type.name())
            + ") &#888888| &#FFFFFF"
            + interval
            + "s &#888888| &#FFFFFF"
            + chance
            + "%");
  }

  private void handleGive(Player player, String[] args) {
    if (args.length < 3) {
      sendError(player, "Usage: &#FFFFFF/e spawner give <mob> <name> [interval] [chance]");
      return;
    }

    EntityType type = parseEntityType(player, args[1]);
    if (type == null) return;

    String name = args[2].replace('_', ' ');
    int interval = parseIntOr(args, 3, 30, player);
    double chance = parseDoubleOr(args, 4, 80.0, player);

    ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
    applyTagsToItem(spawnerItem, type.name(), name, interval, chance);

    player.getInventory().addItem(spawnerItem);
    Messageable.sendRaw(
        player,
        "  &#00FFFFGiven &#FFFFFF"
            + name
            + " &#888888("
            + formatMobName(type.name())
            + ") &#888888| &#FFFFFF"
            + interval
            + "s &#888888| &#FFFFFF"
            + chance
            + "%");
  }

  private void handleHologram(Player player, String[] args) {
    if (args.length < 3) {
      sendError(
          player,
          "Usage: &#FFFFFF/e spawner hologram <@look|id> <setpos|resetpos|scale|billboard|setfacing|color|arrow>");
      return;
    }

    Spawner s = resolveSpawner(player, args[1]);
    if (s == null) return;

    String sub = args[2].toLowerCase(Locale.ROOT);
    org.benski.evolv3.core.managers.SpawnerHologramManager hm =
        org.benski.evolv3.core.managers.SpawnerHologramManager.getInstance();

    switch (sub) {
      case "setpos" -> {
        Location loc = player.getLocation();
        hm.setPosition(s.id(), loc.getX(), loc.getY(), loc.getZ());
        Messageable.sendRaw(
            player,
            "  &#00FFFFHologram position set &#888888[&#FFFFFF" + s.spawnerName() + "&#888888]");
      }
      case "resetpos" -> {
        hm.resetPosition(s.id());
        Messageable.sendRaw(
            player,
            "  &#00FFFFHologram position reset &#888888[&#FFFFFF" + s.spawnerName() + "&#888888]");
      }
      case "scale" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e spawner hologram <id> scale <value>");
          return;
        }
        try {
          float scale = Float.parseFloat(args[3]);
          hm.setScale(s.id(), scale);
          Messageable.sendRaw(
              player,
              "  &#00FFFFScale &#888888→ &#FFFFFF"
                  + scale
                  + " &#888888[&#FFFFFF"
                  + s.spawnerName()
                  + "&#888888]");
        } catch (NumberFormatException e) {
          sendError(player, "Invalid scale: &#FFFFFF" + args[3]);
        }
      }
      case "billboard" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e spawner hologram <id> billboard <true|false>");
          return;
        }
        boolean faces = Boolean.parseBoolean(args[3]);
        hm.setBillboard(s.id(), faces);
        Messageable.sendRaw(
            player,
            "  &#00FFFFBillboard &#888888→ &#FFFFFF"
                + (faces ? "faces player" : "fixed")
                + " &#888888[&#FFFFFF"
                + s.spawnerName()
                + "&#888888]");
      }
      case "setfacing" -> {
        float yaw = (player.getLocation().getYaw() + 180f) % 360f;
        hm.setFacing(s.id(), yaw);
        Messageable.sendRaw(
            player, "  &#00FFFFFacing set &#888888[&#FFFFFF" + s.spawnerName() + "&#888888]");
      }
      case "color" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e spawner hologram <id> color <color|reset>");
          return;
        }
        String colorArg = args[3].toLowerCase(Locale.ROOT);
        if (colorArg.equals("reset")) {
          hm.setColor(s.id(), "");
          Messageable.sendRaw(
              player, "  &#00FFFFColor reset &#888888[&#FFFFFF" + s.spawnerName() + "&#888888]");
        } else if (BeamManager.getColorNames().contains(colorArg)) {
          hm.setColor(s.id(), colorArg);
          Messageable.sendRaw(
              player,
              "  &#00FFFFColor &#888888→ &#FFFFFF"
                  + colorArg
                  + " &#888888[&#FFFFFF"
                  + s.spawnerName()
                  + "&#888888]");
        } else {
          sendError(
              player,
              "Unknown color. Use: &#FFFFFF" + String.join(", ", BeamManager.getColorNames()));
        }
      }
      case "arrow" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e spawner hologram <id> arrow <true|false>");
          return;
        }
        boolean show = Boolean.parseBoolean(args[3]);
        hm.setShowArrow(s.id(), show);
        Messageable.sendRaw(
            player,
            "  &#00FFFFArrow "
                + (show ? "&#00FFFFon" : "&#888888off")
                + " &#888888[&#FFFFFF"
                + s.spawnerName()
                + "&#888888]");
      }
      case "icon" -> {
        if (args.length < 4) {
          sendError(player, "Usage: &#FFFFFF/e spawner hologram <id> icon <emoji|reset>");
          return;
        }
        String icon = args[3].equalsIgnoreCase("reset") ? "" : args[3];
        hm.setIcon(s.id(), icon);
        Messageable.sendRaw(
            player,
            "  &#00FFFFIcon &#888888→ &#FFFFFF"
                + (icon.isEmpty() ? "(default)" : icon)
                + " &#888888[&#FFFFFF"
                + s.spawnerName()
                + "&#888888]");
      }
      default ->
          sendError(
              player,
              "Unknown option: &#FFFFFF"
                  + sub
                  + " &#c— use setpos, resetpos, scale, billboard, setfacing, color, arrow, icon");
    }
  }

  private void handleSet(Player player, String[] args) {
    if (args.length < 4) {
      sendError(player, "Usage: &#FFFFFF/e spawner set <@look|id> <setting> <value>");
      Messageable.sendRaw(
          player,
          "  &#888888Settings: &#FFFFFFmob&#888888, &#FFFFFFname&#888888, &#FFFFFFinterval&#888888, "
              + "&#FFFFFFchance&#888888, &#FFFFFFmob-limit&#888888, &#FFFFFFsearch-radius");
      return;
    }

    Spawner s = resolveSpawner(player, args[1]);
    if (s == null) return;

    String setting = args[2].toLowerCase(Locale.ROOT);
    String value = args[3];

    Spawner updated;
    String displaySetting;
    String displayValue;

    try {
      switch (setting) {
        case "mob" -> {
          EntityType type = parseEntityType(player, value);
          if (type == null) return;
          updated =
              new Spawner(
                  s.id(),
                  s.world(),
                  s.x(),
                  s.y(),
                  s.z(),
                  type.name(),
                  s.intervalSeconds(),
                  s.spawnChance(),
                  s.mobLimit(),
                  s.searchRadius(),
                  s.spawnerName());
          displaySetting = "Mob";
          displayValue = formatMobName(type.name());

          org.bukkit.World world = org.bukkit.Bukkit.getWorld(s.world());
          if (world != null) {
            org.bukkit.block.Block block = world.getBlockAt(s.x(), s.y(), s.z());
            if (block.getState() instanceof org.bukkit.block.CreatureSpawner cs) {
              cs.setSpawnedType(type);
              cs.update(true);
            }
          }
        }
        case "name" -> {
          String newName = value.replace('_', ' ');
          updated =
              new Spawner(
                  s.id(),
                  s.world(),
                  s.x(),
                  s.y(),
                  s.z(),
                  s.mobType(),
                  s.intervalSeconds(),
                  s.spawnChance(),
                  s.mobLimit(),
                  s.searchRadius(),
                  newName);
          displaySetting = "Name";
          displayValue = newName;
        }
        case "interval" -> {
          int iv = Integer.parseInt(value);
          if (iv < 1) {
            sendError(player, "Interval must be at least 1 second.");
            return;
          }
          updated =
              new Spawner(
                  s.id(),
                  s.world(),
                  s.x(),
                  s.y(),
                  s.z(),
                  s.mobType(),
                  iv,
                  s.spawnChance(),
                  s.mobLimit(),
                  s.searchRadius(),
                  s.spawnerName());
          displaySetting = "Interval";
          displayValue = iv + "s";
        }
        case "chance" -> {
          double ch = Double.parseDouble(value);
          if (ch < 0 || ch > 100) {
            sendError(player, "Chance must be 0–100.");
            return;
          }
          updated =
              new Spawner(
                  s.id(),
                  s.world(),
                  s.x(),
                  s.y(),
                  s.z(),
                  s.mobType(),
                  s.intervalSeconds(),
                  ch,
                  s.mobLimit(),
                  s.searchRadius(),
                  s.spawnerName());
          displaySetting = "Spawn chance";
          displayValue = ch + "%";
        }
        case "mob-limit" -> {
          int lim = Integer.parseInt(value);
          if (lim < 0) {
            sendError(player, "Mob limit cannot be negative.");
            return;
          }
          updated =
              new Spawner(
                  s.id(),
                  s.world(),
                  s.x(),
                  s.y(),
                  s.z(),
                  s.mobType(),
                  s.intervalSeconds(),
                  s.spawnChance(),
                  lim,
                  s.searchRadius(),
                  s.spawnerName());
          displaySetting = "Mob limit";
          displayValue = String.valueOf(lim);
        }
        case "search-radius" -> {
          int rad = Integer.parseInt(value);
          if (rad < 1) {
            sendError(player, "Search radius must be at least 1.");
            return;
          }
          updated =
              new Spawner(
                  s.id(),
                  s.world(),
                  s.x(),
                  s.y(),
                  s.z(),
                  s.mobType(),
                  s.intervalSeconds(),
                  s.spawnChance(),
                  s.mobLimit(),
                  rad,
                  s.spawnerName());
          displaySetting = "Search radius";
          displayValue = rad + " blocks";
        }
        default -> {
          sendError(
              player,
              "Unknown setting: &#FFFFFF"
                  + setting
                  + " &#c— use mob, interval, chance, mob-limit, search-radius");
          return;
        }
      }
    } catch (NumberFormatException e) {
      sendError(player, "Invalid number: &#FFFFFF" + value);
      return;
    }

    SpawnerManager.getInstance().updateSpawner(updated);
    Messageable.sendRaw(
        player,
        "  &#00FFFF"
            + displaySetting
            + " &#888888→ &#FFFFFF"
            + displayValue
            + " &#888888[&#FFFFFF"
            + s.spawnerName()
            + "&#888888]");
  }

  /**
   * Resolves a spawner from "@look" (targeted block) or a spawner name (case-insensitive).
   *
   * @return the spawner, or null (error already sent)
   */
  private Spawner resolveSpawner(Player player, String arg) {
    if (arg.equalsIgnoreCase("@look")) {
      org.bukkit.block.Block target = player.getTargetBlockExact(5);
      if (target == null || target.getType() != org.bukkit.Material.SPAWNER) {
        sendError(player, "Look at a spawner block within 5 blocks.");
        return null;
      }
      Spawner s = SpawnerManager.getInstance().getSpawnerAt(target.getLocation());
      if (s == null) {
        sendError(player, "No registered spawner at that block.");
        return null;
      }
      return s;
    }

    Spawner s = SpawnerManager.getInstance().getSpawnerByName(arg);
    if (s != null) return s;

    String lower = arg.toLowerCase(Locale.ROOT);
    for (Spawner candidate : SpawnerManager.getInstance().getAllSpawners().values()) {
      if (candidate.spawnerName().toLowerCase(Locale.ROOT).startsWith(lower)) return candidate;
    }

    sendError(player, "No spawner found: &#FFFFFF" + arg + " &#c— use @look or spawner name");
    return null;
  }

  private void handleList(Player player) {
    Map<String, Spawner> all = SpawnerManager.getInstance().getAllSpawners();
    if (all.isEmpty()) {
      Messageable.sendRaw(player, "  &#888888No active spawners.");
      return;
    }

    Messageable.sendRaw(player, "");
    Messageable.sendRaw(
        player, "  &#FF5555&l⚔ Spawners &#888888(&#FFFFFF" + all.size() + "&#888888)");
    Messageable.sendRaw(player, "  &#444444&m                                    ");

    for (Spawner s : all.values()) {
      int nearby = SpawnerManager.getInstance().countNearbyMobs(s);
      Messageable.sendRaw(
          player,
          "  &#FFFFFF"
              + s.spawnerName()
              + " &#888888| &#FF5555"
              + formatMobName(s.mobType())
              + " &#888888| &#CCCCCC"
              + s.world()
              + " "
              + s.x()
              + ","
              + s.y()
              + ","
              + s.z()
              + " &#888888| &#FFFFFFNearby: "
              + nearby
              + "/"
              + s.mobLimit());
    }
    Messageable.sendRaw(player, "");
  }

  private void handleRemove(Player player) {
    org.bukkit.block.Block target = player.getTargetBlockExact(5);
    if (target == null) {
      sendError(player, "No block in range — look at a spawner block.");
      return;
    }

    org.bukkit.Location loc = target.getLocation();
    Spawner existing = SpawnerManager.getInstance().getSpawnerAt(loc);
    if (existing == null) {
      sendError(player, "No registered spawner at that location.");
      return;
    }

    SpawnerManager.getInstance().removeSpawnerAt(loc);
    org.benski.evolv3.core.managers.SpawnerHologramManager.getInstance()
        .removeHologram(existing.id());
    Messageable.sendRaw(
        player, "  &#00FFFFSpawner &#FFFFFF" + existing.spawnerName() + " &#00FFFFremoved.");
  }

  private void handleReload(Player player) {
    SpawnerManager.getInstance().loadFromConfig();
    org.benski.evolv3.core.managers.SpawnerHologramManager.getInstance().cleanup();
    org.benski.evolv3.core.managers.SpawnerHologramManager.getInstance().initialize();
    Messageable.sendRaw(
        player,
        "  &#00FFFFSpawners reloaded. &#888888("
            + SpawnerManager.getInstance().getAllSpawners().size()
            + " loaded)");
  }

  private void handleInfo(Player player, String[] args) {
    if (args.length < 2) {
      sendError(player, "Usage: &#FFFFFF/e spawner info <id>");
      return;
    }

    String nameArg = args[1];
    Spawner s = SpawnerManager.getInstance().getSpawnerByName(nameArg);
    if (s == null) {
      String lower = nameArg.toLowerCase(Locale.ROOT);
      for (Spawner candidate : SpawnerManager.getInstance().getAllSpawners().values()) {
        if (candidate.spawnerName().toLowerCase(Locale.ROOT).startsWith(lower)) {
          s = candidate;
          break;
        }
      }
    }
    if (s == null) {
      sendError(player, "No spawner found: &#FFFFFF" + nameArg);
      return;
    }

    int nearby = SpawnerManager.getInstance().countNearbyMobs(s);
    long secs = SpawnerManager.getInstance().getSecondsUntilSpawn(s.id());

    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#FF5555&l⚔ Spawner Info");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    Messageable.sendRaw(player, "  &#888888ID            &#FFFFFF" + s.id());
    Messageable.sendRaw(player, "  &#888888Name          &#FFFFFF" + s.spawnerName());
    Messageable.sendRaw(player, "  &#888888Mob           &#FF5555" + formatMobName(s.mobType()));
    Messageable.sendRaw(
        player,
        "  &#888888Location      &#FFFFFF" + s.world() + " " + s.x() + "," + s.y() + "," + s.z());
    Messageable.sendRaw(player, "  &#888888Interval      &#FFFFFF" + s.intervalSeconds() + "s");
    Messageable.sendRaw(player, "  &#888888Chance        &#FFFFFF" + s.spawnChance() + "%");
    Messageable.sendRaw(player, "  &#888888Mob limit     &#FFFFFF" + s.mobLimit());
    Messageable.sendRaw(
        player,
        "  &#888888Hunters       &#FFFFFF" + SpawnerManager.getInstance().countPlayersInRegion(s));
    Messageable.sendRaw(player, "  &#888888Remaining     &#FFFFFF" + nearby);
    Messageable.sendRaw(player, "  &#888888Next spawn    &#FFFFFF" + secs + "s");
    Messageable.sendRaw(player, "");
  }

  /**
   * Applies PDC data, display name, and lore to a SPAWNER item stack.
   *
   * @param item the item to configure (modified in place)
   * @param mobName EntityType name (e.g. "ZOMBIE")
   * @param name display name shown in the hologram
   * @param interval spawn interval in seconds
   * @param chance spawn chance 0–100
   */
  private void applyTagsToItem(
      @NotNull ItemStack item,
      @NotNull String mobName,
      @NotNull String name,
      int interval,
      double chance) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;

    meta.getPersistentDataContainer().set(KEY_MOB, PersistentDataType.STRING, mobName);
    meta.getPersistentDataContainer().set(KEY_NAME, PersistentDataType.STRING, name);
    meta.getPersistentDataContainer().set(KEY_INTERVAL, PersistentDataType.INTEGER, interval);
    meta.getPersistentDataContainer().set(KEY_CHANCE, PersistentDataType.DOUBLE, chance);

    String displayName =
        net.matrixcreations.libraries.MatrixColorAPI.process("&6⚙ " + name + " Spawner");
    meta.displayName(LEGACY.deserialize(displayName));

    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
    lore.add(
        LEGACY.deserialize(
            net.matrixcreations.libraries.MatrixColorAPI.process("&#888888Name: &#FFFFFF" + name)));
    lore.add(
        LEGACY.deserialize(
            net.matrixcreations.libraries.MatrixColorAPI.process(
                "&#888888Mob: &#FFFFFF" + formatMobName(mobName))));
    lore.add(
        LEGACY.deserialize(
            net.matrixcreations.libraries.MatrixColorAPI.process(
                "&#888888Interval: &#FFFFFF" + interval + "s")));
    lore.add(
        LEGACY.deserialize(
            net.matrixcreations.libraries.MatrixColorAPI.process(
                "&#888888Chance: &#FFFFFF" + chance + "%")));
    lore.add(
        LEGACY.deserialize(
            net.matrixcreations.libraries.MatrixColorAPI.process("&#888888Place to activate")));
    meta.lore(lore);

    if (meta instanceof BlockStateMeta bsm) {
      org.bukkit.block.BlockState state = bsm.getBlockState();
      if (state instanceof CreatureSpawner cs) {
        try {
          cs.setSpawnedType(EntityType.valueOf(mobName.toUpperCase()));
          bsm.setBlockState(cs);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }

    item.setItemMeta(meta);
  }

  private @Nullable EntityType parseEntityType(@NotNull Player player, @NotNull String name) {
    try {
      return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      sendError(player, "Unknown mob type: &#FFFFFF" + name);
      return null;
    }
  }

  private int parseIntOr(@NotNull String[] args, int index, int fallback, @NotNull Player player) {
    if (args.length <= index) return fallback;
    try {
      return Integer.parseInt(args[index]);
    } catch (NumberFormatException e) {
      sendError(player, "Invalid number: &#FFFFFF" + args[index]);
      return fallback;
    }
  }

  private double parseDoubleOr(
      @NotNull String[] args, int index, double fallback, @NotNull Player player) {
    if (args.length <= index) return fallback;
    try {
      return Double.parseDouble(args[index]);
    } catch (NumberFormatException e) {
      sendError(player, "Invalid number: &#FFFFFF" + args[index]);
      return fallback;
    }
  }

  /** Converts "ZOMBIE_PIGMAN" → "Zombie Pigman". */
  private static @NotNull String formatMobName(@NotNull String entityTypeName) {
    StringBuilder sb = new StringBuilder();
    for (String word : entityTypeName.split("_")) {
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
    }
    return sb.toString();
  }

  private void sendHelp(@NotNull Player player) {
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(player, "  &#FF5555&l⚔ Spawner Commands");
    Messageable.sendRaw(player, "  &#444444&m                                    ");
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(
        player,
        "  &#888888/e spawner setcontent <mob> <name> [interval] [chance] &#444444» &#CCCCCCTag held spawner");
    Messageable.sendRaw(
        player,
        "  &#888888/e spawner give <mob> <name> [interval] [chance] &#444444» &#CCCCCCGive configured spawner");
    Messageable.sendRaw(
        player,
        "  &#888888/e spawner set <@look|id> <setting> <value> &#444444» &#CCCCCCEdit placed spawner");
    Messageable.sendRaw(
        player, "  &#888888  Settings: mob, name, interval, chance, mob-limit, search-radius");
    Messageable.sendRaw(
        player,
        "  &#888888/e spawner hologram <@look|id> <option> &#444444» &#CCCCCCEdit hologram");
    Messageable.sendRaw(
        player, "  &#888888  Options: setpos, resetpos, scale, billboard, setfacing, color, arrow");
    Messageable.sendRaw(
        player, "  &#888888/e spawner list &#444444» &#CCCCCCList all active spawners");
    Messageable.sendRaw(
        player, "  &#888888/e spawner remove &#444444» &#CCCCCCRemove looked-at spawner");
    Messageable.sendRaw(player, "  &#888888/e spawner reload &#444444» &#CCCCCCReload from config");
    Messageable.sendRaw(
        player, "  &#888888/e spawner info <id> &#444444» &#CCCCCCView spawner details");
    Messageable.sendRaw(player, "");
  }

  private static final List<String> SUBCOMMANDS =
      List.of("setcontent", "give", "set", "hologram", "list", "remove", "reload", "info");

  private static final List<String> SET_SETTINGS =
      List.of("mob", "name", "interval", "chance", "mob-limit", "search-radius");

  private static final List<String> HOLOGRAM_OPTIONS =
      List.of("setpos", "resetpos", "scale", "billboard", "setfacing", "color", "arrow", "icon");

  private static final List<String> INTERVAL_SUGGESTIONS = List.of("10", "20", "30", "60", "120");
  private static final List<String> CHANCE_SUGGESTIONS = List.of("25", "50", "75", "100");

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      return SUBCOMMANDS.stream()
          .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
          .collect(Collectors.toList());
    }

    String sub = args[0].toLowerCase(Locale.ROOT);

    if (args.length == 2 && (sub.equals("setcontent") || sub.equals("give"))) {
      String prefix = args[1].toUpperCase(Locale.ROOT);
      return COMMON_MOBS.stream().filter(m -> m.startsWith(prefix)).collect(Collectors.toList());
    }

    if (args.length == 2 && (sub.equals("info") || sub.equals("set") || sub.equals("hologram"))) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      List<String> names =
          SpawnerManager.getInstance().getAllSpawners().values().stream()
              .map(Spawner::spawnerName)
              .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
              .collect(Collectors.toList());
      if ((sub.equals("set") || sub.equals("hologram")) && "@look".startsWith(prefix))
        names.add(0, "@look");
      return names;
    }

    if (args.length == 3 && sub.equals("hologram")) {
      String prefix = args[2].toLowerCase(Locale.ROOT);
      return HOLOGRAM_OPTIONS.stream()
          .filter(o -> o.startsWith(prefix))
          .collect(Collectors.toList());
    }

    if (args.length == 4 && sub.equals("hologram")) {
      return switch (args[2].toLowerCase(Locale.ROOT)) {
        case "scale" ->
            List.of("1.0", "2.0", "3.0", "4.0", "5.0").stream()
                .filter(v -> v.startsWith(args[3]))
                .collect(Collectors.toList());
        case "billboard" ->
            List.of("true", "false").stream()
                .filter(v -> v.startsWith(args[3]))
                .collect(Collectors.toList());
        case "arrow" ->
            List.of("true", "false").stream()
                .filter(v -> v.startsWith(args[3]))
                .collect(Collectors.toList());
        case "color" -> {
          List<String> colors = new ArrayList<>(BeamManager.getColorNames());
          colors.add("reset");
          yield colors.stream()
              .filter(c -> c.startsWith(args[3].toLowerCase(Locale.ROOT)))
              .collect(Collectors.toList());
        }
        case "icon" ->
            List.of("reset").stream()
                .filter(v -> v.startsWith(args[3].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        default -> List.of();
      };
    }

    if (args.length == 3 && sub.equals("set")) {
      String prefix = args[2].toLowerCase(Locale.ROOT);
      return SET_SETTINGS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
    }

    if (args.length == 4 && sub.equals("set")) {
      String setting = args[2].toLowerCase(Locale.ROOT);
      return switch (setting) {
        case "mob" ->
            COMMON_MOBS.stream()
                .filter(m -> m.startsWith(args[3].toUpperCase(Locale.ROOT)))
                .collect(Collectors.toList());
        case "interval" ->
            List.of("10", "20", "30", "60", "120").stream()
                .filter(v -> v.startsWith(args[3]))
                .collect(Collectors.toList());
        case "chance" ->
            List.of("25", "50", "75", "100").stream()
                .filter(v -> v.startsWith(args[3]))
                .collect(Collectors.toList());
        case "mob-limit" ->
            List.of("1", "5", "10", "20", "50").stream()
                .filter(v -> v.startsWith(args[3]))
                .collect(Collectors.toList());
        case "search-radius" ->
            List.of("8", "16", "32", "64").stream()
                .filter(v -> v.startsWith(args[3]))
                .collect(Collectors.toList());
        default -> List.of();
      };
    }

    if (args.length == 4 && (sub.equals("setcontent") || sub.equals("give"))) {
      return INTERVAL_SUGGESTIONS.stream()
          .filter(s -> s.startsWith(args[3]))
          .collect(Collectors.toList());
    }

    if (args.length == 5 && (sub.equals("setcontent") || sub.equals("give"))) {
      return CHANCE_SUGGESTIONS.stream()
          .filter(s -> s.startsWith(args[4]))
          .collect(Collectors.toList());
    }

    return List.of();
  }
}
