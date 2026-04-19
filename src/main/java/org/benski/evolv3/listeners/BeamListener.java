package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.BeamManager;
import org.benski.evolv3.core.managers.BeamManager.Beam;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Renders UFO-style beam particles and smoothly pulls players upward
 * when inside a beam.
 *
 * <p>Levitation runs on a fast tick (every 2 game ticks) for smooth
 * velocity, while particle rendering runs on a slower configurable tick.
 * Players who are falling (negative Y velocity) pass through without
 * being pulled.</p>
 */
public class BeamListener extends BaseListener
{
    /** Render particles for players within this distance of a beam's centre. */
    private static final double VIEW_DIST_SQ = 150.0 * 150.0;

    /** Accumulated tick counter for swirl animation offset. */
    private long tickCounter;

    public BeamListener(JavaPlugin plugin)
    {
        super(plugin);

        int particleTick = BeamManager.getInstance().getParticleTick();

        // Smooth levitation tick — runs every 2 game ticks (10× per second)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickLevitation, 2L, 2L);

        // Particle rendering on its own (slower) schedule
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, particleTick, particleTick);
    }

    // -------------------------------------------------------------------------
    // Levitation tick (fast — every 2 game ticks for smooth pull)
    // -------------------------------------------------------------------------

    private void tickLevitation()
    {
        Map<String, Beam> allBeams = BeamManager.getInstance().getAllBeams();
        if (allBeams.isEmpty()) return;

        double pullSpeed = BeamManager.getInstance().getPullSpeed();

        for (Player player : Bukkit.getOnlinePlayers())
        {
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            for (Beam beam : allBeams.values())
            {
                if (!beam.contains(px, py, pz)) continue;

                Vector vel = player.getVelocity();
                // Only pull if not actively falling — allows natural drops through
                if (vel.getY() >= -0.1) {
                    player.setVelocity(vel.setY(pullSpeed));
                }
                break; // one beam is enough
            }
        }
    }

    // -------------------------------------------------------------------------
    // Particle tick (slower — configurable rate)
    // -------------------------------------------------------------------------

    private void tickParticles()
    {
        tickCounter++;

        Map<String, Beam> allBeams = BeamManager.getInstance().getAllBeams();
        if (allBeams.isEmpty()) return;

        double density = BeamManager.getInstance().getParticleDensity();

        for (Beam beam : allBeams.values())
        {
            renderBeamForNearbyPlayers(beam, density);
        }
    }

    // -------------------------------------------------------------------------
    // Particle rendering
    // -------------------------------------------------------------------------

    private void renderBeamForNearbyPlayers(Beam beam, double density)
    {
        Color baseColor = beam.bukkitColor();
        Particle.DustOptions dust = new Particle.DustOptions(baseColor, 1.2f);

        Color bright = Color.fromRGB(
                Math.min(255, baseColor.getRed() + 60),
                Math.min(255, baseColor.getGreen() + 60),
                Math.min(255, baseColor.getBlue() + 60));
        Particle.DustOptions topDust = new Particle.DustOptions(bright, 1.8f);

        double centerX = (beam.minX() + beam.maxX()) / 2.0;
        double centerZ = (beam.minZ() + beam.maxZ()) / 2.0;
        double radiusX = (beam.maxX() - beam.minX()) / 2.0;
        double radiusZ = (beam.maxZ() - beam.minZ()) / 2.0;
        double height = beam.maxY() - beam.minY();

        for (Player player : Bukkit.getOnlinePlayers())
        {
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            if (dx * dx + dz * dz > VIEW_DIST_SQ) continue;

            renderEdgeColumns(player, beam, dust, density);
            renderInteriorSwirl(player, centerX, centerZ, radiusX, radiusZ,
                    beam.minY(), height, dust);
            renderTopRing(player, centerX, centerZ, radiusX, radiusZ, beam.maxY(), topDust);
        }
    }

    private void renderEdgeColumns(Player player, Beam beam,
                                    Particle.DustOptions dust, double density)
    {
        double minX = beam.minX(), minY = beam.minY(), minZ = beam.minZ();
        double maxX = beam.maxX(), maxY = beam.maxY(), maxZ = beam.maxZ();

        for (double x = minX; x <= maxX; x += density)
        {
            for (double y = minY; y <= maxY; y += density * 1.5)
            {
                player.spawnParticle(Particle.DUST, x, y, minZ, 1, 0, 0, 0, 0, dust, true);
                player.spawnParticle(Particle.DUST, x, y, maxZ, 1, 0, 0, 0, 0, dust, true);
            }
        }
        for (double z = minZ + density; z < maxZ; z += density)
        {
            for (double y = minY; y <= maxY; y += density * 1.5)
            {
                player.spawnParticle(Particle.DUST, minX, y, z, 1, 0, 0, 0, 0, dust, true);
                player.spawnParticle(Particle.DUST, maxX, y, z, 1, 0, 0, 0, 0, dust, true);
            }
        }
    }

    private void renderInteriorSwirl(Player player,
                                      double cx, double cz,
                                      double rx, double rz,
                                      double minY, double height,
                                      Particle.DustOptions dust)
    {
        int steps = Math.max(4, (int) (height / 0.6));
        double angleOffset = tickCounter * 0.3;

        for (int i = 0; i < steps; i++)
        {
            double t = (double) i / steps;
            double y = minY + t * height;
            double angle = angleOffset + t * Math.PI * 4.0;
            double funnel = 0.4 + 0.6 * (1.0 - t);

            double px = cx + Math.cos(angle) * rx * funnel * 0.7;
            double pz = cz + Math.sin(angle) * rz * funnel * 0.7;
            player.spawnParticle(Particle.DUST, px, y, pz, 1, 0, 0, 0, 0, dust, true);

            double px2 = cx + Math.cos(angle + Math.PI) * rx * funnel * 0.7;
            double pz2 = cz + Math.sin(angle + Math.PI) * rz * funnel * 0.7;
            player.spawnParticle(Particle.DUST, px2, y, pz2, 1, 0, 0, 0, 0, dust, true);
        }
    }

    private void renderTopRing(Player player,
                                double cx, double cz,
                                double rx, double rz,
                                double topY,
                                Particle.DustOptions topDust)
    {
        int ringPoints = 24;
        for (int i = 0; i < ringPoints; i++)
        {
            double angle = (i / (double) ringPoints) * Math.PI * 2.0;
            double px = cx + Math.cos(angle) * rx;
            double pz = cz + Math.sin(angle) * rz;
            player.spawnParticle(Particle.DUST, px, topY, pz, 2, 0.05, 0.05, 0.05, 0, topDust, true);
        }
    }
}
