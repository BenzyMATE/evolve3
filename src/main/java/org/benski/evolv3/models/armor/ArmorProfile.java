package org.benski.evolv3.models.armor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.benski.evolv3.core.abstracts.MongoDocument;
import org.benski.evolv3.enums.ArmorType;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Represents a player's complete armor profile. Contains all four armor pieces and manages their
 * lifecycle.
 */
@Getter
@Setter
public class ArmorProfile extends MongoDocument {
  /** The unique identifier (player UUID). */
  public String id;

  /** The player's UUID. */
  public UUID playerUuid;

  /** Map of armor type to armor item. */
  private Map<ArmorType, ArmorItem> armorPieces = new EnumMap<>(ArmorType.class);

  /**
   * Creates a new ArmorProfile with default armor pieces for a player.
   *
   * @param player The player to create the profile for
   * @return A new ArmorProfile with all armor pieces initialized
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

  /**
   * Gets the player this profile belongs to.
   *
   * @return The player, or null if offline
   */
  public Player getPlayer() {
    return Bukkit.getPlayer(playerUuid);
  }

  /**
   * Initializes the profile with a player reference.
   *
   * @param player The player to initialize with
   */
  public void initialize(Player player) {
    this.playerUuid = player.getUniqueId();
  }

  /**
   * Gets an armor item by type.
   *
   * @param type The armor type
   * @return The armor item, or null if not found
   */
  public ArmorItem getArmor(ArmorType type) {
    return armorPieces.get(type);
  }

  /**
   * Gets the helmet armor item.
   *
   * @return The helmet
   */
  public ArmorItem getHelmet() {
    return armorPieces.get(ArmorType.HELMET);
  }

  /**
   * Gets the chestplate armor item.
   *
   * @return The chestplate
   */
  public ArmorItem getChestplate() {
    return armorPieces.get(ArmorType.CHESTPLATE);
  }

  /**
   * Gets the leggings armor item.
   *
   * @return The leggings
   */
  public ArmorItem getLeggings() {
    return armorPieces.get(ArmorType.LEGGINGS);
  }

  /**
   * Gets the boots armor item.
   *
   * @return The boots
   */
  public ArmorItem getBoots() {
    return armorPieces.get(ArmorType.BOOTS);
  }

  /**
   * Gets the multiplier for a specific armor type. Returns 1.0 if the armor piece is null or
   * disabled.
   *
   * @param type the armor type
   * @return the multiplier value
   */
  public double getMultiplier(ArmorType type) {
    ArmorItem item = armorPieces.get(type);
    return (item != null && item.isEnabled()) ? item.getMultiplier() : 1.0;
  }

  /**
   * Gets the combined sell multiplier from the helmet.
   *
   * @return The sell multiplier
   */
  public double getSellMultiplier() {
    return getMultiplier(ArmorType.HELMET);
  }

  /**
   * Gets the essence multiplier from the chestplate.
   *
   * @return The essence multiplier
   */
  public double getEssenceMultiplier() {
    return getMultiplier(ArmorType.CHESTPLATE);
  }

  /**
   * Gets the mining XP multiplier from the leggings.
   *
   * @return The mining XP multiplier
   */
  public double getMiningMultiplier() {
    return getMultiplier(ArmorType.LEGGINGS);
  }

  /**
   * Gets the speed multiplier from the boots.
   *
   * @return The speed multiplier
   */
  public double getSpeedMultiplier() {
    return getMultiplier(ArmorType.BOOTS);
  }

  /**
   * Equips all armor pieces on the player.
   *
   * @param player The player to equip armor on
   */
  public void equipArmor(Player player) {
    PlayerInventory inventory = player.getInventory();

    for (ArmorType type : ArmorType.values()) {
      ArmorItem item = armorPieces.get(type);
      if (item != null) {
        ItemStack armorItem = createArmorItemStack(item);
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

  /**
   * Updates the player's walking speed based on boots multiplier.
   *
   * @param player The player to update
   */
  public void updatePlayerSpeed(Player player) {
    ArmorItem boots = getBoots();
    if (boots != null && boots.isEnabled()) {
      float baseSpeed = 0.2f;
      float newSpeed = (float) Math.min(1.0f, baseSpeed * boots.getMultiplier());
      player.setWalkSpeed(newSpeed);
    } else {
      player.setWalkSpeed(0.2f);
    }
  }

  /**
   * Creates an ItemStack for an armor piece.
   *
   * @param item The armor item
   * @return The ItemStack representing the armor
   */
  private ItemStack createArmorItemStack(ArmorItem item) {
    return new ItemStack(item.getMaterial());
  }

  /** {@inheritDoc} */
  @Override
  public String getCollectionName() {
    return "Armor";
  }

  /** {@inheritDoc} */
  @Override
  public Document toDocument() {
    Document doc = new Document();
    doc.put("_id", id);
    doc.put("playerUuid", playerUuid.toString());

    List<Document> armorDocs = new ArrayList<>();
    for (ArmorItem item : armorPieces.values()) {
      armorDocs.add(item.toDocument());
    }
    doc.put("armorPieces", armorDocs);

    return doc;
  }

  /** {@inheritDoc} */
  @Override
  public void fromDocument(Document document) {
    this.id = document.getString("_id");
    String uuidStr = document.getString("playerUuid");
    this.playerUuid = uuidStr != null ? UUID.fromString(uuidStr) : null;

    List<Document> armorDocs = document.getList("armorPieces", Document.class);
    if (armorDocs != null) {
      for (Document armorDoc : armorDocs) {
        ArmorItem item = new ArmorItem();
        item.fromDocument(armorDoc);
        if (item.getType() != null) {
          armorPieces.put(item.getType(), item);
        }
      }
    }

    for (ArmorType type : ArmorType.values()) {
      if (!armorPieces.containsKey(type)) {
        armorPieces.put(type, ArmorItem.createDefault(type));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getIdField() {
    return "_id";
  }

  /** {@inheritDoc} */
  @Override
  public Object getIdValue() {
    return this.id;
  }

  /** {@inheritDoc} */
  @Override
  public void save(Consumer<Boolean> callback) {}
}
