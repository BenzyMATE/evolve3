package org.benski.evolv3.core.interfaces;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Consumer;

public interface DataCenter<T>
{
    /**
     * Retrieves data for the specified player and executes the callback with the result.
     * This method looks up cached data for the player and passes it to the provided callback.
     * The callback is executed synchronously if data is found in cache.
     * 
     * @param player the player whose data should be retrieved
     * @param callback function to execute with the retrieved data object
     */
    void get(Player player, Consumer<T> callback);

    /**
     * Retrieves all cached data objects managed by this data center.
     * The returned map provides direct access to the internal cache structure.
     * Modifications to the returned map may affect the data center's state.
     *
     * @return map of all cached data where keys are unique identifiers (typically UUIDs) 
     *         and values are the data objects
     */
    Map<String, T> getAll();

    /**
     * Asynchronously loads a data object from persistent storage into the cache.
     * This method performs database operations on a background thread to avoid blocking
     * the main server thread. Once loaded, the data becomes available through get() calls.
     *
     * @param player the player associated with the data being loaded
     * @param id the unique identifier (typically UUID string) for the data to load
     */
    void loadIntoCache(Player player, String id);


    /**
     * Checks whether data with the specified ID exists in the local cache.
     * This method performs a fast, synchronous lookup without database access.
     * Use this to determine if data is immediately available before calling get().
     *
     * @param id the unique identifier to check for existence in cache
     * @return true if data exists in cache, false otherwise
     */
    boolean exists(String id);

    /**
     * Asynchronously checks if data with the specified ID exists in persistent storage.
     * This method performs a database query on a background thread and executes
     * the callback on the main thread with the result.
     *
     * @param id the unique identifier to check for existence in database
     * @param callback function executed with true if data exists in database, false otherwise
     */
    void existsInDatabase(String id, Consumer<Boolean> callback);

    /**
     * Retrieves a data object directly from cache for the specified player.
     * This method provides immediate, synchronous access to cached data without
     * any database operations. Returns null if data is not found in cache.
     * 
     * @param player the player whose data should be retrieved from cache
     * @return the cached data object, or null if not found in cache
     * @throws UnsupportedOperationException if this data center doesn't support player-based lookup
     */
    default T getLocal(Player player) {
        throw new UnsupportedOperationException("getLocal(Player) not supported by this DataCenter");
    }

    /**
     * Retrieves a data object directly from cache using a forced/override ID.
     * This method allows retrieving data with a specific ID regardless of the player,
     * useful for cases where the ID doesn't match the player's UUID.
     * 
     * @param player the player context for the request
     * @param forcedId the specific ID to use for cache lookup instead of player's UUID
     * @return the cached data object, or null if not found
     * @throws UnsupportedOperationException if this data center doesn't support forced ID lookup
     */
    default T getLocal(Player player, String forcedId) {
        throw new UnsupportedOperationException("getLocal(Player, String) not supported by this DataCenter");
    }

    /**
     * Loads existing data from database or creates new data if none exists.
     * This method first checks the database for existing data with the given ID.
     * If found, it loads the data into cache. If not found, it creates a new data object
     * with default values, saves it to database, and adds it to cache.
     *
     * @param player the player for whom data is being loaded or created
     * @param id the unique identifier for the data (typically player UUID)
     */
    void loadOrCreate(Player player, String id);

    /**
     * Updates the cached data with the provided data object.
     * This method replaces the cached version with the provided data and optionally
     * saves the changes to persistent storage.
     *
     * @param profile the updated data object to store in cache
     * @param save whether to persist the changes to database immediately
     */
    void updateLocal(T profile, boolean save);
}
