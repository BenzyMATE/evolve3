package org.benski.evolv3.core.managers;

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
        List<String> names = new ArrayList<>();

        for (Class<? extends Listener> listenerClass : listenerClasses) {
            try {
                Listener listener;

                try {
                    Constructor<? extends Listener> constructor = listenerClass.getConstructor(JavaPlugin.class);
                    listener = constructor.newInstance(plugin);
                } catch (NoSuchMethodException e) {
                    Constructor<? extends Listener> constructor = listenerClass.getConstructor();
                    listener = constructor.newInstance();
                }

                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                registeredListeners.add(listener);
                names.add(listenerClass.getSimpleName());

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register enchantment listener " + listenerClass.getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (!names.isEmpty()) {
            plugin.getLogger().info("Enchant listeners: " + String.join(", ", names));
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
