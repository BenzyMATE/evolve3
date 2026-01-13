package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * A mongo document that saves to the database
 */
public abstract class MongoDocument implements MongoCollection
{
    protected MongoManager mongoManager = MongoManager.getInstance();
    protected Utils utils;
    protected Logger LOGGER = Main.getInstance().getLogger();

    /**
     * Initializes the entity
     */
    public abstract void initialize(Player player);

    /**
     * Provides default save functionality
     */
    @Override
    public void save()
    {
        save(null);
    }
}