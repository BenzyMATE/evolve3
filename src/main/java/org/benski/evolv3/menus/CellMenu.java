package org.benski.evolv3.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.CellBlockBackupManager;
import org.benski.evolv3.core.managers.CellManager;
import org.benski.evolv3.core.managers.CellManager.CellDef;
import org.benski.evolv3.core.managers.CellRentManager;
import org.benski.evolv3.core.managers.CellRentManager.CellRent;
import org.benski.evolv3.core.managers.CellWorldGuardManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Main cell management GUI — 3 rows (27 slots).
 *
 * <p>Layout: Row 0: border [4=info overrides] Row 1: B [11=members] [13=lock] [15=expand/rift] B
 * Row 2: B B [20=extend rent] B [22=abandon] B B B B
 */
public class CellMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("cell_menu");
  @Getter private static CellMenu instance;

  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  public record RiftData(String cellName, Location wallCenter, long expiry) {}

  private static final ConcurrentHashMap<UUID, RiftData> activeRifts = new ConcurrentHashMap<>();

  private static final ConcurrentHashMap<UUID, Long> riftTriggerCooldowns =
      new ConcurrentHashMap<>();

  public static RiftData getRift(UUID uuid) {
    return activeRifts.get(uuid);
  }

  public static void clearRift(UUID uuid) {
    activeRifts.remove(uuid);
    riftTriggerCooldowns.remove(uuid);
  }

  public static boolean tryTriggerRift(UUID uuid) {
    long now = System.currentTimeMillis();
    Long last = riftTriggerCooldowns.get(uuid);
    if (last != null && now - last < 2000L) return false;
    riftTriggerCooldowns.put(uuid, now);
    return true;
  }

  private static final long MAX_RENT_MS = 7L * 86_400_000L;

  public CellMenu(JavaPlugin plugin) {
    super(plugin);
    instance = this;
  }

  @Override
  protected String getMenuTitle(Player player) {
    return "&8" + player.getName() + "'s Cell";
  }

  @Override
  protected int getMenuSize() {
    return 27;
  }

  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      player.closeInventory();
      return;
    }
    CellDef def = CellManager.getInstance().get(rent.cellName());
    boolean isOwner = rent.ownerUuid().equals(player.getUniqueId());

    Material borderMat = mat(cfg("Border.Material"), Material.GRAY_STAINED_GLASS_PANE);
    ItemStack border = new ItemBuilder(borderMat).name(" ").build();
    for (int i = 0; i < 27; i++) inventory.setItem(i, border);

    String expiresStr = formatTime(rent.rentExpiry());
    String expandStr = rent.expandCount() + "/10";
    String statusStr = rent.locked() ? "&#FF5555Closed" : "&#55FF55Open";
    Material infoMat = Material.IRON_BARS;
    String infoName = "&#5555FF&l" + rent.cellName();
    List<String> infoLore =
        tok(
            List.of(
                "",
                " &f&lINFO",
                " &8| &#888888Owner: &#FFFFFF{owner}",
                " &8| &#888888Allies: &#FFFFFF{allies}",
                "",
                " &f&lSTATISTICS",
                " &8| &#888888Expires: {expires}",
                " &8| &#888888Expansions: &#FFFFFF{expansions}",
                "",
                " &f&lSTATUS",
                " &8| {status}",
                ""),
            "owner",
            rent.ownerName(),
            "allies",
            rent.members().size(),
            "expires",
            expiresStr,
            "expansions",
            expandStr,
            "status",
            statusStr);
    inventory.setItem(4, new ItemBuilder(infoMat).name(infoName).lore(infoLore).glow().build());

    if (isOwner) {
      boolean locked = rent.locked();
      Material lockMat =
          locked
              ? mat(cfg("Lock.SealedMaterial"), Material.IRON_DOOR)
              : mat(cfg("Lock.OpenMaterial"), Material.OAK_DOOR);
      String lockName = locked ? "&#FF5555&lCell Closed" : "&#55FF55&lCell Open";
      List<String> lockLore =
          locked
              ? cfgList(
                  "Lock.SealedLore",
                  List.of(
                      "",
                      " &f&lINFO",
                      " &8| &#888888Decide whether to allow non-members",
                      " &8| &#888888to enter your cell. ",
                      "",
                      " &f&lSTATUS",
                      " &8| &#888888Only members can access this cell.",
                      "",
                      " &8| &aLeft-click &#AAAAAAto open.",
                      ""))
              : cfgList(
                  "Lock.OpenLore",
                  List.of(
                      "",
                      " &f&lINFO",
                      " &8| &#888888Decide whether to allow non-members",
                      " &8| &#888888to enter your cell. ",
                      "",
                      " &f&lSTATUS",
                      " &8| &#888888All prisoners can access this cell.",
                      "",
                      " &8| &aLeft-click &#AAAAAAto close.",
                      ""));
      inventory.setItem(12, new ItemBuilder(lockMat).name(lockName).lore(lockLore).build());
    }

    if (isOwner && def != null) {
      boolean canExpand = rent.expandCount() < def.maxExpansions();
      if (canExpand) {
        int remaining = def.maxExpansions() - rent.expandCount();
        String costStr =
            def.currency().equals("essence")
                ? "&#B366D9" + (int) def.basePrice() + " essence"
                : "&#FFD700⛁ " + (int) def.basePrice();
        Material expandMat = mat(cfg("Expand.AvailableMaterial"), Material.PISTON);
        String expandName = "&#55FF55&lExpand Territory";
        List<String> expandLore =
            tok(
                cfgList(
                    "Expand.AvailableLore",
                    List.of(
                        "",
                        " &f&lINFO",
                        " &8| &#888888Expand your cell by 1 block",
                        "",
                        " &f&lSTATISTICS",
                        " &8| &#888888Cost: {cost}",
                        " &8| &#888888Expansions: &#FFFFFF{expansions}&#888888/&#FFFFFF{max_expansions}",
                        "",
                        " &8| &aLeft-click &#AAAAAAto expand.",
                        "")),
                "cost",
                costStr,
                "expansions",
                rent.expandCount(),
                "max_expansions",
                def.maxExpansions(),
                "remaining",
                remaining,
                "remaining_plural",
                remaining == 1 ? "" : "s");
        inventory.setItem(14, new ItemBuilder(expandMat).name(expandName).lore(expandLore).build());
      } else if (activeRifts.containsKey(player.getUniqueId())) {
        inventory.setItem(
            14,
            new ItemBuilder(Material.ENDER_EYE)
                .name("&#AA00FF&lRift Open")
                .lore(
                    cfgList(
                        "RiftOpen.Lore",
                        List.of(
                            "",
                            " &f&lINFO",
                            " &8| &#888888A rift is open at your cell boundary.",
                            "",
                            " &f&lSTATUS",
                            " &8| &#888888Walk into the rift to evolve.",
                            "")))
                .glow()
                .build());
      } else {
        Material riftMat = mat(cfg("Rift.Material"), Material.ENDER_EYE);
        String riftName = "&#AA00FF&lOpen a Rift";
        List<String> riftLore =
            tok(
                cfgList(
                    "Rift.Lore",
                    List.of(
                        "",
                        " &f&lINFO",
                        " &8| &#888888Open a rift to an unknown",
                        " &8| &#888888location. Walk through to evolve.",
                        "",
                        " &f&lSTATISTICS",
                        " &8| &#888888Expansions: &#FFFFFF{expansions}&#888888/&#FFFFFF{max_expansions}",
                        "",
                        " &8| &#FF5555This cannot be undone.",
                        "",
                        " &8| &aLeft-click &#AAAAAAto open.",
                        "")),
                "expansions",
                rent.expandCount(),
                "max_expansions",
                def.maxExpansions());
        inventory.setItem(14, new ItemBuilder(riftMat).name(riftName).lore(riftLore).build());
      }
    }

    if (isOwner) {
      int mc = rent.members().size();
      Material membersMat = mat(cfg("Members.Material"), Material.PLAYER_HEAD);
      String membersName = "&#00FFFF&lAllies";
      List<String> membersLore =
          tok(
              cfgList(
                  "Members.Lore",
                  List.of(
                      "",
                      " &f&lINFO",
                      " &8| &#888888Manage who has access to",
                      " &8| &#888888your cell.",
                      "",
                      " &f&lSTATUS",
                      " &8| &#888888{member_count} {member_label} sworn in.",
                      "",
                      " &8| &aLeft-click &#AAAAAAto manage.",
                      "")),
              "member_count",
              mc,
              "member_label",
              mc == 1 ? "ally" : "allies");
      inventory.setItem(
          10, new ItemBuilder(membersMat).name(membersName).lore(membersLore).build());
    }

    if (isOwner) {
      long maxExpiry = System.currentTimeMillis() + MAX_RENT_MS;
      long canAddMs = Math.max(0, maxExpiry - rent.rentExpiry());
      long canAddDays = canAddMs / 86_400_000L;

      if (canAddDays <= 0) {
        inventory.setItem(
            16,
            new ItemBuilder(Material.CLOCK)
                .name("&#888888&lMax Rent Reached")
                .lore(
                    tok(
                        cfgList(
                            "ExtendRent.MaxedLore",
                            List.of(
                                "",
                                " &f&lSTATUS",
                                " &8| &#888888Rental period is maxed at 7 days.",
                                " &8| &#888888Expires: {expires}",
                                "")),
                        "expires",
                        expiresStr))
                .build());
      } else {
        String costPerDay =
            def != null
                ? (def.currency().equals("essence")
                    ? "&#B366D9" + (int) def.basePrice() + " essence"
                    : "&#FFD700⛁ " + (int) def.basePrice())
                : "";
        inventory.setItem(
            16,
            new ItemBuilder(Material.CLOCK)
                .name("&#00FFFF&lExtend Rent")
                .lore(
                    tok(
                        cfgList(
                            "ExtendRent.Lore",
                            List.of(
                                "",
                                " &f&lINFO",
                                " &8| &#888888Extend your rental period.",
                                " &8| &#888888Max 7 days from now.",
                                "",
                                " &f&lSTATUS",
                                " &8| &#888888Expires: {expires}",
                                " &8| &#888888Can add: {extend_days} day{extend_plural}",
                                " &8| &#888888Cost per day: {cost}",
                                "",
                                " &8| &aLeft-click &#AAAAAAto add 1 day.",
                                " &8| &#FFEE00Right-click &#AAAAAAto extend max.",
                                "")),
                        "expires",
                        expiresStr,
                        "extend_days",
                        canAddDays,
                        "extend_plural",
                        canAddDays == 1 ? "" : "s",
                        "cost",
                        costPerDay))
                .build());
      }
    }

    Material abandonMat = Material.REDSTONE_BLOCK;
    String abandonName = isOwner ? "&#FF5555&lAbandon Cell" : "&#FF5555&lLeave Cell";
    List<String> abandonLore =
        isOwner
            ? cfgList(
                "Abandon.OwnerLore",
                List.of(
                    "",
                    " &f&lINFO",
                    " &8| &#888888Permanently relinquish your",
                    " &8| &#888888claim to this cell.",
                    "",
                    " &f&lDETAILS",
                    " &8| &#888888All expansions will be undone.",
                    " &8| &#888888The cell will be made available.",
                    "",
                    " &8| &#FF5555This cannot be undone.",
                    "",
                    " &8| &aLeft-click &#AAAAAAto confirm.",
                    ""))
            : cfgList(
                "Abandon.MemberLore",
                List.of(
                    "",
                    " &f&lINFO",
                    " &8| &#888888Step away from this cell.",
                    " &8| &#888888You will lose all access.",
                    "",
                    " &8| &#FF5555This cannot be undone.",
                    "",
                    " &8| &aLeft-click &#AAAAAAto confirm.",
                    ""));
    inventory.setItem(22, new ItemBuilder(abandonMat).name(abandonName).lore(abandonLore).build());
  }

  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    event.setCancelled(true);
    CellRent rent = CellRentManager.getInstance().getPlayerRent(player.getUniqueId());
    if (rent == null) {
      player.closeInventory();
      return true;
    }
    CellDef def = CellManager.getInstance().get(rent.cellName());
    boolean isOwner = rent.ownerUuid().equals(player.getUniqueId());

    switch (slot) {
      case 10 -> {
        if (isOwner) openMemberMenu(player);
      }
      case 12 -> {
        if (isOwner) handleLockToggle(player, rent);
      }
      case 14 -> {
        if (isOwner && def != null) {
          if (rent.expandCount() < def.maxExpansions()) {
            handleExpand(player, rent, def);
          } else if (!activeRifts.containsKey(player.getUniqueId())) {
            openRift(player, rent, def);
          }
        }
      }
      case 16 -> {
        if (isOwner) handleExtendRent(player, rent, def, event.isRightClick());
      }
      case 22 -> {
        if (isOwner) {
          player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 0.9f);
          player.closeInventory();
          Bukkit.getScheduler()
              .runTaskLater(
                  Main.getInstance(),
                  () -> CellAbandonConfirmMenu.getInstance().openMenu(player),
                  1L);
        } else {
          handleLeave(player, rent, false);
        }
      }
    }
    return true;
  }

  private void handleLockToggle(Player player, CellRent rent) {
    CellRentManager.getInstance().updateRent(rent.withLocked(!rent.locked()));
    player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1f);
    refresh(player);
  }

  private void openMemberMenu(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.1f);
    player.closeInventory();
    Bukkit.getScheduler()
        .runTaskLater(Main.getInstance(), () -> CellMemberMenu.getInstance().openMenu(player), 1L);
  }

  private void handleExpand(Player player, CellRent rent, CellDef def) {
    if (rent.expandCount() >= def.maxExpansions()) {
      Messageable.sendError(player, "You have reached the maximum number of expansions.");
      return;
    }
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return;
    PlayerProfile profile = dc.getLocal(player);
    if (profile == null) return;

    double cost = def.basePrice();
    if (def.currency().equals("essence")) {
      if (!profile.hasEssence((int) cost)) {
        Messageable.sendError(
            player, "Not enough essence. Need &#FFFFFF" + (int) cost + " essence.");
        return;
      }
      profile.removeEssence((int) cost);
    } else {
      if (profile.coins < cost) {
        Messageable.sendError(
            player, "Not enough coins. Need &#FFD700⛁ " + String.format("%.0f", cost));
        return;
      }
      profile.removeCoins(cost);
    }
    profile.save(null);
    performExpansion(player, rent, def);
  }

  private void performExpansion(Player player, CellRent rent, CellDef def) {
    World world = Bukkit.getWorld(def.world());
    if (world == null) return;

    Material wallMat = Material.matchMaterial(def.wallMaterial());
    if (wallMat == null) wallMat = Material.PURPLE_WOOL;

    int x1 = def.x1(), y1 = def.y1(), z1 = def.z1();
    int x2 = def.x2(), y2 = def.y2(), z2 = def.z2();
    int newX1 = x1, newY1 = y1, newZ1 = z1;
    int newX2 = x2, newY2 = y2, newZ2 = z2;

    switch (def.upgradeDirection().toUpperCase()) {
      case "N" -> {
        int faceZ = Math.min(z1, z2);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
          for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            world.getBlockAt(x, y, faceZ - 2).setType(wallMat, false);
            world.getBlockAt(x, y, faceZ - 1).setType(Material.AIR, false);
          }
        newZ1 = Math.min(z1, z2) - 1;
      }
      case "S" -> {
        int faceZ = Math.max(z1, z2);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
          for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            world.getBlockAt(x, y, faceZ + 2).setType(wallMat, false);
            world.getBlockAt(x, y, faceZ + 1).setType(Material.AIR, false);
          }
        newZ2 = Math.max(z1, z2) + 1;
      }
      case "E" -> {
        int faceX = Math.max(x1, x2);
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
          for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            world.getBlockAt(faceX + 2, y, z).setType(wallMat, false);
            world.getBlockAt(faceX + 1, y, z).setType(Material.AIR, false);
          }
        newX2 = Math.max(x1, x2) + 1;
      }
      case "W" -> {
        int faceX = Math.min(x1, x2);
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
          for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            world.getBlockAt(faceX - 2, y, z).setType(wallMat, false);
            world.getBlockAt(faceX - 1, y, z).setType(Material.AIR, false);
          }
        newX1 = Math.min(x1, x2) - 1;
      }
    }

    CellDef newDef = def.withBounds(newX1, newY1, newZ1, newX2, newY2, newZ2);
    CellManager.getInstance().update(newDef);
    CellWorldGuardManager.getInstance()
        .expandRegion(def.name(), world, newX1, newY1, newZ1, newX2, newY2, newZ2);

    int newCount = rent.expandCount() + 1;
    CellRentManager.getInstance().updateRent(rent.withExpandCount(newCount));

    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
    Messageable.sendSuccess(
        player, "Cell expanded! &#888888(" + newCount + "/" + def.maxExpansions() + ")");

    if (newCount >= def.maxExpansions()) {
      Messageable.sendInfo(
          player, "&#AA00FFYou've reached the limit. A rift awaits — open the cell menu.");
    }
    refresh(player);
  }

  private void openRift(Player player, CellRent rent, CellDef def) {
    player.closeInventory();
    World world = Bukkit.getWorld(def.world());
    if (world == null) return;

    int x1 = Math.min(def.x1(), def.x2()), x2 = Math.max(def.x1(), def.x2());
    int y1 = Math.min(def.y1(), def.y2()), y2 = Math.max(def.y1(), def.y2());
    int z1 = Math.min(def.z1(), def.z2()), z2 = Math.max(def.z1(), def.z2());
    double cx = (x1 + x2) / 2.0 + 0.5;
    double cy = y1 + 1.5;
    double cz = (z1 + z2) / 2.0 + 0.5;

    Location wallCenter =
        switch (def.upgradeDirection()) {
          case "E" -> new Location(world, x2 + 1.5, cy, cz);
          case "W" -> new Location(world, x1 - 0.5, cy, cz);
          case "N" -> new Location(world, cx, cy, z1 - 0.5);
          case "S" -> new Location(world, cx, cy, z2 + 1.5);
          default -> new Location(world, cx, cy, cz);
        };

    Messageable.sendInfo(player, "&#AA00FFA rift tears open at the boundary of your cell...");
    Messageable.sendInfo(player, "&#888888Walk into the rift to enter the unknown.");

    activeRifts.put(
        player.getUniqueId(), new RiftData(rent.cellName(), wallCenter.clone(), Long.MAX_VALUE));

    final World w = world;
    final Location loc = wallCenter.clone();

    new BukkitRunnable() {
      int ticks = 0;

      public void run() {
        if (!activeRifts.containsKey(player.getUniqueId())) {
          cancel();
          return;
        }

        if (ticks < 8) {
          w.spawnParticle(Particle.PORTAL, loc, 200, 0.6, 1.5, 0.6, 1.0);
          w.spawnParticle(Particle.REVERSE_PORTAL, loc, 120, 0.5, 1.2, 0.5, 0.8);
          w.spawnParticle(Particle.DRAGON_BREATH, loc, 60, 0.4, 1.0, 0.4, 0.15);
          w.spawnParticle(Particle.END_ROD, loc, 40, 0.8, 1.5, 0.8, 0.1);
          w.spawnParticle(Particle.WITCH, loc, 30, 0.5, 1.1, 0.5, 0.0);
          w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 25, 0.4, 0.9, 0.4, 0.05);
          w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20, 0.3, 0.8, 0.3, 0.02);
          w.spawnParticle(
              Particle.DUST,
              loc,
              30,
              0.6,
              1.2,
              0.6,
              0.0,
              new Particle.DustOptions(org.bukkit.Color.fromRGB(170, 0, 255), 3.0f));
          switch (ticks) {
            case 0 -> {
              w.strikeLightningEffect(loc);
              player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.5f);
              player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.3f);
              player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.2f);
              w.playSound(loc, Sound.BLOCK_END_PORTAL_SPAWN, 2.0f, 0.5f);
            }
            case 1 -> {
              player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
              player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 0.7f);
            }
            case 2 -> {
              w.strikeLightningEffect(loc);
              player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.2f);
              w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.6f);
            }
            case 4 -> {
              player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.6f, 0.5f);
              player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 0.55f);
            }
          }
        } else {
          boolean strongPulse = (ticks % 6 == 0);
          boolean medPulse = (ticks % 3 == 0);
          w.spawnParticle(
              Particle.PORTAL, loc, strongPulse ? 80 : (medPulse ? 60 : 40), 0.4, 1.0, 0.4, 0.5);
          w.spawnParticle(
              Particle.REVERSE_PORTAL,
              loc,
              strongPulse ? 50 : (medPulse ? 35 : 20),
              0.3,
              0.8,
              0.3,
              0.4);
          w.spawnParticle(
              Particle.DRAGON_BREATH,
              loc,
              strongPulse ? 22 : (medPulse ? 14 : 8),
              0.3,
              0.8,
              0.3,
              0.06);
          w.spawnParticle(
              Particle.END_ROD, loc, strongPulse ? 12 : (medPulse ? 7 : 4), 0.5, 1.1, 0.5, 0.04);
          w.spawnParticle(
              Particle.ELECTRIC_SPARK,
              loc,
              strongPulse ? 10 : (medPulse ? 6 : 3),
              0.3,
              0.7,
              0.3,
              0.02);
          w.spawnParticle(
              Particle.WITCH, loc, strongPulse ? 10 : (medPulse ? 5 : 2), 0.4, 0.9, 0.4, 0.0);
          if (strongPulse) {
            w.spawnParticle(
                Particle.DUST,
                loc,
                20,
                0.5,
                1.0,
                0.5,
                0.0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(170, 0, 255), 2.5f));
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 15, 0.4, 0.9, 0.4, 0.03);
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.4f);
          }
          if (ticks % 4 == 0) {
            player.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, ticks % 8 == 0 ? 1.2f : 0.8f);
          }
          if (ticks % 8 == 0) {
            player.playSound(loc, Sound.ENTITY_ENDERMAN_AMBIENT, 0.9f, 0.4f);
          }
          if (ticks % 12 == 0) {
            w.strikeLightningEffect(loc);
            w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.6f, 0.8f);
          }
        }
        ticks++;
      }
    }.runTaskTimer(Main.getInstance(), 0L, 10L);
  }

  private void handleExtendRent(Player player, CellRent rent, CellDef def, boolean extendMax) {
    long maxExpiry = System.currentTimeMillis() + MAX_RENT_MS;
    long canAddMs = Math.max(0, maxExpiry - rent.rentExpiry());
    long canAddDays = canAddMs / 86_400_000L;

    if (canAddDays <= 0) {
      Messageable.sendError(player, "Your rent is already at the 7-day maximum.");
      return;
    }
    if (def == null) return;

    long daysToAdd = extendMax ? canAddDays : 1L;

    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return;
    PlayerProfile profile = dc.getLocal(player);
    if (profile == null) return;

    double cost = def.basePrice() * daysToAdd;
    if (def.currency().equals("essence")) {
      if (!profile.hasEssence((int) cost)) {
        Messageable.sendError(
            player, "Not enough essence. Need &#FFFFFF" + (int) cost + " essence.");
        return;
      }
      profile.removeEssence((int) cost);
    } else {
      if (profile.coins < cost) {
        Messageable.sendError(
            player, "Not enough coins. Need &#FFD700⛁ " + String.format("%.0f", cost));
        return;
      }
      profile.removeCoins(cost);
    }
    profile.save(null);

    long newExpiry = rent.rentExpiry() + daysToAdd * 86_400_000L;
    CellRentManager.getInstance().updateRent(rent.withRentExpiry(newExpiry));

    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
    Messageable.sendSuccess(
        player,
        "Rent extended by &#FFFFFF"
            + daysToAdd
            + " day"
            + (daysToAdd == 1 ? "" : "s")
            + "&#00FFFF.");
    refresh(player);
  }

  void handleLeave(Player player, CellRent rent, boolean isOwner) {
    if (isOwner) {
      player.closeInventory();
      String abandonedCell = rent.cellName();
      CellBlockBackupManager.getInstance().restore(abandonedCell);
      CellDef restored = CellManager.getInstance().get(abandonedCell);
      if (restored != null) placeExpansionWall(restored);
      CellWorldGuardManager.getInstance().deleteRegion(abandonedCell);
      CellRentManager.getInstance().removeRent(abandonedCell);
      org.benski.evolv3.core.managers.CellHologramManager.getInstance().refresh(abandonedCell);
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1f, 1f);
      Messageable.sendSuccess(
          player, "You have abandoned your cell &#FFFFFF" + abandonedCell + "&#00FFFF.");
    } else {
      CellRentManager.getInstance().removeMember(rent.cellName(), player.getUniqueId());
      player.closeInventory();
      Messageable.sendSuccess(
          player, "You have left the cell &#FFFFFF" + rent.cellName() + "&#00FFFF.");
    }
  }

  private void placeExpansionWall(CellDef def) {
    World world = Bukkit.getWorld(def.world());
    if (world == null) return;
    Material wallMat = Material.matchMaterial(def.wallMaterial());
    if (wallMat == null) wallMat = Material.PURPLE_WOOL;
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

  private void openEvolveConfirm(Player player) {
    player.closeInventory();
    Bukkit.getScheduler()
        .runTaskLater(
            Main.getInstance(), () -> CellEvolveConfirmMenu.getInstance().openMenu(player), 1L);
  }

  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
  }

  private static FileConfiguration menuCfg() {
    return ConfigRegistry.getInstance().get("menus");
  }

  private static String cfg(String key) {
    return cfg(key, "");
  }

  private static String cfg(String key, String fallback) {
    FileConfiguration c = menuCfg();
    if (c == null) return fallback;
    return c.getString("Cell Menu." + key, fallback);
  }

  private static List<String> cfgList(String key, List<String> fallback) {
    FileConfiguration c = menuCfg();
    if (c == null) return fallback;
    List<String> list = c.getStringList("Cell Menu." + key);
    return list.isEmpty() ? fallback : list;
  }

  private static Material mat(String name, Material fallback) {
    if (name == null || name.isBlank()) return fallback;
    Material m = Material.matchMaterial(name);
    return m != null ? m : fallback;
  }

  private static String tok(String s, Object... pairs) {
    for (int i = 0; i + 1 < pairs.length; i += 2)
      s = s.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
    return s;
  }

  private static List<String> tok(List<String> lines, Object... pairs) {
    List<String> out = new ArrayList<>(lines.size());
    for (String line : lines) out.add(tok(line, pairs));
    return out;
  }
}
