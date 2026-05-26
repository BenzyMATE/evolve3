package org.benski.evolv3.datacenters;

import java.util.function.Consumer;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.bukkit.entity.Player;

/**
 * Local-only cache for armor profiles. Armor is not persisted to the API; data resets on server
 * restart.
 */
public class ArmorDataCenter extends BaseDataCenter<ArmorProfile> {

  /** Returns the profile id. */
  @Override
  protected String getProfileId(ArmorProfile profile) {
    return profile.id;
  }

  /** {@inheritDoc} Retrieves the cached profile synchronously and passes it to the callback. */
  @Override
  public void get(Player player, Consumer<ArmorProfile> callback) {
    callback.accept(data.get(player.getUniqueId().toString()));
  }

  /** Creates and caches a default armor profile immediately — no API call needed. */
  @Override
  public void loadOrCreate(Player player, String id) {
    if (exists(id)) return;
    ArmorProfile profile = ArmorProfile.createDefault(player);
    profile.id = id;
    data.put(id, profile);
  }

  /**
   * Removes the armor profile from cache and resets the player's walk speed to the default. Walk
   * speed must be reset here because it may have been modified by boots multiplier.
   */
  public void unloadProfile(Player player) {
    String id = player.getUniqueId().toString();
    if (data.remove(id) != null) {
      player.setWalkSpeed(0.2f);
    }
  }

  /** No-op: armor is local-only. */
  public void saveAll() {}
}
