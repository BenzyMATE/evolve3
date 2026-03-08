package org.benski.evolv3.core.managers;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core manager that handles listener registration and management.
 * Automatically registers listeners with Bukkit's event system.
 */
public class ListenerRegistryManager
{
    private final JavaPlugin plugin;
    private final Map<String, BaseListener> listenerMap = new HashMap<>();
    private final List<Class<? extends BaseListener>> listenerClasses = new ArrayList<>();

    public ListenerRegistryManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a listener class to be initialized later.
     *
     * @param listenerClass the listener class to register
     */
    public void registerListener(Class<? extends BaseListener> listenerClass) {
        listenerClasses.add(listenerClass);
    }

    /**
     * Initializes all registered listeners.
     * Creates instances and registers them with Bukkit's event system.
     */
    public void initializeListeners() {
        for (Class<? extends BaseListener> listenerClass : listenerClasses) {
            try {
                // Create instance using constructor with JavaPlugin parameter
                Constructor<? extends BaseListener> constructor = listenerClass.getConstructor(JavaPlugin.class);
                BaseListener listener = constructor.newInstance(plugin);

                // Generate identifier from class name (e.g., PlayerJoinListener -> playerjoin)
                String identifier = listenerClass.getSimpleName()
                        .replace("Listener", "")
                        .toLowerCase();

                // Register in our map
                listenerMap.put(identifier, listener);

                // Register with Bukkit's event system
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);

                // Call initialize method
                listener.initialize();


            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register listener " +
                        listenerClass.getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets a listener instance by identifier.
     *
     * @param identifier the listener identifier (e.g., "playerjoin")
     * @return the listener instance, or null if not found
     */
    public BaseListener getListener(String identifier) {
        return listenerMap.get(identifier.toLowerCase());
    }

    /**
     * Gets a typed listener instance by identifier.
     *
     * @param identifier the listener identifier
     * @param type the expected listener class
     * @param <T> the listener type
     * @return the typed listener instance, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseListener> T getListener(String identifier, Class<T> type) {
        BaseListener listener = listenerMap.get(identifier.toLowerCase());
        if (listener == null) {
            return null;
        }

        try {
            return (T) listener;
        } catch (ClassCastException e) {
            plugin.getLogger().warning("Listener " + identifier + " is not of expected type");
            return null;
        }
    }

    /**
     * Checks if a listener is registered.
     *
     * @param identifier the listener identifier
     * @return true if registered
     */
    public boolean isListenerRegistered(String identifier) {
        return listenerMap.containsKey(identifier.toLowerCase());
    }

    /**
     * Gets all registered listeners.
     *
     * @return map of all listeners
     */
    public Map<String, BaseListener> getAllListeners() {
        return new HashMap<>(listenerMap);
    }

    /**
     * Cleanup all registered listeners.
     * Unregisters from Bukkit and calls cleanup methods.
     */
    public void cleanup() {
        for (Map.Entry<String, BaseListener> entry : listenerMap.entrySet()) {
            try {
                String identifier = entry.getKey();
                BaseListener listener = entry.getValue();

                // Call cleanup method
                listener.cleanup();

                // Unregister from Bukkit's event system
                HandlerList.unregisterAll(listener);

            } catch (Exception e) {
                plugin.getLogger().warning("Error cleaning up listener: " + e.getMessage());
            }
        }

        listenerMap.clear();
        listenerClasses.clear();
    }
}
