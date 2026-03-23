package org.benski.evolv3.models.pickaxe;

import org.benski.evolv3.core.interfaces.MongoSubDocument;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.bson.Document;

/**
 * Represents a single enchantment on a pickaxe.
 * Stores the enchants name, level, and configuration data.
 * This is a sub-document embedded within PickaxeEnchants.
 */
public class EnchantData implements MongoSubDocument
{
    /**
     * The internal name of the enchantment (e.g., "efficiency", "frostbite").
     */
    public String name;

    /**
     * The current level of the enchantment.
     */
    public int level;

    /**
     * The maximum level this enchantment can reach.
     */
    public int maxLevel;

    /**
     * The maximum trigger chance at max level (percentage).
     * For passive enchants like Efficiency, this is 0.
     */
    public double maxChance;

    /**
     * Whether this enchantment is enabled (can trigger).
     */
    public boolean enabled;

    /**
     * Default constructor for deserialization.
     */
    public EnchantData()
    {
        this.enabled = true;
    }

    /**
     * Creates a new EnchantData with the specified values.
     *
     * @param name      the internal name of the enchantment
     * @param level     the current level
     * @param maxLevel  the maximum level
     * @param maxChance the maximum trigger chance
     */
    public EnchantData(String name, int level, int maxLevel, double maxChance)
    {
        this.name = name;
        this.level = level;
        this.maxLevel = maxLevel;
        this.maxChance = maxChance;
        this.enabled = true;
    }

    /**
     * Calculates the current trigger chance based on level.
     * Formula: (level / maxLevel) * maxChance
     *
     * @return the current trigger chance as a percentage
     */
    public double getCurrentChance()
    {
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
    public boolean isMaxLevel()
    {
        return level >= maxLevel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document toDocument()
    {
        Document doc = new Document();
        doc.put("name", name);
        doc.put("level", level);
        // maxLevel and maxChance are config-driven — not persisted so config changes
        // always take effect without a DB migration.
        doc.put("enabled", enabled);
        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        if (document == null) {
            return;
        }
        this.name = document.getString("name");
        this.level = document.getInteger("level", 0);
        this.enabled = document.getBoolean("enabled", true);
        // Resolve maxLevel and maxChance from config so they always reflect the
        // current enchants.yml values rather than a potentially stale DB snapshot.
        String displayName = EnchantAnimationConfig.getDisplayName(this.name != null ? this.name : "");
        this.maxLevel  = EnchantAnimationConfig.getEnchantMaxLevel(displayName);
        this.maxChance = EnchantAnimationConfig.getEnchantMaxChance(displayName);
    }
}
