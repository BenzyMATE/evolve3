package org.benski.evolv3.models.pickaxe;

import org.benski.evolv3.core.interfaces.MongoSubDocument;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.bson.Document;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a single enchantment on a pickaxe.
 * Stores the enchant's name, level, configuration data, and up to 3 evolution stone sockets.
 * This is a sub-document embedded within PickaxeEnchants.
 */
public class EnchantData implements MongoSubDocument
{
    /** The number of evolution stone sockets each enchant supports. */
    public static final int SOCKET_COUNT = 3;

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
     * Evolution stone sockets. Each element is the rarity display name of the applied stone
     * (e.g. {@code "Common"}), or {@code null} if the socket is empty.
     * Array length is always {@value #SOCKET_COUNT}.
     */
    public String[] socketStones = new String[SOCKET_COUNT];

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
     * @return number of non-null socket slots (0–{@value #SOCKET_COUNT})
     */
    public int getSocketedCount() {
        int count = 0;
        for (String s : socketStones) {
            if (s != null) count++;
        }
        return count;
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
        doc.put("sockets", Arrays.asList(socketStones[0], socketStones[1], socketStones[2]));
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

        // Deserialize socket stones (null elements are valid — they represent empty sockets)
        List<String> sockets = document.getList("sockets", String.class);
        if (sockets != null) {
            for (int i = 0; i < SOCKET_COUNT && i < sockets.size(); i++) {
                this.socketStones[i] = sockets.get(i);
            }
        }
    }
}
