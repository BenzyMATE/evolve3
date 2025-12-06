package org.benski.evolv3.models;

import org.benski.evolv3.core.abstracts.MongoDocument;
import org.bson.Document;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles the pickaxe data on a players pickaxe
 */
public class PickaxeProfile extends MongoDocument
{
    /**
     * Defines the key/id of the players pickaxe
     */
    public UUID key;
    /**
     * Defines the owner of the pickaxe
     */
    public UUID owner;
    /**
     * Defines the pickaxes level
     */
    public int level;
    /**
     * Defines the pickaxes xp
     */
    public int xp;
    /**
     * Defines the target xp
     */
    public int target;
    /**
     * Defines the evolution of the pickaxe
     */
    public int evolution;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize()
    {
        key = UUID.randomUUID();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCollectionName()
    {
        return "Pickaxes";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document toDocument()
    {
        Document doc = new Document();
        doc.put("key", key);
        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        this.key = UUID.fromString(document.getString("key"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIdField() {
        return "key";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getIdValue() {
        return this.key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(Consumer<Boolean> callback)
    {
        mongoManager.upsertAsync(this)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        super.LOGGER.error("Failed to save pickaxe profile: {}", throwable.getMessage());
                    }
                    if (callback != null) {
                        callback.accept(throwable == null);
                    }
                });
    }
}
