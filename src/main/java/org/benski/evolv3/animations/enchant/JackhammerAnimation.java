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
 * Animation for the Jackhammer enchantment.
 *
 * <p>Plays an expanding shockwave ring at the Y level of the mined block, simulating a
 * ground-shattering pulse that radiates outward to the mine walls. The ring grows from zero to
 * {@code maxRadius} over the animation duration, with increasing particle density as it expands.
 */
public class JackhammerAnimation extends BaseAnimation {
  private static final int DURATION = 30;

  private final int maxRadius;
  private final boolean sound;
  private final boolean particles;

  private Player targetPlayer;

  /** Default constructor required for registry instantiation. */
  public JackhammerAnimation(JavaPlugin plugin) {
    this(plugin, 20, true, true);
  }

  /**
   * @param plugin the plugin instance
   * @param maxRadius the outer radius the shockwave ring expands to (in blocks)
   * @param sound whether to play impact sounds
   * @param particles whether to render shockwave particles
   */
  public JackhammerAnimation(JavaPlugin plugin, int maxRadius, boolean sound, boolean particles) {
    super(plugin, DURATION);
    this.maxRadius = Math.max(1, maxRadius);
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
      targetPlayer.playSound(origin, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.2f, 0.55f);
      targetPlayer.playSound(origin, Sound.BLOCK_STONE_BREAK, 1.0f, 0.7f);
    }
    if (particles) {
      Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(0xFF, 0xAA, 0x00), 2.0f);
      targetPlayer.spawnParticle(Particle.DUST, origin, 20, 0.3, 0.05, 0.3, 0, orange);
      targetPlayer.spawnParticle(Particle.CRIT, origin, 30, 0.5, 0.1, 0.5, 0.15);
      targetPlayer.spawnParticle(
          Particle.BLOCK,
          origin,
          25,
          0.4,
          0.1,
          0.4,
          0.1,
          org.bukkit.Material.STONE.createBlockData());
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

    double currentRadius = progress * maxRadius;
    if (currentRadius < 0.5) return;

    int count = Math.max(12, (int) (currentRadius * Math.PI * 2.5));
    double yOffset = origin.getY() + 0.1;

    Particle.DustOptions ringColor =
        new Particle.DustOptions(
            Color.fromRGB(0xFF, 0xCC, 0x55), (float) Math.max(0.5, 2.0 - progress * 1.5));

    for (int i = 0; i < count; i++) {
      double angle = (2.0 * Math.PI * i) / count;
      double px = origin.getX() + Math.cos(angle) * currentRadius;
      double pz = origin.getZ() + Math.sin(angle) * currentRadius;
      Location pLoc = new Location(origin.getWorld(), px, yOffset, pz);
      targetPlayer.spawnParticle(Particle.DUST, pLoc, 1, 0.05, 0.0, 0.05, 0, ringColor);
      if (i % 2 == 0) {
        targetPlayer.spawnParticle(Particle.CRIT, pLoc, 1, 0.05, 0.02, 0.05, 0.05);
      }
    }

    if (progress < 0.6) {
      double smokeRadius = currentRadius * 0.6;
      int smokeCount = Math.max(4, (int) (smokeRadius * 4));
      for (int i = 0; i < smokeCount; i++) {
        double angle = (2.0 * Math.PI * i) / smokeCount;
        double px = origin.getX() + Math.cos(angle) * smokeRadius;
        double pz = origin.getZ() + Math.sin(angle) * smokeRadius;
        Location sLoc = new Location(origin.getWorld(), px, yOffset, pz);
        targetPlayer.spawnParticle(Particle.SMOKE, sLoc, 1, 0.1, 0.05, 0.1, 0.01);
      }
    }
  }

  /** Called when complete occurs. */
  @Override
  protected void onComplete() {
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    Location origin = getOrigin();
    if (origin == null) return;

    if (sound) {
      targetPlayer.playSound(origin, Sound.BLOCK_STONE_BREAK, 0.6f, 1.3f);
    }
    if (particles) {
      double yOffset = origin.getY() + 0.2;
      int count = Math.max(12, (int) (maxRadius * Math.PI * 2));
      for (int i = 0; i < count; i++) {
        double angle = (2.0 * Math.PI * i) / count;
        double px = origin.getX() + Math.cos(angle) * maxRadius;
        double pz = origin.getZ() + Math.sin(angle) * maxRadius;
        Location pLoc = new Location(origin.getWorld(), px, yOffset, pz);
        targetPlayer.spawnParticle(Particle.SMOKE, pLoc, 2, 0.1, 0.1, 0.1, 0.03);
      }
    }
  }

  /** Returns the resource cost. */
  @Override
  public int getResourceCost() {
    return 4;
  }
}
