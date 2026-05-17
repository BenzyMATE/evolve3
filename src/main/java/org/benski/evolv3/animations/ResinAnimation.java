package org.benski.evolv3.animations;

import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.benski.evolv3.items.AncientResin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Animation for Ancient Resin extraction from logs.
 *
 * <p>Three phases:
 *
 * <ol>
 *   <li>Burst (0–15t) — amber honey drips burst from the break point.
 *   <li>Swirl (15–40t) — particles spiral upward, converging above the block.
 *   <li>Flash (40–50t) — bright amber flash as the resin crystallises.
 * </ol>
 *
 * On completion the Ancient Resin item is dropped at the convergence point.
 */
public class ResinAnimation extends BaseAnimation {
  private static final int DURATION = 50;
  private static final int BURST_END = 15;
  private static final int SWIRL_END = 40;

  private static final Particle.DustOptions AMBER_DUST =
      new Particle.DustOptions(Color.fromRGB(232, 137, 10), 1.4f);
  private static final Particle.DustOptions BRIGHT_DUST =
      new Particle.DustOptions(Color.fromRGB(255, 205, 55), 2.0f);

  private Player targetPlayer;
  private Location centerLocation;

  public ResinAnimation(JavaPlugin plugin) {
    super(plugin, DURATION);
  }

  @Override
  protected void onStart() {
    Location origin = getOrigin();
    if (origin == null || origin.getWorld() == null) return;

    centerLocation = origin.clone();

    var viewers = getViewers();
    if (viewers != null && !viewers.isEmpty()) {
      targetPlayer = viewers.iterator().next();
    }
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    targetPlayer.playSound(centerLocation, Sound.BLOCK_BEEHIVE_DRIP, 1.0f, 0.7f);
    targetPlayer.playSound(centerLocation, Sound.BLOCK_WET_GRASS_STEP, 0.5f, 0.5f);
  }

  @Override
  protected void onTick(double progress) {
    if (targetPlayer == null || !targetPlayer.isOnline()) {
      stop();
      return;
    }

    int tick = getCurrentTick();

    if (tick < BURST_END) {
      spawnParticle(centerLocation, Particle.DRIPPING_HONEY, 2, 0.35, 0.35, 0.35, 0.0);
      Location jitter =
          centerLocation
              .clone()
              .add(
                  (Math.random() - 0.5) * 0.7,
                  (Math.random() - 0.5) * 0.7,
                  (Math.random() - 0.5) * 0.7);
      spawnDust(jitter, AMBER_DUST, 1);

    } else if (tick < SWIRL_END) {
      int swirl = tick - BURST_END;
      double t = (double) swirl / (SWIRL_END - BURST_END);
      double angle = t * 5 * Math.PI;
      double radius = 0.65 * (1.0 - t * 0.85);
      double height = t * 2.2;

      Location spiral =
          centerLocation.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);

      spawnDust(spiral, AMBER_DUST, 2);

      if (swirl % 4 == 0) {
        spawnParticle(spiral, Particle.WAX_ON, 1, 0.04, 0.04, 0.04, 0.0);
      }
      if (swirl % 3 == 0) {
        Location counter =
            centerLocation
                .clone()
                .add(
                    Math.cos(angle + Math.PI) * radius * 0.5,
                    height,
                    Math.sin(angle + Math.PI) * radius * 0.5);
        spawnDust(counter, AMBER_DUST, 1);
      }

    } else {
      Location peak = centerLocation.clone().add(0, 2.2, 0);
      int flash = tick - SWIRL_END;
      if (flash % 2 == 0) {
        spawnDust(peak, BRIGHT_DUST, 3);
        spawnParticle(peak, Particle.GLOW, 2, 0.08, 0.08, 0.08, 0.0);
      }
    }
  }

  @Override
  protected void onComplete() {
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    Location peakLoc =
        centerLocation != null ? centerLocation.clone().add(0, 2.2, 0) : targetPlayer.getLocation();

    targetPlayer.getInventory().addItem(AncientResin.create(1));

    targetPlayer.spawnParticle(Particle.GLOW, peakLoc, 12, 0.2, 0.2, 0.2, 0.0);
    targetPlayer.spawnParticle(Particle.DRIPPING_HONEY, peakLoc, 6, 0.15, 0.15, 0.15, 0.0);
    targetPlayer.playSound(peakLoc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.0f, 0.6f);
    targetPlayer.playSound(peakLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.5f);
  }

  private void spawnParticle(
      Location loc, Particle particle, int count, double ox, double oy, double oz, double extra) {
    if (targetPlayer != null && targetPlayer.isOnline()) {
      targetPlayer.spawnParticle(particle, loc, count, ox, oy, oz, extra);
    }
  }

  private void spawnDust(Location loc, Particle.DustOptions dust, int count) {
    if (targetPlayer != null && targetPlayer.isOnline()) {
      targetPlayer.spawnParticle(Particle.DUST, loc, count, 0.0, 0.0, 0.0, 0.0, dust);
    }
  }

  @Override
  public int getResourceCost() {
    return 3;
  }
}
