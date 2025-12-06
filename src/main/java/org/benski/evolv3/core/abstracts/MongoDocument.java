package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.slf4j.LoggerFactory;

/**
 * A mongo document that saves to the database
 */
public abstract class MongoDocument implements MongoCollection
{
    protected MongoManager mongoManager;
    protected Utils utils;
    protected org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MongoDocument.class);

    /**
     * Initializes the entity
     */
    public abstract void initialize(/* params */);

    /**
     * Provides default save functionality
     */
    @Override
    public void save()
    {
        save(null);
    }
}