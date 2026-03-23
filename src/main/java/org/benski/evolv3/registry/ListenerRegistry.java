package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.ListenerRegistryManager;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.listeners.EnchantListener;
import org.benski.evolv3.listeners.BreakListener;
import org.benski.evolv3.listeners.FurnaceListener;
import org.benski.evolv3.listeners.InteractListener;
import org.benski.evolv3.listeners.JoinListener;
import org.benski.evolv3.listeners.PickaxeOwnershipListener;
import org.benski.evolv3.listeners.RankChatListener;
import org.benski.evolv3.listeners.ScoreboardListener;

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
            JoinListener.class,
            BreakListener.class,
            EnchantListener.class,
            InteractListener.class,
            BackpackListener.class,
            FurnaceListener.class,
            PickaxeOwnershipListener.class,
            ScoreboardListener.class,
            RankChatListener.class
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
