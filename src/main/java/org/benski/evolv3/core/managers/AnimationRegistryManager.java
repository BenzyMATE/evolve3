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
 * Provides tick scheduling, budget enforcement, and factory creation for animations.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Play animation with parameters
 * manager.play("essence", location, List.of(player), 5);
 *
 * // Play simple animation
 * manager.play("explosion", location);
 * }</pre>
 */
public class AnimationRegistryManager
{
    private final JavaPlugin plugin;
    private final Map<String, Class<? extends BaseAnimation>> animationClasses = new HashMap<>();
    private final List<BaseAnimation> activeAnimations = new ArrayList<>();
    /** Animations added while a tick is in progress, flushed after the iterator finishes. */
    private final List<BaseAnimation> pendingAdditions = new ArrayList<>();
    private boolean isTicking = false;

    private int maxConcurrentAnimations = 50;
    private int maxResourceCost = 200;
    private int currentResourceCost = 0;

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
    }

    /**
     * Initializes the animation system and starts the tick scheduler.
     */
    public void initialize() {
        startTicking();
        plugin.getLogger().info("AnimationRegistryManager initialized with " +
                animationClasses.size() + " animation types.");
    }

    private void startTicking() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tick() {
        isTicking = true;
        Iterator<BaseAnimation> iterator = activeAnimations.iterator();

        while (iterator.hasNext()) {
            BaseAnimation animation = iterator.next();

            if (animation.isComplete()) {
                currentResourceCost -= animation.getResourceCost();
                iterator.remove();
            } else if (animation.isPlaying()) {
                animation.tick();
            }
        }

        isTicking = false;

        // Flush any animations that were started during this tick
        if (!pendingAdditions.isEmpty()) {
            activeAnimations.addAll(pendingAdditions);
            pendingAdditions.clear();
        }
    }

    /**
     * Creates an animation by identifier with optional parameters.
     * Scans for a matching constructor based on the provided arguments.
     *
     * @param identifier the animation identifier (e.g., "essence", "hologram")
     * @param args optional constructor arguments (after the plugin parameter)
     * @return the created animation, or null if creation failed
     */
    public BaseAnimation create(String identifier, Object... args) {
        Class<? extends BaseAnimation> animationClass = animationClasses.get(identifier.toLowerCase());
        if (animationClass == null) {
            plugin.getLogger().warning("Animation not found: " + identifier);
            return null;
        }

        try {
            Class<?>[] paramTypes = new Class<?>[args.length + 1];
            paramTypes[0] = JavaPlugin.class;
            for (int i = 0; i < args.length; i++) {
                paramTypes[i + 1] = toPrimitiveType(args[i].getClass());
            }

            Constructor<? extends BaseAnimation> constructor = findConstructor(animationClass, paramTypes);

            if (constructor == null) {
                if (args.length == 0) {
                    constructor = animationClass.getConstructor(JavaPlugin.class);
                } else {
                    plugin.getLogger().warning("No matching constructor for " + identifier + " with " + args.length + " args");
                    return null;
                }
            }

            Object[] paramValues = new Object[args.length + 1];
            paramValues[0] = plugin;
            System.arraycopy(args, 0, paramValues, 1, args.length);

            return constructor.newInstance(paramValues);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create animation " + identifier + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates and plays an animation by identifier with parameters.
     *
     * @param identifier the animation identifier
     * @param location the location to play at
     * @param viewers the players who should see the animation
     * @param args optional constructor arguments
     * @return the animation instance, or null if failed
     */
    public BaseAnimation play(String identifier, Location location, Collection<Player> viewers, Object... args) {
        BaseAnimation animation = create(identifier, args);
        if (animation == null) return null;

        if (play(animation, location, viewers)) {
            return animation;
        }
        return null;
    }

    /**
     * Creates and plays an animation by identifier.
     *
     * @param identifier the animation identifier
     * @param location the location to play at
     * @return the animation instance, or null if failed
     */
    public BaseAnimation play(String identifier, Location location) {
        return play(identifier, location, null, new Object[0]);
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
        return play(identifier, location, viewers, new Object[0]);
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
        // If called during a tick (e.g. an animation spawning a child animation),
        // defer the addition so we don't modify the list mid-iteration.
        if (isTicking) {
            pendingAdditions.add(animation);
        } else {
            activeAnimations.add(animation);
        }
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
        return currentResourceCost + animation.getResourceCost() <= maxResourceCost;
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
        for (BaseAnimation animation : pendingAdditions) {
            animation.stop();
        }
        activeAnimations.clear();
        pendingAdditions.clear();
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
    }

    public int getActiveAnimationCount() {
        return activeAnimations.size();
    }

    public int getCurrentResourceCost() {
        return currentResourceCost;
    }

    public int getMaxConcurrentAnimations() {
        return maxConcurrentAnimations;
    }

    public void setMaxConcurrentAnimations(int max) {
        this.maxConcurrentAnimations = max;
    }

    public int getMaxResourceCost() {
        return maxResourceCost;
    }

    public void setMaxResourceCost(int max) {
        this.maxResourceCost = max;
    }

    @SuppressWarnings("unchecked")
    private Constructor<? extends BaseAnimation> findConstructor(Class<? extends BaseAnimation> clazz, Class<?>[] paramTypes) {
        for (Constructor<?> constructor : clazz.getConstructors()) {
            Class<?>[] constructorParams = constructor.getParameterTypes();
            if (constructorParams.length != paramTypes.length) continue;

            boolean matches = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!isAssignable(constructorParams[i], paramTypes[i])) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                return (Constructor<? extends BaseAnimation>) constructor;
            }
        }
        return null;
    }

    private boolean isAssignable(Class<?> paramType, Class<?> valueType) {
        if (paramType.isAssignableFrom(valueType)) return true;

        if (paramType == int.class && valueType == Integer.class) return true;
        if (paramType == long.class && valueType == Long.class) return true;
        if (paramType == double.class && valueType == Double.class) return true;
        if (paramType == float.class && valueType == Float.class) return true;
        if (paramType == boolean.class && valueType == Boolean.class) return true;
        if (paramType == byte.class && valueType == Byte.class) return true;
        if (paramType == short.class && valueType == Short.class) return true;
        if (paramType == char.class && valueType == Character.class) return true;

        return false;
    }

    private Class<?> toPrimitiveType(Class<?> clazz) {
        if (clazz == Integer.class) return int.class;
        if (clazz == Long.class) return long.class;
        if (clazz == Double.class) return double.class;
        if (clazz == Float.class) return float.class;
        if (clazz == Boolean.class) return boolean.class;
        if (clazz == Byte.class) return byte.class;
        if (clazz == Short.class) return short.class;
        if (clazz == Character.class) return char.class;
        return clazz;
    }
}
