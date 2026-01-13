package org.benski.evolv3.models;

import org.benski.evolv3.core.abstracts.MongoDocument;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
    public String key;
    /**
     * Defines the owner of the pickaxe
     */
    public Player owner;
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
    public void initialize(Player player)
    {
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
        doc.put("id", key);
        doc.put("owner", owner.getUniqueId().toString());
        doc.put("level", level);
        doc.put("xp", xp);
        doc.put("target", target);
        doc.put("evolution", evolution);
        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        this.key = document.getString("id");
        this.owner = Bukkit.getPlayer(UUID.fromString(document.getString("owner")));
        this.level = document.getInteger("level", 1);
        this.xp = document.getInteger("xp", 0);
        this.target = document.getInteger("target", 100);
        this.evolution = document.getInteger("evolution", 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIdField() {
        return "id";
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
                        super.LOGGER.severe("Failed to save pickaxe profile: " + throwable.getMessage());
                    }
                    if (callback != null) {
                        callback.accept(throwable == null);
                    }
                });
    }
}
