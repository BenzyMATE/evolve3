package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.EnchantSelectionAnimation;
import org.benski.evolv3.animations.ExplosionAnimation;
import org.benski.evolv3.animations.pickup.ArcPickupAnimation;
import org.benski.evolv3.animations.pickup.HelixPickupAnimation;
import org.benski.evolv3.animations.pickup.MagneticPickupAnimation;
import org.benski.evolv3.animations.pickup.TeleportPickupAnimation;
import org.benski.evolv3.animations.pickup.VortexPickupAnimation;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.AnimationRegistryManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * A registry to register and manage animations.
 * Animations are tick-based effects that can include particles, sounds, and entities.
 *
 * <p>To add a new animation:</p>
 * <ol>
 *   <li>Create a class that extends {@link BaseAnimation}</li>
 *   <li>Add the class to the {@code animations} list in this registry</li>
 * </ol>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Play an animation by identifier
 * AnimationRegistry.getInstance().play("explosion", location);
 *
 * // Play for specific players
 * AnimationRegistry.getInstance().play("explosion", location, viewers);
 * }</pre>
 */
public class AnimationRegistry implements Registry<BaseAnimation>
{
    @Getter
    private static final AnimationRegistry instance = new AnimationRegistry();
    private final AnimationRegistryManager registryManager = new AnimationRegistryManager(Main.getInstance());

    /**
     * Register animations here.
     * Add your animation classes to this list.
     */
    private final List<Class<? extends BaseAnimation>> animations = List.of(
            ExplosionAnimation.class,
            EnchantSelectionAnimation.class,
            VortexPickupAnimation.class,
            MagneticPickupAnimation.class,
            ArcPickupAnimation.class,
            HelixPickupAnimation.class,
            TeleportPickupAnimation.class
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        animations.forEach(registryManager::registerAnimation);
        registryManager.initialize();
    }

    /**
     * Gets an animation class by identifier.
     * Note: This returns the class, not an instance.
     * Use {@link #play(String, Location)} to create and play an animation.
     *
     * @param id the animation identifier (e.g., "explosion")
     * @return null (animations are created on-demand, not stored)
     */
    @Override
    public BaseAnimation get(String id) {
        // Animations are created on-demand, not stored
        // Use play() methods instead
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        registryManager.cleanup();
    }

    /**
     * Creates and plays an animation by identifier.
     *
     * @param identifier the animation identifier (e.g., "explosion")
     * @param location the location to play at
     * @return the animation instance, or null if failed
     */
    public BaseAnimation play(String identifier, Location location) {
        return registryManager.play(identifier, location);
    }

    /**
     * Creates and plays an animation by identifier for specific players.
     *
     * @param identifier the animation identifier
     * @param location the location to play at
     * @param viewers the players who should see the animation
     * @return the animation instance, or null if failed
     */
    public BaseAnimation play(String identifier, Location location, Collection<Player> viewers) {
        return registryManager.play(identifier, location, viewers);
    }

    /**
     * Plays an existing animation instance.
     *
     * @param animation the animation to play
     * @param location the location to play at
     * @return true if played successfully
     */
    public boolean play(BaseAnimation animation, Location location) {
        return registryManager.play(animation, location);
    }

    /**
     * Plays an existing animation instance for specific players.
     *
     * @param animation the animation to play
     * @param location the location to play at
     * @param viewers the players who should see the animation
     * @return true if played successfully
     */
    public boolean play(BaseAnimation animation, Location location, Collection<Player> viewers) {
        return registryManager.play(animation, location, viewers);
    }

    /**
     * Checks if an animation can be played within budget constraints.
     *
     * @param animation the animation to check
     * @return true if within budget
     */
    public boolean canPlay(BaseAnimation animation) {
        return registryManager.canPlay(animation);
    }

    /**
     * Stops all active animations.
     */
    public void stopAll() {
        registryManager.stopAll();
    }

    /**
     * Gets the number of active animations.
     *
     * @return the count
     */
    public int getActiveAnimationCount() {
        return registryManager.getActiveAnimationCount();
    }

    /**
     * Gets the registry manager for advanced operations.
     *
     * @return the registry manager
     */
    public AnimationRegistryManager getManager() {
        return registryManager;
    }
}
