package org.benski.evolv3.menus.pickaxe;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.ItemTagHelper;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.enchant.EnchantSelectionManager;
import org.benski.evolv3.core.managers.enchant.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.enchant.StoneEffectRegistry;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.passive.FortuneEnchant;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.items.AncientResin;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Menu displaying a player's pickaxe statistics, enchant roll controls, and the full enchant grid
 * where unlocked enchants can be upgraded with essence.
 */
public class PickaxeMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("pickaxe");
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  /**
   * Describes how a specific stone rarity affects an enchant's displayed trigger chance.
   *
   * @param rarity the rarity name that triggers this bonus (e.g. "Common")
   * @param delta the amount to add, or the override value when {@code override} is true
   * @param override if true, sets the chance to {@code delta} and stops further processing
   */
  private record StoneBonus(String rarity, double delta, boolean override) {}

  /**
   * Result of {@link #resolveStoneChance}: adjusted chance and colored diamond indicator string.
   */
  private record StoneChanceResult(double chance, String indicator) {}

  /**
   * Maps enchant internal name → ordered list of stone bonuses that affect displayed chance.
   * Override entries are listed before additives so sequential processing handles precedence
   * correctly.
   */
  private static final Map<String, List<StoneBonus>> STONE_CHANCE_BONUSES;

  static {
    Map<String, List<StoneBonus>> m = new HashMap<>();
    m.put(Enchants.UNBREAKING, List.of(new StoneBonus("Common", 5.0, false)));
    m.put(
        Enchants.ALCHEMY,
        List.of(new StoneBonus("Legendary", 100.0, true), new StoneBonus("Uncommon", 10.0, false)));
    m.put(Enchants.AUTO_SELL, List.of(new StoneBonus("Uncommon", 5.0, false)));
    m.put(Enchants.DRILL, List.of(new StoneBonus("Rare", 5.0, false)));
    m.put(Enchants.FROSTBITE, List.of(new StoneBonus("Uncommon", 3.0, false)));
    m.put(Enchants.FURNACE, List.of(new StoneBonus("Legendary", 100.0, true)));
    m.put(Enchants.LAZER, List.of(new StoneBonus("Common", 2.0, false)));
    m.put(
        Enchants.JACKHAMMER,
        List.of(new StoneBonus("Common", 1.0, false), new StoneBonus("Uncommon", 2.0, false)));
    m.put(Enchants.MERCHANT, List.of(new StoneBonus("Common", 2.0, false)));
    m.put(
        Enchants.RADIOACTIVE,
        List.of(new StoneBonus("Common", 1.0, false), new StoneBonus("Legendary", 5.0, false)));
    m.put(Enchants.NUCLEAR_SHOWER, List.of(new StoneBonus("Uncommon", 2.0, false)));
    m.put(Enchants.THOR, List.of(new StoneBonus("Common", 2.0, false)));
    m.put(
        Enchants.VORTEX,
        List.of(new StoneBonus("Common", 2.0, false), new StoneBonus("Uncommon", 3.0, false)));
    STONE_CHANCE_BONUSES = Collections.unmodifiableMap(m);
  }

  /**
   * Computes the stone-adjusted chance and a colored diamond indicator for an enchant.
   *
   * <p>Bonuses are processed in order. An override entry sets the chance to a fixed value and halts
   * further processing. Additive entries are all applied. The indicator appends a colored ◆ for
   * every matching socketed rarity.
   *
   * @param enchant the enchant whose sockets are inspected
   * @param baseChance the base trigger chance before stone adjustments
   * @return adjusted chance and indicator string (empty string if no stones matched)
   */
  private static StoneChanceResult resolveStoneChance(EnchantData enchant, double baseChance) {
    List<StoneBonus> bonuses = STONE_CHANCE_BONUSES.get(enchant.name);
    if (bonuses == null) return new StoneChanceResult(baseChance, "");

    double chance = baseChance;
    Set<String> matched = new LinkedHashSet<>();

    for (StoneBonus bonus : bonuses) {
      if (enchant.hasStone(bonus.rarity())) {
        matched.add(bonus.rarity());
        if (bonus.override()) {
          chance = bonus.delta();
          break;
        } else {
          chance += bonus.delta();
        }
      }
    }

    if (matched.isEmpty()) return new StoneChanceResult(baseChance, "");

    StringBuilder indicator = new StringBuilder(" ");
    for (String s : enchant.socketStones) {
      if (s != null && matched.contains(s)) {
        RarityConfig rc = EnchantAnimationConfig.getRarityByName(s);
        if (rc != null) {
          indicator.append("&#").append(String.format("%06X", rc.textColor().value())).append("◆");
        }
      }
    }
    return new StoneChanceResult(chance, indicator.toString());
  }

  private static final int[] EVOLUTION_SLOTS = {29, 38, 47};
  private static final int[] ESSENCE_SLOTS = {33, 42, 51};
  private static final int PICKAXE_SLOT = 40;

  /** 7 columns × 3 rows of enchant slots occupying the top half of the GUI. */
  private static final int[] ENCHANT_SLOTS = {
    1, 2, 3, 4, 5, 6, 7,
    10, 11, 12, 13, 14, 15, 16,
    19, 20, 21, 22, 23, 24, 25
  };

  /** Per-player animation frame counter so concurrent viewers don't share state. */
  private final Map<UUID, Integer> animationFrames = new ConcurrentHashMap<>();

  /** Creates and registers the PickaxeMenu. @param plugin the owning plugin */
  public PickaxeMenu(JavaPlugin plugin) {
    super(plugin);
  }

  /** Called when open occurs. */
  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.3f);
  }

  /** Called when close occurs. */
  @Override
  protected void onClose(Player player) {
    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
    animationFrames.remove(player.getUniqueId());
  }

  /** Returns {@code true} if dynamic title. */
  @Override
  protected boolean isDynamicTitle() {
    return true;
  }

  /** Returns {@code true} if dynamic title. */
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
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    var pickaxeProfile = pdc != null ? pdc.getPickaxeForPlayer(player) : null;
    if (pickaxeProfile == null) {
      return "&cLoading...";
    }

    int frame =
        animationFrames.merge(player.getUniqueId(), 1, (cur, inc) -> cur >= 5 ? 1 : cur + 1);

    String essenceIcon = getEssenceProgress(pickaxeProfile, frame);
    String evolveIcon = getEvolveProgress(pickaxeProfile, frame);

    return "&f"
        + "七".repeat(52)
        + "⒑"
        + "七".repeat(89)
        + essenceIcon
        + "七".repeat(105)
        + evolveIcon;
  }

  /** Returns the menu size. */
  @Override
  protected int getMenuSize() {
    return 54;
  }

  /** Populate menu. */
  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return;
    Pickaxe profile = pdc.getPickaxeForPlayer(player);
    if (profile == null) return;

    buildEnchantSlots(player, inventory, profile);

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
        trimmed.add(deserialize(formatMessage(player, storageAction)));
        trimmed.add(deserialize(formatMessage(player, repairAction)));
        trimmed.add(Component.empty());
        displayMeta.lore(trimmed);
        display.setItemMeta(displayMeta);
      }
      inventory.setItem(PICKAXE_SLOT, display);
    }
  }

  /** Handles click. */
  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe clickProfile = pdc != null ? pdc.getPickaxeForPlayer(player) : null;
    if (clickProfile != null) {
      List<EnchantData> sortedEnchants =
          clickProfile.enchants.items.stream()
              .sorted(
                  Comparator.comparingInt(
                      e ->
                          EnchantAnimationConfig.getEnchantRarityIndex(
                              EnchantAnimationConfig.getDisplayName(e.name))))
              .toList();
      for (int i = 0; i < ENCHANT_SLOTS.length; i++) {
        if (slot == ENCHANT_SLOTS[i]) {
          if (i < sortedEnchants.size()) {
            EnchantData enchant = sortedEnchants.get(i);
            String displayName = EnchantAnimationConfig.getDisplayName(enchant.name);
            if (event.isShiftClick() && event.isRightClick()) {
              handleEnchantSocketOpen(player, displayName);
            } else if (event.isRightClick()) {
              handleEnchantToggle(player, displayName);
            } else {
              handleEnchantSlotClick(player, displayName);
            }
          }
          return true;
        }
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
        handleRepair(player);
      } else {
        PickaxeLimitMenu storageMenu =
            (PickaxeLimitMenu) MenuRegistry.getInstance().get("pickaxe_limit");
        if (storageMenu != null) {
          storageMenu.openMenu(player);
        }
      }
      return true;
    }

    return false;
  }

  /** Returns the menu type. */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  /**
   * Populates the enchant grid slots with unlocked enchant items, filling from slot 0. Any
   * remaining slots beyond the player's unlocked count are filled with invisible MAP placeholders.
   *
   * @param player the player viewing the menu
   * @param inventory the inventory to populate
   * @param profile the player's pickaxe profile
   */
  private void buildEnchantSlots(Player player, Inventory inventory, Pickaxe profile) {
    List<EnchantData> unlocked =
        profile.enchants.items.stream()
            .sorted(
                Comparator.comparingInt(
                    e ->
                        EnchantAnimationConfig.getEnchantRarityIndex(
                            EnchantAnimationConfig.getDisplayName(e.name))))
            .toList();
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

    for (int i = 0; i < ENCHANT_SLOTS.length; i++) {
      int slot = ENCHANT_SLOTS[i];
      if (i < unlocked.size()) {
        EnchantData enchant = unlocked.get(i);
        String displayName = EnchantAnimationConfig.getDisplayName(enchant.name);
        inventory.setItem(
            slot, buildUnlockedEnchantItem(player, displayName, enchant, profile.prestige, menus));
      } else {
        inventory.setItem(slot, buildEmptyEnchantSlot(player, menus));
      }
    }
  }

  /**
   * Builds the display item for an enchant the player has unlocked. The skull icon and lore
   * template are read from {@code enchants.yml} via {@link EnchantAnimationConfig}. Tokens in the
   * lore ({@code {level}}, etc.) are replaced with live values before rendering.
   *
   * @param player the player (for formatting / PlaceholderAPI)
   * @param displayName the enchant's display name (e.g. "Auto-Smelt")
   * @param enchant the player's current enchant data
   * @return the configured ItemStack
   */
  private ItemStack buildUnlockedEnchantItem(
      Player player,
      String displayName,
      EnchantData enchant,
      int prestige,
      FileConfiguration menusConfig) {
    var option = EnchantAnimationConfig.getOption(displayName);
    RarityConfig rarity = option != null ? option.rarity() : null;

    String rarityHex = rarity != null ? rarity.textColor().asHexString().toUpperCase() : "#CCCCCC";
    String rarityColor = "&" + rarityHex;
    String rarityGlyph =
        rarity != null ? ItemTagHelper.getRarityGlyph(rarity.displayName().toLowerCase()) : null;
    String otherGlyph = ItemTagHelper.getTypeGlyph("other");
    String rarityStr =
        "&f" + (rarityGlyph != null ? rarityGlyph : "") + (otherGlyph != null ? otherGlyph : "");

    int level = enchant.level;
    int maxLevel = enchant.maxLevel;
    double chance = enchant.getCurrentChance();

    StoneChanceResult stoneChance = resolveStoneChance(enchant, chance);
    chance = stoneChance.chance();
    String stoneChanceIndicator = stoneChance.indicator();
    int cost = EnchantAnimationConfig.getEnchantCost(displayName) * level;
    double speedPerLevel =
        ConfigRegistry.getInstance()
            .get(ConfigNames.ENCHANTS)
            .getDouble(ConfigPaths.Enchants.Efficiency.SPEED_PER_LEVEL, 0.08);
    double speedBonus = level * speedPerLevel * 100;
    String stoneSpeedIndicator = "";
    if (enchant.name.equals(Enchants.EFFICIENCY)) {
      boolean hasLegendary = enchant.hasStone("Legendary");
      boolean hasCommon = enchant.hasStone("Common");
      if (hasLegendary || hasCommon) {
        double adj = speedPerLevel;
        if (hasLegendary) adj *= 2.0;
        speedBonus = level * adj * 100;
        if (hasCommon) speedBonus += 100.0;
        StringBuilder indicator = new StringBuilder(" ");
        for (String s : enchant.socketStones) {
          if ("Legendary".equals(s) || "Common".equals(s)) {
            RarityConfig rc = EnchantAnimationConfig.getRarityByName(s);
            if (rc != null) {
              indicator
                  .append("&#")
                  .append(String.format("%06X", rc.textColor().value()))
                  .append("◆");
            }
          }
        }
        stoneSpeedIndicator = indicator.toString();
      }
    }
    int fortuneDrops = FortuneEnchant.getDropAmount(level);
    String stoneDropsIndicator = "";
    if (displayName.equalsIgnoreCase(org.benski.evolv3.core.constants.Enchants.FORTUNE)) {
      boolean hasCommon = false, hasLegendary = false;
      for (String s : enchant.socketStones) {
        if ("Common".equals(s)) hasCommon = true;
        if ("Legendary".equals(s)) hasLegendary = true;
      }
      if (hasCommon || hasLegendary) {
        if (hasCommon) fortuneDrops += 1;
        if (hasLegendary) fortuneDrops *= 2;
        StringBuilder indicator = new StringBuilder(" ");
        for (String s : enchant.socketStones) {
          if ("Common".equals(s) || "Legendary".equals(s)) {
            RarityConfig rc = EnchantAnimationConfig.getRarityByName(s);
            if (rc != null)
              indicator
                  .append("&#")
                  .append(String.format("%06X", rc.textColor().value()))
                  .append("◆");
          }
        }
        stoneDropsIndicator = indicator.toString();
      }
    }

    List<String> rawLore = EnchantAnimationConfig.getEnchantLore(displayName);
    List<Component> lore = new ArrayList<>(rawLore.size());
    boolean isMax = enchant.isMaxLevel();
    String maxDisplay =
        ConfigRegistry.getInstance()
            .get(ConfigNames.ENCHANTS)
            .getString(ConfigPaths.Enchants.MAX_ENCHANT, "&6&lMAX");
    String stonesHeader =
        menusConfig.getString(ConfigPaths.Menus.PickaxeMenu.STONES_HEADER, " &#FFFFFF&lStones");
    String stonesFilledLine =
        menusConfig.getString(
            ConfigPaths.Menus.PickaxeMenu.STONES_FILLED_LINE,
            " &8| &#888888Stone {index}: &#{hex}{rarity} {enchant} Stone");
    String stonesEmptyLine =
        menusConfig.getString(
            ConfigPaths.Menus.PickaxeMenu.STONES_EMPTY_LINE,
            " &8| &#444444Stone {index}: &#333333Empty");
    String stonesActionLine =
        menusConfig.getString(
            ConfigPaths.Menus.PickaxeMenu.STONES_ACTION_LINE,
            " &aShift+Right-Click &#AAAAAAto manage stones.");
    for (String line : rawLore) {
      if (isMax && line.contains("{cost}")) {
        continue;
      }
      if (line.equals("{stones}")) {
        String internalEnchant = displayName.toLowerCase();
        int unlockedSockets = EnchantData.getUnlockedSlotCount(prestige);
        lore.add(deserialize(formatMessage(player, stonesHeader)));
        for (int si = 0; si < unlockedSockets; si++) {
          String stoneRarity = enchant.socketStones[si];
          if (stoneRarity != null) {
            RarityConfig stoneRarConfig = EnchantAnimationConfig.getRarityByName(stoneRarity);
            if (stoneRarConfig != null) {
              String sHex = String.format("%06X", stoneRarConfig.textColor().value());
              String effectText = StoneEffectRegistry.getEffect(internalEnchant, stoneRarity);
              String[] words = effectText.split(" ");
              StringBuilder chunk = new StringBuilder();
              boolean firstChunk = true;
              for (String word : words) {
                if (!chunk.isEmpty() && chunk.length() + word.length() + 1 > 30) {
                  String template = firstChunk ? stonesFilledLine : " &8| &#{hex}{effect}";
                  lore.add(
                      deserialize(
                          formatMessage(
                              player,
                              template
                                  .replace("{hex}", sHex)
                                  .replace("{rarity}", stoneRarity)
                                  .replace("{enchant}", displayName)
                                  .replace("{effect}", chunk.toString().trim()))));
                  firstChunk = false;
                  chunk = new StringBuilder();
                }
                chunk.append(word).append(" ");
              }
              if (!chunk.isEmpty()) {
                String template = firstChunk ? stonesFilledLine : " &8| &#{hex}{effect}";
                lore.add(
                    deserialize(
                        formatMessage(
                            player,
                            template
                                .replace("{hex}", sHex)
                                .replace("{rarity}", stoneRarity)
                                .replace("{enchant}", displayName)
                                .replace("{effect}", chunk.toString().trim()))));
              }
            }
          } else {
            lore.add(deserialize(formatMessage(player, stonesEmptyLine)));
          }
        }
        continue;
      }
      if (line.equals("{stones_action}")) {
        lore.add(deserialize(formatMessage(player, stonesActionLine)));
        continue;
      }
      if (line.isEmpty()) {
        lore.add(Component.empty());
        continue;
      }
      String levelDisplay = isMax ? maxDisplay : String.valueOf(level);
      String rawLine = isMax ? line.replace("&#888888/&#FFFFFF{maxLevel}", "") : line;
      String processed =
          rawLine
              .replace("{rarity}", rarityStr)
              .replace("{rarityColor}", rarityColor)
              .replace("{level}", levelDisplay)
              .replace("{maxLevel}", String.valueOf(maxLevel))
              .replace("{cost}", String.valueOf(cost))
              .replace("{chance}%", String.format("%.1f", chance) + "%" + stoneChanceIndicator)
              .replace("{chance}", String.format("%.1f", chance))
              .replace("{speed}", String.format("%.0f%%", speedBonus) + stoneSpeedIndicator)
              .replace("{drops}", String.valueOf(fortuneDrops) + "x" + stoneDropsIndicator)
              .replace("{status}", enchant.enabled ? "&#00FF88Enabled ✔" : "&#FF5555Disabled ✖");
      lore.add(deserialize(formatMessage(player, processed)));
    }

    StringBuilder stoneIconsBuilder = new StringBuilder();
    for (String stoneRarity : enchant.socketStones) {
      if (stoneRarity != null) {
        RarityConfig stoneRarConfig = EnchantAnimationConfig.getRarityByName(stoneRarity);
        String sHex =
            stoneRarConfig != null
                ? String.format("%06X", stoneRarConfig.textColor().value())
                : "FFFFFF";
        stoneIconsBuilder.append("&#").append(sHex).append("◆");
      }
    }
    String stoneIcons = stoneIconsBuilder.length() > 0 ? stoneIconsBuilder + " " : "";

    ItemStack item = EnchantAnimationConfig.createEnchantItem(displayName);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.displayName(
        deserialize(formatMessage(player, stoneIcons + rarityColor + "&l " + displayName)));
    meta.lore(lore);
    item.setItemMeta(meta);
    if (rarity != null) {
      ItemTagHelper.applyTooltipStyle(item, rarity.displayName().toLowerCase());
    }
    return item;
  }

  /**
   * Builds an invisible MAP placeholder for an empty enchant slot. Uses the LockedEnchant config
   * from menus.yml for name and lore text.
   *
   * @param player the player (for formatting)
   * @param menusConfig the menus.yml config
   * @return an invisible MAP ItemStack with placeholder text
   */
  private ItemStack buildEmptyEnchantSlot(Player player, FileConfiguration menusConfig) {
    String rawName = menusConfig.getString(ConfigPaths.Menus.PickaxeMenu.ENCHANT_LOCKED_NAME, " ");
    List<String> rawLore =
        menusConfig.getStringList(ConfigPaths.Menus.PickaxeMenu.ENCHANT_LOCKED_LORE);

    List<Component> lore = buildLoreFromConfig(player, rawLore);
    return buildInvisibleMapItem(deserialize(formatMessage(player, rawName)), lore);
  }

  /**
   * Handles a left-click on an enchant grid slot. If the enchant is unlocked, attempts to spend
   * essence to level it up. If locked, informs the player they need an enchant roll.
   *
   * @param player the player who clicked
   * @param displayName the enchant's display name for the clicked slot
   */
  private void handleEnchantSlotClick(Player player, String displayName) {
    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter == null) return;
    Pickaxe profile = playerDataCenter.getPickaxeForPlayer(player);
    if (profile == null) return;

    String internalName = displayName.toLowerCase();
    EnchantData enchant = profile.enchants.getEnchant(internalName);
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

    if (enchant == null) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_NOT_UNLOCKED,
              "%evolv3_prefix% &cThis enchant has not been unlocked yet.");
      player.sendMessage(formatMessage(player, msg));
      return;
    }

    var playerProfile = playerDataCenter.getLocal(player);
    if (playerProfile == null) return;

    boolean upgraded =
        PickaxeManager.getInstance()
            .upgradeEnchantWithEssence(player, profile, playerProfile, displayName);

    if (upgraded) {
      refresh(player);
    }
  }

  /**
   * Handles a shift+left-click on an enchant grid slot. Opens the stone socket panel for the
   * clicked enchant.
   *
   * @param player the player who shift-clicked
   * @param displayName the enchant's display name
   */
  private void handleEnchantSocketOpen(Player player, String displayName) {
    StoneSocketMenu socketMenu = (StoneSocketMenu) MenuRegistry.getInstance().get("stone_socket");
    if (socketMenu != null) {
      closeMenu(player);
      socketMenu.openForEnchant(player, displayName);
    }
  }

  /**
   * Handles a right-click on an enchant grid slot, toggling the enchant on or off.
   *
   * @param player the player who clicked
   * @param displayName the enchant's display name for the clicked slot
   */
  private void handleEnchantToggle(Player player, String displayName) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return;
    Pickaxe profile = pdc.getPickaxeForPlayer(player);
    if (profile == null) return;

    String internalName = displayName.toLowerCase();
    EnchantData enchant = profile.enchants.getEnchant(internalName);
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

    if (enchant == null) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_NOT_UNLOCKED,
              "%evolv3_prefix% &cThis enchant has not been unlocked yet.");
      player.sendMessage(formatMessage(player, msg));
      return;
    }

    enchant.enabled = !enchant.enabled;
    profile.save();

    String msgPath =
        enchant.enabled
            ? ConfigPaths.Messages.Pickaxe.ENCHANT_TOGGLE_ENABLED
            : ConfigPaths.Messages.Pickaxe.ENCHANT_TOGGLE_DISABLED;
    String fallback =
        enchant.enabled
            ? "%evolv3_prefix% &#00FF88✔ &#FFFFFF%enchant_name% &#CCCCCCenabled."
            : "%evolv3_prefix% &#FF5555✗ &#FFFFFF%enchant_name% &#CCCCCCdisabled.";
    String msg = messages.getString(msgPath, fallback).replace("%enchant_name%", displayName);
    player.sendMessage(formatMessage(player, msg));

    refresh(player);
  }

  /**
   * Handles a right-click on the pickaxe display slot, repairing the held pickaxe at the cost of
   * Ancient Resin from the player's inventory.
   *
   * @param player the player who right-clicked
   */
  private void handleRepair(Player player) {
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType() == Material.AIR
        || !org.benski.evolv3.core.helpers.Utils.isPickaxe(held.getType())) {
      FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.NO_PICKAXE,
              "%evolv3_prefix% &cYou must be holding a pickaxe to repair it.");
      player.sendMessage(formatMessage(player, msg));
      return;
    }

    ItemMeta heldMeta = held.getItemMeta();
    if (!(heldMeta instanceof Damageable dmg)) return;

    if (dmg.getDamage() == 0) {
      sendMessageList(player, ConfigPaths.Messages.PickaxeLimit.REPAIR_ALREADY_FULL);
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return;
    }

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int repairCost = config.getInt(ConfigPaths.Default.AncientResin.REPAIR_COST, 5);

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

    dmg.setDamage(0);
    held.setItemMeta(dmg);
    player.getInventory().setItemInMainHand(held);

    String pickaxeId = ItemStackExtensions.getData(held, "id");
    if (pickaxeId != null && !pickaxeId.isEmpty()) {
      PlayerDataCenter repairPdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
      Pickaxe repairProfile = repairPdc != null ? repairPdc.getPickaxeByItemId(pickaxeId) : null;
      if (repairProfile != null) {
        repairProfile.durability = 0;
        repairProfile.save(null);
      }
    }

    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    sendMessageList(player, ConfigPaths.Messages.PickaxeLimit.REPAIR_SUCCESS);
    closeMenu(player);
  }

  /** Sends message list. */
  private void sendMessageList(Player player, String path, String... replacements) {
    List<String> lines = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES).getStringList(path);
    for (String line : lines) {
      for (int i = 0; i + 1 < replacements.length; i += 2) {
        line = line.replace(replacements[i], replacements[i + 1]);
      }
      player.sendMessage(formatMessage(player, line));
    }
  }

  /** Count ancient resin in inventory. */
  private int countAncientResinInInventory(Player player) {
    int total = 0;
    for (ItemStack item : player.getInventory().getContents()) {
      if (AncientResin.isAncientResin(item)) total += item.getAmount();
    }
    return total;
  }

  /** Remove ancient resin from inventory. */
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

  /**
   * Handles a click on an essence/enchant roll slot. Deducts one enchant roll and starts the
   * enchant selection animation. The animation callback applies or upgrades the chosen enchant.
   *
   * @param player the player who clicked
   */
  private void handleEnchantRollClick(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return;
    Pickaxe profile = pdc.getPickaxeForPlayer(player);
    if (profile == null) return;

    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

    if (EnchantSelectionManager.getInstance().hasActiveSelection(player)) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_SELECTION_ALREADY_ACTIVE,
              "&cYou already have an enchant selection active!");
      player.sendMessage(formatMessage(player, msg));
      return;
    }

    if (EvolutionSelectionManager.getInstance().hasActiveEvolution(player)) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_SELECTION_ALREADY_ACTIVE,
              "&cAn evolution is already in progress!");
      player.sendMessage(formatMessage(player, msg));
      return;
    }

    if (profile.essenceVessel.crystals <= 0) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_NO_SPINS,
              "%evolv3_prefix% &cYou need at least 1 enchant spin to use this.");
      player.sendMessage(formatMessage(player, msg));
      return;
    }

    profile.essenceVessel.crystals--;
    profile.save();

    closeMenu(player);

    EnchantSelectionManager.getInstance()
        .startSelection(
            player,
            profile.key,
            (p, selected) -> {
              PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
              if (dc == null) return;
              Pickaxe updatedProfile = dc.getPickaxeForPlayer(p);
              if (updatedProfile == null) return;
              PickaxeManager.getInstance().applyEnchantFromSelection(p, selected, updatedProfile);
            });
  }

  /**
   * Handles a click on an evolution panel slot. Validates the evolution, deducts the roll, closes
   * the menu, rolls the stone rarity, and starts the {@link EvolutionAnimation}. The animation
   * callback applies the actual tier change and stone award when it completes.
   */
  private void handleEvolutionClick(Player player) {
    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;
    Pickaxe profile = playerDC.getPickaxeForPlayer(player);
    if (profile == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    PickaxeManager manager = PickaxeManager.getInstance();
    if (!manager.validateEvolution(player, profile)) return;

    EvolutionTier current = EvolutionTier.fromLevel(profile.evolution);
    EvolutionTier next = current.next();
    if (next == null) return;

    profile.atomVessel.crystals--;
    profile.save(null);

    RarityConfig stone = EnchantAnimationConfig.rollRarity();
    if (stone == null) return;

    closeMenu(player);

    final EvolutionTier finalCurrent = current;
    final EvolutionTier finalNext = next;
    final RarityConfig finalStone = stone;
    final String pickaxeKey = profile.key;

    EvolutionSelectionManager.getInstance()
        .startEvolution(
            player,
            pickaxeKey,
            finalCurrent,
            finalNext,
            finalStone,
            () ->
                manager.applyEvolutionResult(
                    player, profile, playerProfile, finalNext, finalStone));
  }

  /**
   * Builds the invisible evolution-statistics item for slots 29, 38, 47. The action hint at the
   * bottom is conditional on whether the player has evolution rolls.
   *
   * @param player the player viewing the menu
   * @return the built ItemStack
   */
  private ItemStack buildEvolutionItem(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String rawName =
        menus.getString(ConfigPaths.Menus.PickaxeMenu.EVOLVE_NAME, "&#00BFFF⭐ &#FFFFFF&lEvolution");
    List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PickaxeMenu.EVOLVE_LORE);

    PlayerDataCenter evoPdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe evoProfile = evoPdc != null ? evoPdc.getPickaxeForPlayer(player) : null;
    boolean canEvolve = evoProfile != null && evoProfile.atomVessel.crystals > 0;

    List<String> processedLore = new ArrayList<>(rawLore.size());
    for (String line : rawLore) {
      if (line.contains("Left-Click") && line.contains("evolve")) {
        processedLore.add(
            canEvolve
                ? " &8| &aLeft-Click &#AAAAAAto fill the canister."
                : " &8| &#888888Not enough evolution points.");
      } else {
        processedLore.add(line);
      }
    }

    List<Component> lore = buildLoreFromConfig(player, processedLore);
    return buildInvisibleMapItem(deserialize(formatMessage(player, rawName)), lore);
  }

  /**
   * Builds the invisible essence/enchant-roll statistics item for slots 33, 42, 51. The action hint
   * at the bottom is conditional on whether the player has enchant rolls.
   *
   * @param player the player viewing the menu
   * @return the built ItemStack
   */
  private ItemStack buildEssenceItem(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    String rawName =
        menus.getString(ConfigPaths.Menus.PickaxeMenu.ESSENCE_NAME, "&#9932CC🔥 &#FFFFFF&lEssence");
    List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PickaxeMenu.ESSENCE_LORE);

    PlayerDataCenter essPdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe essProfile = essPdc != null ? essPdc.getPickaxeForPlayer(player) : null;
    boolean canRoll = essProfile != null && essProfile.essenceVessel.crystals > 0;

    List<String> processedLore = new ArrayList<>(rawLore.size());
    for (String line : rawLore) {
      if (line.contains("Left-Click") && line.contains("roll")) {
        processedLore.add(
            canRoll
                ? " &8| &aLeft-Click &#AAAAAAto roll an enchant."
                : " &8| &#888888Not enough essence.");
      } else {
        processedLore.add(line);
      }
    }

    List<Component> lore = buildLoreFromConfig(player, processedLore);
    return buildInvisibleMapItem(deserialize(formatMessage(player, rawName)), lore);
  }

  /**
   * Creates a MAP item with custom model data 1 (invisible via resource pack).
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

  /**
   * Resolves a list of config lore strings via PlaceholderAPI and colour codes.
   *
   * @param player the player context for placeholder resolution
   * @param rawLore the raw lore list from config
   * @return formatted Adventure components
   */
  private List<Component> buildLoreFromConfig(Player player, List<String> rawLore) {
    List<Component> lore = new ArrayList<>(rawLore.size());
    for (String line : rawLore) {
      lore.add(deserialize(formatMessage(player, line)));
    }
    return lore;
  }

  /**
   * Generates a Unicode character representing the essence progress for a pickaxe profile.
   *
   * @param profile the player's pickaxe profile
   * @param animationFrame the current animation frame (1–5)
   * @return a single Unicode character for the progress icon
   */
  private String getEssenceProgress(Pickaxe profile, int animationFrame) {
    if (profile.essenceVessel.crystals > 0) {
      return getAnimatedProgressIcon(10, animationFrame);
    }

    int percentage =
        (int)
            Math.round((double) profile.essenceVessel.current / profile.essenceVessel.target * 100);
    int progressStep = Math.min(percentage / 10, 10);

    if (progressStep == 0) {
      return "↛";
    }

    return getAnimatedProgressIcon(progressStep, animationFrame);
  }

  /**
   * Maps a progress step and animation frame to a Unicode progress icon.
   *
   * @param progressStep the current progress level (1–10)
   * @param frame the current animation frame (1–5)
   * @return the corresponding Unicode character
   */
  private String getAnimatedProgressIcon(int progressStep, int frame) {
    String[][] progressIcons = {
      {"①", "②", "③", "④", "⑤"},
      {"⑥", "⑦", "⑧", "⑨", "⑩"},
      {"⑪", "⑫", "⑬", "⑭", "⑮"},
      {"⑯", "⑰", "⑱", "⑲", "⑳"},
      {"㉑", "㉒", "㉓", "㉔", "㉕"},
      {"㉖", "㉗", "㉘", "㉙", "㉚"},
      {"㉛", "㉜", "㉝", "㉞", "㉟"},
      {"㊱", "㊲", "㊳", "㊴", "㊵"},
      {"㊶", "㊷", "㊸", "㊹", "㊺"},
      {"㊻", "㊼", "㊽", "㊾", "㊿"}
    };

    if (progressStep < 1 || progressStep > 10 || frame < 1 || frame > 5) {
      return "";
    }

    return progressIcons[progressStep - 1][frame - 1];
  }

  /**
   * Generates a Unicode character representing the evolution progress for a pickaxe profile.
   *
   * @param profile the player's pickaxe profile
   * @param animationFrame the current animation frame (1–5)
   * @return a single Unicode character for the evolution progress icon
   */
  private String getEvolveProgress(Pickaxe profile, int animationFrame) {
    if (profile.atomVessel.crystals > 0) {
      return getAnimatedEvolveProgressIcon(10, animationFrame);
    }

    int percentage =
        (int) Math.round((double) profile.atomVessel.current / profile.atomVessel.target * 100);
    int progressStep = Math.min(percentage / 10, 10);

    if (progressStep == 0) {
      return "⁐";
    }

    return getAnimatedEvolveProgressIcon(progressStep, animationFrame);
  }

  /**
   * Maps a progress step and animation frame to an evolution-specific Unicode icon.
   *
   * @param progressStep the current progress level (1–10)
   * @param frame the current animation frame (1–5)
   * @return the corresponding Unicode character
   */
  private String getAnimatedEvolveProgressIcon(int progressStep, int frame) {
    String[][] evolveProgressIcons = {
      {"⑾", "⑿", "⒀", "⒁", "⒂"},
      {"⒃", "⒄", "⒅", "⒆", "⒇"},
      {"⒈", "⒉", "⒊", "⒋", "⒌"},
      {"⒍", "⒎", "⒏", "⒐", "⓵"},
      {"⓶", "⓷", "⓸", "⓹", "⓺"},
      {"⓻", "⓼", "⓽", "⓾", "⓿"},
      {"❶", "❷", "❸", "❹", "❺"},
      {"❻", "❼", "❽", "❾", "❿"},
      {"➀", "➁", "➂", "➃", "➄"},
      {"➅", "➆", "➇", "➈", "➉"}
    };

    if (progressStep < 1 || progressStep > 10 || frame < 1 || frame > 5) {
      return "\uD83D\uDFA8";
    }

    return evolveProgressIcons[progressStep - 1][frame - 1];
  }

  /**
   * Deserialises a legacy colour-code string into a non-italic Adventure component.
   *
   * @param message the legacy-formatted string
   * @return the Adventure component
   */
  private Component deserialize(String message) {
    return LEGACY.deserialize(message).decoration(TextDecoration.ITALIC, false);
  }
}
