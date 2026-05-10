package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages cell definitions created by admins.
 * Each cell is a physical region in the world that players can rent.
 */
public final class CellManager
{
    @Getter
    private static final CellManager instance = new CellManager();

    /**
     * Immutable cell definition set by admins.
     *
     * @param name             unique lowercase identifier
     * @param world            world name
     * @param x1 y1 z1         minimum corner of region
     * @param x2 y2 z2         maximum corner of region
     * @param spawnX/Y/Z       cell spawn point
     * @param spawnYaw/Pitch   spawn orientation
     * @param basePrice        base rent price per day
     * @param currency         "coins" or "essence"
     * @param maxExpansions    how many expansions before island portal unlocks
     * @param upgradeDirection N/S/E/W — which wall expands
     * @param wallMaterial     material used when placing expansion wall blocks
     */
    public record CellDef(
            String name,
            String world,
            int x1, int y1, int z1,
            int x2, int y2, int z2,
            double spawnX, double spawnY, double spawnZ,
            float spawnYaw, float spawnPitch,
            double basePrice,
            String currency,
            int maxExpansions,
            String upgradeDirection,
            String wallMaterial
    ) {
        public CellDef withBounds(int nx1, int ny1, int nz1, int nx2, int ny2, int nz2) {
            return new CellDef(name, world, nx1, ny1, nz1, nx2, ny2, nz2,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                    basePrice, currency, maxExpansions, upgradeDirection, wallMaterial);
        }
        public CellDef withSpawn(double sx, double sy, double sz, float yaw, float pitch) {
            return new CellDef(name, world, x1, y1, z1, x2, y2, z2,
                    sx, sy, sz, yaw, pitch,
                    basePrice, currency, maxExpansions, upgradeDirection, wallMaterial);
        }
        public CellDef withPrice(double price) {
            return new CellDef(name, world, x1, y1, z1, x2, y2, z2,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                    price, currency, maxExpansions, upgradeDirection, wallMaterial);
        }
        public CellDef withCurrency(String cur) {
            return new CellDef(name, world, x1, y1, z1, x2, y2, z2,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                    basePrice, cur, maxExpansions, upgradeDirection, wallMaterial);
        }
        public CellDef withMaxExpansions(int max) {
            return new CellDef(name, world, x1, y1, z1, x2, y2, z2,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                    basePrice, currency, max, upgradeDirection, wallMaterial);
        }
        public CellDef withDirection(String dir) {
            return new CellDef(name, world, x1, y1, z1, x2, y2, z2,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                    basePrice, currency, maxExpansions, dir, wallMaterial);
        }
        public CellDef withWallMaterial(String mat) {
            return new CellDef(name, world, x1, y1, z1, x2, y2, z2,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                    basePrice, currency, maxExpansions, upgradeDirection, mat);
        }

        public boolean contains(double x, double y, double z) {
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            return bx >= Math.min(x1, x2) && bx <= Math.max(x1, x2)
                    && by >= Math.min(y1, y2) && by <= Math.max(y1, y2)
                    && bz >= Math.min(z1, z2) && bz <= Math.max(z1, z2);
        }
    }

    private final Map<String, CellDef> cells = new HashMap<>();

    private CellManager() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize() { loadFromDisk(); }

    public void cleanup() { cells.clear(); }

    // =========================================================================
    // CRUD
    // =========================================================================

    public boolean create(@NotNull CellDef def) {
        String key = def.name().toLowerCase();
        if (cells.containsKey(key)) return false;
        cells.put(key, def);
        saveToDisk();
        return true;
    }

    public boolean remove(@NotNull String name) {
        boolean removed = cells.remove(name.toLowerCase()) != null;
        if (removed) saveToDisk();
        return removed;
    }

    public boolean update(@NotNull CellDef def) {
        String key = def.name().toLowerCase();
        if (!cells.containsKey(key)) return false;
        cells.put(key, def);
        saveToDisk();
        return true;
    }

    public @Nullable CellDef get(@NotNull String name) {
        return cells.get(name.toLowerCase());
    }

    public @NotNull Collection<CellDef> getAll() {
        return Collections.unmodifiableCollection(cells.values());
    }

    public boolean exists(@NotNull String name) {
        return cells.containsKey(name.toLowerCase());
    }

    /** Returns the cell def whose bounds contain the given coordinates, or null. */
    public @Nullable CellDef findAt(@NotNull String world, double x, double y, double z) {
        for (CellDef cell : cells.values()) {
            if (cell.world().equals(world) && cell.contains(x, y, z)) return cell;
        }
        return null;
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    public void loadFromDisk() {
        cells.clear();
        File file = dataFile();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("cells");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            String p = "cells." + name;
            try {
                CellDef def = new CellDef(
                        name,
                        cfg.getString(p + ".world", "world"),
                        cfg.getInt(p + ".x1"), cfg.getInt(p + ".y1"), cfg.getInt(p + ".z1"),
                        cfg.getInt(p + ".x2"), cfg.getInt(p + ".y2"), cfg.getInt(p + ".z2"),
                        cfg.getDouble(p + ".spawn.x"), cfg.getDouble(p + ".spawn.y"), cfg.getDouble(p + ".spawn.z"),
                        (float) cfg.getDouble(p + ".spawn.yaw"), (float) cfg.getDouble(p + ".spawn.pitch"),
                        cfg.getDouble(p + ".base-price", 500.0),
                        cfg.getString(p + ".currency", "coins"),
                        cfg.getInt(p + ".max-expansions", 11),
                        cfg.getString(p + ".upgrade-direction", "N"),
                        cfg.getString(p + ".wall-material", "STONE_BRICKS")
                );
                cells.put(name.toLowerCase(), def);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Evolv3] Failed to load cell '" + name + "': " + e.getMessage());
            }
        }
    }

    private void saveToDisk() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (CellDef def : cells.values()) {
            String p = "cells." + def.name();
            cfg.set(p + ".world", def.world());
            cfg.set(p + ".x1", def.x1()); cfg.set(p + ".y1", def.y1()); cfg.set(p + ".z1", def.z1());
            cfg.set(p + ".x2", def.x2()); cfg.set(p + ".y2", def.y2()); cfg.set(p + ".z2", def.z2());
            cfg.set(p + ".spawn.x", def.spawnX()); cfg.set(p + ".spawn.y", def.spawnY()); cfg.set(p + ".spawn.z", def.spawnZ());
            cfg.set(p + ".spawn.yaw", def.spawnYaw()); cfg.set(p + ".spawn.pitch", def.spawnPitch());
            cfg.set(p + ".base-price", def.basePrice());
            cfg.set(p + ".currency", def.currency());
            cfg.set(p + ".max-expansions", def.maxExpansions());
            cfg.set(p + ".upgrade-direction", def.upgradeDirection());
            cfg.set(p + ".wall-material", def.wallMaterial());
        }
        try { cfg.save(dataFile()); }
        catch (IOException e) {
            Bukkit.getLogger().severe("[Evolv3] Failed to save cells.yml: " + e.getMessage());
        }
    }

    private @NotNull File dataFile() {
        File dir = new File(Main.getInstance().getDataFolder(), "localdata");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cells.yml");
    }
}
