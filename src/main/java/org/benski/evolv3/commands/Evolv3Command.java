package org.benski.evolv3.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.items.Scroll;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
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

  public Evolv3Command(JavaPlugin plugin) {
    super(plugin);
  }

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
      default -> {
        sendError(sender, "Unknown subcommand: &#FFFFFF" + args[0]);
        sendInfo(sender, "Use &#FFFFFF/e help &#CCCCCCfor available commands.");
      }
    }

    return true;
  }

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

  private void showHelp(CommandSender sender) {
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    for (String line : messages.getStringList(ConfigPaths.Messages.Admin.HELP)) {
      sender.sendMessage(formatMessage(sender, line));
    }
  }

  private void handleReload(CommandSender sender, String[] args) {
    ConfigRegistry.getInstance().cleanup();
    ConfigRegistry.getInstance().initialize();

    FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    String successMsg =
        messagesConfig.getString(
            ConfigPaths.Messages.General.RELOAD_SUCCESS, "&aConfiguration reloaded successfully!");
    sender.sendMessage(formatMessage(sender, successMsg));
  }

  @SuppressWarnings("deprecation")
  private void handleBackpack(CommandSender sender, String[] args) {
    if (args.length < 3 || !args[1].equalsIgnoreCase("model")) {
      sendInfo(sender, "Usage: &#FFFFFF/e backpack model <number> [player]");
      return;
    }

    int modelData;
    try {
      modelData = Integer.parseInt(args[2]);
    } catch (NumberFormatException e) {
      sendError(sender, "Invalid number: &#FFFFFF" + args[2]);
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

  private void showInfo(CommandSender sender) {
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    for (String line : messages.getStringList(ConfigPaths.Messages.Admin.INFO)) {
      String processed = line.replace("%version%", plugin.getPluginMeta().getVersion());
      sender.sendMessage(formatMessage(sender, processed));
    }
  }

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
    PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    MongoManager mongo = MongoManager.getInstance();

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
      mongo.deleteAsync("Pickaxes", "id", id);
      pickaxeDC.getAll().remove(id);
    }

    for (int i = 0; i < profile.storedPickaxes.size(); i++) {
      profile.storedPickaxes.set(i, "");
    }
    profile.pickaxeLimit = 0;
    profile.save(null);

    sendSuccess(
        sender,
        "Removed &#FFFFFF"
            + pickaxeIds.size()
            + " &#00FFFFpickaxe(s) from &#FFFFFF"
            + target.getName()
            + " &#00FFFFand reset their slot limit.");
  }

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
    PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    MongoManager mongo = MongoManager.getInstance();

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

    pickaxeDC.getAll().values().stream()
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

    for (String id : pickaxeIds) {
      mongo.deleteAsync("Pickaxes", "id", id);
      pickaxeDC.getAll().remove(id);
    }

    String playerId = targetUuid.toString();
    mongo.deleteAsync("Players", "id", playerId);
    playerDC.getAll().remove(playerId);

    sendSuccess(
        sender,
        "Reset &#FFFFFF"
            + target.getName()
            + "&#00FFFF's profile and deleted &#FFFFFF"
            + pickaxeIds.size()
            + " &#00FFFFpickaxe(s).");
  }

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

    if (size < 1) {
      sendError(sender, "Size must be at least &#FFFFFF1&c.");
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
      if (amount < 1) throw new NumberFormatException();
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
          default -> null;
        };

    if (scroll == null) {
      sendError(sender, "Failed to create scroll item.");
      return;
    }

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
          "scroll");

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

  private static List<String> filterPrefix(List<String> options, String prefix) {
    String lc = prefix.toLowerCase();
    return options.stream().filter(o -> o.toLowerCase().startsWith(lc)).toList();
  }

  @Override
  public void cleanup() {
    delegates.values().forEach(BaseCommand::cleanup);
    setRankCommand.cleanup();
    setStaffCommand.cleanup();
    rankPermCommand.cleanup();
  }
}
