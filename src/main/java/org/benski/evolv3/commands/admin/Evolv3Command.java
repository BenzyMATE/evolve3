package org.benski.evolv3.commands.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.benski.evolv3.Main;
import org.benski.evolv3.commands.player.StatisticCommand;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.items.Scroll;
import org.benski.evolv3.menus.pickaxe.PickaxeMenu;
import org.benski.evolv3.menus.pickaxe.StoneSocketMenu;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main admin command for the Evolv3 plugin. All admin subcommands are consolidated here: mine,
 * border, beam, launchpad, rank, etc.
 *
 * <p>Usage: /evolve (or /e) &lt;subcommand&gt; [args]
 */
public class Evolv3Command extends BaseCommand {
  /** Delegated subcommands — each receives shifted args. */
  private final Map<String, BaseCommand> delegates = new LinkedHashMap<>();

  /** Rank subcommands: set, staff, addperm, removeperm, listperms. */
  private BaseCommand setRankCommand;

  private BaseCommand setStaffCommand;
  private BaseCommand rankPermCommand;

  private static final List<String> RANK_SUBS =
      List.of("set", "staff", "addperm", "removeperm", "listperms");

  /**
   * Creates the Evolv3Command; delegates are initialized in {@link #initialize()}. @param plugin
   * the owning plugin
   */
  public Evolv3Command(JavaPlugin plugin) {
    super(plugin);
  }

  /** Initializes this component. */
  @Override
  public void initialize() {
    delegates.put("mine", new MineCommand(plugin));
    delegates.put("border", new BorderCommand(plugin));
    delegates.put("beam", new BeamCommand(plugin));
    delegates.put("launchpad", new LaunchPadCommand(plugin));
    delegates.put("spawner", new SpawnerCommand(plugin));
    delegates.put("statistic", new StatisticCommand(plugin));
    delegates.put("blackhole", new BlackholeCommand(plugin));
    delegates.put("cell", new CellAdminCommand(plugin));
    delegates.put("island", new IslandAdminCommand(plugin));

    setRankCommand = new SetRankCommand(plugin);
    setStaffCommand = new SetStaffCommand(plugin);
    rankPermCommand = new RankPermCommand(plugin);

    delegates.values().forEach(BaseCommand::initialize);
    setRankCommand.initialize();
    setStaffCommand.initialize();
    rankPermCommand.initialize();
  }

  /** Returns the command name. */
  @Override
  public String getCommandName() {
    return "evolve";
  }

  @Override
  public @Nullable String getDescription() {
    return "Main admin command for the Evolv3 plugin";
  }

  @Override
  public @Nullable String getUsage() {
    return "/e <help|reload|info|mine|border|beam|launchpad|spawner|rank|...>";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.admin";
  }

  @Override
  public @Nullable List<String> getAliases() {
    return List.of("e");
  }

  /** Execute. */
  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (args.length == 0) {
      showHelp(sender);
      return true;
    }

    String subCommand = args[0].toLowerCase();

    BaseCommand delegate = delegates.get(subCommand);
    if (delegate != null) {
      String[] shifted = Arrays.copyOfRange(args, 1, args.length);
      return delegate.execute(sender, command, label, shifted);
    }

    switch (subCommand) {
      case "help" -> showHelp(sender);
      case "reload" -> handleReload(sender, args);
      case "info" -> showInfo(sender);
      case "rank" -> handleRank(sender, command, label, args);
      case "backpack" -> handleBackpack(sender, args);
      case "resetpickaxes" -> handleResetPickaxes(sender, args);
      case "resetprofile" -> handleResetProfile(sender, args);
      case "stone" -> handleGiveStone(sender, args);
      case "setbackpack" -> handleSetBackpack(sender, args);
      case "scroll" -> handleScroll(sender, args);
      case "socketcalib" -> handleSocketCalib(sender, args);
      case "vesselcalib" -> handleVesselCalib(sender, args);
      default -> {
        sendError(sender, "Unknown subcommand: &#FFFFFF" + args[0]);
        sendInfo(sender, "Use &#FFFFFF/e help &#CCCCCCfor available commands.");
      }
    }

    return true;
  }

  /** Handles rank. */
  private void handleRank(CommandSender sender, Command command, String label, String[] args) {
    if (args.length < 2) {
      Messageable.sendRaw(sender, "");
      Messageable.sendRaw(sender, "  &#00FFFF&l👑 Rank Commands");
      Messageable.sendRaw(sender, "  &#444444&m                                    ");
      Messageable.sendRaw(sender, "");
      Messageable.sendRaw(
          sender, "  &#888888/e rank set <player> <rank> &#444444» &#CCCCCCSet prison rank");
      Messageable.sendRaw(
          sender, "  &#888888/e rank staff <player> <rank|clear> &#444444» &#CCCCCCSet staff rank");
      Messageable.sendRaw(
          sender, "  &#888888/e rank addperm <rank> <permission> &#444444» &#CCCCCCAdd permission");
      Messageable.sendRaw(
          sender,
          "  &#888888/e rank removeperm <rank> <permission> &#444444» &#CCCCCCRemove permission");
      Messageable.sendRaw(
          sender, "  &#888888/e rank listperms <rank> &#444444» &#CCCCCCList permissions");
      Messageable.sendRaw(sender, "");
      return;
    }

    String rankSub = args[1].toLowerCase();

    switch (rankSub) {
      case "set" -> {
        String[] shifted = Arrays.copyOfRange(args, 2, args.length);
        setRankCommand.execute(sender, command, label, shifted);
      }
      case "staff" -> {
        String[] shifted = Arrays.copyOfRange(args, 2, args.length);
        setStaffCommand.execute(sender, command, label, shifted);
      }
      case "addperm" -> {
        String[] rpArgs = buildRankPermArgs("add", args);
        rankPermCommand.execute(sender, command, label, rpArgs);
      }
      case "removeperm" -> {
        String[] rpArgs = buildRankPermArgs("remove", args);
        rankPermCommand.execute(sender, command, label, rpArgs);
      }
      case "listperms" -> {
        String[] rpArgs = buildRankPermArgs("list", args);
        rankPermCommand.execute(sender, command, label, rpArgs);
      }
      default -> {
        sendError(sender, "Unknown rank subcommand: &#FFFFFF" + rankSub);
        sendInfo(sender, "Use &#FFFFFF/e rank &#CCCCCCfor available options.");
      }
    }
  }

  /**
   * Builds args for RankPermCommand: prepends the action (add/remove/list) and appends args[2..]
   * (rank, permission, etc.).
   */
  private String[] buildRankPermArgs(String action, String[] args) {
    String[] remaining = Arrays.copyOfRange(args, 2, args.length);
    String[] result = new String[remaining.length + 1];
    result[0] = action;
    System.arraycopy(remaining, 0, result, 1, remaining.length);
    return result;
  }

  /** Shows help to the player. */
  private void showHelp(CommandSender sender) {
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    for (String line : messages.getStringList(ConfigPaths.Messages.Admin.HELP)) {
      sender.sendMessage(formatMessage(sender, line));
    }
  }

  /** Handles reload. */
  private void handleReload(CommandSender sender, String[] args) {
    ConfigRegistry.getInstance().cleanup();
    ConfigRegistry.getInstance().initialize();

    FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    String successMsg =
        messagesConfig.getString(
            ConfigPaths.Messages.General.RELOAD_SUCCESS, "&aConfiguration reloaded successfully!");
    sender.sendMessage(formatMessage(sender, successMsg));
  }

  /** Handles backpack. */
  @SuppressWarnings("deprecation")
  private void handleBackpack(CommandSender sender, String[] args) {
    if (args.length < 3 || !args[1].equalsIgnoreCase("model")) {
      sendInfo(sender, "Usage: &#FFFFFF/e backpack model <number> [player]");
      return;
    }

    int modelData;
    try {
      modelData = Integer.parseInt(args[2]);
      if (modelData < 0 || modelData > 99_999) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      sendError(sender, "Model data must be between &#FFFFFF0 &cand &#FFFFFF99999&c.");
      return;
    }

    Player target;
    if (args.length >= 4) {
      target = Bukkit.getPlayer(args[3]);
      if (target == null) {
        sendError(sender, "Player not found: &#FFFFFF" + args[3]);
        return;
      }
    } else {
      if (!requirePlayer(sender)) return;
      target = (Player) sender;
    }

    ItemStack offHand = target.getInventory().getItemInOffHand();
    NamespacedKey backpackKey = new NamespacedKey(Main.getInstance(), "backpack");

    if (offHand.getType() != Material.STICK
        || !offHand.hasItemMeta()
        || !offHand
            .getItemMeta()
            .getPersistentDataContainer()
            .has(backpackKey, PersistentDataType.BYTE)) {
      sendError(
          sender, "&#FFFFFF" + target.getName() + " &cdoes not have a backpack in their offhand.");
      return;
    }

    ItemMeta meta = offHand.getItemMeta();
    meta.setCustomModelData(modelData);
    offHand.setItemMeta(meta);

    sendSuccess(
        sender,
        "Set &#FFFFFF"
            + target.getName()
            + "&#00FFFF's backpack model to &#FFFFFF"
            + modelData
            + "&#00FFFF.");
  }

  /** Shows info to the player. */
  private void showInfo(CommandSender sender) {
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    for (String line : messages.getStringList(ConfigPaths.Messages.Admin.INFO)) {
      String processed = line.replace("%version%", plugin.getPluginMeta().getVersion());
      sender.sendMessage(formatMessage(sender, processed));
    }
  }

  /** Handles reset pickaxes. */
  private void handleResetPickaxes(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sendInfo(sender, "Usage: &#FFFFFF/e resetpickaxes <player>");
      return;
    }

    Player target = Bukkit.getPlayer(args[1]);
    if (target == null) {
      sendError(sender, "Player not found or not online: &#FFFFFF" + args[1]);
      return;
    }

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();

    PlayerProfile profile = playerDC.getLocal(target);
    if (profile == null) {
      sendError(sender, "&#FFFFFF" + target.getName() + "&c's profile is not loaded.");
      return;
    }

    List<String> pickaxeIds = new ArrayList<>();
    for (String entry : profile.storedPickaxes) {
      if (entry == null || entry.isEmpty()) continue;
      String[] parts = entry.split(":", 2);
      if (!parts[0].isEmpty()) pickaxeIds.add(parts[0]);
    }

    ItemStack heldItem = target.getInventory().getItemInMainHand();
    String heldId = ItemStackExtensions.getData(heldItem, "id");
    if (heldId != null && !heldId.isEmpty()) {
      pickaxeIds.add(heldId);
      ItemStackExtensions.setData(heldItem, "id", "");
      target.getInventory().setItemInMainHand(heldItem);
    }

    for (String id : pickaxeIds) {
      profile.removePickaxe(id);
    }

    profile.storedPickaxes.replaceAll(ignored -> "");
    profile.save(null);

    sendSuccess(
        sender,
        "Removed &#FFFFFF"
            + pickaxeIds.size()
            + " &#00FFFFpickaxe(s) from &#FFFFFF"
            + target.getName()
            + " &#00FFFFand reset their slot limit.");
  }

  /** Handles give stone. */
  private void handleGiveStone(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sendInfo(
          sender,
          "Usage: &#FFFFFF/e stone <Common|Uncommon|Rare|Epic|Legendary> [enchant] [player]");
      return;
    }

    boolean giveAll = args[1].equalsIgnoreCase("All");

    String rarityArg = args[1].substring(0, 1).toUpperCase() + args[1].substring(1).toLowerCase();
    RarityConfig rarity = giveAll ? null : EnchantAnimationConfig.getRarityByName(rarityArg);
    if (!giveAll && rarity == null) {
      sendError(
          sender,
          "Unknown rarity: &#FFFFFF"
              + args[1]
              + " &c— use Common, Uncommon, Rare, Epic, Legendary, or All.");
      return;
    }

    List<String> allEnchants = EnchantAnimationConfig.getEnchantOrder();
    String enchantName = null;
    Player target = null;

    if (args.length >= 3) {
      Player possiblePlayer = Bukkit.getPlayer(args[args.length - 1]);
      int enchantEndIndex =
          (possiblePlayer != null && args.length >= 4) ? args.length - 1 : args.length;

      if (possiblePlayer != null && enchantEndIndex == 2) {
        target = possiblePlayer;
      } else {
        String enchantArg = String.join(" ", Arrays.copyOfRange(args, 2, enchantEndIndex));
        final String search = enchantArg.toLowerCase();
        enchantName =
            allEnchants.stream()
                .filter(e -> e.toLowerCase().equals(search))
                .findFirst()
                .orElse(null);
        if (enchantName == null) {
          sendError(sender, "Unknown enchant: &#FFFFFF" + enchantArg);
          return;
        }
        if (possiblePlayer != null) target = possiblePlayer;
      }
    }

    if (enchantName == null) {
      enchantName =
          allEnchants.get(
              java.util.concurrent.ThreadLocalRandom.current().nextInt(allEnchants.size()));
    }

    if (target == null) {
      if (!requirePlayer(sender)) return;
      target = (Player) sender;
    }

    final Player finalTarget = target;
    final String finalEnchant = enchantName;

    if (giveAll) {
      List<String> rarityNames = List.of("Common", "Uncommon", "Rare", "Epic", "Legendary");
      for (String name : rarityNames) {
        RarityConfig rc = EnchantAnimationConfig.getRarityByName(name);
        if (rc == null) continue;
        var overflow = finalTarget.getInventory().addItem(EvolutionStone.create(rc, finalEnchant));
        overflow
            .values()
            .forEach(
                item -> finalTarget.getWorld().dropItemNaturally(finalTarget.getLocation(), item));
      }
      sendSuccess(
          sender,
          "Given &#FFFFFF"
              + finalTarget.getName()
              + " &#00FFFFall 5 Evolution Stones &#888888("
              + finalEnchant
              + ")&#00FFFF.");
    } else {
      var overflow =
          finalTarget.getInventory().addItem(EvolutionStone.create(rarity, finalEnchant));
      overflow
          .values()
          .forEach(
              item -> finalTarget.getWorld().dropItemNaturally(finalTarget.getLocation(), item));
      sendSuccess(
          sender,
          "Given &#FFFFFF"
              + finalTarget.getName()
              + " &#00FFFFa &#FFFFFF"
              + rarity.displayName()
              + " &#00FFFFEvolution Stone &#888888("
              + finalEnchant
              + ")&#00FFFF.");
    }
  }

  /** Handles reset profile. */
  private void handleResetProfile(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sendInfo(sender, "Usage: &#FFFFFF/e resetprofile <player>");
      return;
    }

    Player target = Bukkit.getPlayer(args[1]);
    if (target == null) {
      sendError(sender, "Player not found or not online: &#FFFFFF" + args[1]);
      return;
    }

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();

    UUID targetUuid = target.getUniqueId();
    List<String> pickaxeIds = new ArrayList<>();

    PlayerProfile profile = playerDC.getLocal(target);
    if (profile != null) {
      for (String entry : profile.storedPickaxes) {
        if (entry == null || entry.isEmpty()) continue;
        String[] parts = entry.split(":", 2);
        if (!parts[0].isEmpty()) pickaxeIds.add(parts[0]);
      }
    }

    playerDC.getAllPickaxes().stream()
        .filter(p -> targetUuid.equals(p.ownerUuid))
        .map(p -> p.key)
        .filter(k -> k != null && !k.isEmpty())
        .forEach(
            k -> {
              if (!pickaxeIds.contains(k)) pickaxeIds.add(k);
            });

    for (int i = 0; i < target.getInventory().getContents().length; i++) {
      ItemStack item = target.getInventory().getContents()[i];
      if (item == null || !item.getType().name().contains("PICKAXE")) continue;
      String id = ItemStackExtensions.getData(item, "id");
      if (id != null && !id.isEmpty()) {
        ItemStackExtensions.setData(item, "id", "");
        target.getInventory().setItem(i, item);
      }
    }

    if (profile != null) {
      for (String id : pickaxeIds) {
        profile.removePickaxe(id);
      }
    }

    // Reset to a fresh default profile and persist it to the API
    String playerId = targetUuid.toString();
    playerDC.getAll().remove(playerId);
    PlayerProfile freshProfile = PlayerProfile.createDefault(target);
    playerDC.getAll().put(playerId, freshProfile);
    freshProfile.save(null);

    sendSuccess(
        sender,
        "Reset &#FFFFFF"
            + target.getName()
            + "&#00FFFF's profile and deleted &#FFFFFF"
            + pickaxeIds.size()
            + " &#00FFFFpickaxe(s).");
  }

  /** Handles set backpack. */
  private void handleSetBackpack(CommandSender sender, String[] args) {
    if (args.length < 3) {
      sendInfo(sender, "Usage: &#FFFFFF/e setbackpack <player> <size>");
      return;
    }

    Player target = Bukkit.getPlayer(args[1]);
    if (target == null) {
      sendError(sender, "Player not found or not online: &#FFFFFF" + args[1]);
      return;
    }

    int size;
    try {
      size = Integer.parseInt(args[2]);
    } catch (NumberFormatException e) {
      sendError(sender, "Invalid size: &#FFFFFF" + args[2] + " &c— must be a whole number.");
      return;
    }

    if (size < 1 || size > 10_000) {
      sendError(sender, "Size must be between &#FFFFFF1 &cand &#FFFFFF10,000&c.");
      return;
    }

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) {
      sendError(sender, "Player data centre unavailable.");
      return;
    }

    PlayerProfile profile = playerDC.getLocal(target);
    if (profile == null) {
      sendError(sender, "Could not load profile for &#FFFFFF" + target.getName() + "&c.");
      return;
    }

    profile.backpack.setMaxSize(size);
    profile.save(null);

    sendSuccess(
        sender,
        "Set &#FFFFFF"
            + target.getName()
            + "&#00FFFF's backpack limit to &#FFFFFF"
            + size
            + " &#00FFFFitems.");
  }

  private static final List<String> SCROLL_TYPES =
      List.of("coins", "essence", "atoms", "pickaxe_xp", "backpack_slots");

  /** Handles scroll. */
  private void handleScroll(CommandSender sender, String[] args) {
    if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
      showScrollList(sender);
      return;
    }

    String typeArg = args[1].toLowerCase();
    String internalType =
        switch (typeArg) {
          case "coins" -> Scroll.COINS;
          case "essence" -> Scroll.ESSENCE;
          case "atoms" -> Scroll.ATOMS;
          case "pickaxe_xp" -> Scroll.PICKAXE_XP;
          case "backpack_slots" -> Scroll.BACKPACK_SLOTS;
          default -> null;
        };

    if (internalType == null) {
      sendError(sender, "Unknown scroll type: &#FFFFFF" + args[1]);
      sendInfo(sender, "Valid types: &#FFFFFFcoins, essence, atoms, pickaxe_xp, backpack_slots");
      return;
    }

    if (args.length < 3) {
      sendInfo(sender, "Usage: &#FFFFFF/e scroll <type> <amount> [player]");
      return;
    }

    int amount;
    try {
      amount = Integer.parseInt(args[2]);
      if (amount < 1 || amount > 1_000_000) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      sendError(sender, "Amount must be a positive whole number.");
      return;
    }

    Player target;
    if (args.length >= 4) {
      target = Bukkit.getPlayer(args[3]);
      if (target == null) {
        sendError(sender, "Player not found: &#FFFFFF" + args[3]);
        return;
      }
    } else {
      if (!requirePlayer(sender)) return;
      target = (Player) sender;
    }

    ItemStack scroll =
        switch (internalType) {
          case Scroll.COINS -> Scroll.createCoins(amount);
          case Scroll.ESSENCE -> Scroll.createEssence(amount);
          case Scroll.ATOMS -> Scroll.createAtoms(amount);
          case Scroll.PICKAXE_XP -> Scroll.createPickaxeXP(amount);
          case Scroll.BACKPACK_SLOTS -> Scroll.createBackpackSlots(amount);
          default -> throw new IllegalStateException("Unhandled scroll type: " + internalType);
        };

    target
        .getInventory()
        .addItem(scroll)
        .values()
        .forEach(overflow -> target.getWorld().dropItemNaturally(target.getLocation(), overflow));

    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    String givenMsg =
        messages != null
            ? messages.getString(
                ConfigPaths.Messages.Scroll.GIVEN,
                "  %evolv3_prefix% &#00FFFFGiven &#FFFFFF%player% &#00FFFFa scroll.")
            : "  %evolv3_prefix% &#00FFFFGiven &#FFFFFF%player% &#00FFFFa scroll.";
    sendSuccess(
        sender,
        givenMsg
            .replace("%player%", target.getName())
            .replace("%amount%", String.valueOf(amount))
            .replace("%type%", typeArg)
            .replace("%evolv3_prefix%", ""));
  }

  /** Shows scroll list to the player. */
  private void showScrollList(CommandSender sender) {
    Messageable.sendRaw(sender, "");
    Messageable.sendRaw(sender, "  &#FFD700&l✦ Scroll Types");
    Messageable.sendRaw(sender, "  &#444444&m                                    ");
    Messageable.sendRaw(sender, "");
    Messageable.sendRaw(
        sender, "  &#FFD700coins          &#888888» &#CCCCCCCoin voucher (combinable)");
    Messageable.sendRaw(sender, "  &#B366D9essence        &#888888» &#CCCCCCEssence voucher");
    Messageable.sendRaw(sender, "  &#FFEE00atoms          &#888888» &#CCCCCCAtom voucher");
    Messageable.sendRaw(
        sender, "  &#00FFFFpickaxe_xp     &#888888» &#CCCCCCPickaxe XP (drag onto pickaxe)");
    Messageable.sendRaw(
        sender, "  &#00FFFFbackpack_slots  &#888888» &#CCCCCCBackpack slot voucher");
    Messageable.sendRaw(sender, "");
    Messageable.sendRaw(sender, "  &#888888Usage: &#FFFFFF/e scroll <type> <amount> [player]");
    Messageable.sendRaw(sender, "");
  }

  /**
   * Temporary calibration command for {@link StoneSocketMenu}'s pixel-exact socket icon positions.
   * With no args, prints the current values for all 4 prestige tiers. With {@code <prestige 1-4>
   * <socket 1-4> <value>}, updates {@link StoneSocketMenu#setSocketCenterX} in memory and refreshes
   * the sender's open socket menu, if any, so the change is visible instantly with no
   * rebuild/restart. Remove this subcommand once alignment is finalized and the printed values are
   * copied into {@link StoneSocketMenu}'s {@code SOCKET_CENTER_X} source.
   *
   * <p>Calibration is per prestige tier (not just per socket) because {@code unlocked.png}'s
   * declared glyph width doesn't perfectly match its true rendered width, so drawing it shifts
   * every {@code locked.png} socket that follows it in the same title string — see {@link
   * StoneSocketMenu#SOCKET_CENTER_X}'s own doc for the full explanation.
   */
  private void handleSocketCalib(CommandSender sender, String[] args) {
    int[][] centerX = StoneSocketMenu.getSocketCenterX();

    if (args.length < 4) {
      Messageable.sendRaw(sender, "");
      Messageable.sendRaw(sender, "  &#00FFFF&l⚙ Socket CenterX Calibration");
      Messageable.sendRaw(sender, "  &#444444&m                                    ");
      Messageable.sendRaw(sender, "");
      for (int p = 0; p < centerX.length; p++) {
        StringBuilder row =
            new StringBuilder("  &#888888Prestige " + (p + 1) + " &#444444» &#FFFFFF");
        for (int i = 0; i < centerX[p].length; i++) {
          if (i > 0) row.append("&#444444, &#FFFFFF");
          row.append(centerX[p][i]);
        }
        Messageable.sendRaw(sender, row.toString());
      }
      Messageable.sendRaw(sender, "");
      Messageable.sendRaw(
          sender,
          "  &#888888Usage: &#FFFFFF/e socketcalib <prestige 1-"
              + centerX.length
              + "> <socket 1-"
              + centerX[0].length
              + "> <value>");
      Messageable.sendRaw(sender, "");
      return;
    }

    int prestige;
    int index;
    int value;
    try {
      prestige = Integer.parseInt(args[1]) - 1;
      index = Integer.parseInt(args[2]) - 1;
      value = Integer.parseInt(args[3]);
    } catch (NumberFormatException e) {
      sendError(sender, "Prestige, socket index, and value must all be whole numbers.");
      return;
    }

    if (prestige < 0 || prestige >= centerX.length) {
      sendError(
          sender, "Prestige must be between &#FFFFFF1 &cand &#FFFFFF" + centerX.length + "&c.");
      return;
    }
    if (index < 0 || index >= centerX[0].length) {
      sendError(
          sender,
          "Socket index must be between &#FFFFFF1 &cand &#FFFFFF" + centerX[0].length + "&c.");
      return;
    }

    StoneSocketMenu.setSocketCenterX(prestige, index, value);

    if (sender instanceof Player player) {
      Menu socketMenu = MenuRegistry.getInstance().get("stone_socket");
      if (socketMenu != null && socketMenu.isMenuOpen(player)) {
        socketMenu.refresh(player);
      }
    }

    sendSuccess(
        sender,
        "Prestige &#FFFFFF"
            + (prestige + 1)
            + " &#00FFFFsocket &#FFFFFF"
            + (index + 1)
            + " &#00FFFFcenterX set to &#FFFFFF"
            + value
            + "&#00FFFF.");
  }

  /**
   * Temporary calibration command for the exact-pixel essence/evolve vessel icon offsets and
   * shutter offset in {@link PickaxeMenu} and {@link StoneSocketMenu}. With no args, prints all
   * current values. Remove this subcommand once alignment is finalized and the printed values are
   * copied into each menu's source.
   *
   * <p>{@link PickaxeMenu}'s three offsets are single global values — its title never draws
   * prestige-varying glyphs before them, so they can't drift with prestige — and are set with
   * {@code /e vesselcalib pickaxe <essence|evolve|shutter> <value>}.
   *
   * <p>{@link StoneSocketMenu}'s three offsets sit immediately after its socket icon segment, whose
   * landing position drifts by prestige tier for the same reason {@link StoneSocketMenu
   * #SOCKET_CENTER_X} does (see that field's doc), so they're calibrated per tier with {@code /e
   * vesselcalib socket <essence|evolve|shutter> <prestige 1-4> <value>}.
   */
  private void handleVesselCalib(CommandSender sender, String[] args) {
    if (args.length < 2) {
      printVesselCalib(sender);
      return;
    }

    String menuArg = args[1].toLowerCase();
    if (!menuArg.equals("pickaxe") && !menuArg.equals("socket")) {
      sendError(sender, "Menu must be &#FFFFFFpickaxe &cor &#FFFFFFsocket&c.");
      return;
    }

    if (menuArg.equals("pickaxe")) {
      handlePickaxeVesselCalib(sender, args);
    } else {
      handleSocketVesselCalib(sender, args);
    }
  }

  /** Prints the current pickaxe- and socket-menu vessel/shutter calibration values. */
  private void printVesselCalib(CommandSender sender) {
    Messageable.sendRaw(sender, "");
    Messageable.sendRaw(sender, "  &#00FFFF&l⚙ Vessel/Shutter Calibration");
    Messageable.sendRaw(sender, "  &#444444&m                                    ");
    Messageable.sendRaw(sender, "");
    Messageable.sendRaw(sender, "  &#FFFFFF&lPickaxe Menu");
    Messageable.sendRaw(
        sender, "  &#888888Essence &#444444» &#FFFFFF" + PickaxeMenu.getEssenceIconOffset());
    Messageable.sendRaw(
        sender, "  &#888888Evolve &#444444» &#FFFFFF" + PickaxeMenu.getEvolveIconOffset());
    Messageable.sendRaw(
        sender, "  &#888888Shutter &#444444» &#FFFFFF" + PickaxeMenu.getShutterOffset());
    Messageable.sendRaw(sender, "");
    Messageable.sendRaw(sender, "  &#FFFFFF&lSocket Menu &#666666(per prestige tier)");
    appendVesselCalibRow(sender, "Essence", StoneSocketMenu.getEssenceIconOffset());
    appendVesselCalibRow(sender, "Evolve", StoneSocketMenu.getEvolveIconOffset());
    appendVesselCalibRow(sender, "Shutter", StoneSocketMenu.getShutterOffset());
    Messageable.sendRaw(sender, "");
    Messageable.sendRaw(
        sender, "  &#888888Usage: &#FFFFFF/e vesselcalib pickaxe <essence|evolve|shutter> <value>");
    Messageable.sendRaw(
        sender,
        "  &#888888Usage: &#FFFFFF/e vesselcalib socket <essence|evolve|shutter> <prestige 1-4>"
            + " <value>");
    Messageable.sendRaw(sender, "");
  }

  /** Appends one "Label » v1, v2, v3, v4" row to the {@code /e vesselcalib} listing. */
  private void appendVesselCalibRow(CommandSender sender, String label, int[] values) {
    StringBuilder row = new StringBuilder("  &#888888" + label + " &#444444» &#FFFFFF");
    for (int i = 0; i < values.length; i++) {
      if (i > 0) row.append("&#444444, &#FFFFFF");
      row.append(values[i]);
    }
    Messageable.sendRaw(sender, row.toString());
  }

  /** Handles {@code /e vesselcalib pickaxe <essence|evolve|shutter> <value>}. */
  private void handlePickaxeVesselCalib(CommandSender sender, String[] args) {
    if (args.length < 4) {
      sendError(sender, "Usage: &#FFFFFF/e vesselcalib pickaxe <essence|evolve|shutter> <value>");
      return;
    }

    String fieldArg = args[2].toLowerCase();
    if (!fieldArg.equals("essence") && !fieldArg.equals("evolve") && !fieldArg.equals("shutter")) {
      sendError(sender, "Field must be &#FFFFFFessence&c, &#FFFFFFevolve&c, or &#FFFFFFshutter&c.");
      return;
    }

    int value;
    try {
      value = Integer.parseInt(args[3]);
    } catch (NumberFormatException e) {
      sendError(sender, "Value must be a whole number.");
      return;
    }

    switch (fieldArg) {
      case "essence" -> PickaxeMenu.setEssenceIconOffset(value);
      case "evolve" -> PickaxeMenu.setEvolveIconOffset(value);
      default -> PickaxeMenu.setShutterOffset(value);
    }

    if (sender instanceof Player player) {
      Menu menu = MenuRegistry.getInstance().get("pickaxe");
      if (menu != null && menu.isMenuOpen(player)) {
        menu.refresh(player);
      }
    }

    sendSuccess(
        sender,
        "&#FFFFFFpickaxe " + fieldArg + " &#00FFFFoffset set to &#FFFFFF" + value + "&#00FFFF.");
  }

  /** Handles {@code /e vesselcalib socket <essence|evolve|shutter> <prestige 1-4> <value>}. */
  private void handleSocketVesselCalib(CommandSender sender, String[] args) {
    if (args.length < 5) {
      sendError(
          sender,
          "Usage: &#FFFFFF/e vesselcalib socket <essence|evolve|shutter> <prestige 1-4> <value>");
      return;
    }

    String fieldArg = args[2].toLowerCase();
    if (!fieldArg.equals("essence") && !fieldArg.equals("evolve") && !fieldArg.equals("shutter")) {
      sendError(sender, "Field must be &#FFFFFFessence&c, &#FFFFFFevolve&c, or &#FFFFFFshutter&c.");
      return;
    }

    int[] currentValues =
        switch (fieldArg) {
          case "essence" -> StoneSocketMenu.getEssenceIconOffset();
          case "evolve" -> StoneSocketMenu.getEvolveIconOffset();
          default -> StoneSocketMenu.getShutterOffset();
        };

    int prestige;
    int value;
    try {
      prestige = Integer.parseInt(args[3]) - 1;
      value = Integer.parseInt(args[4]);
    } catch (NumberFormatException e) {
      sendError(sender, "Prestige and value must both be whole numbers.");
      return;
    }

    if (prestige < 0 || prestige >= currentValues.length) {
      sendError(
          sender,
          "Prestige must be between &#FFFFFF1 &cand &#FFFFFF" + currentValues.length + "&c.");
      return;
    }

    switch (fieldArg) {
      case "essence" -> StoneSocketMenu.setEssenceIconOffset(prestige, value);
      case "evolve" -> StoneSocketMenu.setEvolveIconOffset(prestige, value);
      default -> StoneSocketMenu.setShutterOffset(prestige, value);
    }

    if (sender instanceof Player player) {
      Menu menu = MenuRegistry.getInstance().get("stone_socket");
      if (menu != null && menu.isMenuOpen(player)) {
        menu.refresh(player);
      }
    }

    sendSuccess(
        sender,
        "&#FFFFFFsocket "
            + fieldArg
            + " prestige &#FFFFFF"
            + (prestige + 1)
            + " &#00FFFFoffset set to &#FFFFFF"
            + value
            + "&#00FFFF.");
  }

  /** Formats message. */
  private String formatMessage(CommandSender sender, String message) {
    if (sender instanceof Player player) {
      return Utils.formatMessage(player, message);
    }
    return message.replace("&", "§");
  }

  private static final List<String> TOP_LEVEL =
      List.of(
          "help",
          "reload",
          "info",
          "mine",
          "border",
          "beam",
          "launchpad",
          "spawner",
          "blackhole",
          "cell",
          "rank",
          "backpack",
          "resetpickaxes",
          "resetprofile",
          "stone",
          "setbackpack",
          "statistic",
          "scroll",
          "socketcalib",
          "vesselcalib");

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      String current = args[0].toLowerCase();
      return TOP_LEVEL.stream().filter(s -> s.startsWith(current)).toList();
    }

    String sub = args[0].toLowerCase();

    BaseCommand delegate = delegates.get(sub);
    if (delegate != null) {
      String[] shifted = Arrays.copyOfRange(args, 1, args.length);
      List<String> result = delegate.getTabCompletion(sender, command, alias, shifted);
      return result != null ? result : List.of();
    }

    if ("rank".equals(sub)) {
      return tabCompleteRank(sender, command, alias, args);
    }

    if (args.length == 2 && "stone".equals(sub)) {
      return filterPrefix(
          List.of("Common", "Uncommon", "Rare", "Epic", "Legendary", "All"), args[1]);
    }
    if (args.length == 3 && "stone".equals(sub)) {
      java.util.Set<String> implemented =
          java.util.Set.of(
              "efficiency",
              "unbreaking",
              "alchemy",
              "autosell",
              "cascade",
              "drill",
              "explosive",
              "extraction",
              "fortune",
              "frostbite",
              "furnace",
              "merchant",
              "jackhammer",
              "lazer",
              "nuclearshower",
              "radioactive",
              "scavenger",
              "thor",
              "vortex");
      return EnchantAnimationConfig.getEnchantOrder().stream()
          .filter(
              display ->
                  implemented.stream()
                      .anyMatch(
                          internal ->
                              display.equalsIgnoreCase(
                                  EnchantAnimationConfig.getDisplayName(internal))))
          .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    if (args.length == 4 && "stone".equals(sub)) {
      return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
    if (args.length == 2 && "reload".equals(sub)) {
      return filterPrefix(List.of("default", "messages", "enchants", "all"), args[1]);
    }
    if (args.length == 2 && "backpack".equals(sub)) {
      return List.of("model");
    }
    if (args.length == 2
        && ("resetpickaxes".equals(sub)
            || "resetprofile".equals(sub)
            || "setbackpack".equals(sub))) {
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
          .toList();
    }
    if (args.length == 3 && "setbackpack".equals(sub)) {
      return filterPrefix(List.of("100", "200", "500", "1000"), args[2]);
    }
    if (args.length == 3 && "backpack".equals(sub) && args[1].equalsIgnoreCase("model")) {
      return filterPrefix(List.of("1", "2", "3", "4", "5"), args[2]);
    }
    if (args.length == 4 && "backpack".equals(sub) && args[1].equalsIgnoreCase("model")) {
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
          .toList();
    }
    if (args.length == 2 && "socketcalib".equals(sub)) {
      return filterPrefix(List.of("1", "2", "3", "4"), args[1]);
    }
    if (args.length == 3 && "socketcalib".equals(sub)) {
      return filterPrefix(List.of("1", "2", "3", "4"), args[2]);
    }
    if (args.length == 2 && "vesselcalib".equals(sub)) {
      return filterPrefix(List.of("pickaxe", "socket"), args[1]);
    }
    if (args.length == 3 && "vesselcalib".equals(sub)) {
      return filterPrefix(List.of("essence", "evolve", "shutter"), args[2]);
    }
    if (args.length == 4 && "vesselcalib".equals(sub) && "socket".equalsIgnoreCase(args[1])) {
      return filterPrefix(List.of("1", "2", "3", "4"), args[3]);
    }
    if ("scroll".equals(sub)) {
      if (args.length == 2)
        return filterPrefix(
            List.of("list", "coins", "essence", "atoms", "pickaxe_xp", "backpack_slots"), args[1]);
      if (args.length == 3) return filterPrefix(List.of("1", "10", "100", "1000"), args[2]);
      if (args.length == 4)
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
            .toList();
    }

    return List.of();
  }

  /** Tab completion for /e rank <sub> ... */
  private List<String> tabCompleteRank(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 2) {
      return filterPrefix(RANK_SUBS, args[1]);
    }

    String rankSub = args[1].toLowerCase();

    switch (rankSub) {
      case "set" -> {
        String[] shifted = Arrays.copyOfRange(args, 2, args.length);
        List<String> result = setRankCommand.getTabCompletion(sender, command, alias, shifted);
        return result != null ? result : List.of();
      }
      case "staff" -> {
        String[] shifted = Arrays.copyOfRange(args, 2, args.length);
        List<String> result = setStaffCommand.getTabCompletion(sender, command, alias, shifted);
        return result != null ? result : List.of();
      }
      case "addperm", "removeperm", "listperms" -> {
        String action = rankSub.replace("perm", "").replace("perms", "").replace("s", "");
        if (rankSub.equals("addperm")) action = "add";
        else if (rankSub.equals("removeperm")) action = "remove";
        else action = "list";

        String[] rpArgs = new String[args.length - 1];
        rpArgs[0] = action;
        System.arraycopy(args, 2, rpArgs, 1, args.length - 2);

        List<String> result = rankPermCommand.getTabCompletion(sender, command, alias, rpArgs);
        return result != null ? result : List.of();
      }
    }

    return List.of();
  }

  /** Filter prefix. */
  private static List<String> filterPrefix(List<String> options, String prefix) {
    String lc = prefix.toLowerCase();
    return options.stream().filter(o -> o.toLowerCase().startsWith(lc)).toList();
  }

  /** Releases resources held by this component. */
  @Override
  public void cleanup() {
    delegates.values().forEach(BaseCommand::cleanup);
    setRankCommand.cleanup();
    setStaffCommand.cleanup();
    rankPermCommand.cleanup();
  }
}
