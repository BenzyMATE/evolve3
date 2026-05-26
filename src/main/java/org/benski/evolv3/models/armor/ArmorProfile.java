package org.benski.evolv3.models.armor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.benski.evolv3.enums.ArmorType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Represents a player's complete armor profile. Contains all four armor pieces and manages their
 * lifecycle. Persisted locally only — armor is not yet part of the C# API model.
 */
@Getter
@Setter
public class ArmorProfile {

  public String id;
  public UUID playerUuid;

  private Map<ArmorType, ArmorItem> armorPieces = new EnumMap<>(ArmorType.class);

  /**
   * Creates a default ArmorProfile for the given player, initialising all four armor pieces with
   * default stats.
   */
  public static ArmorProfile createDefault(Player player) {
    ArmorProfile profile = new ArmorProfile();
    profile.id = player.getUniqueId().toString();
    profile.playerUuid = player.getUniqueId();
    for (ArmorType type : ArmorType.values()) {
      profile.armorPieces.put(type, ArmorItem.createDefault(type));
    }
    return profile;
  }

  /** Binds this profile to the given player's UUID after deserialization. */
  public void initialize(Player player) {
    this.playerUuid = player.getUniqueId();
  }

  /** Returns the online Player for this profile, or null if offline. */
  public Player getPlayer() {
    return Bukkit.getPlayer(playerUuid);
  }

  /** Returns the ArmorItem for the given armor slot. */
  public ArmorItem getArmor(ArmorType type) {
    return armorPieces.get(type);
  }

  /** Returns the helmet. */
  public ArmorItem getHelmet() {
    return armorPieces.get(ArmorType.HELMET);
  }

  /** Returns the chestplate. */
  public ArmorItem getChestplate() {
    return armorPieces.get(ArmorType.CHESTPLATE);
  }

  /** Returns the leggings. */
  public ArmorItem getLeggings() {
    return armorPieces.get(ArmorType.LEGGINGS);
  }

  /** Returns the boots. */
  public ArmorItem getBoots() {
    return armorPieces.get(ArmorType.BOOTS);
  }

  /** Returns the multiplier for the given armor slot, or 1.0 if the piece is absent or disabled. */
  public double getMultiplier(ArmorType type) {
    ArmorItem item = armorPieces.get(type);
    return (item != null && item.isEnabled()) ? item.getMultiplier() : 1.0;
  }

  /** Returns the sell multiplier. */
  public double getSellMultiplier() {
    return getMultiplier(ArmorType.HELMET);
  }

  /** Returns the essence multiplier. */
  public double getEssenceMultiplier() {
    return getMultiplier(ArmorType.CHESTPLATE);
  }

  /** Returns the mining multiplier. */
  public double getMiningMultiplier() {
    return getMultiplier(ArmorType.LEGGINGS);
  }

  /** Returns the speed multiplier. */
  public double getSpeedMultiplier() {
    return getMultiplier(ArmorType.BOOTS);
  }

  /** Equips all armor pieces to the player's inventory and updates their walk speed. */
  public void equipArmor(Player player) {
    PlayerInventory inventory = player.getInventory();
    for (ArmorType type : ArmorType.values()) {
      ArmorItem item = armorPieces.get(type);
      if (item != null) {
        ItemStack armorItem = new ItemStack(item.getMaterial());
        switch (type) {
          case HELMET -> inventory.setHelmet(armorItem);
          case CHESTPLATE -> inventory.setChestplate(armorItem);
          case LEGGINGS -> inventory.setLeggings(armorItem);
          case BOOTS -> inventory.setBoots(armorItem);
        }
      }
    }
    updatePlayerSpeed(player);
  }

  /** Adjusts the player's walk speed based on the boots evolution multiplier. */
  public void updatePlayerSpeed(Player player) {
    ArmorItem boots = getBoots();
    if (boots != null && boots.isEnabled()) {
      float newSpeed = (float) Math.min(1.0f, 0.2f * boots.getMultiplier());
      player.setWalkSpeed(newSpeed);
    } else {
      player.setWalkSpeed(0.2f);
    }
  }

  /** No-op: armor is not persisted to the API. Data resets on server restart. */
  public void save(Consumer<Boolean> callback) {
    if (callback != null) callback.accept(true);
  }

  /** No-op: armor is not persisted to the API. Data resets on server restart. */
  public void save() {}
}
