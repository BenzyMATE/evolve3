package org.benski.evolv3.core.managers;

import org.benski.evolv3.Main;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager that handles custom enchantment listener registration.
 * In Paper 1.21+, enchantments are registered via the bootstrap class.
 * This manager only handles registering the event listeners for enchantment functionality.
 */
public class EnchantRegistryManager
{
    private final JavaPlugin plugin;
    private final List<Class<? extends Listener>> listenerClasses = new ArrayList<>();
    private final List<Listener> registeredListeners = new ArrayList<>();

    public EnchantRegistryManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an enchantment listener class to be initialized later.
     *
     * @param listenerClass the listener class to register
     */
    public void registerEnchantmentListener(Class<? extends Listener> listenerClass) {
        listenerClasses.add(listenerClass);
    }

    /**
     * Initializes all registered enchantment listeners.
     * Creates instances and registers them with Bukkit's event system.
     */
    public void initializeListeners() {
        for (Class<? extends Listener> listenerClass : listenerClasses) {
            try {
                // Create instance using default constructor
                Constructor<? extends Listener> constructor = listenerClass.getConstructor();
                Listener listener = constructor.newInstance();

                // Register the listener
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                registeredListeners.add(listener);

                plugin.getLogger().info(Main.getBlue() + "Registered enchantment listener: " + listenerClass.getSimpleName() + Main.getReset());

            } catch (Exception e) {
                plugin.getLogger().severe(Main.getBlue() + "Failed to register enchantment listener " + listenerClass.getSimpleName() + ": " + e.getMessage() + Main.getReset());
                e.printStackTrace();
            }
        }
    }

    /**
     * Cleanup all registered listeners.
     */
    public void cleanup() {
        registeredListeners.clear();
        listenerClasses.clear();
    }
}
