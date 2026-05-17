package org.benski.evolv3.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.managers.SpawnerManager.Spawner;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Manages floating {@link TextDisplay} holograms above each custom spawner.
 *
 * <p>Mirrors {@link MineHologramManager}: holograms use {@code setPersistent(false)}, orphaned
 * instances are purged via PDC tag, text is only rebuilt on snapshot change, and hologram
 * appearance is fully configurable per-spawner in-game.
 */
public final class SpawnerHologramManager {
  @Getter private static final SpawnerHologramManager instance = new SpawnerHologramManager();

  /**
   * PDC key applied to every hologram entity for orphan detection. Public so {@link
   * org.benski.evolv3.listeners.SpawnerListener} can reference it.
   */
  public static final NamespacedKey PDC_KEY = new NamespacedKey("evolv3", "spawner_hologram");

  /** Active holograms, keyed by spawner id. */
  private final Map<String, TextDisplay> holograms = new HashMap<>();

  /** Last-rendered values per spawner, used for change detection. */
  private final Map<String, MobSnapshot> lastSnapshot = new HashMap<>();

  /** Per-spawner hologram customization (position, scale, billboard, etc.). */
  private final Map<String, HologramConfig> holoConfigs = new HashMap<>();

  /** Periodic update task — null when not running. */
  private BukkitTask updateTask;

  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  /** Hex colour codes for beam colour presets (matches BeamManager). */
  private static final Map<String, String> COLOR_HEX =
      Map.of(
          "purple", "&#A020F0",
          "yellow", "&#FFFF32",
          "green", "&#32FF64",
          "red", "&#FF3232",
          "blue", "&#32DCFF");

  /**
   * Per-spawner hologram customization settings.
   *
   * @param customPos whether x/y/z override the auto-calculated position
   * @param x custom world X (only used when customPos is true)
   * @param y custom world Y
   * @param z custom world Z
   * @param scale text scale; -1 falls back to the global default
   * @param billboard true = CENTER (faces player), false = FIXED (static)
   * @param facing entity yaw applied when billboard is false
   * @param color beam colour name for the title line, or "" for default
   * @param showArrow whether to include the decorative ▼ arrow line
   * @param icon icon character(s) shown before the spawner name; empty = use global default
   */
  private record HologramConfig(
      boolean customPos,
      double x,
      double y,
      double z,
      float scale,
      boolean billboard,
      float facing,
      String color,
      boolean showArrow,
      String icon) {
    static HologramConfig defaults() {
      return new HologramConfig(false, 0, 0, 0, -1f, true, 0f, "", true, "");
    }
  }

  /**
   * Immutable snapshot of the values last written to a hologram.
   *
   * @param hunters number of players currently inside the spawner's region
   * @param spawnSecs seconds until the next spawn attempt
   * @param remaining current count of mobs of this type inside the spawner's region
   */
  private record MobSnapshot(int hunters, long spawnSecs, int remaining) {}

  private List<String> cachedTemplateLines = null;
  private float cachedScale = 3.0f;
  private double cachedOffsetY = 3.5;
  private String cachedDefaultIcon = "⚔";

  private SpawnerHologramManager() {}

  public void initialize() {
    refreshConfigCache();
    loadHologramConfigs();

    Bukkit.getScheduler()
        .runTaskLater(
            Main.getInstance(),
            () -> {
              purgeOrphanedHolograms();
              for (Spawner s : SpawnerManager.getInstance().getAllSpawners().values()) {
                spawnHologram(s);
              }
              startUpdateTask();
            },
            2L);
  }

  public void cleanup() {
    stopUpdateTask();
    removeAll();
  }

  public void spawnHologram(@NotNull Spawner s) {
    removeHologram(s.id());

    Location loc = getHologramLocation(s);
    if (loc == null || loc.getWorld() == null) return;

    if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

    HologramConfig config = holoConfigs.getOrDefault(s.id(), HologramConfig.defaults());
    float effectiveScale = config.scale() > 0 ? config.scale() : cachedScale;
    Display.Billboard effectiveBillboard =
        config.billboard() ? Display.Billboard.CENTER : Display.Billboard.FIXED;

    if (!config.billboard()) loc.setYaw(config.facing());

    MobSnapshot snap = buildSnapshot(s);

    TextDisplay display =
        loc.getWorld()
            .spawn(
                loc,
                TextDisplay.class,
                td -> {
                  td.text(buildText(s, snap));
                  td.setBillboard(effectiveBillboard);
                  td.setShadowed(true);
                  td.setAlignment(TextDisplay.TextAlignment.CENTER);
                  td.setSeeThrough(false);
                  td.setDefaultBackground(false);
                  td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                  td.setVisibleByDefault(true);
                  td.setPersistent(false);
                  td.getPersistentDataContainer().set(PDC_KEY, PersistentDataType.STRING, s.id());
                  td.setTransformation(
                      new org.bukkit.util.Transformation(
                          new Vector3f(0, 0, 0),
                          new AxisAngle4f(0, 0, 0, 1),
                          new Vector3f(effectiveScale, effectiveScale, effectiveScale),
                          new AxisAngle4f(0, 0, 0, 1)));
                });

    holograms.put(s.id(), display);
    lastSnapshot.put(s.id(), snap);
  }

  public void removeHologram(@NotNull String id) {
    TextDisplay td = holograms.remove(id);
    lastSnapshot.remove(id);
    if (td != null) td.remove();
  }

  public void removeAll() {
    for (TextDisplay td : holograms.values()) {
      if (td != null) td.remove();
    }
    holograms.clear();
    lastSnapshot.clear();
  }

  private void startUpdateTask() {
    if (updateTask != null) return;
    updateTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::updateAll, 20L, 20L);
  }

  private void stopUpdateTask() {
    if (updateTask != null) {
      updateTask.cancel();
      updateTask = null;
    }
  }

  private void updateAll() {
    List<Spawner> needsRespawn = new ArrayList<>();

    for (Map.Entry<String, TextDisplay> entry : holograms.entrySet()) {
      String id = entry.getKey();
      TextDisplay td = entry.getValue();

      Spawner s = SpawnerManager.getInstance().getAllSpawners().get(id);
      if (s == null) {
        if (td != null) td.remove();
        continue;
      }

      if (td == null || !td.isValid()) {
        needsRespawn.add(s);
        continue;
      }

      MobSnapshot current = buildSnapshot(s);
      MobSnapshot last = lastSnapshot.get(id);
      if (last == null || !last.equals(current)) {
        td.text(buildText(s, current));
        lastSnapshot.put(id, current);
      }
    }

    holograms.entrySet().removeIf(e -> e.getValue() == null || !e.getValue().isValid());

    for (Spawner s : needsRespawn) {
      spawnHologram(s);
    }

    for (Spawner s : SpawnerManager.getInstance().getAllSpawners().values()) {
      if (holograms.containsKey(s.id())) continue;

      Location loc = getHologramLocation(s);
      if (loc == null || loc.getWorld() == null) continue;

      if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
        spawnHologram(s);
      }
    }
  }

  public void purgeOrphanedHolograms() {
    for (World w : Bukkit.getWorlds()) {
      for (org.bukkit.entity.Entity e : w.getEntities()) {
        if (!(e instanceof TextDisplay td)) continue;
        if (td.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.STRING)) {
          td.remove();
        }
      }
    }
  }

  private @NotNull MobSnapshot buildSnapshot(@NotNull Spawner s) {
    return new MobSnapshot(
        SpawnerManager.getInstance().countPlayersInRegion(s),
        SpawnerManager.getInstance().getSecondsUntilSpawn(s.id()),
        SpawnerManager.getInstance().countNearbyMobs(s));
  }

  public @NotNull Component buildText(@NotNull Spawner s, @NotNull MobSnapshot snap) {
    HologramConfig config = holoConfigs.getOrDefault(s.id(), HologramConfig.defaults());
    String nameColor = COLOR_HEX.getOrDefault(config.color(), "&#FF5555");
    String icon = config.icon().isEmpty() ? cachedDefaultIcon : config.icon();

    List<String> lines =
        new ArrayList<>(cachedTemplateLines != null ? cachedTemplateLines : defaultTemplate());

    if (!config.showArrow()) lines.removeIf(l -> l.contains("▼"));

    long totalSecs = snap.spawnSecs();
    long min = totalSecs / 60;
    long sec = totalSecs % 60;
    String spawnTime =
        snap.remaining() >= s.mobLimit() ? "&c&lFULL" : String.format("%d:%02d", min, sec);

    String raw =
        String.join("\n", lines)
            .replace("%mob_color%", nameColor)
            .replace("%icon%", icon)
            .replace("%name%", s.spawnerName())
            .replace("%hunters%", String.valueOf(snap.hunters()))
            .replace("%remaining%", String.valueOf(snap.remaining()))
            .replace("%spawn_time%", spawnTime);

    raw = FontUtils.toSmallCaps(raw);
    raw = net.matrixcreations.libraries.MatrixColorAPI.process(raw);
    return LEGACY.deserialize(raw);
  }

  private static @NotNull List<String> defaultTemplate() {
    return List.of(
        "%mob_color%&l%icon% %name%",
        "",
        "&#888888Remaining  &#FFFFFF%remaining%",
        "&#888888Spawning in  &#FFFFFF%spawn_time%",
        "&#888888Hunters    &#FFFFFF%hunters%",
        "",
        "&#444444▼");
  }

  public @Nullable Location getHologramLocation(@NotNull Spawner s) {
    World world = Bukkit.getWorld(s.world());
    if (world == null) return null;

    HologramConfig config = holoConfigs.get(s.id());
    if (config != null && config.customPos()) {
      return new Location(world, config.x(), config.y(), config.z());
    }

    return new Location(world, s.x() + 0.5, s.y() + cachedOffsetY, s.z() + 0.5);
  }

  public void setPosition(@NotNull String id, double x, double y, double z) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            true,
            x,
            y,
            z,
            cur.scale(),
            cur.billboard(),
            cur.facing(),
            cur.color(),
            cur.showArrow(),
            cur.icon()));
    saveHologramConfigs();
    respawnIfActive(id);
  }

  public void resetPosition(@NotNull String id) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            false,
            0,
            0,
            0,
            cur.scale(),
            cur.billboard(),
            cur.facing(),
            cur.color(),
            cur.showArrow(),
            cur.icon()));
    saveHologramConfigs();
    respawnIfActive(id);
  }

  public void setScale(@NotNull String id, float scale) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            cur.customPos(),
            cur.x(),
            cur.y(),
            cur.z(),
            scale,
            cur.billboard(),
            cur.facing(),
            cur.color(),
            cur.showArrow(),
            cur.icon()));
    saveHologramConfigs();
    respawnIfActive(id);
  }

  public void setBillboard(@NotNull String id, boolean facesPlayer) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            cur.customPos(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            facesPlayer,
            cur.facing(),
            cur.color(),
            cur.showArrow(),
            cur.icon()));
    saveHologramConfigs();
    respawnIfActive(id);
  }

  public void setFacing(@NotNull String id, float yaw) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            cur.customPos(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            cur.billboard(),
            yaw,
            cur.color(),
            cur.showArrow(),
            cur.icon()));
    saveHologramConfigs();
    respawnIfActive(id);
  }

  public void setColor(@NotNull String id, @NotNull String colorName) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            cur.customPos(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            cur.billboard(),
            cur.facing(),
            colorName,
            cur.showArrow(),
            cur.icon()));
    saveHologramConfigs();
    Spawner s = SpawnerManager.getInstance().getAllSpawners().get(id);
    if (s != null) {
      TextDisplay td = holograms.get(id);
      if (td != null && td.isValid()) {
        td.text(buildText(s, buildSnapshot(s)));
        lastSnapshot.remove(id);
      }
    }
  }

  public void setShowArrow(@NotNull String id, boolean show) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            cur.customPos(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            cur.billboard(),
            cur.facing(),
            cur.color(),
            show,
            cur.icon()));
    saveHologramConfigs();
    Spawner s = SpawnerManager.getInstance().getAllSpawners().get(id);
    if (s != null) {
      TextDisplay td = holograms.get(id);
      if (td != null && td.isValid()) {
        td.text(buildText(s, buildSnapshot(s)));
        lastSnapshot.remove(id);
      }
    }
  }

  /** Sets the icon shown before the spawner name in the hologram title. */
  public void setIcon(@NotNull String id, @NotNull String icon) {
    HologramConfig cur = holoConfigs.getOrDefault(id, HologramConfig.defaults());
    holoConfigs.put(
        id,
        new HologramConfig(
            cur.customPos(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            cur.billboard(),
            cur.facing(),
            cur.color(),
            cur.showArrow(),
            icon));
    saveHologramConfigs();
    Spawner s = SpawnerManager.getInstance().getAllSpawners().get(id);
    if (s != null) {
      TextDisplay td = holograms.get(id);
      if (td != null && td.isValid()) {
        td.text(buildText(s, buildSnapshot(s)));
        lastSnapshot.remove(id);
      }
    }
  }

  private void respawnIfActive(@NotNull String id) {
    Spawner s = SpawnerManager.getInstance().getAllSpawners().get(id);
    if (s != null) {
      removeHologram(id);
      spawnHologram(s);
    }
  }

  public void onChunkLoad(@NotNull org.bukkit.Chunk chunk) {
    for (Spawner s : SpawnerManager.getInstance().getAllSpawners().values()) {
      TextDisplay existing = holograms.get(s.id());
      if (existing != null && existing.isValid()) continue;

      Location loc = getHologramLocation(s);
      if (loc == null || loc.getWorld() == null) continue;
      if (!loc.getWorld().equals(chunk.getWorld())) continue;
      if ((loc.getBlockX() >> 4) != chunk.getX()) continue;
      if ((loc.getBlockZ() >> 4) != chunk.getZ()) continue;

      spawnHologram(s);
    }
  }

  private void refreshConfigCache() {
    FileConfiguration spawnersCfg = ConfigRegistry.getInstance().get(ConfigNames.SPAWNERS);
    if (spawnersCfg != null) {
      cachedScale = (float) spawnersCfg.getDouble("defaults.hologram-scale", 3.0);
      cachedOffsetY = spawnersCfg.getDouble("defaults.hologram-offset-y", 3.5);
      cachedDefaultIcon = spawnersCfg.getString("defaults.hologram-icon", "⚔");
    }

    FileConfiguration msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    if (msgCfg != null) {
      List<String> lines = msgCfg.getStringList("spawner.hologram-template");
      cachedTemplateLines = lines.isEmpty() ? null : lines;
    }
  }

  private void loadHologramConfigs() {
    holoConfigs.clear();
    File file = getHologramSettingsFile();
    if (!file.exists()) return;

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection section = cfg.getConfigurationSection("spawners");
    if (section == null) return;

    for (String id : section.getKeys(false)) {
      String path = "spawners." + id;
      boolean customPos = cfg.getBoolean(path + ".custom-pos", false);
      double x = cfg.getDouble(path + ".x", 0);
      double y = cfg.getDouble(path + ".y", 0);
      double z = cfg.getDouble(path + ".z", 0);
      float scale = (float) cfg.getDouble(path + ".scale", -1);
      boolean billboard = cfg.getBoolean(path + ".billboard", true);
      float facing = (float) cfg.getDouble(path + ".facing", 0);
      String color = cfg.getString(path + ".color", "");
      boolean showArrow = cfg.getBoolean(path + ".show-arrow", true);
      String icon = cfg.getString(path + ".icon", "");
      holoConfigs.put(
          id,
          new HologramConfig(customPos, x, y, z, scale, billboard, facing, color, showArrow, icon));
    }
  }

  private void saveHologramConfigs() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (Map.Entry<String, HologramConfig> entry : holoConfigs.entrySet()) {
      String path = "spawners." + entry.getKey();
      HologramConfig c = entry.getValue();
      cfg.set(path + ".custom-pos", c.customPos());
      cfg.set(path + ".x", c.x());
      cfg.set(path + ".y", c.y());
      cfg.set(path + ".z", c.z());
      cfg.set(path + ".scale", c.scale());
      cfg.set(path + ".billboard", c.billboard());
      cfg.set(path + ".facing", c.facing());
      cfg.set(path + ".color", c.color());
      cfg.set(path + ".show-arrow", c.showArrow());
      cfg.set(path + ".icon", c.icon());
    }
    try {
      cfg.save(getHologramSettingsFile());
    } catch (IOException e) {
      Bukkit.getLogger()
          .severe("[Evolv3] Failed to save spawner hologram settings: " + e.getMessage());
    }
  }

  private @NotNull File getHologramSettingsFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "spawner-hologram-settings.yml");
  }
}
