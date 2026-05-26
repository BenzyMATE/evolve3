package org.benski.evolv3.core.managers.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.bukkit.plugin.java.JavaPlugin;

/** Manages registration and lifecycle of all data centers. */
public class DataCenterRegistryManager {

  private final JavaPlugin plugin;
  private final Map<String, DataCenter<?>> dataCenterMap = new HashMap<>();
  private final List<Class<? extends BaseDataCenter<?>>> dataCenterClasses = new ArrayList<>();

  /** Creates the DataCenterRegistryManager. @param plugin the owning plugin */
  public DataCenterRegistryManager(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Registers a data center class for deferred instantiation via {@link #initializeDataCenters}.
   */
  public void registerDataCenter(Class<? extends BaseDataCenter<?>> dataCenterClass) {
    dataCenterClasses.add(dataCenterClass);
  }

  /** Instantiates all registered data centers using their no-arg constructors. */
  public void initializeDataCenters() {
    for (Class<? extends BaseDataCenter<?>> dataCenterClass : dataCenterClasses) {
      try {
        DataCenter<?> dataCenter = dataCenterClass.getDeclaredConstructor().newInstance();
        String identifier = dataCenterClass.getSimpleName().replace("DataCenter", "").toLowerCase();
        dataCenterMap.put(identifier, dataCenter);
      } catch (Exception e) {
        plugin
            .getLogger()
            .severe(
                "Failed to register data center "
                    + dataCenterClass.getSimpleName()
                    + ": "
                    + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /** Returns the data center registered under the given identifier, or null. */
  public DataCenter<?> getDataCenter(String identifier) {
    return dataCenterMap.get(identifier.toLowerCase());
  }

  /** Returns a typed data center by identifier, or null if not found or wrong type. */
  @SuppressWarnings("unchecked")
  public <T> DataCenter<T> getDataCenter(String identifier, Class<T> type) {
    DataCenter<?> dataCenter = dataCenterMap.get(identifier.toLowerCase());
    if (dataCenter == null) return null;
    try {
      return (DataCenter<T>) dataCenter;
    } catch (ClassCastException e) {
      plugin.getLogger().warning("Data center " + identifier + " is not of expected type");
      return null;
    }
  }

  /** Returns true if a data center is registered under the given identifier. */
  public boolean isDataCenterRegistered(String identifier) {
    return dataCenterMap.containsKey(identifier.toLowerCase());
  }

  /** Returns a snapshot of all registered data centers keyed by identifier. */
  public Map<String, DataCenter<?>> getAllDataCenters() {
    return new HashMap<>(dataCenterMap);
  }

  /** Clears all registered data centers. */
  public void cleanup() {
    dataCenterMap.clear();
    dataCenterClasses.clear();
  }
}
