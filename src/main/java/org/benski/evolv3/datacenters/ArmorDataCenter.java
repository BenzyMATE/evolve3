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
 * Local-only cache for armor profiles. Armor is not persisted to the API; data resets on server
 * restart.
 */
public class ArmorDataCenter extends BaseDataCenter<ArmorProfile> {

  @Override
  protected String getProfileId(ArmorProfile profile) {
    return profile.id;
  }

  @Override
  public void get(Player player, Consumer<ArmorProfile> callback) {
    callback.accept(data.get(player.getUniqueId().toString()));
  }

  @Override
  public ArmorProfile getLocal(Player player) {
    return data.get(player.getUniqueId().toString());
  }

  /** Creates and caches a default armor profile immediately — no API call needed. */
  @Override
  public void loadOrCreate(Player player, String id) {
    if (exists(id)) return;
    ArmorProfile profile = ArmorProfile.createDefault(player);
    profile.id = id;
    data.put(id, profile);
    player.sendMessage(
        formatMessage(
            player,
            ConfigRegistry.getInstance()
                .get(ConfigNames.MESSAGES)
                .getString(
                    ConfigPaths.Messages.Armor.PROFILE_CREATED, "&aYour armor has been forged!")));
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
