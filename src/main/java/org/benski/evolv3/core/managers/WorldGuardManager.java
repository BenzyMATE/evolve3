package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manager for WorldGuard region checks. Used to verify if players are within designated mine
 * regions. Handles WorldGuard as an optional soft dependency.
 */
public class WorldGuardManager {
  @Getter private static final WorldGuardManager instance = new WorldGuardManager();

  private boolean worldGuardEnabled = false;
  private WorldGuardHandler handler = null;

  private WorldGuardManager() {}

  /** Initializes the WorldGuard manager. Checks if WorldGuard is available on the server. */
  public void initialize() {
    worldGuardEnabled = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    if (worldGuardEnabled) {
      try {
        handler = new WorldGuardHandler();
        Bukkit.getLogger().info("[Evolv3] WorldGuard found - region protection enabled");
      } catch (NoClassDefFoundError | Exception e) {
        worldGuardEnabled = false;
        handler = null;
        Bukkit.getLogger()
            .warning("[Evolv3] WorldGuard found but failed to hook: " + e.getMessage());
      }
    } else {
      Bukkit.getLogger().info("[Evolv3] WorldGuard not found - region protection disabled");
    }
  }

  /**
   * Checks if a location is within a mine region. A mine region is any region whose ID contains the
   * configured mine region keyword.
   *
   * @param location the location to check
   * @return true if within a mine region, false otherwise
   */
  public boolean isWithinMineRegion(Location location) {
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
  public boolean isEnabled() {
    return worldGuardEnabled;
  }

  /**
   * Returns the XZ bounds of the mine region containing the given location, or {@code null} if
   * WorldGuard is disabled or the location is not inside a mine region.
   *
   * @param location the location to query
   * @return int array {@code [minX, minZ, maxX, maxZ]}, or {@code null}
   */
  public int[] getMineRegionXZBounds(Location location) {
    if (!worldGuardEnabled || handler == null) return null;
    if (location == null || location.getWorld() == null) return null;

    try {
      FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
      String mineRegionKeyword = config.getString(ConfigPaths.Default.MINE_REGION, "mine");
      if (mineRegionKeyword == null || mineRegionKeyword.isEmpty()) return null;
      return handler.getRegionXZBounds(location, mineRegionKeyword);
    } catch (NoClassDefFoundError | Exception e) {
      Bukkit.getLogger()
          .warning("[Evolv3] Error fetching WorldGuard region bounds: " + e.getMessage());
      return null;
    }
  }

  /**
   * Checks if a location is within a region whose id contains "spawner".
   *
   * @param location the location to check
   * @return true if within a spawner region (or if WorldGuard is disabled)
   */
  public boolean isWithinSpawnerRegion(Location location) {
    if (!worldGuardEnabled || handler == null) return true;
    if (location == null || location.getWorld() == null) return false;
    try {
      return handler.isInRegion(location, "spawner");
    } catch (NoClassDefFoundError | Exception e) {
      Bukkit.getLogger().warning("[Evolv3] Error checking spawner region: " + e.getMessage());
      return true;
    }
  }

  /**
   * Returns the full 3-D bounds {@code [minX, minY, minZ, maxX, maxY, maxZ]} of the first "spawner"
   * region containing the given location, or {@code null} if none found.
   *
   * @param location the location to query
   * @return 6-element int array, or {@code null}
   */
  public int[] getSpawnerRegionBounds(Location location) {
    if (!worldGuardEnabled || handler == null) return null;
    if (location == null || location.getWorld() == null) return null;
    try {
      return handler.getRegionFullBounds(location, "spawner");
    } catch (NoClassDefFoundError | Exception e) {
      Bukkit.getLogger()
          .warning("[Evolv3] Error fetching spawner region bounds: " + e.getMessage());
      return null;
    }
  }

  /**
   * Checks if a location is within a region whose id contains "omnitool-axe".
   *
   * @param location the location to check
   * @return true if within an omnitool-axe region (false if WorldGuard is disabled)
   */
  public boolean isWithinOmnitoolAxeRegion(Location location) {
    if (!worldGuardEnabled || handler == null) return false;
    if (location == null || location.getWorld() == null) return false;
    try {
      return handler.isInRegion(location, "omnitool-axe");
    } catch (NoClassDefFoundError | Exception e) {
      return false;
    }
  }

  /**
   * Checks if a location is within a region whose id contains "omnitool-sword".
   *
   * @param location the location to check
   * @return true if within an omnitool-sword region (false if WorldGuard is disabled)
   */
  public boolean isWithinOmnitoolSwordRegion(Location location) {
    if (!worldGuardEnabled || handler == null) return false;
    if (location == null || location.getWorld() == null) return false;
    try {
      return handler.isInRegion(location, "omnitool-sword");
    } catch (NoClassDefFoundError | Exception e) {
      return false;
    }
  }

  /**
   * Counts online players currently inside any region whose id contains the given keyword.
   *
   * @param location location used to identify the region
   * @param keyword region id keyword to match
   * @return number of players inside the matching region, or 0 if WorldGuard is unavailable
   */
  public int countPlayersInRegion(Location location, String keyword) {
    if (!worldGuardEnabled || handler == null) return 0;
    if (location == null || location.getWorld() == null) return 0;
    try {
      return handler.countPlayersInRegion(location, keyword);
    } catch (NoClassDefFoundError | Exception e) {
      return 0;
    }
  }

  /**
   * Inner handler class that contains all WorldGuard-specific code. This class is only loaded when
   * WorldGuard is actually available, preventing ClassNotFoundException when WorldGuard is not
   * installed.
   */
  private static class WorldGuardHandler {
    /** Checks if a location is within a region containing the keyword. */
    public boolean isInRegion(Location location, String keyword) {
      com.sk89q.worldguard.protection.managers.RegionManager regionManager =
          getRegionManager(location);
      if (regionManager == null) return false;

      for (com.sk89q.worldguard.protection.regions.ProtectedRegion region :
          regionManager.getApplicableRegions(
              com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location))) {
        if (region.getId().toLowerCase().contains(keyword.toLowerCase())) return true;
      }
      return false;
    }

    /** Returns {@code [minX, minZ, maxX, maxZ]} for the first matching region. */
    @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
    public int[] getRegionXZBounds(Location location, String keyword) {
      com.sk89q.worldguard.protection.managers.RegionManager regionManager =
          getRegionManager(location);
      if (regionManager == null) return null;

      for (com.sk89q.worldguard.protection.regions.ProtectedRegion region :
          regionManager.getApplicableRegions(
              com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location))) {
        if (!region.getId().toLowerCase().contains(keyword.toLowerCase())) continue;
        com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
        com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
        return new int[] {
          min.x(), min.z(), max.x(), max.z()
        }; // NOPMD - intentional find-first loop
      }
      return null;
    }

    /** Returns {@code [minX, minY, minZ, maxX, maxY, maxZ]} for the first matching region. */
    @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
    public int[] getRegionFullBounds(Location location, String keyword) {
      com.sk89q.worldguard.protection.managers.RegionManager regionManager =
          getRegionManager(location);
      if (regionManager == null) return null;

      for (com.sk89q.worldguard.protection.regions.ProtectedRegion region :
          regionManager.getApplicableRegions(
              com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location))) {
        if (!region.getId().toLowerCase().contains(keyword.toLowerCase())) continue;
        com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
        com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
        return new int[] {
          min.x(), min.y(), min.z(), max.x(), max.y(), max.z()
        }; // NOPMD - intentional find-first loop
      }
      return null;
    }

    /** Counts online players inside the first matching region at the given location. */
    public int countPlayersInRegion(Location location, String keyword) {
      com.sk89q.worldguard.protection.managers.RegionManager regionManager =
          getRegionManager(location);
      if (regionManager == null) return 0;

      com.sk89q.worldguard.protection.regions.ProtectedRegion target = null;
      for (com.sk89q.worldguard.protection.regions.ProtectedRegion region :
          regionManager.getApplicableRegions(
              com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location))) {
        if (region.getId().toLowerCase().contains(keyword.toLowerCase())) {
          target = region;
          break;
        }
      }
      if (target == null) return 0;

      com.sk89q.worldedit.math.BlockVector3 min = target.getMinimumPoint();
      com.sk89q.worldedit.math.BlockVector3 max = target.getMaximumPoint();

      int count = 0;
      for (org.bukkit.entity.Player p : location.getWorld().getPlayers()) {
        org.bukkit.Location pl = p.getLocation();
        if (pl.getBlockX() >= min.x()
            && pl.getBlockX() <= max.x()
            && pl.getBlockY() >= min.y()
            && pl.getBlockY() <= max.y()
            && pl.getBlockZ() >= min.z()
            && pl.getBlockZ() <= max.z()) {
          count++;
        }
      }
      return count;
    }

    private com.sk89q.worldguard.protection.managers.RegionManager getRegionManager(
        Location location) {
      com.sk89q.worldguard.protection.regions.RegionContainer container =
          com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
      return container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location.getWorld()));
    }
  }
}
