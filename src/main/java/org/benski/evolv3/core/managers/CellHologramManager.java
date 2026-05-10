package org.benski.evolv3.core.managers;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.managers.CellManager.CellDef;
import org.benski.evolv3.core.managers.CellRentManager.CellRent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Billboard TextDisplay holograms above each cell's spawn point.
 * Supports per-cell config (yOffset, line2, color, scale, billboard, facing, custom pos)
 * persisted to localdata/cell-hologram-settings.yml — same pattern as MineHologramManager.
 */
public final class CellHologramManager
{
    @Getter
    private static final CellHologramManager instance = new CellHologramManager();

    public static final NamespacedKey PDC_KEY = new NamespacedKey("evolv3", "cell_hologram");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final Map<String, String> COLOR_HEX = Map.of(
            "teal",   "&3",
            "blue",   "&#5555FF",
            "purple", "&#A020F0",
            "yellow", "&#FFFF32",
            "green",  "&#55FF55",
            "red",    "&#FF5555",
            "cyan",   "&#00FFFF",
            "white",  "&#FFFFFF"
    );

    /**
     * Per-cell hologram config.
     * billboard=true → CENTER (faces player); billboard=false → FIXED, uses facing yaw.
     * posX/posY/posZ/posWorld are null when no custom position is set.
     */
    private record HoloConfig(
            double  yOffset,
            String  line2,
            String  color,
            float   scale,
            boolean billboard,
            float   facing,
            @Nullable Double  posX,
            @Nullable Double  posY,
            @Nullable Double  posZ,
            @Nullable String  posWorld
    ) {
        static HoloConfig defaults() {
            return new HoloConfig(0.0, "", "teal", 1.5f, false, 0f, null, null, null, null);
        }

        boolean hasCustomPos() { return posX != null; }

        HoloConfig withYOffset(double y)          { return new HoloConfig(y, line2, color, scale, billboard, facing, posX, posY, posZ, posWorld); }
        HoloConfig withLine2(String l)            { return new HoloConfig(yOffset, l, color, scale, billboard, facing, posX, posY, posZ, posWorld); }
        HoloConfig withColor(String c)            { return new HoloConfig(yOffset, line2, c, scale, billboard, facing, posX, posY, posZ, posWorld); }
        HoloConfig withScale(float s)             { return new HoloConfig(yOffset, line2, color, s, billboard, facing, posX, posY, posZ, posWorld); }
        HoloConfig withBillboard(boolean b)       { return new HoloConfig(yOffset, line2, color, scale, b, facing, posX, posY, posZ, posWorld); }
        HoloConfig withFacing(float f)            { return new HoloConfig(yOffset, line2, color, scale, billboard, f, posX, posY, posZ, posWorld); }
        HoloConfig withPos(double x, double y, double z, String world) {
            return new HoloConfig(yOffset, line2, color, scale, billboard, facing, x, y, z, world);
        }
        HoloConfig withoutPos() {
            return new HoloConfig(yOffset, line2, color, scale, billboard, facing, null, null, null, null);
        }
    }

    private final Map<String, TextDisplay>  holograms    = new HashMap<>();
    private final Map<String, Interaction>  interactions = new HashMap<>();
    private final Map<String, HoloConfig>   holoConfigs  = new HashMap<>();
    private BukkitTask updateTask;

    private CellHologramManager() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize() {
        loadConfigs();
        purgeOrphans();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), this::spawnAll, 2L);
        startUpdateTask();
    }

    public void cleanup() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        for (TextDisplay td : holograms.values()) if (td != null) td.remove();
        holograms.clear();
        for (Interaction ia : interactions.values()) if (ia != null) ia.remove();
        interactions.clear();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void refresh(@NotNull String cellName) {
        CellDef def = CellManager.getInstance().get(cellName);
        if (def == null) return;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> spawnHologram(def));
    }

    // =========================================================================
    // Config setters
    // =========================================================================

    public void setYOffset(@NotNull String cellName, double yOffset) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withYOffset(yOffset));
        saveConfigs();
        refresh(key);
    }

    public void setLine2(@NotNull String cellName, @NotNull String line2) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withLine2(line2));
        saveConfigs();
        CellDef def = CellManager.getInstance().get(key);
        if (def == null) return;
        TextDisplay td = holograms.get(key);
        if (td != null && td.isValid()) td.text(buildText(def, getConfig(key)));
    }

    public void setColor(@NotNull String cellName, @NotNull String color) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withColor(color));
        saveConfigs();
        CellDef def = CellManager.getInstance().get(key);
        if (def == null) return;
        TextDisplay td = holograms.get(key);
        if (td != null && td.isValid()) td.text(buildText(def, getConfig(key)));
    }

    public void setScale(@NotNull String cellName, float scale) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withScale(scale));
        saveConfigs();
        refresh(key);
    }

    /** Sets billboard mode. true = CENTER (faces player), false = FIXED (uses facing yaw). */
    public void setBillboard(@NotNull String cellName, boolean facesPlayer) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withBillboard(facesPlayer));
        saveConfigs();
        refresh(key);
    }

    /** Sets the facing yaw for FIXED billboard mode. Only visible when billboard=false. */
    public void setFacing(@NotNull String cellName, float yaw) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withFacing(yaw));
        saveConfigs();
        refresh(key);
    }

    public void setPos(@NotNull String cellName, double x, double y, double z, @NotNull String world) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withPos(x, y, z, world));
        saveConfigs();
        refresh(key);
    }

    public void clearPos(@NotNull String cellName) {
        String key = cellName.toLowerCase();
        holoConfigs.put(key, getConfig(key).withoutPos());
        saveConfigs();
        refresh(key);
    }

    public void resetConfig(@NotNull String cellName) {
        String key = cellName.toLowerCase();
        holoConfigs.remove(key);
        saveConfigs();
        refresh(key);
    }

    public static Map<String, String> getColorOptions() { return COLOR_HEX; }

    // =========================================================================
    // Internal spawn/remove
    // =========================================================================

    private void spawnAll() {
        for (CellDef def : CellManager.getInstance().getAll()) spawnHologram(def);
    }

    private void spawnHologram(@NotNull CellDef def) {
        removeHologram(def.name());
        // Sweep for any orphaned entities from a previous crash
        World sweepWorld = Bukkit.getWorld(def.world());
        if (sweepWorld != null) {
            String key = def.name().toLowerCase();
            sweepWorld.getEntitiesByClass(TextDisplay.class).stream()
                    .filter(e -> key.equals(e.getPersistentDataContainer().get(PDC_KEY, PersistentDataType.STRING)))
                    .forEach(org.bukkit.entity.Entity::remove);
            sweepWorld.getEntitiesByClass(Interaction.class).stream()
                    .filter(e -> key.equals(e.getPersistentDataContainer().get(PDC_KEY, PersistentDataType.STRING)))
                    .forEach(org.bukkit.entity.Entity::remove);
        }

        HoloConfig cfg = getConfig(def.name());
        Location loc = resolveLocation(def, cfg);
        if (loc == null) return;

        float yaw = cfg.hasCustomPos() ? cfg.facing() : defaultFacing(def.upgradeDirection());
        if (!cfg.billboard()) loc.setYaw(yaw);

        Display.Billboard bb = cfg.billboard() ? Display.Billboard.CENTER : Display.Billboard.FIXED;
        float scale = cfg.scale();
        String cellKey = def.name().toLowerCase();

        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.getPersistentDataContainer().set(PDC_KEY, PersistentDataType.STRING, cellKey);
            d.setBillboard(bb);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setTextOpacity((byte) 230);
            d.setBackgroundColor(Color.fromARGB(100, 0, 0, 0));
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setPersistent(false);
            d.text(buildText(def, cfg));
            if (scale != 1.0f) {
                d.setTransformation(new org.bukkit.util.Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
            }
        });
        holograms.put(cellKey, td);

        Location interactionLoc = loc.clone();
        interactionLoc.setY(loc.getY() - 1.0); // position below hologram so hitbox covers it upward
        Interaction ia = loc.getWorld().spawn(interactionLoc, Interaction.class, i -> {
            i.getPersistentDataContainer().set(PDC_KEY, PersistentDataType.STRING, cellKey);
            i.setInteractionWidth(2.0f);
            i.setInteractionHeight(3.0f);
            i.setResponsive(false);
            i.setPersistent(false);
        });
        interactions.put(cellKey, ia);
    }

    private @Nullable Location resolveLocation(@NotNull CellDef def, @NotNull HoloConfig cfg) {
        if (cfg.hasCustomPos()) {
            World world = Bukkit.getWorld(cfg.posWorld() != null ? cfg.posWorld() : def.world());
            if (world == null) return null;
            return new Location(world, cfg.posX(), cfg.posY(), cfg.posZ());
        }
        World world = Bukkit.getWorld(def.world());
        if (world == null) return null;

        int x1 = Math.min(def.x1(), def.x2()), x2 = Math.max(def.x1(), def.x2());
        int y1 = Math.min(def.y1(), def.y2()), y2 = Math.max(def.y1(), def.y2());
        int z1 = Math.min(def.z1(), def.z2()), z2 = Math.max(def.z1(), def.z2());

        // Floor midpoint matches user's expected Y (e.g. y1=65,y2=72 → 68)
        double midY = (int) ((y1 + y2) / 2.0) + cfg.yOffset();
        double cx   = x1 + (x2 - x1) / 2.0 + 0.5;

        // Hologram goes on the ENTRANCE side — opposite of the expansion wall.
        // E expansion → wall east, entrance west → hologram at x1-2 near south corner
        // W expansion → wall west, entrance east → hologram at x2+2 near south corner
        // N expansion → wall north, entrance south → hologram at z2+2 centered
        // S expansion → wall south, entrance north → hologram at z1-2 centered
        return switch (def.upgradeDirection()) {
            case "E" -> new Location(world, x1 - 1.3, midY, z2 + 2.5);
            case "W" -> new Location(world, x2 + 1.3, midY, z2 + 2.5);
            case "N" -> new Location(world, cx,        midY, z2 + 2.5);
            case "S" -> new Location(world, cx,        midY, z1 - 2.5);
            default  -> new Location(world, cx,        midY, z2 + 2.5);
        };
    }

    private void removeHologram(@NotNull String cellName) {
        String key = cellName.toLowerCase();
        TextDisplay td = holograms.remove(key);
        if (td != null) td.remove();
        Interaction ia = interactions.remove(key);
        if (ia != null) ia.remove();
    }

    private void purgeOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.STRING)
                        && (e instanceof TextDisplay || e instanceof Interaction)) {
                    e.remove();
                }
            }
        }
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (CellDef def : CellManager.getInstance().getAll()) {
                String key = def.name().toLowerCase();
                TextDisplay td = holograms.get(key);
                Interaction ia = interactions.get(key);
                if (td == null || !td.isValid() || ia == null || !ia.isValid()) {
                    spawnHologram(def);
                } else if (CellRentManager.getInstance().isRented(key)) {
                    td.text(buildText(def, getConfig(key)));
                }
            }
            holograms.entrySet().removeIf(e -> {
                if (CellManager.getInstance().get(e.getKey()) == null) {
                    if (e.getValue() != null) e.getValue().remove();
                    Interaction ia = interactions.remove(e.getKey());
                    if (ia != null) ia.remove();
                    return true;
                }
                return false;
            });
        }, 100L, 100L);
    }

    // =========================================================================
    // Text building
    // =========================================================================

    private @NotNull Component buildText(@NotNull CellDef def, @NotNull HoloConfig cfg) {
        String colorHex = COLOR_HEX.getOrDefault(cfg.color(), "&3");
        CellRent rent = CellRentManager.getInstance().getRent(def.name());
        String statusBlock;
        if (rent == null) {
            statusBlock = "&#55FF55✔ Available\n&#AAAAAA Right-click";
        } else {
            statusBlock = "&#FF5555Rented by:\n&#FFFFFF" + rent.ownerName()
                    + "\n&#888888" + formatTime(rent.rentExpiry());
        }
        String line2 = cfg.line2().isBlank() ? statusBlock : cfg.line2();
        String text = colorHex + "&l" + toDisplayName(def.name()) + "\n" + line2;
        return deserialize(text);
    }

    private static String formatTime(long rentExpiry) {
        if (rentExpiry <= 0) return "Permanent";
        long ms = rentExpiry - System.currentTimeMillis();
        if (ms <= 0) return "Expired";
        long totalSec = ms / 1000;
        long days    = totalSec / 86400;
        long hours   = (totalSec % 86400) / 3600;
        long minutes = (totalSec % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private static Component deserialize(String text) {
        text = org.benski.evolv3.core.helpers.FontUtils.toSmallCaps(text);
        text = MatrixColorAPI.process(text);
        text = text.replace('&', '§');
        return LEGACY.deserialize(text);
    }

    /** Capitalizes the first letter after each '-' or '_' separator and at position 0. */
    private static String toDisplayName(String name) {
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_') { sb.append(c); cap = true; }
            else if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Returns the yaw (degrees) the hologram should face — toward players at the entrance (opposite the expansion wall). */
    private static float defaultFacing(String upgradeDirection) {
        return switch (upgradeDirection) {
            case "E" -> 90f;   // entrance is west, hologram faces west
            case "W" -> 270f;  // entrance is east, hologram faces east
            case "N" -> 0f;    // entrance is south, hologram faces south
            case "S" -> 180f;  // entrance is north, hologram faces north
            default  -> 0f;
        };
    }

    // =========================================================================
    // Config persistence
    // =========================================================================

    private @NotNull HoloConfig getConfig(@NotNull String cellName) {
        HoloConfig c = holoConfigs.get(cellName.toLowerCase());
        return c != null ? c : HoloConfig.defaults();
    }

    private void loadConfigs() {
        holoConfigs.clear();
        File file = configFile();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("cells");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            String p = "cells." + name;
            double  offset    = cfg.getDouble (p + ".y-offset",  2.5);
            String  line2     = cfg.getString (p + ".line2",     "");
            String  color     = cfg.getString (p + ".color",     "blue");
            float   scale     = (float) cfg.getDouble(p + ".scale", 1.0);
            boolean billboard = cfg.getBoolean(p + ".billboard", true);
            float   facing    = (float) cfg.getDouble(p + ".facing", 0.0);
            Double  posX      = cfg.isSet(p + ".pos.x") ? cfg.getDouble(p + ".pos.x") : null;
            Double  posY      = cfg.isSet(p + ".pos.y") ? cfg.getDouble(p + ".pos.y") : null;
            Double  posZ      = cfg.isSet(p + ".pos.z") ? cfg.getDouble(p + ".pos.z") : null;
            String  posWorld  = cfg.isSet(p + ".pos.world") ? cfg.getString(p + ".pos.world") : null;
            holoConfigs.put(name.toLowerCase(),
                    new HoloConfig(offset, line2, color, scale, billboard, facing, posX, posY, posZ, posWorld));
        }
    }

    private void saveConfigs() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, HoloConfig> entry : holoConfigs.entrySet()) {
            String p = "cells." + entry.getKey();
            HoloConfig c = entry.getValue();
            cfg.set(p + ".y-offset",  c.yOffset());
            cfg.set(p + ".line2",     c.line2());
            cfg.set(p + ".color",     c.color());
            cfg.set(p + ".scale",     c.scale());
            cfg.set(p + ".billboard", c.billboard());
            cfg.set(p + ".facing",    c.facing());
            if (c.hasCustomPos()) {
                cfg.set(p + ".pos.x",     c.posX());
                cfg.set(p + ".pos.y",     c.posY());
                cfg.set(p + ".pos.z",     c.posZ());
                cfg.set(p + ".pos.world", c.posWorld());
            }
        }
        try { cfg.save(configFile()); }
        catch (IOException e) {
            Bukkit.getLogger().severe("[Evolv3] Failed to save cell hologram configs: " + e.getMessage());
        }
    }

    private @NotNull File configFile() {
        File dir = new File(Main.getInstance().getDataFolder(), "localdata");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cell-hologram-settings.yml");
    }
}
