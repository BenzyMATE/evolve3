package org.benski.evolv3.menus.player;

import static org.benski.evolv3.core.helpers.Utils.formatMaterialName;
import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.SmeltingHelper;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.player.HungerListener;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.backpack.BackpackItem;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Menu for displaying and managing the player's backpack. Supports pagination, item selection, and
 * selling. Non-food items fill rows 1-3 (slots 0-26); edible items get their own dedicated rows 4-5
 * (slots 27-44) so they're never mixed into the general sell list. Both sections page together
 * using the same prev/next buttons in row 6.
 */
public class BackpackMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("backpack");
  private static final int ITEMS_PER_PAGE = 27;
  private static final int FOOD_SLOT_START = 27;
  private static final int FOOD_ITEMS_PER_PAGE = 18;
  private static final int PREV_SLOT = 45;
  private static final int SELL_SLOT = 49;
  private static final int NEXT_SLOT = 53;

  private final PlayerDataCenter playerDataCenter;
  private final ArmorDataCenter armorDataCenter;
  private final Map<UUID, Integer> currentPage = new HashMap<>();
  private final Map<UUID, Set<Integer>> selectedItems = new HashMap<>();

  /**
   * Players whose next {@link #onClose} should be silent — set right before {@link
   * #sellSelectedItems} auto-closes the menu because selling emptied the backpack, so that close
   * doesn't play the "closed without selling" sound.
   */
  private final Set<UUID> skipCloseSound = new HashSet<>();

  /** Creates and registers the BackpackMenu. @param plugin the owning plugin */
  public BackpackMenu(JavaPlugin plugin) {
    super(plugin);
    this.playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    this.armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
  }

  /** {@inheritDoc} */
  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), "custom.backpackopen", 0.8f, 1.0f);
  }

  /** {@inheritDoc} */
  @Override
  protected void onClose(Player player) {
    if (skipCloseSound.remove(player.getUniqueId())) {
      return;
    }
    player.playSound(player.getLocation(), "custom.backpackclose", 0.8f, 1.0f);
  }

  /**
   * Opens the backpack menu at a specific page. If the menu is already open for this player, this
   * repopulates the existing inventory in place rather than opening a new one — re-opening a fresh
   * inventory from inside an {@link InventoryClickEvent} handler (e.g. clicking Next/Prev) causes a
   * client/server container-id desync that leaves the next click unhandled, letting items be
   * dragged out with no cancellation and no server-side error.
   *
   * @param player The player to open the menu for
   * @param page The page number (0-indexed)
   */
  public void openMenu(Player player, int page) {
    currentPage.put(player.getUniqueId(), page);
    selectedItems.putIfAbsent(player.getUniqueId(), new HashSet<>());
    if (isMenuOpen(player)) {
      refresh(player);
    } else {
      super.openMenu(player);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void openMenu(Player player) {
    openMenu(player, 0);
  }

  /** {@inheritDoc} */
  @Override
  protected String getMenuTitle(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) {
      return menus.getString(ConfigPaths.Menus.BackpackMenu.TITLE, "&8Backpack");
    }

    int page = currentPage.getOrDefault(player.getUniqueId(), 0);
    int totalPages = getTotalPages(profile.backpack);
    String pagedTitle =
        menus.getString(
            ConfigPaths.Menus.BackpackMenu.TITLE_PAGED, "&8Backpack &7(Page %page%/%total%)");
    return pagedTitle
        .replace("%page%", String.valueOf(page + 1))
        .replace("%total%", String.valueOf(Math.max(1, totalPages)));
  }

  /** {@inheritDoc} */
  @Override
  protected int getMenuSize() {
    return 54;
  }

  /** {@inheritDoc} */
  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) {
      return;
    }

    Backpack backpack = profile.backpack;
    int page = currentPage.getOrDefault(player.getUniqueId(), 0);
    Set<Integer> selected = selectedItems.getOrDefault(player.getUniqueId(), new HashSet<>());

    ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 45; i < 54; i++) {
      inventory.setItem(i, border);
    }

    List<BackpackItem> items = backpack.getItems();
    SlotLayout layout = buildSlotLayout(items);
    List<VisualSlot> combined = layout.combined();
    int nonFoodCount = layout.nonFoodCount();

    int nonFoodStart = page * ITEMS_PER_PAGE;
    int nonFoodEnd = Math.min(nonFoodStart + ITEMS_PER_PAGE, nonFoodCount);
    for (int i = nonFoodStart; i < nonFoodEnd; i++) {
      renderStack(player, inventory, items, combined, i, i - nonFoodStart, selected);
    }

    int foodStart = nonFoodCount + page * FOOD_ITEMS_PER_PAGE;
    int foodEnd = Math.min(foodStart + FOOD_ITEMS_PER_PAGE, combined.size());
    for (int i = foodStart; i < foodEnd; i++) {
      renderStack(
          player, inventory, items, combined, i, FOOD_SLOT_START + (i - foodStart), selected);
    }

    int totalPages = getTotalPages(backpack);

    if (page > 0) {
      List<String> prevLore =
          List.of("", fmt(player, " &#CCCCCCPage " + page + " of " + Math.max(1, totalPages)), "");
      ItemStack prevItem =
          new ItemBuilder(Material.ARROW)
              .name(fmt(player, "&#FFFFFF&l◄ Previous"))
              .lore(prevLore.toArray(new String[0]))
              .build();
      inventory.setItem(PREV_SLOT, prevItem);
    }

    double totalValue = calculateTotalSelectedValue(player, backpack);
    double multiplier = getHelmetMultiplier(player);
    double finalValue = totalValue * multiplier;
    int selectedCount =
        selected.stream()
            .filter(idx -> idx < combined.size())
            .mapToInt(idx -> combined.get(idx).stackAmount())
            .sum();
    boolean hasSelection = selectedCount > 0;

    List<String> sellLore = new ArrayList<>();
    sellLore.add("");
    sellLore.add(fmt(player, " &#FFFFFF&lSummary"));
    sellLore.add(
        fmt(
            player,
            " &#888888Selected: &#FFFFFF"
                + selectedCount
                + " item"
                + (selectedCount == 1 ? "" : "s")));
    sellLore.add(fmt(player, " &#888888Base Value: &#FFD700⛁ &#FFD700" + formatCoins(totalValue)));
    sellLore.add(fmt(player, " &#00FF88Total: &#FFD700⛁ &#FFD700" + formatCoins(finalValue)));
    sellLore.add("");
    sellLore.add(
        hasSelection
            ? fmt(player, " &aLeft-Click &#AAAAAAto sell")
            : fmt(player, " &#FF5555Select items first"));
    sellLore.add("");

    ItemStack sellItem =
        new ItemBuilder(hasSelection ? Material.EMERALD : Material.BARRIER)
            .name(
                hasSelection
                    ? fmt(player, "&#FFD700⛁ &#FFD700&lSell Selected")
                    : fmt(player, "&#888888⛁ &#888888&lSell Selected"))
            .lore(sellLore.toArray(new String[0]))
            .build();
    inventory.setItem(SELL_SLOT, sellItem);

    if (page < totalPages - 1) {
      List<String> nextLore =
          List.of(
              "",
              fmt(player, " &#CCCCCCPage " + (page + 2) + " of " + Math.max(1, totalPages)),
              "");
      ItemStack nextItem =
          new ItemBuilder(Material.ARROW)
              .name(fmt(player, "&#FFFFFF&lNext ►"))
              .lore(nextLore.toArray(new String[0]))
              .build();
      inventory.setItem(NEXT_SLOT, nextItem);
    }
  }

  /**
   * Renders one visual stack (a {@code combined} entry) into a GUI slot — shared by both the
   * non-food and food sections of {@link #populateMenu} so their lore/selection rendering can't
   * drift apart.
   */
  private void renderStack(
      Player player,
      Inventory inventory,
      List<BackpackItem> items,
      List<VisualSlot> combined,
      int flatIndex,
      int guiSlot,
      Set<Integer> selected) {
    VisualSlot visual = combined.get(flatIndex);
    BackpackItem item = items.get(visual.itemIndex());
    boolean isSelected = selected.contains(flatIndex);
    boolean allSelected = allChunksSelected(combined, visual.itemIndex(), selected);

    Material displayMaterial = isSelected ? Material.LIME_STAINED_GLASS_PANE : item.getMaterial();
    String itemName =
        isSelected
            ? fmt(player, "&#00FF88&l✓ &#FFFFFF" + formatMaterialName(item.getMaterial()))
            : fmt(player, "&#FFFFFF" + formatMaterialName(item.getMaterial()));

    List<String> lore = new ArrayList<>();
    if (SmeltingHelper.isSmeltable(item.getMaterial())) {
      lore.add(fmt(player, " &#FF8C00🔥 Smeltable"));
    }
    if (item.getMaterial().isEdible()) {
      lore.add(fmt(player, " &#FF8C00🍖 Edible"));
    }
    if (item.isEvolveItem()) {
      lore.add(fmt(player, " &#9932CC✦ Enchant Drop"));
    }
    lore.add("");
    lore.add(fmt(player, " &#FFFFFF&lItem"));
    lore.add(fmt(player, " &#00FFFFAmount: &#FFFFFF" + formatAmount(item.getAmount())));
    lore.add(fmt(player, " &#00FFFFValue: &#FFD700⛁ &#FFD700" + formatCoins(getItemValue(item))));
    if (item.getMaterial().isEdible()) {
      FileConfiguration defCfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
      int specific =
          defCfg.getInt(
              ConfigPaths.Default.Hunger.FOOD_RESTORE_PREFIX + "." + item.getMaterial().name(), -1);
      int fills =
          specific >= 0
              ? specific
              : defCfg.getInt(ConfigPaths.Default.Hunger.FOOD_RESTORE_PREFIX + ".DEFAULT", 2);
      lore.add(fmt(player, " &#FF8C00Fills: &#FFFFFF+" + fills));
    }
    lore.add("");
    lore.add(
        allSelected
            ? fmt(player, " &#FF5555Left-Click &#AAAAAAto deselect all")
            : fmt(player, " &aLeft-Click &#AAAAAAto select all"));
    if (item.getMaterial().isEdible()) {
      lore.add(fmt(player, " &aRight-Click &#AAAAAAto consume"));
    } else {
      lore.add(
          isSelected
              ? fmt(player, " &#FF5555Right-Click &#AAAAAAto deselect this stack")
              : fmt(player, " &aRight-Click &#AAAAAAto select this stack"));
    }
    lore.add("");

    ItemStack displayItem =
        new ItemBuilder(displayMaterial)
            .name(itemName)
            .lore(lore.toArray(new String[0]))
            .amount(visual.stackAmount())
            .build();

    inventory.setItem(guiSlot, displayItem);
  }

  /** {@inheritDoc} */
  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) {
      return true;
    }

    int page = currentPage.getOrDefault(player.getUniqueId(), 0);
    Set<Integer> selected = selectedItems.getOrDefault(player.getUniqueId(), new HashSet<>());

    if (slot == PREV_SLOT && page > 0) {
      openMenu(player, page - 1);
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
      return true;
    }

    if (slot == NEXT_SLOT && page < getTotalPages(profile.backpack) - 1) {
      openMenu(player, page + 1);
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
      return true;
    }

    if (slot == SELL_SLOT) {
      if (selected.isEmpty()) {
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        String nothingSelected =
            messages.getString(
                ConfigPaths.Messages.Backpack.NOTHING_SELECTED,
                "&c&lNothing Selected &7— Click items to select them first.");
        player.sendMessage(fmt(player, nothingSelected));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return true;
      }

      sellSelectedItems(player, profile);
      return true;
    }

    List<BackpackItem> items = profile.backpack.getItems();
    SlotLayout layout = buildSlotLayout(items);
    List<VisualSlot> combined = layout.combined();
    int nonFoodCount = layout.nonFoodCount();

    int flatIndex;
    if (slot >= 0 && slot < ITEMS_PER_PAGE) {
      flatIndex = page * ITEMS_PER_PAGE + slot;
      if (flatIndex >= nonFoodCount) return true;
    } else if (slot >= FOOD_SLOT_START && slot < FOOD_SLOT_START + FOOD_ITEMS_PER_PAGE) {
      flatIndex = nonFoodCount + page * FOOD_ITEMS_PER_PAGE + (slot - FOOD_SLOT_START);
      if (flatIndex >= combined.size()) return true;
    } else {
      return true;
    }

    int itemIndex = combined.get(flatIndex).itemIndex();
    BackpackItem item = items.get(itemIndex);

    if (event.getClick() == ClickType.RIGHT && item.getMaterial().isEdible()) {
      if (player.getFoodLevel() >= 20) {
        player.sendMessage(
            org.benski.evolv3.core.helpers.Utils.formatMessage(
                player, "%evolv3_prefix% &#FF5555You are not hungry!"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return true;
      }
      profile.backpack.consumeMaterial(item.getMaterial(), 1);
      profile.save(null);
      HungerListener.applyFoodRestore(player, item.getMaterial());
      removeItemChunksFromSelection(combined, itemIndex, selected);
      if (profile.backpack.getItems().isEmpty()) {
        closeMenu(player);
      } else {
        int maxPage = Math.max(0, getTotalPages(profile.backpack) - 1);
        if (page > maxPage) currentPage.put(player.getUniqueId(), maxPage);
        refresh(player);
      }
      return true;
    }

    if (event.getClick() == ClickType.RIGHT) {
      // Right-click toggles only this one 64-stack, letting a player sell part of a large
      // quantity without selecting every stack of that item.
      if (selected.contains(flatIndex)) {
        selected.remove(flatIndex);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
      } else {
        selected.add(flatIndex);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 2.0f);
      }
      refresh(player);
      return true;
    }

    // Left-click toggles every stack belonging to this item (select/deselect the full amount).
    if (allChunksSelected(combined, itemIndex, selected)) {
      removeItemChunksFromSelection(combined, itemIndex, selected);
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    } else {
      for (int i = 0; i < combined.size(); i++) {
        if (combined.get(i).itemIndex() == itemIndex) selected.add(i);
      }
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 2.0f);
    }

    refresh(player);
    return true;
  }

  /** Sell selected items. */
  private void sellSelectedItems(Player player, PlayerProfile profile) {
    Set<Integer> selected = selectedItems.get(player.getUniqueId());
    if (selected == null || selected.isEmpty()) {
      return;
    }

    Backpack backpack = profile.backpack;
    List<BackpackItem> items = backpack.getItems();
    List<VisualSlot> combined = buildSlotLayout(items).combined();
    Map<Integer, Integer> sellAmountsByItem = selectedAmountsByItem(combined, selected);

    double totalValue = 0;
    int itemCount = 0;

    for (Map.Entry<Integer, Integer> entry : sellAmountsByItem.entrySet()) {
      int itemIndex = entry.getKey();
      if (itemIndex >= items.size()) continue;

      BackpackItem item = items.get(itemIndex);
      int sellAmount = Math.min(entry.getValue(), item.getAmount());
      totalValue += getUnitValue(item.getMaterial()) * sellAmount;
      itemCount += sellAmount;

      if (sellAmount >= item.getAmount()) {
        item.markForDeletion();
      } else {
        item.removeAmount(sellAmount);
      }
    }

    double multiplier = getHelmetMultiplier(player);
    double finalValue = totalValue * multiplier;

    backpack.removeMarkedItems();

    profile.addCoins(finalValue);

    var messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    List<String> sellMessages =
        messagesConfig.getStringList(ConfigPaths.Messages.Backpack.SELL_ITEMS);
    for (String line : sellMessages) {
      line =
          line.replace("%total_items%", String.valueOf(itemCount))
              .replace("%total_amount%", String.format("%.2f", finalValue));
      player.sendMessage(formatMessage(player, line));
    }
    player.playSound(player.getLocation(), "custom.coins", 1.0f, 1.0f);

    selected.clear();
    profile.save(null);

    if (backpack.getItems().isEmpty()) {
      skipCloseSound.add(player.getUniqueId());
      closeMenu(player);
    } else {
      int maxPage = Math.max(0, getTotalPages(backpack) - 1);
      int currentPlayerPage = currentPage.getOrDefault(player.getUniqueId(), 0);
      if (currentPlayerPage > maxPage) {
        currentPage.put(player.getUniqueId(), maxPage);
      }
      refresh(player);
    }
  }

  /** Returns the per-unit sell value for a material. */
  private double getUnitValue(Material material) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    String path = ConfigPaths.Default.BLOCK_DEFAULTS + "." + material.name() + ".Sell";
    return config.getDouble(path, 1.0);
  }

  /** Returns the value of an item's entire amount (used for the per-slot "Value:" lore line). */
  private double getItemValue(BackpackItem item) {
    return getUnitValue(item.getMaterial()) * item.getAmount();
  }

  /**
   * Calculates the sell value of only the currently selected stacks (not necessarily whole items).
   */
  private double calculateTotalSelectedValue(Player player, Backpack backpack) {
    Set<Integer> selected = selectedItems.getOrDefault(player.getUniqueId(), new HashSet<>());
    List<BackpackItem> items = backpack.getItems();
    List<VisualSlot> combined = buildSlotLayout(items).combined();
    double total = 0;

    for (Map.Entry<Integer, Integer> entry : selectedAmountsByItem(combined, selected).entrySet()) {
      if (entry.getKey() < items.size()) {
        total += getUnitValue(items.get(entry.getKey()).getMaterial()) * entry.getValue();
      }
    }

    return total;
  }

  /**
   * Returns true if every visual stack belonging to the given item is currently selected — drives
   * the left-click "select all / deselect all" toggle and lore label.
   */
  private boolean allChunksSelected(
      List<VisualSlot> combined, int itemIndex, Set<Integer> selected) {
    for (int i = 0; i < combined.size(); i++) {
      if (combined.get(i).itemIndex() == itemIndex && !selected.contains(i)) return false;
    }
    return true;
  }

  /** Removes every selected flat slot index belonging to the given item. */
  private void removeItemChunksFromSelection(
      List<VisualSlot> combined, int itemIndex, Set<Integer> selected) {
    for (int i = 0; i < combined.size(); i++) {
      if (combined.get(i).itemIndex() == itemIndex) selected.remove(i);
    }
  }

  /** Sums the selected stack amounts per item index (a partial selection sells only that much). */
  private Map<Integer, Integer> selectedAmountsByItem(
      List<VisualSlot> combined, Set<Integer> selected) {
    Map<Integer, Integer> amounts = new LinkedHashMap<>();
    for (int flatIndex : selected) {
      if (flatIndex < 0 || flatIndex >= combined.size()) continue;
      VisualSlot visual = combined.get(flatIndex);
      amounts.merge(visual.itemIndex(), visual.stackAmount(), Integer::sum);
    }
    return amounts;
  }

  /** Returns the helmet multiplier. */
  private double getHelmetMultiplier(Player player) {
    ArmorProfile armorProfile = armorDataCenter.getLocal(player);
    if (armorProfile != null) {
      return armorProfile.getSellMultiplier();
    }
    return 1.0;
  }

  /**
   * Returns the total pages needed to show every stack, taking whichever section (non-food or food)
   * needs more pages at its own per-page slot count.
   */
  private int getTotalPages(Backpack backpack) {
    SlotLayout layout = buildSlotLayout(backpack.getItems());
    int foodCount = layout.combined().size() - layout.nonFoodCount();
    int nonFoodPages = (int) Math.ceil((double) layout.nonFoodCount() / ITEMS_PER_PAGE);
    int foodPages = (int) Math.ceil((double) foodCount / FOOD_ITEMS_PER_PAGE);
    return Math.max(nonFoodPages, foodPages);
  }

  /**
   * One 64-item (max) visual stack shown in a GUI slot, referencing back to the {@link
   * BackpackItem} it's part of by list index.
   */
  private record VisualSlot(int itemIndex, int stackAmount) {}

  /**
   * The non-food and food visual-stack lists concatenated into one combined list (non-food first),
   * plus the boundary between them. Flat indices into {@code combined} are globally unique — food
   * entries always sit at {@code >= nonFoodCount} — so a single {@code selected} index set works
   * across both GUI sections without collisions.
   */
  private record SlotLayout(List<VisualSlot> combined, int nonFoodCount) {}

  /** Builds the combined non-food + food visual slot layout for the given backpack contents. */
  private SlotLayout buildSlotLayout(List<BackpackItem> items) {
    List<VisualSlot> nonFood = buildVisualSlots(items, item -> !item.getMaterial().isEdible());
    List<VisualSlot> food = buildVisualSlots(items, item -> item.getMaterial().isEdible());
    List<VisualSlot> combined = new ArrayList<>(nonFood.size() + food.size());
    combined.addAll(nonFood);
    combined.addAll(food);
    return new SlotLayout(combined, nonFood.size());
  }

  /**
   * Splits every matching {@link BackpackItem}'s amount into consecutive 64-item (max) chunks,
   * mirroring how a real inventory spreads a large quantity across multiple stacks instead of
   * cramming it all into one slot with a misleading "Amount: N" label. Preserves item order; each
   * item's chunks stay consecutive.
   */
  private List<VisualSlot> buildVisualSlots(
      List<BackpackItem> items, Predicate<BackpackItem> filter) {
    List<VisualSlot> slots = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      BackpackItem item = items.get(i);
      if (!filter.test(item)) continue;
      int remaining = item.getAmount();
      while (remaining > 0) {
        int chunk = Math.min(remaining, 64);
        slots.add(new VisualSlot(i, chunk));
        remaining -= chunk;
      }
    }
    return slots;
  }

  /** Shorthand: run a string through formatMessage for hex colors + small-caps. */
  private static String fmt(Player player, String text) {
    return formatMessage(player, text);
  }

  /**
   * Formats a coin amount with comma separators. Shows no decimals for whole numbers, otherwise 2
   * decimal places.
   */
  private static String formatCoins(double amount) {
    if (amount == Math.floor(amount)) {
      return NumberFormat.getIntegerInstance(Locale.US).format((long) amount);
    }
    NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
    fmt.setMinimumFractionDigits(2);
    fmt.setMaximumFractionDigits(2);
    return fmt.format(amount);
  }

  /** Formats an item count with comma separators. */
  private static String formatAmount(int amount) {
    return NumberFormat.getIntegerInstance(Locale.US).format(amount);
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup(Player player) {
    super.cleanup(player);
    currentPage.remove(player.getUniqueId());
    selectedItems.remove(player.getUniqueId());
  }

  /** {@inheritDoc} */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }
}
