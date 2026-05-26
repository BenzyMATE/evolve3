package org.benski.evolv3.core.managers.world;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Manages per-rank border holograms (TextDisplay entities). Multiple slots per rank are supported —
 * slot N auto-aligns with wall N's midpoint. Holograms are client-side: only visible to players who
 * have not yet reached that rank.
 */
public final class BorderHologramManager {
  @Getter private static final BorderHologramManager instance = new BorderHologramManager();

  private final Map<String, TreeMap<Integer, TextDisplay>> holograms = new HashMap<>();
  private final Map<String, TreeMap<Integer, HologramConfig>> holoConfigs = new HashMap<>();

  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  public static final NamespacedKey PDC_KEY = new NamespacedKey("evolv3", "border_hologram");

  private static final float DEFAULT_SCALE = 2.5f;
  private static final double DEFAULT_OFFSET_Y = 3.5;

  /** Hex colour codes for the beam colour presets (matches BeamManager / MineHologramManager). */
  private static final Map<String, String> COLOR_HEX =
      Map.of(
          "purple", "A020F0",
          "yellow", "FFFF32",
          "green", "32FF64",
          "red", "FF3232",
          "blue", "32DCFF");

  /** Hologram config. */
  private record HologramConfig(
      boolean customPos,
      String world,
      double x,
      double y,
      double z,
      float scale,
      boolean billboard,
      float facing,
      String color) {
    static HologramConfig defaults() {
      return new HologramConfig(false, "", 0, 0, 0, DEFAULT_SCALE, false, 0f, "");
    }
  }

  /** Creates a new BorderHologramManager instance. */
  private BorderHologramManager() {}

  /** Loads hologram configs and spawns all border holograms after a one-tick delay. */
  public void initialize() {
    loadHologramConfigs();

    Bukkit.getScheduler()
        .runTaskLater(
            Main.getInstance(),
            () -> {
              purgeOrphaned();
              for (String rankId : BorderManager.getInstance().getAllBorders().keySet())
                spawnHologram(rankId);
            },
            2L);

    Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::respawnInvalid, 40L, 20L);
  }

  /** Removes all hologram entities from the world and clears internal state. */
  public void cleanup() {
    holograms
        .values()
        .forEach(
            slots ->
                slots
                    .values()
                    .forEach(
                        td -> {
                          if (td != null) td.remove();
                        }));
    holograms.clear();
  }

  /** Spawns hologram. */
  public void spawnHologram(@NotNull String rankId) {
    removeHologram(rankId);
    TreeMap<Integer, HologramConfig> configs = holoConfigs.get(rankId.toLowerCase());
    if (configs == null || configs.isEmpty()) {
      spawnHologramSlot(rankId, 0, HologramConfig.defaults());
      return;
    }
    for (Map.Entry<Integer, HologramConfig> e : configs.entrySet())
      spawnHologramSlot(rankId, e.getKey(), e.getValue());
  }

  /** Spawns hologram slot. */
  private void spawnHologramSlot(@NotNull String rankId, int slot, @NotNull HologramConfig cfg) {
    Location loc = resolveLocation(rankId, slot, cfg);
    if (loc == null || loc.getWorld() == null) return;
    if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

    float scale = cfg.scale() > 0 ? cfg.scale() : DEFAULT_SCALE;
    Display.Billboard billboard =
        cfg.billboard() ? Display.Billboard.CENTER : Display.Billboard.FIXED;
    if (!cfg.billboard()) loc.setYaw(cfg.facing());

    TextDisplay td =
        loc.getWorld()
            .spawn(
                loc,
                TextDisplay.class,
                display -> {
                  display.text(buildText(rankId, cfg));
                  display.setBillboard(billboard);
                  display.setShadowed(true);
                  display.setAlignment(TextDisplay.TextAlignment.CENTER);
                  display.setSeeThrough(false);
                  display.setDefaultBackground(false);
                  display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                  display.setVisibleByDefault(false);
                  display.setPersistent(false);
                  display
                      .getPersistentDataContainer()
                      .set(PDC_KEY, PersistentDataType.STRING, rankId.toLowerCase());
                  display.setTransformation(
                      new org.bukkit.util.Transformation(
                          new Vector3f(0, 0, 0),
                          new AxisAngle4f(0, 0, 0, 1),
                          new Vector3f(scale, scale, scale),
                          new AxisAngle4f(0, 0, 0, 1)));
                });

    holograms.computeIfAbsent(rankId.toLowerCase(), k -> new TreeMap<>()).put(slot, td);
    refreshSlotVisibilityForAll(rankId, td);
  }

  /** Remove hologram. */
  public void removeHologram(@NotNull String rankId) {
    TreeMap<Integer, TextDisplay> slots = holograms.remove(rankId.toLowerCase());
    if (slots != null)
      slots
          .values()
          .forEach(
              td -> {
                if (td != null) td.remove();
              });
  }

  /** Remove hologram slot. */
  public void removeHologramSlot(@NotNull String rankId, int slot) {
    TreeMap<Integer, TextDisplay> slots = holograms.get(rankId.toLowerCase());
    if (slots == null) return;
    TextDisplay td = slots.remove(slot);
    if (td != null) td.remove();
  }

  /**
   * Adds a new hologram slot for the rank with default config and spawns it. Returns the slot
   * number assigned.
   */
  public int addHologramSlot(@NotNull String rankId) {
    String key = rankId.toLowerCase();
    TreeMap<Integer, HologramConfig> configs =
        holoConfigs.computeIfAbsent(key, k -> new TreeMap<>());
    int slot = configs.isEmpty() ? 0 : configs.lastKey() + 1;
    configs.put(slot, HologramConfig.defaults());
    saveHologramConfigs();
    if (BorderManager.getInstance().getAllBorders().containsKey(rankId.toUpperCase()))
      spawnHologramSlot(rankId, slot, HologramConfig.defaults());
    return slot;
  }

  /** Removes the hologram entity and its saved config for the given slot. */
  public void deleteHologramSlot(@NotNull String rankId, int slot) {
    removeHologramSlot(rankId, slot);
    TreeMap<Integer, HologramConfig> configs = holoConfigs.get(rankId.toLowerCase());
    if (configs != null) {
      configs.remove(slot);
      if (configs.isEmpty()) holoConfigs.remove(rankId.toLowerCase());
    }
    saveHologramConfigs();
  }

  /** Returns the configured slot numbers for the given rank. */
  public @NotNull Collection<Integer> getSlots(@NotNull String rankId) {
    TreeMap<Integer, HologramConfig> configs = holoConfigs.get(rankId.toLowerCase());
    return configs != null ? configs.keySet() : List.of();
  }

  /** Called after borders change (set/remove) to refresh holograms. */
  public void refreshAll() {
    holograms
        .entrySet()
        .removeIf(
            e -> {
              if (BorderManager.getInstance().getAllBorders().containsKey(e.getKey().toUpperCase()))
                return false;
              e.getValue()
                  .values()
                  .forEach(
                      td -> {
                        if (td != null) td.remove();
                      });
              return true;
            });
    for (String rankId : BorderManager.getInstance().getAllBorders().keySet())
      spawnHologram(rankId);
  }

  /** Public location query used by the list command. */
  public @Nullable Location getHologramLocation(@NotNull String rankId, int slot) {
    return resolveLocation(rankId, slot, getOrDefaultConfig(rankId, slot));
  }

  private @Nullable Location resolveLocation(
      @NotNull String rankId, int slot, @NotNull HologramConfig cfg) {
    if (cfg.customPos()) {
      World w =
          cfg.world().isEmpty()
              ? (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0))
              : Bukkit.getWorld(cfg.world());
      return w == null ? null : new Location(w, cfg.x(), cfg.y(), cfg.z());
    }

    List<BorderManager.BorderWall> walls = BorderManager.getInstance().getBorders(rankId);
    if (walls.isEmpty()) return null;

    BorderManager.BorderWall wall = slot < walls.size() ? walls.get(slot) : walls.get(0);
    World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    if (world == null) return null;

    double mx = (wall.x1() + wall.x2()) / 2.0;
    double mz = (wall.z1() + wall.z2()) / 2.0;
    double my = wall.maxY() + DEFAULT_OFFSET_Y;
    return new Location(world, mx, my, mz);
  }

  private @NotNull Component buildText(@NotNull String rankId, @NotNull HologramConfig cfg) {
    char block = rankId.isEmpty() ? 'A' : Character.toUpperCase(rankId.charAt(0));
    String hex =
        (cfg.color() != null && !cfg.color().isEmpty())
            ? COLOR_HEX.getOrDefault(cfg.color(), blockHex(block))
            : blockHex(block);

    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    long cost =
        ranksCfg != null
            ? ranksCfg.getLong(ConfigPaths.Ranks.DATA_PREFIX + rankId + ".Cost", 0)
            : 0;

    String raw =
        "&#"
            + hex
            + "&l"
            + block
            + "-Block"
            + "\n"
            + "\n"
            + "&#888888Cost  &#FFD700⛁ &#FFFFFF"
            + formatCoins(cost);

    raw = FontUtils.toSmallCaps(raw);
    raw = MatrixColorAPI.process(raw);
    return LEGACY.deserialize(raw);
  }

  /** Block hex. */
  private static String blockHex(char block) {
    return switch (block) {
      case 'A' -> "FF5555";
      case 'B' -> "FFDD44";
      case 'C' -> "55FF77";
      case 'D' -> "FFAA00";
      case 'E' -> "55AAFF";
      case 'F' -> "FF55FF";
      default -> "CCCCCC";
    };
  }

  /** Formats a coin value as a compact string (e.g. 1500000 → "1.5M"). */
  private static String formatCoins(long value) {
    if (value >= 1_000_000_000L)
      return trimZero(String.format(Locale.US, "%.1fB", value / 1_000_000_000.0));
    if (value >= 1_000_000L)
      return trimZero(String.format(Locale.US, "%.1fM", value / 1_000_000.0));
    if (value >= 1_000L) return trimZero(String.format(Locale.US, "%.1fK", value / 1_000.0));
    return String.valueOf(value);
  }

  /** Trim zero. */
  private static String trimZero(String s) {
    return s.replaceAll("\\.0([BKM])", "$1");
  }

  /** Sets a custom world position for the given border hologram slot and respawns it. */
  public void setPosition(
      @NotNull String rankId, int slot, @NotNull String worldName, double x, double y, double z) {
    HologramConfig cur = getOrDefaultConfig(rankId, slot);
    putConfig(
        rankId,
        slot,
        new HologramConfig(
            true, worldName, x, y, z, cur.scale(), cur.billboard(), cur.facing(), cur.color()));
    saveHologramConfigs();
    respawnSlot(rankId, slot);
  }

  /** Resets the position of the given border hologram slot to its default and respawns it. */
  public void resetPosition(@NotNull String rankId, int slot) {
    HologramConfig cur = getOrDefaultConfig(rankId, slot);
    putConfig(
        rankId,
        slot,
        new HologramConfig(
            false, "", 0, 0, 0, cur.scale(), cur.billboard(), cur.facing(), cur.color()));
    saveHologramConfigs();
    respawnSlot(rankId, slot);
  }

  /** Sets the scale of the given border hologram slot. */
  public void setScale(@NotNull String rankId, int slot, float scale) {
    HologramConfig cur = getOrDefaultConfig(rankId, slot);
    putConfig(
        rankId,
        slot,
        new HologramConfig(
            cur.customPos(),
            cur.world(),
            cur.x(),
            cur.y(),
            cur.z(),
            scale,
            cur.billboard(),
            cur.facing(),
            cur.color()));
    saveHologramConfigs();
    respawnSlot(rankId, slot);
  }

  /**
   * Sets whether the given border hologram slot faces the player (billboard) or uses fixed facing.
   */
  public void setBillboard(@NotNull String rankId, int slot, boolean facesPlayer) {
    HologramConfig cur = getOrDefaultConfig(rankId, slot);
    putConfig(
        rankId,
        slot,
        new HologramConfig(
            cur.customPos(),
            cur.world(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            facesPlayer,
            cur.facing(),
            cur.color()));
    saveHologramConfigs();
    respawnSlot(rankId, slot);
  }

  /** Sets the fixed yaw (facing) of the given border hologram slot. */
  public void setFacing(@NotNull String rankId, int slot, float yaw) {
    HologramConfig cur = getOrDefaultConfig(rankId, slot);
    putConfig(
        rankId,
        slot,
        new HologramConfig(
            cur.customPos(),
            cur.world(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            cur.billboard(),
            yaw,
            cur.color()));
    saveHologramConfigs();
    respawnSlot(rankId, slot);
  }

  /** Sets the colour of the given border hologram slot. */
  public void setColor(@NotNull String rankId, int slot, @NotNull String colorName) {
    HologramConfig cur = getOrDefaultConfig(rankId, slot);
    putConfig(
        rankId,
        slot,
        new HologramConfig(
            cur.customPos(),
            cur.world(),
            cur.x(),
            cur.y(),
            cur.z(),
            cur.scale(),
            cur.billboard(),
            cur.facing(),
            colorName));
    saveHologramConfigs();
    respawnSlot(rankId, slot);
  }

  /** Returns the or default config. */
  private HologramConfig getOrDefaultConfig(@NotNull String rankId, int slot) {
    TreeMap<Integer, HologramConfig> configs = holoConfigs.get(rankId.toLowerCase());
    return (configs != null && configs.containsKey(slot))
        ? configs.get(slot)
        : HologramConfig.defaults();
  }

  /** Put config. */
  private void putConfig(@NotNull String rankId, int slot, @NotNull HologramConfig cfg) {
    holoConfigs.computeIfAbsent(rankId.toLowerCase(), k -> new TreeMap<>()).put(slot, cfg);
  }

  /** Respawn slot. */
  private void respawnSlot(@NotNull String rankId, int slot) {
    removeHologramSlot(rankId, slot);
    if (!BorderManager.getInstance().getAllBorders().containsKey(rankId.toUpperCase())) return;
    spawnHologramSlot(rankId, slot, getOrDefaultConfig(rankId, slot));
  }

  /** Respawn invalid. */
  private void respawnInvalid() {
    for (String rankId : BorderManager.getInstance().getAllBorders().keySet()) {
      TreeMap<Integer, HologramConfig> configs = holoConfigs.get(rankId.toLowerCase());
      if (configs == null || configs.isEmpty()) {
        TreeMap<Integer, TextDisplay> existing = holograms.get(rankId.toLowerCase());
        TextDisplay slot0 = (existing != null) ? existing.get(0) : null;
        if (slot0 == null || !slot0.isValid()) spawnHologram(rankId);
        continue;
      }
      for (Map.Entry<Integer, HologramConfig> e : configs.entrySet()) {
        TreeMap<Integer, TextDisplay> slots = holograms.get(rankId.toLowerCase());
        TextDisplay td = (slots != null) ? slots.get(e.getKey()) : null;
        if (td != null && td.isValid()) continue;
        spawnHologramSlot(rankId, e.getKey(), e.getValue());
      }
    }
    holograms
        .entrySet()
        .removeIf(
            e -> {
              if (BorderManager.getInstance().getAllBorders().containsKey(e.getKey().toUpperCase()))
                return false;
              e.getValue()
                  .values()
                  .forEach(
                      td -> {
                        if (td != null) td.remove();
                      });
              return true;
            });
    refreshAllVisibilities();
  }

  /** Refresh slot visibility for all. */
  private void refreshSlotVisibilityForAll(@NotNull String rankId, @NotNull TextDisplay td) {
    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    if (ranksCfg == null) return;
    List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
    int wallIdx = order.indexOf(rankId.toUpperCase());

    for (Player player : Bukkit.getOnlinePlayers()) {
      if (shouldPlayerSee(player, wallIdx, order)) player.showEntity(Main.getInstance(), td);
      else player.hideEntity(Main.getInstance(), td);
    }
  }

  /** Refresh visibility for player. */
  public void refreshVisibilityForPlayer(@NotNull Player player) {
    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    if (ranksCfg == null) return;
    List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);

    for (Map.Entry<String, TreeMap<Integer, TextDisplay>> entry : holograms.entrySet()) {
      int wallIdx = order.indexOf(entry.getKey().toUpperCase());
      boolean see = shouldPlayerSee(player, wallIdx, order);
      for (TextDisplay td : entry.getValue().values()) {
        if (td == null || !td.isValid()) continue;
        if (see) player.showEntity(Main.getInstance(), td);
        else player.hideEntity(Main.getInstance(), td);
      }
    }
  }

  /** Refresh all visibilities. */
  private void refreshAllVisibilities() {
    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    if (ranksCfg == null) return;
    List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);

    for (Player player : Bukkit.getOnlinePlayers()) {
      for (Map.Entry<String, TreeMap<Integer, TextDisplay>> entry : holograms.entrySet()) {
        int wallIdx = order.indexOf(entry.getKey().toUpperCase());
        boolean see = shouldPlayerSee(player, wallIdx, order);
        for (TextDisplay td : entry.getValue().values()) {
          if (td == null || !td.isValid()) continue;
          if (see) player.showEntity(Main.getInstance(), td);
          else player.hideEntity(Main.getInstance(), td);
        }
      }
    }
  }

  /** Should player see. */
  private boolean shouldPlayerSee(
      @NotNull Player player, int wallIdx, @NotNull List<String> order) {
    if (wallIdx == -1) return false;

    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    String defaultRank =
        (ranksCfg != null) ? ranksCfg.getString(ConfigPaths.Ranks.DEFAULT_RANK, "A1") : "A1";

    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    PlayerProfile profile = (dc != null) ? dc.getLocal(player) : null;
    String playerRank =
        (profile != null && profile.getRank() != null) ? profile.getRank() : defaultRank;

    int playerIdx = order.indexOf(playerRank);
    if (playerIdx == -1) playerIdx = 0;

    return playerIdx < wallIdx;
  }

  /** Purge orphaned. */
  private void purgeOrphaned() {
    for (World w : Bukkit.getWorlds()) {
      for (org.bukkit.entity.Entity e : w.getEntities()) {
        if (!(e instanceof TextDisplay td)) continue;
        if (td.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.STRING)) td.remove();
      }
    }
  }

  /** Loads hologram configs. */
  private void loadHologramConfigs() {
    holoConfigs.clear();
    File file = settingsFile();
    if (!file.exists()) return;

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection borders = cfg.getConfigurationSection("borders");
    if (borders == null) return;

    for (String rankKey : borders.getKeys(false)) {
      ConfigurationSection rankSec = borders.getConfigurationSection(rankKey);
      if (rankSec == null) continue;

      TreeMap<Integer, HologramConfig> slots = new TreeMap<>();
      for (String slotKey : rankSec.getKeys(false)) {
        int slot;
        try {
          slot = Integer.parseInt(slotKey);
        } catch (NumberFormatException ignored) {
          continue;
        }

        String path = "borders." + rankKey + "." + slotKey;
        boolean customPos = cfg.getBoolean(path + ".custom-pos", false);
        String world = cfg.getString(path + ".world", "");
        double x = cfg.getDouble(path + ".x", 0);
        double y = cfg.getDouble(path + ".y", 0);
        double z = cfg.getDouble(path + ".z", 0);
        float scale = (float) cfg.getDouble(path + ".scale", DEFAULT_SCALE);
        boolean billboard = cfg.getBoolean(path + ".billboard", false);
        float facing = (float) cfg.getDouble(path + ".facing", 0);
        String color = cfg.getString(path + ".color", "");
        slots.put(
            slot, new HologramConfig(customPos, world, x, y, z, scale, billboard, facing, color));
      }
      if (!slots.isEmpty()) holoConfigs.put(rankKey.toLowerCase(), slots);
    }
  }

  /** Saves hologram configs. */
  private void saveHologramConfigs() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (Map.Entry<String, TreeMap<Integer, HologramConfig>> rankEntry : holoConfigs.entrySet()) {
      for (Map.Entry<Integer, HologramConfig> slotEntry : rankEntry.getValue().entrySet()) {
        String path = "borders." + rankEntry.getKey() + "." + slotEntry.getKey();
        HologramConfig c = slotEntry.getValue();
        cfg.set(path + ".custom-pos", c.customPos());
        cfg.set(path + ".world", c.world());
        cfg.set(path + ".x", c.x());
        cfg.set(path + ".y", c.y());
        cfg.set(path + ".z", c.z());
        cfg.set(path + ".scale", c.scale());
        cfg.set(path + ".billboard", c.billboard());
        cfg.set(path + ".facing", c.facing());
        cfg.set(path + ".color", c.color());
      }
    }
    try {
      cfg.save(settingsFile());
    } catch (IOException ex) {
      Bukkit.getLogger()
          .severe("[Evolv3] Failed to save border hologram settings: " + ex.getMessage());
    }
  }

  private @NotNull File settingsFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "border-hologram-settings.yml");
  }
}
