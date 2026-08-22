package org.benski.evolv3.models.pickaxe;

import java.util.Base64;
import org.benski.evolv3.Main;
import org.bukkit.inventory.ItemStack;

/**
 * Base64 (de)serialization helpers for the pickaxe {@link ItemStack} held by a pending enchant
 * selection, used when reading/writing {@link org.benski.evolv3.api.dto.PendingDataDto} via the
 * pending-data API.
 */
public final class PendingEnchantDocument {

  private PendingEnchantDocument() {}

  /** Serializes an ItemStack to a Base64 string, or returns null on failure. */
  public static String serializeItem(ItemStack item) {
    if (item == null) return null;
    try {
      return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    } catch (Exception e) {
      Main.getInstance()
          .getLogger()
          .warning("PendingEnchantDocument: failed to serialize ItemStack: " + e.getMessage());
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
          .warning("PendingEnchantDocument: failed to deserialize ItemStack: " + e.getMessage());
      return null;
    }
  }
}
