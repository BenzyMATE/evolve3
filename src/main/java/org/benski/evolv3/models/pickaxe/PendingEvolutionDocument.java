package org.benski.evolv3.models.pickaxe;

import java.util.Base64;
import java.util.function.Consumer;
import org.benski.evolv3.Main;
import org.bukkit.inventory.ItemStack;

/**
 * Holds in-progress evolution animation state for a player. Pending evolutions survive player
 * disconnects within a session but are not persisted across server restarts.
 */
public final class PendingEvolutionDocument {

  /** The player's UUID string — primary key. */
  public String playerUuid;

  /** Base64-encoded {@link ItemStack#serializeAsBytes()} of the pickaxe. */
  public String originalItemBase64;

  /** {@link org.benski.evolv3.enums.EvolutionTier#level} before evolution. */
  public int currentTierLevel;

  /** {@link org.benski.evolv3.enums.EvolutionTier#level} after evolution. */
  public int nextTierLevel;

  /** Display name of the rolled rarity stone. */
  public String rarityName;

  /** The pickaxe's item-data key. */
  public String pickaxeKey;

  /**
   * {@code true} if the evolution callback fired before the player disconnected (tier change
   * applied) but items were not yet delivered.
   */
  public boolean callbackAlreadyApplied;

  /** Returns the database collection name for this document type. */
  public String getCollectionName() {
    return "PendingEvolutions";
  }

  /** Returns the name of the field used as the primary key. */
  public String getIdField() {
    return "playerUuid";
  }

  /** Returns the primary key value (the player UUID string). */
  public Object getIdValue() {
    return playerUuid;
  }

  /** No-op: pending evolution state is not persisted to the API. */
  public void save(Consumer<Boolean> callback) {
    if (callback != null) callback.accept(true);
  }

  /** No-op: pending evolution state is not persisted to the API. */
  public void save() {}

  /** Serializes an ItemStack to a Base64 string, or returns null on failure. */
  public static String serializeItem(ItemStack item) {
    if (item == null) return null;
    try {
      return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    } catch (Exception e) {
      Main.getInstance()
          .getLogger()
          .warning("PendingEvolutionDocument: failed to serialize ItemStack: " + e.getMessage());
      return null;
    }
  }

  /** Deserializes an ItemStack from a Base64 string, or returns null on failure. */
  public static ItemStack deserializeItem(String base64) {
    if (base64 == null || base64.isEmpty()) return null;
    try {
      return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
    } catch (Exception e) {
      Main.getInstance()
          .getLogger()
          .warning("PendingEvolutionDocument: failed to deserialize ItemStack: " + e.getMessage());
      return null;
    }
  }
}
