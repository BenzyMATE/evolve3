package org.benski.evolv3.core.managers.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages zone border walls for the prison rank system.
 *
 * <p>A border wall is a line segment (two XZ points) that blocks players from crossing until they
 * reach the required rank. Multiple walls can divide the map into ranked zones (A zone, B zone, C
 * zone, …).
 *
 * <p>Each wall is keyed by the rank ID that unlocks it (e.g. "B1" unlocks the B-zone wall). Players
 * whose rank comes before that ID in {@code ranks.yml} are blocked; players at or beyond it pass
 * freely.
 *
 * <p>Up to {@code max-borders-per-rank} walls (configured in {@code borders.yml}) can be defined
 * per rank to handle zones with multiple entrances.
 *
 * <p>Walls are persisted in {@code borders.yml}.
 */
public final class BorderManager {
  @Getter private static final BorderManager instance = new BorderManager();

  /** rankId → ordered list of walls (slot 1 = index 0, slot 2 = index 1, …) */
  private final Map<String, List<BorderWall>> borders = new HashMap<>();

  /** Throttle: last time the BLOCKED title was sent, millis */
  private final Map<UUID, Long> lastTitleMs = new HashMap<>();

  /** Creates a new BorderManager instance. */
  private BorderManager() {}

  /**
   * Immutable snapshot of one border wall.
   *
   * @param rankId the rank ID required to cross this wall (e.g. "B1", "C1")
   * @param slot 1-based slot number within this rank (1 … max-borders-per-rank)
   * @param x1 X coordinate of the first endpoint
   * @param z1 Z coordinate of the first endpoint
   * @param x2 X coordinate of the second endpoint
   * @param z2 Z coordinate of the second endpoint
   * @param minY bottom Y coordinate of the wall
   * @param maxY top Y coordinate of the wall
   */
  public record BorderWall(
      String rankId,
      int slot,
      double x1,
      double z1,
      double x2,
      double z2,
      double minY,
      double maxY) {
    /**
     * Returns true if the movement segment (fx,fz)→(tx,tz) crosses this wall. Uses the 2-D
     * line-segment intersection test via signed cross products.
     */
    public boolean crossesWall(double fx, double fz, double tx, double tz) {
      double d1 = cross(x2 - x1, z2 - z1, fx - x1, fz - z1);
      double d2 = cross(x2 - x1, z2 - z1, tx - x1, tz - z1);
      double d3 = cross(tx - fx, tz - fz, x1 - fx, z1 - fz);
      double d4 = cross(tx - fx, tz - fz, x2 - fx, z2 - fz);

      return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
          && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    /** Cross. */
    private static double cross(double ax, double az, double bx, double bz) {
      return ax * bz - az * bx;
    }
  }

  /** Loads border walls from config. Delegates to {@link #loadFromConfig()}. */
  public void initialize() {
    loadFromConfig();
  }

  /** Loads all border walls from {@code borders.yml} into memory. */
  public void loadFromConfig() {
    borders.clear();
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BORDERS);
    if (cfg == null) return;

    ConfigurationSection ranksSection = cfg.getConfigurationSection("borders");
    if (ranksSection == null) return;

    for (String rankId : ranksSection.getKeys(false)) {
      ConfigurationSection rankSection = ranksSection.getConfigurationSection(rankId);
      if (rankSection == null) continue;

      List<BorderWall> walls = new ArrayList<>();
      for (String slotKey : rankSection.getKeys(false)) {
        int slot;
        try {
          slot = Integer.parseInt(slotKey);
        } catch (NumberFormatException e) {
          continue;
        }

        String path = "borders." + rankId + "." + slotKey;
        double x1 = cfg.getDouble(path + ".x1");
        double z1 = cfg.getDouble(path + ".z1");
        double x2 = cfg.getDouble(path + ".x2");
        double z2 = cfg.getDouble(path + ".z2");
        double minY = cfg.getDouble(path + ".minY", 0);
        double maxY = cfg.getDouble(path + ".maxY", 256);
        walls.add(new BorderWall(rankId, slot, x1, z1, x2, z2, minY, maxY));
      }

      walls.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
      if (!walls.isEmpty()) borders.put(rankId, walls);
    }
  }

  /** Persists all in-memory border walls to {@code borders.yml}. */
  public void saveToConfig() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BORDERS);
    if (cfg == null) return;

    cfg.set("borders", null);

    for (Map.Entry<String, List<BorderWall>> entry : borders.entrySet()) {
      String rankId = entry.getKey();
      for (BorderWall w : entry.getValue()) {
        String path = "borders." + rankId + "." + w.slot();
        cfg.set(path + ".x1", w.x1());
        cfg.set(path + ".z1", w.z1());
        cfg.set(path + ".x2", w.x2());
        cfg.set(path + ".z2", w.z2());
        cfg.set(path + ".minY", w.minY());
        cfg.set(path + ".maxY", w.maxY());
      }
    }

    ConfigRegistry.getInstance().save(ConfigNames.BORDERS);
  }

  /**
   * Returns the maximum number of border walls allowed per rank, as configured by {@code
   * max-borders-per-rank} in {@code borders.yml}.
   */
  public int getMaxBordersPerRank() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BORDERS);
    return (cfg != null) ? cfg.getInt("max-borders-per-rank", 2) : 2;
  }

  /**
   * Adds a new border wall for the given rank into the next available slot.
   *
   * @return the 1-based slot number used, or {@code -1} if the rank is already at its limit
   */
  public int addBorder(
      @NotNull String rankId,
      double x1,
      double z1,
      double x2,
      double z2,
      double minY,
      double maxY) {
    int max = getMaxBordersPerRank();
    List<BorderWall> walls = borders.computeIfAbsent(rankId, k -> new ArrayList<>());

    if (walls.size() >= max) return -1;

    int slot = 1;
    for (BorderWall w : walls) {
      if (w.slot() == slot) slot++;
      else break;
    }

    walls.add(new BorderWall(rankId, slot, x1, z1, x2, z2, minY, maxY));
    walls.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
    saveToConfig();
    return slot;
  }

  /**
   * Removes all border walls for the given rank.
   *
   * @return true if any walls existed and were removed
   */
  public boolean removeBorder(@NotNull String rankId) {
    if (borders.remove(rankId) == null) return false;
    saveToConfig();
    return true;
  }

  /**
   * Removes the border wall at the specified 1-based slot for the given rank.
   *
   * @return true if the wall existed and was removed
   */
  public boolean removeBorder(@NotNull String rankId, int slot) {
    List<BorderWall> walls = borders.get(rankId);
    if (walls == null) return false;

    boolean removed = walls.removeIf(w -> w.slot() == slot);
    if (!removed) return false;

    if (walls.isEmpty()) borders.remove(rankId);
    saveToConfig();
    return true;
  }

  /** Returns all walls for the given rank, or an empty list if none. */
  public @NotNull List<BorderWall> getBorders(@NotNull String rankId) {
    return Collections.unmodifiableList(borders.getOrDefault(rankId, List.of()));
  }

  /** Returns all configured borders, keyed by rank ID. */
  public @NotNull Map<String, List<BorderWall>> getAllBorders() {
    return Collections.unmodifiableMap(borders);
  }

  /**
   * No-op kept for API compatibility (WorldBorder is no longer used). Clears any previously set
   * player world border.
   */
  public void updatePlayerBorder(@NotNull Player player, @Nullable String playerRankId) {
    player.setWorldBorder(null);
  }

  /**
   * Returns the first border wall that would block the player's movement from (fx, fz) to (tx, tz),
   * or {@code null} if the move is allowed.
   *
   * <p>Only walls the player has not yet unlocked are checked. Walls are checked in
   * rank-progression order; the first blocking wall is returned.
   *
   * @param playerRankId the player's current rank ID (null → default rank)
   * @param fx from X coordinate
   * @param fz from Z coordinate
   * @param tx to X coordinate
   * @param tz to Z coordinate
   * @return the blocking wall, or null if movement is allowed
   */
  public @Nullable BorderWall getBlockingWall(
      @Nullable String playerRankId, double fx, double fz, double tx, double tz) {
    if (borders.isEmpty()) return null;

    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    if (ranksCfg == null) return null;

    List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
    if (order.isEmpty()) return null;

    String resolvedRank =
        (playerRankId != null)
            ? playerRankId
            : ranksCfg.getString(ConfigPaths.Ranks.DEFAULT_RANK, "A1");

    int playerIndex = order.indexOf(resolvedRank);
    if (playerIndex == -1) playerIndex = 0;

    for (int i = 0; i < order.size(); i++) {
      if (i <= playerIndex) continue;

      List<BorderWall> walls = borders.get(order.get(i));
      if (walls == null) continue;

      for (BorderWall wall : walls) {
        if (wall.crossesWall(fx, fz, tx, tz)) return wall;
      }
    }
    return null;
  }

  /**
   * Returns true if the BLOCKED title should be shown to this player now. Throttled to once every 2
   * seconds to prevent spam.
   */
  public boolean shouldShowTitle(@NotNull UUID uuid) {
    long now = System.currentTimeMillis();
    Long last = lastTitleMs.get(uuid);
    if (last != null && now - last < 2_000L) return false;
    lastTitleMs.put(uuid, now);
    return true;
  }

  /** Removes the title throttle state for the given player UUID. */
  public void cleanupPlayer(@NotNull UUID uuid) {
    lastTitleMs.remove(uuid);
  }

  /** Clears all in-memory border and throttle data. */
  public void cleanup() {
    borders.clear();
    lastTitleMs.clear();
  }
}
