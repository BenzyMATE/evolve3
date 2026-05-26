package org.benski.evolv3.listeners.world;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.world.BeamManager;
import org.benski.evolv3.core.managers.world.BeamManager.Beam;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Renders UFO-style beam particles and smoothly pulls players upward when inside a beam.
 *
 * <p>Performance design: the old edge-column renderer spawned O(W×H + D×H) individual particles per
 * beam per tick, which with large beams and many simultaneous beams would generate thousands of
 * packets per tick and destroy server TPS. This version spawns a fixed O(H) set of corner-line
 * particles regardless of beam width/depth, capping per-beam cost to ~100 particle calls regardless
 * of horizontal size.
 */
public class BeamListener extends BaseListener {
  /** Only render particles for players within this distance of a beam's centre. */
  private static final double VIEW_DIST_SQ = 80.0 * 80.0;

  /** Vertical step between corner-line particles (blocks). */
  private static final double CORNER_STEP = 2.0;

  /** Points around the top ring. */
  private static final int RING_POINTS = 16;

  private long tickCounter;

  /**
   * Players currently in active "lift" mode. A player enters lift mode when they reach the bottom
   * of a beam volume. They leave it when they exit the beam entirely.
   */
  private final Set<UUID> liftingPlayers = new HashSet<>();

  private double pullSpeed;
  private double particleDensity;

  /**
   * Creates and registers the BeamListener and starts levitation and particle tick
   * schedulers. @param plugin the owning plugin
   */
  public BeamListener(JavaPlugin plugin) {
    super(plugin);

    pullSpeed = BeamManager.getInstance().getPullSpeed();
    particleDensity = BeamManager.getInstance().getParticleDensity();

    int particleTick = BeamManager.getInstance().getParticleTick();

    Bukkit.getScheduler().runTaskTimer(plugin, this::tickLevitation, 2L, 2L);

    Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, particleTick, particleTick);
  }

  /** Ticks levitation. */
  private void tickLevitation() {
    Map<String, Beam> allBeams = BeamManager.getInstance().getAllBeams();
    if (allBeams.isEmpty()) return;

    for (Player player : Bukkit.getOnlinePlayers()) {
      double px = player.getX();
      double py = player.getY();
      double pz = player.getZ();

      Beam beam = null;
      for (Beam b : allBeams.values()) {
        if (b.contains(px, py, pz)) {
          beam = b;
          break;
        }
      }

      if (beam == null) {
        liftingPlayers.remove(player.getUniqueId());
        continue;
      }

      Vector vel = player.getVelocity();

      if (player.isSneaking()) {
        liftingPlayers.remove(player.getUniqueId());
        player.setVelocity(vel.setY(-pullSpeed));
        continue;
      }

      UUID uuid = player.getUniqueId();
      boolean nearBottom = (py - beam.minY()) < 3.0;
      if (nearBottom) liftingPlayers.add(uuid);

      if (liftingPlayers.contains(uuid)) {
        player.setVelocity(vel.setY(pullSpeed));
      }
    }

    for (Beam beam : allBeams.values()) {
      double centerX = (beam.minX() + beam.maxX()) / 2.0;
      double centerZ = (beam.minZ() + beam.maxZ()) / 2.0;
      double centerY = (beam.minY() + beam.maxY()) / 2.0;
      double halfW = (beam.maxX() - beam.minX()) / 2.0 + 0.5;
      double halfH = (beam.maxY() - beam.minY()) / 2.0 + 0.5;
      double halfD = (beam.maxZ() - beam.minZ()) / 2.0 + 0.5;

      for (org.bukkit.World world : Bukkit.getWorlds()) {
        Location center = new Location(world, centerX, centerY, centerZ);
        Collection<Entity> nearby = world.getNearbyEntities(center, halfW, halfH, halfD);

        for (Entity entity : nearby) {
          if (entity instanceof Player) continue;
          if (!(entity instanceof LivingEntity)) continue;

          double ex = entity.getX();
          double ey = entity.getY();
          double ez = entity.getZ();
          if (!beam.contains(ex, ey, ez)) continue;

          double dx = ex - centerX;
          double dz = ez - centerZ;
          double dist = Math.sqrt(dx * dx + dz * dz);
          if (dist < 0.01) {
            dx = 1.0;
            dz = 0.0;
            dist = 1.0;
          }
          double pushX = (dx / dist) * 0.5;
          double pushZ = (dz / dist) * 0.5;

          Vector vel = entity.getVelocity();
          entity.setVelocity(new Vector(pushX, Math.max(vel.getY(), 0.0), pushZ));
        }
      }
    }
  }

  /** Ticks particles. */
  private void tickParticles() {
    tickCounter++;

    pullSpeed = BeamManager.getInstance().getPullSpeed();
    particleDensity = BeamManager.getInstance().getParticleDensity();

    Map<String, Beam> allBeams = BeamManager.getInstance().getAllBeams();
    if (allBeams.isEmpty()) return;

    for (Beam beam : allBeams.values()) {
      renderBeamForNearbyPlayers(beam);
    }
  }

  /** Renders beam for nearby players. */
  private void renderBeamForNearbyPlayers(Beam beam) {
    Color baseColor = beam.bukkitColor();
    Particle.DustOptions dust = new Particle.DustOptions(baseColor, 1.2f);
    Particle.DustOptions topDust =
        new Particle.DustOptions(
            Color.fromRGB(
                Math.min(255, baseColor.getRed() + 60),
                Math.min(255, baseColor.getGreen() + 60),
                Math.min(255, baseColor.getBlue() + 60)),
            1.8f);

    double centerX = (beam.minX() + beam.maxX()) / 2.0;
    double centerZ = (beam.minZ() + beam.maxZ()) / 2.0;
    double radiusX = (beam.maxX() - beam.minX()) / 2.0;
    double radiusZ = (beam.maxZ() - beam.minZ()) / 2.0;
    double height = beam.maxY() - beam.minY();

    for (Player player : Bukkit.getOnlinePlayers()) {
      double dx = player.getX() - centerX;
      double dz = player.getZ() - centerZ;
      if (dx * dx + dz * dz > VIEW_DIST_SQ) continue;

      renderCornerLines(player, beam, dust);
      renderInteriorSwirl(player, centerX, centerZ, radiusX, radiusZ, beam.minY(), height, dust);
      renderTopRing(player, centerX, centerZ, radiusX, radiusZ, beam.maxY(), topDust);
    }
  }

  /**
   * Renders the 4 vertical corner lines of the beam.
   *
   * <p>Replaces the old full-face edge-column renderer. Cost is O(height/CORNER_STEP) — completely
   * independent of beam width or depth — so 12 large beams no longer generate thousands of packets
   * per tick.
   */
  private void renderCornerLines(Player player, Beam beam, Particle.DustOptions dust) {
    double minX = beam.minX(), minY = beam.minY(), minZ = beam.minZ();
    double maxX = beam.maxX(), maxY = beam.maxY(), maxZ = beam.maxZ();

    for (double y = minY; y <= maxY; y += CORNER_STEP) {
      player.spawnParticle(Particle.DUST, minX, y, minZ, 1, 0, 0, 0, 0, dust, true);
      player.spawnParticle(Particle.DUST, maxX, y, minZ, 1, 0, 0, 0, 0, dust, true);
      player.spawnParticle(Particle.DUST, minX, y, maxZ, 1, 0, 0, 0, 0, dust, true);
      player.spawnParticle(Particle.DUST, maxX, y, maxZ, 1, 0, 0, 0, 0, dust, true);
    }
  }

  /** Renders interior swirl. */
  private void renderInteriorSwirl(
      Player player,
      double cx,
      double cz,
      double rx,
      double rz,
      double minY,
      double height,
      Particle.DustOptions dust) {
    int steps = Math.max(4, (int) (height / 1.2));
    double angleOffset = tickCounter * 0.3;

    for (int i = 0; i < steps; i++) {
      double t = (double) i / steps;
      double y = minY + t * height;
      double angle = angleOffset + t * Math.PI * 4.0;
      double funnel = 0.4 + 0.6 * (1.0 - t);

      double px = cx + Math.cos(angle) * rx * funnel * 0.7;
      double pz = cz + Math.sin(angle) * rz * funnel * 0.7;
      double px2 = cx + Math.cos(angle + Math.PI) * rx * funnel * 0.7;
      double pz2 = cz + Math.sin(angle + Math.PI) * rz * funnel * 0.7;

      player.spawnParticle(Particle.DUST, px, y, pz, 1, 0, 0, 0, 0, dust, true);
      player.spawnParticle(Particle.DUST, px2, y, pz2, 1, 0, 0, 0, 0, dust, true);
    }
  }

  /** Renders top ring. */
  private void renderTopRing(
      Player player,
      double cx,
      double cz,
      double rx,
      double rz,
      double topY,
      Particle.DustOptions topDust) {
    for (int i = 0; i < RING_POINTS; i++) {
      double angle = (i / (double) RING_POINTS) * Math.PI * 2.0;
      double px = cx + Math.cos(angle) * rx;
      double pz = cz + Math.sin(angle) * rz;
      player.spawnParticle(Particle.DUST, px, topY, pz, 2, 0.05, 0.05, 0.05, 0, topDust, true);
    }
  }
}
