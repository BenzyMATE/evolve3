package org.benski.evolv3.core.managers;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Manager class responsible for all MongoDB operations in the Evolve plugin.
 * Provides asynchronous database operations using MongoDB's async driver.
 * Supports CRUD operations for MongoCollection objects and connection handling.
 * All database operations are performed asynchronously to avoid blocking the main server thread.
 */
public class MongoManager
{
    private final Logger LOGGER = LoggerFactory.getLogger(MongoManager.class);
    private MongoClient mongoClient;
    private MongoDatabase database;

    // Shared thread pool for database operations to prevent thread exhaustion
    private final ExecutorService databaseExecutor = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors())
    );

    /**
     * Constructs a new MongoManager with the specified connection string.
     * Initializes the MongoDB client and connects to the database.
     *
     * @param connectionString MongoDB connection string
     */
    public MongoManager(String connectionString)
    {
        initConnection(connectionString);
    }

    /**
     * Initializes the MongoDB connection using the provided connection string.
     * Extracts the database name from the connection string and establishes connection.
     */
    private void initConnection(String connectionString)
    {
        try {
            mongoClient = MongoClients.create(connectionString);

            // Extract database name from connection string
            String databaseName = extractDatabaseName(connectionString);
            database = mongoClient.getDatabase(databaseName);

            // Test connection
            database.runCommand(new Document("ping", 1));
            LOGGER.info("MongoManager: Successfully connected to MongoDB.");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize MongoDB connection: {}", e.getMessage());
        }
    }

    /**
     * Extracts the database name from the MongoDB connection string.
     * If no database name is specified, defaults to "Evolve".
     */
    private String extractDatabaseName(String connectionString)
    {
        try {
            // Extract database name from connection string (after the last '/' and before '?')
            int lastSlash = connectionString.lastIndexOf('/');
            int questionMark = connectionString.indexOf('?', lastSlash);

            if (lastSlash != -1) {
                String dbPart = questionMark != -1
                    ? connectionString.substring(lastSlash + 1, questionMark)
                    : connectionString.substring(lastSlash + 1);

                if (!dbPart.isEmpty()) {
                    return dbPart;
                }
            }

            return "Evolve"; // Default database name
        } catch (Exception e) {
            LOGGER.warn("Could not extract database name from connection string, using default 'Evolve'");
            return "Evolve";
        }
    }

    /**
     * Legacy compatibility method for SQL-style calls.
     * @deprecated Use getAllAsync(String, String, Object, Class) instead
     */
    @Deprecated
    public <T extends MongoCollection> CompletableFuture<List<T>> getAllAsync(Class<T> modelClass, String fieldName, Object value)
    {
        try {
            T instance = modelClass.getDeclaredConstructor().newInstance();
            return getAllAsync(instance.getCollectionName(), fieldName, value, modelClass);
        } catch (Exception e) {
            LOGGER.error("Error creating model instance for legacy getAllAsync: {}", e.getMessage());
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Asynchronously retrieves a list of documents from the specified collection
     * where the value of the specified field matches a given value.
     *
     * @param collectionName The name of the collection to query
     * @param fieldName The name of the field to filter by
     * @param value The value to match against
     * @param modelClass The class type for result mapping
     * @return A CompletableFuture that resolves to a list of model objects
     */
    public <T extends MongoCollection> CompletableFuture<List<T>> getAllAsync(String collectionName, String fieldName, Object value, Class<T> modelClass)
    {
        return CompletableFuture.supplyAsync(() -> {
            List<T> results = new ArrayList<>();

            try {
                com.mongodb.client.MongoCollection<Document> collection = database.getCollection(collectionName);

                for (Document doc : collection.find(Filters.eq(fieldName, value))) {
                    T model = modelClass.getDeclaredConstructor().newInstance();
                    model.fromDocument(doc);
                    results.add(model);
                }
            } catch (Exception e) {
                LOGGER.error("Error in getAllAsync for collection {}: {}", collectionName, e.getMessage());
            }

            return results;
        }, databaseExecutor);
    }

    /**
     * Legacy compatibility method for SQL-style calls.
     * @deprecated Use getByIdAsync(String, String, Object, Class) instead
     */
    @Deprecated
    public <T extends MongoCollection> CompletableFuture<T> getByIdAsync(Class<T> modelClass, String idField, Object idValue)
    {
        try {
            T instance = modelClass.getDeclaredConstructor().newInstance();
            return getByIdAsync(instance.getCollectionName(), idField, idValue, modelClass);
        } catch (Exception e) {
            LOGGER.error("Error creating model instance for legacy getByIdAsync: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Asynchronously retrieves a single document from the specified collection by ID.
     *
     * @param collectionName The name of the collection to query
     * @param idField The name of the ID field
     * @param idValue The ID value to search for
     * @param modelClass The class type for result mapping
     * @return A CompletableFuture that resolves to the model object or null if not found
     */
    public <T extends MongoCollection> CompletableFuture<T> getByIdAsync(String collectionName, String idField, Object idValue, Class<T> modelClass)
    {
        return CompletableFuture.supplyAsync(() -> {
            try {
                com.mongodb.client.MongoCollection<Document> collection = database.getCollection(collectionName);
                Document doc = collection.find(Filters.eq(idField, idValue)).first();

                if (doc != null) {
                    T model = modelClass.getDeclaredConstructor().newInstance();
                    model.fromDocument(doc);
                    return model;
                }

                return null;
            } catch (Exception e) {
                LOGGER.error("Error in getByIdAsync for collection {}: {}", collectionName, e.getMessage());
                return null;
            }
        }, databaseExecutor);
    }

    /**
     * Asynchronously upserts (inserts or updates) a model in MongoDB.
     * Uses MongoDB's replaceOne with upsert option for efficient upsert operations.
     *
     * @param model The MongoCollection object to upsert
     * @return A CompletableFuture representing the completion of the upsert operation
     */
    public CompletableFuture<Void> upsertAsync(MongoCollection model)
    {
        return CompletableFuture.runAsync(() -> {
            try {
                com.mongodb.client.MongoCollection<Document> collection = database.getCollection(model.getCollectionName());
                Document document = model.toDocument();

                collection.replaceOne(
                    Filters.eq(model.getIdField(), model.getIdValue()),
                    document,
                    new ReplaceOptions().upsert(true)
                );
            } catch (Exception e) {
                LOGGER.error("Error upserting into collection {}: {}", model.getCollectionName(), e.getMessage());
            }
        }, databaseExecutor);
    }

    /**
     * Asynchronously deletes a document from the specified collection.
     *
     * @param collectionName The name of the collection
     * @param fieldName The field name to match against
     * @param value The value to match for deletion
     * @return A CompletableFuture representing the completion of the delete operation
     */
    public CompletableFuture<Void> deleteAsync(String collectionName, String fieldName, Object value)
    {
        return CompletableFuture.runAsync(() -> {
            try {
                com.mongodb.client.MongoCollection<Document> collection = database.getCollection(collectionName);
                collection.deleteOne(Filters.eq(fieldName, value));
            } catch (Exception e) {
                LOGGER.error("Error deleting from collection {} where {} = {}: {}", collectionName, fieldName, value, e.getMessage());
            }
        }, databaseExecutor);
    }

    /**
     * Asynchronously deletes multiple documents from a collection.
     * More efficient than individual delete operations when removing many items.
     *
     * @param collectionName The name of the collection
     * @param fieldName The field name to match against
     * @param values List of values to match for deletion
     * @return A CompletableFuture representing the completion of the batch deletion
     */
    public CompletableFuture<Void> batchDeleteAsync(String collectionName, String fieldName, List<Object> values)
    {
        if (values == null || values.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                com.mongodb.client.MongoCollection<Document> collection = database.getCollection(collectionName);
                collection.deleteMany(Filters.in(fieldName, values));

                LOGGER.info("Batch deleted {} documents from {}", values.size(), collectionName);
            } catch (Exception e) {
                LOGGER.error("Error in batch delete from collection {}: {}", collectionName, e.getMessage());
            }
        }, databaseExecutor);
    }

    /**
     * Performs a batch upsert operation for multiple models in a single operation.
     * More efficient than individual upsert operations when saving many items.
     *
     * @param models List of MongoCollection objects to upsert
     * @return A CompletableFuture representing the completion of the batch operation
     */
    public CompletableFuture<Void> batchUpsertAsync(List<? extends MongoCollection> models)
    {
        if (models == null || models.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Group models by collection for batch processing
                models.stream()
                    .collect(java.util.stream.Collectors.groupingBy(MongoCollection::getCollectionName))
                    .forEach((collectionName, collectionModels) -> {
                        com.mongodb.client.MongoCollection<Document> collection = database.getCollection(collectionName);

                        // Process each model in the collection
                        collectionModels.forEach(model -> {
                            try {
                                Document document = model.toDocument();
                                collection.replaceOne(
                                    Filters.eq(model.getIdField(), model.getIdValue()),
                                    document,
                                    new ReplaceOptions().upsert(true)
                                );
                            } catch (Exception e) {
                                LOGGER.error("Error in batch upsert for model: {}", e.getMessage());
                            }
                        });

                        LOGGER.info("Batch upserted {} documents in collection {}", collectionModels.size(), collectionName);
                    });

                LOGGER.info("Batch upsert operation completed successfully");
            } catch (Exception e) {
                LOGGER.error("Error in batch upsert: {}", e.getMessage());
            }
        }, databaseExecutor);
    }

    /**
     * Closes the MongoDB client and releases all connections.
     * Shuts down the database executor gracefully.
     */
    public void close()
    {
        // Shutdown the database executor first
        try {
            LOGGER.info("MongoManager: Shutting down database executor...");
            databaseExecutor.shutdown();

            // Wait for existing tasks to complete
            if (!databaseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warn("MongoManager: Database executor did not terminate gracefully, forcing shutdown...");
                databaseExecutor.shutdownNow();
            }
            LOGGER.info("MongoManager: Database executor shutdown completed.");
        } catch (InterruptedException e) {
            LOGGER.warn("MongoManager: Database executor shutdown interrupted: {}", e.getMessage());
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Then close the MongoDB client
        if (mongoClient != null) {
            mongoClient.close();
            LOGGER.info("MongoManager: MongoDB client closed.");
        }
    }
}