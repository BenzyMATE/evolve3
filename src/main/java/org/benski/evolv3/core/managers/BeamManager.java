package org.benski.evolv3.core.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages levitation beams — rectangular columns that pull players upward and render a UFO-style
 * tractor-beam particle effect.
 *
 * <p>Each beam is defined by two opposite corners of a 3-D box. Any player inside the box is
 * smoothly pulled upward with velocity, and swirling dust particles are rendered in the beam's
 * chosen colour.
 *
 * <p>Beams are persisted in {@code beams.yml}.
 */
public final class BeamManager {
  @Getter private static final BeamManager instance = new BeamManager();

  /** Preset beam colours selectable via /beam set. */
  private static final Map<String, Color> BEAM_COLORS =
      Map.of(
          "purple", Color.fromRGB(160, 32, 240),
          "yellow", Color.fromRGB(255, 255, 50),
          "green", Color.fromRGB(50, 255, 100),
          "red", Color.fromRGB(255, 50, 50),
          "blue", Color.fromRGB(50, 220, 255));

  private final GridManager gridManager = new GridManager(16);
  private final Map<String, Beam> beams = new HashMap<>();

  private BeamManager() {}

  /** Returns the list of valid colour names. */
  public static List<String> getColorNames() {
    return List.of("purple", "yellow", "green", "red", "blue");
  }

  /** Returns the Bukkit Color for a preset name, or null if unknown. */
  public static @Nullable Color getPresetColor(@NotNull String name) {
    return BEAM_COLORS.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Immutable snapshot of one levitation beam volume.
   *
   * <p>Coordinates are already expanded to include full block volume (the max corner has +1 applied
   * at creation time).
   *
   * @param name the unique name of this beam
   * @param color the preset colour name (purple, yellow, green, red, blue)
   * @param x1 X coordinate of the first corner
   * @param y1 Y coordinate of the first corner
   * @param z1 Z coordinate of the first corner
   * @param x2 X coordinate of the opposite corner (block-expanded)
   * @param y2 Y coordinate of the opposite corner (block-expanded)
   * @param z2 Z coordinate of the opposite corner (block-expanded)
   */
  public record Beam(
      String name, String color, double x1, double y1, double z1, double x2, double y2, double z2) {
    public double minX() {
      return Math.min(x1, x2);
    }

    public double minY() {
      return Math.min(y1, y2);
    }

    public double minZ() {
      return Math.min(z1, z2);
    }

    public double maxX() {
      return Math.max(x1, x2);
    }

    public double maxY() {
      return Math.max(y1, y2);
    }

    public double maxZ() {
      return Math.max(z1, z2);
    }

    /** Returns true if the given point is inside this beam's bounding box. */
    public boolean contains(double px, double py, double pz) {
      return px >= minX()
          && px <= maxX()
          && py >= minY()
          && py <= maxY()
          && pz >= minZ()
          && pz <= maxZ();
    }

    /** Returns the Bukkit Color for this beam, falling back to green. */
    public Color bukkitColor() {
      Color c = BEAM_COLORS.get(color);
      return (c != null) ? c : BEAM_COLORS.get("green");
    }
  }

  /** Loads all beams from {@code beams.yml} into memory. */
  public void loadFromConfig() {
    beams.clear();
    gridManager.clear();
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BEAMS);
    if (cfg == null) return;

    ConfigurationSection section = cfg.getConfigurationSection("beams");
    if (section == null) return;

    for (String name : section.getKeys(false)) {
      String path = "beams." + name;
      String color = cfg.getString(path + ".color", "green");
      double x1 = cfg.getDouble(path + ".x1");
      double y1 = cfg.getDouble(path + ".y1");
      double z1 = cfg.getDouble(path + ".z1");
      double x2 = cfg.getDouble(path + ".x2");
      double y2 = cfg.getDouble(path + ".y2");
      double z2 = cfg.getDouble(path + ".z2");
      Beam beam = new Beam(name, color, x1, y1, z1, x2, y2, z2);
      beams.put(name, beam);
      gridManager.addBeam(beam);
    }
  }

  /** Persists all in-memory beams to {@code beams.yml}. */
  public void saveToConfig() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BEAMS);
    if (cfg == null) return;

    cfg.set("beams", null);

    for (Beam b : beams.values()) {
      String path = "beams." + b.name();
      cfg.set(path + ".color", b.color());
      cfg.set(path + ".x1", b.x1());
      cfg.set(path + ".y1", b.y1());
      cfg.set(path + ".z1", b.z1());
      cfg.set(path + ".x2", b.x2());
      cfg.set(path + ".y2", b.y2());
      cfg.set(path + ".z2", b.z2());
    }

    ConfigRegistry.getInstance().save(ConfigNames.BEAMS);
  }

  /**
   * Creates or replaces a beam. The two corners should be raw block coordinates — this method
   * expands them to include the full block volume.
   */
  public void setBeam(
      @NotNull String name,
      @NotNull String color,
      int bx1,
      int by1,
      int bz1,
      int bx2,
      int by2,
      int bz2) {
    double minX = Math.min(bx1, bx2);
    double minY = Math.min(by1, by2);
    double minZ = Math.min(bz1, bz2);
    double maxX = Math.max(bx1, bx2) + 1.0;
    double maxY = Math.max(by1, by2) + 1.0;
    double maxZ = Math.max(bz1, bz2) + 1.0;

    Beam oldBeam = beams.get(name);
    if (oldBeam != null) gridManager.removeBeam(oldBeam);

    Beam newBeam =
        new Beam(name, color.toLowerCase(Locale.ROOT), minX, minY, minZ, maxX, maxY, maxZ);
    beams.put(name, newBeam);
    gridManager.addBeam(newBeam);
    saveToConfig();
  }

  /**
   * Removes a beam by name.
   *
   * @return true if a beam existed and was removed
   */
  public boolean removeBeam(@NotNull String name) {
    Beam removed = beams.remove(name);
    if (removed == null) return false;
    gridManager.removeBeam(removed);
    saveToConfig();
    return true;
  }

  public @Nullable Beam getBeam(@NotNull String name) {
    return beams.get(name);
  }

  public @NotNull Map<String, Beam> getAllBeams() {
    return Collections.unmodifiableMap(beams);
  }

  /** Returns the first beam that contains the given location, or null if none. */
  public @Nullable Beam getBeamAt(double x, double y, double z) {
    for (Beam beam : gridManager.getBeamsAt(x, y, z)) {
      if (beam.contains(x, y, z)) return beam;
    }
    return null;
  }

  /** Returns the upward velocity applied to players inside beams. */
  public double getPullSpeed() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BEAMS);
    return (cfg != null) ? cfg.getDouble("pull-speed", 0.35) : 0.35;
  }

  /** Returns the particle density (spacing) from config. */
  public double getParticleDensity() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BEAMS);
    return (cfg != null) ? cfg.getDouble("particle-density", 0.8) : 0.8;
  }

  /** Returns the particle tick interval from config. */
  public int getParticleTick() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.BEAMS);
    return (cfg != null) ? cfg.getInt("particle-tick", 5) : 5;
  }

  public void cleanup() {
    beams.clear();
    gridManager.clear();
  }

  /** Spatial partitioning grid for efficient beam lookups. */
  private static class GridManager {
    private final int cellSize;
    private final Map<Long, List<Beam>> beamsByCell = new HashMap<>();

    public GridManager(int cellSize) {
      this.cellSize = cellSize;
    }

    private long getCellKey(int cellX, int cellY, int cellZ) {
      return (((long) cellX & 0xFFFFFFFFL) << 32)
          | (((long) (cellY & 0xFFFFFFFF)) << 16)
          | (cellZ & 0xFFFFFFFF);
    }

    private int getCellCoordinate(double worldCoord) {
      return Math.floorDiv((int) Math.floor(worldCoord), cellSize);
    }

    public void addBeam(Beam beam) {
      int minCellX = getCellCoordinate(beam.minX());
      int minCellY = getCellCoordinate(beam.minY());
      int minCellZ = getCellCoordinate(beam.minZ());
      int maxCellX = getCellCoordinate(beam.maxX() - 1e-6);
      int maxCellY = getCellCoordinate(beam.maxY() - 1e-6);
      int maxCellZ = getCellCoordinate(beam.maxZ() - 1e-6);

      for (int x = minCellX; x <= maxCellX; x++) {
        for (int y = minCellY; y <= maxCellY; y++) {
          for (int z = minCellZ; z <= maxCellZ; z++) {
            long key = getCellKey(x, y, z);
            beamsByCell.computeIfAbsent(key, k -> new ArrayList<>()).add(beam);
          }
        }
      }
    }

    public void removeBeam(Beam beam) {
      int minCellX = getCellCoordinate(beam.minX());
      int minCellY = getCellCoordinate(beam.minY());
      int minCellZ = getCellCoordinate(beam.minZ());
      int maxCellX = getCellCoordinate(beam.maxX() - 1e-6);
      int maxCellY = getCellCoordinate(beam.maxY() - 1e-6);
      int maxCellZ = getCellCoordinate(beam.maxZ() - 1e-6);

      for (int x = minCellX; x <= maxCellX; x++) {
        for (int y = minCellY; y <= maxCellY; y++) {
          for (int z = minCellZ; z <= maxCellZ; z++) {
            long key = getCellKey(x, y, z);
            List<Beam> cellBeams = beamsByCell.get(key);
            if (cellBeams != null) {
              cellBeams.remove(beam);
              if (cellBeams.isEmpty()) {
                beamsByCell.remove(key);
              }
            }
          }
        }
      }
    }

    public List<Beam> getBeamsAt(double x, double y, double z) {
      int cellX = getCellCoordinate(x);
      int cellY = getCellCoordinate(y);
      int cellZ = getCellCoordinate(z);
      long key = getCellKey(cellX, cellY, cellZ);
      return beamsByCell.getOrDefault(key, Collections.emptyList());
    }

    public void clear() {
      beamsByCell.clear();
    }

    public int getTotalBeams() {
      return beamsByCell.values().stream().mapToInt(List::size).sum();
    }
  }
}
