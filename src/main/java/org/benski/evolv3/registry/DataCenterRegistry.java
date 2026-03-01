package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.DataCenterRegistryManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;

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
     * PlayerDataCenter is registered first as other data centers may depend on player profiles.
     */
    private final List<Class<? extends BaseDataCenter<?>>> datacenters = List.of(
            PlayerDataCenter.class,
            ArmorDataCenter.class,
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

    /**
     * Gets the PickaxeDataCenter instance.
     *
     * @return the PickaxeDataCenter
     */
    public PickaxeDataCenter getPickaxeDataCenter() {
        return (PickaxeDataCenter) registryManager.getDataCenter("pickaxe");
    }

    /**
     * Gets the PlayerDataCenter instance.
     *
     * @return the PlayerDataCenter
     */
    public PlayerDataCenter getPlayerDataCenter() {
        return (PlayerDataCenter) registryManager.getDataCenter("player");
    }

    /**
     * Gets the ArmorDataCenter instance.
     *
     * @return the ArmorDataCenter
     */
    public ArmorDataCenter getArmorDataCenter() {
        return (ArmorDataCenter) registryManager.getDataCenter("armor");
    }
}
