package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.TaskManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public abstract class BaseDataCenter<T extends MongoCollection> implements DataCenter<T>
{
    protected final Map<String, T> data = new HashMap<>();
    protected final MongoManager mongoManager = MongoManager.getInstance();
    protected final StorageRegistryManager storageManager = StorageRegistryManager.getInstance();
    private final Logger logger = Main.getInstance().getLogger();

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, T> getAll() {
        return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(String id) {
        return data.containsKey(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateLocal(T profile, boolean save) {
        data.put(getProfileId(profile), profile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void existsInDatabase(String id, Consumer<Boolean> callback) {
        this.mongoManager.getByIdAsync(getCollectionName(), "id", id, getProfileClass())
                .thenAcceptAsync(profile -> callback.accept(profile != null), TaskManager.getInstance()::runSync)
                .exceptionally(throwable -> {
                    logger.severe("Failed to check existence for ID " + id + ": " + throwable.getMessage());
                    callback.accept(false);
                    return null;
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadIntoCache(Player player, String profileId) {
        this.mongoManager.getByIdAsync(getCollectionName(), "id", profileId, getProfileClass())
                .thenAcceptAsync(profile -> {
                    if (profile == null) {
                        logger.warning(getLogPrefix() + "Profile was null for ID " + profileId + " despite existsInDatabase returning true!");
                        return;
                    }

                    initializeProfile(profile, player);
                    data.put(profileId, profile);

                }, TaskManager.getInstance()::runSync)
                .exceptionally(throwable -> {
                    logger.severe("Failed to load profile " + profileId + ": " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadOrCreate(Player player, String id) {
        if (exists(id)) {
            return;
        }

        existsInDatabase(id, exists -> {
            if (exists) {
                loadIntoCache(player, id);
            } else {
                createNewProfile(player, id);
            }
        });
    }

    protected abstract String getCollectionName();
    protected abstract Class<T> getProfileClass();
    protected abstract void initializeProfile(T profile, Player player);
    protected abstract void createNewProfile(Player player, String id);
    protected abstract String getProfileId(T profile);
    protected abstract String getLogPrefix();
}
