package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.generators.WaterWorldGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the island world (evolv3_islands).
 * Creates the world on first use and provides parcel-building utilities.
 */
public final class IslandWorldManager
{
    @Getter
    private static final IslandWorldManager instance = new IslandWorldManager();

    public static final String WORLD_NAME = "evolv3_islands";

    /** Y level at which island platforms sit. */
    private static final int PLATFORM_Y = 63;

    private World islandWorld = null;

    private IslandWorldManager() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize() {
        islandWorld = Bukkit.getWorld(WORLD_NAME);
        if (islandWorld == null) {
            islandWorld = WorldCreator.name(WORLD_NAME)
                    .generator(new WaterWorldGenerator())
                    .type(WorldType.FLAT)
                    .createWorld();
            if (islandWorld != null) {
                Bukkit.getLogger().info("[Evolv3] Created island world: " + WORLD_NAME);
            }
        }
    }

    public void cleanup() {}

    // =========================================================================
    // Public API
    // =========================================================================

    public @Nullable World getWorld() {
        if (islandWorld == null) islandWorld = Bukkit.getWorld(WORLD_NAME);
        return islandWorld;
    }

    /**
     * Sets a personal world border for the given player centred on their island parcel.
     *
     * @param player the player to receive the border
     * @param data   the island whose position defines the border
     */
    public void setPlayerBorder(@NotNull Player player, @NotNull IslandManager.IslandData data) {
        World world = getWorld();
        if (world == null) return;
        WorldBorder border = Bukkit.createWorldBorder();
        double cx = data.worldX() + (IslandManager.ISLAND_SIZE / 2.0);
        double cz = data.worldZ() + (IslandManager.ISLAND_SIZE / 2.0);
        border.setCenter(cx, cz);
        border.setSize(IslandManager.ISLAND_SIZE);
        border.setWarningDistance(5);
        border.setWarningTime(0);
        player.setWorldBorder(border);
    }

    /** Returns the spawn Location for the given island (above initial parcel centre). */
    public @Nullable Location getSpawnLocation(@NotNull IslandManager.IslandData data) {
        World world = getWorld();
        if (world == null) return null;
        return new Location(world, data.spawnX(), IslandManager.ISLAND_SPAWN_Y + 1, data.spawnZ(), 0f, 0f);
    }

    /**
     * Builds the initial 11×11 grass parcel for a freshly created island.
     * Uses the INITIAL_PARCEL slot (22) coordinates.
     */
    public void buildPlatform(@NotNull IslandManager.IslandData data) {
        int initialCol = IslandManager.INITIAL_PARCEL % IslandManager.GRID_COLS;
        int initialRow = IslandManager.INITIAL_PARCEL / IslandManager.GRID_COLS;
        buildParcel(data, initialCol, initialRow);
    }

    /**
     * Clears all purchased parcels back to water, removing all player-built blocks.
     * Called when an island is deleted.
     */
    public void deleteIsland(@NotNull IslandManager.IslandData data) {
        World world = getWorld();
        if (world == null) return;
        for (int slot : data.purchasedSlots()) {
            int col = slot % IslandManager.GRID_COLS;
            int row = slot / IslandManager.GRID_COLS;
            clearParcel(data, col, row);
        }
    }

    private void clearParcel(@NotNull IslandManager.IslandData data, int col, int row) {
        World world = getWorld();
        if (world == null) return;
        int startX = data.worldX() + col * IslandManager.PARCEL_SIZE;
        int startZ = data.worldZ() + row * IslandManager.PARCEL_SIZE;

        for (int x = startX; x < startX + IslandManager.PARCEL_SIZE; x++) {
            for (int z = startZ; z < startZ + IslandManager.PARCEL_SIZE; z++) {
                // Restore floor layers to water
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.WATER, false);
                world.getBlockAt(x, PLATFORM_Y - 1, z).setType(Material.WATER, false);
                // Clear all player-built blocks above the floor
                for (int y = PLATFORM_Y + 1; y <= IslandManager.ISLAND_SPAWN_Y + 40; y++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /**
     * Builds an 11×11 grass/dirt parcel at the given grid position within the island plot.
     * Clears water above the parcel floor level.
     *
     * @param data the island whose world-space origin is used
     * @param col  parcel column (0-based)
     * @param row  parcel row (0-based)
     */
    public void buildParcel(@NotNull IslandManager.IslandData data, int col, int row) {
        World world = getWorld();
        if (world == null) return;

        int startX = data.worldX() + col * IslandManager.PARCEL_SIZE;
        int startZ = data.worldZ() + row * IslandManager.PARCEL_SIZE;
        int y = PLATFORM_Y;

        for (int x = startX; x < startX + IslandManager.PARCEL_SIZE; x++) {
            for (int z = startZ; z < startZ + IslandManager.PARCEL_SIZE; z++) {
                world.getBlockAt(x, y, z).setType(Material.GRASS_BLOCK, false);
                world.getBlockAt(x, y - 1, z).setType(Material.DIRT, false);
                // Clear water above
                for (int by = y + 1; by <= IslandManager.ISLAND_SPAWN_Y + 3; by++) {
                    if (world.getBlockAt(x, by, z).getType() == Material.WATER) {
                        world.getBlockAt(x, by, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
