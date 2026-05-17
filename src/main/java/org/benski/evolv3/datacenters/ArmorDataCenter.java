package org.benski.evolv3.datacenters;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.function.Consumer;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.entity.Player;

/**
 * Data center for managing armor profiles. Handles loading, caching, and creating armor profiles
 * from MongoDB.
 */
public class ArmorDataCenter extends BaseDataCenter<ArmorProfile> {
  /** {@inheritDoc} */
  @Override
  protected String getCollectionName() {
    return "Armor";
  }

  /** {@inheritDoc} */
  @Override
  protected Class<ArmorProfile> getProfileClass() {
    return ArmorProfile.class;
  }

  /** {@inheritDoc} */
  @Override
  protected void initializeProfile(ArmorProfile profile, Player player) {
    profile.initialize(player);
  }

  /** {@inheritDoc} */
  @Override
  protected String getProfileId(ArmorProfile profile) {
    return profile.id;
  }

  /** {@inheritDoc} */
  @Override
  protected String getLogPrefix() {
    return "Armor";
  }

  /**
   * Gets the player's armor profile asynchronously. If the profile is cached, it will be returned
   * immediately via callback.
   *
   * @param player The player to get the profile for
   * @param callback The callback to receive the profile
   */
  @Override
  public void get(Player player, Consumer<ArmorProfile> callback) {
    callback.accept(data.get(player.getUniqueId().toString()));
  }

  /**
   * Gets the player's armor profile synchronously from cache.
   *
   * @param player The player to get the profile for
   * @return The player's armor profile, or null if not cached
   */
  @Override
  public ArmorProfile getLocal(Player player) {
    return data.get(player.getUniqueId().toString());
  }

  /** {@inheritDoc} */
  @Override
  protected void createNewProfile(Player player, String id) {
    ArmorProfile profile = ArmorProfile.createDefault(player);
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
                            ConfigPaths.Messages.Armor.PROFILE_CREATED,
                            "&aYour armor has been forged!")));
          }
        });
  }

  /**
   * Removes a player's armor profile from cache. Should be called when a player leaves the server.
   *
   * @param player The player whose profile to unload
   */
  public void unloadProfile(Player player) {
    String playerId = player.getUniqueId().toString();
    ArmorProfile profile = data.get(playerId);

    if (profile != null) {
      player.setWalkSpeed(0.2f);

      profile.save(
          result -> {
            data.remove(playerId);
          });
    }
  }

  /** Saves all cached armor profiles to the database. Should be called during plugin disable. */
  public void saveAll() {
    for (ArmorProfile profile : data.values()) {
      profile.save();
    }
  }
}
