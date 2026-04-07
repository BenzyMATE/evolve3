package org.benski.evolv3.core.abstracts;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * Base class for all animations in the plugin.
 * Provides common functionality for animation lifecycle management.
 *
 * <p>Animations are tick-based and managed by the AnimationRegistryManager
 * which handles performance budgets and cleanup.</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * public class ExplosionAnimation extends BaseAnimation {
 *     public ExplosionAnimation(JavaPlugin plugin) {
 *         super(plugin, 30); // 30 tick duration (1.5 seconds)
 *     }
 *
 *     @Override
 *     protected void onStart() {
 *         // Spawn entities, play initial effects
 *     }
 *
 *     @Override
 *     protected void onTick(double progress) {
 *         // Update positions, spawn particles
 *     }
 *
 *     @Override
 *     protected void onComplete() {
 *         // Cleanup entities, play final effects
 *     }
 * }
 * }</pre>
 */
public abstract class BaseAnimation
{
    /**
     * -- GETTER --
     *  Gets the plugin instance.
     */
    @Getter
    protected final JavaPlugin plugin;
    /**
     * -- GETTER --
     *  Gets the animation duration in ticks.
     */
    @Getter
    protected final int duration;

    private Location origin;
    /**
     * -- GETTER --
     *  Gets the viewers for this animation.
     */
    @Getter
    private Collection<Player> viewers;
    /**
     * -- GETTER --
     *  Gets the current tick.
     */
    @Getter
    private int currentTick;
    /**
     * -- GETTER --
     *  Checks if the animation is currently playing.
     */
    @Getter
    private boolean playing;
    /**
     * -- GETTER --
     *  Checks if the animation has completed.
     */
    @Getter
    private boolean complete;

    /**
     * Constructor for BaseAnimation.
     *
     * @param plugin the plugin instance
     * @param duration the animation duration in ticks (20 ticks = 1 second)
     */
    public BaseAnimation(JavaPlugin plugin, int duration) {
        this.plugin = plugin;
        this.duration = Math.max(1, duration);
        this.currentTick = 0;
        this.playing = false;
        this.complete = false;
    }

    /**
     * Plays the animation at the specified location for all players.
     *
     * @param location the origin location
     */
    public void play(Location location) {
        play(location, null);
    }

    /**
     * Plays the animation at the specified location for specific players.
     *
     * @param location the origin location
     * @param viewers the players who should see the animation (null for all)
     */
    public void play(Location location, Collection<Player> viewers) {
        if (playing) return;

        this.origin = location.clone();
        this.viewers = viewers;
        this.currentTick = 0;
        this.playing = true;
        this.complete = false;

        onStart();
    }

    /**
     * Stops the animation immediately.
     */
    public void stop() {
        if (!playing) return;

        playing = false;
        complete = true;
        onComplete();
    }

    /**
     * Called by the AnimationRegistryManager each tick.
     * Do not call this method directly.
     */
    public void tick() {
        if (!playing || complete) return;

        // Calculate progress (0.0 to 1.0)
        double progress = (double) currentTick / duration;

        // Update animation
        onTick(progress);

        // Increment tick
        currentTick++;

        // Check for completion
        if (currentTick >= duration) {
            playing = false;
            complete = true;
            onComplete();
        }
    }

    /**
     * Called when the animation starts.
     * Override to spawn entities, play initial sounds, etc.
     */
    protected abstract void onStart();

    /**
     * Called each tick while the animation is playing.
     *
     * @param progress the animation progress from 0.0 (start) to 1.0 (end)
     */
    protected abstract void onTick(double progress);

    /**
     * Called when the animation completes or is stopped.
     * Override to cleanup entities, play final effects, etc.
     */
    protected abstract void onComplete();

    /**
     * Gets the estimated resource cost of this animation.
     * Used by the manager for performance budgeting.
     * Override to return accurate counts for complex animations.
     *
     * @return the resource cost (default: 1)
     */
    public int getResourceCost() {
        return 1;
    }

    // Getters

    /**
     * Gets the origin location.
     *
     * @return the origin location, or null if not playing
     */
    public Location getOrigin() {
        return origin != null ? origin.clone() : null;
    }

    /**
     * Gets the animation progress from 0.0 to 1.0.
     *
     * @return the progress
     */
    public double getProgress() {
        return duration > 0 ? (double) currentTick / duration : 1.0;
    }

}
