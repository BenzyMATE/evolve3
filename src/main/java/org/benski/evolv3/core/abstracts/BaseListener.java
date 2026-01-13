package org.benski.evolv3.core.abstracts;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base class for all event listeners in the plugin.
 * Provides common functionality and access to the plugin instance.
 */
public abstract class BaseListener implements Listener
{
    protected final JavaPlugin plugin;

    /**
     * Constructor for BaseListener.
     *
     * @param plugin The plugin instance
     */
    public BaseListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when the listener is registered.
     * Override this to perform initialization logic.
     */
    public void initialize() {
        // Default implementation does nothing
        // Subclasses can override to add initialization logic
    }

    /**
     * Called when the plugin is being disabled.
     * Override this to perform cleanup logic.
     */
    public void cleanup() {
        // Default implementation does nothing
        // Subclasses can override to add cleanup logic
    }

    /**
     * Gets the plugin instance.
     *
     * @return The plugin instance
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
}
