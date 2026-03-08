package org.benski.evolv3.core.managers;

import org.benski.evolv3.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageRegistryManager
{
    private final JavaPlugin plugin;
    private final File dataFolder;
    private static final StorageRegistryManager instance = new StorageRegistryManager(Main.getInstance());
    private final Map<String, FileConfiguration> configCache = new HashMap<>();
    private final Map<String, File> registeredConfigs = new HashMap<>();
    private final Map<String, File> registeredBins = new HashMap<>();

    public StorageRegistryManager(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();

        // Create data folder if it doesn't exist
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * Gets the MongoManager instance.
     */
    public static StorageRegistryManager getInstance() {
        return instance;
    }

    /**
     * Register a config file to be managed by StorageManager.
     * This will automatically create/copy it from resources on initialization.
     *
     * @param name The name of the config (without .yml extension)
     */
    public void registerConfig(String name)
    {
        File configFile = new File(dataFolder, name + ".yml");
        registeredConfigs.put(name, configFile);
    }

    /**
     * Register a binary file to be managed by StorageManager.
     *
     * @param name The name of the binary file (without .bin extension)
     */
    public void registerBin(String name)
    {
        File binFile = new File(dataFolder, name + ".bin");
        registeredBins.put(name, binFile);
        plugin.getLogger().info("Bin named " + name + " has been registered");
    }

    /**
     * Initialize all registered configs - creates them from resources if they don't exist.
     * Call this when your plugin enables.
     */
    public void initializeConfigs()
    {
        List<String> created = new ArrayList<>();

        for (Map.Entry<String, File> entry : registeredConfigs.entrySet())
        {
            String name = entry.getKey();
            File configFile = entry.getValue();

            if (!configFile.exists()) {
                try {
                    plugin.saveResource(name + ".yml", false);
                    created.add(name);
                } catch (IllegalArgumentException e) {
                    try {
                        configFile.createNewFile();
                        plugin.getLogger().warning("Config " + name + ".yml not found in resources, created empty file");
                    } catch (IOException ex) {
                        plugin.getLogger().severe("Failed to create config file: " + name + ".yml");
                        ex.printStackTrace();
                    }
                }
            }

            // Load into cache
            configCache.put(name, YamlConfiguration.loadConfiguration(configFile));
        }

        String names = String.join(", ", registeredConfigs.keySet());
        if (created.isEmpty()) {
            plugin.getLogger().info("Configs loaded: " + names);
        } else {
            plugin.getLogger().info("Configs loaded: " + names + " (created: " + String.join(", ", created) + ")");
        }
    }

    /**
     * Get a config file by name. Automatically loads it if not cached.
     *
     * @param name The name of the config (without .yml extension)
     * @return The FileConfiguration, or null if it doesn't exist
     */
    public FileConfiguration getConfig(String name)
    {
        // Return from cache if available
        if (configCache.containsKey(name)) {
            return configCache.get(name);
        }

        // Otherwise, load it
        File configFile = new File(dataFolder, name + ".yml");
        if (!configFile.exists()) {
            plugin.getLogger().warning("Config file not found: " + name + ".yml");
            return null;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configCache.put(name, config);
        return config;
    }

    /**
     * Save a config file.
     *
     * @param name The name of the config (without .yml extension)
     */
    public void saveConfig(String name)
    {
        FileConfiguration config = configCache.get(name);
        if (config == null) {
            plugin.getLogger().warning("Cannot save config " + name + " - not loaded in cache");
            return;
        }

        File configFile = new File(dataFolder, name + ".yml");
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config: " + name + ".yml");
            e.printStackTrace();
        }
    }

    /**
     * Reload a config file from disk.
     *
     * @param name The name of the config (without .yml extension)
     */
    public void reloadConfig(String name)
    {
        File configFile = new File(dataFolder, name + ".yml");
        if (!configFile.exists()) {
            plugin.getLogger().warning("Cannot reload config " + name + " - file doesn't exist");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configCache.put(name, config);
    }

    /**
     * Save all cached configs.
     */
    public void saveAllConfigs()
    {
        for (String name : configCache.keySet()) {
            saveConfig(name);
        }
    }

    /**
     * Write an object to a binary file using Java serialization.
     *
     * @param name The name of the binary file (without .bin extension)
     * @param object The object to serialize
     * @param <T> The type of object
     */
    public <T extends Serializable> void writeBin(String name, T object)
    {
        File binFile = new File(dataFolder, name + ".bin");

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(binFile))) {
            oos.writeObject(object);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write binary file: " + name + ".bin");
            e.printStackTrace();
        }
    }

    /**
     * Read an object from a binary file.
     *
     * @param name The name of the binary file (without .bin extension)
     * @param <T> The type of object to return
     * @return The deserialized object, or null if failed
     */
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T readBin(String name)
    {
        File binFile = new File(dataFolder, name + ".bin");

        if (!binFile.exists()) {
            plugin.getLogger().warning("Binary file not found: " + name + ".bin");
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(binFile))) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().severe("Failed to read binary file: " + name + ".bin");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if a config file exists.
     *
     * @param name The name of the config (without .yml extension)
     * @return true if the file exists
     */
    public boolean configExists(String name)
    {
        File configFile = new File(dataFolder, name + ".yml");
        return configFile.exists();
    }

    /**
     * Check if a binary file exists.
     *
     * @param name The name of the binary file (without .bin extension)
     * @return true if the file exists
     */
    public boolean binExists(String name)
    {
        File binFile = new File(dataFolder, name + ".bin");
        return binFile.exists();
    }

    /**
     * Delete a config file.
     *
     * @param name The name of the config (without .yml extension)
     * @return true if deletion was successful
     */
    public boolean deleteConfig(String name)
    {
        configCache.remove(name);
        File configFile = new File(dataFolder, name + ".yml");
        return configFile.delete();
    }

    /**
     * Delete a binary file.
     *
     * @param name The name of the binary file (without .bin extension)
     * @return true if deletion was successful
     */
    public boolean deleteBin(String name)
    {
        File binFile = new File(dataFolder, name + ".bin");
        return binFile.delete();
    }

    /**
     * Get the data folder.
     *
     * @return The plugin's data folder
     */
    public File getDataFolder()
    {
        return dataFolder;
    }

    /**
     * Copy a file from resources to the data folder (overwrite if exists).
     *
     * @param resourcePath Path in resources
     * @param targetName Target file name in data folder
     */
    public void copyFromResources(String resourcePath, String targetName)
    {
        File targetFile = new File(dataFolder, targetName);

        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("Resource not found: " + resourcePath);
                return;
            }

            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Copied resource: " + resourcePath + " -> " + targetName);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy resource: " + resourcePath);
            e.printStackTrace();
        }
    }
}