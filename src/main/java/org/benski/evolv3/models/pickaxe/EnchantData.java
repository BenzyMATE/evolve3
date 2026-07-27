package org.benski.evolv3.models.pickaxe;

/**
 * Represents a single enchantment on a pickaxe. Stores name, level, config data, and up to 4
 * evolution stone sockets.
 */
public final class EnchantData {

  public static final int MAX_SOCKET_COUNT = 4;

  public String name;
  public int level;
  public int maxLevel;
  public double maxChance;
  public boolean enabled;
  public String[] socketStones = new String[MAX_SOCKET_COUNT];

  /** Creates a default-enabled enchant with no other fields set. */
  public EnchantData() {
    this.enabled = true;
  }

  /**
   * Creates a fully specified enchant data record.
   *
   * @param name internal lowercase name (e.g. "explosive")
   * @param level current enchant level
   * @param maxLevel maximum possible level
   * @param maxChance maximum proc chance at max level
   */
  public EnchantData(String name, int level, int maxLevel, double maxChance) {
    this.name = name;
    this.level = level;
    this.maxLevel = maxLevel;
    this.maxChance = maxChance;
    this.enabled = true;
  }

  /**
   * Returns the number of socket slots unlocked at the given prestige level (capped at {@link
   * #MAX_SOCKET_COUNT}).
   */
  public static int getUnlockedSlotCount(int prestige) {
    return Math.min(1 + prestige, MAX_SOCKET_COUNT);
  }

  /** Returns the proc chance at the current level scaled linearly against max level/chance. */
  public double getCurrentChance() {
    if (maxLevel <= 0 || maxChance <= 0) return 0;
    return ((double) level / maxLevel) * maxChance;
  }

  /** Returns {@code true} if the enchant is at its configured maximum level. */
  public boolean isMaxLevel() {
    return level >= maxLevel;
  }

  /** Returns {@code true} if any socketed stone matches the given rarity name. */
  public boolean hasStone(String rarity) {
    for (String s : socketStones) {
      if (rarity.equals(s)) return true;
    }
    return false;
  }

  /** Returns the number of non-null stone sockets (across all slots). */
  public int getSocketedCount() {
    int count = 0;
    for (String s : socketStones) {
      if (s != null) count++;
    }
    return count;
  }

  /**
   * Returns the index of the first empty socket within the prestige-unlocked range, or -1 if all
   * unlocked slots are filled.
   */
  public int getFirstEmptySocket(int prestige) {
    int unlocked = getUnlockedSlotCount(prestige);
    for (int i = 0; i < unlocked; i++) {
      if (socketStones[i] == null) return i;
    }
    return -1;
  }
}
