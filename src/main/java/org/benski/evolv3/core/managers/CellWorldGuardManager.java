package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Manages WorldGuard regions for cells.
 * Region IDs follow the pattern "evolv3-cell-<cellname>".
 * Silently no-ops if WorldGuard is not installed.
 */
public final class CellWorldGuardManager
{
    @Getter
    private static final CellWorldGuardManager instance = new CellWorldGuardManager();

    private boolean available = false;
    private CellGuardHandler handler = null;

    private CellWorldGuardManager() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return;
        try {
            handler = new CellGuardHandler();
            available = true;
        } catch (NoClassDefFoundError | Exception e) {
            Bukkit.getLogger().warning("[Evolv3] WorldGuard found but cell WG hook failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Creates a WG region for the cell, owned by the given player. */
    public void createRegion(@NotNull String cellName, @NotNull World world,
                             int x1, int y1, int z1, int x2, int y2, int z2,
                             @NotNull UUID ownerUuid) {
        if (!available || handler == null) return;
        try { handler.createRegion(regionId(cellName), world, x1, y1, z1, x2, y2, z2, ownerUuid); }
        catch (Exception e) { Bukkit.getLogger().warning("[Evolv3] WG createRegion failed: " + e.getMessage()); }
    }

    /** Expands (replaces) the WG region with new bounds, keeping existing ownership. */
    public void expandRegion(@NotNull String cellName, @NotNull World world,
                              int x1, int y1, int z1, int x2, int y2, int z2) {
        if (!available || handler == null) return;
        try { handler.expandRegion(regionId(cellName), world, x1, y1, z1, x2, y2, z2); }
        catch (Exception e) { Bukkit.getLogger().warning("[Evolv3] WG expandRegion failed: " + e.getMessage()); }
    }

    /** Deletes the WG region for the cell. */
    public void deleteRegion(@NotNull String cellName) {
        if (!available || handler == null) return;
        try {
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                handler.deleteRegion(regionId(cellName), w);
            }
        } catch (Exception e) { Bukkit.getLogger().warning("[Evolv3] WG deleteRegion failed: " + e.getMessage()); }
    }

    public void addMember(@NotNull String cellName, @NotNull World world, @NotNull UUID memberUuid) {
        if (!available || handler == null) return;
        try { handler.addMember(regionId(cellName), world, memberUuid); }
        catch (Exception e) { Bukkit.getLogger().warning("[Evolv3] WG addMember failed: " + e.getMessage()); }
    }

    public void removeMember(@NotNull String cellName, @NotNull World world, @NotNull UUID memberUuid) {
        if (!available || handler == null) return;
        try { handler.removeMember(regionId(cellName), world, memberUuid); }
        catch (Exception e) { Bukkit.getLogger().warning("[Evolv3] WG removeMember failed: " + e.getMessage()); }
    }

    private static String regionId(@NotNull String cellName) {
        return "evolv3-cell-" + cellName.toLowerCase();
    }

    // =========================================================================
    // Inner handler (WorldGuard classes loaded lazily)
    // =========================================================================

    private static final class CellGuardHandler
    {
        void createRegion(String id, World world, int x1, int y1, int z1, int x2, int y2, int z2, UUID ownerUuid) {
            com.sk89q.worldguard.protection.managers.RegionManager rm = getRM(world);
            if (rm == null) return;
            com.sk89q.worldedit.math.BlockVector3 min = com.sk89q.worldedit.math.BlockVector3.at(
                    Math.min(x1,x2), Math.min(y1,y2), Math.min(z1,z2));
            com.sk89q.worldedit.math.BlockVector3 max = com.sk89q.worldedit.math.BlockVector3.at(
                    Math.max(x1,x2), Math.max(y1,y2), Math.max(z1,z2));
            com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion region =
                    new com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion(id, min, max);
            com.sk89q.worldguard.domains.DefaultDomain owners = new com.sk89q.worldguard.domains.DefaultDomain();
            owners.addPlayer(ownerUuid);
            region.setOwners(owners);
            rm.addRegion(region);
        }

        void expandRegion(String id, World world, int x1, int y1, int z1, int x2, int y2, int z2) {
            com.sk89q.worldguard.protection.managers.RegionManager rm = getRM(world);
            if (rm == null) return;
            com.sk89q.worldguard.protection.regions.ProtectedRegion existing = rm.getRegion(id);
            com.sk89q.worldedit.math.BlockVector3 min = com.sk89q.worldedit.math.BlockVector3.at(
                    Math.min(x1,x2), Math.min(y1,y2), Math.min(z1,z2));
            com.sk89q.worldedit.math.BlockVector3 max = com.sk89q.worldedit.math.BlockVector3.at(
                    Math.max(x1,x2), Math.max(y1,y2), Math.max(z1,z2));
            com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion region =
                    new com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion(id, min, max);
            if (existing != null) {
                region.setOwners(existing.getOwners());
                region.setMembers(existing.getMembers());
            }
            rm.addRegion(region);
        }

        void deleteRegion(String id, World world) {
            com.sk89q.worldguard.protection.managers.RegionManager rm = getRM(world);
            if (rm == null) return;
            rm.removeRegion(id);
        }

        void addMember(String id, World world, UUID memberUuid) {
            com.sk89q.worldguard.protection.managers.RegionManager rm = getRM(world);
            if (rm == null) return;
            com.sk89q.worldguard.protection.regions.ProtectedRegion region = rm.getRegion(id);
            if (region == null) return;
            region.getMembers().addPlayer(memberUuid);
        }

        void removeMember(String id, World world, UUID memberUuid) {
            com.sk89q.worldguard.protection.managers.RegionManager rm = getRM(world);
            if (rm == null) return;
            com.sk89q.worldguard.protection.regions.ProtectedRegion region = rm.getRegion(id);
            if (region == null) return;
            region.getMembers().removePlayer(memberUuid);
        }

        private @Nullable com.sk89q.worldguard.protection.managers.RegionManager getRM(World world) {
            com.sk89q.worldguard.protection.regions.RegionContainer container =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
            return container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
        }
    }
}
