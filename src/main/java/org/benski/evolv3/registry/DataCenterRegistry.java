package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.DataCenterRegistryManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;

import java.util.List;

/**
 * A registry to register data centers.
 */
public class DataCenterRegistry implements Registry<DataCenter<?>>
{
    @Getter
    private static final DataCenterRegistry instance = new DataCenterRegistry();
    private final DataCenterRegistryManager registryManager = new DataCenterRegistryManager(Main.getInstance());

    /**
     * Register data centers.
     */
    private final List<Class<? extends BaseDataCenter<?>>> datacenters = List.of(
            PickaxeDataCenter.class
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        datacenters.forEach(registryManager::registerDataCenter);
        registryManager.initializeDataCenters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataCenter<?> get(String id) {
        return registryManager.getDataCenter(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        registryManager.cleanup();
    }

}
