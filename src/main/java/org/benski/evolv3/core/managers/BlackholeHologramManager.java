package org.benski.evolv3.core.managers;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.managers.BlackholeManager.Blackhole;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages floating TextDisplay holograms above each blackhole.
 *
 * Line 1: fixed "⚫ Blackhole" header in configurable colour.
 * Line 2: customizable text, defaults to the blackhole name.
 * Bottom: decorative ▼ arrow matching the mine hologram style.
 *
 * Entities are non-persistent and are respawned every 5 seconds
 * if they go invalid (chunk unloads, etc.).
 */
public final class BlackholeHologramManager
{
    @Getter
    private static final BlackholeHologramManager instance = new BlackholeHologramManager();

    private record HoloConfig(double yOffset, String line2, String color, float scale)
    {
        static HoloConfig defaults(String bhName)
        {
            return new HoloConfig(5.0, bhName, "purple", 2.0f);
        }
        HoloConfig withYOffset(double y) { return new HoloConfig(y, line2, color, scale); }
        HoloConfig withLine2(String l)   { return new HoloConfig(yOffset, l, color, scale); }
        HoloConfig withColor(String c)   { return new HoloConfig(yOffset, line2, c, scale); }
        HoloConfig withScale(float s)    { return new HoloConfig(yOffset, line2, color, s); }
    }

    private static final Map<String, String> COLOR_HEX = Map.of(
            "purple", "&#A020F0",
            "yellow", "&#FFFF32",
            "green",  "&#32FF64",
            "red",    "&#FF3232",
            "blue",   "&#32DCFF",
            "white",  "&#FFFFFF",
            "cyan",   "&#00FFFF"
    );

    public static final NamespacedKey PDC_KEY =
            new NamespacedKey("evolv3", "blackhole_hologram");

    private final Map<String, TextDisplay> holograms   = new HashMap<>();
    private final Map<String, HoloConfig>  holoConfigs = new HashMap<>();
    private BukkitTask updateTask;

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private BlackholeHologramManager() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize()
    {
        loadConfigs();
        startUpdateTask();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () ->
        {
            purgeOrphans();
            for (Blackhole bh : BlackholeManager.getInstance().getAll())
                spawnHologram(bh);
        }, 2L);
    }

    private void purgeOrphans()
    {
        for (World w : Bukkit.getWorlds())
            for (org.bukkit.entity.Entity e : w.getEntities())
                if (e instanceof TextDisplay td &&
                    td.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.STRING))
                    td.remove();
    }

    public void cleanup()
    {
        stopUpdateTask();
        for (TextDisplay td : holograms.values())
            if (td != null) td.remove();
        holograms.clear();
    }

    // =========================================================================
    // Spawn / Remove / Refresh
    // =========================================================================

    public void spawnHologram(@NotNull Blackhole bh)
    {
        removeHologram(bh.name());

        World world = Bukkit.getWorld(bh.world());
        if (world == null) return;

        HoloConfig cfg = getConfig(bh.name());
        Location   loc = new Location(world, bh.cx(), bh.cy() + cfg.yOffset(), bh.cz());

        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        float scale = cfg.scale();
        TextDisplay display = world.spawn(loc, TextDisplay.class, td ->
        {
            td.text(buildText(bh, cfg));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setVisibleByDefault(true);
            td.setPersistent(false);
            td.getPersistentDataContainer()
              .set(PDC_KEY, PersistentDataType.STRING, bh.name().toLowerCase());
            td.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
        });
        holograms.put(bh.name().toLowerCase(), display);
    }

    public void removeHologram(@NotNull String name)
    {
        TextDisplay td = holograms.remove(name.toLowerCase());
        if (td != null) td.remove();
    }

    public void refresh(@NotNull String name)
    {
        Blackhole bh = BlackholeManager.getInstance().get(name);
        if (bh != null) spawnHologram(bh);
        else            removeHologram(name);
    }

    // =========================================================================
    // Update task — every 5 seconds
    // =========================================================================

    private void startUpdateTask()
    {
        if (updateTask != null) return;
        updateTask = Bukkit.getScheduler().runTaskTimer(
                Main.getInstance(), this::updateAll, 100L, 100L);
    }

    private void stopUpdateTask()
    {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
    }

    private void updateAll()
    {
        // Remove holograms whose blackhole was deleted
        holograms.entrySet().removeIf(entry ->
        {
            if (!BlackholeManager.getInstance().exists(entry.getKey()))
            {
                if (entry.getValue() != null) entry.getValue().remove();
                return true;
            }
            return false;
        });

        // Respawn entities that went invalid (e.g. chunk unloaded)
        for (Map.Entry<String, TextDisplay> entry : new ArrayList<>(holograms.entrySet()))
        {
            if (entry.getValue() == null || !entry.getValue().isValid())
            {
                Blackhole bh = BlackholeManager.getInstance().get(entry.getKey());
                if (bh != null) spawnHologram(bh);
            }
        }
        holograms.entrySet().removeIf(e -> e.getValue() == null || !e.getValue().isValid());

        // Spawn for blackholes that don't yet have a hologram (chunk just loaded, newly created)
        for (Blackhole bh : BlackholeManager.getInstance().getAll())
        {
            String key = bh.name().toLowerCase();
            TextDisplay existing = holograms.get(key);
            if (existing != null && existing.isValid()) continue;
            spawnHologram(bh);
        }
    }

    // =========================================================================
    // Text building
    // =========================================================================

    private @NotNull Component buildText(@NotNull Blackhole bh, @NotNull HoloConfig cfg)
    {
        String colorHex = COLOR_HEX.getOrDefault(cfg.color(), "&#A020F0");
        String line2    = cfg.line2().isEmpty() ? bh.name() : cfg.line2();
        String text     = colorHex + "&l⚫ Blackhole\n"
                        + "&#FFFFFF" + line2 + "\n"
                        + "\n"
                        + "&#444444▼";
        text = net.matrixcreations.libraries.MatrixColorAPI.process(text);
        return LEGACY.deserialize(text);
    }

    // =========================================================================
    // Config setters
    // =========================================================================

    public void setYOffset(@NotNull String name, double yOffset)
    {
        String key = name.toLowerCase();
        holoConfigs.put(key, getConfig(key).withYOffset(yOffset));
        saveConfigs();
        refresh(key);
    }

    public void setLine2(@NotNull String name, @NotNull String line2)
    {
        String key = name.toLowerCase();
        holoConfigs.put(key, getConfig(key).withLine2(line2));
        saveConfigs();
        Blackhole bh = BlackholeManager.getInstance().get(key);
        if (bh == null) return;
        TextDisplay td = holograms.get(key);
        if (td != null && td.isValid()) td.text(buildText(bh, getConfig(key)));
    }

    public void setColor(@NotNull String name, @NotNull String color)
    {
        String key = name.toLowerCase();
        holoConfigs.put(key, getConfig(key).withColor(color));
        saveConfigs();
        Blackhole bh = BlackholeManager.getInstance().get(key);
        if (bh == null) return;
        TextDisplay td = holograms.get(key);
        if (td != null && td.isValid()) td.text(buildText(bh, getConfig(key)));
    }

    public void setScale(@NotNull String name, float scale)
    {
        String key = name.toLowerCase();
        holoConfigs.put(key, getConfig(key).withScale(scale));
        saveConfigs();
        refresh(key);
    }

    public void resetConfig(@NotNull String name)
    {
        String key = name.toLowerCase();
        holoConfigs.remove(key);
        saveConfigs();
        Blackhole bh = BlackholeManager.getInstance().get(key);
        if (bh != null) spawnHologram(bh);
        else            removeHologram(key);
    }

    private @NotNull HoloConfig getConfig(@NotNull String name)
    {
        HoloConfig c = holoConfigs.get(name.toLowerCase());
        if (c != null) return c;
        Blackhole bh = BlackholeManager.getInstance().get(name.toLowerCase());
        return HoloConfig.defaults(bh != null ? bh.name() : name.toLowerCase());
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    private void loadConfigs()
    {
        holoConfigs.clear();
        File file = configFile();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("blackholes");
        if (sec == null) return;
        for (String name : sec.getKeys(false))
        {
            String path   = "blackholes." + name;
            double offset = cfg.getDouble(path + ".y-offset", 5.0);
            String line2  = cfg.getString(path + ".line2",    "");
            String color  = cfg.getString(path + ".color",    "purple");
            float  scale  = (float) cfg.getDouble(path + ".scale", 2.0);
            holoConfigs.put(name.toLowerCase(), new HoloConfig(offset, line2, color, scale));
        }
    }

    private void saveConfigs()
    {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, HoloConfig> entry : holoConfigs.entrySet())
        {
            String path  = "blackholes." + entry.getKey();
            HoloConfig c = entry.getValue();
            cfg.set(path + ".y-offset", c.yOffset());
            cfg.set(path + ".line2",    c.line2());
            cfg.set(path + ".color",    c.color());
            cfg.set(path + ".scale",    c.scale());
        }
        try { cfg.save(configFile()); }
        catch (IOException e)
        {
            Bukkit.getLogger().severe("[Evolv3] Failed to save blackhole hologram configs: " + e.getMessage());
        }
    }

    private @NotNull File configFile()
    {
        File dir = new File(Main.getInstance().getDataFolder(), "localdata");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "blackhole-hologram-settings.yml");
    }
}
