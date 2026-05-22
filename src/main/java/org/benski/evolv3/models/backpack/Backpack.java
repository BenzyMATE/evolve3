package org.benski.evolv3.models.backpack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
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
public class Backpack {

  private int maxSize;
  private int totalItems;

  @Getter(AccessLevel.NONE)
  private boolean full;

  private List<BackpackItem> items = new ArrayList<>();

  public static Backpack createDefault() {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    Backpack backpack = new Backpack();
    backpack.maxSize = config.getInt(ConfigPaths.Default.Backpack.SIZE, 100);
    backpack.totalItems = 0;
    backpack.full = false;
    backpack.items = new ArrayList<>();
    return backpack;
  }

  public boolean isFull() {
    return totalItems >= maxSize;
  }

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

  public boolean hasMaterial(Material material) {
    return items.stream().anyMatch(item -> item.getMaterial() == material);
  }

  public int getMaterialAmount(Material material) {
    return items.stream()
        .filter(item -> item.getMaterial() == material)
        .mapToInt(BackpackItem::getAmount)
        .sum();
  }

  public boolean hasEnoughMaterial(Material material, int required) {
    return getMaterialAmount(material) >= required;
  }

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

  public void removeMarkedItems() {
    items.removeIf(BackpackItem::isMarkedForDeletion);
    updateStats();
  }

  public void clear() {
    items.clear();
    updateStats();
  }

  public int getStackCount() {
    return items.size();
  }

  public int getRemainingCapacity() {
    return maxSize - items.size();
  }

  private void updateStats() {
    this.totalItems = items.stream().mapToInt(BackpackItem::getAmount).sum();
    this.full = items.size() >= maxSize;
  }
}
