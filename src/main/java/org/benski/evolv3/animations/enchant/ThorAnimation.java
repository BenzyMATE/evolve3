package org.benski.evolv3.animations.enchant;

import java.util.Collection;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Animation for the Thor enchantment.
 *
 * <p>Plays a lightning-bolt effect that descends from the top of the mine column (where Thor
 * struck) down to the bottom. The lightning front is rendered each tick using electric spark
 * particles, with a white dust burst at the initial strike point and a smoke/spark impact at the
 * base.
 *
 * <p>{@code setVisibleByDefault(false)} is not needed for particle-only animations; all particles
 * are spawned via {@code player.spawnParticle()} so they are already player-scoped.
 */
public class ThorAnimation extends BaseAnimation {
  private static final int DURATION = 28;

  private final int columnHeight;
  private final boolean sound;
  private final boolean particles;

  private Player targetPlayer;

  /** Default constructor required for registry instantiation. */
  public ThorAnimation(JavaPlugin plugin) {
    this(plugin, 10, true, true);
  }

  /**
   * @param plugin the plugin instance
   * @param columnHeight distance in blocks from top to bottom of the column
   * @param sound whether to play thunder/impact sounds
   * @param particles whether to render spark particles
   */
  public ThorAnimation(JavaPlugin plugin, int columnHeight, boolean sound, boolean particles) {
    super(plugin, DURATION);
    this.columnHeight = Math.max(1, columnHeight);
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
      targetPlayer.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.85f);
    }
    if (particles) {
      Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 3.0f);
      targetPlayer.spawnParticle(Particle.DUST, origin, 25, 0.2, 0.2, 0.2, 0, white);
      targetPlayer.spawnParticle(Particle.ELECTRIC_SPARK, origin, 50, 0.3, 0.1, 0.3, 0.25);
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

    double currentY = origin.getY() - (progress * columnHeight);
    Location front = origin.clone();
    front.setY(currentY);

    targetPlayer.spawnParticle(Particle.ELECTRIC_SPARK, front, 10, 0.15, 0.4, 0.15, 0.06);
  }

  /** Called when complete occurs. */
  @Override
  protected void onComplete() {
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    Location origin = getOrigin();
    if (origin == null) return;

    Location bottom = origin.clone();
    bottom.setY(origin.getY() - columnHeight);

    if (sound) {
      targetPlayer.playSound(bottom, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.1f);
    }
    if (particles) {
      targetPlayer.spawnParticle(Particle.ELECTRIC_SPARK, bottom, 40, 0.4, 0.2, 0.4, 0.18);
      targetPlayer.spawnParticle(Particle.SMOKE, bottom, 25, 0.3, 0.4, 0.3, 0.04);
    }
  }

  /** Returns the resource cost. */
  @Override
  public int getResourceCost() {
    return 3;
  }
}
