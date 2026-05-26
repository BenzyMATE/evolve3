package org.benski.evolv3.animations.enchant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Animation for the Vortex enchantment.
 *
 * <p>Plays a black-hole collapse effect: dark orbiting particles form a swirling vortex at the
 * centre while {@link BlockDisplay} entities spawned at every broken block position spiral inward,
 * shrink as they approach, and vanish on contact. A final implosion burst fires when the last shard
 * is consumed.
 *
 * <p>Block displays are capped at {@value MAX_SHARDS} to keep frame cost bounded regardless of the
 * sphere radius. Remaining broken blocks are represented only by the squid-ink burst played when
 * they would have reached the centre.
 *
 * <p>All particles are spawned via {@code player.spawnParticle()} and all block displays use {@code
 * setVisibleByDefault(false)} + {@code showEntity()}, so only the triggering player sees the
 * effect.
 */
public class VortexAnimation extends BaseAnimation {
  private static final int DURATION = 50;
  private static final int MAX_SHARDS = 35;
  private static final float SHARD_SCALE = 0.28f;

  private static final Particle.DustOptions DARK_PURPLE =
      new Particle.DustOptions(Color.fromRGB(60, 0, 80), 1.5f);

  /** How quickly the pull force ramps up over the animation. */
  private static final double PULL_BASE = 0.04;

  private static final double PULL_RAMP = 0.16;

  /** One broken block to be animated as a flying shard. */
  public record BlockEntry(Location location, Material material) {}

  private final List<BlockEntry> entries;
  private final boolean sound;
  private final boolean particles;
  private final List<FlyingBlock> shards = new ArrayList<>();
  private Player targetPlayer;

  /** Default constructor required for registry instantiation. */
  public VortexAnimation(JavaPlugin plugin) {
    this(plugin, List.of(), true, true);
  }

  /**
   * @param plugin the plugin instance
   * @param entries each broken block that should appear as a flying shard
   * @param sound whether to play the formation / implosion sounds
   * @param particles whether to render the vortex particle effects
   */
  public VortexAnimation(
      JavaPlugin plugin, List<BlockEntry> entries, boolean sound, boolean particles) {
    super(plugin, DURATION);
    this.entries = entries;
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
      targetPlayer.playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.9f, 0.5f);
    }

    List<BlockEntry> sample = new ArrayList<>(entries);
    if (sample.size() > MAX_SHARDS) {
      Collections.shuffle(sample);
      sample = sample.subList(0, MAX_SHARDS);
    }

    World world = origin.getWorld();
    for (BlockEntry entry : sample) {
      if (!entry.material().isBlock() || entry.material().isAir()) continue;

      final Material mat = entry.material();
      final Location spawnLoc = entry.location().clone().add(0.5, 0.5, 0.5);

      BlockDisplay display =
          world.spawn(
              spawnLoc,
              BlockDisplay.class,
              entity -> {
                entity.setBlock(mat.createBlockData());
                entity.setTransformation(
                    new Transformation(
                        new Vector3f(-SHARD_SCALE / 2, -SHARD_SCALE / 2, -SHARD_SCALE / 2),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(SHARD_SCALE, SHARD_SCALE, SHARD_SCALE),
                        new AxisAngle4f(0, 0, 0, 1)));
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
                entity.setTeleportDuration(2);
                entity.setInterpolationDelay(0);
                entity.setInterpolationDuration(2);

                Collection<Player> vw = getViewers();
                if (vw != null) {
                  for (Player viewer : vw) viewer.showEntity(plugin, entity);
                }
              });

      shards.add(new FlyingBlock(display, spawnLoc.clone()));
    }
  }

  /** Called when tick occurs. */
  @Override
  protected void onTick(double progress) {
    if (targetPlayer == null || !targetPlayer.isOnline()) {
      stop();
      return;
    }

    Location center = getOrigin();
    if (center == null) return;

    ThreadLocalRandom rng = ThreadLocalRandom.current();

    if (particles) {
      targetPlayer.spawnParticle(Particle.SQUID_INK, center, 4, 0.12, 0.12, 0.12, 0.02);

      targetPlayer.spawnParticle(Particle.DUST, center, 2, 0.2, 0.2, 0.2, 0, DARK_PURPLE);

      double orbitRadius = 1.8 * (1.0 - progress * 0.55);
      for (int i = 0; i < 5; i++) {
        double angle = progress * Math.PI * 20 + i * (Math.PI * 2 / 5);
        Location particleLoc =
            center
                .clone()
                .add(
                    Math.cos(angle) * orbitRadius,
                    (rng.nextDouble() - 0.5) * 0.35,
                    Math.sin(angle) * orbitRadius);
        targetPlayer.spawnParticle(Particle.SMOKE, particleLoc, 1, 0, 0, 0, 0);
      }
    }

    double pullStrength = PULL_BASE + PULL_RAMP * progress;

    boolean sendPacket = (getCurrentTick() % 2 == 0);

    Iterator<FlyingBlock> it = shards.iterator();
    while (it.hasNext()) {
      FlyingBlock fb = it.next();
      if (fb.display.isDead()) {
        it.remove();
        continue;
      }

      Vector toCenter = center.toVector().subtract(fb.position.toVector());
      double dist = toCenter.length();

      if (dist < 0.30) {
        if (particles && targetPlayer.isOnline()) {
          targetPlayer.spawnParticle(Particle.SQUID_INK, fb.position, 3, 0.05, 0.05, 0.05, 0);
        }
        fb.display.remove();
        it.remove();
        continue;
      }

      Vector pull = toCenter.normalize().multiply(pullStrength);
      fb.velocity.add(pull);

      Vector rawTangent = new Vector(-toCenter.getZ(), 0, toCenter.getX());
      if (rawTangent.lengthSquared() > 1e-8) {
        fb.velocity.add(rawTangent.normalize().multiply(pullStrength * 0.45));
      }

      double speed = fb.velocity.length();
      if (speed > 0.55) {
        fb.velocity.multiply(0.55 / speed);
      }

      fb.position.add(fb.velocity);

      Location dest = fb.position;
      if (!Double.isFinite(dest.getX())
          || !Double.isFinite(dest.getY())
          || !Double.isFinite(dest.getZ())) {
        fb.display.remove();
        it.remove();
        continue;
      }

      if (sendPacket) {
        fb.display.teleport(dest);
        float scale = SHARD_SCALE * (float) Math.min(1.0, dist / 1.5);
        if (scale > 0.01f) {
          fb.display.setTransformation(
              new Transformation(
                  new Vector3f(-scale / 2, -scale / 2, -scale / 2),
                  new AxisAngle4f(0, 0, 0, 1),
                  new Vector3f(scale, scale, scale),
                  new AxisAngle4f(0, 0, 0, 1)));
        }
      }
    }
  }

  /** Called when complete occurs. */
  @Override
  protected void onComplete() {
    for (FlyingBlock fb : shards) {
      if (!fb.display.isDead()) fb.display.remove();
    }
    shards.clear();

    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    Location center = getOrigin();
    if (center == null) return;

    if (sound) {
      targetPlayer.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.35f, 2.0f);
    }
    if (particles) {
      targetPlayer.spawnParticle(Particle.SQUID_INK, center, 40, 0.4, 0.4, 0.4, 0.08);
      Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(120, 0, 180), 2.5f);
      targetPlayer.spawnParticle(Particle.DUST, center, 25, 0.6, 0.6, 0.6, 0, purple);
    }
  }

  /** Returns the resource cost. */
  @Override
  public int getResourceCost() {
    return 5;
  }

  private static class FlyingBlock {
    final BlockDisplay display;
    final Location position;
    Vector velocity = new Vector(0, 0, 0);

    FlyingBlock(BlockDisplay display, Location position) {
      this.display = display;
      this.position = position;
    }
  }
}
