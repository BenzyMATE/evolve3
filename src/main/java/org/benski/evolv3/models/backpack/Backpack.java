package org.benski.evolv3.models.backpack;

import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import org.benski.evolv3.core.abstracts.MongoSubDoc;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Represents a player's backpack for storing mined items.
 * Embedded within PlayerProfile as a sub-document.
 */
@Getter
@Setter
public class Backpack extends MongoSubDoc
{
    /**
     * Maximum number of unique item stacks in the backpack.
     */
    private int maxSize;

    /**
     * Total number of items across all stacks.
     */
    private int totalItems;

    /**
     * Whether the backpack is at maximum capacity.
     * Suppressed in favour of a live-computed {@link #isFull()} to prevent stale state.
     */
    @Getter(AccessLevel.NONE)
    private boolean full;

    /**
     * List of items in the backpack.
     */
    private List<BackpackItem> items = new ArrayList<>();

    /**
     * Creates a new Backpack with default values from config.
     *
     * @return A new Backpack with default values
     */
    public static Backpack createDefault()
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        Backpack backpack = new Backpack();
        backpack.maxSize = config.getInt(ConfigPaths.Default.Backpack.SIZE, 100);
        backpack.totalItems = 0;
        backpack.full = false;
        backpack.items = new ArrayList<>();
        return backpack;
    }

    /**
     * Adds an item to the backpack or updates an existing stack.
     *
     * @param player     The player adding the item
     * @param material   The material type
     * @param amount     The quantity to add
     * @param evolveItem Whether from an enchantment effect
     * @return true if the item was added successfully
     */
    /**
     * Returns whether the backpack has reached its total-item capacity.
     * Always computed live from {@code totalItems} to prevent stale cached state.
     */
    public boolean isFull() {
        return totalItems >= maxSize;
    }

    public boolean addItem(Player player, Material material, int amount, boolean evolveItem)
    {
        if (totalItems >= maxSize) {
            return false;
        }

        // Clamp to remaining capacity so no call site can accidentally overfill the backpack
        // (e.g. Fortune with a large multiplier, or multiple enchants firing simultaneously).
        int clamped = Math.min(amount, maxSize - totalItems);

        Optional<BackpackItem> existingItem = items.stream()
                .filter(item -> item.getMaterial() == material && item.isEvolveItem() == evolveItem)
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().addAmount(clamped);
        } else {
            items.add(BackpackItem.create(material, clamped, evolveItem));
        }

        updateStats();
        return true;
    }

    /**
     * Checks if the backpack contains the specified material.
     *
     * @param material The material to check for
     * @return true if the material exists in the backpack
     */
    public boolean hasMaterial(Material material)
    {
        return items.stream().anyMatch(item -> item.getMaterial() == material);
    }

    /**
     * Gets the total amount of a specific material in the backpack.
     *
     * @param material The material to count
     * @return The total amount of the material
     */
    public int getMaterialAmount(Material material)
    {
        return items.stream()
                .filter(item -> item.getMaterial() == material)
                .mapToInt(BackpackItem::getAmount)
                .sum();
    }

    /**
     * Checks if the backpack has enough of a specific material.
     *
     * @param material The material to check
     * @param required The required amount
     * @return true if sufficient material is available
     */
    public boolean hasEnoughMaterial(Material material, int required)
    {
        return getMaterialAmount(material) >= required;
    }

    /**
     * Consumes a specific amount of material from the backpack.
     *
     * @param material The material to consume
     * @param amount   The amount to consume
     * @return true if the material was consumed successfully
     */
    public boolean consumeMaterial(Material material, int amount)
    {
        if (!hasEnoughMaterial(material, amount)) {
            return false;
        }

        int remaining = amount;
        Iterator<BackpackItem> iterator = items.iterator();

        while (iterator.hasNext() && remaining > 0) {
            BackpackItem item = iterator.next();
            if (item.getMaterial() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    iterator.remove();
                } else {
                    item.removeAmount(remaining);
                    remaining = 0;
                }
            }
        }

        updateStats();
        return true;
    }

    /**
     * Removes all items marked for deletion and updates stats.
     */
    public void removeMarkedItems()
    {
        items.removeIf(BackpackItem::isMarkedForDeletion);
        updateStats();
    }

    /**
     * Clears all items from the backpack.
     */
    public void clear()
    {
        items.clear();
        updateStats();
    }

    /**
     * Gets the number of unique item stacks in the backpack.
     *
     * @return The number of stacks
     */
    public int getStackCount()
    {
        return items.size();
    }

    /**
     * Gets the remaining capacity for new stacks.
     *
     * @return The number of new stacks that can be added
     */
    public int getRemainingCapacity()
    {
        return maxSize - items.size();
    }

    /**
     * Updates backpack statistics (totalItems and full status).
     */
    private void updateStats()
    {
        this.totalItems = items.stream().mapToInt(BackpackItem::getAmount).sum();
        this.full = items.size() >= maxSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document toDocument()
    {
        Document doc = new Document();
        doc.put("maxSize", maxSize);
        doc.put("totalItems", totalItems);
        doc.put("full", full);

        List<Document> itemDocs = new ArrayList<>();
        for (BackpackItem item : items) {
            itemDocs.add(item.toDocument());
        }
        doc.put("items", itemDocs);

        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        this.maxSize = document.getInteger("maxSize", 100);
        this.totalItems = document.getInteger("totalItems", 0);
        this.full = document.getBoolean("full", false);

        List<Document> itemDocs = document.getList("items", Document.class);
        this.items = new ArrayList<>();
        if (itemDocs != null) {
            for (Document itemDoc : itemDocs) {
                BackpackItem item = new BackpackItem();
                item.fromDocument(itemDoc);
                items.add(item);
            }
        }
        // Recompute stats after load so cached fields reflect actual item list
        updateStats();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(String parentId, String playerId, Utils utils) {
    }
}
