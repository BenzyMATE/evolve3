package org.benski.evolv3.core.managers.mine;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.helpers.WorldEditSelection;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Central manager for all mine operations: CRUD, block fill/reset, percentage tracking, and cave
 * generation delegation.
 *
 * <p>Each mine is a named cuboid region with:
 *
 * <ul>
 *   <li>Configurable block composition (weighted random materials)
 *   <li>Timed auto-reset and/or percentage-threshold auto-reset
 *   <li>Optional cave generation in the surrounding walls
 *   <li>Per-mine spawn point for post-reset teleportation
 *   <li>Real-time block tracking for percentage placeholders
 * </ul>
 */
public final class MineManager {
  @Getter private static final MineManager instance = new MineManager();

  /** All loaded mines, keyed by lowercase name. */
  private final Map<String, Mine> mines = new LinkedHashMap<>();

  /** Live block-mined counters, keyed by mine name (used for reset-threshold checks). */
  private final Map<String, AtomicInteger> minedCounters = new ConcurrentHashMap<>();

  /** Mines whose block count changed since the last scan — triggers an immediate rescan. */
  private final Set<String> dirtyMines = ConcurrentHashMap.newKeySet();

  /** Cached remaining % from the last block scan, keyed by mine name (lowercase). */
  private final Map<String, Double> cachedRemainingPct = new ConcurrentHashMap<>();

  /** Timestamp (ms) of the last block scan per mine — enforces a minimum re-scan interval. */
  private final Map<String, Long> lastScanTimeMs = new ConcurrentHashMap<>();

  /** Minimum time between full block scans (5 s), even when no dirty flag is set. */
  private static final long SCAN_INTERVAL_MS = 5_000L;

  /** Cave tier (1–4) per mine name — controls which reward table is used. */
  private final Map<String, Integer> caveTiers = new ConcurrentHashMap<>();

  /** Scheduler task IDs for auto-reset timers, keyed by mine name. */
  private final Map<String, Integer> resetTaskIds = new ConcurrentHashMap<>();

  /** Live countdown ticks remaining per mine (written by the reset timer each tick). */
  private final Map<String, AtomicLong> resetCountdowns = new ConcurrentHashMap<>();

  /**
   * Mine names (lowercase) with a reset currently in flight. Guards against overlapping resets — a
   * mine's percentage threshold can be crossed by several blocks broken in the same tick (e.g. an
   * AOE-mining enchant), each independently scheduling a {@link #resetMine(String)} call. Without
   * this guard, the earlier reset's cave-generation loop is never cancelled and keeps pasting caves
   * alongside the new reset's loop, producing more caves than {@code cave-count} configures.
   */
  private final Set<String> resettingMines = ConcurrentHashMap.newKeySet();

  private static final Random RANDOM = new Random();

  /** Creates a new MineManager instance. */
  private MineManager() {}

  /**
   * Immutable snapshot of a mine definition.
   *
   * @param name unique lowercase name
   * @param world Bukkit world name
   * @param minX minimum X (inclusive)
   * @param minY minimum Y (inclusive)
   * @param minZ minimum Z (inclusive)
   * @param maxX maximum X (inclusive)
   * @param maxY maximum Y (inclusive)
   * @param maxZ maximum Z (inclusive)
   * @param spawnX spawn X after reset (center-top if unset)
   * @param spawnY spawn Y after reset
   * @param spawnZ spawn Z after reset
   * @param resetInterval auto-reset interval in minutes (0 = disabled)
   * @param resetPercentage auto-reset when this % is mined (0 = disabled)
   * @param resetWarning seconds before reset to warn players
   * @param teleportOnReset whether to teleport players on reset
   * @param caveCount number of caves to generate per reset
   * @param caveMinDistance minimum distance between cave entrances
   * @param hologram whether to display a floating info hologram
   * @param contents block composition: material name → weight
   */
  public record Mine(
      String name,
      String world,
      int minX,
      int minY,
      int minZ,
      int maxX,
      int maxY,
      int maxZ,
      double spawnX,
      double spawnY,
      double spawnZ,
      int resetInterval,
      int resetPercentage,
      int resetWarning,
      boolean teleportOnReset,
      int caveCount,
      int caveMinDistance,
      boolean hologram,
      Map<String, Integer> contents) {
    /** Total number of blocks in this mine's cuboid. */
    public int totalBlocks() {
      return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Returns true if the block coordinates are inside this mine. */
    public boolean contains(int bx, int by, int bz) {
      return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    /** Returns true if the location is inside this mine (same world + bounds). */
    public boolean contains(@NotNull Location loc) {
      if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
      return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** Spawn location for this mine. */
    public @Nullable Location spawnLocation() {
      World w = Bukkit.getWorld(world);
      return w != null ? new Location(w, spawnX, spawnY, spawnZ) : null;
    }

    /** Centre-top location (fallback when no spawn is set). */
    public @Nullable Location centreTop() {
      World w = Bukkit.getWorld(world);
      if (w == null) return null;
      double cx = (minX + maxX) / 2.0;
      double cz = (minZ + maxZ) / 2.0;
      return new Location(w, cx, maxY + 2, cz);
    }
  }

  /**
   * Loads all mines from {@code localdata/mines-data.yml}. Global defaults (reset-interval,
   * cave-count, etc.) are still read from the {@code mines.yml} config resource so they can be
   * safely deleted/reset without losing mine definitions.
   */
  public void loadFromConfig() {
    cancelAllResetTimers();
    mines.clear();
    minedCounters.clear();
    dirtyMines.clear();
    cachedRemainingPct.clear();
    lastScanTimeMs.clear();
    caveTiers.clear();

    File file = getMinesDataFile();
    if (!file.exists()) return;

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection section = cfg.getConfigurationSection("mines");
    if (section == null) return;

    for (String name : section.getKeys(false)) {
      String path = "mines." + name;
      String world = cfg.getString(path + ".world", "world");
      int minX = cfg.getInt(path + ".minX");
      int minY = cfg.getInt(path + ".minY");
      int minZ = cfg.getInt(path + ".minZ");
      int maxX = cfg.getInt(path + ".maxX");
      int maxY = cfg.getInt(path + ".maxY");
      int maxZ = cfg.getInt(path + ".maxZ");

      double spawnX = cfg.getDouble(path + ".spawnX", (minX + maxX) / 2.0);
      double spawnY = cfg.getDouble(path + ".spawnY", maxY + 2);
      double spawnZ = cfg.getDouble(path + ".spawnZ", (minZ + maxZ) / 2.0);

      int resetInterval = cfg.getInt(path + ".reset-interval", getDefaultResetInterval());
      int resetPercentage = cfg.getInt(path + ".reset-percentage", getDefaultResetPercentage());
      int resetWarning = cfg.getInt(path + ".reset-warning", getDefaultResetWarning());
      boolean teleport = cfg.getBoolean(path + ".teleport-on-reset", getDefaultTeleportOnReset());
      int caveCount = cfg.getInt(path + ".cave-count", getDefaultCaveCount());
      int caveMinDist = cfg.getInt(path + ".cave-min-distance", getDefaultCaveMinDistance());
      boolean hologram = cfg.getBoolean(path + ".hologram", getDefaultHologramEnabled());

      Map<String, Integer> contents = new LinkedHashMap<>();
      ConfigurationSection contentsSection = cfg.getConfigurationSection(path + ".contents");
      if (contentsSection != null) {
        for (String mat : contentsSection.getKeys(false)) {
          contents.put(mat.toUpperCase(), contentsSection.getInt(mat));
        }
      }

      int caveTier = cfg.getInt(path + ".cave-tier", 1);
      caveTiers.put(name.toLowerCase(), Math.max(1, Math.min(4, caveTier)));

      Mine mine =
          new Mine(
              name,
              world,
              minX,
              minY,
              minZ,
              maxX,
              maxY,
              maxZ,
              spawnX,
              spawnY,
              spawnZ,
              resetInterval,
              resetPercentage,
              resetWarning,
              teleport,
              caveCount,
              caveMinDist,
              hologram,
              contents);
      mines.put(name.toLowerCase(), mine);
      minedCounters.put(name.toLowerCase(), new AtomicInteger(0));
      cachedRemainingPct.put(name.toLowerCase(), 100.0);
    }

    for (Mine mine : mines.values()) {
      scheduleAutoReset(mine);
    }

    MineHologramManager.getInstance().cleanup();
    MineHologramManager.getInstance().initialize();
  }

  /** Saves all mine definitions to {@code localdata/mines-data.yml}. */
  public void saveToConfig() {
    YamlConfiguration cfg = new YamlConfiguration();

    for (Mine m : mines.values()) {
      String path = "mines." + m.name();
      cfg.set(path + ".world", m.world());
      cfg.set(path + ".minX", m.minX());
      cfg.set(path + ".minY", m.minY());
      cfg.set(path + ".minZ", m.minZ());
      cfg.set(path + ".maxX", m.maxX());
      cfg.set(path + ".maxY", m.maxY());
      cfg.set(path + ".maxZ", m.maxZ());
      cfg.set(path + ".spawnX", m.spawnX());
      cfg.set(path + ".spawnY", m.spawnY());
      cfg.set(path + ".spawnZ", m.spawnZ());
      cfg.set(path + ".reset-interval", m.resetInterval());
      cfg.set(path + ".reset-percentage", m.resetPercentage());
      cfg.set(path + ".reset-warning", m.resetWarning());
      cfg.set(path + ".teleport-on-reset", m.teleportOnReset());
      cfg.set(path + ".cave-count", m.caveCount());
      cfg.set(path + ".cave-min-distance", m.caveMinDistance());
      cfg.set(path + ".hologram", m.hologram());

      cfg.set(path + ".cave-tier", caveTiers.getOrDefault(m.name().toLowerCase(), 1));

      for (Map.Entry<String, Integer> entry : m.contents().entrySet()) {
        cfg.set(path + ".contents." + entry.getKey(), entry.getValue());
      }
    }

    try {
      cfg.save(getMinesDataFile());
    } catch (IOException e) {
      Bukkit.getLogger().severe("[Evolv3] Failed to save mines data: " + e.getMessage());
    }
  }

  /**
   * Creates a mine from a WorldEdit selection.
   *
   * @return the created mine, or null if a mine with that name already exists
   */
  public @Nullable Mine createMine(@NotNull String name, @NotNull Player player) {
    String key = name.toLowerCase();
    if (mines.containsKey(key)) return null;

    if (!WorldEditSelection.isAvailable()) return null;
    int[][] sel = WorldEditSelection.getSelection(player);
    if (sel == null) return null;

    String world = player.getWorld().getName();
    int minX = sel[0][0], minY = sel[0][1], minZ = sel[0][2];
    int maxX = sel[1][0], maxY = sel[1][1], maxZ = sel[1][2];
    double spawnX = (minX + maxX) / 2.0;
    double spawnY = maxY + 2;
    double spawnZ = (minZ + maxZ) / 2.0;

    Mine mine =
        new Mine(
            key,
            world,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            spawnX,
            spawnY,
            spawnZ,
            getDefaultResetInterval(),
            getDefaultResetPercentage(),
            getDefaultResetWarning(),
            getDefaultTeleportOnReset(),
            getDefaultCaveCount(),
            getDefaultCaveMinDistance(),
            getDefaultHologramEnabled(),
            new LinkedHashMap<>());

    mines.put(key, mine);
    minedCounters.put(key, new AtomicInteger(0));
    cachedRemainingPct.put(key, 100.0);
    saveToConfig();
    scheduleAutoReset(mine);
    MineHologramManager.getInstance().spawnHologram(mine);
    return mine;
  }

  /** Removes a mine by name. Returns true if it existed. */
  public boolean removeMine(@NotNull String name) {
    String key = name.toLowerCase();
    cancelResetTimer(key);
    minedCounters.remove(key);
    cachedRemainingPct.remove(key);
    dirtyMines.remove(key);
    lastScanTimeMs.remove(key);

    if (mines.remove(key) == null) return false;
    CaveManager.getInstance().clearCaves(key);
    MineHologramManager.getInstance().removeHologram(key);
    saveToConfig();
    return true;
  }

  /** Returns a mine by name, or null. */
  public @Nullable Mine getMine(@NotNull String name) {
    return mines.get(name.toLowerCase());
  }

  /** Returns the mine containing the given location, or null. */
  public @Nullable Mine getMineAt(@NotNull Location loc) {
    for (Mine mine : mines.values()) {
      if (mine.contains(loc)) return mine;
    }
    return null;
  }

  /** Returns all mines. */
  public @NotNull Map<String, Mine> getAllMines() {
    return Collections.unmodifiableMap(mines);
  }

  /** Replaces a mine with updated settings. The mine is looked up by name. */
  public void updateMine(@NotNull Mine updated) {
    String key = updated.name().toLowerCase();
    cancelResetTimer(key);
    mines.put(key, updated);
    saveToConfig();
    scheduleAutoReset(updated);
  }

  /** Adds or updates a material in the mine's block composition. */
  public void setContent(@NotNull String mineName, @NotNull Material material, int weight) {
    Mine mine = getMine(mineName);
    if (mine == null) return;

    Map<String, Integer> newContents = new LinkedHashMap<>(mine.contents());
    newContents.put(material.name(), weight);

    updateMine(
        new Mine(
            mine.name(),
            mine.world(),
            mine.minX(),
            mine.minY(),
            mine.minZ(),
            mine.maxX(),
            mine.maxY(),
            mine.maxZ(),
            mine.spawnX(),
            mine.spawnY(),
            mine.spawnZ(),
            mine.resetInterval(),
            mine.resetPercentage(),
            mine.resetWarning(),
            mine.teleportOnReset(),
            mine.caveCount(),
            mine.caveMinDistance(),
            mine.hologram(),
            newContents));
  }

  /** Removes a material from the mine's block composition. */
  public boolean removeContent(@NotNull String mineName, @NotNull Material material) {
    Mine mine = getMine(mineName);
    if (mine == null) return false;

    Map<String, Integer> newContents = new LinkedHashMap<>(mine.contents());
    if (newContents.remove(material.name()) == null) return false;

    updateMine(
        new Mine(
            mine.name(),
            mine.world(),
            mine.minX(),
            mine.minY(),
            mine.minZ(),
            mine.maxX(),
            mine.maxY(),
            mine.maxZ(),
            mine.spawnX(),
            mine.spawnY(),
            mine.spawnZ(),
            mine.resetInterval(),
            mine.resetPercentage(),
            mine.resetWarning(),
            mine.teleportOnReset(),
            mine.caveCount(),
            mine.caveMinDistance(),
            mine.hologram(),
            newContents));
    return true;
  }

  /** Sets the spawn point for a mine. */
  public void setSpawn(@NotNull String mineName, @NotNull Location loc) {
    Mine mine = getMine(mineName);
    if (mine == null) return;

    updateMine(
        new Mine(
            mine.name(),
            mine.world(),
            mine.minX(),
            mine.minY(),
            mine.minZ(),
            mine.maxX(),
            mine.maxY(),
            mine.maxZ(),
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            mine.resetInterval(),
            mine.resetPercentage(),
            mine.resetWarning(),
            mine.teleportOnReset(),
            mine.caveCount(),
            mine.caveMinDistance(),
            mine.hologram(),
            mine.contents()));
  }

  /** Sets the reset interval (minutes) for a mine. */
  public void setResetInterval(@NotNull String mineName, int minutes) {
    Mine mine = getMine(mineName);
    if (mine == null) return;

    updateMine(
        new Mine(
            mine.name(),
            mine.world(),
            mine.minX(),
            mine.minY(),
            mine.minZ(),
            mine.maxX(),
            mine.maxY(),
            mine.maxZ(),
            mine.spawnX(),
            mine.spawnY(),
            mine.spawnZ(),
            minutes,
            mine.resetPercentage(),
            mine.resetWarning(),
            mine.teleportOnReset(),
            mine.caveCount(),
            mine.caveMinDistance(),
            mine.hologram(),
            mine.contents()));
  }

  /** Sets the reset percentage threshold for a mine. */
  public void setResetPercentage(@NotNull String mineName, int percentage) {
    Mine mine = getMine(mineName);
    if (mine == null) return;

    updateMine(
        new Mine(
            mine.name(),
            mine.world(),
            mine.minX(),
            mine.minY(),
            mine.minZ(),
            mine.maxX(),
            mine.maxY(),
            mine.maxZ(),
            mine.spawnX(),
            mine.spawnY(),
            mine.spawnZ(),
            mine.resetInterval(),
            percentage,
            mine.resetWarning(),
            mine.teleportOnReset(),
            mine.caveCount(),
            mine.caveMinDistance(),
            mine.hologram(),
            mine.contents()));
  }

  /**
   * Resets a mine: fills the cuboid with the weighted random block composition, teleports players,
   * and generates caves.
   */
  public void resetMine(@NotNull String name) {
    Mine mine = getMine(name);
    if (mine == null) return;

    String resetKey = mine.name().toLowerCase();
    if (!resettingMines.add(resetKey)) return;

    World world = Bukkit.getWorld(mine.world());
    if (world == null) {
      resettingMines.remove(resetKey);
      return;
    }

    NavigableMap<Integer, Material> weightedMap = buildWeightedMap(mine.contents());
    boolean hasContents = !weightedMap.isEmpty();

    String resetMsg = getMessage("reset-broadcast").replace("%mine%", mine.name());
    var resetComponent =
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
            .deserialize(
                net.matrixcreations.libraries.MatrixColorAPI.process(
                    FontUtils.toSmallCaps(resetMsg)));
    for (Player p : Bukkit.getOnlinePlayers()) {
      p.sendMessage(resetComponent);
    }

    if (mine.teleportOnReset()) {
      Location spawn = mine.spawnLocation();
      if (spawn == null) spawn = mine.centreTop();
      if (spawn != null) {
        Location finalSpawn = spawn;
        for (Player p : world.getPlayers()) {
          if (mine.contains(p.getLocation())) {
            p.teleport(finalSpawn);
          }
        }
        for (Map.Entry<String, CaveManager.CaveBounds> caveEntry :
            CaveManager.getInstance().getAllCaveRegions().entrySet()) {
          if (!mine.name()
              .equalsIgnoreCase(CaveManager.getInstance().getMineName(caveEntry.getKey())))
            continue;
          for (Player p : world.getPlayers()) {
            if (caveEntry.getValue().contains(p.getLocation())) {
              p.teleport(finalSpawn);
            }
          }
        }
      }
    }

    CaveManager.getInstance().undoCaves(mine.name());

    Bukkit.getScheduler()
        .runTask(
            Main.getInstance(),
            () -> {
              try {
                if (hasContents) {
                  int totalWeight = weightedMap.lastKey();
                  com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
                  BlockVector3 min = BlockVector3.at(mine.minX(), mine.minY(), mine.minZ());
                  BlockVector3 max = BlockVector3.at(mine.maxX(), mine.maxY(), mine.maxZ());
                  CuboidRegion region = new CuboidRegion(weWorld, min, max);

                  try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                    for (BlockVector3 pos : region) {
                      int roll = RANDOM.nextInt(totalWeight);
                      Material mat = weightedMap.higherEntry(roll).getValue();
                      editSession.setBlock(pos, (Pattern) BukkitAdapter.asBlockType(mat));
                    }
                  }
                }

                AtomicInteger counter = minedCounters.get(resetKey);
                if (counter != null) counter.set(0);
                cachedRemainingPct.put(resetKey, 100.0);
                dirtyMines.remove(resetKey);
                lastScanTimeMs.remove(resetKey);

                if (mine.caveCount() > 0) {
                  CaveManager.getInstance().generateCaves(mine);
                }
              } catch (Exception e) {
                Bukkit.getLogger()
                    .severe(
                        "[Evolv3] Failed to reset mine '" + mine.name() + "': " + e.getMessage());
                e.printStackTrace();
              } finally {
                resettingMines.remove(resetKey);
              }
            });
  }

  /**
   * Called by the break listener when a block inside a mine is broken. Increments the counter and
   * checks if the percentage threshold is met.
   */
  public void onBlockMined(@NotNull String mineName) {
    String key = mineName.toLowerCase();
    dirtyMines.add(key);

    AtomicInteger counter = minedCounters.get(key);
    if (counter == null) return;

    int mined = counter.incrementAndGet();
    Mine mine = getMine(key);
    if (mine == null || mine.resetPercentage() <= 0) return;

    int total = mine.totalBlocks();
    if (total <= 0) return;

    double pct = (double) mined / total * 100.0;
    if (pct >= mine.resetPercentage()) {
      Bukkit.getScheduler().runTask(Main.getInstance(), () -> resetMine(key));
    }
  }

  /** Returns the number of blocks mined since the last reset for a mine. */
  public int getMinedCount(@NotNull String mineName) {
    AtomicInteger counter = minedCounters.get(mineName.toLowerCase());
    return counter != null ? counter.get() : 0;
  }

  /**
   * Convenience helper: finds the mine at the given location and increments its mined counter.
   * Called by enchants that bypass {@link BlockBreakEvent} by calling {@code block.setType(AIR)}
   * directly.
   */
  public void trackBlockAtLocation(@NotNull Location loc) {
    Mine mine = getMineAt(loc);
    if (mine != null) onBlockMined(mine.name());
  }

  /**
   * Returns the remaining percentage of solid blocks in a mine (0.0–100.0).
   *
   * <p>Scans actual block types inside the mine region for accuracy — the result is never stale due
   * to missed events or counter drift. Results are cached and re-used until a block is broken
   * (dirty flag set by {@link #onBlockMined}) or {@value #SCAN_INTERVAL_MS} ms have elapsed, so the
   * main-thread scan cost is at most ~once per second per mine.
   */
  public double getRemainingPercentage(@NotNull String mineName) {
    String key = mineName.toLowerCase();
    Mine mine = getMine(key);
    if (mine == null) return 0.0;

    long now = System.currentTimeMillis();
    boolean dirty = dirtyMines.remove(key);
    boolean stale = (now - lastScanTimeMs.getOrDefault(key, 0L)) >= SCAN_INTERVAL_MS;

    if ((dirty || stale) && Bukkit.isPrimaryThread()) {
      double pct = scanRemainingPercentage(mine);
      cachedRemainingPct.put(key, pct);
      lastScanTimeMs.put(key, now);
      return pct;
    }

    return cachedRemainingPct.getOrDefault(key, 100.0);
  }

  /** Counts solid (non-air) blocks inside the mine region on the calling (main) thread. */
  private double scanRemainingPercentage(@NotNull Mine mine) {
    World world = Bukkit.getWorld(mine.world());
    if (world == null) return cachedRemainingPct.getOrDefault(mine.name().toLowerCase(), 100.0);

    int total = mine.totalBlocks();
    if (total <= 0) return 0.0;

    int solidCount = 0;
    for (int x = mine.minX(); x <= mine.maxX(); x++) {
      for (int y = mine.minY(); y <= mine.maxY(); y++) {
        for (int z = mine.minZ(); z <= mine.maxZ(); z++) {
          if (!world.getBlockAt(x, y, z).getType().isAir()) solidCount++;
        }
      }
    }

    return (double) solidCount / total * 100.0;
  }

  /**
   * Returns the number of seconds until the next auto-reset for a mine. Returns 0 if the mine has
   * no timed reset or the timer is not active.
   */
  public long getSecondsUntilReset(@NotNull String mineName) {
    AtomicLong ticks = resetCountdowns.get(mineName.toLowerCase());
    return ticks != null ? Math.max(0, ticks.get() / 20L) : 0;
  }

  /** Counts players currently inside a mine. */
  public int getPlayerCount(@NotNull String mineName) {
    Mine mine = getMine(mineName);
    if (mine == null) return 0;
    World world = Bukkit.getWorld(mine.world());
    if (world == null) return 0;

    int count = 0;
    for (Player p : world.getPlayers()) {
      if (mine.contains(p.getLocation())) count++;
    }
    return count;
  }

  /** Schedules auto reset. */
  private void scheduleAutoReset(@NotNull Mine mine) {
    if (mine.resetInterval() <= 0) return;

    long intervalTicks = mine.resetInterval() * 60L * 20L;
    long warningTicks = mine.resetWarning() * 20L;

    resetCountdowns
        .computeIfAbsent(mine.name().toLowerCase(), k -> new AtomicLong())
        .set(intervalTicks);

    int taskId =
        Bukkit.getScheduler()
            .runTaskTimer(
                Main.getInstance(),
                new Runnable() {
                  private long ticksRemaining = intervalTicks;

                  /** Executes this scheduled task. */
                  @Override
                  public void run() {
                    ticksRemaining -= 20L;

                    resetCountdowns
                        .computeIfAbsent(mine.name().toLowerCase(), k -> new AtomicLong())
                        .set(ticksRemaining);

                    if (ticksRemaining > 0
                        && ticksRemaining <= warningTicks
                        && ticksRemaining % 20L == 0) {
                      long seconds = ticksRemaining / 20L;
                      String warnMsg =
                          getMessage("reset-warning")
                              .replace("%mine%", mine.name())
                              .replace("%seconds%", String.valueOf(seconds));

                      World world = Bukkit.getWorld(mine.world());
                      if (world != null) {
                        var component =
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacySection()
                                .deserialize(
                                    net.matrixcreations.libraries.MatrixColorAPI.process(
                                        FontUtils.toSmallCaps(warnMsg)));
                        for (Player p : world.getPlayers()) {
                          if (mine.contains(p.getLocation())) p.sendMessage(component);
                        }
                      }
                    }

                    if (ticksRemaining <= 0) {
                      resetMine(mine.name());
                      ticksRemaining = intervalTicks;
                    }
                  }
                },
                20L,
                20L)
            .getTaskId();

    resetTaskIds.put(mine.name().toLowerCase(), taskId);
  }

  /** Returns {@code true} if cel reset timer is permitted. */
  private void cancelResetTimer(@NotNull String mineName) {
    Integer taskId = resetTaskIds.remove(mineName.toLowerCase());
    if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    resetCountdowns.remove(mineName.toLowerCase());
  }

  /** Returns {@code true} if cel all reset timers is permitted. */
  private void cancelAllResetTimers() {
    for (Integer taskId : resetTaskIds.values()) {
      Bukkit.getScheduler().cancelTask(taskId);
    }
    resetTaskIds.clear();
  }

  /**
   * Builds a cumulative weight → Material TreeMap for efficient weighted random selection. Using a
   * NavigableMap with {@code higherEntry(roll)} gives O(log n) lookups.
   */
  private static NavigableMap<Integer, Material> buildWeightedMap(Map<String, Integer> contents) {
    TreeMap<Integer, Material> map = new TreeMap<>();
    int cumulative = 0;
    for (Map.Entry<String, Integer> entry : contents.entrySet()) {
      Material mat = Material.matchMaterial(entry.getKey());
      if (mat == null) continue;
      cumulative += entry.getValue();
      map.put(cumulative, mat);
    }
    return map;
  }

  /** Returns the cave reward tier (1–4) for a mine; defaults to 1. */
  public int getCaveTier(@NotNull String mineName) {
    return caveTiers.getOrDefault(mineName.toLowerCase(), 1);
  }

  /** Returns a message string from the messages config, with prefix resolved. */
  public @NotNull String getMessage(@NotNull String key) {
    return Messageable.getMessage("mine." + key);
  }

  /** Returns the default reset interval. */
  private int getDefaultResetInterval() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg != null ? cfg.getInt("defaults.reset-interval", 5) : 5;
  }

  /** Returns the default reset percentage. */
  private int getDefaultResetPercentage() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg != null ? cfg.getInt("defaults.reset-percentage", 90) : 90;
  }

  /** Returns the default reset warning. */
  private int getDefaultResetWarning() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg != null ? cfg.getInt("defaults.reset-warning", 10) : 10;
  }

  /** Returns the default teleport on reset. */
  private boolean getDefaultTeleportOnReset() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg == null || cfg.getBoolean("defaults.teleport-on-reset", true);
  }

  /** Returns the default cave count. */
  private int getDefaultCaveCount() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg != null ? cfg.getInt("defaults.cave-count", 4) : 4;
  }

  /** Returns the default cave min distance. */
  private int getDefaultCaveMinDistance() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg != null ? cfg.getInt("defaults.cave-min-distance", 20) : 20;
  }

  /** Returns the default hologram enabled. */
  private boolean getDefaultHologramEnabled() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg == null || cfg.getBoolean("defaults.hologram-enabled", true);
  }

  /**
   * Returns the localdata file used to persist mine definitions. This is separate from the {@code
   * mines.yml} config resource so that deleting the config to reset defaults does not destroy mine
   * data.
   */
  private @NotNull File getMinesDataFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "mines-data.yml");
  }

  /** Loads mine definitions from config. Delegates to {@link #loadFromConfig()}. */
  public void initialize() {
    loadFromConfig();
  }

  /** Cancels all reset timers and clears all in-memory mine state. Called on plugin disable. */
  public void cleanup() {
    cancelAllResetTimers();
    mines.clear();
    minedCounters.clear();
    dirtyMines.clear();
    cachedRemainingPct.clear();
    lastScanTimeMs.clear();
    resetCountdowns.clear();
  }
}
