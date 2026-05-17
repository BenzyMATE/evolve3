package org.benski.evolv3.models.pickaxe;

import java.util.Arrays;
import java.util.List;
import org.benski.evolv3.core.interfaces.MongoSubDocument;
import org.bson.Document;

/**
 * Represents a single enchantment on a pickaxe. Stores the enchant's name, level, configuration
 * data, and up to 3 evolution stone sockets. This is a sub-document embedded within
 * PickaxeEnchants.
 */
public class EnchantData implements MongoSubDocument {
  /** Maximum possible evolution stone sockets per enchant (requires max prestige to unlock all). */
  public static final int MAX_SOCKET_COUNT = 5;

  /** The internal name of the enchantment (e.g., "efficiency", "frostbite"). */
  public String name;

  /** The current level of the enchantment. */
  public int level;

  /** The maximum level this enchantment can reach. */
  public int maxLevel;

  /**
   * The maximum trigger chance at max level (percentage). For passive enchants like Efficiency,
   * this is 0.
   */
  public double maxChance;

  /** Whether this enchantment is enabled (can trigger). */
  public boolean enabled;

  /**
   * Evolution stone sockets. Each element is the rarity display name of the applied stone (e.g.
   * {@code "Common"}), or {@code null} if the socket is empty. Array length is always {@value
   * #MAX_SOCKET_COUNT}; only the first {@link #getUnlockedSlotCount(int)} slots are usable based on
   * pickaxe prestige.
   */
  public String[] socketStones = new String[MAX_SOCKET_COUNT];

  /** Default constructor for deserialization. */
  public EnchantData() {
    this.enabled = true;
  }

  /**
   * Creates a new EnchantData with the specified values.
   *
   * @param name the internal name of the enchantment
   * @param level the current level
   * @param maxLevel the maximum level
   * @param maxChance the maximum trigger chance
   */
  public EnchantData(String name, int level, int maxLevel, double maxChance) {
    this.name = name;
    this.level = level;
    this.maxLevel = maxLevel;
    this.maxChance = maxChance;
    this.enabled = true;
  }

  /**
   * Returns the number of usable socket slots for the given prestige level. Starts at 1 and
   * increases by 1 per prestige, capped at {@value #MAX_SOCKET_COUNT}.
   *
   * @param prestige the pickaxe prestige level (0 = no prestige)
   * @return number of unlocked socket slots
   */
  public static int getUnlockedSlotCount(int prestige) {
    return Math.min(1 + prestige, MAX_SOCKET_COUNT);
  }

  /**
   * Calculates the current trigger chance based on level. Formula: (level / maxLevel) * maxChance
   *
   * @return the current trigger chance as a percentage
   */
  public double getCurrentChance() {
    if (maxLevel <= 0 || maxChance <= 0) {
      return 0;
    }
    return ((double) level / maxLevel) * maxChance;
  }

  /**
   * Checks if this enchantment is at maximum level.
   *
   * @return true if level equals maxLevel
   */
  public boolean isMaxLevel() {
    return level >= maxLevel;
  }

  /**
   * Returns {@code true} if the given rarity is socketed in any slot of this enchant.
   *
   * @param rarity the rarity display name to check (e.g. {@code "Common"})
   * @return {@code true} if that rarity is present in {@link #socketStones}
   */
  public boolean hasStone(String rarity) {
    for (String s : socketStones) {
      if (rarity.equals(s)) return true;
    }
    return false;
  }

  /**
   * Returns how many sockets are currently occupied by a stone.
   *
   * @return number of non-null socket slots (0–{@value #MAX_SOCKET_COUNT})
   */
  public int getSocketedCount() {
    int count = 0;
    for (String s : socketStones) {
      if (s != null) count++;
    }
    return count;
  }

  /**
   * Returns the first empty unlocked socket index for the given prestige, or -1 if all are full.
   *
   * @param prestige the pickaxe prestige level
   * @return index of the first empty socket, or -1
   */
  public int getFirstEmptySocket(int prestige) {
    int unlocked = getUnlockedSlotCount(prestige);
    for (int i = 0; i < unlocked; i++) {
      if (socketStones[i] == null) return i;
    }
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public Document toDocument() {
    Document doc = new Document();
    doc.put("name", name);
    doc.put("level", level);
    doc.put("enabled", enabled);
    List<String> socketList = new java.util.ArrayList<>(MAX_SOCKET_COUNT);
    socketList.addAll(Arrays.asList(socketStones).subList(0, MAX_SOCKET_COUNT));
    doc.put("sockets", socketList);
    return doc;
  }

  /** {@inheritDoc} */
  @Override
  public void fromDocument(Document document) {
    if (document == null) {
      return;
    }
    this.name = document.getString("name");
    this.level = document.getInteger("level", 0);
    this.enabled = document.getBoolean("enabled", true);

    List<String> sockets = document.getList("sockets", String.class);
    if (sockets != null) {
      for (int i = 0; i < MAX_SOCKET_COUNT && i < sockets.size(); i++) {
        this.socketStones[i] = sockets.get(i);
      }
    }
  }
}
