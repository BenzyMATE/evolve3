package org.benski.evolv3.datacenters;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.entity.Player;

/**
 * Data center for managing player profiles. Handles loading, caching, and creating player profiles
 * from MongoDB.
 */
public class PlayerDataCenter extends BaseDataCenter<PlayerProfile> {
  /** {@inheritDoc} */
  @Override
  protected String getCollectionName() {
    return "Players";
  }

  /** {@inheritDoc} */
  @Override
  protected Class<PlayerProfile> getProfileClass() {
    return PlayerProfile.class;
  }

  /** {@inheritDoc} */
  @Override
  protected void initializeProfile(PlayerProfile profile, Player player) {
    profile.initialize(player);
  }

  /** {@inheritDoc} */
  @Override
  protected String getProfileId(PlayerProfile profile) {
    return profile.id;
  }

  /** {@inheritDoc} */
  @Override
  protected String getLogPrefix() {
    return "Player";
  }

  /**
   * Gets the player's profile asynchronously. If the profile is cached, it will be returned
   * immediately via callback.
   *
   * @param player The player to get the profile for
   * @param callback The callback to receive the profile
   */
  @Override
  public void get(Player player, Consumer<PlayerProfile> callback) {
    callback.accept(data.get(player.getUniqueId().toString()));
  }

  /**
   * Gets the player's profile synchronously from cache.
   *
   * @param player The player to get the profile for
   * @return The player's profile, or null if not cached
   */
  @Override
  public PlayerProfile getLocal(Player player) {
    return data.get(player.getUniqueId().toString());
  }

  /** {@inheritDoc} */
  @Override
  protected void createNewProfile(Player player, String id) {
    PlayerProfile profile = PlayerProfile.createDefault(player);
    profile.id = id;

    data.put(id, profile);

    profile.save(
        result -> {
          if (result) {
            player.sendMessage(
                formatMessage(
                    player,
                    ConfigRegistry.getInstance()
                        .get(ConfigNames.MESSAGES)
                        .getString(
                            ConfigPaths.Messages.Player.PROFILE_CREATED,
                            "&aYour profile has been created!")));
          }
        });
  }

  /**
   * Removes a player's profile from cache immediately, then saves asynchronously. Removing first
   * prevents a reconnecting player from seeing a stale eviction delete their freshly-loaded profile
   * if the async callback fires late.
   *
   * @param player The player whose profile to unload
   */
  public void unloadProfile(Player player) {
    String playerId = player.getUniqueId().toString();
    PlayerProfile profile = data.remove(playerId);
    if (profile != null) {
      profile.save();
    }
  }

  /**
   * Saves all cached profiles and blocks until every save completes (or times out). Should be
   * called during plugin disable to prevent data loss on shutdown. Waits up to 10 seconds for all
   * saves to finish before returning.
   */
  public void saveAll() {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (PlayerProfile profile : data.values()) {
      futures.add(mongoManager.upsertAsync(profile));
    }
    if (futures.isEmpty()) return;
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
          .get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      Main.getInstance()
          .getLogger()
          .severe("Timed out waiting for player profile saves on shutdown: " + e.getMessage());
    }
  }
}
