package org.benski.evolv3.menus;

import static org.benski.evolv3.core.helpers.Utils.formatMaterialName;
import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.text.NumberFormat;
import java.util.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.SmeltingHelper;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.backpack.BackpackItem;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.FurnaceView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Furnace smelting menu using a real {@link InventoryType#FURNACE} GUI.
 *
 * <h3>Item ownership model</h3>
 *
 * <ul>
 *   <li>When a player confirms an amount in the amount picker, that many items are <em>immediately
 *       deducted from their backpack</em> and stored inside {@link FurnaceData#inputStored} /
 *       {@link FurnaceData#fuelStored}.
 *   <li>The smelting tick consumes from those stored counts — the backpack is never touched during
 *       smelting.
 *   <li>Right-clicking a slot (or breaking the furnace) returns its stored items to <em>whoever is
 *       interacting with it</em> — intentional theft mechanic.
 *   <li>When a player quits or switches furnaces, their stored items <em>remain in the
 *       furnace</em>; another player may later open it and clear them.
 * </ul>
 */
public class FurnaceSmeltMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("furnace-smelt");

  private static final int SLOT_INPUT = 0;
  private static final int SLOT_FUEL = 1;
  private static final int SLOT_OUTPUT = 2;

  private static final int PICKER_BACK = 0;
  private static final int PICKER_PREV = 45;
  private static final int PICKER_INFO = 49;
  private static final int PICKER_NEXT = 53;
  private static final int[] PICKER_SLOTS = buildRange(9, 35);

  private static final int SLOT_AMT_MINUS_64 = 10;
  private static final int SLOT_AMT_MINUS_10 = 11;
  private static final int SLOT_AMT_MINUS_1 = 12;
  private static final int SLOT_AMT_DISPLAY = 13;
  private static final int SLOT_AMT_PLUS_1 = 14;
  private static final int SLOT_AMT_PLUS_10 = 15;
  private static final int SLOT_AMT_PLUS_64 = 16;
  private static final int SLOT_AMT_ALL = 17;
  private static final int SLOT_AMT_BACK = 21;
  private static final int SLOT_AMT_CONFIRM = 23;

  private static int[] buildRange(int from, int to) {
    int[] arr = new int[to - from + 1];
    for (int i = 0; i < arr.length; i++) arr[i] = from + i;
    return arr;
  }

  private static final class PickerHolder implements InventoryHolder {
    final UUID ownerUuid;
    final boolean pickingInput;
    private Inventory inventory;

    PickerHolder(UUID ownerUuid, boolean pickingInput) {
      this.ownerUuid = ownerUuid;
      this.pickingInput = pickingInput;
    }

    @Override
    public @NotNull Inventory getInventory() {
      return inventory;
    }
  }

  private static final class AmountPickerHolder implements InventoryHolder {
    final UUID ownerUuid;
    final boolean forInput;
    final Material material;
    final int maxAmount;
    int currentAmount;
    private Inventory inventory;

    AmountPickerHolder(
        UUID ownerUuid, boolean forInput, Material material, int maxAmount, int defaultAmount) {
      this.ownerUuid = ownerUuid;
      this.forInput = forInput;
      this.material = material;
      this.maxAmount = maxAmount;
      this.currentAmount = defaultAmount;
    }

    @Override
    public @NotNull Inventory getInventory() {
      return inventory;
    }
  }

  private static final class FurnaceData {
    final Location furnaceBlockLoc;
    final int cookTotal;

    @Nullable Material inputMat = null;
    int inputStored = 0;
    @Nullable Material fuelMat = null;
    int fuelStored = 0;

    int burnTimeLeft = 0;
    int maxBurnTime = 0;
    int cookTimeLeft = 0;

    @Nullable Material resultMat = null;
    int pendingOutput = 0;

    @Nullable UUID ownerUUID = null;
    @Nullable BukkitTask smeltTask = null;

    int pickerPage = 0;
    boolean reopenAfterPicker = false;

    @Nullable Material pendingAmountMat = null;
    boolean pendingAmountInput = false;

    FurnaceData(Location loc, int cookTotal) {
      this.furnaceBlockLoc = loc.clone();
      this.cookTotal = Math.max(1, cookTotal);
    }

    void cancelTask() {
      if (smeltTask != null && !smeltTask.isCancelled()) smeltTask.cancel();
      smeltTask = null;
    }

    boolean isTaskRunning() {
      return smeltTask != null && !smeltTask.isCancelled();
    }
  }

  private final PlayerDataCenter playerDataCenter;

  /** Per-furnace-block data, keyed by {@link #locationKey}. */
  private final Map<String, FurnaceData> furnaceDataMap = new HashMap<>();

  /** Maps each player UUID to the furnace key they're currently assigned to. */
  private final Map<UUID, String> playerFurnaceKey = new HashMap<>();

  public FurnaceSmeltMenu(JavaPlugin plugin) {
    super(plugin);
    this.playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
  }

  @Override
  protected String getMenuTitle(Player player) {
    return ConfigRegistry.getInstance()
        .get(ConfigNames.MENUS)
        .getString(ConfigPaths.Menus.FurnaceSmeltMenu.TITLE, "&8Furnace &7» &8Smelting");
  }

  @Override
  protected int getMenuSize() {
    return 9;
  }

  @Override
  protected void populateMenu(Player p, Inventory inv) {
    /* managed in openFurnaceUI */
  }

  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.7f, 1.0f);
  }

  @Override
  protected void onClose(Player player) {
    FurnaceData data = getPlayerFurnaceData(player);
    if (data == null || !data.reopenAfterPicker) {
      player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 0.9f);
    }
  }

  /** Called when reopening the furnace after a picker or amount-picker closes. */
  @Override
  public void openMenu(Player player) {
    String key = playerFurnaceKey.get(player.getUniqueId());
    if (key == null) return;
    FurnaceData data = furnaceDataMap.get(key);
    if (data == null) return;
    openFurnaceUI(player, data);
  }

  /**
   * Called from {@link org.benski.evolv3.listeners.FurnaceListener} when right-clicking a furnace
   * block.
   */
  public void openMenu(Player player, Block furnaceBlock) {
    int cookTotal =
        ConfigRegistry.getInstance()
            .get(ConfigNames.MENUS)
            .getInt(ConfigPaths.Menus.FurnaceSmeltMenu.SMELT_TICKS_PER_ITEM, 20);
    String newKey = locationKey(furnaceBlock.getLocation());

    FurnaceData existing = furnaceDataMap.get(newKey);
    if (existing != null
        && existing.ownerUUID != null
        && !existing.ownerUUID.equals(player.getUniqueId())
        && existing.isTaskRunning()) {
      Player owner = Bukkit.getPlayer(existing.ownerUUID);
      if (owner != null && owner.isOnline()) {
        player.sendMessage(
            formatMessage(
                player,
                "&#FF4444This furnace is in use by &#FFFFFF" + owner.getName() + "&#FF4444."));
        return;
      }
    }

    String oldKey = playerFurnaceKey.get(player.getUniqueId());
    if (oldKey != null && !oldKey.equals(newKey)) {
      FurnaceData oldData = furnaceDataMap.get(oldKey);
      if (oldData != null && player.getUniqueId().equals(oldData.ownerUUID)) {
        oldData.cancelTask();
        oldData.ownerUUID = null;
      }
    }

    FurnaceData data =
        furnaceDataMap.computeIfAbsent(
            newKey, k -> new FurnaceData(furnaceBlock.getLocation(), cookTotal));

    data.ownerUUID = player.getUniqueId();
    playerFurnaceKey.put(player.getUniqueId(), newKey);
    openFurnaceUI(player, data);
  }

  private void openFurnaceUI(Player player, FurnaceData data) {
    data.reopenAfterPicker = false;
    data.pendingAmountMat = null;

    String title = formatMessage(player, getMenuTitle(player));
    Inventory inv =
        Bukkit.createInventory(
            this,
            InventoryType.FURNACE,
            LegacyComponentSerializer.legacySection().deserialize(title));

    openInventories.put(player.getUniqueId(), inv);
    player.openInventory(inv);
    onOpen(player);

    updateFurnaceDisplay(player, inv, data);

    if (data.furnaceBlockLoc != null && data.burnTimeLeft > 0) {
      sendLitFurnace(player, data.furnaceBlockLoc, true);
    }

    if (!data.isTaskRunning()) {
      data.smeltTask =
          Bukkit.getScheduler()
              .runTaskTimer(
                  plugin,
                  () -> {
                    Player owner = data.ownerUUID != null ? Bukkit.getPlayer(data.ownerUUID) : null;
                    if (owner == null || !owner.isOnline()) {
                      data.cancelTask();
                      data.ownerUUID = null;
                      return;
                    }
                    tickFurnace(owner, data);
                  },
                  1L,
                  1L);
    }
  }

  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    FurnaceData data = getPlayerFurnaceData(player);
    if (data == null) return true;

    Inventory inv = openInventories.get(player.getUniqueId());
    boolean right = event.getClick() == ClickType.RIGHT;

    if (slot == SLOT_INPUT) {
      if (right) clearInput(player, data, inv);
      else openPicker(player, data, true);
    } else if (slot == SLOT_FUEL) {
      if (right) clearFuel(player, data, inv);
      else openPicker(player, data, false);
    } else if (slot == SLOT_OUTPUT) {
      collectOutput(player, inv, data);
    }
    return true;
  }

  /**
   * Returns all items from the furnace's input slot to the interacting player's backpack. Whoever
   * clicks can take them — intentional theft mechanic.
   */
  private void clearInput(Player player, FurnaceData data, @Nullable Inventory inv) {
    if (data.inputMat != null && data.inputStored > 0) {
      PlayerProfile profile = playerDataCenter.getLocal(player);
      if (profile != null) {
        profile.backpack.addItem(player, data.inputMat, data.inputStored, false);
        profile.save(null);
      }
    }
    data.inputMat = null;
    data.inputStored = 0;
    data.cookTimeLeft = 0;
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
    if (inv != null) updateFurnaceDisplay(player, inv, data);
  }

  /** Same as {@link #clearInput} but for the fuel slot. */
  private void clearFuel(Player player, FurnaceData data, @Nullable Inventory inv) {
    if (data.fuelMat != null && data.fuelStored > 0) {
      PlayerProfile profile = playerDataCenter.getLocal(player);
      if (profile != null) {
        profile.backpack.addItem(player, data.fuelMat, data.fuelStored, false);
        profile.save(null);
      }
    }
    data.fuelMat = null;
    data.fuelStored = 0;
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
    if (inv != null) updateFurnaceDisplay(player, inv, data);
  }

  private void openPicker(Player player, FurnaceData data, boolean pickingInput) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) return;

    data.reopenAfterPicker = true;
    data.pickerPage = 0;

    String title =
        formatMessage(
            player,
            pickingInput
                ? "&#FFFFFF&lSelect Input — Backpack"
                : "&#FF8C00&lSelect Fuel — Backpack");

    PickerHolder holder = new PickerHolder(player.getUniqueId(), pickingInput);
    Inventory pickerInv =
        Bukkit.createInventory(
            holder, 54, LegacyComponentSerializer.legacySection().deserialize(title));
    holder.inventory = pickerInv;

    populatePicker(player, pickerInv, profile.backpack, holder, data);
    player.openInventory(pickerInv);
  }

  private void populatePicker(
      Player player, Inventory inv, Backpack backpack, PickerHolder holder, FurnaceData data) {
    boolean pickingInput = holder.pickingInput;
    List<Material> choices = pickingInput ? collectSmeltables(backpack) : collectFuels(backpack);

    int perPage = PICKER_SLOTS.length;
    int totalPages = Math.max(1, (int) Math.ceil((double) choices.size() / perPage));
    int page = Math.min(data.pickerPage, totalPages - 1);

    ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 0; i < 54; i++) inv.setItem(i, glass);

    inv.setItem(
        PICKER_BACK,
        new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
            .name(fmt(player, "&#AAAAAA← Back"))
            .lore("", fmt(player, " &8| &aLeft-Click &#AAAAAAto go back"), "")
            .build());

    inv.setItem(
        4,
        new ItemBuilder(pickingInput ? Material.BLAST_FURNACE : Material.COAL)
            .name(
                fmt(player, pickingInput ? "&#FFFFFF&lSelect Input Item" : "&#FF8C00&lSelect Fuel"))
            .lore("", fmt(player, " &#CCCCCCClick an item to choose an amount."), "")
            .build());

    int start = page * perPage;
    int end = Math.min(start + perPage, choices.size());

    for (int i = start; i < end; i++) {
      Material mat = choices.get(i);
      int invSlot = PICKER_SLOTS[i - start];
      int amt = backpack.getMaterialAmount(mat);
      String color = pickingInput ? "&#FFFFFF" : "&#FF8C00";

      List<String> lore = new ArrayList<>();
      lore.add("");
      if (pickingInput) {
        FurnaceRecipe recipe = SmeltingHelper.getRecipe(mat);
        String result = recipe != null ? formatMaterialName(recipe.getResult().getType()) : "?";
        lore.add(fmt(player, " &#FFFFFF&lSmeltable"));
        lore.add(fmt(player, " &8| &#888888In backpack: &#FFFFFF" + fmt(amt)));
        lore.add(fmt(player, " &8| &#FF8C00Smelts into: &#FFFFFF" + result));
      } else {
        int ipf = SmeltingHelper.getItemsPerFuel(mat);
        lore.add(fmt(player, " &#FFFFFF&lFuel"));
        lore.add(fmt(player, " &8| &#888888In backpack: &#FFFFFF" + fmt(amt)));
        lore.add(
            fmt(
                player,
                " &8| &#FF8C00Smelts: &#FFFFFF" + ipf + " item" + (ipf == 1 ? "" : "s") + " each"));
      }
      lore.add("");
      lore.add(fmt(player, " &8| &aLeft-Click &#AAAAAAto load into furnace"));
      lore.add("");

      inv.setItem(
          invSlot,
          new ItemBuilder(mat)
              .name(fmt(player, color + formatMaterialName(mat)))
              .lore(lore.toArray(new String[0]))
              .amount(Math.min(amt, 64))
              .build());
    }

    if (page > 0) {
      inv.setItem(
          PICKER_PREV,
          new ItemBuilder(Material.ARROW)
              .name(fmt(player, "&#FFFFFF&l◄ Previous"))
              .lore("", fmt(player, " &#CCCCCCPage " + page + " of " + totalPages), "")
              .build());
    }
    inv.setItem(
        PICKER_INFO,
        new ItemBuilder(Material.PAPER)
            .name(fmt(player, "&#888888Page " + (page + 1) + " of " + totalPages))
            .build());
    if (page < totalPages - 1) {
      inv.setItem(
          PICKER_NEXT,
          new ItemBuilder(Material.ARROW)
              .name(fmt(player, "&#FFFFFF&lNext ►"))
              .lore("", fmt(player, " &#CCCCCCPage " + (page + 2) + " of " + totalPages), "")
              .build());
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onPickerClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    Inventory clicked = event.getClickedInventory();
    if (clicked == null) return;
    if (!(clicked.getHolder() instanceof PickerHolder holder)) return;
    if (!holder.ownerUuid.equals(player.getUniqueId())) return;

    event.setCancelled(true);

    FurnaceData data = getPlayerFurnaceData(player);
    if (data == null) return;

    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) return;

    Backpack backpack = profile.backpack;
    boolean pickingInput = holder.pickingInput;
    List<Material> choices = pickingInput ? collectSmeltables(backpack) : collectFuels(backpack);

    int slot = event.getSlot();
    int perPage = PICKER_SLOTS.length;
    int totalPages = Math.max(1, (int) Math.ceil((double) choices.size() / perPage));

    if (slot == PICKER_BACK) {
      data.reopenAfterPicker = false;
      player.closeInventory();
      return;
    }

    if (slot == PICKER_PREV && data.pickerPage > 0) {
      data.pickerPage--;
      populatePicker(player, clicked, backpack, holder, data);
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
      return;
    }

    if (slot == PICKER_NEXT && data.pickerPage < totalPages - 1) {
      data.pickerPage++;
      populatePicker(player, clicked, backpack, holder, data);
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
      return;
    }

    for (int i = 0; i < PICKER_SLOTS.length; i++) {
      if (slot == PICKER_SLOTS[i]) {
        int index = data.pickerPage * perPage + i;
        if (index < choices.size()) {
          data.pendingAmountMat = choices.get(index);
          data.pendingAmountInput = pickingInput;
        }
        player.closeInventory();
        return;
      }
    }
  }

  @EventHandler
  public void onPickerClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) return;
    if (!(event.getInventory().getHolder() instanceof PickerHolder holder)) return;
    if (!holder.ownerUuid.equals(player.getUniqueId())) return;

    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (!player.isOnline()) return;
              FurnaceData data = getPlayerFurnaceData(player);
              if (data != null && data.pendingAmountMat != null) {
                Material mat = data.pendingAmountMat;
                boolean forInput = data.pendingAmountInput;
                data.pendingAmountMat = null;
                openAmountPicker(player, data, mat, forInput);
              } else {
                openMenu(player);
              }
            },
            1L);
  }

  private void openAmountPicker(
      Player player, FurnaceData data, Material material, boolean forInput) {
    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) {
      openMenu(player);
      return;
    }

    int backpackAmt = profile.backpack.getMaterialAmount(material);
    if (backpackAmt <= 0) {
      openMenu(player);
      return;
    }

    int defaultAmt;
    if (forInput && material.equals(data.inputMat) && data.inputStored > 0) {
      defaultAmt = Math.min(data.inputStored, backpackAmt);
    } else if (!forInput && material.equals(data.fuelMat) && data.fuelStored > 0) {
      defaultAmt = Math.min(data.fuelStored, backpackAmt);
    } else {
      defaultAmt = backpackAmt;
    }

    String title =
        formatMessage(
            player,
            forInput
                ? "&#FFFFFF&lHow many to load into furnace?"
                : "&#FF8C00&lHow much fuel to load?");

    AmountPickerHolder holder =
        new AmountPickerHolder(player.getUniqueId(), forInput, material, backpackAmt, defaultAmt);
    Inventory inv =
        Bukkit.createInventory(
            holder, 27, LegacyComponentSerializer.legacySection().deserialize(title));
    holder.inventory = inv;

    populateAmountPicker(player, inv, holder);
    player.openInventory(inv);
  }

  private void populateAmountPicker(Player player, Inventory inv, AmountPickerHolder holder) {
    ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 0; i < 27; i++) inv.setItem(i, glass);

    String color = holder.forInput ? "&#FFFFFF" : "&#FF8C00";

    inv.setItem(
        SLOT_AMT_DISPLAY,
        new ItemBuilder(holder.material)
            .name(fmt(player, color + "&l" + formatMaterialName(holder.material)))
            .lore(
                "",
                fmt(
                    player,
                    " &#888888Amount: &#FFFFFF"
                        + fmt(holder.currentAmount)
                        + " &#888888/ &#FFFFFF"
                        + fmt(holder.maxAmount)),
                fmt(player, " &#888888(Use buttons to adjust)"),
                "")
            .amount(Math.min(holder.currentAmount, 64))
            .build());

    inv.setItem(
        SLOT_AMT_MINUS_64,
        new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
            .name(fmt(player, "&#FF4444&l-64"))
            .build());
    inv.setItem(
        SLOT_AMT_MINUS_10,
        new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
            .name(fmt(player, "&#FF4444&l-10"))
            .build());
    inv.setItem(
        SLOT_AMT_MINUS_1,
        new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(fmt(player, "&#FF4444&l-1")).build());

    inv.setItem(
        SLOT_AMT_PLUS_1,
        new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
            .name(fmt(player, "&#44FF44&l+1"))
            .build());
    inv.setItem(
        SLOT_AMT_PLUS_10,
        new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
            .name(fmt(player, "&#44FF44&l+10"))
            .build());
    inv.setItem(
        SLOT_AMT_PLUS_64,
        new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
            .name(fmt(player, "&#44FF44&l+64"))
            .build());

    inv.setItem(
        SLOT_AMT_ALL,
        new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
            .name(fmt(player, "&#FFFF44&lAll"))
            .lore("", fmt(player, " &#888888Set to: &#FFFFFF" + fmt(holder.maxAmount)), "")
            .build());

    inv.setItem(
        SLOT_AMT_BACK,
        new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
            .name(fmt(player, "&#AAAAAA← Back"))
            .lore("", fmt(player, " &8| &aLeft-Click &#AAAAAAto go back"), "")
            .build());

    inv.setItem(
        SLOT_AMT_CONFIRM,
        new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
            .name(fmt(player, "&#00FF00&l✓ Confirm — Load into Furnace"))
            .lore("", fmt(player, " &8| &aLeft-Click &#AAAAAAto confirm"), "")
            .build());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onAmountPickerClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    Inventory clicked = event.getClickedInventory();
    if (clicked == null) return;
    if (!(clicked.getHolder() instanceof AmountPickerHolder holder)) return;
    if (!holder.ownerUuid.equals(player.getUniqueId())) return;

    event.setCancelled(true);

    switch (event.getSlot()) {
      case SLOT_AMT_MINUS_64 -> adjustAmount(player, clicked, holder, -64);
      case SLOT_AMT_MINUS_10 -> adjustAmount(player, clicked, holder, -10);
      case SLOT_AMT_MINUS_1 -> adjustAmount(player, clicked, holder, -1);
      case SLOT_AMT_PLUS_1 -> adjustAmount(player, clicked, holder, +1);
      case SLOT_AMT_PLUS_10 -> adjustAmount(player, clicked, holder, +10);
      case SLOT_AMT_PLUS_64 -> adjustAmount(player, clicked, holder, +64);
      case SLOT_AMT_ALL -> {
        holder.currentAmount = holder.maxAmount;
        populateAmountPicker(player, clicked, holder);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
      }
      case SLOT_AMT_BACK -> player.closeInventory();
      case SLOT_AMT_CONFIRM -> confirmLoad(player, holder);
    }
  }

  private void adjustAmount(Player player, Inventory inv, AmountPickerHolder holder, int delta) {
    holder.currentAmount = Math.max(1, Math.min(holder.maxAmount, holder.currentAmount + delta));
    populateAmountPicker(player, inv, holder);
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
  }

  /**
   * Confirms loading items from the player's backpack into the furnace.
   *
   * <ul>
   *   <li>Returns any previously stored items in that slot to the player's backpack.
   *   <li>Deducts the chosen amount from the backpack and stores it in the furnace.
   * </ul>
   */
  private void confirmLoad(Player player, AmountPickerHolder holder) {
    FurnaceData data = getPlayerFurnaceData(player);
    if (data == null) {
      player.closeInventory();
      return;
    }

    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) {
      player.closeInventory();
      return;
    }

    Backpack backpack = profile.backpack;
    int available = backpack.getMaterialAmount(holder.material);
    int toPlace = Math.min(holder.currentAmount, available);

    if (toPlace <= 0) {
      player.closeInventory();
      return;
    }

    if (holder.forInput) {
      if (data.inputMat != null && data.inputStored > 0) {
        backpack.addItem(player, data.inputMat, data.inputStored, false);
      }
      if (!holder.material.equals(data.inputMat)) data.cookTimeLeft = 0;
      data.inputMat = holder.material;
      data.inputStored = toPlace;
    } else {
      if (data.fuelMat != null && data.fuelStored > 0) {
        backpack.addItem(player, data.fuelMat, data.fuelStored, false);
      }
      data.fuelMat = holder.material;
      data.fuelStored = toPlace;
    }

    backpack.consumeMaterial(holder.material, toPlace);
    profile.save(null);

    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 2.0f);
    player.closeInventory();
  }

  @EventHandler
  public void onAmountPickerClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) return;
    if (!(event.getInventory().getHolder() instanceof AmountPickerHolder holder)) return;
    if (!holder.ownerUuid.equals(player.getUniqueId())) return;

    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (player.isOnline()) openMenu(player);
            },
            1L);
  }

  /**
   * Runs every tick. Consumes from {@link FurnaceData#inputStored} and {@link
   * FurnaceData#fuelStored} — the player's backpack is never touched here.
   */
  private void tickFurnace(Player player, FurnaceData data) {
    Inventory inv = openInventories.get(player.getUniqueId());
    boolean menuOpen = inv != null;

    if (data.inputMat == null || data.fuelMat == null || data.inputStored <= 0) {
      if (menuOpen) {
        updateFurnaceDisplay(player, inv, data);
        updateAnimation(player, data);
      }
      return;
    }

    FurnaceRecipe recipe = SmeltingHelper.getRecipe(data.inputMat);
    if (recipe == null) {
      data.inputMat = null;
      data.inputStored = 0;
      return;
    }

    boolean wasBurning = data.burnTimeLeft > 0;

    if (data.burnTimeLeft > 0) {
      data.burnTimeLeft--;
    } else if (data.inputStored > 0 && data.fuelStored > 0) {
      int rawBurn = SmeltingHelper.getFuelBurnTime(data.fuelMat);
      int scaledBurn = Math.max(1, rawBurn * data.cookTotal / SmeltingHelper.COOK_TIME_TICKS);
      data.maxBurnTime = scaledBurn;
      data.burnTimeLeft = scaledBurn;
      data.fuelStored--;
      if (data.fuelStored <= 0) {
        data.fuelStored = 0;
        data.fuelMat = null;
      }
    } else if (data.inputStored == 0 && data.cookTimeLeft > 0) {
      data.cookTimeLeft = Math.max(0, data.cookTimeLeft - 2);
    }

    if (data.burnTimeLeft > 0 && data.inputStored > 0) {
      data.cookTimeLeft++;
      if (data.cookTimeLeft >= data.cookTotal) {
        data.cookTimeLeft = 0;
        data.resultMat = recipe.getResult().getType();
        data.pendingOutput += recipe.getResult().getAmount();
        data.inputStored--;
        if (data.inputStored <= 0) {
          data.inputStored = 0;
          data.inputMat = null;
        }

        if (recipe.getExperience() > 0) {
          player.giveExp(Math.round(recipe.getExperience()));
        }
      }
    }

    boolean isBurning = data.burnTimeLeft > 0;
    if (data.furnaceBlockLoc != null && isBurning != wasBurning) {
      sendLitFurnace(player, data.furnaceBlockLoc, isBurning);
    }

    if (menuOpen) {
      updateFurnaceDisplay(player, inv, data);
      updateAnimation(player, data);
    }
  }

  private void collectOutput(Player player, @Nullable Inventory inv, FurnaceData data) {
    if (data.resultMat == null || data.pendingOutput <= 0) return;

    PlayerProfile profile = playerDataCenter.getLocal(player);
    if (profile == null) return;

    profile.backpack.addItem(player, data.resultMat, data.pendingOutput, false);
    profile.save(null);

    data.pendingOutput = 0;
    data.resultMat = null;

    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
    if (inv != null) updateFurnaceDisplay(player, inv, data);
  }

  private void updateFurnaceDisplay(Player player, Inventory inv, FurnaceData data) {

    if (data.inputMat != null) {
      FurnaceRecipe recipe = SmeltingHelper.getRecipe(data.inputMat);
      String result = recipe != null ? formatMaterialName(recipe.getResult().getType()) : "?";

      inv.setItem(
          SLOT_INPUT,
          new ItemBuilder(data.inputMat)
              .name(fmt(player, "&#FFFFFF&l" + formatMaterialName(data.inputMat)))
              .lore(
                  "",
                  fmt(player, " &#FFFFFF&lInput"),
                  fmt(player, " &8| &#888888In furnace: &#FFFFFF" + fmt(data.inputStored)),
                  fmt(player, " &8| &#FF8C00Smelts into: &#FFFFFF" + result),
                  "",
                  fmt(player, " &8| &aLeft-Click &#AAAAAAto change"),
                  fmt(player, " &8| &cRight-Click &#AAAAAAto clear & return"),
                  "")
              .amount(Math.min(Math.max(1, data.inputStored), 64))
              .build());
    } else {
      inv.setItem(
          SLOT_INPUT,
          new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE)
              .name(fmt(player, "&#FFFFFF&lLoad Input"))
              .lore(
                  "",
                  fmt(player, " &#CCCCCCClick to load a smeltable item"),
                  fmt(player, " &#CCCCCCfrom your backpack."),
                  "")
              .build());
    }

    if (data.fuelMat != null) {
      int ipf = SmeltingHelper.getItemsPerFuel(data.fuelMat);

      inv.setItem(
          SLOT_FUEL,
          new ItemBuilder(data.fuelMat)
              .name(fmt(player, "&#FF8C00&l" + formatMaterialName(data.fuelMat)))
              .lore(
                  "",
                  fmt(player, " &#FFFFFF&lFuel"),
                  fmt(player, " &8| &#888888In furnace: &#FFFFFF" + fmt(data.fuelStored)),
                  fmt(
                      player,
                      " &8| &#FF8C00Smelts: &#FFFFFF"
                          + ipf
                          + " item"
                          + (ipf == 1 ? "" : "s")
                          + " each"),
                  "",
                  fmt(player, " &8| &aLeft-Click &#AAAAAAto change"),
                  fmt(player, " &8| &cRight-Click &#AAAAAAto clear & return"),
                  "")
              .amount(Math.min(Math.max(1, data.fuelStored), 64))
              .build());
    } else {
      inv.setItem(
          SLOT_FUEL,
          new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
              .name(fmt(player, "&#FF8C00&lLoad Fuel"))
              .lore(
                  "",
                  fmt(player, " &#CCCCCCClick to load fuel"),
                  fmt(player, " &#CCCCCCfrom your backpack."),
                  "")
              .build());
    }

    if (data.resultMat != null && data.pendingOutput > 0) {
      inv.setItem(
          SLOT_OUTPUT,
          new ItemBuilder(data.resultMat)
              .name(fmt(player, "&#00FF00&l" + formatMaterialName(data.resultMat)))
              .lore(
                  "",
                  fmt(player, " &#00FF00&lOutput"),
                  fmt(player, " &8| &#888888Total ready: &#FFFFFF" + fmt(data.pendingOutput)),
                  "",
                  fmt(player, " &8| &aLeft-Click &#AAAAAAto collect"),
                  "")
              .amount(Math.min(data.pendingOutput, 64))
              .build());
    } else {
      inv.setItem(
          SLOT_OUTPUT,
          new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
              .name(fmt(player, "&#888888Output"))
              .lore("", fmt(player, " &#CCCCCCSmelted items appear here."), "")
              .build());
    }
  }

  @SuppressWarnings({"deprecation", "removal"})
  private static void updateAnimation(Player player, FurnaceData data) {
    InventoryView view = player.getOpenInventory();
    if (view == null) return;

    if (view instanceof FurnaceView furnaceView) {
      furnaceView.setCookTime(data.cookTimeLeft, data.cookTotal);
    }

    view.setProperty(InventoryView.Property.BURN_TIME, data.burnTimeLeft);
    view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_FUEL, data.maxBurnTime);
    view.setProperty(InventoryView.Property.COOK_TIME, data.cookTimeLeft);
    view.setProperty(InventoryView.Property.TICKS_FOR_CURRENT_SMELTING, data.cookTotal);
  }

  private static void sendLitFurnace(Player player, Location loc, boolean lit) {
    if (loc == null || loc.getWorld() == null) return;
    Block block = loc.getBlock();
    if (block.getType() != Material.FURNACE) return;
    org.bukkit.block.data.BlockData bd = block.getBlockData().clone();
    if (bd instanceof Lightable l) l.setLit(lit);
    player.sendBlockChange(loc, bd);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    String key = playerFurnaceKey.remove(player.getUniqueId());
    if (key == null) return;

    FurnaceData data = furnaceDataMap.get(key);
    if (data != null && player.getUniqueId().equals(data.ownerUUID)) {
      data.cancelTask();
      data.ownerUUID = null;
    }
  }

  /** Drops all items stored in a furnace when its block is broken. */
  @EventHandler
  public void onFurnaceBreak(BlockBreakEvent event) {
    if (event.getBlock().getType() != Material.FURNACE) return;
    String key = locationKey(event.getBlock().getLocation());

    FurnaceData data = furnaceDataMap.remove(key);
    if (data == null) return;

    data.cancelTask();
    if (data.ownerUUID != null) {
      playerFurnaceKey.remove(data.ownerUUID);
      data.ownerUUID = null;
    }

    Location dropLoc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
    dropStacks(dropLoc, data.inputMat, data.inputStored);
    dropStacks(dropLoc, data.fuelMat, data.fuelStored);
    dropStacks(dropLoc, data.resultMat, data.pendingOutput);
  }

  /** Drops an arbitrary quantity as naturally-stacked item entities. */
  private static void dropStacks(@NotNull Location loc, @Nullable Material mat, int amount) {
    if (mat == null || amount <= 0 || loc.getWorld() == null) return;
    int remaining = amount;
    while (remaining > 0) {
      int stackAmt = Math.min(remaining, mat.getMaxStackSize());
      loc.getWorld().dropItemNaturally(loc, new ItemStack(mat, stackAmt));
      remaining -= stackAmt;
    }
  }

  @Override
  public void cleanup(Player player) {
    super.cleanup(player);
  }

  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  private @Nullable FurnaceData getPlayerFurnaceData(Player player) {
    String key = playerFurnaceKey.get(player.getUniqueId());
    return key != null ? furnaceDataMap.get(key) : null;
  }

  private static String locationKey(Location loc) {
    return loc.getWorld().getUID()
        + ":"
        + loc.getBlockX()
        + ":"
        + loc.getBlockY()
        + ":"
        + loc.getBlockZ();
  }

  private List<Material> collectSmeltables(Backpack backpack) {
    List<Material> result = new ArrayList<>();
    Set<Material> seen = new HashSet<>();
    for (BackpackItem item : backpack.getItems()) {
      Material mat = item.getMaterial();
      if (seen.add(mat) && SmeltingHelper.isSmeltable(mat)) result.add(mat);
    }
    return result;
  }

  private List<Material> collectFuels(Backpack backpack) {
    List<Material> result = new ArrayList<>();
    Set<Material> seen = new HashSet<>();
    for (BackpackItem item : backpack.getItems()) {
      Material mat = item.getMaterial();
      if (seen.add(mat) && SmeltingHelper.isFuel(mat)) result.add(mat);
    }
    return result;
  }

  private static String fmt(Player player, String text) {
    return formatMessage(player, text);
  }

  private static String fmt(int amount) {
    return NumberFormat.getIntegerInstance(Locale.US).format(amount);
  }
}
