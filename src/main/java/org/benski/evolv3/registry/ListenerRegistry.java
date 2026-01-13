package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.ListenerRegistryManager;
import org.benski.evolv3.listeners.OnJoinListener;

import java.util.List;

/**
 * A registry to register event listeners.
 */
public class ListenerRegistry implements Registry<BaseListener>
{
    @Getter
    private static final ListenerRegistry instance = new ListenerRegistry();
    private final ListenerRegistryManager registryManager = new ListenerRegistryManager(Main.getInstance());

    /**
     * Register listeners here.
     * Add your listener classes to this list.
     */
    private final List<Class<? extends BaseListener>> listeners = List.of(
            OnJoinListener.class
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        listeners.forEach(registryManager::registerListener);
        registryManager.initializeListeners();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BaseListener get(String id) {
        return registryManager.getListener(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        registryManager.cleanup();
    }

}
