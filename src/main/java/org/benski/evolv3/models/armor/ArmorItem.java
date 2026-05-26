package org.benski.evolv3.models.armor;

import lombok.Getter;
import lombok.Setter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.enums.ArmorType;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Represents an individual armor piece with its own progression stats. Each armor type has unique
 * leveling mechanics and multiplier effects.
 */
@Getter
@Setter
public final class ArmorItem {

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
   * Creates an ArmorItem with default stats read from configuration.
   *
   * @param type the armor slot this item occupies
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

  /** Returns the evolution tier (0–4) based on the current level and evolution interval. */
  public int getEvolutionTier() {
    if (evolutionInterval <= 0) return 0;
    return Math.min(4, level / evolutionInterval);
  }

  /**
   * Returns true if the item is exactly at an evolution milestone and has not yet reached tier 4.
   */
  public boolean isAtEvolutionThreshold() {
    if (evolutionInterval <= 0) return false;
    return level > 0 && level % evolutionInterval == 0 && getEvolutionTier() < 4;
  }

  /** Returns the Bukkit material corresponding to this armor type's current evolution tier. */
  public org.bukkit.Material getMaterial() {
    return type.getMaterial(getEvolutionTier());
  }

  /**
   * Adds XP to this armor item, levelling up as required.
   *
   * @param amount XP to add
   * @return true if at least one level-up occurred
   */
  public boolean addXp(int amount) {
    if (!enabled) return false;
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

  /** Returns XP progress toward the next level as a value in [0.0, 1.0]. */
  public double getProgress() {
    if (target <= 0) return 0.0;
    return (double) xp / target;
  }

  /** Returns XP progress as a formatted percentage string (e.g. {@code "42.3%"}). */
  public String getProgressPercentage() {
    return String.format("%.1f%%", getProgress() * 100);
  }
}
