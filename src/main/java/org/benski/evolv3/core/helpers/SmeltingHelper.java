package org.benski.evolv3.core.helpers;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Static utility for furnace smelting lookups. Provides a lazily-initialized recipe cache and a
 * static fuel burn-time map.
 */
public final class SmeltingHelper {
  /** Creates a new SmeltingHelper instance. */
  private SmeltingHelper() {}

  /** Ticks required to smelt one item in a vanilla furnace. */
  public static final int COOK_TIME_TICKS = 200;

  private static volatile Map<Material, FurnaceRecipe> recipeCache;
  private static final Object CACHE_LOCK = new Object();

  /** Vanilla fuel burn times in ticks (one item unit of the given fuel). */
  private static final Map<Material, Integer> FUEL_BURN_TIMES;

  static {
    Map<Material, Integer> fuels = new EnumMap<>(Material.class);

    fuels.put(Material.LAVA_BUCKET, 20000);
    fuels.put(Material.COAL_BLOCK, 16000);
    fuels.put(Material.BLAZE_ROD, 2400);
    fuels.put(Material.BLAZE_POWDER, 1200);
    fuels.put(Material.DRIED_KELP_BLOCK, 4000);
    fuels.put(Material.BAMBOO_BLOCK, 2500);
    fuels.put(Material.COAL, 1600);
    fuels.put(Material.CHARCOAL, 1600);

    for (Material m : Material.values()) {
      String name = m.name();
      if (name.endsWith("_LOG") || (name.endsWith("_WOOD") && !name.contains("DEAD"))) {
        fuels.put(m, 300);
      }
    }

    for (Material m : Material.values()) {
      if (m.name().endsWith("_PLANKS")) {
        fuels.put(m, 300);
      }
    }

    for (Material m : Material.values()) {
      String name = m.name();
      if (name.endsWith("_FENCE") && !name.contains("NETHER") && !name.contains("GATE")) {
        fuels.put(m, 300);
      }
    }

    for (Material m : Material.values()) {
      String name = m.name();
      if (name.endsWith("_TRAPDOOR") && !name.startsWith("IRON")) {
        fuels.put(m, 300);
      }
    }

    for (Material m : Material.values()) {
      String name = m.name();
      if (name.endsWith("_STAIRS") && isWoodType(name)) {
        fuels.put(m, 300);
      }
    }

    for (Material m : Material.values()) {
      String name = m.name();
      if (name.endsWith("_SLAB") && isWoodType(name)) {
        fuels.put(m, 150);
      }
    }

    for (Material m : Material.values()) {
      String name = m.name();
      if (name.endsWith("_DOOR") && isWoodType(name)) {
        fuels.put(m, 200);
      }
    }

    for (Material m : Material.values()) {
      if (m.name().startsWith("WOODEN_")) {
        fuels.put(m, 200);
      }
    }

    for (Material m : Material.values()) {
      String name = m.name();
      if (name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT")) {
        fuels.put(m, 1200);
      }
    }

    for (Material m : Material.values()) {
      if (m.name().endsWith("_SAPLING")) {
        fuels.put(m, 100);
      }
    }

    for (Material m : Material.values()) {
      if (m.name().endsWith("_WOOL")) {
        fuels.put(m, 100);
      }
    }

    fuels.put(Material.BOW, 300);
    fuels.put(Material.FISHING_ROD, 300);
    fuels.put(Material.CROSSBOW, 300);
    fuels.put(Material.NOTE_BLOCK, 300);
    fuels.put(Material.JUKEBOX, 300);
    fuels.put(Material.CRAFTING_TABLE, 300);
    fuels.put(Material.CHEST, 300);
    fuels.put(Material.BOOKSHELF, 300);
    fuels.put(Material.BOOK, 300);

    fuels.put(Material.DEAD_BUSH, 100);
    fuels.put(Material.CARVED_PUMPKIN, 100);

    fuels.put(Material.BAMBOO, 50);

    fuels.put(Material.STICK, 100);

    FUEL_BURN_TIMES = fuels;
  }

  /** Returns true if the material name contains a wood-type identifier. */
  private static boolean isWoodType(String name) {
    return name.contains("OAK")
        || name.contains("SPRUCE")
        || name.contains("BIRCH")
        || name.contains("JUNGLE")
        || name.contains("ACACIA")
        || name.contains("DARK_OAK")
        || name.contains("MANGROVE")
        || name.contains("CHERRY")
        || name.contains("BAMBOO")
        || name.contains("CRIMSON")
        || name.contains("WARPED");
  }

  /**
   * Returns the lazily-initialized smelting recipe cache. Uses double-checked locking for thread
   * safety.
   */
  @SuppressWarnings("deprecation")
  private static Map<Material, FurnaceRecipe> getRecipeCache() {
    if (recipeCache == null) {
      synchronized (CACHE_LOCK) {
        if (recipeCache == null) {
          Map<Material, FurnaceRecipe> cache = new EnumMap<>(Material.class);
          Iterator<Recipe> it = Bukkit.recipeIterator();
          while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof FurnaceRecipe furnace) {
              cache.putIfAbsent(furnace.getInput().getType(), furnace);
            }
          }
          recipeCache = cache;
        }
      }
    }
    return recipeCache;
  }

  /** Invalidates the recipe cache. Call this after recipes are added or removed at runtime. */
  public static void invalidateCache() {
    synchronized (CACHE_LOCK) {
      recipeCache = null;
    }
  }

  /** Returns true if the given material can be smelted in a furnace. */
  public static boolean isSmeltable(Material material) {
    return getRecipeCache().containsKey(material);
  }

  /** Returns the FurnaceRecipe for the given input material, or null if none exists. */
  public static FurnaceRecipe getRecipe(Material material) {
    return getRecipeCache().get(material);
  }

  /** Returns the smelting result ItemStack for the given material, or null if not smeltable. */
  public static ItemStack getSmeltResult(Material material) {
    FurnaceRecipe recipe = getRecipe(material);
    return recipe != null ? recipe.getResult() : null;
  }

  /** Returns true if the given material can be used as furnace fuel. */
  public static boolean isFuel(Material material) {
    return FUEL_BURN_TIMES.containsKey(material);
  }

  /** Returns how many ticks one unit of the given fuel burns, or 0 if it is not a fuel. */
  public static int getFuelBurnTime(Material material) {
    return FUEL_BURN_TIMES.getOrDefault(material, 0);
  }

  /**
   * Returns how many items one unit of the given fuel can fully smelt. Result is at least 1 for any
   * valid fuel.
   */
  public static int getItemsPerFuel(Material fuel) {
    int burnTime = getFuelBurnTime(fuel);
    if (burnTime <= 0) return 0;
    return Math.max(1, burnTime / COOK_TIME_TICKS);
  }

  /**
   * Returns the number of fuel units needed to smelt the given number of items. Uses ceiling
   * division to account for partial fuel burn.
   */
  public static int fuelNeeded(int itemCount, Material fuel) {
    double ipf = getFuelBurnTime(fuel) / (double) COOK_TIME_TICKS;
    if (ipf <= 0) return Integer.MAX_VALUE;
    return (int) Math.ceil(itemCount / ipf);
  }
}
