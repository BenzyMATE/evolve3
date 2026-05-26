package org.benski.evolv3.listeners.mine;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.cell.CellHologramManager;
import org.benski.evolv3.core.managers.cell.CellManager;
import org.benski.evolv3.core.managers.cell.CellManager.CellDef;
import org.benski.evolv3.core.managers.cell.CellRentManager;
import org.benski.evolv3.core.types.CellPermission;
import org.benski.evolv3.menus.cell.CellEvolveConfirmMenu;
import org.benski.evolv3.menus.cell.CellMenu;
import org.benski.evolv3.menus.cell.CellRentInfoMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Enforces per-permission protection inside cell regions. */
public class CellListener extends BaseListener {
  /** Creates and registers the CellListener. @param plugin the owning plugin */
  public CellListener(JavaPlugin plugin) {
    super(plugin);
  }

  /** Called when block break occurs. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.isOp()) return;

    Block block = event.getBlock();
    CellDef cell =
        CellManager.getInstance()
            .findAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    if (cell == null) return;
    if (!CellRentManager.getInstance().isRented(cell.name())) return;

    if (!CellRentManager.getInstance()
        .hasMemberPermission(cell.name(), player.getUniqueId(), CellPermission.BREAK)) {
      event.setCancelled(true);
      Messageable.sendError(player, "You don't have permission to break blocks here.");
    }
  }

  /** Called when block place occurs. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (player.isOp()) return;

    Block block = event.getBlock();
    CellDef cell =
        CellManager.getInstance()
            .findAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    if (cell == null) return;
    if (!CellRentManager.getInstance().isRented(cell.name())) return;

    if (!CellRentManager.getInstance()
        .hasMemberPermission(cell.name(), player.getUniqueId(), CellPermission.BUILD)) {
      event.setCancelled(true);
      Messageable.sendError(player, "You don't have permission to place blocks here.");
    }
  }

  /** Called when iron bars physics occurs. */
  @EventHandler(priority = EventPriority.LOW)
  public void onIronBarsPhysics(BlockPhysicsEvent event) {
    if (event.getBlock().getType() != Material.IRON_BARS) return;
    Block b = event.getBlock();
    String world = b.getWorld().getName();
    int x = b.getX(), y = b.getY(), z = b.getZ();
    if (CellManager.getInstance().findAt(world, x, y, z) != null
        || CellManager.getInstance().findAt(world, x + 1, y, z) != null
        || CellManager.getInstance().findAt(world, x - 1, y, z) != null
        || CellManager.getInstance().findAt(world, x, y, z + 1) != null
        || CellManager.getInstance().findAt(world, x, y, z - 1) != null) {
      event.setCancelled(true);
    }
  }

  /** Called when interact at entity occurs. */
  @EventHandler(priority = EventPriority.NORMAL)
  public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
    if (!(event.getRightClicked() instanceof Interaction ia)) return;
    if (!ia.getPersistentDataContainer()
        .has(CellHologramManager.PDC_KEY, PersistentDataType.STRING)) return;
    String cellName =
        ia.getPersistentDataContainer().get(CellHologramManager.PDC_KEY, PersistentDataType.STRING);
    if (cellName == null) return;
    event.setCancelled(true);
    Player player = event.getPlayer();
    Bukkit.getScheduler()
        .runTask(
            Main.getInstance(), () -> CellRentInfoMenu.getInstance().openFor(player, cellName));
  }

  /** Called when player interact occurs. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onPlayerInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    if (player.isOp()) return;

    var action = event.getAction();
    if (action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
    Block block = event.getClickedBlock();
    if (block == null) return;
    if (!isContainer(block.getType())) return;

    CellDef cell =
        CellManager.getInstance()
            .findAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    if (cell == null) return;
    if (!CellRentManager.getInstance().isRented(cell.name())) return;

    if (!CellRentManager.getInstance()
        .hasMemberPermission(cell.name(), player.getUniqueId(), CellPermission.CONTAINERS)) {
      event.setCancelled(true);
      Messageable.sendError(player, "You don't have permission to open containers here.");
    }
  }

  /** Called when player move occurs. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onPlayerMove(PlayerMoveEvent event) {
    if (event.getFrom().getBlockX() == event.getTo().getBlockX()
        && event.getFrom().getBlockY() == event.getTo().getBlockY()
        && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

    Player player = event.getPlayer();

    CellMenu.RiftData rift = CellMenu.getRift(player.getUniqueId());
    if (rift != null) {
      if (System.currentTimeMillis() > rift.expiry()) {
        CellMenu.clearRift(player.getUniqueId());
      } else {
        Location riftLoc = rift.wallCenter();
        Location playerLoc = event.getTo();
        if (riftLoc.getWorld() != null
            && playerLoc.getWorld() != null
            && riftLoc.getWorld().equals(playerLoc.getWorld())) {
          double dx = riftLoc.getX() - playerLoc.getX();
          double dz = riftLoc.getZ() - playerLoc.getZ();
          if (dx * dx + dz * dz < 2.25) {
            if (CellMenu.tryTriggerRift(player.getUniqueId())) {
              Bukkit.getScheduler()
                  .runTaskLater(
                      Main.getInstance(),
                      () -> CellEvolveConfirmMenu.getInstance().openMenu(player),
                      1L);
            }
          }
        }
      }
    }

    if (player.isOp()) return;

    org.bukkit.block.Block block = event.getTo().getBlock();
    CellDef cell =
        CellManager.getInstance()
            .findAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    if (cell == null) return;

    CellRentManager.CellRent rent = CellRentManager.getInstance().getRent(cell.name());
    if (rent == null || !rent.locked()) return;

    if (rent.ownerUuid().equals(player.getUniqueId())) return;

    if (rent.members().contains(player.getUniqueId())) return;

    event.setCancelled(true);
    Messageable.sendError(player, "This cell is sealed.");
  }

  /** Returns {@code true} if container. */
  private boolean isContainer(Material material) {
    return switch (material) {
      case CHEST,
              TRAPPED_CHEST,
              BARREL,
              SHULKER_BOX,
              WHITE_SHULKER_BOX,
              ORANGE_SHULKER_BOX,
              MAGENTA_SHULKER_BOX,
              LIGHT_BLUE_SHULKER_BOX,
              YELLOW_SHULKER_BOX,
              LIME_SHULKER_BOX,
              PINK_SHULKER_BOX,
              GRAY_SHULKER_BOX,
              LIGHT_GRAY_SHULKER_BOX,
              CYAN_SHULKER_BOX,
              PURPLE_SHULKER_BOX,
              BLUE_SHULKER_BOX,
              BROWN_SHULKER_BOX,
              GREEN_SHULKER_BOX,
              RED_SHULKER_BOX,
              BLACK_SHULKER_BOX,
              FURNACE,
              BLAST_FURNACE,
              SMOKER,
              DISPENSER,
              DROPPER,
              HOPPER,
              CRAFTING_TABLE ->
          true;
      default -> false;
    };
  }
}
