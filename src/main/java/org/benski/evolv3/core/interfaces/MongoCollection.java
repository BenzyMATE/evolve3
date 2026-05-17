package org.benski.evolv3.core.interfaces;

import java.util.function.Consumer;
import org.bson.Document;

/**
 * Interface for MongoDB collection models that represent complete documents stored in MongoDB
 * collections.
 *
 * <p>This interface is designed for top-level entities that have their own collection in MongoDB,
 * such as PlayerProfile (stored in "Players" collection) and PickaxeProfile (stored in "Pickaxes"
 * collection). These models can contain embedded sub-documents but are themselves the primary
 * document in a collection.
 *
 * <p>Models implementing this interface should handle:
 *
 * <ul>
 *   <li>Complete CRUD operations (save, load, delete)
 *   <li>Collection management and naming
 *   <li>Document serialization/deserialization including embedded objects
 *   <li>Unique identification within their collection
 * </ul>
 *
 * @see MongoSubDocument for embedded document models
 * @author Evolve Development Team
 * @since 1.0.0
 */
public interface MongoCollection {

  /**
   * Gets the name of the MongoDB collection where this model's documents are stored.
   *
   * @return the collection name (e.g., "Players", "Pickaxes")
   */
  String getCollectionName();

  /**
   * Converts this model instance to a MongoDB Document for storage.
   *
   * <p>This method should handle the complete serialization of the model, including any embedded
   * sub-documents by calling their respective {@code toDocument()} methods.
   *
   * @return a MongoDB Document representation of this model
   */
  Document toDocument();

  /**
   * Populates this model instance from a MongoDB Document.
   *
   * <p>This method should handle the complete deserialization of the model, including any embedded
   * sub-documents by calling their respective {@code fromDocument()} methods. It should also handle
   * cases where expected fields might be missing from the document.
   *
   * @param document the MongoDB Document to load data from
   */
  void fromDocument(Document document);

  /**
   * Gets the name of the field that serves as the unique identifier for this model.
   *
   * @return the field name used as the unique identifier (typically "ID" or "_id")
   */
  String getIdField();

  /**
   * Gets the value of the unique identifier for this model instance.
   *
   * @return the unique identifier value
   */
  Object getIdValue();

  /**
   * Asynchronously saves this model to the MongoDB collection.
   *
   * <p>This method performs an upsert operation, creating a new document if none exists with the
   * current ID, or updating the existing document. The operation is performed asynchronously and
   * the callback is executed upon completion.
   *
   * @param callback function executed with {@code true} if save succeeded, {@code false} if failed
   */
  void save(Consumer<Boolean> callback);

  /**
   * Synchronously saves this model to the MongoDB collection.
   *
   * <p>This is a convenience method that calls {@link #save(Consumer)} with a null callback. Use
   * this when you don't need to be notified of the save completion.
   */
  void save();
}
