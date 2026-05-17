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
import org.benski.evolv3.core.managers.MineManager.Mine;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
 * Manages floating TextDisplay holograms above each mine showing live stats.
 *
 * <h3>Design goals</h3>
 *
 * <ul>
 *   <li>No file I/O in the hot update loop — config values are cached and refreshed every {@value
 *       #CONFIG_REFRESH_TICKS} ticks.
 *   <li>Text is only rebuilt when values actually change, using a per-mine {@link MineSnapshot} for
 *       change detection.
 *   <li>Entities use {@code setPersistent(false)} so they are never written to disk, eliminating
 *       the duplicate-entity crash on restart.
 *   <li>Entities are only spawned in chunks that are already player-loaded. {@link
 *       #onChunkLoad(Chunk)} handles the spawn the moment a player naturally loads the relevant
 *       chunk, avoiding the force-load/instant- unload loop that previously prevented text from
 *       updating.
 * </ul>
 */
public final class MineHologramManager {
  @Getter private static final MineHologramManager instance = new MineHologramManager();

  /** Active holograms, keyed by lowercase mine name. */
  private final Map<String, TextDisplay> holograms = new HashMap<>();

  /**
   * Values that were last used to render each hologram. Text is only rebuilt when these change,
   * avoiding redundant work.
   */
  private final Map<String, MineSnapshot> lastSnapshot = new HashMap<>();

  /** Per-mine hologram customization (position, scale, billboard mode). */
  private final Map<String, HologramConfig> holoConfigs = new HashMap<>();

  /**
   * Per-mine hologram customization settings.
   *
   * @param customPos whether {@code x/y/z} override the auto-calculated position
   * @param x custom world X (only used when {@code customPos} is true)
   * @param y custom world Y
   * @param z custom world Z
   * @param scale text scale; use -1 to fall back to the global default
   * @param billboard true = CENTER (hologram faces the nearest player), false = FIXED (hologram is
   *     static, readable from one direction)
   * @param facing entity yaw in degrees, applied when {@code billboard} is false
   * @param color beam colour name for the mine-name line (purple/yellow/green/red/blue), or an
   *     empty string to use the template default
   * @param showArrow whether to include the decorative arrow line at the bottom
   * @param icon icon character(s) shown before the mine name; empty = use global default
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

  /** Hex colour codes for the beam colour presets (matches BeamManager.BEAM_COLORS). */
  private static final Map<String, String> COLOR_HEX =
      Map.of(
          "purple", "&#A020F0",
          "yellow", "&#FFFF32",
          "green", "&#32FF64",
          "red", "&#FF3232",
          "blue", "&#32DCFF");

  /** Periodic update task — null when not running. */
  private BukkitTask updateTask;

  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  /**
   * PDC key applied to every hologram entity so orphaned instances from previous runs can be
   * identified and removed. Public so {@code MineListener} can reference the same key without
   * duplication.
   */
  public static final NamespacedKey PDC_KEY = new NamespacedKey("evolv3", "mine_hologram");

  /** How often (in ticks) to re-read hologram config values. */
  private static final int CONFIG_REFRESH_TICKS = 6_000;

  private List<String> cachedTemplateLines = null;
  private float cachedScale = 4.0f;
  private double cachedOffsetY = 14.0;
  private String cachedDefaultIcon = "⛏";

  /** Ticks accumulated since the last config refresh. */
  private int ticksSinceRefresh = CONFIG_REFRESH_TICKS;

  /**
   * Immutable record of the values last written to a hologram. If the current values match this
   * record, no {@code text()} call is made.
   */
  private record MineSnapshot(double remaining, int players, int caves, long resetSecs) {}

  private MineHologramManager() {}

  /**
   * Primes the config cache, starts the update task, and defers initial entity spawning by 2 ticks
   * so all worlds are fully available.
   */
  public void initialize() {
    if (!isEnabled()) return;

    refreshConfigCache();
    loadHologramConfigs();
    startUpdateTask();

    Bukkit.getScheduler()
        .runTaskLater(
            Main.getInstance(),
            () -> {
              purgeOrphanedHolograms();
              for (Mine mine : MineManager.getInstance().getAllMines().values()) {
                if (mine.hologram()) spawnHologram(mine);
              }
            },
            2L);
  }

  /**
   * Removes all PDC-tagged TextDisplay entities from every loaded world. Runs at startup to
   * eliminate holograms saved by the old {@code setPersistent(true)} build before they duplicate
   * fresh spawns.
   */
  private void purgeOrphanedHolograms() {
    for (World w : Bukkit.getWorlds()) {
      for (org.bukkit.entity.Entity e : w.getEntities()) {
        if (!(e instanceof TextDisplay td)) continue;
        if (td.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.STRING)) {
          td.remove();
        }
      }
    }
  }

  /** Cancels the update task and removes all hologram entities. */
  public void cleanup() {
    stopUpdateTask();
    removeAll();
  }

  /**
   * Called by {@code MineListener.onChunkLoad} after the old-entity purge.
   *
   * <p>Spawns a hologram for any mine whose hologram position falls inside {@code chunk}, provided
   * no valid entity exists yet. This is the primary spawn path for holograms in chunks that were
   * not loaded during {@link #initialize()} and ensures entities are only ever created inside
   * already-player-loaded chunks (preventing the force-load/instant-unload cycle that stops text
   * from updating).
   */
  public void onChunkLoad(@NotNull Chunk chunk) {
    if (!isEnabled()) return;

    for (Mine mine : MineManager.getInstance().getAllMines().values()) {
      if (!mine.hologram()) continue;

      TextDisplay existing = holograms.get(mine.name().toLowerCase());
      if (existing != null && existing.isValid()) continue;

      Location loc = getHologramLocation(mine);
      if (loc == null || loc.getWorld() == null) continue;
      if (!loc.getWorld().equals(chunk.getWorld())) continue;
      if ((loc.getBlockX() >> 4) != chunk.getX()) continue;
      if ((loc.getBlockZ() >> 4) != chunk.getZ()) continue;

      spawnHologram(mine);
    }
  }

  /**
   * Spawns a TextDisplay for the given mine.
   *
   * <p>If the hologram chunk is not currently loaded, this method returns immediately — {@link
   * #onChunkLoad(Chunk)} will trigger the spawn once a player loads the chunk naturally.
   */
  public void spawnHologram(@NotNull Mine mine) {
    if (!isEnabled()) return;

    removeHologram(mine.name());

    Location loc = getHologramLocation(mine);
    if (loc == null || loc.getWorld() == null) return;

    if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

    MineSnapshot snap = buildSnapshot(mine);

    HologramConfig config =
        holoConfigs.getOrDefault(mine.name().toLowerCase(), HologramConfig.defaults());
    float effectiveScale = config.scale() > 0 ? config.scale() : cachedScale;
    Display.Billboard effectiveBillboard =
        config.billboard() ? Display.Billboard.CENTER : Display.Billboard.FIXED;

    if (!config.billboard()) loc.setYaw(config.facing());

    TextDisplay display =
        loc.getWorld()
            .spawn(
                loc,
                TextDisplay.class,
                td -> {
                  td.text(buildText(mine, snap));
                  td.setBillboard(effectiveBillboard);
                  td.setShadowed(true);
                  td.setAlignment(TextDisplay.TextAlignment.CENTER);
                  td.setSeeThrough(false);
                  td.setDefaultBackground(false);
                  td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                  td.setVisibleByDefault(true);
                  td.setPersistent(false);
                  td.getPersistentDataContainer()
                      .set(PDC_KEY, PersistentDataType.STRING, mine.name().toLowerCase());
                  td.setTransformation(
                      new org.bukkit.util.Transformation(
                          new Vector3f(0, 0, 0),
                          new AxisAngle4f(0, 0, 0, 1),
                          new Vector3f(effectiveScale, effectiveScale, effectiveScale),
                          new AxisAngle4f(0, 0, 0, 1)));
                });

    holograms.put(mine.name().toLowerCase(), display);
    lastSnapshot.put(mine.name().toLowerCase(), snap);
  }

  /** Removes the hologram entity and tracking state for a mine. */
  public void removeHologram(@NotNull String mineName) {
    TextDisplay display = holograms.remove(mineName.toLowerCase());
    lastSnapshot.remove(mineName.toLowerCase());
    if (display != null) display.remove();
  }

  /** Removes all hologram entities and clears all tracking state. */
  public void removeAll() {
    for (TextDisplay display : holograms.values()) {
      if (display != null) display.remove();
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

  /**
   * Core update loop — runs every second.
   *
   * <ul>
   *   <li>Refreshes config cache every {@value #CONFIG_REFRESH_TICKS} ticks.
   *   <li>For each tracked hologram: rebuilds text only when values changed.
   *   <li>Re-spawns entities that have gone invalid (chunk unloaded), but only in naturally-loaded
   *       chunks.
   *   <li>Catches mines that gained hologram=true after initialisation.
   * </ul>
   */
  private void updateAll() {
    ticksSinceRefresh += 20;
    if (ticksSinceRefresh >= CONFIG_REFRESH_TICKS) {
      refreshConfigCache();
      ticksSinceRefresh = 0;
    }

    List<Mine> needsRespawn = new ArrayList<>();

    for (Map.Entry<String, TextDisplay> entry : holograms.entrySet()) {
      String key = entry.getKey();
      TextDisplay td = entry.getValue();

      Mine mine = MineManager.getInstance().getMine(key);
      if (mine == null) {
        if (td != null) td.remove();
        continue;
      }

      if (td == null || !td.isValid()) {
        needsRespawn.add(mine);
        continue;
      }

      MineSnapshot current = buildSnapshot(mine);
      MineSnapshot last = lastSnapshot.get(key);
      if (last == null || !last.equals(current)) {
        td.text(buildText(mine, current));
        lastSnapshot.put(key, current);
      }
    }

    holograms.entrySet().removeIf(e -> e.getValue() == null || !e.getValue().isValid());

    for (Mine mine : needsRespawn) {
      if (mine.hologram()) spawnHologram(mine);
    }

    for (Mine mine : MineManager.getInstance().getAllMines().values()) {
      if (!mine.hologram()) continue;
      if (holograms.containsKey(mine.name().toLowerCase())) continue;

      Location loc = getHologramLocation(mine);
      if (loc == null || loc.getWorld() == null) continue;
      if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
        spawnHologram(mine);
      }
    }
  }

  /** Reads the current live values for a mine into a snapshot record. */
  private @NotNull MineSnapshot buildSnapshot(@NotNull Mine mine) {
    return new MineSnapshot(
        MineManager.getInstance().getRemainingPercentage(mine.name()),
        MineManager.getInstance().getPlayerCount(mine.name()),
        CaveManager.getInstance().getActiveCaveCount(mine.name()),
        MineManager.getInstance().getSecondsUntilReset(mine.name()));
  }

  /**
   * Builds the hologram {@link Component} from the cached template, substituting live values from
   * the supplied snapshot.
   */
  private @NotNull Component buildText(@NotNull Mine mine, @NotNull MineSnapshot snap) {
    String resetTime;
    if (mine.resetInterval() <= 0) {
      resetTime = "--:--";
    } else {
      long min = snap.resetSecs() / 60;
      long sec = snap.resetSecs() % 60;
      resetTime = String.format("%d:%02d", min, sec);
    }

    HologramConfig config =
        holoConfigs.getOrDefault(mine.name().toLowerCase(), HologramConfig.defaults());
    String nameColor = COLOR_HEX.getOrDefault(config.color(), "&#00FFFF");
    String icon = config.icon().isEmpty() ? cachedDefaultIcon : config.icon();

    List<String> lines =
        new java.util.ArrayList<>(
            (cachedTemplateLines != null) ? cachedTemplateLines : defaultTemplate());

    if (mine.caveCount() == 0) {
      lines.removeIf(l -> l.contains("%caves_discovered%") || l.contains("%caves_total%"));
    }

    if (!config.showArrow()) {
      lines.removeIf(l -> l.contains("▼"));
    }

    String text =
        String.join("\n", lines)
            .replace("%name_color%", nameColor)
            .replace("%icon%", icon)
            .replace("%mine%", mine.name().toUpperCase())
            .replace("%remaining%", String.format("%.1f", snap.remaining()))
            .replace("%reset_time%", resetTime)
            .replace("%players%", String.valueOf(snap.players()))
            .replace("%caves_discovered%", String.valueOf(snap.caves()))
            .replace("%caves_total%", String.valueOf(mine.caveCount()));

    text = FontUtils.toSmallCaps(text);
    text = net.matrixcreations.libraries.MatrixColorAPI.process(text);

    return LEGACY.deserialize(text);
  }

  private static @NotNull List<String> defaultTemplate() {
    return List.of(
        "%name_color%&l%icon% %mine%",
        "",
        "&#888888Remaining  &#FFFFFF%remaining%%",
        "&#888888Reset  &#FFFFFF%reset_time%",
        "&#888888Miners  &#FFFFFF%players%",
        "&#888888Caves  &#FFFFFF%caves_discovered%&#888888/&#FFFFFF%caves_total%",
        "",
        "&#444444▼");
  }

  /**
   * Reads hologram-related config values into local fields. Called once during {@link
   * #initialize()} and then every {@value #CONFIG_REFRESH_TICKS} ticks from the update loop.
   */
  private void refreshConfigCache() {
    FileConfiguration minesCfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    if (minesCfg != null) {
      cachedScale = (float) minesCfg.getDouble("defaults.hologram-scale", 4.0);
      cachedOffsetY = minesCfg.getDouble("defaults.hologram-offset-y", 14.0);
      cachedDefaultIcon = minesCfg.getString("defaults.hologram-icon", "⛏");
    }

    FileConfiguration msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    if (msgCfg != null) {
      List<String> lines = msgCfg.getStringList("mine.hologram-template");
      cachedTemplateLines = lines.isEmpty() ? null : lines;
    }
  }

  /**
   * Returns the world-space location where the hologram should sit: the horizontal centre of the
   * mine at {@code maxY + offsetY}.
   *
   * <p>Public so other components (e.g. the cave-discovery animation) can anchor relative to the
   * mine hologram position.
   */
  public @Nullable Location getHologramLocation(@NotNull Mine mine) {
    World world = Bukkit.getWorld(mine.world());
    if (world == null) return null;

    HologramConfig config = holoConfigs.get(mine.name().toLowerCase());
    if (config != null && config.customPos()) {
      return new Location(world, config.x(), config.y(), config.z());
    }

    double cx = (mine.minX() + mine.maxX()) / 2.0;
    double cz = (mine.minZ() + mine.maxZ()) / 2.0;

    return new Location(world, cx, mine.maxY() + cachedOffsetY, cz);
  }

  /** Sets a custom world-space position for the named mine's hologram and respawns it. */
  public void setPosition(@NotNull String mineName, double x, double y, double z) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    respawnIfActive(mineName);
  }

  /** Clears any custom position and reverts to the auto-calculated centre-top location. */
  public void resetPosition(@NotNull String mineName) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    respawnIfActive(mineName);
  }

  /** Sets the text scale for the named mine's hologram. Use -1 to revert to the global default. */
  public void setScale(@NotNull String mineName, float scale) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    respawnIfActive(mineName);
  }

  /**
   * Sets whether the hologram rotates to face the nearest player.
   *
   * @param facesPlayer true = CENTER billboard (rotates to face player); false = FIXED (static,
   *     faces one direction)
   */
  public void setBillboard(@NotNull String mineName, boolean facesPlayer) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    respawnIfActive(mineName);
  }

  /**
   * Sets the facing yaw for a FIXED-billboard hologram. Has no visible effect when billboard mode
   * is CENTER.
   *
   * @param yaw degrees (0 = south, 90 = west, 180 = north, -90/270 = east)
   */
  public void setFacing(@NotNull String mineName, float yaw) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    respawnIfActive(mineName);
  }

  /**
   * Sets the colour of the mine-name line using a beam colour preset name (purple, yellow, green,
   * red, blue), or an empty string to use the template default.
   */
  public void setColor(@NotNull String mineName, @NotNull String colorName) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    Mine mine = MineManager.getInstance().getMine(mineName);
    if (mine != null && mine.hologram()) {
      TextDisplay td = holograms.get(key);
      if (td != null && td.isValid()) {
        td.text(buildText(mine, buildSnapshot(mine)));
        lastSnapshot.remove(key);
      }
    }
  }

  /** Toggles the decorative arrow (▼) at the bottom of the hologram. */
  public void setShowArrow(@NotNull String mineName, boolean show) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    Mine mine = MineManager.getInstance().getMine(mineName);
    if (mine != null && mine.hologram()) {
      TextDisplay td = holograms.get(key);
      if (td != null && td.isValid()) {
        td.text(buildText(mine, buildSnapshot(mine)));
        lastSnapshot.remove(key);
      }
    }
  }

  /**
   * Sets the icon shown before the mine name in the hologram title. Pass an empty string to revert
   * to the global default from mines.yml.
   */
  public void setIcon(@NotNull String mineName, @NotNull String icon) {
    String key = mineName.toLowerCase();
    HologramConfig cur = holoConfigs.getOrDefault(key, HologramConfig.defaults());
    holoConfigs.put(
        key,
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
    Mine mine = MineManager.getInstance().getMine(mineName);
    if (mine != null && mine.hologram()) {
      TextDisplay td = holograms.get(key);
      if (td != null && td.isValid()) {
        td.text(buildText(mine, buildSnapshot(mine)));
        lastSnapshot.remove(key);
      }
    }
  }

  private void respawnIfActive(@NotNull String mineName) {
    Mine mine = MineManager.getInstance().getMine(mineName);
    if (mine != null && mine.hologram()) {
      removeHologram(mineName);
      spawnHologram(mine);
    }
  }

  private void loadHologramConfigs() {
    holoConfigs.clear();
    File file = getHologramSettingsFile();
    if (!file.exists()) return;

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection section = cfg.getConfigurationSection("mines");
    if (section == null) return;

    for (String name : section.getKeys(false)) {
      String path = "mines." + name;
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
          name.toLowerCase(),
          new HologramConfig(customPos, x, y, z, scale, billboard, facing, color, showArrow, icon));
    }
  }

  private void saveHologramConfigs() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (Map.Entry<String, HologramConfig> entry : holoConfigs.entrySet()) {
      String path = "mines." + entry.getKey();
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
      Bukkit.getLogger().severe("[Evolv3] Failed to save hologram settings: " + e.getMessage());
    }
  }

  private @NotNull File getHologramSettingsFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "hologram-settings.yml");
  }

  private boolean isEnabled() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    return cfg != null && cfg.getBoolean("defaults.hologram-enabled", true);
  }
}
