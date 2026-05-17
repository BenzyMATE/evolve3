package org.benski.evolv3.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages blackhole points. Each blackhole has two radii: - effectRadius: outer sphere where
 * visual/potion effects begin - teleportRadius: inner sphere where the player is pulled in and
 * eventually teleported
 */
public final class BlackholeManager {
  @Getter private static final BlackholeManager instance = new BlackholeManager();

  public record Blackhole(
      String name,
      String world,
      double cx,
      double cy,
      double cz,
      double effectRadius,
      double teleportRadius,
      @Nullable String linkedTo) {
    public Blackhole withLink(@Nullable String link) {
      return new Blackhole(name, world, cx, cy, cz, effectRadius, teleportRadius, link);
    }

    public Blackhole withEffectRadius(double r) {
      return new Blackhole(name, world, cx, cy, cz, r, teleportRadius, linkedTo);
    }

    public Blackhole withTeleportRadius(double r) {
      return new Blackhole(name, world, cx, cy, cz, effectRadius, r, linkedTo);
    }

    public Blackhole withCy(double y) {
      return new Blackhole(name, world, cx, y, cz, effectRadius, teleportRadius, linkedTo);
    }
  }

  private final Map<String, Blackhole> blackholes = new HashMap<>();

  private BlackholeManager() {}

  public void initialize() {
    loadFromDisk();
  }

  public void cleanup() {
    blackholes.clear();
  }

  public boolean create(
      @NotNull String name,
      @NotNull String world,
      double cx,
      double cy,
      double cz,
      double effectRadius,
      double teleportRadius) {
    String key = name.toLowerCase();
    if (blackholes.containsKey(key)) return false;
    blackholes.put(key, new Blackhole(key, world, cx, cy, cz, effectRadius, teleportRadius, null));
    saveToDisk();
    return true;
  }

  public boolean remove(@NotNull String name) {
    String key = name.toLowerCase();
    Blackhole bh = blackholes.remove(key);
    if (bh == null) return false;
    if (bh.linkedTo() != null) {
      Blackhole partner = blackholes.get(bh.linkedTo());
      if (partner != null) blackholes.put(partner.name(), partner.withLink(null));
    }
    saveToDisk();
    return true;
  }

  public boolean link(@NotNull String nameA, @NotNull String nameB) {
    String ka = nameA.toLowerCase(), kb = nameB.toLowerCase();
    Blackhole a = blackholes.get(ka), b = blackholes.get(kb);
    if (a == null || b == null || ka.equals(kb)) return false;
    blackholes.put(ka, a.withLink(kb));
    blackholes.put(kb, b.withLink(ka));
    saveToDisk();
    return true;
  }

  public boolean unlink(@NotNull String name) {
    String key = name.toLowerCase();
    Blackhole bh = blackholes.get(key);
    if (bh == null || bh.linkedTo() == null) return false;
    Blackhole partner = blackholes.get(bh.linkedTo());
    if (partner != null) blackholes.put(partner.name(), partner.withLink(null));
    blackholes.put(key, bh.withLink(null));
    saveToDisk();
    return true;
  }

  public boolean setEffectRadius(@NotNull String name, double radius) {
    String key = name.toLowerCase();
    Blackhole bh = blackholes.get(key);
    if (bh == null) return false;
    blackholes.put(key, bh.withEffectRadius(radius));
    saveToDisk();
    return true;
  }

  public boolean setGroundLevel(@NotNull String name, double y) {
    String key = name.toLowerCase();
    Blackhole bh = blackholes.get(key);
    if (bh == null) return false;
    blackholes.put(key, bh.withCy(y));
    saveToDisk();
    return true;
  }

  public boolean setTeleportRadius(@NotNull String name, double radius) {
    String key = name.toLowerCase();
    Blackhole bh = blackholes.get(key);
    if (bh == null) return false;
    blackholes.put(key, bh.withTeleportRadius(radius));
    saveToDisk();
    return true;
  }

  public @Nullable Blackhole get(@NotNull String name) {
    return blackholes.get(name.toLowerCase());
  }

  public @NotNull Collection<Blackhole> getAll() {
    return Collections.unmodifiableCollection(blackholes.values());
  }

  public boolean exists(@NotNull String name) {
    return blackholes.containsKey(name.toLowerCase());
  }

  public void loadFromDisk() {
    blackholes.clear();
    File file = dataFile();
    if (!file.exists()) return;

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection sec = cfg.getConfigurationSection("blackholes");
    if (sec == null) return;

    for (String name : sec.getKeys(false)) {
      String path = "blackholes." + name;
      String world = cfg.getString(path + ".world", "world");
      double cx = cfg.getDouble(path + ".cx");
      double cy = cfg.getDouble(path + ".cy");
      double cz = cfg.getDouble(path + ".cz");
      double effectR = cfg.getDouble(path + ".effect-radius", 30.0);
      double teleportR = cfg.getDouble(path + ".teleport-radius", 8.0);
      String linked = cfg.getString(path + ".linked-to", "");
      if (linked.isEmpty()) linked = null;
      blackholes.put(
          name.toLowerCase(),
          new Blackhole(name.toLowerCase(), world, cx, cy, cz, effectR, teleportR, linked));
    }
  }

  private void saveToDisk() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (Blackhole bh : blackholes.values()) {
      String path = "blackholes." + bh.name();
      cfg.set(path + ".world", bh.world());
      cfg.set(path + ".cx", bh.cx());
      cfg.set(path + ".cy", bh.cy());
      cfg.set(path + ".cz", bh.cz());
      cfg.set(path + ".effect-radius", bh.effectRadius());
      cfg.set(path + ".teleport-radius", bh.teleportRadius());
      cfg.set(path + ".linked-to", bh.linkedTo() != null ? bh.linkedTo() : "");
    }
    try {
      cfg.save(dataFile());
    } catch (IOException ex) {
      Bukkit.getLogger().severe("[Evolv3] Failed to save blackholes: " + ex.getMessage());
    }
  }

  private @NotNull File dataFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "blackholes.yml");
  }
}
