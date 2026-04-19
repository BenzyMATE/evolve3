package org.benski.evolv3.core.helpers;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the current WorldEdit selection (pos1 / pos2) for a player.
 *
 * <p>This isolates all WorldEdit API calls so that the rest of the plugin
 * doesn't need to import WorldEdit classes directly. If WorldEdit is not
 * installed, {@link #isAvailable()} returns false and selection methods
 * return null.</p>
 */
public final class WorldEditSelection
{
    private WorldEditSelection() {}

    /** Returns true if WorldEdit is loaded on this server. */
    public static boolean isAvailable()
    {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    }

    /**
     * Returns the player's current WorldEdit selection as two block-coordinate
     * arrays: {@code [minX, minY, minZ]} and {@code [maxX, maxY, maxZ]}.
     *
     * @return an {@code int[2][3]} array ({@code [0] = min, [1] = max}),
     *         or null if the selection is incomplete or WorldEdit is missing
     */
    public static int @Nullable [][] getSelection(Player player)
    {
        if (!isAvailable()) return null;

        try {
            var wePlayer = BukkitAdapter.adapt(player);
            var session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            Region region = session.getSelection(wePlayer.getWorld());

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            return new int[][]{
                    {min.x(), min.y(), min.z()},
                    {max.x(), max.y(), max.z()}
            };
        } catch (IncompleteRegionException e) {
            return null;
        }
    }
}
