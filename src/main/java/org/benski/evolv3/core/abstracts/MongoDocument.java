package org.benski.evolv3.core.abstracts;

import java.util.logging.Logger;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.bukkit.entity.Player;

/** A mongo document that saves to the database. */
public abstract class MongoDocument implements MongoCollection {
  protected static final MongoManager mongoManager = MongoManager.getInstance();
  protected static final Logger LOGGER = Logger.getLogger("Evolv3");

  /** Initializes the entity */
  public abstract void initialize(Player player);

  /** {@inheritDoc} */
  @Override
  public void save() {
    save(null);
  }
}
