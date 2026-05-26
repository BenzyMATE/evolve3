package org.benski.evolv3.core.managers.world;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages all placed custom mob spawners.
 *
 * <p>Each spawner is a point in the world with a configured mob type, spawn interval, spawn chance,
 * and mob-cap radius. A single shared tick task decrements all countdown timers every second and
 * triggers spawns when ready.
 *
 * <p>Spawner definitions are persisted to {@code localdata/spawners-data.yml}. Default values are
 * read from {@code spawners.yml} via {@link ConfigRegistry}.
 */
public final class SpawnerManager {
  @Getter private static final SpawnerManager instance = new SpawnerManager();

  /** PDC key placed on every mob spawned by this manager so it can be identified and contained. */
  public static final NamespacedKey SPAWNER_MOB_KEY = new NamespacedKey("evolv3", "spawner_id");

  /**
   * Immutable snapshot of a spawner definition.
   *
   * @param id UUID string uniquely identifying this spawner
   * @param world Bukkit world name
   * @param x block X
   * @param y block Y
   * @param z block Z
   * @param mobType EntityType name string (e.g. "ZOMBIE")
   * @param intervalSeconds seconds between spawn attempts
   * @param spawnChance percentage chance (0–100) per attempt to actually spawn
   * @param mobLimit max entities of this type in the spawner region before skipping
   * @param searchRadius radius in blocks used for spawn offset attempts
   * @param spawnerName display name shown in the hologram
   */
  public record Spawner(
      String id,
      String world,
      int x,
      int y,
      int z,
      String mobType,
      int intervalSeconds,
      double spawnChance,
      int mobLimit,
      int searchRadius,
      String spawnerName) {}

  /** All loaded spawners, keyed by id. */
  private final Map<String, Spawner> spawners = new LinkedHashMap<>();

  /** Location key ("world:x:y:z") → spawner id for fast block-position lookups. */
  private final Map<String, String> locationIndex = new ConcurrentHashMap<>();

  /** Remaining ticks until next spawn attempt per spawner id. */
  private final Map<String, AtomicLong> countdowns = new ConcurrentHashMap<>();

  /**
   * Cached WorldGuard region bounds per spawner id. Format: {@code [minX, minY, minZ, maxX, maxY,
   * maxZ]}, or absent if none found.
   */
  private final Map<String, int[]> regionBoundsCache = new ConcurrentHashMap<>();

  /** Single shared task that ticks every 20 ticks (1 second). */
  private BukkitTask sharedTickTask;

  private static final Random RANDOM = new Random();

  /** Creates a new SpawnerManager instance. */
  private SpawnerManager() {}

  /**
   * Loads spawner definitions from disk and starts the shared tick task. Called during plugin
   * enable after configs are initialized.
   */
  public void initialize() {
    loadFromConfig();
  }

  /** Cancels the shared tick task and clears all in-memory state. Called during plugin disable. */
  public void cleanup() {
    if (sharedTickTask != null) {
      sharedTickTask.cancel();
      sharedTickTask = null;
    }
    spawners.clear();
    locationIndex.clear();
    countdowns.clear();
    regionBoundsCache.clear();
  }

  /**
   * Loads all spawners from {@code localdata/spawners-data.yml} and starts the shared countdown
   * task.
   */
  public void loadFromConfig() {
    if (sharedTickTask != null) {
      sharedTickTask.cancel();
      sharedTickTask = null;
    }
    spawners.clear();
    locationIndex.clear();
    countdowns.clear();
    regionBoundsCache.clear();

    File file = getDataFile();
    if (!file.exists()) {
      startSharedTask();
      return;
    }

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection section = cfg.getConfigurationSection("spawners");
    if (section == null) {
      startSharedTask();
      return;
    }

    int defaultInterval = getDefaultInterval();
    double defaultChance = getDefaultChance();
    int defaultLimit = getDefaultMobLimit();
    int defaultRadius = getDefaultSearchRadius();

    for (String id : section.getKeys(false)) {
      String path = "spawners." + id;
      String world = cfg.getString(path + ".world", "world");
      int bx = cfg.getInt(path + ".x");
      int by = cfg.getInt(path + ".y");
      int bz = cfg.getInt(path + ".z");
      String mobType = cfg.getString(path + ".mob-type", "ZOMBIE");
      int interval = cfg.getInt(path + ".interval-seconds", defaultInterval);
      double chance = cfg.getDouble(path + ".spawn-chance", defaultChance);
      int limit = cfg.getInt(path + ".mob-limit", defaultLimit);
      int radius = cfg.getInt(path + ".search-radius", defaultRadius);
      String name = cfg.getString(path + ".spawner-name", mobType);

      Spawner s =
          new Spawner(id, world, bx, by, bz, mobType, interval, chance, limit, radius, name);
      spawners.put(id, s);
      locationIndex.put(locationKey(world, bx, by, bz), id);
      countdowns.put(id, new AtomicLong((long) interval * 20L));
      cacheRegionBounds(s);
    }

    startSharedTask();

    Bukkit.getScheduler()
        .runTaskLater(
            Main.getInstance(),
            () -> {
              for (Spawner s : spawners.values()) cacheRegionBounds(s);
            },
            1L);
  }

  /** Saves all spawner definitions to {@code localdata/spawners-data.yml}. */
  public void saveToConfig() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (Spawner s : spawners.values()) {
      String path = "spawners." + s.id();
      cfg.set(path + ".world", s.world());
      cfg.set(path + ".x", s.x());
      cfg.set(path + ".y", s.y());
      cfg.set(path + ".z", s.z());
      cfg.set(path + ".mob-type", s.mobType());
      cfg.set(path + ".spawner-name", s.spawnerName());
      cfg.set(path + ".interval-seconds", s.intervalSeconds());
      cfg.set(path + ".spawn-chance", s.spawnChance());
      cfg.set(path + ".mob-limit", s.mobLimit());
      cfg.set(path + ".search-radius", s.searchRadius());
    }

    try {
      cfg.save(getDataFile());
    } catch (IOException e) {
      Bukkit.getLogger().severe("[Evolv3] Failed to save spawners data: " + e.getMessage());
    }
  }

  /**
   * Adds a spawner, saves to disk, and resets its countdown.
   *
   * @param s the spawner to add
   */
  public void addSpawner(@NotNull Spawner s) {
    spawners.put(s.id(), s);
    locationIndex.put(locationKey(s.world(), s.x(), s.y(), s.z()), s.id());
    countdowns.put(s.id(), new AtomicLong((long) s.intervalSeconds() * 20L));
    cacheRegionBounds(s);
    saveToConfig();
  }

  /**
   * Replaces a spawner's definition in-place (same id, position, and world). If the interval
   * changed the countdown is reset to the new value.
   *
   * @param s the updated spawner record
   */
  public void updateSpawner(@NotNull Spawner s) {
    Spawner old = spawners.get(s.id());
    spawners.put(s.id(), s);
    if (old == null || old.intervalSeconds() != s.intervalSeconds()) {
      AtomicLong cd = countdowns.get(s.id());
      if (cd != null) cd.set((long) s.intervalSeconds() * 20L);
    }
    cacheRegionBounds(s);
    saveToConfig();
  }

  /**
   * Removes the spawner at the given location, cancels its countdown, and saves.
   *
   * @param loc the block location of the spawner
   * @return true if a spawner existed at that location and was removed
   */
  public boolean removeSpawnerAt(@NotNull Location loc) {
    if (loc.getWorld() == null) return false;
    String key =
        locationKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    String id = locationIndex.remove(key);
    if (id == null) return false;

    spawners.remove(id);
    countdowns.remove(id);
    regionBoundsCache.remove(id);
    saveToConfig();
    return true;
  }

  /**
   * Returns the spawner at the given location, or null if none exists there.
   *
   * @param loc the block location to look up
   * @return the spawner, or null
   */
  public @Nullable Spawner getSpawnerAt(@NotNull Location loc) {
    if (loc.getWorld() == null) return null;
    String key =
        locationKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    String id = locationIndex.get(key);
    return id != null ? spawners.get(id) : null;
  }

  /**
   * Returns an unmodifiable view of all spawners keyed by id.
   *
   * @return all spawners
   */
  public @NotNull Map<String, Spawner> getAllSpawners() {
    return Collections.unmodifiableMap(spawners);
  }

  /**
   * Finds a spawner by its display name (case-insensitive). Returns null if not found. Names are
   * unique so this returns at most one result.
   *
   * @param name the spawner display name
   * @return the matching spawner, or null
   */
  public @Nullable Spawner getSpawnerByName(@NotNull String name) {
    String lower = name.toLowerCase(java.util.Locale.ROOT);
    for (Spawner s : spawners.values()) {
      if (s.spawnerName().toLowerCase(java.util.Locale.ROOT).equals(lower)) return s;
    }
    return null;
  }

  /**
   * Returns the number of seconds until the next spawn attempt for the given id.
   *
   * @param id the spawner id
   * @return seconds until spawn, or 0 if unknown
   */
  public long getSecondsUntilSpawn(@NotNull String id) {
    AtomicLong ticks = countdowns.get(id);
    return ticks != null ? Math.max(0L, ticks.get() / 20L) : 0L;
  }

  /** Starts shared task. */
  private void startSharedTask() {
    sharedTickTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                Main.getInstance(),
                () -> {
                  for (Map.Entry<String, AtomicLong> entry : countdowns.entrySet()) {
                    long remaining = entry.getValue().addAndGet(-20L);
                    if (remaining <= 0) {
                      Spawner s = spawners.get(entry.getKey());
                      if (s != null) {
                        doSpawn(s);
                        entry.getValue().set((long) s.intervalSeconds() * 20L);
                      }
                    }
                  }
                },
                20L,
                20L);

    Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::containMobs, 4L, 4L);
  }

  /**
   * Pushes mobs owned by each spawner (identified via PDC tag) back into their region when they
   * wander out. Runs every 4 ticks for tight containment. Mobs just over the border get velocity
   * nudged; mobs far outside get teleported.
   */
  private void containMobs() {
    for (Spawner s : spawners.values()) {
      int[] b = regionBoundsCache.get(s.id());
      if (b == null) continue;

      World world = Bukkit.getWorld(s.world());
      if (world == null) continue;

      double cx = (b[0] + b[3]) / 2.0;
      double cy = (b[1] + b[4]) / 2.0;
      double cz = (b[2] + b[5]) / 2.0;
      double rx = (b[3] - b[0]) / 2.0 + 12;
      double ry = (b[4] - b[1]) / 2.0 + 12;
      double rz = (b[5] - b[2]) / 2.0 + 12;

      for (Entity entity : world.getNearbyEntities(new Location(world, cx, cy, cz), rx, ry, rz)) {
        if (!(entity instanceof LivingEntity) || entity instanceof org.bukkit.entity.Player)
          continue;
        if (!s.id()
            .equals(
                entity
                    .getPersistentDataContainer()
                    .get(SPAWNER_MOB_KEY, PersistentDataType.STRING))) continue;

        Location el = entity.getLocation();
        double ex = el.getX(), ey = el.getY(), ez = el.getZ();

        boolean inside =
            ex >= b[0]
                && ex <= b[3] + 1
                && ey >= b[1]
                && ey <= b[4] + 1
                && ez >= b[2]
                && ez <= b[5] + 1;
        if (inside) continue;

        double overX = Math.max(0, b[0] - ex) + Math.max(0, ex - b[3] - 1);
        double overZ = Math.max(0, b[2] - ez) + Math.max(0, ez - b[5] - 1);
        double totalOver = overX + overZ;

        if (totalOver > 3.0) {
          entity.teleport(new Location(world, cx, b[1] + 1, cz, el.getYaw(), el.getPitch()));
        } else {
          double dx = cx - ex;
          double dy = cy - ey;
          double dz = cz - ez;
          double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
          if (dist < 0.01) continue;
          entity.setVelocity(
              new org.bukkit.util.Vector(
                  (dx / dist) * 0.6, Math.max(0.1, (dy / dist) * 0.3), (dz / dist) * 0.6));
        }
      }
    }
  }

  /**
   * Counts all living entities of the spawner's mob type within the region bounds, or within a wide
   * radius fallback. Counts by entity type (not PDC tag) so mobs that lost their tag or were
   * spawned before tagging was in place are still counted.
   *
   * @param s the spawner
   * @return the count of matching entities
   */
  public int countNearbyMobs(@NotNull Spawner s) {
    World world = Bukkit.getWorld(s.world());
    if (world == null) return 0;

    EntityType type = parseEntityType(s.mobType());
    if (type == null) return 0;

    int[] bounds = regionBoundsCache.get(s.id());
    if (bounds != null) {
      return countMobsInBounds(world, bounds, type);
    }

    Location center = new Location(world, s.x(), s.y(), s.z());
    int count = 0;
    for (Entity e : world.getNearbyEntities(center, 128, 128, 128)) {
      if (!(e instanceof LivingEntity) || e instanceof org.bukkit.entity.Player) continue;
      if (e.getType() == type) count++;
    }
    return count;
  }

  /** Returns the number of players currently inside the spawner's cached region, or 0. */
  public int countPlayersInRegion(@NotNull Spawner s) {
    World world = Bukkit.getWorld(s.world());
    if (world == null) return 0;

    int[] bounds = regionBoundsCache.get(s.id());
    if (bounds == null) return 0;

    int count = 0;
    for (org.bukkit.entity.Player p : world.getPlayers()) {
      Location pl = p.getLocation();
      if (pl.getBlockX() >= bounds[0]
          && pl.getBlockX() <= bounds[3]
          && pl.getBlockY() >= bounds[1]
          && pl.getBlockY() <= bounds[4]
          && pl.getBlockZ() >= bounds[2]
          && pl.getBlockZ() <= bounds[5]) {
        count++;
      }
    }
    return count;
  }

  /** Count mobs in bounds. */
  private int countMobsInBounds(World world, int[] bounds, EntityType type) {
    double cx = (bounds[0] + bounds[3]) / 2.0;
    double cy = (bounds[1] + bounds[4]) / 2.0;
    double cz = (bounds[2] + bounds[5]) / 2.0;
    double rx = (bounds[3] - bounds[0]) / 2.0 + 1;
    double ry = (bounds[4] - bounds[1]) / 2.0 + 1;
    double rz = (bounds[5] - bounds[2]) / 2.0 + 1;

    int count = 0;
    for (Entity e : world.getNearbyEntities(new Location(world, cx, cy, cz), rx, ry, rz)) {
      if (!(e instanceof LivingEntity) || e instanceof org.bukkit.entity.Player) continue;
      if (e.getType() == type) count++;
    }
    return count;
  }

  /**
   * Removes excess mobs of the spawner's type down to the mob limit. Handles the case where mobs
   * accumulated (e.g. via unloaded-chunk bypass) and need to be actively culled rather than just
   * blocked from spawning.
   */
  private void cullExcessMobs(@NotNull Spawner s, int excess) {
    World world = Bukkit.getWorld(s.world());
    if (world == null) return;

    EntityType type = parseEntityType(s.mobType());
    if (type == null) return;

    int[] bounds = regionBoundsCache.get(s.id());
    if (bounds == null) return;

    double cx = (bounds[0] + bounds[3]) / 2.0;
    double cy = (bounds[1] + bounds[4]) / 2.0;
    double cz = (bounds[2] + bounds[5]) / 2.0;
    double rx = (bounds[3] - bounds[0]) / 2.0 + 1;
    double ry = (bounds[4] - bounds[1]) / 2.0 + 1;
    double rz = (bounds[5] - bounds[2]) / 2.0 + 1;

    int removed = 0;
    for (Entity e : world.getNearbyEntities(new Location(world, cx, cy, cz), rx, ry, rz)) {
      if (removed >= excess) break;
      if (!(e instanceof LivingEntity) || e instanceof org.bukkit.entity.Player) continue;
      if (e.getType() == type) {
        e.remove();
        removed++;
      }
    }
  }

  private static @Nullable EntityType parseEntityType(@NotNull String name) {
    try {
      return EntityType.valueOf(name.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Returns true if the location is inside a WorldGuard region whose id contains "spawner". */
  public boolean isSpawnerRegion(@NotNull Location location) {
    return WorldGuardManager.getInstance().isWithinSpawnerRegion(location);
  }

  /** Caches the WorldGuard region bounds for the given spawner (must be called on main thread). */
  public void cacheRegionBounds(@NotNull Spawner s) {
    World world = Bukkit.getWorld(s.world());
    if (world == null) return;
    Location loc = new Location(world, s.x(), s.y(), s.z());
    int[] bounds = WorldGuardManager.getInstance().getSpawnerRegionBounds(loc);
    if (bounds != null) regionBoundsCache.put(s.id(), bounds);
  }

  /** Returns the cached region bounds for a spawner, or {@code null} if not found. */
  public @Nullable int[] getRegionBounds(@NotNull String spawnerId) {
    return regionBoundsCache.get(spawnerId);
  }

  /**
   * Returns true if any player is within 128 blocks of the spawner (i.e. the region chunks are
   * loaded).
   */
  private boolean hasNearbyPlayer(@NotNull Spawner s) {
    World world = Bukkit.getWorld(s.world());
    if (world == null) return false;
    Location center = new Location(world, s.x(), s.y(), s.z());
    for (org.bukkit.entity.Player p : world.getPlayers()) {
      if (p.getLocation().distanceSquared(center) <= 128.0 * 128.0) return true;
    }
    return false;
  }

  /**
   * Attempts to spawn a mob for the given spawner. Checks the mob cap, rolls the spawn chance, and
   * finds a safe random location within 2 blocks XZ of center.
   *
   * @param s the spawner definition
   */
  private void doSpawn(@NotNull Spawner s) {
    EntityType type = parseEntityType(s.mobType());
    if (type == null) {
      Bukkit.getLogger()
          .warning("[Evolv3] SpawnerManager: unknown entity type '" + s.mobType() + "'");
      return;
    }

    if (!hasNearbyPlayer(s)) return;
    int current = countNearbyMobs(s);
    if (current >= s.mobLimit()) {
      if (current > s.mobLimit()) cullExcessMobs(s, current - s.mobLimit());
      return;
    }
    if (RANDOM.nextDouble() * 100.0 >= s.spawnChance()) return;

    World world = Bukkit.getWorld(s.world());
    if (world == null) return;

    for (int attempt = 0; attempt < 10; attempt++) {
      int offsetX = RANDOM.nextInt(5) - 2;
      int offsetZ = RANDOM.nextInt(5) - 2;
      Location spawnLoc =
          new Location(world, s.x() + offsetX + 0.5, s.y() + 1, s.z() + offsetZ + 0.5);

      if (!spawnLoc.getBlock().getType().isSolid()) {
        Entity spawned = world.spawnEntity(spawnLoc, type);
        spawned
            .getPersistentDataContainer()
            .set(SPAWNER_MOB_KEY, PersistentDataType.STRING, s.id());
        return;
      }
    }
  }

  /** Returns the default interval. */
  private int getDefaultInterval() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.SPAWNERS);
    return cfg != null ? cfg.getInt("defaults.spawn-interval", 30) : 30;
  }

  /** Returns the default chance. */
  private double getDefaultChance() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.SPAWNERS);
    return cfg != null ? cfg.getDouble("defaults.spawn-chance", 80.0) : 80.0;
  }

  /** Returns the default mob limit. */
  private int getDefaultMobLimit() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.SPAWNERS);
    return cfg != null ? cfg.getInt("defaults.mob-limit", 10) : 10;
  }

  /** Returns the default search radius. */
  private int getDefaultSearchRadius() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.SPAWNERS);
    return cfg != null ? cfg.getInt("defaults.mob-search-radius", 32) : 32;
  }

  private static @NotNull String locationKey(@NotNull String world, int x, int y, int z) {
    return world + ":" + x + ":" + y + ":" + z;
  }

  private @NotNull File getDataFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "spawners-data.yml");
  }
}
