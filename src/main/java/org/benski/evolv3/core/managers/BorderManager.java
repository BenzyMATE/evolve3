package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages zone border walls for the prison rank system.
 *
 * <p>A border wall is a line segment (two XZ points) that blocks players from
 * crossing until they reach the required rank. Multiple walls can divide the map
 * into ranked zones (A zone, B zone, C zone, …).</p>
 *
 * <p>Each wall is keyed by the rank ID that unlocks it (e.g. "B1" unlocks the
 * B-zone wall). Players whose rank comes before that ID in {@code ranks.yml}
 * are blocked; players at or beyond it pass freely.</p>
 *
 * <p>Walls are persisted in {@code borders.yml}.</p>
 */
public final class BorderManager
{
    @Getter
    private static final BorderManager instance = new BorderManager();

    /** wallId → wall data */
    private final Map<String, BorderWall> borders = new HashMap<>();

    /** Per-player wand selection points */
    private final Map<UUID, Location> point1 = new HashMap<>();
    private final Map<UUID, Location> point2 = new HashMap<>();

    /** Throttle: last time the BLOCKED title was sent, millis */
    private final Map<UUID, Long> lastTitleMs = new HashMap<>();

    private BorderManager() {}

    // -------------------------------------------------------------------------
    // Data model
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of one border wall.
     *
     * @param wallId the rank ID required to cross this wall (e.g. "B1", "C1")
     * @param x1     X coordinate of the first endpoint
     * @param z1     Z coordinate of the first endpoint
     * @param x2     X coordinate of the second endpoint
     * @param z2     Z coordinate of the second endpoint
     */
    public record BorderWall(String wallId, double x1, double z1, double x2, double z2)
    {
        /**
         * Returns true if the movement segment (fx,fz)→(tx,tz) crosses this wall.
         * Uses the 2-D line-segment intersection test via signed cross products.
         */
        public boolean crossesWall(double fx, double fz, double tx, double tz)
        {
            double d1 = cross(x2 - x1, z2 - z1, fx - x1, fz - z1);
            double d2 = cross(x2 - x1, z2 - z1, tx - x1, tz - z1);
            double d3 = cross(tx - fx, tz - fz, x1 - fx, z1 - fz);
            double d4 = cross(tx - fx, tz - fz, x2 - fx, z2 - fz);

            return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
        }

        private static double cross(double ax, double az, double bx, double bz)
        {
            return ax * bz - az * bx;
        }
    }

    // -------------------------------------------------------------------------
    // Config persistence
    // -------------------------------------------------------------------------

    /** Loads all border walls from {@code borders.yml} into memory. */
    public void loadFromConfig()
    {
        borders.clear();
        FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BORDERS);
        if (cfg == null) return;

        ConfigurationSection section = cfg.getConfigurationSection("borders");
        if (section == null) return;

        for (String wallId : section.getKeys(false))
        {
            String path = "borders." + wallId;
            double x1 = cfg.getDouble(path + ".x1");
            double z1 = cfg.getDouble(path + ".z1");
            double x2 = cfg.getDouble(path + ".x2");
            double z2 = cfg.getDouble(path + ".z2");
            borders.put(wallId, new BorderWall(wallId, x1, z1, x2, z2));
        }
    }

    /** Persists all in-memory border walls to {@code borders.yml}. */
    public void saveToConfig()
    {
        FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BORDERS);
        if (cfg == null) return;

        cfg.set("borders", null); // clear existing

        for (BorderWall w : borders.values())
        {
            String path = "borders." + w.wallId();
            cfg.set(path + ".x1", w.x1());
            cfg.set(path + ".z1", w.z1());
            cfg.set(path + ".x2", w.x2());
            cfg.set(path + ".z2", w.z2());
        }

        StorageRegistryManager.getInstance().saveConfig(ConfigNames.BORDERS);
    }

    // -------------------------------------------------------------------------
    // Border CRUD
    // -------------------------------------------------------------------------

    /**
     * Creates or replaces the border wall for the given rank ID, then saves to disk.
     */
    public void setBorder(@NotNull String wallId,
                          double x1, double z1, double x2, double z2)
    {
        borders.put(wallId, new BorderWall(wallId, x1, z1, x2, z2));
        saveToConfig();
    }

    /**
     * Removes the border wall for the given rank ID.
     *
     * @return true if a wall existed and was removed
     */
    public boolean removeBorder(@NotNull String wallId)
    {
        if (borders.remove(wallId) == null) return false;
        saveToConfig();
        return true;
    }

    public @Nullable BorderWall getBorder(@NotNull String wallId)
    {
        return borders.get(wallId);
    }

    public @NotNull Map<String, BorderWall> getAllBorders()
    {
        return Collections.unmodifiableMap(borders);
    }

    // -------------------------------------------------------------------------
    // Per-player border API
    // -------------------------------------------------------------------------

    /**
     * No-op kept for API compatibility (WorldBorder is no longer used).
     * Clears any previously set player world border.
     */
    public void updatePlayerBorder(@NotNull Player player, @Nullable String playerRankId)
    {
        player.setWorldBorder(null);
    }

    // -------------------------------------------------------------------------
    // Movement enforcement
    // -------------------------------------------------------------------------

    /**
     * Returns the first border wall that would block the player's movement from
     * (fx, fz) to (tx, tz), or {@code null} if the move is allowed.
     *
     * <p>Only walls the player has not yet unlocked are checked. Walls are
     * checked in rank-progression order; the first blocking wall is returned.</p>
     *
     * @param playerRankId the player's current rank ID (null → default rank)
     * @param fx           from X coordinate
     * @param fz           from Z coordinate
     * @param tx           to X coordinate
     * @param tz           to Z coordinate
     * @return the blocking wall, or null if movement is allowed
     */
    public @Nullable BorderWall getBlockingWall(@Nullable String playerRankId,
                                                 double fx, double fz,
                                                 double tx, double tz)
    {
        if (borders.isEmpty()) return null;

        FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
        if (ranksCfg == null) return null;

        List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
        if (order.isEmpty()) return null;

        String resolvedRank = (playerRankId != null) ? playerRankId
                : ranksCfg.getString(ConfigPaths.Ranks.DEFAULT_RANK, "A1");

        int playerIndex = order.indexOf(resolvedRank);
        if (playerIndex == -1) playerIndex = 0;

        // Check walls in rank order; return the first one the player crosses
        for (int i = 0; i < order.size(); i++)
        {
            if (i <= playerIndex) continue; // player has unlocked walls up to their rank

            BorderWall wall = borders.get(order.get(i));
            if (wall == null) continue;

            if (wall.crossesWall(fx, fz, tx, tz)) return wall;
        }
        return null;
    }

    /**
     * Returns true if the BLOCKED title should be shown to this player now.
     * Throttled to once every 2 seconds to prevent spam.
     */
    public boolean shouldShowTitle(@NotNull UUID uuid)
    {
        long now = System.currentTimeMillis();
        Long last = lastTitleMs.get(uuid);
        if (last != null && now - last < 2_000L) return false;
        lastTitleMs.put(uuid, now);
        return true;
    }

    // -------------------------------------------------------------------------
    // Wand point tracking
    // -------------------------------------------------------------------------

    public void setPoint1(@NotNull UUID uuid, @NotNull Location loc) { point1.put(uuid, loc.clone()); }
    public void setPoint2(@NotNull UUID uuid, @NotNull Location loc) { point2.put(uuid, loc.clone()); }
    public @Nullable Location getPoint1(@NotNull UUID uuid) { return point1.get(uuid); }
    public @Nullable Location getPoint2(@NotNull UUID uuid) { return point2.get(uuid); }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    public void cleanupPlayer(@NotNull UUID uuid)
    {
        point1.remove(uuid);
        point2.remove(uuid);
        lastTitleMs.remove(uuid);
    }

    public void cleanup()
    {
        borders.clear();
        point1.clear();
        point2.clear();
        lastTitleMs.clear();
    }
}
