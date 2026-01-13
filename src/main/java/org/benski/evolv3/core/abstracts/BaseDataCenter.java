package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.TaskManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

public abstract class BaseDataCenter<T extends MongoCollection> implements DataCenter<T>
{
    protected final Map<String, T> data = new HashMap<>();
    protected final MongoManager mongoManager = MongoManager.getInstance();
    protected final StorageRegistryManager storageManager = StorageRegistryManager.getInstance();
    protected final FileConfiguration messagesConfig = storageManager.getConfig(ConfigNames.MESSAGES);
    private final Logger logger = Main.getInstance().getLogger();

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
        data.put(getProfileId(profile), profile);
    }

    @Override
    public void existsInDatabase(String id, Consumer<Boolean> callback) {
        logger.info(getLogPrefix() + "Checking if profile exists in database for ID: " + id);
        this.mongoManager.getByIdAsync(getCollectionName(), "id", id, getProfileClass())
                .thenAcceptAsync(profile -> {
                    boolean exists = profile != null;
                    logger.info(getLogPrefix() + "Database check result for ID " + id + ": exists = " + exists);
                    callback.accept(exists);
                }, TaskManager.getInstance()::runSync)
                .exceptionally(throwable -> {
                    logger.severe("Failed to check existence for ID " + id + ": " + throwable.getMessage());
                    callback.accept(false);
                    return null;
                });
    }

    @Override
    public void loadIntoCache(Player player, String profileId) {
        logger.info(getLogPrefix() + "loadIntoCache starting for ID: " + profileId);
        this.mongoManager.getByIdAsync(getCollectionName(), "id", profileId, getProfileClass())
                .thenAcceptAsync(profile -> {
                    logger.info(getLogPrefix() + "Database query completed for ID: " + profileId + ", result: " + (profile != null ? "found" : "null"));

                    if (profile == null) {
                        logger.warning(getLogPrefix() + "Profile was null for ID " + profileId + " despite existsInDatabase returning true!");
                        return;
                    }

                    // Initialize the profile with dependencies
                    initializeProfile(profile, player);

                    // Add to cache
                    data.put(profileId, profile);
                    logger.info(getLogPrefix() + "Successfully added profile to cache for ID: " + profileId);

                    // Notify player
                    player.sendMessage(formatMessage(player,
                            messagesConfig.getString(getLoadingMessagePath(),
                                    "Loading your profile...")));

                }, TaskManager.getInstance()::runSync)
                .exceptionally(throwable -> {
                    logger.severe("Failed to load profile " + profileId + ": " + throwable.getMessage());
                    return null;
                });
    }

    @Override
    public void loadOrCreate(Player player, String id) {
        if (exists(id)) {
            return; // Profile already loaded in cache
        }

        existsInDatabase(id, exists -> {
            logger.info(getLogPrefix() + "Database existence callback called for ID " + id + ": exists = " + exists);
            if (exists) {
                // Profile exists in database but not in cache - load it
                logger.info(getLogPrefix() + "Profile exists in DB, loading into cache for " + player.getName() + " (ID: " + id + ")");
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
    protected abstract String getLoadingMessagePath();
    protected abstract String getLogPrefix();
}