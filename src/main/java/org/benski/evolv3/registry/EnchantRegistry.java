package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.EnchantRegistryManager;
import org.benski.evolv3.enchants.ExplosiveEnchant;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * A registry to register custom enchantment listeners.
 * In Paper 1.21+, enchantments themselves are registered in Evolv3Bootstrap.
 * This registry only handles the event listeners for enchantment functionality.
 */
public class EnchantRegistry implements Registry<Listener>
{
    @Getter
    private static final EnchantRegistry instance = new EnchantRegistry();
    private final EnchantRegistryManager registryManager = new EnchantRegistryManager(Main.getInstance());

    /**
     * Register custom enchantment listeners here.
     * Add your custom enchantment listener classes to this list.
     */
    private final List<Class<? extends Listener>> enchantmentListeners = List.of(
            ExplosiveEnchant.class
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        enchantmentListeners.forEach(registryManager::registerEnchantmentListener);
        registryManager.initializeListeners();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Listener get(String id) {
        // Not applicable for listeners
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        registryManager.cleanup();
    }
}
