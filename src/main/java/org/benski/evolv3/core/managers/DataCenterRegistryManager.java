package org.benski.evolv3.core.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Core manager that handles data center registration and management. Data centers manage profile
 * data from MongoDB and provide caching.
 */
public class DataCenterRegistryManager {
  private final JavaPlugin plugin;
  private final Map<String, DataCenter<?>> dataCenterMap = new HashMap<>();
  private final List<Class<? extends BaseDataCenter<?>>> dataCenterClasses = new ArrayList<>();

  public DataCenterRegistryManager(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Registers a data center class to be initialized later.
   *
   * @param dataCenterClass the data center class to register
   */
  public void registerDataCenter(Class<? extends BaseDataCenter<?>> dataCenterClass) {
    dataCenterClasses.add(dataCenterClass);
  }

  /**
   * Initializes all registered data centers. Creates instances using default no-arg constructor.
   * Data centers use getInstance() to get their dependencies.
   */
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

  /**
   * Gets a data center instance by identifier.
   *
   * @param identifier the data center identifier (e.g., "pickaxe")
   * @return the data center instance, or null if not found
   */
  public DataCenter<?> getDataCenter(String identifier) {
    return dataCenterMap.get(identifier.toLowerCase());
  }

  /**
   * Gets a typed data center instance by identifier.
   *
   * @param identifier the data center identifier
   * @param type the expected type class
   * @param <T> the profile type
   * @return the typed data center instance, or null if not found or wrong type
   */
  @SuppressWarnings("unchecked")
  public <T extends MongoCollection> DataCenter<T> getDataCenter(String identifier, Class<T> type) {
    DataCenter<?> dataCenter = dataCenterMap.get(identifier.toLowerCase());
    if (dataCenter == null) {
      return null;
    }

    try {
      return (DataCenter<T>) dataCenter;
    } catch (ClassCastException e) {
      plugin.getLogger().warning("Data center " + identifier + " is not of expected type");
      return null;
    }
  }

  /**
   * Checks if a data center is registered.
   *
   * @param identifier the data center identifier
   * @return true if registered
   */
  public boolean isDataCenterRegistered(String identifier) {
    return dataCenterMap.containsKey(identifier.toLowerCase());
  }

  /**
   * Gets all registered data centers.
   *
   * @return map of all data centers
   */
  public Map<String, DataCenter<?>> getAllDataCenters() {
    return new HashMap<>(dataCenterMap);
  }

  /** Cleanup all registered data centers. Saves all cached data and clears internal state. */
  public void cleanup() {

    dataCenterMap.clear();
    dataCenterClasses.clear();
  }
}
