package org.benski.evolv3.datacenters;

import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Data center for managing armor profiles.
 * Handles loading, caching, and creating armor profiles from MongoDB.
 */
public class ArmorDataCenter extends BaseDataCenter<ArmorProfile>
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected String getCollectionName()
    {
        return "Armor";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<ArmorProfile> getProfileClass()
    {
        return ArmorProfile.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initializeProfile(ArmorProfile profile, Player player)
    {
        profile.initialize(player);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getProfileId(ArmorProfile profile)
    {
        return profile.id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLoadingMessagePath()
    {
        return ConfigPaths.Messages.Armor.LOADING_PROFILE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLogPrefix()
    {
        return "Armor";
    }

    /**
     * Gets the player's armor profile asynchronously.
     * If the profile is cached, it will be returned immediately via callback.
     *
     * @param player   The player to get the profile for
     * @param callback The callback to receive the profile
     */
    @Override
    public void get(Player player, Consumer<ArmorProfile> callback)
    {
        String playerId = player.getUniqueId().toString();

        if (data.containsKey(playerId)) {
            ArmorProfile profile = data.get(playerId);
            profile.initialize(player);
            callback.accept(profile);
        }
    }

    /**
     * Gets the player's armor profile synchronously from cache.
     *
     * @param player The player to get the profile for
     * @return The player's armor profile, or null if not cached
     */
    @Override
    public ArmorProfile getLocal(Player player)
    {
        String playerId = player.getUniqueId().toString();
        ArmorProfile profile = data.get(playerId);

        if (profile != null) {
            profile.initialize(player);
        }

        return profile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createNewProfile(Player player, String id)
    {
        ArmorProfile profile = ArmorProfile.createDefault(player);
        profile.id = id;

        // Add to cache immediately
        data.put(id, profile);

        // Save to database
        profile.save(result -> {
            if (result) {
                player.sendMessage(formatMessage(player,
                        messagesConfig.getString(ConfigPaths.Messages.Armor.PROFILE_CREATED,
                                "&aYour armor has been forged!")));
            }
        });
    }

    /**
     * Removes a player's armor profile from cache.
     * Should be called when a player leaves the server.
     *
     * @param player The player whose profile to unload
     */
    public void unloadProfile(Player player)
    {
        String playerId = player.getUniqueId().toString();
        ArmorProfile profile = data.get(playerId);

        if (profile != null) {
            // Reset player speed before saving
            player.setWalkSpeed(0.2f);

            // Save before unloading
            profile.save(result -> {
                data.remove(playerId);
            });
        }
    }

    /**
     * Saves all cached armor profiles to the database.
     * Should be called during plugin disable.
     */
    public void saveAll()
    {
        for (ArmorProfile profile : data.values()) {
            profile.save();
        }
    }
}
