package org.benski.evolv3.menus.cell;

import lombok.Getter;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.cell.CellBlockBackupManager;
import org.benski.evolv3.core.managers.cell.CellManager;
import org.benski.evolv3.core.managers.cell.CellManager.CellDef;
import org.benski.evolv3.core.managers.cell.CellRentManager;
import org.benski.evolv3.core.managers.cell.CellRentManager.CellRent;
import org.benski.evolv3.core.managers.cell.CellWorldGuardManager;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** 27-slot confirmation menu for cell abandonment. */
public class CellAbandonConfirmMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("cell_abandon_confirm");
  @Getter private static CellAbandonConfirmMenu instance;

  /** Returns the menu type. */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  /** Creates and registers the CellAbandonConfirmMenu singleton. @param plugin the owning plugin */
  public CellAbandonConfirmMenu(JavaPlugin plugin) {
    super(plugin);
    instance = this;
  }

  /** Returns the menu title. */
  @Override
  protected String getMenuTitle(Player player) {
    return "&#FF5555Abandon your cell?";
  }

  /** Returns the menu size. */
  @Override
  protected int getMenuSize() {
    return 27;
  }

  /** Populate menu. */
  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    ItemStack border = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 0; i < 27; i++) inventory.setItem(i, border);

    inventory.setItem(
        11,
        new ItemBuilder(Material.LIME_DYE)
            .name("&#55FF55&l✔ Confirm Abandonment")
            .lore(
                "",
                " &f&lINFO",
                " &#888888Your cell will be reset.",
                " &#888888All expansions will be lost.",
                "",
                " &#FF5555This cannot be undone.",
                "",
                " &aLeft-click &#AAAAAAto confirm.",
                "")
            .build());

    inventory.setItem(15, new ItemBuilder(Material.RED_DYE).name("&#FF4444&l✖ Cancel").build());
  }

  /** Handles click. */
  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    event.setCancelled(true);
    if (slot == 11) {
      performAbandonment(player);
    } else if (slot == 15) {
      player.closeInventory();
      CellMenu.getInstance().openMenu(player);
    }
    return true;
  }

  /** Perform abandonment. */
  private void performAbandonment(Player player) {
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      player.closeInventory();
      return;
    }
    player.closeInventory();
    String cellName = rent.cellName();
    CellBlockBackupManager.getInstance().restore(cellName);
    CellDef restored = CellManager.getInstance().get(cellName);
    if (restored != null) placeExpansionWall(restored);
    CellWorldGuardManager.getInstance().deleteRegion(cellName);
    CellRentManager.getInstance().removeRent(cellName);
    org.benski.evolv3.core.managers.cell.CellHologramManager.getInstance().refresh(cellName);
    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1f, 1f);
    Messageable.sendSuccess(
        player, "You have abandoned your cell &#FFFFFF" + cellName + "&#00FFFF.");
  }

  /** Places expansion wall. */
  private void placeExpansionWall(CellDef def) {
    org.bukkit.World world = org.bukkit.Bukkit.getWorld(def.world());
    if (world == null) return;
    org.bukkit.Material wallMat = org.bukkit.Material.matchMaterial(def.wallMaterial());
    if (wallMat == null) wallMat = org.bukkit.Material.PURPLE_WOOL;
    int x1 = Math.min(def.x1(), def.x2()), x2 = Math.max(def.x1(), def.x2());
    int y1 = Math.min(def.y1(), def.y2()), y2 = Math.max(def.y1(), def.y2());
    int z1 = Math.min(def.z1(), def.z2()), z2 = Math.max(def.z1(), def.z2());
    switch (def.upgradeDirection()) {
      case "N" -> {
        for (int x = x1; x <= x2; x++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x, y, z1 - 1).setType(wallMat, false);
      }
      case "S" -> {
        for (int x = x1; x <= x2; x++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x, y, z2 + 1).setType(wallMat, false);
      }
      case "E" -> {
        for (int z = z1; z <= z2; z++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x2 + 1, y, z).setType(wallMat, false);
      }
      case "W" -> {
        for (int z = z1; z <= z2; z++)
          for (int y = y1; y <= y2; y++) world.getBlockAt(x1 - 1, y, z).setType(wallMat, false);
      }
    }
  }
}
