package org.benski.evolv3.animations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Single-level shard burst for the Drill enchantment. Spawns {@link BlockDisplay} entities that fly
 * upward and outward from the centre of one 2×2 drill level, tumble with randomised rotation,
 * shrink as they fall, and are cleaned up on completion.
 *
 * <p>One instance is played per depth level, triggered sequentially by {@code DrillEnchant} with a
 * short delay between each, so the shattering visually propagates downward through the shaft.
 *
 * <p>{@code setVisibleByDefault(false)} + {@code showEntity()} ensures only the triggering player
 * ever sees the entities.
 */
public class DrillAnimation extends BaseAnimation {
  private static final int DURATION = 25;
  private static final int SHARD_COUNT = 5;
  private static final double GRAVITY = 0.05;
  private static final float SHARD_SCALE = 0.30f;

  /** Progress fraction at which shards begin shrinking to zero. */
  private static final double FADE_START = 0.60;

  private final List<Material> materials;
  private final List<FlyingBlock> shards = new ArrayList<>();

  private Player targetPlayer;

  /** Default constructor required for registry instantiation. */
  public DrillAnimation(JavaPlugin plugin) {
    this(plugin, List.of());
  }

  /**
   * @param plugin the plugin instance
   * @param materials block types broken at this level; used for shard appearance
   */
  public DrillAnimation(JavaPlugin plugin, List<Material> materials) {
    super(plugin, DURATION);
    this.materials = materials.isEmpty() ? List.of(Material.STONE) : materials;
  }

  @Override
  protected void onStart() {
    Location origin = getOrigin();
    if (origin == null || origin.getWorld() == null) return;

    var viewers = getViewers();
    if (viewers != null && !viewers.isEmpty()) {
      targetPlayer = viewers.iterator().next();
    }
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    World world = origin.getWorld();
    ThreadLocalRandom rng = ThreadLocalRandom.current();

    for (int i = 0; i < SHARD_COUNT; i++) {
      Material mat = pickMaterial(rng);

      Location spawnLoc =
          origin
              .clone()
              .add(
                  (rng.nextDouble() * 2 - 1),
                  (rng.nextDouble() - 0.5) * 0.25,
                  (rng.nextDouble() * 2 - 1));

      double angle = rng.nextDouble() * Math.PI * 2;
      double outward = 0.03 + rng.nextDouble() * 0.09;
      double upward = 0.16 + rng.nextDouble() * 0.14;
      Vector velocity = new Vector(Math.cos(angle) * outward, upward, Math.sin(angle) * outward);

      Vector3f rotAxis =
          new Vector3f(rng.nextFloat() - 0.5f, rng.nextFloat() - 0.5f, rng.nextFloat() - 0.5f)
              .normalize();
      float rotSpeed = (rng.nextFloat() - 0.5f) * 0.28f;

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

      shards.add(new FlyingBlock(display, spawnLoc.clone(), velocity, rotAxis, rotSpeed));
    }
  }

  @Override
  protected void onTick(double progress) {
    if (targetPlayer == null || !targetPlayer.isOnline()) {
      stop();
      return;
    }

    float scale = SHARD_SCALE;
    if (progress > FADE_START) {
      float fade = (float) ((progress - FADE_START) / (1.0 - FADE_START));
      scale = SHARD_SCALE * (1.0f - fade);
    }

    boolean sendPacket = (getCurrentTick() % 2 == 0);
    for (FlyingBlock fb : shards) {
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
    for (FlyingBlock fb : shards) {
      if (!fb.display.isDead()) fb.display.remove();
    }
    shards.clear();
  }

  private Material pickMaterial(ThreadLocalRandom rng) {
    for (int attempt = 0; attempt < 3; attempt++) {
      Material m = materials.get(rng.nextInt(materials.size()));
      if (m.isBlock() && !m.isAir()) return m;
    }
    return Material.STONE;
  }

  @Override
  public int getResourceCost() {
    return 3;
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
