package org.benski.evolv3.animations.enchant;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Single-link animation for the Cascade enchantment.
 *
 * <p>Plays a quick fiery burst at one explosion point in the chain. Multiple instances are played
 * in rapid succession by {@code CascadeEnchant} (one per chain link), so this animation is
 * intentionally particle-only — no {@link org.bukkit.entity.BlockDisplay} entities — to keep the
 * cost low when several fire within a fraction of a second of each other.
 *
 * <p>Visually distinct from {@link ExplosionAnimation}: the palette leans orange/red (fire) rather
 * than the gray/stone look of a generic explosion.
 */
public class CascadeAnimation extends BaseAnimation {
  private static final int DURATION = 12;

  private final boolean sound;
  private final boolean particles;
  private Player targetPlayer;

  /** Default constructor required for registry instantiation. */
  public CascadeAnimation(JavaPlugin plugin) {
    this(plugin, true, true);
  }

  /**
   * @param plugin the plugin instance
   * @param sound whether to play the impact sound for this chain link
   * @param particles whether to render the burst particles
   */
  public CascadeAnimation(JavaPlugin plugin, boolean sound, boolean particles) {
    super(plugin, DURATION);
    this.sound = sound;
    this.particles = particles;
  }

  /** Called when this schedule starts. */
  @Override
  protected void onStart() {
    Location origin = getOrigin();
    if (origin == null || origin.getWorld() == null) return;

    Collection<Player> viewers = getViewers();
    if (viewers != null && !viewers.isEmpty()) {
      targetPlayer = viewers.iterator().next();
    }
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    if (sound) {
      float pitch = 0.85f + ThreadLocalRandom.current().nextFloat() * 0.35f;
      targetPlayer.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, pitch);
    }

    if (particles) {
      targetPlayer.spawnParticle(Particle.EXPLOSION, origin, 2, 0.15, 0.15, 0.15, 0);

      Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.8f);
      targetPlayer.spawnParticle(Particle.DUST, origin, 20, 0.5, 0.5, 0.5, 0, orange);

      targetPlayer.spawnParticle(Particle.LAVA, origin, 8, 0.4, 0.4, 0.4, 0.1);

      targetPlayer.spawnParticle(Particle.FLAME, origin, 12, 0.3, 0.3, 0.3, 0.08);
    }
  }

  /** Called when tick occurs. */
  @Override
  protected void onTick(double progress) {
    if (targetPlayer == null || !targetPlayer.isOnline()) {
      stop();
      return;
    }
    if (!particles) return;

    Location origin = getOrigin();
    if (origin == null) return;

    targetPlayer.spawnParticle(
        Particle.SMOKE,
        origin,
        2,
        0.3 * (1 - progress),
        0.3 * (1 - progress),
        0.3 * (1 - progress),
        0.02);
  }

  /** Called when complete occurs. */
  @Override
  protected void onComplete() {
    /* particle-only — nothing to clean up */
  }

  /** Returns the resource cost. */
  @Override
  public int getResourceCost() {
    return 1;
  }
}
