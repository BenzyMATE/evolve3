package org.benski.evolv3.datacenters;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.Main;
import org.benski.evolv3.api.EvolveApiClient;
import org.benski.evolv3.api.ProfileMapper;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.OmnitoolManager;
import org.benski.evolv3.core.managers.TaskManager;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Data center for player profiles. Backed by the Evolve HTTP API. A single {@code GET /api/player}
 * call on join loads the full aggregate (player, pickaxes, preferences) into the local cache. All
 * pickaxe access goes through this data center — there is no separate pickaxe cache.
 */
@ExtensionMethod(ItemStackExtensions.class)
public class PlayerDataCenter extends BaseDataCenter<PlayerProfile> {

  private final Logger logger = Main.getInstance().getLogger();

  @Override
  protected String getProfileId(PlayerProfile profile) {
    return profile.id;
  }

  @Override
  public void get(Player player, java.util.function.Consumer<PlayerProfile> callback) {
    callback.accept(data.get(player.getUniqueId().toString()));
  }

  @Override
  public PlayerProfile getLocal(Player player) {
    return data.get(player.getUniqueId().toString());
  }

  /** Looks up a cached profile directly by its string UUID for cross-data-center delegation. */
  public PlayerProfile getLocalById(String id) {
    return data.get(id);
  }

  /**
   * Returns the pickaxe the player currently holds in their main hand, falling back to any stored
   * omnitool pickaxe. Returns {@code null} if no matching pickaxe profile is found.
   */
  @Nullable
  public Pickaxe getPickaxeForPlayer(Player player) {
    PlayerProfile profile = getLocal(player);
    if (profile == null) return null;

    String itemId = player.getInventory().getItemInMainHand().getData("id");
    if (itemId == null || itemId.isEmpty()) {
      ItemStack stored = OmnitoolManager.getInstance().getStoredPickaxe(player);
      if (stored != null) itemId = stored.getData("id");
    }
    if (itemId == null || itemId.isEmpty()) return null;
    return profile.getPickaxe(itemId);
  }

  /**
   * Searches all cached player profiles for a pickaxe with the given key. Returns {@code null} if
   * no online player owns this pickaxe.
   */
  @Nullable
  public Pickaxe getPickaxeByItemId(String pickaxeId) {
    if (pickaxeId == null || pickaxeId.isEmpty()) return null;
    for (PlayerProfile profile : data.values()) {
      Pickaxe p = profile.getPickaxe(pickaxeId);
      if (p != null) return p;
    }
    return null;
  }

  /**
   * Returns the {@link PlayerProfile} that owns the given pickaxe key, or {@code null} if no online
   * player owns it.
   */
  @Nullable
  public PlayerProfile getPlayerOwningPickaxe(String pickaxeId) {
    if (pickaxeId == null || pickaxeId.isEmpty()) return null;
    for (PlayerProfile profile : data.values()) {
      if (profile.getPickaxe(pickaxeId) != null) return profile;
    }
    return null;
  }

  /**
   * Returns a flat collection of all pickaxes across every currently cached player profile. The
   * returned list is a snapshot and is not backed by the cache.
   */
  public Collection<Pickaxe> getAllPickaxes() {
    List<Pickaxe> result = new ArrayList<>();
    for (PlayerProfile profile : data.values()) {
      result.addAll(profile.pickaxes);
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Ensures the pickaxe identified by {@code id} exists in the owning player's profile. If the
   * player's profile does not contain this pickaxe, a default profile is created, persisted, and
   * the player is notified. This is a no-op if the pickaxe is already present.
   */
  public void ensurePickaxe(Player player, String id) {
    PlayerProfile profile = getLocal(player);
    if (profile == null) return;
    if (profile.getPickaxe(id) != null) return;

    Pickaxe pickaxe = Pickaxe.createDefault(player, id);
    profile.upsertPickaxe(pickaxe);
    profile.save(
        result -> {
          if (result) {
            player.sendMessage(
                formatMessage(
                    player,
                    ConfigRegistry.getInstance()
                        .get(ConfigNames.MESSAGES)
                        .getString(
                            ConfigPaths.Messages.Pickaxe.PROFILE_CREATED,
                            "&aPickaxe profile created!")));
          }
        });
  }

  @Override
  public void loadOrCreate(Player player, String id) {
    if (exists(id)) return;
    if (!tryMarkInFlight(id)) return;

    EvolveApiClient.getInstance()
        .getByIdAsync(player.getUniqueId())
        .thenAcceptAsync(
            dto -> {
              try {
                PlayerProfile profile;
                if (dto != null) {
                  profile = ProfileMapper.fromDto(dto, player);
                } else {
                  profile = PlayerProfile.createDefault(player);
                  final PlayerProfile newProfile = profile;
                  EvolveApiClient.getInstance()
                      .createAsync(ProfileMapper.toDto(newProfile))
                      .thenAcceptAsync(
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
                            } else {
                              logger.severe("Failed to create API profile for " + player.getName());
                            }
                          },
                          TaskManager.getInstance()::runSync);
                }
                data.put(id, profile);
              } finally {
                clearInFlight(id);
              }
            },
            EvolveApiClient.getInstance().getExecutor());
  }

  /**
   * Removes the profile from cache immediately, then persists to the API asynchronously. Removing
   * first prevents a reconnecting player's fresh load from being overwritten by a late callback.
   */
  public void unloadProfile(Player player) {
    String id = player.getUniqueId().toString();
    PlayerProfile profile = data.remove(id);
    if (profile != null) {
      EvolveApiClient.getInstance().updateAsync(ProfileMapper.toDto(profile));
    }
  }

  /**
   * Persists all cached profiles and blocks until complete or the 10-second timeout elapses. Called
   * on server shutdown to prevent data loss.
   */
  public void saveAll() {
    List<CompletableFuture<Boolean>> futures = new ArrayList<>();
    for (PlayerProfile profile : data.values()) {
      futures.add(EvolveApiClient.getInstance().updateAsync(ProfileMapper.toDto(profile)));
    }
    if (futures.isEmpty()) return;
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
          .get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      logger.severe("Timed out waiting for player profile saves on shutdown: " + e.getMessage());
    }
  }
}
