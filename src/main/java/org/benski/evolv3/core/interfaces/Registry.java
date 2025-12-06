package org.benski.evolv3.core.interfaces;

/**
 * Interface for all Evolve plugin commands, providing standardized command handling
 * and tab completion functionality.
 */
public interface Registry<T>
{
    /**
     *  Initialize the registry
     */
    void initialize();

    /**
     * Retrieves an item from the registry.
     * @param id The id of the item you want to get
     * @return The entity of the registry
     */
    T get(String id);

    /**
     * Cleanup the registered entities
     */
    void cleanup();
}