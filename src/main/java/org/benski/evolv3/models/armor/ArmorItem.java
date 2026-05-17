package org.benski.evolv3.models.armor;

import lombok.Getter;
import lombok.Setter;
import org.benski.evolv3.core.abstracts.MongoSubDoc;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.enums.ArmorType;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Represents an individual armor piece with its own progression stats. Each armor type has unique
 * leveling mechanics and multiplier effects.
 */
@Getter
@Setter
public final class ArmorItem extends MongoSubDoc {
  private ArmorType type;
  private int level;
  private int xp;
  private int target;
  private double multiplier;
  private double xpMultiplier;
  private double multiplierIncrement;
  private int evolutionInterval;
  private boolean enabled;

  /**
   * Creates a new ArmorItem with default values from config.
   *
   * @param type The armor type
   * @return A new ArmorItem with default values
   */
  public static ArmorItem createDefault(ArmorType type) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    ArmorItem item = new ArmorItem();

    item.type = type;
    item.level = config.getInt(ConfigPaths.Default.Armor.DEFAULT_LEVEL, 1);
    item.xp = config.getInt(ConfigPaths.Default.Armor.DEFAULT_XP, 0);
    item.target = config.getInt(ConfigPaths.Default.Armor.DEFAULT_TARGET, 100);
    item.multiplier = config.getDouble(ConfigPaths.Default.Armor.DEFAULT_MULTIPLIER, 1.0);
    item.xpMultiplier = config.getDouble(ConfigPaths.Default.Armor.XP_MULTIPLIER, 1.1);
    item.multiplierIncrement =
        config.getDouble(ConfigPaths.Default.Armor.MULTIPLIER_INCREMENT, 0.05);
    item.evolutionInterval = config.getInt(ConfigPaths.Default.Armor.EVOLUTION_INTERVAL, 30);
    item.enabled = true;

    return item;
  }

  /**
   * Gets the current evolution tier based on level. Tier 0: Leather, Tier 1: Chainmail, Tier 2:
   * Iron, Tier 3: Diamond, Tier 4: Netherite
   *
   * @return The current evolution tier (0-4)
   */
  public int getEvolutionTier() {
    if (evolutionInterval <= 0) {
      return 0;
    }
    return Math.min(4, level / evolutionInterval);
  }

  /**
   * Checks if the armor has reached a new evolution tier.
   *
   * @return true if at an evolution threshold level
   */
  public boolean isAtEvolutionThreshold() {
    if (evolutionInterval <= 0) {
      return false;
    }
    return level > 0 && level % evolutionInterval == 0 && getEvolutionTier() < 4;
  }

  /**
   * Gets the current material based on evolution tier.
   *
   * @return The material for this armor piece
   */
  public org.bukkit.Material getMaterial() {
    return type.getMaterial(getEvolutionTier());
  }

  /**
   * Adds XP to this armor piece and handles level-ups.
   *
   * @param amount The amount of XP to add
   * @return true if a level-up occurred
   */
  public boolean addXp(int amount) {
    if (!enabled) {
      return false;
    }

    xp += amount;
    boolean leveledUp = false;

    while (xp >= target) {
      xp -= target;
      level++;
      target = (int) (target * xpMultiplier);
      multiplier += multiplierIncrement;
      leveledUp = true;
    }

    return leveledUp;
  }

  /**
   * Gets the progress percentage towards the next level.
   *
   * @return Progress as a value between 0.0 and 1.0
   */
  public double getProgress() {
    if (target <= 0) {
      return 0.0;
    }
    return (double) xp / target;
  }

  /**
   * Gets the progress as a formatted percentage string.
   *
   * @return Progress percentage (e.g., "45.5%")
   */
  public String getProgressPercentage() {
    return String.format("%.1f%%", getProgress() * 100);
  }

  /** {@inheritDoc} */
  @Override
  public Document toDocument() {
    Document doc = new Document();
    doc.put("type", type.getIdentifier());
    doc.put("level", level);
    doc.put("xp", xp);
    doc.put("target", target);
    doc.put("multiplier", multiplier);
    doc.put("xpMultiplier", xpMultiplier);
    doc.put("multiplierIncrement", multiplierIncrement);
    doc.put("evolutionInterval", evolutionInterval);
    doc.put("enabled", enabled);
    return doc;
  }

  /** {@inheritDoc} */
  @Override
  public void fromDocument(Document document) {
    String typeStr = document.getString("type");
    this.type = ArmorType.fromIdentifier(typeStr);
    this.level = document.getInteger("level", 1);
    this.xp = document.getInteger("xp", 0);
    this.target = document.getInteger("target", 100);
    this.multiplier = document.getDouble("multiplier");
    this.xpMultiplier = document.getDouble("xpMultiplier");
    this.multiplierIncrement = document.getDouble("multiplierIncrement");
    this.evolutionInterval = document.getInteger("evolutionInterval", 30);
    this.enabled = document.getBoolean("enabled", true);
  }

  /** {@inheritDoc} */
  @Override
  public void initialize(String parentId, String playerId, Utils utils) {}
}
