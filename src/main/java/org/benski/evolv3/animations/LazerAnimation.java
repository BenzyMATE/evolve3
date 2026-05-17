package org.benski.evolv3.animations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Laser beam animation for the Lazer enchantment.
 *
 * <p>Two visual layers:
 *
 * <ol>
 *   <li><b>Beam</b> — an instant burst of cyan {@link Particle#DUST} particles along the full beam
 *       path at tick 0, with a single afterglow pass at tick 3. Minimal particle count (1 per 0.5
 *       blocks).
 *   <li><b>Shards</b> — {@link BlockDisplay} entities at each broken block position, spawned in a
 *       staggered sweep from near to far over the first 12 ticks. Shards fly perpendicular to the
 *       beam direction (sideways) + upward, tumble with randomised rotation, shrink after 65%
 *       progress, and are cleaned up on completion.
 * </ol>
 *
 * <p>Everything is sent via per-player methods — no other player ever sees it.
 */
public class LazerAnimation extends BaseAnimation {
  /** A block position and material broken by the laser. */
  public record ShardBlock(Location location, Material material) {}

  private static final int DURATION = 35;
  private static final int SPAWN_TICKS = 12;
  private static final double GRAVITY = 0.05;
  private static final float SHARD_SCALE = 0.28f;
  private static final double FADE_START = 0.65;
  private static final int SHARDS_PER_BLOCK = 2;

  private static final Particle.DustOptions BEAM_DUST =
      new Particle.DustOptions(Color.fromRGB(0, 210, 255), 1.3f);

  private final Vector direction;

  /** Horizontal vector 90° clockwise from direction — used to fan shards sideways. */
  private final Vector perp;

  private final List<ShardBlock> orderedBlocks;
  private final boolean prefSound;
  private final boolean prefParticles;

  /** Maps tick → list of ShardBlocks to spawn on that tick. */
  private final Map<Integer, List<ShardBlock>> spawnSchedule = new HashMap<>();

  private final List<FlyingBlock> flyingBlocks = new ArrayList<>();

  private Player targetPlayer;
  private Location beamOrigin;

  /** Default constructor required for registry instantiation. */
  public LazerAnimation(JavaPlugin plugin) {
    this(plugin, new Vector(1, 0, 0), List.of(), true, true);
  }

  /**
   * @param plugin the plugin instance
   * @param direction cardinal direction the laser fires in (already normalised)
   * @param orderedBlocks blocks broken by the laser, ordered near → far from origin
   * @param prefSound whether to play sounds (from player EnchantPreference)
   * @param prefParticles whether to show beam particles (from player EnchantPreference)
   */
  public LazerAnimation(
      JavaPlugin plugin,
      Vector direction,
      List<ShardBlock> orderedBlocks,
      boolean prefSound,
      boolean prefParticles) {
    super(plugin, DURATION);
    this.direction = direction.clone();
    this.perp = new Vector(direction.getZ(), 0, -direction.getX());
    this.orderedBlocks = orderedBlocks;
    this.prefSound = prefSound;
    this.prefParticles = prefParticles;
  }

  @Override
  protected void onStart() {
    beamOrigin = getOrigin();
    if (beamOrigin == null || beamOrigin.getWorld() == null) return;

    var viewers = getViewers();
    if (viewers != null && !viewers.isEmpty()) {
      targetPlayer = viewers.iterator().next();
    }
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    buildSpawnSchedule();

    if (prefParticles) spawnBeam(1.3f);

    if (prefSound) {
      targetPlayer.playSound(beamOrigin, Sound.ENTITY_GUARDIAN_ATTACK, 1.0f, 0.8f);
      targetPlayer.playSound(beamOrigin, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 1.4f);
    }

    spawnScheduledShards(0);
  }

  @Override
  protected void onTick(double progress) {
    if (targetPlayer == null || !targetPlayer.isOnline()) {
      stop();
      return;
    }

    int tick = getCurrentTick();

    if (tick == 3 && prefParticles) spawnBeam(0.8f);

    spawnScheduledShards(tick);

    float scale = SHARD_SCALE;
    if (progress > FADE_START) {
      float fade = (float) ((progress - FADE_START) / (1.0 - FADE_START));
      scale = SHARD_SCALE * (1.0f - fade);
    }

    boolean sendPacket = (tick % 2 == 0);
    for (FlyingBlock fb : flyingBlocks) {
      if (fb.display.isDead()) continue;

      fb.velocity.setY(fb.velocity.getY() - GRAVITY);
      fb.position.add(fb.velocity);
      fb.rotation += fb.rotSpeed;

      if (sendPacket) {
        fb.display.teleport(fb.position);
        fb.display.setTransformation(
            new Transformation(
                new Vector3f(-scale / 2, -scale / 2, -scale / 2),
                new AxisAngle4f(fb.rotation, fb.rotAxis.x, fb.rotAxis.y, fb.rotAxis.z),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)));
      }
    }
  }

  @Override
  protected void onComplete() {
    for (FlyingBlock fb : flyingBlocks) {
      if (!fb.display.isDead()) fb.display.remove();
    }
    flyingBlocks.clear();
    spawnSchedule.clear();
  }

  /**
   * Spawns a line of DUST particles along the full beam path from the origin to the last broken
   * block. One particle every 0.5 blocks — minimal count, instant laser feel.
   *
   * @param size dust particle size (lower = more transparent afterglow)
   */
  private void spawnBeam(float size) {
    if (orderedBlocks.isEmpty()) return;

    Particle.DustOptions opts = new Particle.DustOptions(Color.fromRGB(0, 210, 255), size);

    Location last =
        orderedBlocks.get(orderedBlocks.size() - 1).location().clone().add(0.5, 0.5, 0.5);
    double totalDist = beamOrigin.distance(last);
    int steps = (int) (totalDist * 2);

    for (int s = 0; s <= steps; s++) {
      double t = (double) s / Math.max(steps, 1);
      Location pos =
          beamOrigin
              .clone()
              .add(direction.getX() * totalDist * t, 0, direction.getZ() * totalDist * t);
      targetPlayer.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, opts);
    }
  }

  /**
   * Pre-computes which tick each ShardBlock's displays should be spawned on, spreading the sweep
   * evenly across {@value SPAWN_TICKS} ticks.
   */
  private void buildSpawnSchedule() {
    int n = orderedBlocks.size();
    for (int i = 0; i < n; i++) {
      int tick = (n == 1) ? 0 : (int) ((double) i / (n - 1) * SPAWN_TICKS);
      spawnSchedule.computeIfAbsent(tick, k -> new ArrayList<>()).add(orderedBlocks.get(i));
    }
  }

  private void spawnScheduledShards(int tick) {
    List<ShardBlock> toSpawn = spawnSchedule.get(tick);
    if (toSpawn == null) return;

    World world = beamOrigin.getWorld();
    if (world == null) return;

    ThreadLocalRandom rng = ThreadLocalRandom.current();

    for (ShardBlock sb : toSpawn) {
      Material mat =
          sb.material().isBlock() && !sb.material().isAir() ? sb.material() : Material.STONE;

      Location blockCenter = sb.location().clone().add(0.5, 0.5, 0.5);

      for (int s = 0; s < SHARDS_PER_BLOCK; s++) {
        double side = rng.nextBoolean() ? 1 : -1;
        double lateralSpeed = 0.10 + rng.nextDouble() * 0.12;
        double upSpeed = 0.07 + rng.nextDouble() * 0.10;
        double alongBeam = (rng.nextDouble() - 0.5) * 0.05;

        Vector velocity =
            new Vector(
                perp.getX() * side * lateralSpeed + direction.getX() * alongBeam,
                upSpeed,
                perp.getZ() * side * lateralSpeed + direction.getZ() * alongBeam);

        Location spawnLoc =
            blockCenter
                .clone()
                .add(
                    (rng.nextDouble() - 0.5) * 0.5,
                    (rng.nextDouble() - 0.5) * 0.3,
                    (rng.nextDouble() - 0.5) * 0.5);

        Vector3f rotAxis =
            new Vector3f(rng.nextFloat() - 0.5f, rng.nextFloat() - 0.5f, rng.nextFloat() - 0.5f)
                .normalize();
        float rotSpeed = (rng.nextFloat() - 0.5f) * 0.30f;

        final Material blockMat = mat;
        BlockDisplay display =
            world.spawn(
                spawnLoc,
                BlockDisplay.class,
                entity -> {
                  entity.setBlock(blockMat.createBlockData());
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

        flyingBlocks.add(new FlyingBlock(display, spawnLoc.clone(), velocity, rotAxis, rotSpeed));
      }
    }
  }

  @Override
  public int getResourceCost() {
    return 4 + orderedBlocks.size();
  }

  private static class FlyingBlock {
    final BlockDisplay display;
    final Location position;
    final Vector velocity;
    final Vector3f rotAxis;
    final float rotSpeed;
    float rotation = 0;

    FlyingBlock(
        BlockDisplay display,
        Location position,
        Vector velocity,
        Vector3f rotAxis,
        float rotSpeed) {
      this.display = display;
      this.position = position;
      this.velocity = velocity;
      this.rotAxis = rotAxis;
      this.rotSpeed = rotSpeed;
    }
  }
}
