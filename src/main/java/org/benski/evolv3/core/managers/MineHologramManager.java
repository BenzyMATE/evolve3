package org.benski.evolv3.core.managers;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.managers.MineManager.Mine;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages floating TextDisplay holograms above each mine showing live stats.
 *
 * <h3>Design goals</h3>
 * <ul>
 *   <li>No file I/O in the hot update loop — config values are cached and
 *       refreshed every {@value #CONFIG_REFRESH_TICKS} ticks.</li>
 *   <li>Text is only rebuilt when values actually change, using a per-mine
 *       {@link MineSnapshot} for change detection.</li>
 *   <li>Entities use {@code setPersistent(false)} so they are never written
 *       to disk, eliminating the duplicate-entity crash on restart.</li>
 *   <li>Entities are only spawned in chunks that are already player-loaded.
 *       {@link #onChunkLoad(Chunk)} handles the spawn the moment a player
 *       naturally loads the relevant chunk, avoiding the force-load/instant-
 *       unload loop that previously prevented text from updating.</li>
 * </ul>
 */
public final class MineHologramManager
{
    @Getter
    private static final MineHologramManager instance = new MineHologramManager();

    /** Active holograms, keyed by lowercase mine name. */
    private final Map<String, TextDisplay> holograms = new HashMap<>();

    /**
     * Values that were last used to render each hologram.
     * Text is only rebuilt when these change, avoiding redundant work.
     */
    private final Map<String, MineSnapshot> lastSnapshot = new HashMap<>();

    /** Periodic update task — null when not running. */
    private BukkitTask updateTask;

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    /**
     * PDC key applied to every hologram entity so orphaned instances from
     * previous runs can be identified and removed.  Public so {@code MineListener}
     * can reference the same key without duplication.
     */
    public static final NamespacedKey PDC_KEY =
            new NamespacedKey("evolv3", "mine_hologram");

    // -------------------------------------------------------------------------
    // Config cache — refreshed every CONFIG_REFRESH_TICKS to avoid file reads
    // in the hot update loop.
    // -------------------------------------------------------------------------

    /** How often (in ticks) to re-read hologram config values. */
    private static final int CONFIG_REFRESH_TICKS = 6_000; // 5 minutes

    private List<String> cachedTemplateLines = null;
    private float         cachedScale         = 4.0f;
    private double        cachedOffsetY        = 14.0;

    /** Ticks accumulated since the last config refresh. */
    private int ticksSinceRefresh = CONFIG_REFRESH_TICKS; // trigger on first tick

    // -------------------------------------------------------------------------
    // Snapshot — captures live mine values for change detection.
    // -------------------------------------------------------------------------

    /**
     * Immutable record of the values last written to a hologram.
     * If the current values match this record, no {@code text()} call is made.
     */
    private record MineSnapshot(double remaining, int players, int caves, long resetSecs) {}

    private MineHologramManager() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Primes the config cache, starts the update task, and defers initial
     * entity spawning by 2 ticks so all worlds are fully available.
     */
    public void initialize()
    {
        if (!isEnabled()) return;

        refreshConfigCache();
        startUpdateTask();

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () ->
        {
            purgeOrphanedHolograms();
            for (Mine mine : MineManager.getInstance().getAllMines().values())
            {
                if (mine.hologram()) spawnHologram(mine);
            }
        }, 2L);
    }

    /**
     * Removes all PDC-tagged TextDisplay entities from every loaded world.
     * Runs at startup to eliminate holograms saved by the old
     * {@code setPersistent(true)} build before they duplicate fresh spawns.
     */
    private void purgeOrphanedHolograms()
    {
        for (World w : Bukkit.getWorlds())
        {
            for (org.bukkit.entity.Entity e : w.getEntities())
            {
                if (!(e instanceof TextDisplay td)) continue;
                if (td.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.STRING))
                {
                    td.remove();
                }
            }
        }
    }

    /** Cancels the update task and removes all hologram entities. */
    public void cleanup()
    {
        stopUpdateTask();
        removeAll();
    }

    // =========================================================================
    // Chunk-load hook
    // =========================================================================

    /**
     * Called by {@code MineListener.onChunkLoad} after the old-entity purge.
     *
     * <p>Spawns a hologram for any mine whose hologram position falls inside
     * {@code chunk}, provided no valid entity exists yet.  This is the primary
     * spawn path for holograms in chunks that were not loaded during
     * {@link #initialize()} and ensures entities are only ever created inside
     * already-player-loaded chunks (preventing the force-load/instant-unload
     * cycle that stops text from updating).</p>
     */
    public void onChunkLoad(@NotNull Chunk chunk)
    {
        if (!isEnabled()) return;

        for (Mine mine : MineManager.getInstance().getAllMines().values())
        {
            if (!mine.hologram()) continue;

            // Skip if a valid entity already exists
            TextDisplay existing = holograms.get(mine.name().toLowerCase());
            if (existing != null && existing.isValid()) continue;

            // Check whether this chunk contains the hologram spawn point
            Location loc = getHologramLocation(mine);
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(chunk.getWorld())) continue;
            if ((loc.getBlockX() >> 4) != chunk.getX()) continue;
            if ((loc.getBlockZ() >> 4) != chunk.getZ()) continue;

            spawnHologram(mine);
        }
    }

    // =========================================================================
    // Spawn / Remove
    // =========================================================================

    /**
     * Spawns a TextDisplay for the given mine.
     *
     * <p>If the hologram chunk is not currently loaded, this method returns
     * immediately — {@link #onChunkLoad(Chunk)} will trigger the spawn once a
     * player loads the chunk naturally.</p>
     */
    public void spawnHologram(@NotNull Mine mine)
    {
        if (!isEnabled()) return;

        removeHologram(mine.name()); // clean up any stale entry first

        Location loc = getHologramLocation(mine);
        if (loc == null || loc.getWorld() == null) return;

        // Never force-load a chunk just to spawn a hologram.
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        MineSnapshot snap = buildSnapshot(mine);

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td ->
        {
            td.text(buildText(mine, snap));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setVisibleByDefault(true);
            td.setPersistent(false); // never written to disk — no duplicate-on-restart
            td.getPersistentDataContainer()
              .set(PDC_KEY, PersistentDataType.STRING, mine.name().toLowerCase());
            td.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(cachedScale, cachedScale, cachedScale),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
        });

        holograms.put(mine.name().toLowerCase(), display);
        lastSnapshot.put(mine.name().toLowerCase(), snap); // seed snapshot to skip first redundant update
    }

    /** Removes the hologram entity and tracking state for a mine. */
    public void removeHologram(@NotNull String mineName)
    {
        TextDisplay display = holograms.remove(mineName.toLowerCase());
        lastSnapshot.remove(mineName.toLowerCase());
        if (display != null) display.remove();
    }

    /** Removes all hologram entities and clears all tracking state. */
    public void removeAll()
    {
        for (TextDisplay display : holograms.values())
        {
            if (display != null) display.remove();
        }
        holograms.clear();
        lastSnapshot.clear();
    }

    // =========================================================================
    // Periodic update (runs every 20 ticks on the main thread)
    // =========================================================================

    private void startUpdateTask()
    {
        if (updateTask != null) return;
        updateTask = Bukkit.getScheduler().runTaskTimer(
                Main.getInstance(), this::updateAll, 20L, 20L);
    }

    private void stopUpdateTask()
    {
        if (updateTask != null)
        {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Core update loop — runs every second.
     *
     * <ul>
     *   <li>Refreshes config cache every {@value #CONFIG_REFRESH_TICKS} ticks.</li>
     *   <li>For each tracked hologram: rebuilds text only when values changed.</li>
     *   <li>Re-spawns entities that have gone invalid (chunk unloaded), but only
     *       in naturally-loaded chunks.</li>
     *   <li>Catches mines that gained hologram=true after initialisation.</li>
     * </ul>
     */
    private void updateAll()
    {
        // Refresh config cache periodically (no file I/O on every tick)
        ticksSinceRefresh += 20;
        if (ticksSinceRefresh >= CONFIG_REFRESH_TICKS)
        {
            refreshConfigCache();
            ticksSinceRefresh = 0;
        }

        List<Mine> needsRespawn = new ArrayList<>();

        for (Map.Entry<String, TextDisplay> entry : holograms.entrySet())
        {
            String key     = entry.getKey();
            TextDisplay td = entry.getValue();

            Mine mine = MineManager.getInstance().getMine(key);
            if (mine == null)
            {
                if (td != null) td.remove();
                continue;
            }

            if (td == null || !td.isValid())
            {
                needsRespawn.add(mine);
                continue;
            }

            // Rebuild text only when something actually changed
            MineSnapshot current = buildSnapshot(mine);
            MineSnapshot last    = lastSnapshot.get(key);
            if (last == null || !last.equals(current))
            {
                td.text(buildText(mine, current));
                lastSnapshot.put(key, current);
            }
        }

        // Drop stale map entries
        holograms.entrySet().removeIf(e -> e.getValue() == null || !e.getValue().isValid());

        // Re-spawn entities that went invalid — only if chunk is already loaded
        for (Mine mine : needsRespawn)
        {
            if (mine.hologram()) spawnHologram(mine);
        }

        // Catch mines whose chunks became loaded since last tick
        // (e.g. new mine created, or world was slow to load at startup)
        for (Mine mine : MineManager.getInstance().getAllMines().values())
        {
            if (!mine.hologram()) continue;
            if (holograms.containsKey(mine.name().toLowerCase())) continue;

            Location loc = getHologramLocation(mine);
            if (loc == null || loc.getWorld() == null) continue;
            if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4))
            {
                spawnHologram(mine);
            }
        }
    }

    // =========================================================================
    // Text building
    // =========================================================================

    /** Reads the current live values for a mine into a snapshot record. */
    private @NotNull MineSnapshot buildSnapshot(@NotNull Mine mine)
    {
        return new MineSnapshot(
                MineManager.getInstance().getRemainingPercentage(mine.name()),
                MineManager.getInstance().getPlayerCount(mine.name()),
                CaveManager.getInstance().getActiveCaveCount(mine.name()),
                MineManager.getInstance().getSecondsUntilReset(mine.name())
        );
    }

    /**
     * Builds the hologram {@link Component} from the cached template,
     * substituting live values from the supplied snapshot.
     */
    private @NotNull Component buildText(@NotNull Mine mine, @NotNull MineSnapshot snap)
    {
        String resetTime;
        if (mine.resetInterval() <= 0)
        {
            resetTime = "--:--";
        }
        else
        {
            long min = snap.resetSecs() / 60;
            long sec = snap.resetSecs() % 60;
            resetTime = String.format("%d:%02d", min, sec);
        }

        List<String> lines = (cachedTemplateLines != null) ? cachedTemplateLines : defaultTemplate();

        String text = String.join("\n", lines)
                .replace("%mine%",             mine.name().toUpperCase())
                .replace("%remaining%",        String.format("%.1f", snap.remaining()))
                .replace("%reset_time%",       resetTime)
                .replace("%players%",          String.valueOf(snap.players()))
                .replace("%caves_discovered%", String.valueOf(snap.caves()))
                .replace("%caves_total%",      String.valueOf(mine.caveCount()));

        return LEGACY.deserialize(net.matrixcreations.libraries.MatrixColorAPI.process(text));
    }

    private static @NotNull List<String> defaultTemplate()
    {
        return List.of(
                "&#00FFFF&l⛏ %mine%",
                "",
                "&#888888Remaining  &#FFFFFF%remaining%%",
                "&#888888Reset  &#FFFFFF%reset_time%",
                "&#888888Miners  &#FFFFFF%players%",
                "&#888888Caves  &#FFFFFF%caves_discovered%&#888888/&#FFFFFF%caves_total%",
                "",
                "&#444444▼"
        );
    }

    // =========================================================================
    // Config cache
    // =========================================================================

    /**
     * Reads hologram-related config values into local fields.
     * Called once during {@link #initialize()} and then every
     * {@value #CONFIG_REFRESH_TICKS} ticks from the update loop.
     */
    private void refreshConfigCache()
    {
        FileConfiguration minesCfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
        if (minesCfg != null)
        {
            cachedScale   = (float) minesCfg.getDouble("defaults.hologram-scale",    4.0);
            cachedOffsetY =         minesCfg.getDouble("defaults.hologram-offset-y", 14.0);
        }

        FileConfiguration msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        if (msgCfg != null)
        {
            List<String> lines = msgCfg.getStringList("mine.hologram-template");
            cachedTemplateLines = lines.isEmpty() ? null : lines;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns the world-space location where the hologram should sit:
     * the horizontal centre of the mine at {@code maxY + offsetY}.
     *
     * <p>Public so other components (e.g. the cave-discovery animation) can
     * anchor relative to the mine hologram position.</p>
     */
    public @Nullable Location getHologramLocation(@NotNull Mine mine)
    {
        World world = Bukkit.getWorld(mine.world());
        if (world == null) return null;

        double cx = (mine.minX() + mine.maxX()) / 2.0;
        double cz = (mine.minZ() + mine.maxZ()) / 2.0;

        return new Location(world, cx, mine.maxY() + cachedOffsetY, cz);
    }

    private boolean isEnabled()
    {
        FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
        return cfg != null && cfg.getBoolean("defaults.hologram-enabled", true);
    }
}
