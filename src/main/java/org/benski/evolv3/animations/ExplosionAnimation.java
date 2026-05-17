package org.benski.evolv3.animations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.benski.evolv3.core.abstracts.BaseAnimation;
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
 * Animation that creates an explosion effect with blocks flying outward. Used by the Explosive
 * enchantment when breaking blocks.
 *
 * <p>The animation spawns BlockDisplay entities that scatter outward with physics-based motion and
 * gravity.
 */
public class ExplosionAnimation extends BaseAnimation {
  private static final int DEFAULT_DURATION = 30;

  private static final double GRAVITY = 0.06;
  private static final float BLOCK_SCALE = 0.35f;

  private final List<Material> materials;
  private final int level;
  private final List<FlyingBlock> flyingBlocks = new ArrayList<>();

  /**
   * Creates an explosion animation with default settings.
   *
   * @param plugin the plugin instance
   */
  public ExplosionAnimation(JavaPlugin plugin) {
    this(plugin, List.of(Material.STONE), 1);
  }

  /**
   * Creates an explosion animation with specific materials and level.
   *
   * @param plugin the plugin instance
   * @param materials the block materials to display
   * @param level the explosion level (affects count and spread)
   */
  public ExplosionAnimation(JavaPlugin plugin, List<Material> materials, int level) {
    super(plugin, DEFAULT_DURATION + (level * 5));
    this.materials = materials.isEmpty() ? List.of(Material.STONE) : materials;
    this.level = Math.max(1, level);
  }

  /** {@inheritDoc} */
  @Override
  protected void onStart() {
    Location origin = getOrigin();
    if (origin == null || origin.getWorld() == null) return;

    World world = origin.getWorld();

    int blockCount = 6 + (level * 3);

    for (int i = 0; i < blockCount; i++) {
      Material material = materials.get(ThreadLocalRandom.current().nextInt(materials.size()));

      final Material blockMaterial =
          (!material.isBlock() || material.isAir()) ? Material.STONE : material;

      double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
      double pitch = ThreadLocalRandom.current().nextDouble() * Math.PI * 0.5;
      double speed = 0.15 + ThreadLocalRandom.current().nextDouble() * 0.2 * level;

      Vector velocity =
          new Vector(
              Math.cos(angle) * Math.cos(pitch) * speed,
              Math.sin(pitch) * speed + 0.1,
              Math.sin(angle) * Math.cos(pitch) * speed);

      BlockDisplay display =
          world.spawn(
              origin,
              BlockDisplay.class,
              entity -> {
                entity.setBlock(blockMaterial.createBlockData());
                entity.setTransformation(
                    new Transformation(
                        new Vector3f(-BLOCK_SCALE / 2, -BLOCK_SCALE / 2, -BLOCK_SCALE / 2),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE),
                        new AxisAngle4f(0, 0, 0, 1)));
                entity.setPersistent(false);

                Collection<Player> viewers = getViewers();
                if (viewers != null) {
                  entity.setVisibleByDefault(false);
                  for (Player viewer : viewers) {
                    viewer.showEntity(plugin, entity);
                  }
                }
              });

      Vector3f rotationAxis =
          new Vector3f(
                  ThreadLocalRandom.current().nextFloat() - 0.5f,
                  ThreadLocalRandom.current().nextFloat() - 0.5f,
                  ThreadLocalRandom.current().nextFloat() - 0.5f)
              .normalize();
      float rotationSpeed = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.3f;

      flyingBlocks.add(
          new FlyingBlock(display, origin.clone(), velocity, rotationAxis, rotationSpeed));
    }

    Collection<Player> soundViewers = getViewers();
    if (soundViewers != null && !soundViewers.isEmpty()) {
      float volume = 0.6f + (level * 0.15f);
      float pitch = 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f;
      for (Player viewer : soundViewers) {
        viewer.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, volume, pitch);
        viewer.spawnParticle(Particle.EXPLOSION, origin, 1);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  protected void onTick(double progress) {
    Location origin = getOrigin();
    if (origin == null || origin.getWorld() == null) return;

    World world = origin.getWorld();

    for (FlyingBlock block : flyingBlocks) {
      if (block.display.isDead()) continue;

      block.velocity.setY(block.velocity.getY() - GRAVITY);

      block.position.add(block.velocity);

      block.display.teleport(block.position);

      block.rotation += block.rotationSpeed;
      block.display.setTransformation(
          new Transformation(
              new Vector3f(-BLOCK_SCALE / 2, -BLOCK_SCALE / 2, -BLOCK_SCALE / 2),
              new AxisAngle4f(
                  block.rotation, block.rotationAxis.x, block.rotationAxis.y, block.rotationAxis.z),
              new Vector3f(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE),
              new AxisAngle4f(0, 0, 0, 1)));

      if (ThreadLocalRandom.current().nextFloat() < 0.3) {
        Collection<Player> smokeViewers = getViewers();
        if (smokeViewers != null && !smokeViewers.isEmpty()) {
          for (Player viewer : smokeViewers) {
            viewer.spawnParticle(Particle.SMOKE, block.position, 1, 0.05, 0.05, 0.05, 0.01);
          }
        }
      }
    }

    if (progress > 0.7) {
      float fadeProgress = (float) ((progress - 0.7) / 0.3);
      float scale = BLOCK_SCALE * (1 - fadeProgress);

      for (FlyingBlock block : flyingBlocks) {
        if (block.display.isDead()) continue;

        block.display.setTransformation(
            new Transformation(
                new Vector3f(-scale / 2, -scale / 2, -scale / 2),
                new AxisAngle4f(
                    block.rotation,
                    block.rotationAxis.x,
                    block.rotationAxis.y,
                    block.rotationAxis.z),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  protected void onComplete() {
    for (FlyingBlock block : flyingBlocks) {
      if (!block.display.isDead()) {
        block.display.remove();
      }
    }
    flyingBlocks.clear();
  }

  /** {@inheritDoc} */
  @Override
  public int getResourceCost() {
    return 6 + (level * 3);
  }

  /** Helper class to track flying block state. */
  private static class FlyingBlock {
    final BlockDisplay display;
    final Location position;
    final Vector velocity;
    final Vector3f rotationAxis;
    final float rotationSpeed;
    float rotation = 0;

    FlyingBlock(
        BlockDisplay display,
        Location position,
        Vector velocity,
        Vector3f rotationAxis,
        float rotationSpeed) {
      this.display = display;
      this.position = position;
      this.velocity = velocity;
      this.rotationAxis = rotationAxis;
      this.rotationSpeed = rotationSpeed;
    }
  }
}
