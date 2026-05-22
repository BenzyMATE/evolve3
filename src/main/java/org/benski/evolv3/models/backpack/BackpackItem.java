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
public class BackpackItem {

  public boolean markedForDeletion;
  private String id;
  private Material material;
  private int amount;
  private boolean evolveItem;

  public static BackpackItem create() {
    BackpackItem item = new BackpackItem();
    item.id = UUID.randomUUID().toString();
    item.markedForDeletion = false;
    return item;
  }

  public static BackpackItem create(Material material, int amount, boolean evolveItem) {
    BackpackItem item = new BackpackItem();
    item.id = UUID.randomUUID().toString();
    item.material = material;
    item.amount = amount;
    item.evolveItem = evolveItem;
    item.markedForDeletion = false;
    return item;
  }

  public void addAmount(int additionalAmount) {
    this.amount += additionalAmount;
  }

  public boolean removeAmount(int removeAmount) {
    this.amount -= removeAmount;
    return this.amount <= 0;
  }

  public void markForDeletion() {
    this.markedForDeletion = true;
  }
}
