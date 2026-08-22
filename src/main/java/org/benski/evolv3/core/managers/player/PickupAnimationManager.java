package org.benski.evolv3.core.managers.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.pickup.DissolvePickupAnimation;
import org.benski.evolv3.animations.pickup.TeleportPickupAnimation;
import org.benski.evolv3.core.abstracts.BasePickupAnimation;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages pickup animations and player preferences. Handles animation registration, player
 * preference storage, and animation playback.
 */
public class PickupAnimationManager {
  @Getter private static final PickupAnimationManager instance = new PickupAnimationManager();

  private final JavaPlugin plugin;
  private final Map<String, Function<JavaPlugin, BasePickupAnimation>> animationFactories =
      new HashMap<>();
  private final Map<UUID, Boolean> showOthersAnimations = new HashMap<>();
  private final List<String> animationIds = new ArrayList<>();

  private static final String DEFAULT_ANIMATION = "dissolve";
  private static final double VIEW_OTHERS_RADIUS = 32.0;

  /** Creates a new PickupAnimationManager instance. */
  private PickupAnimationManager() {
    this.plugin = Main.getInstance();
  }

  /** Initializes the pickup animation manager. Registers all available pickup animations. */
  public void initialize() {
    registerAnimation("teleport", TeleportPickupAnimation::new);
    registerAnimation("dissolve", DissolvePickupAnimation::new);
    animationIds.add("disabled");

    plugin
        .getLogger()
        .info("Pickup Animation Manager initialized with " + animationIds.size() + " animations");
  }

  /**
   * Registers a pickup animation type.
   *
   * @param id the animation identifier
   * @param factory the factory function to create new instances
   */
  public void registerAnimation(String id, Function<JavaPlugin, BasePickupAnimation> factory) {
    animationFactories.put(id.toLowerCase(), factory);
    animationIds.add(id.toLowerCase());
  }

  /**
   * Creates a new instance of a pickup animation.
   *
   * @param id the animation identifier
   * @return the animation instance, or null if not found
   */
  public BasePickupAnimation createAnimation(String id) {
    Function<JavaPlugin, BasePickupAnimation> factory = animationFactories.get(id.toLowerCase());
    if (factory == null) {
      return null;
    }
    return factory.apply(plugin);
  }

  /**
   * Gets the preferred animation ID for a player, derived from their persisted {@link
   * PlayerPreferences#pickupEffect} field.
   *
   * @param player the player
   * @return the animation ID, or the default if the profile is unavailable
   */
  public String getPlayerAnimation(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc != null) {
      PlayerProfile profile = pdc.getLocal(player);
      if (profile != null) {
        return effectToId(profile.preferences.pickupEffect);
      }
    }
    return DEFAULT_ANIMATION;
  }

  /**
   * Sets the preferred animation for a player and persists it to their profile.
   *
   * @param player the player
   * @param animationId the animation identifier ("dissolve", "teleport", or "disabled")
   * @return true if the animation was set successfully
   */
  public boolean setPlayerAnimation(Player player, String animationId) {
    if (!isValidAnimation(animationId)) return false;
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return false;
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return false;
    profile.preferences.pickupEffect = idToEffect(animationId);
    profile.save();
    return true;
  }

  /**
   * Resets the animation preference for a player to the default.
   *
   * @param player the player
   */
  public void clearPlayerAnimation(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return;
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return;
    profile.preferences.pickupEffect = PreferenceType.PickupEffect.DEFAULT;
    profile.save();
  }

  /**
   * Checks if a player wants to see other players' animations.
   *
   * @param player the player
   * @return true if they want to see others' animations
   */
  public boolean canSeeOthersAnimations(Player player) {
    return showOthersAnimations.getOrDefault(player.getUniqueId(), false);
  }

  /**
   * Sets whether a player wants to see other players' animations.
   *
   * @param player the player
   * @param enabled true to see others' animations
   */
  public void setShowOthersAnimations(Player player, boolean enabled) {
    showOthersAnimations.put(player.getUniqueId(), enabled);
  }

  /**
   * Toggles whether a player wants to see other players' animations.
   *
   * @param player the player
   * @return the new state
   */
  public boolean toggleShowOthersAnimations(Player player) {
    boolean newState = !canSeeOthersAnimations(player);
    setShowOthersAnimations(player, newState);
    return newState;
  }

  /**
   * Plays a pickup animation for a player, applying their saved style and type preferences.
   *
   * @param player the player receiving the items
   * @param location the location where the block was broken
   * @param items the items to animate
   * @param blockMaterial the material of the broken block
   * @param dropCategory whether this is a normal, essence, or atom drop
   * @return true if the animation was played
   */
  public boolean playPickupAnimation(
      Player player,
      Location location,
      List<ItemStack> items,
      Material blockMaterial,
      PreferenceType.DropCategory dropCategory) {
    if (items == null || items.isEmpty() || player == null || location == null) {
      return false;
    }

    PreferenceType.PickupEffect effect = PreferenceType.PickupEffect.DEFAULT;
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc != null) {
      PlayerProfile profile = pdc.getLocal(player);
      if (profile != null) {
        effect = profile.preferences.pickupEffect;
      }
    }

    if (effect == PreferenceType.PickupEffect.DISABLED) {
      return false;
    }

    String animationId =
        effect == PreferenceType.PickupEffect.SIMPLE ? "teleport" : DEFAULT_ANIMATION;
    PreferenceType.PickupAnimation style =
        effect == PreferenceType.PickupEffect.SIMPLE
            ? PreferenceType.PickupAnimation.SIMPLE
            : PreferenceType.PickupAnimation.NORMAL;

    BasePickupAnimation animation = createAnimation(animationId);
    if (animation == null) {
      animation = createAnimation(DEFAULT_ANIMATION);
      if (animation == null) return false;
    }

    if (!AnimationRegistry.getInstance().canPlay(animation)) {
      giveItemsDirectly(player, items, location);
      return false;
    }

    animation.setStyle(style);
    animation.setDropCategory(dropCategory);

    if (pdc != null) {
      PlayerProfile profileForPrefs = pdc.getLocal(player);
      if (profileForPrefs != null) {
        if (dropCategory == PreferenceType.DropCategory.ESSENCE) {
          animation.setPrefGlow(profileForPrefs.preferences.essenceGlow);
          animation.setPrefParticles(profileForPrefs.preferences.essenceParticles);
        } else if (dropCategory == PreferenceType.DropCategory.ATOM) {
          animation.setPrefGlow(profileForPrefs.preferences.atomGlow);
          animation.setPrefParticles(profileForPrefs.preferences.atomParticles);
        }
      }
    }

    animation.initialize(player, items, blockMaterial);

    return AnimationRegistry.getInstance()
        .play(animation, location, collectViewers(player, location));
  }

  /**
   * Plays a pickup animation with an explicit XP amount shown in the hologram, bypassing the
   * config-based block XP lookup. Used by omnitool and other systems where the XP source is not a
   * mine block.
   *
   * @param player the player receiving the items
   * @param location the location of the event
   * @param items the items to animate
   * @param blockMaterial the material (for visual purposes only)
   * @param dropCategory the drop category
   * @param xpAmount the XP amount to display in the hologram
   * @return true if the animation was played
   */
  public boolean playPickupAnimation(
      Player player,
      Location location,
      List<ItemStack> items,
      Material blockMaterial,
      PreferenceType.DropCategory dropCategory,
      int xpAmount) {
    if (items == null || items.isEmpty() || player == null || location == null) return false;

    PreferenceType.PickupEffect effect = PreferenceType.PickupEffect.DEFAULT;
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc != null) {
      PlayerProfile profile = pdc.getLocal(player);
      if (profile != null) effect = profile.preferences.pickupEffect;
    }

    if (effect == PreferenceType.PickupEffect.DISABLED) return false;

    String animationId =
        effect == PreferenceType.PickupEffect.SIMPLE ? "teleport" : DEFAULT_ANIMATION;
    PreferenceType.PickupAnimation style =
        effect == PreferenceType.PickupEffect.SIMPLE
            ? PreferenceType.PickupAnimation.SIMPLE
            : PreferenceType.PickupAnimation.NORMAL;

    BasePickupAnimation animation = createAnimation(animationId);
    if (animation == null) {
      animation = createAnimation(DEFAULT_ANIMATION);
      if (animation == null) return false;
    }

    if (!AnimationRegistry.getInstance().canPlay(animation)) {
      return false;
    }

    animation.setStyle(style);
    animation.setDropCategory(dropCategory);
    animation.setXpOverride(xpAmount);

    animation.initialize(player, items, blockMaterial);

    return AnimationRegistry.getInstance()
        .play(animation, location, collectViewers(player, location));
  }

  /**
   * Fallback when animation cannot play. Items are already handled by the backpack system, so this
   * is a no-op.
   */
  private void giveItemsDirectly(Player player, List<ItemStack> items, Location location) {}

  /**
   * Returns the player plus every nearby online player who has opted in to seeing others' pickup
   * animations, within {@link #VIEW_OTHERS_RADIUS} blocks of {@code location}.
   */
  private List<Player> collectViewers(Player player, Location location) {
    List<Player> viewers = new ArrayList<>();
    viewers.add(player);
    if (location.getWorld() != null) {
      for (Player nearby : location.getWorld().getPlayers()) {
        if (nearby.equals(player)) continue;
        if (!canSeeOthersAnimations(nearby)) continue;
        if (nearby.getLocation().distance(location) <= VIEW_OTHERS_RADIUS) {
          viewers.add(nearby);
        }
      }
    }
    return viewers;
  }

  /**
   * Gets all registered animation IDs.
   *
   * @return unmodifiable list of animation IDs
   */
  public List<String> getAnimationIds() {
    return Collections.unmodifiableList(animationIds);
  }

  /**
   * Gets display information about an animation. For "disabled", information is sourced directly
   * from {@link PreferenceType.PickupEffect}.
   *
   * @param id the animation identifier
   * @return array with [displayName, description], or null if not found
   */
  public String[] getAnimationInfo(String id) {
    if (id.equalsIgnoreCase("disabled")) {
      PreferenceType.PickupEffect e = PreferenceType.PickupEffect.DISABLED;
      return new String[] {e.getDisplayName(), e.getDescription()};
    }
    BasePickupAnimation animation = createAnimation(id);
    if (animation == null) return null;
    return new String[] {animation.getDisplayName(), animation.getDescription()};
  }

  /**
   * Checks if an animation ID is valid.
   *
   * @param id the animation identifier
   * @return true if the animation exists
   */
  public boolean isValidAnimation(String id) {
    String lower = id.toLowerCase();
    return animationFactories.containsKey(lower) || lower.equals("disabled");
  }

  /**
   * Cycles to the next animation in the list for a player and persists the change.
   *
   * @param player the player
   * @return the next animation ID
   */
  public String cycleNextAnimation(Player player) {
    String current = getPlayerAnimation(player);
    int currentIndex = animationIds.indexOf(current);
    int nextIndex = (currentIndex + 1) % animationIds.size();
    String next = animationIds.get(nextIndex);
    setPlayerAnimation(player, next);
    return next;
  }

  /** Maps a {@link PreferenceType.PickupEffect} to its animation ID string. */
  private static String effectToId(PreferenceType.PickupEffect effect) {
    return switch (effect) {
      case SIMPLE -> "teleport";
      case DISABLED -> "disabled";
      default -> "dissolve";
    };
  }

  /** Maps an animation ID string to the corresponding {@link PreferenceType.PickupEffect}. */
  private static PreferenceType.PickupEffect idToEffect(String id) {
    return switch (id.toLowerCase()) {
      case "teleport" -> PreferenceType.PickupEffect.SIMPLE;
      case "disabled" -> PreferenceType.PickupEffect.DISABLED;
      default -> PreferenceType.PickupEffect.DEFAULT;
    };
  }

  /** Cleans up resources when the plugin is disabled. */
  public void cleanup() {
    showOthersAnimations.clear();
    animationFactories.clear();
    animationIds.clear();
  }
}
