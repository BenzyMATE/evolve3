package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.interfaces.MongoSubDocument;

/**
 * Base class for embedded sub-documents within MongoDocument entities.
 */
public abstract class MongoSubDoc implements MongoSubDocument
{
    protected String id;
    protected String owner;

    /**
     * Initializes the sub-document with parent and player context.
     *
     * @param parentId the parent document ID
     * @param playerId the player UUID string
     * @param utils    the utils instance (unused, kept for API compatibility)
     */
    public abstract void initialize(String parentId, String playerId, Utils utils);
}