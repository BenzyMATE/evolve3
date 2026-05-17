package org.benski.evolv3.core.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages permanent island ownership data. Islands are assigned grid positions in the
 * evolv3_islands world.
 */
public final class IslandManager {
  @Getter private static final IslandManager instance = new IslandManager();

  /** Size of each island plot (blocks along each axis). */
  public static final int ISLAND_SIZE = 100;

  /** Gap between island plots in blocks. */
  public static final int ISLAND_GAP = 20;

  /** Total pitch per island slot: size + gap. */
  public static final int ISLAND_PITCH = ISLAND_SIZE + ISLAND_GAP;

  /** Blocks per parcel side. */
  public static final int PARCEL_SIZE = 11;

  /** Number of parcel columns in the island grid menu. */
  public static final int GRID_COLS = 9;

  /** Number of parcel rows in the island grid menu. */
  public static final int GRID_ROWS = 5;

  /** Center parcel slot (row 2, col 4 of the 5×9 grid). */
  public static final int INITIAL_PARCEL = 22;

  /** Cost in coins to purchase one land parcel. */
  public static final double PARCEL_BUY_COST = 1000.0;

  /** Y level of the island spawn platform. */
  public static final int ISLAND_SPAWN_Y = 64;

  public record IslandData(
      UUID ownerUuid,
      String ownerName,
      int gridX,
      int gridZ,
      boolean locked,
      boolean openForVisits,
      List<UUID> members,
      Set<Integer> purchasedSlots) {
    public IslandData withLocked(boolean l) {
      return new IslandData(
          ownerUuid, ownerName, gridX, gridZ, l, openForVisits, members, purchasedSlots);
    }

    public IslandData withOpenForVisits(boolean o) {
      return new IslandData(ownerUuid, ownerName, gridX, gridZ, locked, o, members, purchasedSlots);
    }

    public IslandData withMembers(List<UUID> m) {
      return new IslandData(
          ownerUuid, ownerName, gridX, gridZ, locked, openForVisits, m, purchasedSlots);
    }

    public IslandData withPurchasedSlots(Set<Integer> ps) {
      return new IslandData(ownerUuid, ownerName, gridX, gridZ, locked, openForVisits, members, ps);
    }

    /** World X origin of this island plot. */
    public int worldX() {
      return gridX * ISLAND_PITCH;
    }

    /** World Z origin of this island plot. */
    public int worldZ() {
      return gridZ * ISLAND_PITCH;
    }

    /** Island spawn X — centre of the initial parcel (col 4). */
    public double spawnX() {
      return worldX() + (INITIAL_PARCEL % GRID_COLS) * PARCEL_SIZE + PARCEL_SIZE / 2.0;
    }

    /** Island spawn Z — centre of the initial parcel (row 2). */
    public double spawnZ() {
      return worldZ() + (INITIAL_PARCEL / GRID_COLS) * PARCEL_SIZE + PARCEL_SIZE / 2.0;
    }
  }

  private final Map<UUID, IslandData> islands = new HashMap<>();
  private int nextGridX = 0;
  private int nextGridZ = 0;

  private IslandManager() {}

  public void initialize() {
    loadFromDisk();
  }

  public void cleanup() {
    islands.clear();
  }

  /** Creates a new island for the player, assigning the next grid slot. */
  public @NotNull IslandData createIsland(@NotNull UUID ownerUuid, @NotNull String ownerName) {
    if (islands.containsKey(ownerUuid)) return islands.get(ownerUuid);
    int[] slot = nextSlot();
    Set<Integer> purchased = new HashSet<>();
    purchased.add(INITIAL_PARCEL);
    IslandData data =
        new IslandData(
            ownerUuid, ownerName, slot[0], slot[1], false, false, new ArrayList<>(), purchased);
    islands.put(ownerUuid, data);
    saveToDisk();
    return data;
  }

  public boolean removeIsland(@NotNull UUID ownerUuid) {
    boolean removed = islands.remove(ownerUuid) != null;
    if (removed) saveToDisk();
    return removed;
  }

  public boolean update(@NotNull IslandData data) {
    if (!islands.containsKey(data.ownerUuid())) return false;
    islands.put(data.ownerUuid(), data);
    saveToDisk();
    return true;
  }

  public @Nullable IslandData get(@NotNull UUID ownerUuid) {
    return islands.get(ownerUuid);
  }

  public boolean hasIsland(@NotNull UUID ownerUuid) {
    return islands.containsKey(ownerUuid);
  }

  public @NotNull Collection<IslandData> getAll() {
    return Collections.unmodifiableCollection(islands.values());
  }

  /** Returns all islands with openForVisits=true. */
  public @NotNull List<IslandData> getOpenIslands() {
    return islands.values().stream().filter(IslandData::openForVisits).toList();
  }

  private int[] nextSlot() {
    int x = nextGridX;
    int z = nextGridZ;
    nextGridX++;
    if (nextGridX > 100) {
      nextGridX = 0;
      nextGridZ++;
    }
    return new int[] {x, z};
  }

  private void recalculateNextSlot() {
    int maxX = -1, maxZ = -1;
    for (IslandData d : islands.values()) {
      if (d.gridZ() > maxZ || (d.gridZ() == maxZ && d.gridX() > maxX)) {
        maxZ = d.gridZ();
        maxX = d.gridX();
      }
    }
    if (maxZ < 0) {
      nextGridX = 0;
      nextGridZ = 0;
      return;
    }
    nextGridX = maxX + 1;
    nextGridZ = maxZ;
    if (nextGridX > 100) {
      nextGridX = 0;
      nextGridZ = maxZ + 1;
    }
  }

  public void loadFromDisk() {
    islands.clear();
    File file = dataFile();
    if (!file.exists()) return;
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    if (!cfg.isConfigurationSection("islands")) return;
    for (String uuidStr : cfg.getConfigurationSection("islands").getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(uuidStr);
        String p = "islands." + uuidStr;
        List<UUID> members = new ArrayList<>();
        for (String m : cfg.getStringList(p + ".members")) {
          try {
            members.add(UUID.fromString(m));
          } catch (IllegalArgumentException ignored) {
          }
        }
        Set<Integer> purchasedSlots = new HashSet<>(cfg.getIntegerList(p + ".purchased-slots"));
        if (purchasedSlots.isEmpty()) purchasedSlots.add(INITIAL_PARCEL);
        IslandData data =
            new IslandData(
                uuid,
                cfg.getString(p + ".owner-name", ""),
                cfg.getInt(p + ".grid-x", 0),
                cfg.getInt(p + ".grid-z", 0),
                cfg.getBoolean(p + ".locked", false),
                cfg.getBoolean(p + ".open-for-visits", false),
                members,
                purchasedSlots);
        islands.put(uuid, data);
      } catch (Exception e) {
        Bukkit.getLogger()
            .warning("[Evolv3] Failed to load island for " + uuidStr + ": " + e.getMessage());
      }
    }
    recalculateNextSlot();
  }

  private void saveToDisk() {
    YamlConfiguration cfg = new YamlConfiguration();
    for (IslandData data : islands.values()) {
      String p = "islands." + data.ownerUuid();
      cfg.set(p + ".owner-name", data.ownerName());
      cfg.set(p + ".grid-x", data.gridX());
      cfg.set(p + ".grid-z", data.gridZ());
      cfg.set(p + ".locked", data.locked());
      cfg.set(p + ".open-for-visits", data.openForVisits());
      cfg.set(p + ".members", data.members().stream().map(UUID::toString).toList());
      cfg.set(p + ".purchased-slots", new ArrayList<>(data.purchasedSlots()));
    }
    try {
      cfg.save(dataFile());
    } catch (IOException e) {
      Bukkit.getLogger().severe("[Evolv3] Failed to save islands.yml: " + e.getMessage());
    }
  }

  private @NotNull File dataFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "islands.yml");
  }
}
