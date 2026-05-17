package org.benski.evolv3.enums;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Enum representing the different types of armor pieces. Each type has a unique progression method
 * and multiplier effect.
 */
@Getter
public enum ArmorType {
  HELMET("helmet", "Helmet", EquipmentSlot.HEAD, 39),
  CHESTPLATE("chestplate", "Chestplate", EquipmentSlot.CHEST, 38),
  LEGGINGS("leggings", "Leggings", EquipmentSlot.LEGS, 37),
  BOOTS("boots", "Boots", EquipmentSlot.FEET, 36);

  private final String identifier;
  private final String displayName;
  private final EquipmentSlot equipmentSlot;
  private final int inventorySlot;

  ArmorType(String identifier, String displayName, EquipmentSlot equipmentSlot, int inventorySlot) {
    this.identifier = identifier;
    this.displayName = displayName;
    this.equipmentSlot = equipmentSlot;
    this.inventorySlot = inventorySlot;
  }

  /**
   * Gets the material for this armor type at the specified evolution tier.
   *
   * @param tier The evolution tier (0-4)
   * @return The material for this armor piece at the given tier
   */
  public Material getMaterial(int tier) {
    return switch (this) {
      case HELMET ->
          switch (tier) {
            case 0 -> Material.LEATHER_HELMET;
            case 1 -> Material.CHAINMAIL_HELMET;
            case 2 -> Material.IRON_HELMET;
            case 3 -> Material.DIAMOND_HELMET;
            default -> Material.NETHERITE_HELMET;
          };
      case CHESTPLATE ->
          switch (tier) {
            case 0 -> Material.LEATHER_CHESTPLATE;
            case 1 -> Material.CHAINMAIL_CHESTPLATE;
            case 2 -> Material.IRON_CHESTPLATE;
            case 3 -> Material.DIAMOND_CHESTPLATE;
            default -> Material.NETHERITE_CHESTPLATE;
          };
      case LEGGINGS ->
          switch (tier) {
            case 0 -> Material.LEATHER_LEGGINGS;
            case 1 -> Material.CHAINMAIL_LEGGINGS;
            case 2 -> Material.IRON_LEGGINGS;
            case 3 -> Material.DIAMOND_LEGGINGS;
            default -> Material.NETHERITE_LEGGINGS;
          };
      case BOOTS ->
          switch (tier) {
            case 0 -> Material.LEATHER_BOOTS;
            case 1 -> Material.CHAINMAIL_BOOTS;
            case 2 -> Material.IRON_BOOTS;
            case 3 -> Material.DIAMOND_BOOTS;
            default -> Material.NETHERITE_BOOTS;
          };
    };
  }

  /**
   * Gets the ArmorType from a string identifier.
   *
   * @param identifier The identifier string
   * @return The ArmorType, or null if not found
   */
  public static ArmorType fromIdentifier(String identifier) {
    for (ArmorType type : values()) {
      if (type.identifier.equalsIgnoreCase(identifier)) {
        return type;
      }
    }
    return null;
  }

  /**
   * Gets the ArmorType from an inventory slot.
   *
   * @param slot The inventory slot number
   * @return The ArmorType, or null if not an armor slot
   */
  public static ArmorType fromInventorySlot(int slot) {
    for (ArmorType type : values()) {
      if (type.inventorySlot == slot) {
        return type;
      }
    }
    return null;
  }
}
