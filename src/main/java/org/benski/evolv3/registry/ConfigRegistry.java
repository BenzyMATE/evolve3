package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.bukkit.configuration.file.FileConfiguration;

/** A registry to register config files. */
public class ConfigRegistry implements Registry<FileConfiguration> {
  @Getter private static final ConfigRegistry instance = new ConfigRegistry();
  private final StorageRegistryManager storageManager =
      new StorageRegistryManager(Main.getInstance());

  /** Register configs. */
  private final List<String> configs =
      List.of(
          "default",
          "messages",
          "menus",
          "enchants",
          "ranks",
          "stones",
          "mines",
          "spawners",
          "scrolls",
          "cells",
          "localdata/borders",
          "localdata/beams",
          "localdata/launchpads");

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    configs.forEach(storageManager::registerConfig);
    storageManager.initializeConfigs();
  }

  /** {@inheritDoc} */
  @Override
  public FileConfiguration get(String id) {
    return storageManager.getConfig(id);
  }

  /**
   * Saves a config file to disk.
   *
   * @param id the config name (without .yml extension)
   */
  public void save(String id) {
    storageManager.saveConfig(id);
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    configs.forEach(storageManager::unloadConfig);
  }
}
