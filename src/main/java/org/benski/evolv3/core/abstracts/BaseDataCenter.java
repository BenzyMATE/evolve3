package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

public abstract class BaseDataCenter<T extends MongoCollection> implements DataCenter<T>
{
    protected final Map<String, T> data = new HashMap<>();
    protected final MongoManager mongoManager;
    protected final StorageRegistryManager storageManager;
    protected final FileConfiguration messagesConfig;
    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(BaseDataCenter.class);

    public BaseDataCenter(MongoManager mongoManager, StorageRegistryManager storageManager)
    {
        this.mongoManager = mongoManager;
        this.storageManager = storageManager;
        messagesConfig = storageManager.getConfig("Messages");
    }

    @Override
    public Map<String, T> getAll() {
        return data;
    }

    @Override
    public Boolean exists(String id) {
        return data.containsKey(id);
    }

    @Override
    public void updateLocal(T profile, Boolean save) {
        if (save) {
            saveProfile(profile);
        }
        data.put(getProfileId(profile), profile);
    }

    @Override
    public void existsInDatabase(String id, Consumer<Boolean> callback) {
        LOGGER.info("{}Checking if profile exists in database for ID: {}", getLogPrefix(), id);
        this.mongoManager.getByIdAsync(getCollectionName(), "id", id, getProfileClass())
                .thenAcceptAsync(profile -> {
                    boolean exists = profile != null;
                    LOGGER.info("{}Database check result for ID {}: exists = {}", getLogPrefix(), id, exists);
                    callback.accept(exists);
                }, runnable -> Bukkit.getScheduler().runTask(Main.getInstance(), runnable))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to check existence for ID {}: {}", id, throwable.getMessage());
                    callback.accept(false);
                    return null;
                });
    }

    @Override
    public void loadIntoCache(Player player, String profileId) {
        LOGGER.info("{}loadIntoCache starting for ID: {}", getLogPrefix(), profileId);
        this.mongoManager.getByIdAsync(getCollectionName(), "id", profileId, getProfileClass())
                .thenAcceptAsync(profile -> {
                    LOGGER.info("{}Database query completed for ID: {}, result: {}", getLogPrefix(), profileId, profile != null ? "found" : "null");

                    if (profile == null) {
                        LOGGER.warn("{}Profile was null for ID {} despite existsInDatabase returning true!", getLogPrefix(), profileId);
                        return;
                    }

                    // Initialize the profile with dependencies
                    initializeProfile(profile, player);

                    // Add to cache
                    data.put(profileId, profile);
                    LOGGER.info("{}Successfully added profile to cache for ID: {}", getLogPrefix(), profileId);

                    // Notify player
                    player.sendMessage(formatMessage(player,
                            messagesConfig.getString(getLoadingMessagePath(),
                                    "Loading your profile...")));

                }, runnable -> Bukkit.getScheduler().runTask(Main.getInstance(), runnable))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to load profile {}: {}", profileId, throwable.getMessage());
                    return null;
                });
    }

    @Override
    public void loadOrCreate(Player player, String id) {
        if (exists(id)) {
            return; // Profile already loaded in cache
        }

        existsInDatabase(id, exists -> {
            LOGGER.info("{}Database existence callback called for ID {}: exists = {}", getLogPrefix(), id, exists);
            if (exists) {
                // Profile exists in database but not in cache - load it
                LOGGER.info("{}Profile exists in DB, loading into cache for {} (ID: {})", getLogPrefix(), player.getName(), id);
                loadIntoCache(player, id);
            } else {
                // Profile doesn't exist in database - create new one
                createNewProfile(player, id);
            }
        });
    }

    // Abstract methods that subclasses must implement
    protected abstract String getCollectionName();
    protected abstract Class<T> getProfileClass();
    protected abstract void initializeProfile(T profile, Player player);
    protected abstract void createNewProfile(Player player, String id);
    protected abstract String getProfileId(T profile);
    protected abstract void saveProfile(T profile);
    protected abstract String getLoadingMessagePath();
    protected abstract String getLogPrefix();
}