package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * A registry to register config files.
 */
public class ConfigRegistry implements Registry<FileConfiguration>
{
    @Getter
    private static final ConfigRegistry instance = new ConfigRegistry();
    private final StorageRegistryManager storageManager = new StorageRegistryManager(Main.getInstance());

    /**
     * Register configs.
     */
    private final List<String> configs = List.of(
            "default",
            "messages",
            "menus",
            "enchants",
            "ranks"
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        configs.forEach(storageManager::registerConfig);
        storageManager.initializeConfigs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileConfiguration get(String id) {
        return storageManager.getConfig(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        configs.forEach(storageManager::deleteConfig);
    }

}
