package org.benski.evolv3.listeners.pickaxe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager.Form;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.core.managers.player.PickupAnimationManager;
import org.benski.evolv3.core.managers.world.WorldGuardManager;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles the Omnitool ability lifecycle:
 *
 * <ul>
 *   <li>Transforms a registered pickaxe into an axe/sword when entering the matching region.
 *   <li>Reverts to the pickaxe when leaving the region or disconnecting.
 *   <li>Adds pickaxe XP when breaking logs in axe form.
 *   <li>Plays the pickup animation for mob drops in sword form.
 *   <li>Warns the player when they try to open the pickaxe menu while transformed.
 * </ul>
 */
public class OmnitoolListener extends BaseListener {
  /** Last known omnitool region per player: "axe", "sword", or null (none). */
  private final Map<UUID, String> lastRegionState = new HashMap<>();

  /** Creates and registers the OmnitoolListener. @param plugin the owning plugin */
  public OmnitoolListener(JavaPlugin plugin) {
    super(plugin);
  }

  /** Called when player move occurs. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerMove(PlayerMoveEvent event) {
    Location from = event.getFrom();
    Location to = event.getTo();

    if (from.getBlockX() == to.getBlockX()
        && from.getBlockY() == to.getBlockY()
        && from.getBlockZ() == to.getBlockZ()) return;

    Player player = event.getPlayer();
    WorldGuardManager wg = WorldGuardManager.getInstance();

    boolean inAxe = wg.isWithinOmnitoolAxeRegion(to);
    boolean inSword = !inAxe && wg.isWithinOmnitoolSwordRegion(to);

    String current = inAxe ? "axe" : inSword ? "sword" : null;
    String last = lastRegionState.get(player.getUniqueId());
    if (Objects.equals(current, last)) return;

    OmnitoolManager omni = OmnitoolManager.getInstance();

    if (current == null) {
      lastRegionState.put(player.getUniqueId(), null);
      if (omni.isInOmnitoolMode(player)) {
        omni.revertToPickaxe(player);
        playTransformAnimation(player);
        sendActionBar(player, "  &#888888⚒ Omnitool &cDeactivated");
      }
      return;
    }

    Form targetForm = current.equals("axe") ? Form.AXE : Form.SWORD;

    if (omni.isInOmnitoolMode(player)) {
      lastRegionState.put(player.getUniqueId(), current);
      if (omni.getForm(player) != targetForm) {
        omni.revertToPickaxe(player);
        if (omni.transformTo(player, targetForm)) {
          playTransformAnimation(player);
          sendTransformMessage(player, targetForm);
        }
      }
    } else {
      if (omni.transformTo(player, targetForm)) {
        lastRegionState.put(player.getUniqueId(), current);
        playTransformAnimation(player);
        sendTransformMessage(player, targetForm);
      }
    }
  }

  /** Called when player quit occurs. */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    OmnitoolManager.getInstance().revertToPickaxe(player);
    lastRegionState.remove(player.getUniqueId());
  }

  /**
   * Bug #1: Player enters omnitool region without a pickaxe, then switches to one. Movement events
   * won't re-fire since their position hasn't changed, so we catch the slot change here and trigger
   * a transform if needed.
   */
  @EventHandler
  public void onItemHeld(PlayerItemHeldEvent event) {
    Player player = event.getPlayer();
    String region = lastRegionState.get(player.getUniqueId());
    if (region == null) return;

    OmnitoolManager omni = OmnitoolManager.getInstance();
    if (omni.isInOmnitoolMode(player)) return;

    Form targetForm = region.equals("axe") ? Form.AXE : Form.SWORD;
    org.bukkit.Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (!player.isOnline()) return;
              if (omni.isInOmnitoolMode(player)) return;
              if (omni.transformTo(player, targetForm)) {
                playTransformAnimation(player);
                sendTransformMessage(player, targetForm);
              }
            },
            1L);
  }

  /**
   * Bug #2: When the omnitool sword/axe breaks, clean up the stored state and restore the original
   * pickaxe so it isn't lost.
   */
  @EventHandler
  public void onItemBreak(PlayerItemBreakEvent event) {
    Player player = event.getPlayer();
    ItemMeta meta = event.getBrokenItem().getItemMeta();
    if (meta == null) return;
    if (!meta.getPersistentDataContainer()
        .has(OmnitoolManager.OMNITOOL_ITEM_KEY, PersistentDataType.STRING)) return;

    OmnitoolManager.getInstance().revertToPickaxe(player);
    lastRegionState.remove(player.getUniqueId());
  }

  /** Called when player interact occurs. */
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getAction().isLeftClick()) return;

    Player player = event.getPlayer();
    if (!OmnitoolManager.getInstance().isInOmnitoolMode(player)) return;

    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType().isAir()) return;
    ItemMeta meta = held.getItemMeta();
    if (meta == null) return;
    if (!meta.getPersistentDataContainer()
        .has(OmnitoolManager.OMNITOOL_ITEM_KEY, PersistentDataType.STRING)) return;

    sendActionBar(player, "&#FF5555⚠ &#FFFFFFPickaxe menu unavailable.");
    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
  }

  /**
   * Runs at MONITOR priority so other handlers can cancel the event first. When a player in axe
   * form breaks a log, XP is credited to their pickaxe.
   *
   * <p>Requires the main-hand item to actually be the omnitool-tagged axe — {@code
   * isInOmnitoolMode} only means the player's pickaxe is transformed <em>somewhere</em> in their
   * inventory, not that they're holding it right now. Without this check, breaking a log while in
   * axe-form region but holding an unrelated item would credit pickaxe XP and (via {@link
   * PickaxeManager#updateLore}) overwrite that unrelated item's name/lore with the pickaxe's.
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onLogBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    OmnitoolManager omni = OmnitoolManager.getInstance();

    if (!omni.isInOmnitoolMode(player)) return;
    if (omni.getForm(player) != Form.AXE) return;
    if (!isLog(event.getBlock().getType())) return;

    ItemStack held = player.getInventory().getItemInMainHand();
    if (!isOmnitoolItem(held)) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int xpAmount = config.getInt("Omnitool.LogBreakXp", 5);
    if (xpAmount <= 0) return;

    PickaxeManager.getInstance().addXp(player, xpAmount);
    PickaxeManager.getInstance().updateLore(player, held);
    sendXpActionBar(player);
  }

  /** Called when entity death occurs. */
  @EventHandler(priority = EventPriority.HIGH)
  public void onEntityDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    Player killer = entity.getKiller();
    if (killer == null) return;

    OmnitoolManager omni = OmnitoolManager.getInstance();
    if (!omni.isInOmnitoolMode(killer)) return;
    if (omni.getForm(killer) != Form.SWORD) return;

    ItemStack killerHeld = killer.getInventory().getItemInMainHand();
    boolean holdingOmnitoolSword = isOmnitoolItem(killerHeld);

    List<ItemStack> drops = new ArrayList<>(event.getDrops());
    List<ItemStack> nonEmpty =
        drops.stream().filter(i -> i != null && !i.getType().isAir()).toList();
    if (nonEmpty.isEmpty()) return;

    event.getDrops().clear();
    Location loc = entity.getLocation().add(0, 1, 0);

    for (ItemStack drop : nonEmpty) {
      Map<Integer, ItemStack> overflow = killer.getInventory().addItem(drop.clone());
      if (loc.getWorld() != null) {
        for (ItemStack leftover : overflow.values()) {
          loc.getWorld().dropItemNaturally(loc, leftover);
        }
      }
    }

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int xpAmount = config.getInt("Omnitool.MobKillXp", 5);

    PickupAnimationManager.getInstance()
        .playPickupAnimation(
            killer,
            loc,
            nonEmpty,
            nonEmpty.get(0).getType(),
            PreferenceType.DropCategory.NORMAL,
            Math.max(0, xpAmount));

    if (xpAmount > 0 && holdingOmnitoolSword) {
      PickaxeManager.getInstance().addXp(killer, xpAmount);
      PickaxeManager.getInstance().updateLore(killer, killerHeld);
      sendXpActionBar(killer);
    }
  }

  /** Plays transform animation. */
  private void playTransformAnimation(Player player) {
    Location loc = player.getLocation().add(0, 1, 0);
    if (loc.getWorld() == null) return;

    for (int i = 0; i < 20; i++) {
      double angle = 2 * Math.PI * i / 20;
      loc.getWorld()
          .spawnParticle(
              Particle.WITCH, loc.clone().add(Math.cos(angle), 0, Math.sin(angle)), 2, 0, 0, 0, 0);
    }
    loc.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.3, 0.5, 0.3, 0.5);
    loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
    loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 0.8f);
  }

  /** Sends transform message. */
  private void sendTransformMessage(Player player, Form form) {
    sendActionBar(player, "  &#888888⚒ Omnitool &aActivated");
  }

  /** Sends action bar. */
  private static void sendActionBar(Player player, String raw) {
    String formatted = Utils.formatMessage(player, raw);
    player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(formatted));
  }

  /** Returns {@code true} if log. */
  private static boolean isLog(Material material) {
    String name = material.name();
    return name.endsWith("_LOG") || name.endsWith("_STEM") || material == Material.BAMBOO_BLOCK;
  }

  /** Returns {@code true} if the item is the player's transformed omnitool axe/sword. */
  private static boolean isOmnitoolItem(ItemStack item) {
    if (item == null || item.getType().isAir()) return false;
    ItemMeta meta = item.getItemMeta();
    return meta != null
        && meta.getPersistentDataContainer()
            .has(OmnitoolManager.OMNITOOL_ITEM_KEY, PersistentDataType.STRING);
  }

  /** Sends xp action bar. */
  private static void sendXpActionBar(Player player) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    String actionBar = config.getString(ConfigPaths.Default.Pickaxe.ACTION_BAR, "");
    if (actionBar.isEmpty()) return;
    String formatted = Utils.formatMessage(player, actionBar);
    player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(formatted));
  }
}
