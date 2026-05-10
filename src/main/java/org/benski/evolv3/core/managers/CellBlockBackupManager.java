package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.managers.CellManager.CellDef;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and restores all blocks within a cell's region bounds.
 * Backups are stored as YAML in localdata/cell-backups/<name>.yml.
 * Only solid/non-air blocks are stored to keep file sizes small.
 */
public final class CellBlockBackupManager
{
    @Getter
    private static final CellBlockBackupManager instance = new CellBlockBackupManager();

    private CellBlockBackupManager() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Saves every block in the cell's current region to disk.
     * Should be called when a player rents the cell (before they modify anything)
     * and after each expansion (to capture the new bounds).
     */
    public void save(@NotNull String cellName) {
        CellDef def = CellManager.getInstance().get(cellName);
        if (def == null) return;
        World world = Bukkit.getWorld(def.world());
        if (world == null) return;

        int x1 = Math.min(def.x1(), def.x2()), x2 = Math.max(def.x1(), def.x2());
        int y1 = Math.min(def.y1(), def.y2()), y2 = Math.max(def.y1(), def.y2());
        int z1 = Math.min(def.z1(), def.z2()), z2 = Math.max(def.z1(), def.z2());

        List<String> blocks = new ArrayList<>();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    Material mat = world.getBlockAt(x, y, z).getType();
                    if (mat != Material.AIR && mat != Material.CAVE_AIR && mat != Material.VOID_AIR) {
                        blocks.add(x + ":" + y + ":" + z + ":" + mat.name());
                    }
                }
            }
        }

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("world", def.world());
        cfg.set("x1", x1); cfg.set("y1", y1); cfg.set("z1", z1);
        cfg.set("x2", x2); cfg.set("y2", y2); cfg.set("z2", z2);
        cfg.set("blocks", blocks);

        try { cfg.save(backupFile(cellName)); }
        catch (IOException e) {
            Bukkit.getLogger().severe("[Evolv3] Failed to save cell backup for '" + cellName + "': " + e.getMessage());
        }
    }

    /**
     * Restores all blocks from the backup.
     * Clears the CURRENT cell bounds (may be larger after expansions), then re-places the
     * original backed-up blocks, and resets the CellDef bounds to the original backup bounds.
     */
    public void restore(@NotNull String cellName) {
        File file = backupFile(cellName);
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String worldName = cfg.getString("world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Bukkit.getLogger().warning("[Evolv3] Cannot restore cell '" + cellName + "': world '" + worldName + "' not found.");
            return;
        }

        // Original (backup) bounds — used for re-placing blocks and resetting CellDef
        int bx1 = cfg.getInt("x1"), by1 = cfg.getInt("y1"), bz1 = cfg.getInt("z1");
        int bx2 = cfg.getInt("x2"), by2 = cfg.getInt("y2"), bz2 = cfg.getInt("z2");

        // Determine clearing bounds: use the CURRENT cell def (may be expanded beyond backup)
        CellDef currentDef = CellManager.getInstance().get(cellName);
        int cx1, cy1, cz1, cx2, cy2, cz2;
        if (currentDef != null) {
            cx1 = Math.min(Math.min(currentDef.x1(), currentDef.x2()), bx1);
            cy1 = Math.min(Math.min(currentDef.y1(), currentDef.y2()), by1);
            cz1 = Math.min(Math.min(currentDef.z1(), currentDef.z2()), bz1);
            cx2 = Math.max(Math.max(currentDef.x1(), currentDef.x2()), bx2);
            cy2 = Math.max(Math.max(currentDef.y1(), currentDef.y2()), by2);
            cz2 = Math.max(Math.max(currentDef.z1(), currentDef.z2()), bz2);
        } else {
            cx1 = bx1; cy1 = by1; cz1 = bz1;
            cx2 = bx2; cy2 = by2; cz2 = bz2;
        }

        // Clear the full expanded area first
        for (int x = cx1; x <= cx2; x++) {
            for (int y = cy1; y <= cy2; y++) {
                for (int z = cz1; z <= cz2; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }

        // Restore original backed-up blocks
        for (String entry : cfg.getStringList("blocks")) {
            try {
                String[] parts = entry.split(":", 4);
                if (parts.length < 4) continue;
                int rx = Integer.parseInt(parts[0]);
                int ry = Integer.parseInt(parts[1]);
                int rz = Integer.parseInt(parts[2]);
                Material mat = Material.matchMaterial(parts[3]);
                if (mat != null) {
                    world.getBlockAt(rx, ry, rz).setType(mat, false);
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Evolv3] Skipping bad backup entry for cell '" + cellName + "': " + entry);
            }
        }

        // Reset CellDef bounds back to the original (pre-expansion) bounds
        if (currentDef != null) {
            CellManager.getInstance().update(currentDef.withBounds(bx1, by1, bz1, bx2, by2, bz2));
        }
    }

    /** Returns true if a backup file exists for the given cell. */
    public boolean hasBackup(@NotNull String cellName) {
        return backupFile(cellName).exists();
    }

    /** Deletes the backup file for the given cell. */
    public void deleteBackup(@NotNull String cellName) {
        File file = backupFile(cellName);
        if (file.exists()) file.delete();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private @NotNull File backupFile(@NotNull String cellName) {
        File dir = new File(Main.getInstance().getDataFolder(), "localdata/cell-backups");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, cellName.toLowerCase() + ".yml");
    }
}
