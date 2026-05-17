package org.benski.evolv3.listeners;

import io.papermc.paper.event.entity.EntityMoveEvent;
import java.util.UUID;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.SpawnerHologramManager;
import org.benski.evolv3.core.managers.SpawnerManager;
import org.benski.evolv3.core.managers.SpawnerManager.Spawner;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles block placement and destruction events for custom mob spawners.
 *
 * <h3>Responsibilities</h3>
 *
 * <ul>
 *   <li>{@link BlockPlaceEvent} — detect PDC-tagged SPAWNER items, register the spawner with {@link
 *       SpawnerManager}, and spawn its hologram.
 *   <li>{@link BlockBreakEvent} — cancel vanilla break, remove the spawner registration, drop a
 *       re-configured spawner item, and remove the hologram.
 *   <li>{@link ChunkLoadEvent} — respawn holograms for spawners whose position falls in the
 *       newly-loaded chunk (same pattern as MineListener).
 * </ul>
 */
public class SpawnerListener extends BaseListener {
  /** PDC keys matching those used by {@link org.benski.evolv3.commands.SpawnerCommand}. */
  private static final NamespacedKey KEY_MOB = new NamespacedKey("evolv3", "spawner_mob");

  private static final NamespacedKey KEY_INTERVAL = new NamespacedKey("evolv3", "spawner_interval");
  private static final NamespacedKey KEY_CHANCE = new NamespacedKey("evolv3", "spawner_chance");
  private static final NamespacedKey KEY_NAME = new NamespacedKey("evolv3", "spawner_name");

  public SpawnerListener(JavaPlugin plugin) {
    super(plugin);
  }

  /**
   * When a SPAWNER block is placed and its item carries the evolv3 PDC mob tag, a new {@link
   * Spawner} is registered and a hologram is spawned above it.
   *
   * <p>The vanilla spawner's entity type is also set for visual feedback in-world.
   */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    Block block = event.getBlockPlaced();
    if (block.getType() != Material.SPAWNER) return;

    ItemStack item = event.getItemInHand();
    if (item.getType() != Material.SPAWNER) return;

    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;

    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    if (!pdc.has(KEY_MOB, PersistentDataType.STRING)) return;

    String mobType = pdc.getOrDefault(KEY_MOB, PersistentDataType.STRING, "ZOMBIE");
    String mobName = pdc.getOrDefault(KEY_NAME, PersistentDataType.STRING, mobType);
    int interval = pdc.getOrDefault(KEY_INTERVAL, PersistentDataType.INTEGER, 30);
    double chance = pdc.getOrDefault(KEY_CHANCE, PersistentDataType.DOUBLE, 80.0);

    Location loc = block.getLocation();
    Player player = event.getPlayer();

    if (!SpawnerManager.getInstance().isSpawnerRegion(loc)) {
      event.setCancelled(true);
      Messageable.sendRaw(
          player, "  &#FF5555Spawners can only be placed inside a &#FFFFFFspawner&#FF5555 region.");
      return;
    }

    org.bukkit.configuration.file.FileConfiguration spawnCfg =
        org.benski.evolv3.registry.ConfigRegistry.getInstance()
            .get(org.benski.evolv3.core.constants.ConfigNames.SPAWNERS);
    int mobLimit = spawnCfg != null ? spawnCfg.getInt("defaults.mob-limit", 15) : 15;
    int searchRadius = spawnCfg != null ? spawnCfg.getInt("defaults.mob-search-radius", 32) : 32;

    String id = UUID.randomUUID().toString();
    Spawner spawner =
        new Spawner(
            id,
            loc.getWorld() != null ? loc.getWorld().getName() : "world",
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ(),
            mobType,
            interval,
            chance,
            mobLimit,
            searchRadius,
            mobName);

    SpawnerManager.getInstance().addSpawner(spawner);
    SpawnerHologramManager.getInstance().spawnHologram(spawner);

    BlockState state = block.getState();
    if (state instanceof CreatureSpawner cs) {
      try {
        cs.setSpawnedType(EntityType.valueOf(mobType.toUpperCase()));
        cs.update(true);
      } catch (IllegalArgumentException ignored) {
      }
    }

    Messageable.sendRaw(
        player,
        "  &#FF5555⚔ &#00FFFFSpawner placed! &#888888("
            + formatMobName(mobType)
            + " &#888888| &#FFFFFF"
            + interval
            + "s &#888888| &#FFFFFF"
            + chance
            + "%)");
  }

  /**
   * When a registered spawner block is broken, the event is cancelled to suppress vanilla drops.
   * The plugin removes the spawner registration, drops a configured item, and removes the hologram.
   */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (block.getType() != Material.SPAWNER) return;

    Location loc = block.getLocation();
    Spawner existing = SpawnerManager.getInstance().getSpawnerAt(loc);
    if (existing == null) return;

    event.setCancelled(true);
    event.setDropItems(false);

    Player player = event.getPlayer();

    SpawnerManager.getInstance().removeSpawnerAt(loc);
    SpawnerHologramManager.getInstance().removeHologram(existing.id());

    ItemStack drop = buildSpawnerItem(existing);
    if (loc.getWorld() != null) {
      loc.getWorld().dropItemNaturally(loc.add(0.5, 0.5, 0.5), drop);
    }

    Messageable.sendRaw(player, "  &#FF5555⚔ &#00FFFFSpawner removed. &#888888(item dropped)");
  }

  /**
   * Purges any stale PDC-tagged {@link TextDisplay} entities saved to disk in older builds, then
   * spawns holograms for spawners whose position falls in the newly-loaded chunk.
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    for (org.bukkit.entity.Entity e : event.getChunk().getEntities()) {
      if (!(e instanceof TextDisplay td)) continue;
      if (td.getPersistentDataContainer()
          .has(SpawnerHologramManager.PDC_KEY, PersistentDataType.STRING)) {
        td.remove();
      }
    }

    SpawnerHologramManager.getInstance().onChunkLoad(event.getChunk());
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEntityMove(EntityMoveEvent event) {
    if (!(event.getEntity() instanceof LivingEntity)) return;
    if (event.getEntity() instanceof Player) return;

    String spawnerId =
        event
            .getEntity()
            .getPersistentDataContainer()
            .get(SpawnerManager.SPAWNER_MOB_KEY, PersistentDataType.STRING);
    if (spawnerId == null) return;

    int[] bounds = SpawnerManager.getInstance().getRegionBounds(spawnerId);
    if (bounds == null) return;

    Location to = event.getTo();
    boolean inside =
        to.getX() >= bounds[0]
            && to.getX() <= bounds[3] + 1
            && to.getY() >= bounds[1]
            && to.getY() <= bounds[4] + 1
            && to.getZ() >= bounds[2]
            && to.getZ() <= bounds[5] + 1;
    if (!inside) event.setCancelled(true);
  }

  /**
   * Builds a configured SPAWNER item matching the given spawner's settings, suitable for dropping
   * on block break.
   *
   * @param s the spawner whose settings to encode
   * @return a tagged, named SPAWNER item stack
   */
  private @org.jetbrains.annotations.NotNull ItemStack buildSpawnerItem(
      @org.jetbrains.annotations.NotNull Spawner s) {
    ItemStack item = new ItemStack(Material.SPAWNER);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    meta.getPersistentDataContainer().set(KEY_MOB, PersistentDataType.STRING, s.mobType());
    meta.getPersistentDataContainer().set(KEY_NAME, PersistentDataType.STRING, s.spawnerName());
    meta.getPersistentDataContainer()
        .set(KEY_INTERVAL, PersistentDataType.INTEGER, s.intervalSeconds());
    meta.getPersistentDataContainer().set(KEY_CHANCE, PersistentDataType.DOUBLE, s.spawnChance());

    String displayName =
        net.matrixcreations.libraries.MatrixColorAPI.process("&6⚙ " + s.spawnerName() + " Spawner");
    meta.displayName(
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize(displayName));

    java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
    lore.add(
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize(
                net.matrixcreations.libraries.MatrixColorAPI.process(
                    "&#888888Name: &#FFFFFF" + s.spawnerName())));
    lore.add(
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize(
                net.matrixcreations.libraries.MatrixColorAPI.process(
                    "&#888888Mob: &#FFFFFF" + formatMobName(s.mobType()))));
    lore.add(
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize(
                net.matrixcreations.libraries.MatrixColorAPI.process(
                    "&#888888Interval: &#FFFFFF" + s.intervalSeconds() + "s")));
    lore.add(
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize(
                net.matrixcreations.libraries.MatrixColorAPI.process(
                    "&#888888Chance: &#FFFFFF" + s.spawnChance() + "%")));
    lore.add(
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize(
                net.matrixcreations.libraries.MatrixColorAPI.process("&#888888Place to activate")));
    meta.lore(lore);

    if (meta instanceof BlockStateMeta bsm) {
      BlockState state = bsm.getBlockState();
      if (state instanceof CreatureSpawner cs) {
        try {
          cs.setSpawnedType(EntityType.valueOf(s.mobType().toUpperCase()));
          bsm.setBlockState(cs);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }

    item.setItemMeta(meta);
    return item;
  }

  /** Converts "ZOMBIE_PIGMAN" → "Zombie Pigman". */
  private static @org.jetbrains.annotations.NotNull String formatMobName(
      @org.jetbrains.annotations.NotNull String entityTypeName) {
    StringBuilder sb = new StringBuilder();
    for (String word : entityTypeName.split("_")) {
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
    }
    return sb.toString();
  }
}
