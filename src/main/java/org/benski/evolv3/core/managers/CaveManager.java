package org.benski.evolv3.core.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.managers.MineManager.Mine;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.items.AncientResin;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.items.Scroll;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages cave schematic pasting into mine walls, block backup/restore, and cave region tracking
 * for the cave entry/exit listener.
 *
 * <p>Caves are pasted into the walls surrounding a mine's cuboid region, rotated to face inward.
 * Original blocks are backed up so they can be restored when the mine resets.
 *
 * <p>Cave data is persisted in {@code dataManager/caves.yml} to survive server restarts.
 */
public final class CaveManager {
  @Getter private static final CaveManager instance = new CaveManager();

  /** Backed-up blocks for each cave: caveKey → list of (material, x, y, z, worldName). */
  private final Map<String, List<BlockBackup>> caveBackups = new HashMap<>();

  /** Bounding box of each pasted cave for entry/exit detection. */
  private final Map<String, CaveBounds> caveRegions = new HashMap<>();

  /** Chest block locations for each cave: caveKey → list of chest locations. */
  private final Map<String, List<Location>> caveChestLocations = new HashMap<>();

  /** Maps each cave key to its owning mine name (for spawn lookup on despawn). */
  private final Map<String, String> caveMineMap = new ConcurrentHashMap<>();

  /** Per-instance tier (1–4) for each pasted cave, assigned randomly at paste time. */
  private final Map<String, Integer> caveInstanceTiers = new ConcurrentHashMap<>();

  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final Random RANDOM = new Random();
  private static final int MAX_PLACEMENT_ATTEMPTS = 500;

  /** Wall indices: 0=left(-X), 1=right(+X), 2=front(-Z), 3=back(+Z) */
  private static final int WALL_COUNT = 4;

  private CaveManager() {}

  private record BlockBackup(Material material, String world, int x, int y, int z) {}

  /** AABB bounds of a pasted cave, used for player entry/exit detection. */
  public record CaveBounds(
      String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public boolean contains(int bx, int by, int bz) {
      return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    public boolean contains(@NotNull Location loc) {
      if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
      return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
  }

  /**
   * Generates caves around a mine's walls. Caves are pasted one per tick to avoid freezing the
   * server on large operations.
   */
  public void generateCaves(@NotNull Mine mine) {
    int caveCount = mine.caveCount();
    if (caveCount <= 0) return;

    File schematicFile = new File(Main.getInstance().getDataFolder(), "schematics/cave.schem");
    if (!schematicFile.exists()) return;

    int minDist = mine.caveMinDistance();
    List<BlockVector3> usedLocations = new ArrayList<>();
    AtomicInteger placed = new AtomicInteger(0);

    Bukkit.getScheduler()
        .runTask(
            Main.getInstance(),
            new Runnable() {
              @Override
              public void run() {
                if (placed.get() >= caveCount) {
                  saveCaveData();
                  return;
                }

                int wall = RANDOM.nextInt(WALL_COUNT);
                BlockVector3 location = findCaveLocation(mine, wall, usedLocations, minDist);

                if (location != null) {
                  usedLocations.add(location);
                  World world = Bukkit.getWorld(mine.world());
                  if (world != null) {
                    pasteCave(
                        new Location(world, location.x(), location.y(), location.z()),
                        schematicFile,
                        mine,
                        wall);
                  }
                  placed.incrementAndGet();
                }

                Bukkit.getScheduler().runTaskLater(Main.getInstance(), this, 1L);
              }
            });
  }

  /**
   * Finds a valid cave placement location on the specified wall of the mine, respecting minimum
   * distance from other caves and staying outside the mine.
   */
  private @NotNull BlockVector3 findCaveLocation(
      @NotNull Mine mine, int wall, @NotNull List<BlockVector3> used, int minDist) {
    int offset = 1;
    int minY = mine.minY() + 3;
    int maxY = mine.maxY() - 6;
    if (maxY <= minY) maxY = minY + 1;

    int minX = mine.minX() + 2, maxX = mine.maxX() - 2;
    int minZ = mine.minZ() + 2, maxZ = mine.maxZ() - 2;

    for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
      int ry = RANDOM.nextInt(maxY - minY + 1) + minY;
      BlockVector3 loc =
          switch (wall) {
            case 0 ->
                BlockVector3.at(mine.minX() - offset, ry, RANDOM.nextInt(maxZ - minZ + 1) + minZ);
            case 1 ->
                BlockVector3.at(mine.maxX() + offset, ry, RANDOM.nextInt(maxZ - minZ + 1) + minZ);
            case 2 ->
                BlockVector3.at(RANDOM.nextInt(maxX - minX + 1) + minX, ry, mine.minZ() - offset);
            case 3 ->
                BlockVector3.at(RANDOM.nextInt(maxX - minX + 1) + minX, ry, mine.maxZ() + offset);
            default -> BlockVector3.at(mine.minX(), mine.minY(), mine.minZ());
          };

      boolean tooClose = false;
      for (BlockVector3 existing : used) {
        if (loc.distance(existing) < minDist) {
          tooClose = true;
          break;
        }
      }
      if (!tooClose) return loc;
    }

    return BlockVector3.at(
        mine.minX() - 1,
        RANDOM.nextInt(maxY - minY + 1) + minY,
        RANDOM.nextInt(maxZ - minZ + 1) + minZ);
  }

  /**
   * Pastes a cave schematic at the given location, rotated to face into the mine. Backs up original
   * blocks before pasting.
   */
  private void pasteCave(
      @NotNull Location location, @NotNull File schematicFile, @NotNull Mine mine, int wall) {
    ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
    if (format == null) return;

    try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
      if (location.getWorld() == null) return;

      com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());
      Clipboard clipboard = reader.read();

      AffineTransform transform = new AffineTransform();
      transform =
          switch (wall) {
            case 0 -> transform.rotateY(90);
            case 1 -> transform.rotateY(-90);
            case 2 -> transform.rotateY(0);
            case 3 -> transform.rotateY(180);
            default -> transform;
          };

      ClipboardHolder holder = new ClipboardHolder(clipboard);
      holder.setTransform(transform);

      BlockVector3 pastePos =
          BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());

      BlockVector3 dims = clipboard.getDimensions();
      BlockVector3 origin = clipboard.getOrigin().subtract(clipboard.getMinimumPoint());

      BlockVector3 clipMin = clipboard.getMinimumPoint();
      BlockVector3 clipMax = clipboard.getMaximumPoint();
      BlockVector3 clipOrigin = clipboard.getOrigin();

      int cxMin = clipMin.x() - clipOrigin.x();
      int cyMin = clipMin.y() - clipOrigin.y();
      int czMin = clipMin.z() - clipOrigin.z();
      int cxMax = clipMax.x() - clipOrigin.x();
      int cyMax = clipMax.y() - clipOrigin.y();
      int czMax = clipMax.z() - clipOrigin.z();

      Vector3[] corners = {
        Vector3.at(cxMin, cyMin, czMin), Vector3.at(cxMax, cyMin, czMin),
        Vector3.at(cxMin, cyMax, czMin), Vector3.at(cxMin, cyMin, czMax),
        Vector3.at(cxMax, cyMax, czMin), Vector3.at(cxMax, cyMin, czMax),
        Vector3.at(cxMin, cyMax, czMax), Vector3.at(cxMax, cyMax, czMax)
      };

      double wMinX = Double.MAX_VALUE, wMinY = Double.MAX_VALUE, wMinZ = Double.MAX_VALUE;
      double wMaxX = -Double.MAX_VALUE, wMaxY = -Double.MAX_VALUE, wMaxZ = -Double.MAX_VALUE;

      for (Vector3 corner : corners) {
        Vector3 rotated = transform.apply(corner);
        double wx = pastePos.x() + rotated.x();
        double wy = pastePos.y() + rotated.y();
        double wz = pastePos.z() + rotated.z();
        wMinX = Math.min(wMinX, wx);
        wMinY = Math.min(wMinY, wy);
        wMinZ = Math.min(wMinZ, wz);
        wMaxX = Math.max(wMaxX, wx);
        wMaxY = Math.max(wMaxY, wy);
        wMaxZ = Math.max(wMaxZ, wz);
      }

      BlockVector3 minPt =
          BlockVector3.at(
              (int) Math.floor(wMinX), (int) Math.floor(wMinY), (int) Math.floor(wMinZ));
      BlockVector3 maxPt =
          BlockVector3.at(
              (int) Math.floor(wMaxX), (int) Math.floor(wMaxY), (int) Math.floor(wMaxZ));

      List<BlockBackup> backups = new ArrayList<>();
      String worldName = location.getWorld().getName();
      for (int x = 0; x < dims.x(); x++) {
        for (int y = 0; y < dims.y(); y++) {
          for (int z = 0; z < dims.z(); z++) {
            BlockVector3 rel = BlockVector3.at(x, y, z);
            BlockVector3 transformed =
                transform
                    .apply(rel.subtract(origin).toVector3())
                    .add(pastePos.x(), pastePos.y(), pastePos.z())
                    .toBlockPoint();

            if (mine.contains(transformed.x(), transformed.y(), transformed.z())) continue;

            Block block =
                location.getWorld().getBlockAt(transformed.x(), transformed.y(), transformed.z());
            backups.add(
                new BlockBackup(
                    block.getType(), worldName, transformed.x(), transformed.y(), transformed.z()));
          }
        }
      }

      String caveKey =
          mine.name()
              + "_"
              + location.getBlockX()
              + "_"
              + location.getBlockY()
              + "_"
              + location.getBlockZ();
      caveBackups.put(caveKey, backups);
      caveRegions.put(
          caveKey,
          new CaveBounds(
              worldName, minPt.x(), minPt.y(), minPt.z(), maxPt.x(), maxPt.y(), maxPt.z()));
      caveMineMap.put(caveKey, mine.name());
      caveInstanceTiers.put(caveKey, pickRandomTier());

      try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
        Operation operation =
            holder.createPaste(editSession).to(pastePos).ignoreAirBlocks(false).build();
        Operations.complete(operation);
      } catch (WorldEditException e) {
        Bukkit.getLogger().severe("[Evolv3] Failed to paste cave: " + e.getMessage());
      }

      final String finalCaveKey = caveKey;
      final BlockVector3 finalMin = minPt;
      final BlockVector3 finalMax = maxPt;
      Bukkit.getScheduler()
          .runTaskLater(
              Main.getInstance(),
              () -> scanAndFillChests(finalCaveKey, location.getWorld(), finalMin, finalMax),
              1L);
    } catch (IOException e) {
      Bukkit.getLogger().severe("[Evolv3] Failed to read cave schematic: " + e.getMessage());
    }
  }

  /**
   * Scans the pasted cave region for chest blocks and fills them with placeholder reward items from
   * the {@code mines.yml} configuration.
   */
  private void scanAndFillChests(
      @NotNull String caveKey,
      @NotNull World world,
      @NotNull BlockVector3 minPt,
      @NotNull BlockVector3 maxPt) {
    List<Location> chests = new ArrayList<>();

    for (int x = minPt.x(); x <= maxPt.x(); x++) {
      for (int y = minPt.y(); y <= maxPt.y(); y++) {
        for (int z = minPt.z(); z <= maxPt.z(); z++) {
          Block block = world.getBlockAt(x, y, z);
          Material type = block.getType();
          if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
            chests.add(block.getLocation());
            fillChestWithRewards(block, caveKey);
          }
        }
      }
    }

    if (chests.isEmpty()) {
      despawnCave(caveKey);
    } else {
      caveChestLocations.put(caveKey, chests);
    }
  }

  /**
   * Fills a chest block with weighted-random reward items determined by the cave tier of the owning
   * mine (read from {@code cave-tiers} in mines.yml).
   */
  private void fillChestWithRewards(@NotNull Block block, @NotNull String caveKey) {
    if (!(block.getState() instanceof Chest chest)) return;

    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MINES);
    if (cfg == null) return;

    int tier = caveInstanceTiers.getOrDefault(caveKey, 1);
    ConfigurationSection tierSection = cfg.getConfigurationSection("cave-tiers." + tier);
    if (tierSection == null) tierSection = cfg.getConfigurationSection("cave-tiers.1");
    if (tierSection == null) return;

    int chestItems = tierSection.getInt("chest-items", 4);
    List<Map<?, ?>> rewardList = tierSection.getMapList("rewards");
    if (rewardList.isEmpty()) return;

    int totalWeight = 0;
    for (Map<?, ?> entry : rewardList) {
      totalWeight += numValue(entry.get("weight"), 1);
    }
    if (totalWeight <= 0) return;

    chest.getBlockInventory().clear();

    List<ItemStack> selected = new ArrayList<>();
    for (int i = 0; i < chestItems; i++) {
      int roll = RANDOM.nextInt(totalWeight);
      int cumulative = 0;
      for (Map<?, ?> entry : rewardList) {
        cumulative += numValue(entry.get("weight"), 1);
        if (roll < cumulative) {
          String type = (String) entry.get("type");
          int min = numValue(entry.get("min"), 1);
          int max = numValue(entry.get("max"), 1);
          int amount = (min >= max) ? min : min + RANDOM.nextInt(max - min + 1);
          String rarity = entry.get("rarity") instanceof String s ? s : null;
          ItemStack item = createScrollItem(type, amount, rarity);
          if (item != null) selected.add(item);
          break;
        }
      }
    }

    int invSize = chest.getBlockInventory().getSize();
    int[] slots = new int[invSize];
    for (int i = 0; i < invSize; i++) slots[i] = i;
    for (int i = invSize - 1; i > 0; i--) {
      int j = RANDOM.nextInt(i + 1);
      int tmp = slots[i];
      slots[i] = slots[j];
      slots[j] = tmp;
    }
    for (int i = 0; i < selected.size() && i < invSize; i++) {
      chest.getBlockInventory().setItem(slots[i], selected.get(i));
    }
  }

  private static int numValue(@Nullable Object value, int fallback) {
    return (value instanceof Number n) ? n.intValue() : fallback;
  }

  /** Maps a cave-tier reward type to the corresponding item. */
  private static @Nullable ItemStack createScrollItem(
      @Nullable String type, int amount, @Nullable String rarity) {
    if (type == null) return null;
    return switch (type) {
      case "MONEY" -> Scroll.createCoins(amount);
      case "ESSENCE" -> Scroll.createEssence(amount);
      case "ATOMS" -> Scroll.createAtoms(amount);
      case "PICKAXE_XP" -> Scroll.createPickaxeXP(amount);
      case "BACKPACK_SLOTS" -> Scroll.createBackpackSlots(amount);
      case "RESIN" -> AncientResin.create(amount);
      case "EVOLUTION_STONE" -> {
        RarityConfig rc = (rarity != null) ? EnchantAnimationConfig.getRarityByName(rarity) : null;
        if (rc == null) {
          List<RarityConfig> rarities = EnchantAnimationConfig.getRarities();
          rc = rarities.isEmpty() ? null : rarities.get(0);
        }
        if (rc == null) yield null;
        List<String> pool = EnchantAnimationConfig.getEnchantsForRarity(rc.displayName());
        if (pool.isEmpty()) yield null;
        String enchant = pool.get(RANDOM.nextInt(pool.size()));
        yield EvolutionStone.create(rc, enchant);
      }
      default -> null;
    };
  }

  /**
   * Despawns a single cave: restores backed-up blocks, removes from all tracking, and persists the
   * change. Called when a player empties all chest items.
   *
   * @param caveKey the unique key for the cave (e.g. "minename_x_y_z")
   */
  public void despawnCave(@NotNull String caveKey) {
    List<BlockBackup> backups = caveBackups.get(caveKey);
    if (backups != null) {
      for (BlockBackup b : backups) {
        World world = Bukkit.getWorld(b.world());
        if (world == null) continue;
        world.getBlockAt(b.x(), b.y(), b.z()).setType(b.material());
      }
    }

    caveBackups.remove(caveKey);
    caveRegions.remove(caveKey);
    caveChestLocations.remove(caveKey);
    caveMineMap.remove(caveKey);
    caveInstanceTiers.remove(caveKey);

    saveCaveData();
  }

  /**
   * Returns the cave key that owns the chest at the given location, or null if the location is not
   * a tracked cave chest.
   */
  public @Nullable String getCaveKeyForChest(@NotNull Location loc) {
    if (loc.getWorld() == null) return null;

    for (Map.Entry<String, List<Location>> entry : caveChestLocations.entrySet()) {
      CaveBounds bounds = caveRegions.get(entry.getKey());
      if (bounds == null) continue;
      if (!bounds.world().equals(loc.getWorld().getName())) continue;

      for (Location chestLoc : entry.getValue()) {
        if (chestLoc.getBlockX() == loc.getBlockX()
            && chestLoc.getBlockY() == loc.getBlockY()
            && chestLoc.getBlockZ() == loc.getBlockZ()) {
          return entry.getKey();
        }
      }
    }
    return null;
  }

  /** Returns the mine name associated with a cave key, or null. */
  public @Nullable String getMineName(@NotNull String caveKey) {
    return caveMineMap.get(caveKey);
  }

  /**
   * Counts the number of active (currently spawned) caves for a given mine.
   *
   * @param mineName the mine name (case-insensitive)
   * @return the number of active caves belonging to this mine
   */
  public int getActiveCaveCount(@NotNull String mineName) {
    String prefix = mineName.toLowerCase() + "_";
    int count = 0;
    for (String key : caveRegions.keySet()) {
      if (key.startsWith(prefix)) count++;
    }
    return count;
  }

  /**
   * Returns the cave key for the cave region containing the given location, or null.
   *
   * @param loc the location to check
   * @return the cave key, or null if the location is not inside any cave
   */
  public @Nullable String getCaveKeyAt(@NotNull Location loc) {
    for (Map.Entry<String, CaveBounds> entry : caveRegions.entrySet()) {
      if (entry.getValue().contains(loc)) {
        return entry.getKey();
      }
    }
    return null;
  }

  /** Restores all caves for a mine, undoing the schematic pastes. */
  public void undoCaves(@NotNull String mineName) {
    String prefix = mineName.toLowerCase() + "_";
    List<String> toRemove = new ArrayList<>();

    for (Map.Entry<String, List<BlockBackup>> entry : caveBackups.entrySet()) {
      if (!entry.getKey().startsWith(prefix)) continue;
      toRemove.add(entry.getKey());

      for (BlockBackup backup : entry.getValue()) {
        World world = Bukkit.getWorld(backup.world());
        if (world == null) continue;
        world.getBlockAt(backup.x(), backup.y(), backup.z()).setType(backup.material());
      }
    }

    for (String key : toRemove) {
      caveBackups.remove(key);
      caveRegions.remove(key);
      caveChestLocations.remove(key);
      caveMineMap.remove(key);
      caveInstanceTiers.remove(key);
    }

    saveCaveData();
  }

  /** Clears all cave data for a mine without restoring blocks (used on mine removal). */
  public void clearCaves(@NotNull String mineName) {
    String prefix = mineName.toLowerCase() + "_";
    caveBackups.keySet().removeIf(k -> k.startsWith(prefix));
    caveRegions.keySet().removeIf(k -> k.startsWith(prefix));
    caveChestLocations.keySet().removeIf(k -> k.startsWith(prefix));
    caveMineMap.keySet().removeIf(k -> k.startsWith(prefix));
    caveInstanceTiers.keySet().removeIf(k -> k.startsWith(prefix));
    saveCaveData();
  }

  /** Returns the cave key if the location is inside any cave region, or null. */
  public @NotNull Map<String, CaveBounds> getAllCaveRegions() {
    return caveRegions;
  }

  /**
   * Returns the tier (1–4) assigned to a specific cave instance at paste time. Defaults to 1 if no
   * tier has been recorded for the cave key.
   */
  public int getCaveTierForCave(@Nullable String caveKey) {
    if (caveKey == null) return 1;
    return caveInstanceTiers.getOrDefault(caveKey, 1);
  }

  private static int pickRandomTier() {
    int roll = RANDOM.nextInt(100);
    if (roll < 10) return 4;
    if (roll < 30) return 3;
    if (roll < 60) return 2;
    return 1;
  }

  /** Saves cave backups and regions to disk. */
  public void saveCaveData() {
    File file = getCavesFile();
    YamlConfiguration cfg = new YamlConfiguration();

    for (Map.Entry<String, List<BlockBackup>> entry : caveBackups.entrySet()) {
      List<Map<String, Object>> list = new ArrayList<>();
      for (BlockBackup b : entry.getValue()) {
        Map<String, Object> map = new HashMap<>();
        map.put("material", b.material().name());
        map.put("world", b.world());
        map.put("x", b.x());
        map.put("y", b.y());
        map.put("z", b.z());
        list.add(map);
      }
      cfg.set("backups." + entry.getKey(), list);
    }

    for (Map.Entry<String, CaveBounds> entry : caveRegions.entrySet()) {
      CaveBounds b = entry.getValue();
      String path = "regions." + entry.getKey();
      cfg.set(path + ".world", b.world());
      cfg.set(path + ".minX", b.minX());
      cfg.set(path + ".minY", b.minY());
      cfg.set(path + ".minZ", b.minZ());
      cfg.set(path + ".maxX", b.maxX());
      cfg.set(path + ".maxY", b.maxY());
      cfg.set(path + ".maxZ", b.maxZ());
    }

    for (Map.Entry<String, List<Location>> entry : caveChestLocations.entrySet()) {
      List<Map<String, Object>> list = new ArrayList<>();
      for (Location loc : entry.getValue()) {
        Map<String, Object> map = new HashMap<>();
        map.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
        map.put("x", loc.getBlockX());
        map.put("y", loc.getBlockY());
        map.put("z", loc.getBlockZ());
        list.add(map);
      }
      cfg.set("chests." + entry.getKey(), list);
    }

    for (Map.Entry<String, String> entry : caveMineMap.entrySet()) {
      cfg.set("mines." + entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String, Integer> entry : caveInstanceTiers.entrySet()) {
      cfg.set("tiers." + entry.getKey(), entry.getValue());
    }

    try {
      cfg.save(file);
    } catch (IOException e) {
      Bukkit.getLogger().severe("[Evolv3] Failed to save caves data: " + e.getMessage());
    }
  }

  /** Loads cave backups and regions from disk. */
  public void loadCaveData() {
    caveBackups.clear();
    caveRegions.clear();
    caveChestLocations.clear();
    caveMineMap.clear();

    File file = getCavesFile();
    if (!file.exists()) return;

    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

    ConfigurationSection backupsSection = cfg.getConfigurationSection("backups");
    if (backupsSection != null) {
      for (String key : backupsSection.getKeys(false)) {
        List<Map<?, ?>> list = cfg.getMapList("backups." + key);
        List<BlockBackup> backups = new ArrayList<>();
        for (Map<?, ?> map : list) {
          Material mat = Material.matchMaterial((String) map.get("material"));
          if (mat == null) mat = Material.STONE;
          String world = (String) map.get("world");
          int x = ((Number) map.get("x")).intValue();
          int y = ((Number) map.get("y")).intValue();
          int z = ((Number) map.get("z")).intValue();
          backups.add(new BlockBackup(mat, world, x, y, z));
        }
        caveBackups.put(key, backups);
      }
    }

    ConfigurationSection regionsSection = cfg.getConfigurationSection("regions");
    if (regionsSection != null) {
      for (String key : regionsSection.getKeys(false)) {
        String path = "regions." + key;
        String world = cfg.getString(path + ".world", "world");
        int minX = cfg.getInt(path + ".minX");
        int minY = cfg.getInt(path + ".minY");
        int minZ = cfg.getInt(path + ".minZ");
        int maxX = cfg.getInt(path + ".maxX");
        int maxY = cfg.getInt(path + ".maxY");
        int maxZ = cfg.getInt(path + ".maxZ");
        caveRegions.put(key, new CaveBounds(world, minX, minY, minZ, maxX, maxY, maxZ));
      }
    }

    ConfigurationSection chestsSection = cfg.getConfigurationSection("chests");
    if (chestsSection != null) {
      for (String key : chestsSection.getKeys(false)) {
        List<Map<?, ?>> list = cfg.getMapList("chests." + key);
        List<Location> locs = new ArrayList<>();
        for (Map<?, ?> map : list) {
          String worldName = (String) map.get("world");
          int x = ((Number) map.get("x")).intValue();
          int y = ((Number) map.get("y")).intValue();
          int z = ((Number) map.get("z")).intValue();
          World world = Bukkit.getWorld(worldName);
          locs.add(new Location(world, x, y, z));
        }
        if (!locs.isEmpty()) {
          caveChestLocations.put(key, locs);
        }
      }
    }

    ConfigurationSection minesSection = cfg.getConfigurationSection("mines");
    if (minesSection != null) {
      for (String key : minesSection.getKeys(false)) {
        caveMineMap.put(key, cfg.getString("mines." + key));
      }
    }

    ConfigurationSection tiersSection = cfg.getConfigurationSection("tiers");
    if (tiersSection != null) {
      for (String key : tiersSection.getKeys(false)) {
        caveInstanceTiers.put(key, cfg.getInt("tiers." + key, 1));
      }
    }

    Bukkit.getScheduler()
        .runTaskLater(
            Main.getInstance(),
            () -> {
              List<String> orphaned = new ArrayList<>();
              for (String key : caveRegions.keySet()) {
                List<Location> chests = caveChestLocations.get(key);
                if (chests == null || chests.isEmpty()) {
                  orphaned.add(key);
                }
              }
              for (String key : orphaned) {
                despawnCave(key);
              }
            },
            40L);
  }

  private @NotNull File getCavesFile() {
    File dir = new File(Main.getInstance().getDataFolder(), "localdata");
    if (!dir.exists()) dir.mkdirs();
    return new File(dir, "caves.yml");
  }

  public void cleanup() {
    saveCaveData();
    caveBackups.clear();
    caveRegions.clear();
    caveChestLocations.clear();
    caveMineMap.clear();
    caveInstanceTiers.clear();
  }
}
