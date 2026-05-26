package org.benski.evolv3.models.pickaxe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;

/**
 * Container for all enchantments on a pickaxe. Manages the ordered list and provides O(1) lookup
 * via an internal index.
 */
public class PickaxeEnchants {

  private final Map<String, EnchantData> index = new HashMap<>();
  public List<EnchantData> items = new ArrayList<>();

  /** Creates a new PickaxeEnchants instance. */
  public PickaxeEnchants() {}

  static void hydrateConfigValues(EnchantData enchant) {
    String displayName =
        EnchantAnimationConfig.getDisplayName(enchant.name != null ? enchant.name : "");
    enchant.maxLevel = EnchantAnimationConfig.getEnchantMaxLevel(displayName);
    enchant.maxChance = EnchantAnimationConfig.getEnchantMaxChance(displayName);
  }

  /** Normalize key. */
  private static String normalizeKey(String name) {
    return name.toLowerCase().replace(" ", "");
  }

  /** Returns the enchant with the given name (case-insensitive), or null if not present. */
  public EnchantData getEnchant(String name) {
    if (name == null) return null;
    return index.get(normalizeKey(name));
  }

  /** Returns {@code true} if the pickaxe has an enchant with the given name. */
  public boolean hasEnchant(String name) {
    return getEnchant(name) != null;
  }

  /** Returns the level of the named enchant, or 0 if not present. */
  public int getEnchantLevel(String name) {
    EnchantData enchant = getEnchant(name);
    return enchant != null ? enchant.level : 0;
  }

  /** Returns {@code true} if the named enchant exists and is enabled. */
  public boolean isEnchantEnabled(String name) {
    EnchantData enchant = getEnchant(name);
    return enchant != null && enchant.enabled;
  }

  /** Adds or replaces an enchant in the collection (matched by name). */
  public void setEnchant(EnchantData enchant) {
    String key = enchant.name != null ? normalizeKey(enchant.name) : "";
    EnchantData existing = index.get(key);
    if (existing != null) {
      items.set(items.indexOf(existing), enchant);
    } else {
      items.add(enchant);
    }
    index.put(key, enchant);
  }

  /** Removes the named enchant. Returns {@code true} if it existed. */
  public boolean removeEnchant(String name) {
    if (name == null) return false;
    String key = normalizeKey(name);
    EnchantData enchant = index.remove(key);
    if (enchant != null) {
      items.remove(enchant);
      return true;
    }
    return false;
  }

  /** Returns {@code true} if no enchants are stored. */
  public boolean isEmpty() {
    return items.isEmpty();
  }

  /** Returns the number of stored enchants. */
  public int size() {
    return items.size();
  }
}
