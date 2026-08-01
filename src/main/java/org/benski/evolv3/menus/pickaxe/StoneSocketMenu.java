package org.benski.evolv3.menus.pickaxe;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.PixelSpace;
import org.benski.evolv3.core.constants.ShutterFrames;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.ItemTagHelper;
import org.benski.evolv3.core.helpers.VesselProgress;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Menu for viewing and managing the three evolution stone sockets on a single enchant.
 *
 * <p>Open this menu by shift+left-clicking an enchant slot in {@link PickaxeMenu}. The menu
 * displays three socket slots showing any applied stones along with their passive effect
 * descriptions. Players apply and remove stones with left-click.
 *
 * <h3>Socket interactions</h3>
 *
 * <ul>
 *   <li><b>Left-click on socket with an Evolution Stone on cursor</b> — applies the stone to that
 *       socket. Any previously socketed stone is returned to the player's inventory.
 *   <li><b>Left-click on an occupied socket with an empty (or non-stone) cursor</b> — removes the
 *       stone and returns it to the player's inventory.
 *   <li><b>Back button (slots 20-21)</b> — closes this menu and re-opens the Pickaxe Menu.
 * </ul>
 */
public class StoneSocketMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("stone_socket");
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private static final int SLOT_ENCHANT_HEADER = 22;

  /**
   * All possible socket slot positions (up to MAX_SOCKET_COUNT). Prestige unlocks each in order.
   */
  private static final int[] SOCKET_SLOTS = {10, 12, 14, 16};

  /** Back button occupies two slots; both carry an identical item and trigger the same action. */
  private static final int[] SLOT_BACK = {20, 21};

  /** Info button occupies two slots; both carry an identical hover-only item. */
  private static final int[] SLOT_INFO = {23, 24};

  /**
   * Same slot numbers as {@link PickaxeMenu}'s evolution/essence/pickaxe buttons, so they land in
   * identical positions across the shutter transition between the two menus.
   */
  private static final int[] EVOLUTION_SLOTS = {29, 38, 47};

  private static final int[] ESSENCE_SLOTS = {33, 42, 51};
  private static final int PICKAXE_SLOT = 40;

  /** Per-player state: which enchant display name this player is currently viewing. */
  private final Map<UUID, String> pendingEnchants = new ConcurrentHashMap<>();

  /** Per-player animation frame counter for the vessel progress icons, mirroring PickaxeMenu. */
  private final Map<UUID, Integer> animationFrames = new ConcurrentHashMap<>();

  /**
   * Per-player shutter animation frame (1-{@value ShutterFrames#FRAME_COUNT}), present only while a
   * transition to/from {@link PickaxeMenu} is covering/uncovering this menu.
   */
  private final Map<UUID, Integer> shutterFrame = new ConcurrentHashMap<>();

  /** Ticks between shutter animation frames. */
  private static final int SHUTTER_STEP_TICKS = 2;

  /**
   * Spacer count (in {@code 七} characters) before the shutter glyph, positioning it over the socket
   * frame art. Placeholder — needs in-game tuning.
   */
  private static final int BACKGROUND_OFFSET = 52;

  /**
   * Exact pixel offset (via {@link PixelSpace}) from "⠣" (plus the vessel icons, when present) to
   * the shutter glyph, positioning it over the socket frame art. Tunable in-game via {@code /e
   * vesselcalib} without a rebuild/restart.
   */
  private static int SHUTTER_OFFSET = -65;

  /**
   * Exact pixel offset (via {@link PixelSpace}) from the end of the socket icon segment to the
   * essence vessel icon. Tunable in-game via {@code /e vesselcalib} without a rebuild/restart.
   */
  private static int ESSENCE_ICON_OFFSET = -113;

  /** Exact pixel offset from the essence icon to the evolve vessel icon. */
  private static int EVOLVE_ICON_OFFSET = -105;

  /**
   * Temporary calibration hooks for {@code /e vesselcalib} so {@link #ESSENCE_ICON_OFFSET}, {@link
   * #EVOLVE_ICON_OFFSET}, and {@link #SHUTTER_OFFSET} can be tuned in-game without a
   * rebuild/restart. Remove alongside the command once alignment is finalized and the defaults
   * above are updated to match.
   */
  public static void setEssenceIconOffset(int value) {
    ESSENCE_ICON_OFFSET = value;
  }

  public static int getEssenceIconOffset() {
    return ESSENCE_ICON_OFFSET;
  }

  public static void setEvolveIconOffset(int value) {
    EVOLVE_ICON_OFFSET = value;
  }

  public static int getEvolveIconOffset() {
    return EVOLVE_ICON_OFFSET;
  }

  public static void setShutterOffset(int value) {
    SHUTTER_OFFSET = value;
  }

  public static int getShutterOffset() {
    return SHUTTER_OFFSET;
  }

  /**
   * Rendered pixel width of {@code socket_menu.png} (the {@code ⠣} background), trimmed to its
   * rightmost non-transparent column — this is where the cursor sits, in pixels, immediately after
   * the background image is drawn. Measured directly from the source PNG after binarizing its alpha
   * channel (Minecraft's bitmap font glyphs require fully-opaque-or-fully-transparent pixels; the
   * source art had a few semi-transparent anti-aliased edge pixels that were quietly inflating
   * every trimmed-width measurement below by several pixels — that was the actual cause of the
   * growing per-socket drift, not a math error).
   */
  private static final int BACKGROUND_RENDERED_WIDTH = 236;

  /**
   * X pixel of the <em>center</em> (within {@code socket_menu.png}'s own 256px-wide canvas) of each
   * of the 4 socket octagons, left to right, measured by scanning for the octagons' dark fill
   * colour (each spans 25px, e.g. the first is columns 65-89). Index N corresponds to {@link
   * #SOCKET_SLOTS}[N] / {@code enchant.socketStones}[N]. Icons are centered within this, not
   * left-aligned, using each glyph's own measured width from {@link #GLYPH_WIDTH}.
   */
  private static final int[] SOCKET_CENTER_X = {75, 105, 135, 163};

  /**
   * Temporary calibration hook for {@code /e socketcalib} so {@link #SOCKET_CENTER_X} can be tuned
   * in-game without a rebuild/restart. Remove alongside the command once alignment is finalized and
   * the values above are updated to match.
   *
   * @param socketIndex index into {@link #SOCKET_CENTER_X} (0-3)
   * @param value the new centre X pixel value
   */
  public static void setSocketCenterX(int socketIndex, int value) {
    SOCKET_CENTER_X[socketIndex] = value;
  }

  /** Returns a copy of the current (possibly live-tuned) {@link #SOCKET_CENTER_X} values. */
  public static int[] getSocketCenterX() {
    return SOCKET_CENTER_X.clone();
  }

  /**
   * Rendered pixel width of each socket glyph (trimmed to its rightmost non-transparent column
   * after alpha binarization), keyed by the glyph character itself. Used both to center the icon
   * within its socket and to compute the return jump back to the reference point afterward.
   * Measured directly from the source PNGs.
   */
  private static final Map<String, Integer> GLYPH_WIDTH =
      Map.of(
          "Ⅰ", 20,
          "Ⅱ", 20,
          "Ⅲ", 21,
          "Ⅳ", 20,
          "Ⅴ", 20,
          "⠤", 17,
          "⠥", 20);

  /**
   * Shutter frame at which the header row (slot {@value SLOT_ENCHANT_HEADER}) is cleared while
   * closing.
   */
  private static final int HEADER_CLEAR_FRAME = 10;

  /** Shutter frame at which the socket row ({@link #SOCKET_SLOTS}) is cleared while closing. */
  private static final int SOCKETS_CLEAR_FRAME = 20;

  /** Creates and registers the StoneSocketMenu. @param plugin the owning plugin */
  public StoneSocketMenu(JavaPlugin plugin) {
    super(plugin);
  }

  /**
   * Prepares the socket view for the given enchant and opens the menu for the player. The enchant
   * display name is stored per-player and consumed by {@link #populateMenu}.
   *
   * @param player the player to open the menu for
   * @param displayName the enchant's display name (e.g. "Efficiency", "Nuclear Shower")
   */
  public void openForEnchant(Player player, String displayName) {
    UUID uuid = player.getUniqueId();
    pendingEnchants.put(uuid, displayName);
    shutterFrame.put(uuid, ShutterFrames.FRAME_COUNT);
    openMenu(player);
    playOpeningShutter(player);
  }

  /**
   * Ramps the shutter from fully closed down to fully open, revealing the already-populated menu
   * underneath. Assumes {@link #shutterFrame} is currently set to {@link ShutterFrames#FRAME_COUNT}
   * for this player.
   *
   * @param player the player to animate for
   */
  private void playOpeningShutter(Player player) {
    UUID uuid = player.getUniqueId();
    playShutterSound(player);
    runFrameRamp(
        player,
        ShutterFrames.FRAME_COUNT,
        1,
        SHUTTER_STEP_TICKS,
        frame -> {
          shutterFrame.put(uuid, frame);
          updateTitle(player);
        },
        () -> {
          shutterFrame.remove(uuid);
          updateTitle(player);
        });
  }

  /**
   * Allows the player to pick up/place items from their own inventory while this menu is open, so
   * an Evolution Stone can be dragged onto the cursor and left-clicked into a socket.
   */
  @Override
  protected boolean cancelPlayerInventoryClicks() {
    return false;
  }

  /** Returns {@code true} if dynamic title. */
  @Override
  protected boolean isDynamicTitle() {
    return true;
  }

  /**
   * Returns {@code true} if dynamic title, gated on the same per-player preference as {@link
   * PickaxeMenu}.
   */
  @Override
  protected boolean isDynamicTitle(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return false;
    PlayerProfile profile = pdc.getLocal(player);
    return profile != null && profile.preferences.pickaxeMenuAnimation;
  }

  /** Returns the title update interval. */
  @Override
  protected int getTitleUpdateInterval() {
    return 10;
  }

  /** Returns the menu title. */
  @Override
  protected String getMenuTitle(Player player) {
    Integer shutter = shutterFrame.get(player.getUniqueId());
    // At rest (no active transition) the shutter sits on its nearly-fully-transparent open
    // keyframe; mid-transition it follows the real ramp frame, same as before.
    int shutterGlyphFrame = shutter != null ? shutter : 1;

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe pickaxeProfile = pdc != null ? pdc.getPickaxeForPlayer(player) : null;

    String vessels = "";
    if (pickaxeProfile != null) {
      int frame =
          animationFrames.merge(player.getUniqueId(), 1, (cur, inc) -> cur >= 5 ? 1 : cur + 1);
      String essenceIcon = VesselProgress.getEssenceProgress(pickaxeProfile, frame);
      String evolveIcon = VesselProgress.getEvolveProgress(pickaxeProfile, frame);
      vessels =
          PixelSpace.of(ESSENCE_ICON_OFFSET)
              + essenceIcon
              + PixelSpace.of(EVOLVE_ICON_OFFSET)
              + evolveIcon;
    }

    // Socket icons must be drawn immediately after "⠣" with nothing in between — their own
    // jump/return math assumes the cursor is exactly at BACKGROUND_RENDERED_WIDTH when it starts,
    // and always returns there when it finishes, so appending vessels/shutter afterward is safe.
    // The shutter glyph is drawn last so it composites on top of the stones (later-drawn glyphs
    // overlay earlier ones) — that's what lets the stones fade in/out as the shutter opens/closes
    // instead of only popping in once the transition fully completes.
    return "&f"
        + "七".repeat(BACKGROUND_OFFSET)
        + "⠣"
        + buildSocketIconsSegment(player)
        + vessels
        + PixelSpace.of(SHUTTER_OFFSET)
        + ShutterFrames.glyph(shutterGlyphFrame);
  }

  /**
   * Builds the segment of the title string that draws each socket's icon (rarity gem, locked, or
   * unlocked-but-empty) at its measured position over the socket frame art.
   *
   * <p>Each icon is positioned independently: jump from the fixed reference point (immediately
   * after the background image) to that socket's center minus half the icon's own width — i.e.
   * centered within the socket, not left-aligned — draw the icon, then jump back by the icon's own
   * width to restore the reference point before the next one. This means no icon's position can
   * ever be affected by another icon's presence or width.
   *
   * @param player the player viewing the menu
   * @return the composed icon segment
   */
  private String buildSocketIconsSegment(Player player) {
    String displayName = pendingEnchants.get(player.getUniqueId());
    if (displayName == null) return "";

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe profile = pdc != null ? pdc.getPickaxeForPlayer(player) : null;
    if (profile == null) return "";

    EnchantData enchant = profile.enchants.getEnchant(displayName.toLowerCase());
    int unlockedSlots = EnchantData.getUnlockedSlotCount(profile.prestige);

    StringBuilder segment = new StringBuilder();
    for (int socketIndex = 0; socketIndex < SOCKET_SLOTS.length; socketIndex++) {
      String glyph = resolveSocketGlyph(enchant, socketIndex, unlockedSlots);
      int width = GLYPH_WIDTH.getOrDefault(glyph, 20);
      int targetLeftX = SOCKET_CENTER_X[socketIndex] - (width / 2);
      int jump = targetLeftX - BACKGROUND_RENDERED_WIDTH;
      segment.append(PixelSpace.of(jump));
      segment.append(glyph);
      segment.append(PixelSpace.of(-(jump + width)));
    }
    return segment.toString();
  }

  /**
   * Resolves the font glyph for a single socket: the locked icon if beyond the unlocked count, the
   * rarity gem icon if a stone is socketed, or the blank {@code unlocked.png} placeholder for an
   * unlocked-but-empty socket. Every state always draws <em>some</em> same-sized image — leaving a
   * gap empty here would mean that socket contributes no image width to the cursor advance, so
   * every icon drawn after it would shift position depending on whether earlier sockets happened to
   * be filled or not.
   */
  private String resolveSocketGlyph(EnchantData enchant, int socketIndex, int unlockedSlots) {
    if (socketIndex >= unlockedSlots) return "⠤";

    String rarity = enchant != null ? enchant.socketStones[socketIndex] : null;
    if (rarity == null) return "⠥";

    return switch (rarity) {
      case "Common" -> "Ⅰ";
      case "Uncommon" -> "Ⅱ";
      case "Rare" -> "Ⅲ";
      case "Epic" -> "Ⅳ";
      case "Legendary" -> "Ⅴ";
      default -> "⠥";
    };
  }

  /** Returns the menu size. */
  @Override
  protected int getMenuSize() {
    return 54;
  }

  /** Called when open occurs. */
  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
  }

  /** Called when close occurs. */
  @Override
  protected void onClose(Player player) {
    pendingEnchants.remove(player.getUniqueId());
    shutterFrame.remove(player.getUniqueId());
    animationFrames.remove(player.getUniqueId());
    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 1.1f);
  }

  /** Refreshes the menu contents and re-renders the title so socket icon changes are reflected. */
  @Override
  public void refresh(Player player) {
    super.refresh(player);
    updateTitle(player);
  }

  /** Populate menu. */
  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    String displayName = pendingEnchants.get(player.getUniqueId());
    if (displayName == null) return;

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe profile = pdc != null ? pdc.getPickaxeForPlayer(player) : null;
    if (profile == null) return;

    EnchantData enchant = profile.enchants.getEnchant(displayName.toLowerCase());

    int unlockedSlots = EnchantData.getUnlockedSlotCount(profile.prestige);
    inventory.setItem(
        SLOT_ENCHANT_HEADER, buildEnchantHeader(player, displayName, enchant, unlockedSlots));

    for (int i = 0; i < SOCKET_SLOTS.length; i++) {
      if (i < unlockedSlots) {
        inventory.setItem(SOCKET_SLOTS[i], buildSocketItem(player, displayName, enchant, i));
      } else {
        inventory.setItem(SOCKET_SLOTS[i], buildLockedSocketItem(player, i));
      }
    }

    ItemStack backButton = buildBackButton(player);
    for (int slot : SLOT_BACK) {
      inventory.setItem(slot, backButton.clone());
    }

    ItemStack infoButton = buildInfoItem(player);
    for (int slot : SLOT_INFO) {
      inventory.setItem(slot, infoButton.clone());
    }

    ItemStack evolutionItem = buildEvolutionItem(player);
    for (int slot : EVOLUTION_SLOTS) {
      inventory.setItem(slot, evolutionItem.clone());
    }

    ItemStack essenceItem = buildEssenceItem(player);
    for (int slot : ESSENCE_SLOTS) {
      inventory.setItem(slot, essenceItem.clone());
    }

    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType() != Material.AIR) {
      inventory.setItem(PICKAXE_SLOT, buildHeldPickaxeDisplay(player, held));
    }
  }

  /**
   * Builds the display item for the player's held pickaxe, mirroring {@link
   * PickaxeMenu#populateMenu}'s equivalent block so the same slot looks identical across the
   * shutter transition between the two menus.
   */
  private ItemStack buildHeldPickaxeDisplay(Player player, ItemStack held) {
    ItemStack display = held.clone();
    FileConfiguration defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    int footerSize = defaultConfig.getStringList(ConfigPaths.Default.Pickaxe.LORE_FOOTER).size();
    String storageAction =
        menus.getString(
            ConfigPaths.Menus.PickaxeMenu.STORAGE_ACTION_LINE,
            " &8| &aLeft-Click &#AAAAAAto open pickaxe storage");
    String repairAction =
        menus.getString(
            ConfigPaths.Menus.PickaxeMenu.REPAIR_ACTION_LINE,
            " &8| &#FFEE00Right-Click &#AAAAAAto repair &#777777(&#E8890A5 &#777777Ancient Resin)");
    ItemMeta displayMeta = display.getItemMeta();
    if (displayMeta != null) {
      List<Component> lore = displayMeta.lore();
      List<Component> trimmed =
          lore != null && lore.size() > footerSize
              ? new ArrayList<>(lore.subList(0, lore.size() - footerSize))
              : (lore != null ? new ArrayList<>(lore) : new ArrayList<>());
      trimmed.add(Component.empty());
      trimmed.add(format(player, storageAction));
      trimmed.add(format(player, repairAction));
      trimmed.add(Component.empty());
      displayMeta.lore(trimmed);
      display.setItemMeta(displayMeta);
    }
    return display;
  }

  /**
   * Builds the invisible evolution-statistics item for {@link #EVOLUTION_SLOTS}, mirroring {@link
   * PickaxeMenu#buildEvolutionItem}.
   */
  private ItemStack buildEvolutionItem(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String rawName =
        menus.getString(ConfigPaths.Menus.PickaxeMenu.EVOLVE_NAME, "&#00BFFF⭐ &#FFFFFF&lEvolution");
    List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PickaxeMenu.EVOLVE_LORE);

    PlayerDataCenter evoPdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe evoProfile = evoPdc != null ? evoPdc.getPickaxeForPlayer(player) : null;
    boolean canEvolve = evoProfile != null && evoProfile.atomVessel.crystals > 0;

    List<Component> lore = new ArrayList<>(rawLore.size());
    for (String line : rawLore) {
      if (line.contains("Left-Click") && line.contains("evolve")) {
        lore.add(
            format(
                player,
                canEvolve
                    ? " &8| &aLeft-Click &#AAAAAAto fill the canister."
                    : " &8| &#888888Not enough evolution points."));
      } else {
        lore.add(format(player, line));
      }
    }

    return buildInvisibleMapItem(format(player, rawName), lore);
  }

  /**
   * Builds the invisible essence/enchant-roll statistics item for {@link #ESSENCE_SLOTS}, mirroring
   * {@link PickaxeMenu#buildEssenceItem}.
   */
  private ItemStack buildEssenceItem(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String rawName =
        menus.getString(ConfigPaths.Menus.PickaxeMenu.ESSENCE_NAME, "&#9932CC🔥 &#FFFFFF&lEssence");
    List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PickaxeMenu.ESSENCE_LORE);

    PlayerDataCenter essPdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe essProfile = essPdc != null ? essPdc.getPickaxeForPlayer(player) : null;
    boolean canRoll = essProfile != null && essProfile.essenceVessel.crystals > 0;

    List<Component> lore = new ArrayList<>(rawLore.size());
    for (String line : rawLore) {
      if (line.contains("Left-Click") && line.contains("roll")) {
        lore.add(
            format(
                player,
                canRoll
                    ? " &8| &aLeft-Click &#AAAAAAto roll an enchant."
                    : " &8| &#888888Not enough essence."));
      } else {
        lore.add(format(player, line));
      }
    }

    return buildInvisibleMapItem(format(player, rawName), lore);
  }

  /** Handles click. */
  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    if (shutterFrame.containsKey(player.getUniqueId())) {
      return true;
    }

    for (int backSlot : SLOT_BACK) {
      if (slot == backSlot) {
        handleBackButton(player);
        return true;
      }
    }

    for (int i = 0; i < SOCKET_SLOTS.length; i++) {
      if (slot == SOCKET_SLOTS[i]) {
        handleSocketClick(player, i, event);
        return true;
      }
    }

    for (int s : EVOLUTION_SLOTS) {
      if (slot == s) {
        handleEvolutionClick(player);
        return true;
      }
    }

    for (int s : ESSENCE_SLOTS) {
      if (slot == s) {
        handleEnchantRollClick(player);
        return true;
      }
    }

    if (slot == PICKAXE_SLOT) {
      if (event.isRightClick()) {
        PickaxeManager.getInstance().repairPickaxe(player, () -> closeMenu(player));
      } else {
        PickaxeLimitMenu storageMenu =
            (PickaxeLimitMenu) MenuRegistry.getInstance().get("pickaxe_limit");
        if (storageMenu != null) {
          storageMenu.openMenu(player);
        }
      }
      return true;
    }

    return true;
  }

  /**
   * Handles a click on the essence/enchant roll slot, mirroring {@link
   * PickaxeMenu#handleEnchantRollClick}.
   */
  private void handleEnchantRollClick(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return;
    Pickaxe profile = pdc.getPickaxeForPlayer(player);
    if (profile == null) return;

    PickaxeManager.getInstance().startEnchantRoll(player, profile, () -> closeMenu(player));
  }

  /** Handles a click on the evolution slot, mirroring {@link PickaxeMenu#handleEvolutionClick}. */
  private void handleEvolutionClick(Player player) {
    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;
    Pickaxe profile = playerDC.getPickaxeForPlayer(player);
    if (profile == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    PickaxeManager.getInstance()
        .startEvolution(player, profile, playerProfile, () -> closeMenu(player));
  }

  /**
   * Plays the closing shutter animation over this menu, clearing the header and socket rows in sync
   * with the shutter's sweep so they appear to vanish behind it, then hands off to {@link
   * PickaxeMenu} with its own entrance shutter animation. No {@code closeInventory()} call is made
   * anywhere in this path — {@link PickaxeMenu#openWithEntranceShutter} opens the next inventory
   * directly, which is what avoids the close/reopen flicker.
   *
   * @param player the player who clicked back
   */
  private void handleBackButton(Player player) {
    PickaxeMenu pickaxeMenu = (PickaxeMenu) MenuRegistry.getInstance().get("pickaxe");
    if (pickaxeMenu == null) return;

    UUID uuid = player.getUniqueId();
    boolean[] headerCleared = {false};
    boolean[] socketsCleared = {false};

    playShutterSound(player);
    runFrameRamp(
        player,
        1,
        ShutterFrames.FRAME_COUNT,
        SHUTTER_STEP_TICKS,
        frame -> {
          shutterFrame.put(uuid, frame);
          updateTitle(player);

          Inventory inventory = openInventories.get(uuid);
          if (inventory == null) return;

          if (!headerCleared[0] && frame >= HEADER_CLEAR_FRAME) {
            inventory.setItem(SLOT_ENCHANT_HEADER, null);
            headerCleared[0] = true;
          }
          if (!socketsCleared[0] && frame >= SOCKETS_CLEAR_FRAME) {
            for (int socketSlot : SOCKET_SLOTS) {
              inventory.setItem(socketSlot, null);
            }
            socketsCleared[0] = true;
          }
        },
        () -> {
          shutterFrame.remove(uuid);
          pickaxeMenu.openWithEntranceShutter(player);
        });
  }

  /** Returns the menu type. */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  /** Handles socket click. */
  private void handleSocketClick(Player player, int socketIndex, InventoryClickEvent event) {
    String displayName = pendingEnchants.get(player.getUniqueId());
    if (displayName == null) return;

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe profile = pdc != null ? pdc.getPickaxeForPlayer(player) : null;
    if (profile == null) return;

    if (socketIndex >= EnchantData.getUnlockedSlotCount(profile.prestige)) return;

    EnchantData enchant = profile.enchants.getEnchant(displayName.toLowerCase());
    if (enchant == null) return;

    if (!event.isLeftClick()) return;

    ItemStack cursor = event.getCursor();
    if (!EvolutionStone.isEvolutionStone(cursor)) {
      String existing = enchant.socketStones[socketIndex];
      if (existing == null) {
        player.sendMessage(
            formatMessage(
                player,
                "%evolv3_prefix% &#CCCCCCHold an &#00FFFFEvolution Stone &#CCCCCCin your cursor to socket it."));
        return;
      }

      RarityConfig rarity = EnchantAnimationConfig.getRarityByName(existing);
      if (rarity != null) {
        ItemStack stone = EvolutionStone.create(rarity, displayName);
        if (player.getInventory().firstEmpty() >= 0) {
          player.getInventory().addItem(stone);
        } else {
          player.getWorld().dropItemNaturally(player.getLocation(), stone);
        }
      }
      enchant.socketStones[socketIndex] = null;
      profile.save(null);
      player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.0f);
      refresh(player);
      return;
    }

    String rarityName = EvolutionStone.getRarityName(cursor);
    if (rarityName == null) return;

    String stonEnchant = EvolutionStone.getEnchantName(cursor);
    if (stonEnchant != null && !stonEnchant.equalsIgnoreCase(displayName)) {
      player.sendMessage(
          formatMessage(
              player,
              "%evolv3_prefix% &#CCCCCCThat stone is bound to &#00FFFF"
                  + stonEnchant
                  + "&#CCCCCC, not &#00FFFF"
                  + displayName
                  + "&#CCCCCC."));
      return;
    }

    String existingRarity = enchant.socketStones[socketIndex];
    if (existingRarity != null) {
      RarityConfig existing = EnchantAnimationConfig.getRarityByName(existingRarity);
      if (existing != null) {
        ItemStack returnStone = EvolutionStone.create(existing, displayName);
        if (player.getInventory().firstEmpty() >= 0) {
          player.getInventory().addItem(returnStone);
        } else {
          player.getWorld().dropItemNaturally(player.getLocation(), returnStone);
        }
      }
    }

    for (int i = 0; i < EnchantData.MAX_SOCKET_COUNT; i++) {
      if (i != socketIndex && rarityName.equals(enchant.socketStones[i])) {
        player.sendMessage(
            formatMessage(
                player,
                "%evolv3_prefix% &#CCCCCCYou already have a &#00FFFF"
                    + rarityName
                    + " &#CCCCCCstone socketed in this enchant."));
        return;
      }
    }

    enchant.socketStones[socketIndex] = rarityName;
    profile.save(null);

    cursor.setAmount(cursor.getAmount() - 1);
    player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);

    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.9f, 1.5f);
    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.2f);
    refresh(player);
  }

  /** Builds enchant header. */
  private ItemStack buildEnchantHeader(
      Player player, String displayName, EnchantData enchant, int unlockedSlots) {
    ItemStack item = EnchantAnimationConfig.createEnchantItem(displayName);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    var option = EnchantAnimationConfig.getOption(displayName);
    var rarity = option != null ? option.rarity() : null;
    String rarityHex =
        rarity != null ? String.format("%06X", rarity.textColor().value()) : "CCCCCC";
    String cc = "&#" + rarityHex;
    String rarityName = rarity != null ? rarity.displayName() : "Unknown";

    meta.displayName(format(player, cc + "&l" + displayName));

    List<Component> lore = new ArrayList<>();
    lore.add(format(player, " " + cc + rarityName));
    lore.add(Component.empty());
    lore.add(format(player, " &#FFFFFF&lStatistics"));
    if (enchant != null) {
      lore.add(
          format(
              player,
              " &8| &#888888Level: &#FFFFFF" + enchant.level + " &8/ &7" + enchant.maxLevel));
      lore.add(
          format(
              player,
              " &8| &#888888Sockets: &#FFFFFF"
                  + enchant.getSocketedCount()
                  + " &8/ &7"
                  + unlockedSlots));
    } else {
      lore.add(format(player, " &8| &#FF5555This enchant has not been unlocked."));
    }
    lore.add(Component.empty());
    lore.add(format(player, " &#FFFFFF&lSocket Guide"));
    lore.add(format(player, " &8| &aLeft-Click &#AAAAAAa socket with a stone to apply."));
    lore.add(format(player, " &8| &cLeft-Click &#AAAAAAan occupied socket to remove."));
    lore.add(Component.empty());
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  /** Builds socket item. */
  private ItemStack buildSocketItem(
      Player player, String enchantDisplayName, EnchantData enchant, int socketIndex) {
    String storedRarity = (enchant != null) ? enchant.socketStones[socketIndex] : null;

    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.STONES);

    if (storedRarity != null) {
      RarityConfig rarity = EnchantAnimationConfig.getRarityByName(storedRarity);
      if (rarity != null) {
        String removeHint =
            cfg != null
                ? cfg.getString(
                    ConfigPaths.Stones.SocketMenu.FILLED_REMOVE_HINT,
                    " &cLeft-Click &#AAAAAAto remove this stone.")
                : " &cLeft-Click &#AAAAAAto remove this stone.";

        Component name = EvolutionStone.buildDisplayName(rarity, enchantDisplayName);
        List<Component> lore =
            new ArrayList<>(EvolutionStone.buildSharedLore(rarity, enchantDisplayName));
        lore.add(format(player, removeHint));
        lore.add(Component.empty());

        ItemStack item = buildInvisibleMapItem(name, lore);
        ItemTagHelper.applyTooltipStyle(item, storedRarity.toLowerCase());
        return item;
      }
    }

    String emptyName =
        cfg != null
            ? cfg.getString(
                ConfigPaths.Stones.SocketMenu.EMPTY_NAME,
                "&#888888Socket {index} &#444444\u2014 Empty")
            : "&#888888Socket {index} &#444444\u2014 Empty";
    String howToHeader =
        cfg != null
            ? cfg.getString(
                ConfigPaths.Stones.SocketMenu.EMPTY_HOW_TO_HEADER, " &#FFFFFF&lHow to Socket")
            : " &#FFFFFF&lHow to Socket";
    List<String> howToLines =
        cfg != null
            ? cfg.getStringList(ConfigPaths.Stones.SocketMenu.EMPTY_HOW_TO_LINES)
            : List.of(
                " &8| &#888888Hold an &#00FFFFEvolution Stone",
                " &8| &#888888in your cursor and left-click.");

    Component name = format(player, emptyName.replace("{index}", String.valueOf(socketIndex + 1)));
    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    lore.add(format(player, howToHeader));
    for (String line : howToLines) {
      lore.add(format(player, line));
    }
    lore.add(Component.empty());
    return buildInvisibleMapItem(name, lore);
  }

  /** Builds locked socket item. */
  private ItemStack buildLockedSocketItem(Player player, int socketIndex) {
    int requiredPrestige = socketIndex;
    Component name = format(player, "&#FF4444&lLocked Socket");
    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    lore.add(format(player, " &#888888Requires &#FFD700Prestige " + requiredPrestige));
    lore.add(Component.empty());
    lore.add(format(player, " &#888888Prestige your pickaxe to"));
    lore.add(format(player, " &#888888unlock this socket slot."));
    lore.add(Component.empty());
    return buildInvisibleMapItem(name, lore);
  }

  /**
   * Creates a MAP item with custom model data 1 (invisible via resource pack). The real graphic is
   * drawn by the menu title at this socket's position; this item only carries the hover lore,
   * mirroring the vessel items in {@link PickaxeMenu}.
   *
   * @param displayName the display name component
   * @param lore the lore lines to apply
   * @return the configured ItemStack
   */
  @SuppressWarnings("deprecation")
  private ItemStack buildInvisibleMapItem(Component displayName, List<Component> lore) {
    ItemStack item = new ItemStack(Material.MAP);
    ItemMeta meta = item.getItemMeta();

    meta.displayName(displayName);
    meta.setCustomModelData(1);
    meta.lore(lore);

    item.setItemMeta(meta);
    ItemStackExtensions.setData(item, "id", "1");
    return item;
  }

  /** Builds the hidden-map back button, placed identically into both {@link #SLOT_BACK} slots. */
  private ItemStack buildBackButton(Player player) {
    Component name = format(player, "&c&l\u2190 Back");
    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    lore.add(format(player, " &#888888Return to your pickaxe."));
    lore.add(Component.empty());
    return buildInvisibleMapItem(name, lore);
  }

  /**
   * Builds the hidden-map info button explaining what evolution sockets are, placed identically
   * into both {@link #SLOT_INFO} slots. Hover-only \u2014 {@link #handleClick} has no action for
   * it.
   */
  private ItemStack buildInfoItem(Player player) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.STONES);
    String rawName =
        cfg != null
            ? cfg.getString(ConfigPaths.Stones.SocketMenu.INFO_NAME, "&#00BFFF&lEvolution Sockets")
            : "&#00BFFF&lEvolution Sockets";
    List<String> rawLore =
        cfg != null ? cfg.getStringList(ConfigPaths.Stones.SocketMenu.INFO_LORE) : List.of();

    Component name = format(player, rawName);
    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    for (String line : rawLore) {
      lore.add(format(player, line.replace("{max}", String.valueOf(EnchantData.MAX_SOCKET_COUNT))));
    }
    lore.add(Component.empty());
    return buildInvisibleMapItem(name, lore);
  }

  /**
   * Processes hex colour codes via MatrixColorAPI, then deserialises to a non-italic Adventure
   * component. Use this instead of bare deserialize() for any string that may contain &#RRGGBB
   * codes.
   */
  private Component format(Player player, String message) {
    return LEGACY
        .deserialize(formatMessage(player, message))
        .decoration(TextDecoration.ITALIC, false);
  }
}
