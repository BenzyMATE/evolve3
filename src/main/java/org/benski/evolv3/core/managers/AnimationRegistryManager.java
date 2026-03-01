package org.benski.evolv3.core.managers;

import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Core manager that handles animation registration, lifecycle, and performance.
 * Provides tick scheduling and budget enforcement for animations.
 */
public class AnimationRegistryManager
{
    private final JavaPlugin plugin;
    private final Map<String, Class<? extends BaseAnimation>> animationClasses = new HashMap<>();
    private final List<BaseAnimation> activeAnimations = new ArrayList<>();

    // Performance settings
    private int maxConcurrentAnimations = 50;
    private int maxResourceCost = 200;
    private int currentResourceCost = 0;

    // Tick scheduler
    private BukkitTask tickTask;

    public AnimationRegistryManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an animation class.
     *
     * @param animationClass the animation class to register
     */
    public void registerAnimation(Class<? extends BaseAnimation> animationClass) {
        String identifier = animationClass.getSimpleName()
                .replace("Animation", "")
                .toLowerCase();
        animationClasses.put(identifier, animationClass);
        plugin.getLogger().info("Registered animation: " + animationClass.getSimpleName());
    }

    /**
     * Initializes the animation system and starts the tick scheduler.
     */
    public void initialize() {
        startTicking();
        plugin.getLogger().info("AnimationRegistryManager initialized with " +
                animationClasses.size() + " animation types.");
    }

    /**
     * Starts the tick scheduler.
     */
    private void startTicking() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Called every tick to update all active animations.
     */
    private void tick() {
        Iterator<BaseAnimation> iterator = activeAnimations.iterator();

        while (iterator.hasNext()) {
            BaseAnimation animation = iterator.next();

            if (animation.isComplete()) {
                // Remove completed animations and free resources
                currentResourceCost -= animation.getResourceCost();
                iterator.remove();
            } else if (animation.isPlaying()) {
                // Tick active animations
                animation.tick();
            }
        }
    }

    /**
     * Creates and plays an animation by identifier.
     *
     * @param identifier the animation identifier (e.g., "explosion")
     * @param location the location to play at
     * @return the animation instance, or null if failed
     */
    public BaseAnimation play(String identifier, Location location) {
        return play(identifier, location, null);
    }

    /**
     * Creates and plays an animation by identifier for specific players.
     *
     * @param identifier the animation identifier
     * @param location the location to play at
     * @param viewers the players who should see the animation (null for all)
     * @return the animation instance, or null if failed
     */
    public BaseAnimation play(String identifier, Location location, Collection<Player> viewers) {
        Class<? extends BaseAnimation> animationClass = animationClasses.get(identifier.toLowerCase());
        if (animationClass == null) {
            plugin.getLogger().warning("Animation not found: " + identifier);
            return null;
        }

        try {
            // Create instance
            Constructor<? extends BaseAnimation> constructor = animationClass.getConstructor(JavaPlugin.class);
            BaseAnimation animation = constructor.newInstance(plugin);

            // Check budget
            if (!canPlay(animation)) {
                plugin.getLogger().warning("Animation budget exceeded, skipping: " + identifier);
                return null;
            }

            // Play and track
            animation.play(location, viewers);
            activeAnimations.add(animation);
            currentResourceCost += animation.getResourceCost();

            return animation;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create animation " + identifier + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Plays an existing animation instance.
     *
     * @param animation the animation to play
     * @param location the location to play at
     * @return true if played successfully
     */
    public boolean play(BaseAnimation animation, Location location) {
        return play(animation, location, null);
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
        if (!canPlay(animation)) {
            plugin.getLogger().warning("Animation budget exceeded, skipping animation.");
            return false;
        }

        animation.play(location, viewers);
        activeAnimations.add(animation);
        currentResourceCost += animation.getResourceCost();
        return true;
    }

    /**
     * Checks if an animation can be played within budget constraints.
     *
     * @param animation the animation to check
     * @return true if within budget
     */
    public boolean canPlay(BaseAnimation animation) {
        if (activeAnimations.size() >= maxConcurrentAnimations) {
            return false;
        }
        if (currentResourceCost + animation.getResourceCost() > maxResourceCost) {
            return false;
        }
        return true;
    }

    /**
     * Gets an animation class by identifier.
     *
     * @param identifier the animation identifier
     * @return the animation class, or null if not found
     */
    public Class<? extends BaseAnimation> getAnimationClass(String identifier) {
        return animationClasses.get(identifier.toLowerCase());
    }

    /**
     * Stops all active animations.
     */
    public void stopAll() {
        for (BaseAnimation animation : new ArrayList<>(activeAnimations)) {
            animation.stop();
        }
        activeAnimations.clear();
        currentResourceCost = 0;
    }

    /**
     * Cleanup the animation system.
     */
    public void cleanup() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        stopAll();
        animationClasses.clear();
        plugin.getLogger().info("AnimationRegistryManager cleaned up.");
    }

    // Getters and setters for performance settings

    /**
     * Gets the number of active animations.
     *
     * @return the count
     */
    public int getActiveAnimationCount() {
        return activeAnimations.size();
    }

    /**
     * Gets the current resource cost.
     *
     * @return the current cost
     */
    public int getCurrentResourceCost() {
        return currentResourceCost;
    }

    /**
     * Gets the maximum concurrent animations allowed.
     *
     * @return the max
     */
    public int getMaxConcurrentAnimations() {
        return maxConcurrentAnimations;
    }

    /**
     * Sets the maximum concurrent animations allowed.
     *
     * @param max the new max
     */
    public void setMaxConcurrentAnimations(int max) {
        this.maxConcurrentAnimations = max;
    }

    /**
     * Gets the maximum resource cost allowed.
     *
     * @return the max
     */
    public int getMaxResourceCost() {
        return maxResourceCost;
    }

    /**
     * Sets the maximum resource cost allowed.
     *
     * @param max the new max
     */
    public void setMaxResourceCost(int max) {
        this.maxResourceCost = max;
    }
}
