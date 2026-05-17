package org.benski.evolv3.core.interfaces;

import org.bson.Document;

/**
 * Interface for MongoDB sub-document models that are embedded within collection documents.
 *
 * <p>This interface is designed for models that are stored as embedded documents within a parent
 * {@link MongoCollection} model. Examples include Armor, Backpack, Enchants, ArmorItem,
 * BackpackItem, and EnchantItem. These models do not have their own collection but are serialized
 * as part of their parent document.
 *
 * <p>Models implementing this interface should handle:
 *
 * <ul>
 *   <li>Document serialization/deserialization for embedding
 *   <li>Data integrity within their embedded context
 *   <li>Proper initialization with required dependencies
 * </ul>
 *
 * <p><strong>Note:</strong> Sub-documents are saved as part of their parent collection model. They
 * do not handle their own persistence operations.
 *
 * @see MongoCollection for top-level collection models
 * @author Evolve Development Team
 * @since 1.0.0
 */
public interface MongoSubDocument {

  /**
   * Converts this sub-document model instance to a MongoDB Document for embedding.
   *
   * <p>This method should serialize all persistent fields of the model into a Document that can be
   * embedded within a parent document. Fields marked with {@code @NonPersisted} should be excluded
   * from serialization.
   *
   * <p>If this sub-document contains other sub-documents (e.g., a list of items), this method
   * should also handle their serialization by calling their respective {@code toDocument()}
   * methods.
   *
   * @return a MongoDB Document representation of this sub-document
   */
  Document toDocument();

  /**
   * Populates this sub-document model instance from a MongoDB Document.
   *
   * <p>This method should handle the complete deserialization of the sub-document, including any
   * nested sub-documents. It should also handle cases where expected fields might be missing from
   * the document by providing sensible defaults.
   *
   * <p>If this sub-document contains collections of other sub-documents, this method should iterate
   * through the embedded documents and populate the respective objects.
   *
   * @param document the MongoDB Document to load data from, may be null
   */
  void fromDocument(Document document);
}
