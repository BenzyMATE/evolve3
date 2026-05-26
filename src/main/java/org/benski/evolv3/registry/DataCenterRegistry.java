package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.registry.DataCenterRegistryManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;

/** Registry for all data centers. Initializes and provides typed access to each data center. */
public class DataCenterRegistry implements Registry<DataCenter<?>> {
  @Getter private static final DataCenterRegistry instance = new DataCenterRegistry();
  private final DataCenterRegistryManager registryManager =
      new DataCenterRegistryManager(Main.getInstance());

  /**
   * Data center registration order. {@link PlayerDataCenter} must be first because all pickaxe data
   * is embedded within the player profile and loaded with it on join.
   */
  private final List<Class<? extends BaseDataCenter<?>>> datacenters =
      List.of(PlayerDataCenter.class, ArmorDataCenter.class);

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    datacenters.forEach(registryManager::registerDataCenter);
    registryManager.initializeDataCenters();
  }

  /** {@inheritDoc} */
  @Override
  public DataCenter<?> get(String id) {
    return registryManager.getDataCenter(id);
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    registryManager.cleanup();
  }

  /** Returns the {@link PlayerDataCenter} instance. */
  public PlayerDataCenter getPlayerDataCenter() {
    return (PlayerDataCenter) registryManager.getDataCenter("player");
  }

  /** Returns the {@link ArmorDataCenter} instance. */
  public ArmorDataCenter getArmorDataCenter() {
    return (ArmorDataCenter) registryManager.getDataCenter("armor");
  }
}
