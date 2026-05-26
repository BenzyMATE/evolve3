package org.benski.evolv3.menus.island;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.cell.IslandManager;
import org.benski.evolv3.core.managers.cell.IslandManager.IslandData;
import org.benski.evolv3.core.managers.cell.IslandWorldManager;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Browse open islands and teleport to them. Players can only visit if the island owner has
 * openForVisits = true.
 */
public class IslandsMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("islands_menu");
  @Getter private static IslandsMenu instance;

  /** Returns the menu type. */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  private static final int PAGE_SIZE = 28;

  /** Creates and registers the IslandsMenu singleton. @param plugin the owning plugin */
  public IslandsMenu(JavaPlugin plugin) {
    super(plugin);
    instance = this;
  }

  /** Returns the menu title. */
  @Override
  protected String getMenuTitle(Player player) {
    return "&#00FFFF&l🏝 Open Islands";
  }

  /** Returns the menu size. */
  @Override
  protected int getMenuSize() {
    return 54;
  }

  /** Populate menu. */
  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    List<IslandData> openIslands = IslandManager.getInstance().getOpenIslands();

    ItemStack border = new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 0; i < 9; i++) inventory.setItem(i, border);
    for (int i = 45; i < 54; i++) inventory.setItem(i, border);
    for (int row = 1; row <= 4; row++) {
      inventory.setItem(row * 9, border);
      inventory.setItem(row * 9 + 8, border);
    }

    if (openIslands.isEmpty()) {
      inventory.setItem(
          22,
          new ItemBuilder(Material.BARRIER)
              .name("&#888888No open islands found.")
              .lore(
                  "&#CCCCCCIsland owners can open their island",
                  "&#CCCCCCfor visits in the &#FFFFFF/island &#CCCCCCmenu.")
              .build());
      return;
    }

    int[] innerSlots = buildInnerSlots();
    int max = Math.min(openIslands.size(), innerSlots.length);

    for (int i = 0; i < max; i++) {
      IslandData island = openIslands.get(i);
      var owner = Bukkit.getOfflinePlayer(island.ownerUuid());
      String ownerName = island.ownerName().isEmpty() ? owner.getName() : island.ownerName();

      ItemStack head =
          new ItemBuilder(Material.PLAYER_HEAD)
              .name("&#00FFFF&l" + ownerName + "'s Island")
              .lore(
                  "&#888888Members: &#FFFFFF" + island.members().size(),
                  "&#888888Status: &#55FF55Open for visits",
                  "",
                  "&#CCCCCCClick to visit!")
              .build();

      if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
        skullMeta.setOwningPlayer(owner);
        head.setItemMeta(skullMeta);
      }

      inventory.setItem(innerSlots[i], head);
    }
  }

  /** Handles click. */
  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    event.setCancelled(true);
    List<IslandData> openIslands = IslandManager.getInstance().getOpenIslands();
    int[] innerSlots = buildInnerSlots();

    for (int i = 0; i < innerSlots.length; i++) {
      if (innerSlots[i] == slot && i < openIslands.size()) {
        visitIsland(player, openIslands.get(i));
        return true;
      }
    }
    return true;
  }

  /** Visit island. */
  private void visitIsland(Player player, IslandData island) {
    if (island.locked()
        && !island.members().contains(player.getUniqueId())
        && !island.ownerUuid().equals(player.getUniqueId())) {
      Messageable.sendError(player, "This island is locked.");
      return;
    }
    Location spawnLoc = IslandWorldManager.getInstance().getSpawnLocation(island);
    if (spawnLoc == null) {
      Messageable.sendError(player, "Island world is not loaded.");
      return;
    }
    player.closeInventory();
    player.teleport(spawnLoc);
    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    Messageable.sendSuccess(
        player, "Teleporting to &#FFFFFF" + island.ownerName() + "&#00FFFF's island.");
  }

  /** Builds inner slots. */
  private static int[] buildInnerSlots() {
    int[] slots = new int[PAGE_SIZE];
    int idx = 0;
    for (int row = 1; row <= 4; row++) {
      for (int col = 1; col <= 7; col++) {
        slots[idx++] = row * 9 + col;
      }
    }
    return slots;
  }

  /** Called when open occurs. */
  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.8f, 1f);
  }
}
