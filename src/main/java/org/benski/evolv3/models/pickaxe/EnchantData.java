package org.benski.evolv3.models.pickaxe;

/**
 * Represents a single enchantment on a pickaxe. Stores name, level, config data, and up to 5
 * evolution stone sockets.
 */
public class EnchantData {

  public static final int MAX_SOCKET_COUNT = 5;

  public String name;
  public int level;
  public int maxLevel;
  public double maxChance;
  public boolean enabled;
  public String[] socketStones = new String[MAX_SOCKET_COUNT];

  public EnchantData() {
    this.enabled = true;
  }

  public EnchantData(String name, int level, int maxLevel, double maxChance) {
    this.name = name;
    this.level = level;
    this.maxLevel = maxLevel;
    this.maxChance = maxChance;
    this.enabled = true;
  }

  public static int getUnlockedSlotCount(int prestige) {
    return Math.min(1 + prestige, MAX_SOCKET_COUNT);
  }

  public double getCurrentChance() {
    if (maxLevel <= 0 || maxChance <= 0) return 0;
    return ((double) level / maxLevel) * maxChance;
  }

  public boolean isMaxLevel() {
    return level >= maxLevel;
  }

  public boolean hasStone(String rarity) {
    for (String s : socketStones) {
      if (rarity.equals(s)) return true;
    }
    return false;
  }

  public int getSocketedCount() {
    int count = 0;
    for (String s : socketStones) {
      if (s != null) count++;
    }
    return count;
  }

  public int getFirstEmptySocket(int prestige) {
    int unlocked = getUnlockedSlotCount(prestige);
    for (int i = 0; i < unlocked; i++) {
      if (socketStones[i] == null) return i;
    }
    return -1;
  }
}
