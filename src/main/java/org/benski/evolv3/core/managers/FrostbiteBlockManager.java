package org.benski.evolv3.core.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists Frostbite-frozen block locations and their original materials
 * so they can be restored when the server restarts.
 *
 * <p>Block metadata (used for live enchant interaction) does not survive restarts.
 * This manager writes to {@code frozen_blocks.yml} in the plugin data folder so
 * the Frostbite enchant's world changes are always cleaned up on next startup.</p>
 */
public class FrostbiteBlockManager
{
    private static final FrostbiteBlockManager instance = new FrostbiteBlockManager();

    private JavaPlugin plugin;
    private File dataFile;
    private FileConfiguration data;

    /** Live in-memory cache: location key → original block material. Cleared on restore. */
    private final Map<String, Material> liveCache = new ConcurrentHashMap<>();

    private FrostbiteBlockManager() {}

    public static FrostbiteBlockManager getInstance() {
        return instance;
    }

    /**
     * Loads persisted frozen blocks from disk and immediately restores them
     * to their original material. Call this early in {@code onEnable()} so
     * the world is clean before any enchant logic runs.
     *
     * @param plugin the plugin instance
     */
    public void initialize(JavaPlugin plugin) {
        this.plugin = plugin;
        File localDataDir = new File(plugin.getDataFolder(), "localdata");
        if (!localDataDir.exists()) localDataDir.mkdirs();
        dataFile = new File(localDataDir, "frozen_blocks.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        restoreAll();
    }

    /**
     * Records a newly frozen block. Must be called after the block has been
     * set to {@link Material#PACKED_ICE}.
     *
     * @param loc      the block's location
     * @param original the material the block was before freezing
     */
    public void addFrozenBlock(Location loc, Material original) {
        List<Map<String, Object>> blocks = getMutableBlockList();

        Map<String, Object> entry = new HashMap<>();
        entry.put("world", loc.getWorld().getName());
        entry.put("x", loc.getBlockX());
        entry.put("y", loc.getBlockY());
        entry.put("z", loc.getBlockZ());
        entry.put("original", original.name());

        blocks.add(entry);
        data.set("blocks", blocks);
        save();
        liveCache.put(locKey(loc), original);
    }

    /**
     * Removes a frozen block from persistence (call when it is mined).
     *
     * @param loc the block's location
     */
    public void removeFrozenBlock(Location loc) {
        List<Map<String, Object>> blocks = getMutableBlockList();
        boolean removed = blocks.removeIf(e ->
                Objects.equals(e.get("world"), loc.getWorld().getName())
                && toInt(e.get("x")) == loc.getBlockX()
                && toInt(e.get("y")) == loc.getBlockY()
                && toInt(e.get("z")) == loc.getBlockZ()
        );
        if (removed) {
            data.set("blocks", blocks);
            save();
            liveCache.remove(locKey(loc));
        }
    }

    /**
     * Removes multiple frozen blocks in a single save pass (used by Shatter).
     *
     * @param locations the locations to remove
     */
    public void removeFrozenBlocks(List<Location> locations) {
        List<Map<String, Object>> blocks = getMutableBlockList();
        for (Location loc : locations) {
            blocks.removeIf(e ->
                    Objects.equals(e.get("world"), loc.getWorld().getName())
                    && toInt(e.get("x")) == loc.getBlockX()
                    && toInt(e.get("y")) == loc.getBlockY()
                    && toInt(e.get("z")) == loc.getBlockZ()
            );
            liveCache.remove(locKey(loc));
        }
        data.set("blocks", blocks);
        save();
    }

    /**
     * Returns an unmodifiable view of the live frozen block cache.
     * Keys are {@code "world,x,y,z"} strings; values are original block materials.
     */
    public Map<String, Material> getLiveCache() {
        return Collections.unmodifiableMap(liveCache);
    }

    /**
     * Parses a live-cache key back into a {@link Location}.
     * Returns {@code null} if the key is malformed or the world is not loaded.
     */
    public static Location parseKey(String key) {
        String[] parts = key.split(",", 4);
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // -------------------------------------------------------------------------

    private void restoreAll() {
        List<Map<?, ?>> blocks = data.getMapList("blocks");
        if (blocks.isEmpty()) return;

        int count = 0;
        for (Map<?, ?> entry : blocks) {
            String worldName = (String) entry.get("world");
            int x = toInt(entry.get("x"));
            int y = toInt(entry.get("y"));
            int z = toInt(entry.get("z"));
            String matName = (String) entry.get("original");

            if (worldName == null || matName == null) continue;

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            try {
                Material original = Material.valueOf(matName);
                world.getBlockAt(x, y, z).setType(original);
                count++;
            } catch (IllegalArgumentException ignored) {}
        }

        data.set("blocks", null);
        save();
        liveCache.clear();

        if (count > 0) {
            plugin.getLogger().info("Restored " + count + " Frostbite-frozen block(s) from last session.");
        }
    }

    private List<Map<String, Object>> getMutableBlockList() {
        List<Map<?, ?>> raw = data.getMapList("blocks");
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Map<?, ?> m : raw) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            result.add(copy);
        }
        return result;
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save frozen_blocks.yml: " + e.getMessage());
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }
}
