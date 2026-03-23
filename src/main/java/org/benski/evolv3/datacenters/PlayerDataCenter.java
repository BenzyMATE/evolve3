package org.benski.evolv3.datacenters;

import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Data center for managing player profiles.
 * Handles loading, caching, and creating player profiles from MongoDB.
 */
public class PlayerDataCenter extends BaseDataCenter<PlayerProfile>
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected String getCollectionName()
    {
        return "Players";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<PlayerProfile> getProfileClass()
    {
        return PlayerProfile.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initializeProfile(PlayerProfile profile, Player player)
    {
        profile.initialize(player);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getProfileId(PlayerProfile profile)
    {
        return profile.id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLogPrefix()
    {
        return "Player";
    }

    /**
     * Gets the player's profile asynchronously.
     * If the profile is cached, it will be returned immediately via callback.
     *
     * @param player   The player to get the profile for
     * @param callback The callback to receive the profile
     */
    @Override
    public void get(Player player, Consumer<PlayerProfile> callback)
    {
        callback.accept(data.get(player.getUniqueId().toString()));
    }

    /**
     * Gets the player's profile synchronously from cache.
     *
     * @param player The player to get the profile for
     * @return The player's profile, or null if not cached
     */
    @Override
    public PlayerProfile getLocal(Player player)
    {
        return data.get(player.getUniqueId().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createNewProfile(Player player, String id)
    {
        PlayerProfile profile = PlayerProfile.createDefault(player);
        profile.id = id;

        data.put(id, profile);

        profile.save(result -> {
            if (result) {
                player.sendMessage(formatMessage(player,
                        ConfigRegistry.getInstance().get(ConfigNames.MESSAGES)
                                .getString(ConfigPaths.Messages.Player.PROFILE_CREATED,
                                        "&aYour profile has been created!")));
            }
        });
    }

    /**
     * Removes a player's profile from cache.
     * Should be called when a player leaves the server.
     *
     * @param player The player whose profile to unload
     */
    public void unloadProfile(Player player)
    {
        String playerId = player.getUniqueId().toString();
        PlayerProfile profile = data.get(playerId);

        if (profile != null) {
            profile.save(result -> {
                data.remove(playerId);
            });
        }
    }

    /**
     * Saves all cached profiles to the database.
     * Should be called during plugin disable.
     */
    public void saveAll()
    {
        for (PlayerProfile profile : data.values()) {
            profile.save();
        }
    }
}
