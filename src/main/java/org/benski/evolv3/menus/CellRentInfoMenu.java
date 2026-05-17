package org.benski.evolv3.menus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.CellBlockBackupManager;
import org.benski.evolv3.core.managers.CellManager;
import org.benski.evolv3.core.managers.CellManager.CellDef;
import org.benski.evolv3.core.managers.CellRentManager;
import org.benski.evolv3.core.managers.CellRentManager.CellRent;
import org.benski.evolv3.core.managers.CellWorldGuardManager;
import org.benski.evolv3.core.managers.IslandManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Info/rent popup opened when a player shift-right-clicks inside a cell. If the cell is unrented:
 * shows price info with selectable rent-day options. If the cell is rented by the viewing player:
 * redirects to CellMenu. If the cell is rented by another player: shows read-only owner info.
 */
public class CellRentInfoMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("cell_rent_info");
  @Getter private static CellRentInfoMenu instance;

  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  /** Tracks which cell a player is currently viewing in this menu. */
  private final Map<UUID, String> viewingCell = new ConcurrentHashMap<>();

  public CellRentInfoMenu(JavaPlugin plugin) {
    super(plugin);
    instance = this;
  }

  /** Opens the menu for the given cell. */
  public void openFor(Player player, String cellName) {
    viewingCell.put(player.getUniqueId(), cellName.toLowerCase());
    openMenu(player);
  }

  @Override
  protected String getMenuTitle(Player player) {
    String name = viewingCell.getOrDefault(player.getUniqueId(), "Cell");
    return "&8" + name;
  }

  @Override
  protected int getMenuSize() {
    return 27;
  }

  @Override
  protected void onClose(Player player) {
    viewingCell.remove(player.getUniqueId());
  }

  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    String cellName = viewingCell.get(player.getUniqueId());
    if (cellName == null) {
      player.closeInventory();
      return;
    }

    CellDef def = CellManager.getInstance().get(cellName);
    if (def == null) {
      player.closeInventory();
      return;
    }

    CellRent rent = CellRentManager.getInstance().getRent(cellName);

    ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 0; i < 27; i++) inventory.setItem(i, border);

    String priceStr =
        def.currency().equals("essence")
            ? "&#B366D9" + (int) def.basePrice() + " essence / day"
            : "&#FFD700⛁ " + (int) def.basePrice() + " / day";

    if (rent == null) {
      boolean canRent =
          !CellRentManager.getInstance().hasCell(player.getUniqueId())
              && !IslandManager.getInstance().hasIsland(player.getUniqueId());

      inventory.setItem(
          4,
          new ItemBuilder(Material.IRON_BARS)
              .name("&#5555FF&l" + def.name())
              .lore(
                  "&#55FF55Available to rent",
                  "",
                  "&#888888Price: " + priceStr,
                  "&#888888Max Expansions: &#FFFFFF" + def.maxExpansions())
              .glow()
              .build());

      inventory.setItem(10, buildRentDayItem(def, 1, canRent));
      inventory.setItem(13, buildRentDayItem(def, 3, canRent));
      inventory.setItem(16, buildRentDayItem(def, 7, canRent));

      if (!canRent) {
        inventory.setItem(
            22,
            new ItemBuilder(Material.BARRIER)
                .name("&#FF5555Cannot Rent")
                .lore("&#888888You already own a cell or island.")
                .build());
      }
    } else if (rent.ownerUuid().equals(player.getUniqueId())) {
      player.closeInventory();
      Bukkit.getScheduler()
          .runTaskLater(
              org.benski.evolv3.Main.getInstance(),
              () -> CellMenu.getInstance().openMenu(player),
              1L);
    } else {
      inventory.setItem(
          4,
          new ItemBuilder(Material.IRON_BARS)
              .name("&#5555FF&l" + def.name())
              .lore(
                  "&#FF5555Currently rented.",
                  "",
                  "&#888888Price: " + priceStr,
                  "&#888888Max Expansions: &#FFFFFF" + def.maxExpansions())
              .glow()
              .build());

      inventory.setItem(
          13,
          new ItemBuilder(Material.PLAYER_HEAD)
              .name("&#FF5555Currently Rented")
              .lore(
                  "&#888888Owner: &#FFFFFF" + rent.ownerName(),
                  "&#888888Expires: " + formatTime(rent.rentExpiry()),
                  "&#888888Members: &#FFFFFF" + rent.members().size())
              .build());
    }
  }

  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    event.setCancelled(true);
    String cellName = viewingCell.get(player.getUniqueId());
    if (cellName == null) return true;

    CellDef def = CellManager.getInstance().get(cellName);
    if (def == null) return true;
    if (CellRentManager.getInstance().isRented(cellName)) return true;

    int days =
        switch (slot) {
          case 10 -> 1;
          case 13 -> 3;
          case 16 -> 7;
          default -> 0;
        };
    if (days > 0) handleRent(player, def, days);
    return true;
  }

  private static String formatTime(long rentExpiry) {
    if (rentExpiry <= 0) return "&#00FFFFPermanent";
    long ms = rentExpiry - System.currentTimeMillis();
    if (ms <= 0) return "&#FF5555Expired";
    long totalSec = ms / 1000;
    long days = totalSec / 86400;
    long hours = (totalSec % 86400) / 3600;
    long minutes = (totalSec % 3600) / 60;
    if (days > 0) return "&#FFFF00" + days + "d " + hours + "h " + minutes + "m";
    if (hours > 0) return "&#FFFF00" + hours + "h " + minutes + "m";
    return "&#FFFF00" + minutes + "m";
  }

  private ItemStack buildRentDayItem(CellDef def, int days, boolean canRent) {
    double total = def.basePrice() * days;
    String totalStr =
        def.currency().equals("essence")
            ? "&#B366D9" + (int) total + " essence"
            : "&#FFD700⛁ " + (int) total;
    String dayLabel = days == 1 ? "1 Day" : days + " Days";
    return new ItemBuilder(canRent ? Material.LIME_DYE : Material.GRAY_DYE)
        .name((canRent ? "&#55FF55" : "&#888888") + "Rent for " + dayLabel)
        .lore(
            "&#888888Cost: " + totalStr,
            "",
            canRent
                ? " &8| &aLeft-click &#AAAAAAto rent."
                : " &8| &#FF5555You cannot rent right now.")
        .build();
  }

  private void handleRent(Player player, CellDef def, int days) {
    if (CellRentManager.getInstance().hasCell(player.getUniqueId())) {
      Messageable.sendError(player, "You already have a cell.");
      return;
    }
    if (IslandManager.getInstance().hasIsland(player.getUniqueId())) {
      Messageable.sendError(player, "You already have an island.");
      return;
    }
    if (CellRentManager.getInstance().isRented(def.name())) {
      Messageable.sendError(player, "This cell was just rented by someone else.");
      player.closeInventory();
      return;
    }

    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return;
    PlayerProfile profile = dc.getLocal(player);
    if (profile == null) return;

    double cost = def.basePrice() * days;
    if (def.currency().equals("essence")) {
      if (!profile.hasEssence((int) cost)) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1f);
        Messageable.sendError(
            player, "Not enough essence. Need &#FFFFFF" + (int) cost + " essence.");
        return;
      }
      profile.removeEssence((int) cost);
    } else {
      if (profile.coins < cost) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1f);
        Messageable.sendError(player, "Not enough coins. Need &#FFD700⛁ " + (int) cost);
        return;
      }
      profile.removeCoins(cost);
    }
    profile.save(null);

    CellRentManager.getInstance()
        .createRent(def.name(), player.getUniqueId(), player.getName(), days);
    CellWorldGuardManager.getInstance()
        .createRegion(
            def.name(),
            Bukkit.getWorld(def.world()),
            def.x1(),
            def.y1(),
            def.z1(),
            def.x2(),
            def.y2(),
            def.z2(),
            player.getUniqueId());
    CellBlockBackupManager.getInstance().save(def.name());
    org.benski.evolv3.core.managers.CellHologramManager.getInstance().refresh(def.name());

    player.closeInventory();
    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    Messageable.sendSuccess(
        player,
        "You rented &#FFFFFF"
            + def.name()
            + "&#00FFFF for &#FFFFFF"
            + days
            + (days == 1 ? " day" : " days")
            + "&#00FFFF.");
    Messageable.sendInfo(player, "Use &#FFFFFF/cell &#CCCCCCto manage your cell.");
  }

  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
  }
}
