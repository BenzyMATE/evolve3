package org.benski.evolv3.models.pickaxe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;

/**
 * Container for all enchantments on a pickaxe. Manages the ordered list and provides O(1) lookup
 * via an internal index.
 */
public class PickaxeEnchants {

  private final Map<String, EnchantData> index = new HashMap<>();
  public List<EnchantData> items = new ArrayList<>();

  public PickaxeEnchants() {}

  static void hydrateConfigValues(EnchantData enchant) {
    String displayName =
        EnchantAnimationConfig.getDisplayName(enchant.name != null ? enchant.name : "");
    enchant.maxLevel = EnchantAnimationConfig.getEnchantMaxLevel(displayName);
    enchant.maxChance = EnchantAnimationConfig.getEnchantMaxChance(displayName);
  }

  private static String normalizeKey(String name) {
    return name.toLowerCase().replace(" ", "");
  }

  public EnchantData getEnchant(String name) {
    if (name == null) return null;
    return index.get(normalizeKey(name));
  }

  public boolean hasEnchant(String name) {
    return getEnchant(name) != null;
  }

  public int getEnchantLevel(String name) {
    EnchantData enchant = getEnchant(name);
    return enchant != null ? enchant.level : 0;
  }

  public boolean isEnchantEnabled(String name) {
    EnchantData enchant = getEnchant(name);
    return enchant != null && enchant.enabled;
  }

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

  public boolean isEmpty() {
    return items.isEmpty();
  }

  public int size() {
    return items.size();
  }
}
