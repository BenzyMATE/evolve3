package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.interfaces.MongoSubDocument;

public abstract class MongoSubDoc implements MongoSubDocument
{
    protected String id;
    protected String owner;
    protected Utils utils;

    public abstract void initialize(String parentId, String playerId, Utils utils);
}