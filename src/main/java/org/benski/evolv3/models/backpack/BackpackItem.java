package org.benski.evolv3.models.backpack;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

/**
 * Represents an individual item stored in a player's backpack. Tracks material type, quantity, and
 * whether it came from an enchantment effect.
 */
@Getter
@Setter
public final class BackpackItem {

  public boolean markedForDeletion;
  private String id;
  private Material material;
  private int amount;
  private boolean evolveItem;

  /** Creates an empty BackpackItem with a new random UUID and deletion flag cleared. */
  public static BackpackItem create() {
    BackpackItem item = new BackpackItem();
    item.id = UUID.randomUUID().toString();
    item.markedForDeletion = false;
    return item;
  }

  /** Creates a BackpackItem with the given material, amount, and evolve flag. */
  public static BackpackItem create(Material material, int amount, boolean evolveItem) {
    BackpackItem item = new BackpackItem();
    item.id = UUID.randomUUID().toString();
    item.material = material;
    item.amount = amount;
    item.evolveItem = evolveItem;
    item.markedForDeletion = false;
    return item;
  }

  /** Increases the stack amount by the given value. */
  public void addAmount(int additionalAmount) {
    this.amount += additionalAmount;
  }

  /**
   * Decreases the stack amount by the given value. Returns {@code true} if the amount dropped to
   * zero or below.
   */
  public boolean removeAmount(int removeAmount) {
    this.amount -= removeAmount;
    return this.amount <= 0;
  }

  /** Flags this item for removal on the next {@link Backpack#removeMarkedItems()} call. */
  public void markForDeletion() {
    this.markedForDeletion = true;
  }
}
