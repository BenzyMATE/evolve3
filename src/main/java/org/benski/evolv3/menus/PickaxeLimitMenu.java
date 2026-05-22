package org.benski.evolv3.menus;

import static org.benski.evolv3.core.helpers.Utils.buildDurabilityBar;
import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.AncientResin;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Menu for managing pickaxe storage slots. Slots are unlocked automatically as the player's highest
 * evolution level increases. Evolution 1 = 1 slot, Evolution 2 = 2 slots, and so on up to the
 * configured maximum.
 *
 * <p>Each slot can be in one of five states:
 *
 * <ul>
 *   <li><b>LOCKED</b> – player's max evolution is below the required level for this slot
 *   <li><b>CLAIM</b> – slot unlocked, no pickaxe claimed yet, no active pickaxe
 *   <li><b>BLOCKED</b> – slot unlocked, empty, but player already has an active pickaxe
 *   <li><b>ACTIVE</b> – pickaxe for this slot is currently in the player's inventory (LEAD)
 *   <li><b>STORED</b> – pickaxe for this slot is stored away (pickaxe item)
 * </ul>
 *
 * <p>Layout (27 slots / 3 rows):
 *
 * <pre>
 *   Row 0 ( 0- 8): border
 *   Row 1 ( 9-17): border [sl0][sl1][sl2][sl3][sl4][sl5][sl6] border
 *   Row 2 (18-26): border, purchase button at slot 22, border
 * </pre>
 *
 * <p>Configurable via {@code default.yml} (PickaxeStorage section), {@code menus.yml} (Pickaxe
 * Limit Menu section), and {@code messages.yml} (pickaxe-limit section).
 */
@ExtensionMethod(ItemStackExtensions.class)
public class PickaxeLimitMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("pickaxe_limit");

  /**
   * Fixed GUI slot positions for each storage entry. The array length is the absolute layout
   * maximum (7 for a 27-slot, 3-row GUI). The effective max is read from config via {@link
   * #getMaxSlots()}.
   */
  private static final int[] SLOT_POSITIONS = {10, 11, 12, 13, 14, 15, 16};

  /** Inventory slot index for the purchase / unlock button. */
  private static final int PURCHASE_SLOT = 22;

  private static final LegacyComponentSerializer SERIALIZER =
      LegacyComponentSerializer.legacySection();

  /**
   * Per-player claim timestamps used for rate-limiting. Each entry is a list of epoch-ms values for
   * recent claims. Static so the window survives menu re-opens.
   */
  private static final Map<UUID, List<Long>> claimTimestamps = new ConcurrentHashMap<>();

  private final PlayerDataCenter playerDataCenter;

  public PickaxeLimitMenu(JavaPlugin plugin) {
    super(plugin);
    this.playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
  }

  @Override
  protected String getMenuTitle(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    return menus.getString(ConfigPaths.Menus.PickaxeLimitMenu.TITLE, "&8Pickaxe Storage");
  }

  @Override
  protected int getMenuSize() {
    return 27;
  }

  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) return;

    ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 0; i < 27; i++) {
      inventory.setItem(i, border);
    }

    boolean hasActive = playerHasRegisteredPickaxe(player);
    int unlockedSlots = getUnlockedSlots(player);
    int maxSlots = getMaxSlots();

    for (int i = 0; i < maxSlots; i++) {
      int guiSlot = SLOT_POSITIONS[i];

      if (i >= unlockedSlots) {
        inventory.setItem(guiSlot, buildLockedSlot(player, i));
        continue;
      }

      String entry = getStoredEntry(profile, i);
      if (entry.isEmpty()) {
        inventory.setItem(guiSlot, hasActive ? buildBlockedSlot(player) : buildClaimSlot(player));
      } else {
        String pickaxeId = entry.split(":", 2)[0];
        if (pickaxeIsActive(player, pickaxeId)) {
          inventory.setItem(guiSlot, buildActiveSlot(player, entry));
        } else {
          inventory.setItem(guiSlot, buildStoredSlot(player, entry));
        }
      }
    }

    if (unlockedSlots < maxSlots) {
      inventory.setItem(PURCHASE_SLOT, buildEvolutionInfoButton(player, unlockedSlots, maxSlots));
    } else {
      inventory.setItem(PURCHASE_SLOT, buildMaxedButton(player));
    }
  }

  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) return true;

    int slotIndex = getSlotIndex(slot);
    if (slotIndex >= 0) {
      if (event.isRightClick()) {
        handleRepairClick(player, profile, slotIndex);
      } else {
        handleStorageSlotClick(player, profile, slotIndex);
      }
      return true;
    }

    return true;
  }

  private void handleStorageSlotClick(Player player, PlayerProfile profile, int slotIndex) {
    if (slotIndex >= getUnlockedSlots(player)) {
      sendMessage(
          player,
          ConfigPaths.Messages.PickaxeLimit.BLOCKED_CLICK,
          "%evolv3_prefix% &#CCCCCCThis slot is locked. Purchase it to unlock.");
      return;
    }

    String entry = getStoredEntry(profile, slotIndex);

    if (entry.isEmpty()) {
      if (playerHasRegisteredPickaxe(player)) {
        sendMessage(
            player,
            ConfigPaths.Messages.PickaxeLimit.MUST_DEPOSIT_BEFORE_CLAIM,
            "%evolv3_prefix% &#CCCCCCDeposit your active pickaxe before claiming another slot.");
      } else {
        handleClaim(player, profile, slotIndex);
      }
      return;
    }

    String pickaxeId = entry.split(":", 2)[0];
    if (pickaxeIsActive(player, pickaxeId)) {
      handleDeposit(player, pickaxeId);
    } else {
      handleWithdraw(player, entry);
    }
  }

  /** Deposits a specific pickaxe back into storage. */
  private void handleDeposit(Player player, String pickaxeId) {
    ItemStack cursor = player.getOpenInventory().getCursor();
    if (cursor != null
        && cursor.getType().name().contains("PICKAXE")
        && pickaxeId.equals(cursor.getData("id"))) {
      ItemMeta depositMeta = cursor.getItemMeta();
      if (depositMeta instanceof Damageable dmg) {
        Pickaxe depositProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);
        if (depositProfile != null) {
          depositProfile.durability = dmg.getDamage();
          depositProfile.save(null);
        }
      }
      player.getOpenInventory().setCursor(new ItemStack(Material.AIR));
      player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);
      sendBar(player, ConfigPaths.Messages.PickaxeLimit.PICKAXE_DEPOSITED);
      refresh(player);
      return;
    }

    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length; i++) {
      ItemStack item = contents[i];
      if (item == null || !item.getType().name().contains("PICKAXE")) continue;
      String id = item.getData("id");
      if (!pickaxeId.equals(id)) continue;

      ItemMeta depositMeta = item.getItemMeta();
      if (depositMeta instanceof Damageable dmg) {
        Pickaxe depositProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);
        if (depositProfile != null) {
          depositProfile.durability = dmg.getDamage();
          depositProfile.save(null);
        }
      }

      player.getInventory().setItem(i, new ItemStack(Material.AIR));
      player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);
      sendBar(player, ConfigPaths.Messages.PickaxeLimit.PICKAXE_DEPOSITED);
      refresh(player);
      return; // NOPMD - intentional find-first loop
    }
    sendMessage(
        player,
        ConfigPaths.Messages.PickaxeLimit.PICKAXE_NOT_FOUND,
        "%evolv3_prefix% &#CCCCCCPickaxe not found in your inventory.");
  }

  /** Withdraws a stored pickaxe into the player's inventory. */
  private void handleWithdraw(Player player, String entry) {
    if (playerHasRegisteredPickaxe(player)) {
      sendMessage(
          player,
          ConfigPaths.Messages.PickaxeLimit.MUST_DEPOSIT_BEFORE_WITHDRAW,
          "%evolv3_prefix% &#CCCCCCDeposit your active pickaxe before withdrawing another.");
      return;
    }

    if (player.getInventory().firstEmpty() == -1) {
      showInventoryFullTitle(player);
      return;
    }

    String[] parts = entry.split(":", 2);
    String pickaxeId = parts[0];
    Material material = parts.length > 1 ? parseMaterial(parts[1]) : Material.WOODEN_PICKAXE;

    ItemStack pickaxeItem = new ItemStack(material);
    pickaxeItem.setData("id", pickaxeId);

    Pickaxe withdrawProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);
    if (withdrawProfile != null && withdrawProfile.durability > 0) {
      ItemMeta withdrawMeta = pickaxeItem.getItemMeta();
      if (withdrawMeta instanceof Damageable dmg) {
        dmg.setDamage(withdrawProfile.durability);
        pickaxeItem.setItemMeta(withdrawMeta);
      }
    }

    if (withdrawProfile != null) {
      PickaxeManager.getInstance().applyLoreAndName(player, withdrawProfile, pickaxeItem);
    }
    player.getInventory().addItem(pickaxeItem);
    player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.2f);
    sendBar(player, ConfigPaths.Messages.PickaxeLimit.PICKAXE_WITHDRAWN);
    refresh(player);
  }

  /** Claims a new pickaxe for the given slot. */
  private void handleClaim(Player player, PlayerProfile profile, int slotIndex) {
    if (!canClaimPickaxe(player)) {
      sendMessage(
          player,
          ConfigPaths.Messages.PickaxeLimit.CLAIM_RATE_LIMITED,
          "&#FF4444You've claimed too many pickaxes recently. Try again in a few minutes.");
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return;
    }

    if (player.getInventory().firstEmpty() == -1) {
      showInventoryFullTitle(player);
      return;
    }

    String pickaxeId = UUID.randomUUID().toString();
    Material material = Material.WOODEN_PICKAXE;

    ItemStack pickaxeItem = new ItemStack(material);
    pickaxeItem.setData("id", pickaxeId);

    PlayerProfile claimProfile = playerDataCenter.getLocal(player);
    if (claimProfile == null) return;
    Pickaxe pickProfile = Pickaxe.createDefault(player, pickaxeId);
    claimProfile.upsertPickaxe(pickProfile);
    pickProfile.save(null);

    setStoredEntry(profile, slotIndex, pickaxeId + ":" + material.name());
    profile.save(null);

    PickaxeManager.getInstance().applyLoreAndName(player, pickProfile, pickaxeItem);
    player.getInventory().addItem(pickaxeItem);
    recordClaim(player);
    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    sendBar(player, ConfigPaths.Messages.PickaxeLimit.PICKAXE_CLAIMED);
    refresh(player);
  }

  /**
   * Returns true if this player is allowed to claim another pickaxe given the configured rate-limit
   * (max claims per rolling time window).
   */
  private boolean canClaimPickaxe(Player player) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int maxClaims = config.getInt(ConfigPaths.Default.PickaxeStorage.MAX_CLAIMS_PER_WINDOW, 5);
    long windowMs =
        config.getLong(ConfigPaths.Default.PickaxeStorage.CLAIM_WINDOW_MINUTES, 5) * 60_000L;

    List<Long> timestamps =
        claimTimestamps.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
    long cutoff = System.currentTimeMillis() - windowMs;
    timestamps.removeIf(t -> t < cutoff);
    return timestamps.size() < maxClaims;
  }

  /** Records a pickaxe claim for rate-limiting purposes. */
  private void recordClaim(Player player) {
    claimTimestamps
        .computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
        .add(System.currentTimeMillis());
  }

  private ItemStack buildClaimSlot(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String name =
        menus.getString(
            ConfigPaths.Menus.PickaxeLimitMenu.CLAIM_NAME, "&#00FFFF⛏ &#FFFFFF&lClaim Pickaxe");
    List<String> lore =
        getLoreFromConfig(
            player,
            ConfigPaths.Menus.PickaxeLimitMenu.CLAIM_LORE,
            List.of(
                "",
                " &#FFFFFF&lDescription",
                " &8| &#CCCCCCThis slot is ready for a pickaxe.",
                " &8| &#CCCCCCClick to claim your new pickaxe.",
                "",
                " &8| &aLeft-Click &#AAAAAAto claim",
                ""));

    return new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
        .name(fmt(player, name))
        .lore(lore)
        .build();
  }

  private ItemStack buildBlockedSlot(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String name =
        menus.getString(
            ConfigPaths.Menus.PickaxeLimitMenu.BLOCKED_NAME, "&#FF4444⬡ &#FFFFFF&lSlot Blocked");
    List<String> lore =
        getLoreFromConfig(
            player,
            ConfigPaths.Menus.PickaxeLimitMenu.BLOCKED_LORE,
            List.of(
                "",
                " &#FFFFFF&lBlocked",
                " &8| &#CCCCCCYou already have an active pickaxe.",
                " &8| &#CCCCCCDeposit it first to claim this slot.",
                ""));

    return new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
        .name(fmt(player, name))
        .lore(lore)
        .build();
  }

  /** LEAD — the pickaxe for this slot is currently in the player's inventory (active). */
  private ItemStack buildActiveSlot(Player player, String entry) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String rawName =
        menus.getString(
            ConfigPaths.Menus.PickaxeLimitMenu.ACTIVE_NAME,
            "&#00FFFF⛏ &#FFFFFF&lPickaxe &#00FF88(Active)");

    String[] parts = entry.split(":", 2);
    String pickaxeId = parts[0];
    Material material = parts.length > 1 ? parseMaterial(parts[1]) : Material.WOODEN_PICKAXE;
    Pickaxe pickProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);

    int damage = 0;
    ItemStack heldPickaxe = findPickaxeInInventory(player, pickaxeId);
    if (heldPickaxe != null) {
      ItemMeta heldMeta = heldPickaxe.getItemMeta();
      if (heldMeta instanceof Damageable dmg) damage = dmg.getDamage();
    }

    ItemStack item = new ItemStack(Material.LEAD);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    meta.displayName(
        SERIALIZER.deserialize(fmt(player, rawName)).decoration(TextDecoration.ITALIC, false));

    if (pickProfile != null) {
      String durabilityBar = buildDurabilityBar(damage, material.getMaxDurability());
      FileConfiguration activeConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
      String durabilityLabel =
          activeConfig.getString(
              ConfigPaths.Default.Pickaxe.DURABILITY_LABEL, " &8| &#00FFFFDurability:");
      String barPrefix =
          activeConfig.getString(ConfigPaths.Default.Pickaxe.DURABILITY_BAR_PREFIX, " &8| ");
      List<String> footer = new ArrayList<>();
      footer.add("");
      footer.add(durabilityLabel);
      footer.add(barPrefix + durabilityBar);
      footer.add("");
      footer.add(" &8| &aLeft-Click &#AAAAAAto deposit");
      footer.add(
          " &8| &#FFEE00Right-Click &#AAAAAAto repair &#777777(&#E8890A5 &#777777Ancient Resin)");
      footer.add("");
      meta.lore(PickaxeManager.getInstance().buildLoreComponents(player, pickProfile, footer));
    } else {
      meta.lore(
          List.of(
              Component.empty(),
              SERIALIZER
                  .deserialize(
                      fmt(player, " &8| &#CCCCCCPickaxe is currently active in your inventory."))
                  .decoration(TextDecoration.ITALIC, false),
              Component.empty(),
              SERIALIZER
                  .deserialize(fmt(player, " &8| &aLeft-Click &#AAAAAAto deposit"))
                  .decoration(TextDecoration.ITALIC, false),
              Component.empty()));
    }

    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    item.setItemMeta(meta);
    return item;
  }

  /** Pickaxe item — the pickaxe for this slot is stored away. */
  private ItemStack buildStoredSlot(Player player, String entry) {
    String[] parts = entry.split(":", 2);
    String pickaxeId = parts[0];
    Material material = parts.length > 1 ? parseMaterial(parts[1]) : Material.WOODEN_PICKAXE;
    Pickaxe pickProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);

    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    if (pickProfile != null) {
      if (meta instanceof Damageable dmg && pickProfile.durability > 0) {
        dmg.setDamage(pickProfile.durability);
      }

      FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
      String nameTemplate = config.getString(ConfigPaths.Default.Pickaxe.NAME, "&#00FFFF⛏ Pickaxe");
      String resolvedName = nameTemplate.replace("%evolv3_player_name%", player.getName());
      meta.displayName(
          SERIALIZER
              .deserialize(fmt(player, resolvedName))
              .decoration(TextDecoration.ITALIC, false));

      String durabilityBar =
          buildDurabilityBar(pickProfile.durability, material.getMaxDurability());
      String durabilityLabel =
          config.getString(
              ConfigPaths.Default.Pickaxe.DURABILITY_LABEL, " &8| &#00FFFFDurability:");
      String barPrefix =
          config.getString(ConfigPaths.Default.Pickaxe.DURABILITY_BAR_PREFIX, " &8| ");
      List<String> footer = new ArrayList<>();
      footer.add("");
      footer.add(durabilityLabel);
      footer.add(barPrefix + durabilityBar);
      footer.add("");
      footer.add(" &8| &aLeft-Click &#AAAAAAto withdraw");
      footer.add(
          " &8| &#FFEE00Right-Click &#AAAAAAto repair &#777777(&#E8890A5 &#777777Ancient Resin)");
      footer.add("");
      meta.lore(PickaxeManager.getInstance().buildLoreComponents(player, pickProfile, footer));
    } else {
      meta.displayName(
          SERIALIZER
              .deserialize(fmt(player, "&#00FFFF⛏ &#FFFFFF&lStored Pickaxe"))
              .decoration(TextDecoration.ITALIC, false));
      meta.lore(
          List.of(
              Component.empty(),
              SERIALIZER
                  .deserialize(fmt(player, " &8| &aLeft-Click &#AAAAAAto withdraw"))
                  .decoration(TextDecoration.ITALIC, false),
              Component.empty()));
    }

    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack buildLockedSlot(Player player, int slotIndex) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    int requiredEvolution = slotIndex + 1;

    String name =
        menus.getString(
            ConfigPaths.Menus.PickaxeLimitMenu.LOCKED_NAME, "&#666666⬡ &#666666Locked Slot");
    List<String> lore =
        List.of(
            fmt(player, " &#444444Locked"),
            "",
            fmt(player, " &#FFFFFF&lRequirements"),
            fmt(
                player,
                " &8| &#CCCCCCReach &#FFFFFF&lEvolution "
                    + requiredEvolution
                    + " &#CCCCCCto unlock."),
            "");

    return new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
        .name(fmt(player, name))
        .lore(lore)
        .build();
  }

  private ItemStack buildEvolutionInfoButton(Player player, int unlockedSlots, int maxSlots) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    int currentMaxEvolution = profile != null ? profile.getHighestEvolution() : unlockedSlots;
    int nextEvolution = unlockedSlots + 1;

    List<String> lore = new ArrayList<>();
    lore.add("");
    lore.add(fmt(player, " &#FFFFFF&lSlot Unlocks"));
    lore.add(fmt(player, " &8| &#00FFFFUnlocked: &#FFFFFF" + unlockedSlots + " / " + maxSlots));
    lore.add(fmt(player, " &8| &#00FFFFYour Evolution: &#FFFFFF" + currentMaxEvolution));
    lore.add("");
    lore.add(fmt(player, " &#FFFFFF&lNext Slot"));
    lore.add(
        fmt(
            player,
            " &8| &#CCCCCCReach &#FFFFFF&lEvolution " + nextEvolution + " &#CCCCCCto unlock."));
    lore.add("");

    return new ItemBuilder(Material.EXPERIENCE_BOTTLE)
        .name(fmt(player, "&#00FFFF⬡ &#FFFFFF&lSlot Progress"))
        .lore(lore)
        .build();
  }

  private ItemStack buildMaxedButton(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String name =
        menus.getString(
            ConfigPaths.Menus.PickaxeLimitMenu.MAXED_NAME,
            "&#00FFFF★ &#FFFFFF&lAll Slots Unlocked");
    List<String> lore =
        getLoreFromConfig(
            player,
            ConfigPaths.Menus.PickaxeLimitMenu.MAXED_LORE,
            List.of(
                "", " &#FFFFFF&lStatus",
                " &8| &#00FF88All pickaxe storage slots are unlocked!", ""));

    return new ItemBuilder(Material.NETHER_STAR).name(fmt(player, name)).lore(lore).build();
  }

  /** Returns the effective maximum slots, read from config and capped at the layout limit. */
  private int getMaxSlots() {
    int configured =
        ConfigRegistry.getInstance()
            .get(ConfigNames.DEFAULT)
            .getInt(ConfigPaths.Default.PickaxeStorage.MAX_SLOTS, SLOT_POSITIONS.length);
    return Math.min(configured, SLOT_POSITIONS.length);
  }

  /**
   * Returns the number of unlocked slots for the player.
   *
   * <p>Reads from {@link PlayerProfile#highestEvolution}, which is persisted to MongoDB and updated
   * every time a pickaxe evolves. This means the correct slot count is available on join regardless
   * of which pickaxes are held or in storage.
   *
   * <p>As a migration safety net, locally-cached pickaxe profiles are also scanned. If any cached
   * pickaxe has a higher evolution than the stored value, the profile is updated and saved so the
   * value self-corrects for players who evolved before this field was introduced.
   */
  private int getUnlockedSlots(Player player) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    int maxEvolution = profile != null ? profile.getHighestEvolution() : 1;
    return Math.min(maxEvolution, getMaxSlots());
  }

  /**
   * Reads a lore list from menus config, formats each line through {@link #fmt}, and returns it.
   * Falls back to {@code fallback} if the config list is empty. Accepts optional token pairs
   * (token, value, token, value, ...) applied before formatting.
   */
  private List<String> getLoreFromConfig(
      Player player, String path, List<String> fallback, String... tokens) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    List<String> raw = menus.getStringList(path);
    if (raw.isEmpty()) raw = fallback;

    List<String> result = new ArrayList<>(raw.size());
    for (String line : raw) {
      for (int i = 0; i + 1 < tokens.length; i += 2) {
        line = line.replace(tokens[i], tokens[i + 1]);
      }
      result.add(fmt(player, line));
    }
    return result;
  }

  /** Reads a message from messages.yml, falling back to the given default. */
  private String getMessage(String path, String fallback) {
    return ConfigRegistry.getInstance().get(ConfigNames.MESSAGES).getString(path, fallback);
  }

  /** Sends a message from messages.yml (with fallback) to the player, with color processing. */
  private void sendMessage(Player player, String path, String fallback) {
    player.sendMessage(fmt(player, getMessage(path, fallback)));
  }

  /** Sends a multi-line message list from messages.yml to the player, with color processing. */
  private void sendMessageList(Player player, String path) {
    List<String> lines = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES).getStringList(path);
    for (String line : lines) {
      player.sendMessage(fmt(player, line));
    }
  }

  /** Sends a single-string message from messages.yml as an action bar. */
  private void sendBar(Player player, String path) {
    String msg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES).getString(path, "");
    if (msg.isEmpty()) return;
    player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(fmt(player, msg)));
  }

  /** Shows an inventory-full title and subtitle to the player. */
  private void showInventoryFullTitle(Player player) {
    FileConfiguration msgs = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    String titleRaw =
        msgs.getString(
            ConfigPaths.Messages.PickaxeLimit.INVENTORY_FULL_TITLE, "&c&lInventory Full");
    String subRaw =
        msgs.getString(
            ConfigPaths.Messages.PickaxeLimit.INVENTORY_FULL_SUBTITLE,
            "&cMake room and try again.");
    Component title =
        SERIALIZER.deserialize(fmt(player, titleRaw)).decoration(TextDecoration.ITALIC, false);
    Component sub =
        SERIALIZER.deserialize(fmt(player, subRaw)).decoration(TextDecoration.ITALIC, false);
    player.showTitle(
        Title.title(
            title,
            sub,
            Title.Times.times(
                Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
  }

  /** Sends a message list with token replacements applied (token, value, token, value, ...). */
  private void sendMessageList(Player player, String path, String... replacements) {
    List<String> lines = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES).getStringList(path);
    for (String line : lines) {
      for (int i = 0; i + 1 < replacements.length; i += 2) {
        line = line.replace(replacements[i], replacements[i + 1]);
      }
      player.sendMessage(fmt(player, line));
    }
  }

  private void handleRepairClick(Player player, PlayerProfile profile, int slotIndex) {
    if (slotIndex >= getUnlockedSlots(player)) return;

    String entry = getStoredEntry(profile, slotIndex);
    if (entry.isEmpty()) return;

    String[] parts = entry.split(":", 2);
    String pickaxeId = parts[0];
    Material material = parts.length > 1 ? parseMaterial(parts[1]) : Material.WOODEN_PICKAXE;
    boolean isActive = pickaxeIsActive(player, pickaxeId);

    handleRepair(player, pickaxeId, material, isActive);
  }

  private void handleRepair(Player player, String pickaxeId, Material material, boolean isActive) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int repairCost = config.getInt(ConfigPaths.Default.AncientResin.REPAIR_COST, 5);

    int currentDamage;
    if (isActive) {
      ItemStack heldPickaxe = findPickaxeInInventory(player, pickaxeId);
      if (heldPickaxe == null) return;
      ItemMeta m = heldPickaxe.getItemMeta();
      currentDamage = (m instanceof Damageable dmg) ? dmg.getDamage() : 0;
    } else {
      Pickaxe pickProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);
      if (pickProfile == null) return;
      currentDamage = pickProfile.durability;
    }

    if (currentDamage == 0) {
      sendMessageList(player, ConfigPaths.Messages.PickaxeLimit.REPAIR_ALREADY_FULL);
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return;
    }

    if (countAncientResinInInventory(player) < repairCost) {
      sendMessageList(
          player,
          ConfigPaths.Messages.PickaxeLimit.REPAIR_INSUFFICIENT_WOOD,
          "%required%",
          String.valueOf(repairCost));
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return;
    }

    removeAncientResinFromInventory(player, repairCost);

    if (isActive) {
      ItemStack heldPickaxe = findPickaxeInInventory(player, pickaxeId);
      if (heldPickaxe == null) return;
      ItemMeta m = heldPickaxe.getItemMeta();
      if (m instanceof Damageable dmg) {
        dmg.setDamage(0);
        heldPickaxe.setItemMeta(m);
      }
    } else {
      Pickaxe pickProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);
      if (pickProfile == null) return;
      pickProfile.durability = 0;
      pickProfile.save(null);
    }

    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    sendBar(player, ConfigPaths.Messages.PickaxeLimit.REPAIR_SUCCESS);
    refresh(player);
  }

  private int countAncientResinInInventory(Player player) {
    int total = 0;
    for (ItemStack item : player.getInventory().getContents()) {
      if (AncientResin.isAncientResin(item)) total += item.getAmount();
    }
    return total;
  }

  private void removeAncientResinFromInventory(Player player, int amount) {
    int remaining = amount;
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && remaining > 0; i++) {
      ItemStack item = contents[i];
      if (!AncientResin.isAncientResin(item)) continue;
      if (item.getAmount() <= remaining) {
        remaining -= item.getAmount();
        player.getInventory().setItem(i, new ItemStack(Material.AIR));
      } else {
        item.setAmount(item.getAmount() - remaining);
        remaining = 0;
      }
    }
  }

  private ItemStack findPickaxeInInventory(Player player, String pickaxeId) {
    for (ItemStack item : player.getInventory().getContents()) {
      if (item == null || !item.getType().name().contains("PICKAXE")) continue;
      if (pickaxeId.equals(item.getData("id"))) return item;
    }
    return null;
  }

  private static String fmt(Player player, String text) {
    return formatMessage(player, text);
  }

  private boolean playerHasRegisteredPickaxe(Player player) {
    ItemStack cursor = player.getOpenInventory().getCursor();
    if (cursor != null && cursor.getType().name().contains("PICKAXE")) {
      String id = cursor.getData("id");
      if (id != null && !id.isEmpty()) return true;
    }
    for (ItemStack item : player.getInventory().getContents()) {
      if (item == null || !item.getType().name().contains("PICKAXE")) continue;
      String id = item.getData("id");
      if (id != null && !id.isEmpty()) return true;
    }
    return false;
  }

  private boolean pickaxeIsActive(Player player, String pickaxeId) {
    ItemStack cursor = player.getOpenInventory().getCursor();
    if (cursor != null
        && cursor.getType().name().contains("PICKAXE")
        && pickaxeId.equals(cursor.getData("id"))) {
      return true;
    }
    for (ItemStack item : player.getInventory().getContents()) {
      if (item == null || !item.getType().name().contains("PICKAXE")) continue;
      if (pickaxeId.equals(item.getData("id"))) return true;
    }
    return false;
  }

  private int getSlotIndex(int guiSlot) {
    for (int i = 0; i < SLOT_POSITIONS.length; i++) {
      if (SLOT_POSITIONS[i] == guiSlot) return i;
    }
    return -1;
  }

  private String getStoredEntry(PlayerProfile profile, int slotIndex) {
    if (slotIndex < 0 || slotIndex >= profile.storedPickaxes.size()) return "";
    String entry = profile.storedPickaxes.get(slotIndex);
    return entry != null ? entry : "";
  }

  private void setStoredEntry(PlayerProfile profile, int slotIndex, String value) {
    while (profile.storedPickaxes.size() <= slotIndex) {
      profile.storedPickaxes.add("");
    }
    profile.storedPickaxes.set(slotIndex, value != null ? value : "");
  }

  private Material parseMaterial(String name) {
    try {
      return Material.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException e) {
      return Material.WOODEN_PICKAXE;
    }
  }

  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }
}
