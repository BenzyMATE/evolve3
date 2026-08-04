package org.benski.evolv3.menus.cell;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.cell.CellRentManager;
import org.benski.evolv3.core.managers.cell.CellRentManager.CellRent;
import org.benski.evolv3.core.types.CellPermission;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Cell member management menu — shows the current member list and provides add/remove controls. */
public class CellMemberMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("cell_member_menu");
  private static final long PENDING_ADD_TIMEOUT_MILLIS = 60_000L;
  @Getter private static CellMemberMenu instance;
  private final Map<UUID, PendingAdd> pendingAdd = new ConcurrentHashMap<>();

  /**
   * A cell awaiting an ally name typed in chat, expiring after {@link #PENDING_ADD_TIMEOUT_MILLIS}.
   */
  private record PendingAdd(String cellName, long expiresAtMillis) {
    boolean isExpired() {
      return System.currentTimeMillis() > expiresAtMillis;
    }
  }

  /** Creates and registers the CellMemberMenu singleton. @param plugin the owning plugin */
  public CellMemberMenu(JavaPlugin plugin) {
    super(plugin);
    instance = this;
  }

  /** Builds inner slots. */
  private static int[] buildInnerSlots() {
    int[] slots = new int[28];
    int idx = 0;
    for (int row = 1; row <= 4; row++) {
      for (int col = 1; col <= 7; col++) {
        slots[idx++] = row * 9 + col;
      }
    }
    return slots;
  }

  /** Returns the menu type. */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  /** Returns the menu title. */
  @Override
  protected String getMenuTitle(Player player) {
    return "&8Members";
  }

  /** Returns the menu size. */
  @Override
  protected int getMenuSize() {
    return 54;
  }

  /** Populate menu. */
  @Override
  @SuppressWarnings("deprecation")
  protected void populateMenu(Player player, Inventory inventory) {
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      player.closeInventory();
      return;
    }

    ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    for (int i = 0; i < 9; i++) inventory.setItem(i, border);
    for (int i = 45; i < 54; i++) inventory.setItem(i, border);
    for (int row = 1; row <= 4; row++) {
      inventory.setItem(row * 9, border);
      inventory.setItem(row * 9 + 8, border);
    }

    List<UUID> members = rent.members();
    int[] innerSlots = buildInnerSlots();
    int max = Math.min(members.size(), innerSlots.length);

    for (int i = 0; i < max; i++) {
      UUID memberUuid = members.get(i);
      var offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
      String memberName =
          offlinePlayer.getName() != null ? offlinePlayer.getName() : memberUuid.toString();
      var perms = rent.getPermissions(memberUuid);

      ItemStack head =
          new ItemBuilder(Material.PLAYER_HEAD)
              .name("&#00FFFF&l" + memberName)
              .lore(
                  "",
                  " &f&lPERMISSIONS",
                  " &#888888Build: "
                      + (perms.contains(CellPermission.BUILD) ? "&#55FF55✔" : "&#FF5555✗"),
                  " &#888888Break: "
                      + (perms.contains(CellPermission.BREAK) ? "&#55FF55✔" : "&#FF5555✗"),
                  " &#888888Containers: "
                      + (perms.contains(CellPermission.CONTAINERS) ? "&#55FF55✔" : "&#FF5555✗"),
                  "",
                  " &aLeft-click &#AAAAAAto edit.",
                  " &#FFEE00Right-click &#AAAAAAto remove.",
                  "")
              .meta(
                  m -> {
                    if (m instanceof org.bukkit.inventory.meta.SkullMeta sm) {
                      org.bukkit.profile.PlayerProfile profile =
                          Bukkit.createProfile(memberUuid, memberName);
                      sm.setOwnerProfile(profile);
                      sm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    }
                  })
              .build();
      inventory.setItem(innerSlots[i], head);
    }

    if (members.isEmpty()) {
      inventory.setItem(
          22,
          new ItemBuilder(Material.GRAY_DYE)
              .name("&#888888&lNo Members")
              .lore("&#888888No members have been added.")
              .build());
    }

    inventory.setItem(
        49,
        new ItemBuilder(Material.LIME_DYE)
            .name("&#55FF55&l+ Add Ally")
            .lore(
                "",
                " &f&lINFO",
                " &#888888Invite a player to share",
                " &#888888your cell.",
                "",
                " &aLeft-click &#AAAAAAto add.")
            .build());

    inventory.setItem(47, new ItemBuilder(Material.ARROW).name("&#FF5555&l← Back").build());
  }

  /** Handles click. */
  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    event.setCancelled(true);
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      player.closeInventory();
      return true;
    }
    if (!rent.ownerUuid().equals(player.getUniqueId())) return true;

    if (slot == 49) {
      player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.2f);
      handleAddMember(player, rent);
      return true;
    }
    if (slot == 47) {
      player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 0.9f);
      player.closeInventory();
      Bukkit.getScheduler()
          .runTaskLater(Main.getInstance(), () -> CellMenu.getInstance().openMenu(player), 1L);
      return true;
    }

    UUID clickedMember = getMemberAtSlot(slot, rent);
    if (clickedMember == null) return true;

    if (event.isRightClick()) {
      handleRemoveMember(player, rent, clickedMember);
    } else {
      player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.1f);
      handleOpenPermMenu(player, rent, clickedMember);
    }
    return true;
  }

  /** Handles add member. */
  private void handleAddMember(Player player, CellRent rent) {
    player.closeInventory();
    pendingAdd.put(
        player.getUniqueId(),
        new PendingAdd(rent.cellName(), System.currentTimeMillis() + PENDING_ADD_TIMEOUT_MILLIS));
    Messageable.sendRaw(player, "");
    Messageable.sendRaw(
        player, "  &#AAAAAA" + "Type a player name in chat to add them as an ally.");
    Messageable.sendRaw(player, "  &#888888Type &#FFFFFFcancel &#888888to abort.");
    Messageable.sendRaw(player, "");
  }

  /** Handles remove member. */
  private void handleRemoveMember(Player player, CellRent rent, UUID memberUuid) {
    var offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
    String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : memberUuid.toString();
    CellRentManager.getInstance().removeMember(rent.cellName(), memberUuid);
    player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 0.8f);
    Messageable.sendSuccess(player, "Removed &#FFFFFF" + name + " &#00FFFFfrom your cell.");
    refresh(player);
  }

  /** Handles open perm menu. */
  private void handleOpenPermMenu(Player player, CellRent rent, UUID memberUuid) {
    player.closeInventory();
    Bukkit.getScheduler()
        .runTaskLater(
            Main.getInstance(),
            () -> {
              CellMemberPermMenu.getInstance().openFor(player, rent.cellName(), memberUuid);
            },
            1L);
  }

  /** Called when player chat occurs. */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerChat(AsyncChatEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    PendingAdd pending = pendingAdd.get(uuid);
    if (pending == null) return;
    if (pending.isExpired()) {
      pendingAdd.remove(uuid);
      return;
    }

    event.setCancelled(true);
    pendingAdd.remove(uuid);
    String cellName = pending.cellName();
    String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

    if (input.equalsIgnoreCase("cancel")) {
      Player cancelPlayer = event.getPlayer();
      Bukkit.getScheduler()
          .runTask(
              Main.getInstance(),
              () -> {
                Messageable.sendInfo(cancelPlayer, "Member add cancelled.");
                CellMenu.getInstance().openMenu(cancelPlayer);
              });
      return;
    }

    Player viewer = event.getPlayer();
    @SuppressWarnings("deprecation")
    var target = Bukkit.getOfflinePlayer(input);

    if (!target.hasPlayedBefore() && !target.isOnline()) {
      Bukkit.getScheduler()
          .runTask(
              Main.getInstance(),
              () -> {
                Messageable.sendError(viewer, "Player &#FFFFFF" + input + " &#FF5555not found.");
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1f);
                Bukkit.getScheduler()
                    .runTaskLater(
                        Main.getInstance(),
                        () -> CellMemberMenu.getInstance().openMenu(viewer),
                        3L);
              });
      return;
    }
    if (target.getUniqueId().equals(viewer.getUniqueId())) {
      Bukkit.getScheduler()
          .runTask(
              Main.getInstance(),
              () -> {
                Messageable.sendError(viewer, "You cannot add yourself.");
                Bukkit.getScheduler()
                    .runTaskLater(
                        Main.getInstance(),
                        () -> CellMemberMenu.getInstance().openMenu(viewer),
                        3L);
              });
      return;
    }

    boolean added = CellRentManager.getInstance().addMember(cellName, target.getUniqueId());
    String targetName = target.getName() != null ? target.getName() : input;
    Bukkit.getScheduler()
        .runTask(
            Main.getInstance(),
            () -> {
              if (added) {
                Messageable.sendSuccess(
                    viewer, "Added &#FFFFFF" + targetName + " &#00FFFFas a member.");
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
              } else {
                Messageable.sendError(
                    viewer, "&#FFFFFF" + targetName + " &#FF5555is already a member.");
              }
              Bukkit.getScheduler()
                  .runTaskLater(
                      Main.getInstance(), () -> CellMemberMenu.getInstance().openMenu(viewer), 3L);
            });
  }

  /** Returns the member at slot. */
  private UUID getMemberAtSlot(int slot, CellRent rent) {
    int[] innerSlots = buildInnerSlots();
    for (int i = 0; i < innerSlots.length; i++) {
      if (innerSlots[i] == slot && i < rent.members().size()) {
        return rent.members().get(i);
      }
    }
    return null;
  }

  /** Called when open occurs. */
  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
  }

  /**
   * Called when this menu closes. Abandoning the menu (e.g. pressing escape instead of typing a
   * name or "cancel") must not leave a pending ally-add armed against the player's next message.
   */
  @Override
  protected void onClose(Player player) {
    pendingAdd.remove(player.getUniqueId());
  }

  /** Clears any pending ally-add so a disconnected player's next login isn't affected. */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    pendingAdd.remove(event.getPlayer().getUniqueId());
  }
}
