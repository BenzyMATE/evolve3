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

  public int getEvolutionTier() {
    if (evolutionInterval <= 0) return 0;
    return Math.min(4, level / evolutionInterval);
  }

  public boolean isAtEvolutionThreshold() {
    if (evolutionInterval <= 0) return false;
    return level > 0 && level % evolutionInterval == 0 && getEvolutionTier() < 4;
  }

  public org.bukkit.Material getMaterial() {
    return type.getMaterial(getEvolutionTier());
  }

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

  public double getProgress() {
    if (target <= 0) return 0.0;
    return (double) xp / target;
  }

  public String getProgressPercentage() {
    return String.format("%.1f%%", getProgress() * 100);
  }
}
