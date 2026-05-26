package org.benski.evolv3.core.managers.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages individual launch pads stored in {@code launchpads.yml}.
 *
 * <p>Each launch pad is a single pressure plate location with configurable forward and upward
 * power. Admins register pads via a selection mode (right-clicking pressure plates) and each pad
 * persists across restarts.
 */
public final class LaunchPadManager {
  @Getter private static final LaunchPadManager instance = new LaunchPadManager();

  /** Key = "world,x,y,z" → pad settings */
  private final Map<String, LaunchPad> pads = new LinkedHashMap<>();

  /** Creates a new LaunchPadManager instance. */
  private LaunchPadManager() {}

  /**
   * Immutable snapshot of one launch pad.
   *
   * @param locationKey serialised key: "world,x,y,z"
   * @param world world name
   * @param x block X
   * @param y block Y
   * @param z block Z
   * @param forwardPower forward velocity multiplier
   * @param upwardPower upward velocity component
   */
  public record LaunchPad(
      String locationKey,
      String world,
      int x,
      int y,
      int z,
      double forwardPower,
      double upwardPower) {}

  /** Builds the location key used as the map identifier. */
  public static @NotNull String toKey(@NotNull String world, int x, int y, int z) {
    return world + "," + x + "," + y + "," + z;
  }

  /** Builds a location key from a Bukkit {@link Location}. */
  public static @NotNull String toKey(@NotNull Location loc) {
    return toKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
  }

  /** Loads launch pads from config. Delegates to {@link #loadFromConfig()}. */
  public void initialize() {
    loadFromConfig();
  }

  /** Loads all launch pads from {@code launchpads.yml} into memory. */
  public void loadFromConfig() {
    pads.clear();
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    if (cfg == null) return;

    ConfigurationSection section = cfg.getConfigurationSection("pads");
    if (section == null) return;

    for (String key : section.getKeys(false)) {
      String path = "pads." + key;
      String world = cfg.getString(path + ".world", "world");
      int x = cfg.getInt(path + ".x");
      int y = cfg.getInt(path + ".y");
      int z = cfg.getInt(path + ".z");
      double forward = cfg.getDouble(path + ".forward-power", 2.0);
      double upward = cfg.getDouble(path + ".upward-power", 1.2);

      String locKey = toKey(world, x, y, z);
      pads.put(locKey, new LaunchPad(locKey, world, x, y, z, forward, upward));
    }
  }

  /** Persists all in-memory pads to {@code launchpads.yml}. */
  public void saveToConfig() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    if (cfg == null) return;

    cfg.set("pads", null);

    int index = 0;
    for (LaunchPad pad : pads.values()) {
      String path = "pads.pad" + index;
      cfg.set(path + ".world", pad.world());
      cfg.set(path + ".x", pad.x());
      cfg.set(path + ".y", pad.y());
      cfg.set(path + ".z", pad.z());
      cfg.set(path + ".forward-power", pad.forwardPower());
      cfg.set(path + ".upward-power", pad.upwardPower());
      index++;
    }

    ConfigRegistry.getInstance().save(ConfigNames.LAUNCHPADS);
  }

  /**
   * Registers a new launch pad at the given location.
   *
   * @return the created pad, or null if one already exists at that location
   */
  public @Nullable LaunchPad addPad(
      @NotNull Location loc, double forwardPower, double upwardPower) {
    String key = toKey(loc);
    if (pads.containsKey(key)) return null;

    LaunchPad pad =
        new LaunchPad(
            key,
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ(),
            forwardPower,
            upwardPower);
    pads.put(key, pad);
    saveToConfig();
    return pad;
  }

  /**
   * Removes the launch pad at the given location.
   *
   * @return true if a pad existed and was removed
   */
  public boolean removePad(@NotNull Location loc) {
    if (pads.remove(toKey(loc)) == null) return false;
    saveToConfig();
    return true;
  }

  /** Returns the pad at the given location, or null. */
  public @Nullable LaunchPad getPad(@NotNull Location loc) {
    return pads.get(toKey(loc));
  }

  /** Returns all configured launch pads. */
  public @NotNull Map<String, LaunchPad> getAllPads() {
    return Collections.unmodifiableMap(pads);
  }

  /** Player cooldown between launches, in milliseconds. */
  public long getCooldownMs() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    return (cfg != null) ? cfg.getLong("cooldown-ms", 2000L) : 2000L;
  }

  /** Duration of fall damage protection after a launch, in ticks. */
  public int getNoFallDamageTicks() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    return (cfg != null) ? cfg.getInt("no-fall-damage-ticks", 80) : 80;
  }

  /** Duration of the cloud trail after launch, in ticks. */
  public int getTrailDurationTicks() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    return (cfg != null) ? cfg.getInt("cloud-trail.duration-ticks", 40) : 40;
  }

  /** Number of cloud particles per trail tick. */
  public int getTrailParticleCount() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    return (cfg != null) ? cfg.getInt("cloud-trail.particle-count", 3) : 3;
  }

  /** Whether the cloud trail is enabled. */
  public boolean isTrailEnabled() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    return (cfg == null) || cfg.getBoolean("cloud-trail.enabled", true);
  }

  /** Tick interval for the ambient particles around pads. */
  public int getAmbientParticleTick() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    return (cfg != null) ? cfg.getInt("ambient-particles.tick-interval", 10) : 10;
  }

  /** Number of ambient cloud particles per tick around each pad. */
  public int getAmbientParticleCount() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.LAUNCHPADS);
    return (cfg != null) ? cfg.getInt("ambient-particles.particle-count", 2) : 2;
  }

  /** Clears all in-memory launch pad data. */
  public void cleanup() {
    pads.clear();
  }
}
