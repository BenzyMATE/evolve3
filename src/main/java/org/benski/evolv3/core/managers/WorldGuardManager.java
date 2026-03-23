package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manager for WorldGuard region checks.
 * Used to verify if players are within designated mine regions.
 * Handles WorldGuard as an optional soft dependency.
 */
public class WorldGuardManager
{
    @Getter
    private static final WorldGuardManager instance = new WorldGuardManager();

    private boolean worldGuardEnabled = false;
    private WorldGuardHandler handler = null;

    private WorldGuardManager() {}

    /**
     * Initializes the WorldGuard manager.
     * Checks if WorldGuard is available on the server.
     */
    public void initialize()
    {
        worldGuardEnabled = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (worldGuardEnabled) {
            try {
                handler = new WorldGuardHandler();
                Bukkit.getLogger().info("[Evolv3] WorldGuard found - region protection enabled");
            } catch (NoClassDefFoundError | Exception e) {
                worldGuardEnabled = false;
                handler = null;
                Bukkit.getLogger().warning("[Evolv3] WorldGuard found but failed to hook: " + e.getMessage());
            }
        } else {
            Bukkit.getLogger().info("[Evolv3] WorldGuard not found - region protection disabled");
        }
    }

    /**
     * Checks if a location is within a mine region.
     * A mine region is any region whose ID contains the configured mine region keyword.
     *
     * @param location the location to check
     * @return true if within a mine region, false otherwise
     */
    public boolean isWithinMineRegion(Location location)
    {
        if (!worldGuardEnabled || handler == null) {
            return true;
        }

        if (location == null || location.getWorld() == null) {
            return false;
        }

        try {
            FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            String mineRegionKeyword = config.getString(ConfigPaths.Default.MINE_REGION, "mine");

            if (mineRegionKeyword == null || mineRegionKeyword.isEmpty()) {
                return true;
            }

            return handler.isInRegion(location, mineRegionKeyword);
        } catch (NoClassDefFoundError | Exception e) {
            Bukkit.getLogger().warning("[Evolv3] Error checking WorldGuard region: " + e.getMessage());
            return true;
        }
    }

    /**
     * Checks if WorldGuard is enabled.
     *
     * @return true if WorldGuard is available
     */
    public boolean isEnabled()
    {
        return worldGuardEnabled;
    }

    /**
     * Returns the XZ bounds of the mine region containing the given location,
     * or {@code null} if WorldGuard is disabled or the location is not inside a mine region.
     *
     * @param location the location to query
     * @return int array {@code [minX, minZ, maxX, maxZ]}, or {@code null}
     */
    public int[] getMineRegionXZBounds(Location location)
    {
        if (!worldGuardEnabled || handler == null) return null;
        if (location == null || location.getWorld() == null) return null;

        try {
            FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            String mineRegionKeyword = config.getString(ConfigPaths.Default.MINE_REGION, "mine");
            if (mineRegionKeyword == null || mineRegionKeyword.isEmpty()) return null;
            return handler.getRegionXZBounds(location, mineRegionKeyword);
        } catch (NoClassDefFoundError | Exception e) {
            Bukkit.getLogger().warning("[Evolv3] Error fetching WorldGuard region bounds: " + e.getMessage());
            return null;
        }
    }

    /**
     * Inner handler class that contains all WorldGuard-specific code.
     * This class is only loaded when WorldGuard is actually available,
     * preventing ClassNotFoundException when WorldGuard is not installed.
     */
    private static class WorldGuardHandler
    {
        /**
         * Checks if a location is within a region containing the keyword.
         *
         * @param location the location to check
         * @param keyword the keyword to search for in region IDs
         * @return true if in a matching region
         */
        public boolean isInRegion(Location location, String keyword)
        {
            com.sk89q.worldguard.protection.regions.RegionContainer container =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();

            com.sk89q.worldguard.protection.managers.RegionManager regionManager =
                    container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location.getWorld()));

            if (regionManager == null) {
                return false;
            }

            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region :
                    regionManager.getApplicableRegions(com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location))) {
                if (region.getId().toLowerCase().contains(keyword.toLowerCase())) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Returns {@code [minX, minZ, maxX, maxZ]} for the first mine region
         * containing the given location, or {@code null} if none found.
         */
        public int[] getRegionXZBounds(Location location, String keyword)
        {
            com.sk89q.worldguard.protection.regions.RegionContainer container =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();

            com.sk89q.worldguard.protection.managers.RegionManager regionManager =
                    container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location.getWorld()));

            if (regionManager == null) return null;

            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region :
                    regionManager.getApplicableRegions(com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location))) {
                if (!region.getId().toLowerCase().contains(keyword.toLowerCase())) continue;

                com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
                com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
                return new int[]{ min.x(), min.z(), max.x(), max.z() };
            }

            return null;
        }
    }
}
