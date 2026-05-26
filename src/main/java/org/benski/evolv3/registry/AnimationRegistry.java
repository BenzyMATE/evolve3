package org.benski.evolv3.registry;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.enchant.CascadeAnimation;
import org.benski.evolv3.animations.enchant.DrillAnimation;
import org.benski.evolv3.animations.enchant.ExplosionAnimation;
import org.benski.evolv3.animations.enchant.JackhammerAnimation;
import org.benski.evolv3.animations.enchant.LazerAnimation;
import org.benski.evolv3.animations.enchant.NuclearShowerAnimation;
import org.benski.evolv3.animations.enchant.ThorAnimation;
import org.benski.evolv3.animations.enchant.VortexAnimation;
import org.benski.evolv3.animations.pickup.DissolvePickupAnimation;
import org.benski.evolv3.animations.pickup.TeleportPickupAnimation;
import org.benski.evolv3.animations.progression.AtomAnimation;
import org.benski.evolv3.animations.progression.EnchantAnimation;
import org.benski.evolv3.animations.progression.EssenceAnimation;
import org.benski.evolv3.animations.progression.ResinAnimation;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.registry.AnimationRegistryManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Registry for animations. Delegates all operations to {@link AnimationRegistryManager}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * animationRegistry.play("essence", location, List.of(player), 5);
 * animationRegistry.play("hologram", location, List.of(player), "&d+5 Essence");
 * animationRegistry.play("explosion", location);
 * }</pre>
 */
public class AnimationRegistry implements Registry<BaseAnimation> {
  @Getter private static final AnimationRegistry instance = new AnimationRegistry();

  @Getter
  private final AnimationRegistryManager manager = new AnimationRegistryManager(Main.getInstance());

  /** Register animations here. */
  private final List<Class<? extends BaseAnimation>> animations =
      List.of(
          ExplosionAnimation.class,
          EnchantAnimation.class,
          TeleportPickupAnimation.class,
          DissolvePickupAnimation.class,
          EssenceAnimation.class,
          AtomAnimation.class,
          HologramAnimation.class,
          ResinAnimation.class,
          DrillAnimation.class,
          LazerAnimation.class,
          ThorAnimation.class,
          VortexAnimation.class,
          CascadeAnimation.class,
          JackhammerAnimation.class,
          NuclearShowerAnimation.class);

  /** Initializes this component. */
  @Override
  public void initialize() {
    EnchantAnimationConfig.load();
    animations.forEach(manager::registerAnimation);
    manager.initialize();

    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    if (cfg != null) {
      manager.setMaxConcurrentAnimations(
          cfg.getInt(ConfigPaths.Default.Animations.MAX_CONCURRENT, 100));
      manager.setMaxResourceCost(cfg.getInt(ConfigPaths.Default.Animations.MAX_RESOURCE_COST, 500));
    }
  }

  /** Get. */
  @Override
  public BaseAnimation get(String id) {
    throw new UnsupportedOperationException(
        "AnimationRegistry does not store instances. Use create(id, args) or play(id, location, viewers).");
  }

  /** Releases resources held by this component. */
  @Override
  public void cleanup() {
    manager.cleanup();
  }

  /**
   * Creates a new animation instance by identifier, forwarding optional construction args.
   *
   * @param identifier the registered animation type (e.g. "essence", "explosion")
   * @param args optional construction arguments passed to the animation factory
   */
  public BaseAnimation create(String identifier, Object... args) {
    return manager.create(identifier, args);
  }

  /**
   * Creates and plays an animation by identifier with viewer-scoped visibility and extra args.
   *
   * @param identifier the registered animation type
   * @param location the world location to play at
   * @param viewers the players who can see the animation
   * @param args optional construction arguments
   */
  public void play(
      String identifier, Location location, Collection<Player> viewers, Object... args) {
    manager.play(identifier, location, viewers, args);
  }

  /**
   * Creates and plays an animation visible to all nearby players.
   *
   * @param identifier the registered animation type
   * @param location the world location to play at
   */
  public BaseAnimation play(String identifier, Location location) {
    return manager.play(identifier, location);
  }

  /**
   * Creates and plays an animation visible only to the given viewers.
   *
   * @param identifier the registered animation type
   * @param location the world location to play at
   * @param viewers the players who can see the animation
   */
  public BaseAnimation play(String identifier, Location location, Collection<Player> viewers) {
    return manager.play(identifier, location, viewers);
  }

  /**
   * Plays an already-constructed animation visible to all nearby players.
   *
   * @param animation the animation instance to play
   * @param location the world location to play at
   */
  public void play(BaseAnimation animation, Location location) {
    manager.play(animation, location);
  }

  /**
   * Plays an already-constructed animation visible only to the given viewers. Returns {@code false}
   * if the animation cannot be played (resource limit exceeded).
   *
   * @param animation the animation instance to play
   * @param location the world location to play at
   * @param viewers the players who can see the animation
   */
  public boolean play(BaseAnimation animation, Location location, Collection<Player> viewers) {
    return manager.play(animation, location, viewers);
  }

  /** Returns {@code true} if the resource budget allows this animation to be played. */
  public boolean canPlay(BaseAnimation animation) {
    return manager.canPlay(animation);
  }

  /** Immediately stops all currently active animations and clears them from the registry. */
  public void stopAll() {
    manager.stopAll();
  }

  /** Returns the number of animations currently active. */
  public int getActiveAnimationCount() {
    return manager.getActiveAnimationCount();
  }
}
