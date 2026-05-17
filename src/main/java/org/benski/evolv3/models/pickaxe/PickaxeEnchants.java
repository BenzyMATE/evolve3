package org.benski.evolv3.models.pickaxe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.benski.evolv3.core.interfaces.MongoSubDocument;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.bson.Document;

/**
 * Container for all enchantments on a pickaxe. Manages the collection of EnchantData items and
 * provides helper methods for enchant lookup and manipulation. This is a sub-document embedded
 * within PickaxeProfile.
 */
public class PickaxeEnchants implements MongoSubDocument {
  /** Ordered list of enchantments — used by PickaxeMenu for slot-indexed rendering. */
  public List<EnchantData> items = new ArrayList<>();

  /**
   * Fast lookup map keyed by lowercase internal enchant name. Always kept in sync with {@link
   * #items}.
   */
  private final Map<String, EnchantData> index = new HashMap<>();

  /** Default constructor. */
  public PickaxeEnchants() {}

  /**
   * Retrieves an enchantment by its name.
   *
   * @param name the internal name of the enchantment (case-insensitive)
   * @return the EnchantData if found, null otherwise
   */
  public EnchantData getEnchant(String name) {
    if (name == null) return null;
    return index.get(normalizeKey(name));
  }

  /**
   * Checks if this pickaxe has a specific enchantment.
   *
   * @param name the internal name of the enchantment (case-insensitive)
   * @return true if the enchantment exists on this pickaxe
   */
  public boolean hasEnchant(String name) {
    return getEnchant(name) != null;
  }

  /**
   * Gets the level of a specific enchantment.
   *
   * @param name the internal name of the enchantment (case-insensitive)
   * @return the level of the enchantment, or 0 if not found
   */
  public int getEnchantLevel(String name) {
    EnchantData enchant = getEnchant(name);
    return enchant != null ? enchant.level : 0;
  }

  /**
   * Checks if an enchantment is enabled.
   *
   * @param name the internal name of the enchantment (case-insensitive)
   * @return true if the enchantment exists and is enabled
   */
  public boolean isEnchantEnabled(String name) {
    EnchantData enchant = getEnchant(name);
    return enchant != null && enchant.enabled;
  }

  /**
   * Adds or updates an enchantment on this pickaxe.
   *
   * @param enchant the enchantment data to add or update
   */
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

  /**
   * Removes an enchantment from this pickaxe.
   *
   * @param name the internal name of the enchantment to remove
   * @return true if the enchantment was removed
   */
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

  /**
   * Checks if this pickaxe has any enchantments.
   *
   * @return true if there are no enchantments
   */
  public boolean isEmpty() {
    return items.isEmpty();
  }

  /**
   * Gets the total number of enchantments on this pickaxe.
   *
   * @return the number of enchantments
   */
  public int size() {
    return items.size();
  }

  private static void hydrateConfigValues(EnchantData enchant) {
    String displayName =
        EnchantAnimationConfig.getDisplayName(enchant.name != null ? enchant.name : "");
    enchant.maxLevel = EnchantAnimationConfig.getEnchantMaxLevel(displayName);
    enchant.maxChance = EnchantAnimationConfig.getEnchantMaxChance(displayName);
  }

  private static String normalizeKey(String name) {
    return name.toLowerCase().replace(" ", "");
  }

  @Override
  public Document toDocument() {
    Document doc = new Document();
    List<Document> enchantDocs = new ArrayList<>();
    for (EnchantData enchant : items) {
      enchantDocs.add(enchant.toDocument());
    }
    doc.put("items", enchantDocs);
    return doc;
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public void fromDocument(Document document) {
    items.clear();
    index.clear();
    if (document == null) {
      return;
    }

    List<Document> enchantDocs = document.getList("items", Document.class);
    if (enchantDocs != null) {
      for (Document enchantDoc : enchantDocs) {
        EnchantData enchant = new EnchantData();
        enchant.fromDocument(enchantDoc);
        hydrateConfigValues(enchant);
        items.add(enchant);
        if (enchant.name != null) {
          index.put(normalizeKey(enchant.name), enchant);
        }
      }
    }
  }
}
