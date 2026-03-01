package org.benski.evolv3.models.backpack;

import lombok.Getter;
import lombok.Setter;
import org.benski.evolv3.core.abstracts.MongoSubDoc;
import org.benski.evolv3.core.helpers.Utils;
import org.bson.Document;
import org.bukkit.Material;

import java.util.UUID;

/**
 * Represents an individual item stored in a player's backpack.
 * Each item tracks the material type, quantity, and whether it came from an enchantment effect.
 */
@Getter
@Setter
public class BackpackItem extends MongoSubDoc
{
    /**
     * Unique identifier for this backpack item.
     */
    private String id;

    /**
     * The material type of this item.
     */
    private Material material;

    /**
     * The quantity of this item.
     */
    private int amount;

    /**
     * Whether this item came from an enchantment effect (alchemy, furnace, etc.).
     */
    private boolean evolveItem;

    /**
     * Flag for soft deletion.
     */
    private boolean markedForDeletion;

    /**
     * Creates a new BackpackItem.
     *
     * @param material   The material type
     * @param amount     The quantity
     * @param evolveItem Whether from an enchantment effect
     * @return A new BackpackItem
     */
    public static BackpackItem create(Material material, int amount, boolean evolveItem)
    {
        BackpackItem item = new BackpackItem();
        item.id = UUID.randomUUID().toString();
        item.material = material;
        item.amount = amount;
        item.evolveItem = evolveItem;
        item.markedForDeletion = false;
        return item;
    }

    /**
     * Adds to the item amount.
     *
     * @param additionalAmount The amount to add
     */
    public void addAmount(int additionalAmount)
    {
        this.amount += additionalAmount;
    }

    /**
     * Removes from the item amount.
     *
     * @param removeAmount The amount to remove
     * @return true if the item should be deleted (amount <= 0)
     */
    public boolean removeAmount(int removeAmount)
    {
        this.amount -= removeAmount;
        return this.amount <= 0;
    }

    /**
     * Marks this item for deletion.
     */
    public void markForDeletion()
    {
        this.markedForDeletion = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document toDocument()
    {
        Document doc = new Document();
        doc.put("id", id);
        doc.put("material", material.name());
        doc.put("amount", amount);
        doc.put("evolveItem", evolveItem);
        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        this.id = document.getString("id");
        String materialStr = document.getString("material");
        this.material = materialStr != null ? Material.getMaterial(materialStr) : Material.STONE;
        this.amount = document.getInteger("amount", 0);
        this.evolveItem = document.getBoolean("evolveItem", false);
        this.markedForDeletion = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(String parentId, String playerId, Utils utils) {
    }
}
