package org.benski.evolv3.models.backpack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Represents a player's backpack for storing mined items. */
@Getter
@Setter
public final class Backpack {

  private int maxSize;
  private int totalItems;
  private List<BackpackItem> items = new ArrayList<>();

  /** Creates a new backpack with default capacity from {@code default.yml}. */
  public static Backpack createDefault() {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    Backpack backpack = new Backpack();
    backpack.maxSize = config.getInt(ConfigPaths.Default.Backpack.SIZE, 100);
    backpack.totalItems = 0;
    backpack.items = new ArrayList<>();
    return backpack;
  }

  /** Returns {@code true} if the total item count has reached the maximum capacity. */
  public boolean isFull() {
    return totalItems >= maxSize;
  }

  /**
   * Adds items to the backpack, stacking with an existing entry if one exists. Returns {@code
   * false} if the backpack is full or the material is invalid.
   */
  public boolean addItem(Player player, Material material, int amount, boolean evolveItem) {
    if (material == null || material.isAir() || material == Material.PACKED_ICE) return false;
    if (totalItems >= maxSize) return false;

    int clamped = Math.min(amount, maxSize - totalItems);

    Optional<BackpackItem> existing =
        items.stream()
            .filter(item -> item.getMaterial() == material && item.isEvolveItem() == evolveItem)
            .findFirst();

    if (existing.isPresent()) {
      existing.get().addAmount(clamped);
    } else {
      items.add(BackpackItem.create(material, clamped, evolveItem));
    }

    updateStats();
    return true;
  }

  /** Returns {@code true} if the backpack contains at least one stack of the given material. */
  public boolean hasMaterial(Material material) {
    return items.stream().anyMatch(item -> item.getMaterial() == material);
  }

  /** Returns the total count of items with the given material across all stacks. */
  public int getMaterialAmount(Material material) {
    return items.stream()
        .filter(item -> item.getMaterial() == material)
        .mapToInt(BackpackItem::getAmount)
        .sum();
  }

  /** Returns {@code true} if the backpack holds at least {@code required} of the given material. */
  public boolean hasEnoughMaterial(Material material, int required) {
    return getMaterialAmount(material) >= required;
  }

  /**
   * Removes exactly {@code amount} of the given material from the backpack. Returns {@code false}
   * if there is not enough.
   */
  public boolean consumeMaterial(Material material, int amount) {
    if (!hasEnoughMaterial(material, amount)) return false;
    int remaining = amount;
    Iterator<BackpackItem> iterator = items.iterator();
    while (iterator.hasNext() && remaining > 0) {
      BackpackItem item = iterator.next();
      if (item.getMaterial() == material) {
        if (item.getAmount() <= remaining) {
          remaining -= item.getAmount();
          iterator.remove();
        } else {
          item.removeAmount(remaining);
          remaining = 0;
        }
      }
    }
    updateStats();
    return true;
  }

  /** Removes all items that have been marked for deletion and recalculates totals. */
  public void removeMarkedItems() {
    items.removeIf(BackpackItem::isMarkedForDeletion);
    updateStats();
  }

  /** Removes all items from the backpack. */
  public void clear() {
    items.clear();
    updateStats();
  }

  /** Returns the number of distinct item stacks currently in the backpack. */
  public int getStackCount() {
    return items.size();
  }

  /** Returns how many more items can be added before the backpack reaches {@link #maxSize}. */
  public int getRemainingCapacity() {
    return maxSize - totalItems;
  }

  /** Updates stats. */
  private void updateStats() {
    this.totalItems = items.stream().mapToInt(BackpackItem::getAmount).sum();
  }
}
