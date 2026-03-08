package org.benski.evolv3.core.managers;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Manages pickup animations and player preferences.
 * Handles animation registration, player preference storage, and animation playback.
 */
public class PickupAnimationManager
{
    @Getter
    private static final PickupAnimationManager instance = new PickupAnimationManager();

    private final JavaPlugin plugin;
    private final Map<String, Function<JavaPlugin, BasePickupAnimation>> animationFactories = new HashMap<>();
    private final Map<UUID, String> playerPreferences = new HashMap<>();
    private final Map<UUID, Boolean> showOthersAnimations = new HashMap<>();
    private final List<String> animationIds = new ArrayList<>();

    private static final String DEFAULT_ANIMATION = "dissolve";
    private static final double VIEW_OTHERS_RADIUS = 32.0;

    private PickupAnimationManager() {
        this.plugin = Main.getInstance();
    }

    /**
     * Initializes the pickup animation manager.
     * Registers all available pickup animations.
     */
    public void initialize() {
        registerAnimation("teleport", TeleportPickupAnimation::new);
        registerAnimation("dissolve", DissolvePickupAnimation::new);

        plugin.getLogger().info("Pickup Animation Manager initialized with " + animationIds.size() + " animations");
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
     * Gets the preferred animation ID for a player.
     *
     * @param player the player
     * @return the animation ID, or default if not set
     */
    public String getPlayerAnimation(Player player) {
        return playerPreferences.getOrDefault(player.getUniqueId(), DEFAULT_ANIMATION);
    }

    /**
     * Sets the preferred animation for a player.
     *
     * @param player the player
     * @param animationId the animation identifier
     * @return true if the animation was set successfully
     */
    public boolean setPlayerAnimation(Player player, String animationId) {
        String id = animationId.toLowerCase();
        if (!animationFactories.containsKey(id)) {
            return false;
        }
        playerPreferences.put(player.getUniqueId(), id);
        return true;
    }

    /**
     * Clears the animation preference for a player.
     *
     * @param player the player
     */
    public void clearPlayerAnimation(Player player) {
        playerPreferences.remove(player.getUniqueId());
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
     * @param player        the player receiving the items
     * @param location      the location where the block was broken
     * @param items         the items to animate
     * @param blockMaterial the material of the broken block
     * @param dropCategory  whether this is a normal, essence, or atom drop
     * @return true if the animation was played
     */
    public boolean playPickupAnimation(Player player, Location location, List<ItemStack> items,
                                       Material blockMaterial, PreferenceType.DropCategory dropCategory) {
        if (items == null || items.isEmpty() || player == null || location == null) {
            return false;
        }

        // Resolve animation type and style from the player's PickupEffect preference
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

        String animationId = effect == PreferenceType.PickupEffect.SIMPLE ? "teleport" : DEFAULT_ANIMATION;
        PreferenceType.PickupAnimation style = effect == PreferenceType.PickupEffect.SIMPLE
                ? PreferenceType.PickupAnimation.SIMPLE
                : PreferenceType.PickupAnimation.NORMAL;

        BasePickupAnimation animation = createAnimation(animationId);
        if (animation == null) {
            animation = createAnimation(DEFAULT_ANIMATION);
            if (animation == null) return false;
        }

        // Check if we can play within budget
        if (!AnimationRegistry.getInstance().canPlay(animation)) {
            giveItemsDirectly(player, items, location);
            return false;
        }

        animation.setStyle(style);
        animation.setDropCategory(dropCategory);

        // Apply per-category glow/particle preferences
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

        // Initialize the pickup animation with context
        animation.initialize(player, items, blockMaterial);

        // Build viewers list - always include the mining player
        List<Player> viewers = new ArrayList<>();
        viewers.add(player);

        // Add nearby players who want to see others' animations
        if (location.getWorld() != null) {
            for (Player nearby : location.getWorld().getPlayers()) {
                if (nearby.equals(player)) continue;
                if (!canSeeOthersAnimations(nearby)) continue;
                if (nearby.getLocation().distance(location) <= VIEW_OTHERS_RADIUS) {
                    viewers.add(nearby);
                }
            }
        }

        // Play the animation
        return AnimationRegistry.getInstance().play(animation, location, viewers);
    }

    /**
     * Fallback when animation cannot play.
     * Items are already handled by the backpack system, so this is a no-op.
     */
    private void giveItemsDirectly(Player player, List<ItemStack> items, Location location) {
        // No-op: items are handled upstream by the backpack system before the animation is played.
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
     * Gets information about an animation.
     *
     * @param id the animation identifier
     * @return array with [displayName, description], or null if not found
     */
    public String[] getAnimationInfo(String id) {
        BasePickupAnimation animation = createAnimation(id);
        if (animation == null) {
            return null;
        }
        return new String[] { animation.getDisplayName(), animation.getDescription() };
    }

    /**
     * Checks if an animation ID is valid.
     *
     * @param id the animation identifier
     * @return true if the animation exists
     */
    public boolean isValidAnimation(String id) {
        return animationFactories.containsKey(id.toLowerCase());
    }

    /**
     * Gets the next animation in the cycle for a player.
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

    /**
     * Cleans up resources when the plugin is disabled.
     */
    public void cleanup() {
        playerPreferences.clear();
        showOthersAnimations.clear();
        animationFactories.clear();
        animationIds.clear();
    }
}
