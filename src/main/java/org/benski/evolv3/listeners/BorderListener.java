package org.benski.evolv3.listeners;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.BorderHologramManager;
import org.benski.evolv3.core.managers.BorderManager;
import org.benski.evolv3.core.managers.BorderManager.BorderWall;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Enforces zone movement restrictions and renders a particle wall so players can see where borders
 * are.
 *
 * <p>Wall particles are rendered every 20 ticks for each player who hasn't unlocked the wall and is
 * within {@link #PERP_TRIGGER_DIST} blocks of it.
 *
 * <p>When a player is blocked, a burst of particles fires from the wall surface toward them,
 * curling around the player to simulate the barrier pushing back.
 */
public class BorderListener extends BaseListener {
  private static final Component BLOCKED_TITLE =
      Component.text("BLOCKED").color(TextColor.color(0xFF5555)).decorate(TextDecoration.BOLD);

  private static final Particle.DustOptions IMPACT_DUST =
      new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.4f);

  private static final double PERP_TRIGGER_DIST = 100.0;
  private static final double ALONG_WALL_RANGE = 120.0;
  private static final double PARTICLE_SPACING = 1.8;
  private static final double PARTICLE_Y_STEP = 1.2;
  private static final int ARC_COLUMNS = 3;
  private static final int NODE_COLUMNS = 6;

  private int tick = 0;

  public BorderListener(JavaPlugin plugin) {
    super(plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 20L, 4L);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onPlayerMove(PlayerMoveEvent event) {
    Location from = event.getFrom();
    Location to = event.getTo();
    if (to == null) return;
    if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;

    Player player = event.getPlayer();
    PlayerProfile profile = getProfile(player);
    String rankId = (profile != null) ? profile.getRank() : null;

    BorderWall blocking =
        BorderManager.getInstance()
            .getBlockingWall(rankId, from.getX(), from.getZ(), to.getX(), to.getZ());

    if (blocking == null) return;

    event.setCancelled(true);

    spawnImpactBurst(player, blocking, to);

    if (BorderManager.getInstance().shouldShowTitle(player.getUniqueId())) {
      showBlockedTitle(player, blocking);
    }
  }

  private void tickParticles() {
    tick++;
    Map<String, List<BorderWall>> allBorders = BorderManager.getInstance().getAllBorders();
    if (allBorders.isEmpty()) return;

    FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    if (ranksCfg == null) return;

    List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
    if (order.isEmpty()) return;

    String defaultRank = ranksCfg.getString(ConfigPaths.Ranks.DEFAULT_RANK, "A1");

    for (Player player : Bukkit.getOnlinePlayers()) {
      PlayerProfile profile = getProfile(player);
      String rankId = (profile != null) ? profile.getRank() : null;
      String resolvedRank = (rankId != null) ? rankId : defaultRank;

      int playerIndex = order.indexOf(resolvedRank);
      if (playerIndex == -1) playerIndex = 0;

      double px = player.getX();
      double py = player.getY();
      double pz = player.getZ();

      for (Map.Entry<String, List<BorderWall>> entry : allBorders.entrySet()) {
        int wallRankIndex = order.indexOf(entry.getKey());
        if (wallRankIndex == -1 || playerIndex >= wallRankIndex) continue;

        for (BorderWall wall : entry.getValue()) {
          renderWallParticles(player, wall, px, py, pz);
        }
      }
    }
  }

  /**
   * Plasma barrier effect: - DUST_COLOR_TRANSITION shimmer (rank color → white) fills the wall
   * plane - ELECTRIC_SPARK arcs crackle at random heights every few columns - GLOW pulses at
   * energy-node columns - END_ROD scan line sweeps up the wall over ~3 seconds - END_ROD edge glow
   * traces the top and bottom of the wall
   */
  private void renderWallParticles(
      Player player, BorderWall wall, double px, double py, double pz) {
    if (perpDistToWall(wall, px, pz) > PERP_TRIGGER_DIST) return;

    double dx = wall.x2() - wall.x1();
    double dz = wall.z2() - wall.z1();
    double length = Math.sqrt(dx * dx + dz * dz);
    if (length < 0.001) return;

    Color base = rankColor(wall.rankId());
    Color white = Color.fromRGB(220, 240, 255);
    Particle.DustTransition shimmer = new Particle.DustTransition(base, white, 1.2f);

    double wallHeight = wall.maxY() - wall.minY();
    double scanY = wall.minY() + ((tick * 0.4) % (wallHeight + 2));

    int steps = Math.max(1, (int) (length / PARTICLE_SPACING));
    double rangeSq = ALONG_WALL_RANGE * ALONG_WALL_RANGE;

    for (int step = 0; step <= steps; step++) {
      double t = (double) step / steps;
      double wx = wall.x1() + t * dx;
      double wz = wall.z1() + t * dz;
      if ((wx - px) * (wx - px) + (wz - pz) * (wz - pz) > rangeSq) continue;

      boolean isNode = (step % NODE_COLUMNS == 0);
      boolean isArc = (step % ARC_COLUMNS == 0);

      for (double y = wall.minY(); y <= wall.maxY(); y += PARTICLE_Y_STEP) {
        player.spawnParticle(
            Particle.DUST_COLOR_TRANSITION, wx, y, wz, 1, 0, 0, 0, 0, shimmer, true);
      }

      if (scanY >= wall.minY() && scanY <= wall.maxY()) {
        player.spawnParticle(Particle.END_ROD, wx, scanY, wz, 1, 0.0, 0.05, 0.0, 0.0, null, true);
      }

      player.spawnParticle(
          Particle.END_ROD, wx, wall.minY(), wz, 1, 0.0, 0.0, 0.0, 0.0, null, true);
      player.spawnParticle(
          Particle.END_ROD, wx, wall.maxY(), wz, 1, 0.0, 0.0, 0.0, 0.0, null, true);

      if (isNode) {
        double midY = (wall.minY() + wall.maxY()) / 2.0;
        player.spawnParticle(
            Particle.GLOW, wx, midY, wz, 5, 0.05, wallHeight * 0.35, 0.05, 0, null, true);
      }

      if (isArc && Math.random() < 0.45) {
        double arcY = wall.minY() + 1 + Math.random() * (wallHeight - 2);
        player.spawnParticle(
            Particle.ELECTRIC_SPARK, wx, arcY, wz, 6, 0.1, 0.4, 0.1, 0.04, null, true);
      }
    }
  }

  /** Maps a rank's block letter to a display color for the wall particles. */
  private static Color rankColor(String rankId) {
    char block =
        (rankId == null || rankId.isEmpty()) ? 'A' : Character.toUpperCase(rankId.charAt(0));
    return switch (block) {
      case 'A' -> Color.fromRGB(255, 85, 85);
      case 'B' -> Color.fromRGB(255, 221, 68);
      case 'C' -> Color.fromRGB(85, 255, 119);
      case 'D' -> Color.fromRGB(255, 170, 0);
      case 'E' -> Color.fromRGB(85, 170, 255);
      case 'F' -> Color.fromRGB(255, 85, 255);
      default -> Color.fromRGB(180, 180, 180);
    };
  }

  /**
   * Spawns a three-part particle effect when a player is pushed back by a wall:
   *
   * <ol>
   *   <li>A burst at the impact point on the wall surface
   *   <li>A scattered trail from the wall to the player
   *   <li>A swirling ring that curls around the player
   * </ol>
   */
  private void spawnImpactBurst(Player player, BorderWall wall, Location blocked) {
    double px = blocked.getX();
    double py = blocked.getY();
    double pz = blocked.getZ();

    double[] impact = closestPointOnWall(wall, px, pz);
    double ix = impact[0];
    double iz = impact[1];

    player.spawnParticle(
        Particle.DUST, ix, py + 1.0, iz, 25, 0.4, 0.7, 0.4, 0.0, IMPACT_DUST, true);

    for (int i = 1; i <= 8; i++) {
      double t = i / 9.0;
      double cx = ix + t * (px - ix) + (Math.random() - 0.5) * 0.5;
      double cy = py + 0.5 + Math.random() * 1.5;
      double cz = iz + t * (pz - iz) + (Math.random() - 0.5) * 0.5;
      player.spawnParticle(Particle.DUST, cx, cy, cz, 2, 0.05, 0.05, 0.05, 0.0, IMPACT_DUST, true);
    }

    int ringPoints = 16;
    for (int i = 0; i < ringPoints; i++) {
      double angle = (i / (double) ringPoints) * Math.PI * 2.0;
      double r = 0.8 + Math.random() * 0.4;
      double ry = py + 0.5 + (Math.sin(angle * 2) * 0.6 + 0.6);
      player.spawnParticle(
          Particle.DUST,
          px + Math.cos(angle) * r,
          ry,
          pz + Math.sin(angle) * r,
          1,
          0.0,
          0.0,
          0.0,
          0.0,
          IMPACT_DUST,
          true);
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    BorderHologramManager.getInstance().refreshVisibilityForPlayer(event.getPlayer());
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    BorderManager.getInstance().cleanupPlayer(event.getPlayer().getUniqueId());
  }

  /** Perpendicular distance from (px, pz) to the wall line segment. */
  private static double perpDistToWall(BorderWall wall, double px, double pz) {
    double[] closest = closestPointOnWall(wall, px, pz);
    double ddx = px - closest[0], ddz = pz - closest[1];
    return Math.sqrt(ddx * ddx + ddz * ddz);
  }

  /** Returns the closest point on the wall segment to (px, pz) as [x, z]. */
  private static double[] closestPointOnWall(BorderWall wall, double px, double pz) {
    double dx = wall.x2() - wall.x1(), dz = wall.z2() - wall.z1();
    double len2 = dx * dx + dz * dz;
    if (len2 < 0.001) return new double[] {wall.x1(), wall.z1()};
    double t = Math.max(0.0, Math.min(1.0, ((px - wall.x1()) * dx + (pz - wall.z1()) * dz) / len2));
    return new double[] {wall.x1() + t * dx, wall.z1() + t * dz};
  }

  private void showBlockedTitle(Player player, BorderWall wall) {
    Component subtitle =
        Component.text("Reach rank ")
            .color(TextColor.color(0xAAAAAA))
            .append(
                Component.text(wall.rankId())
                    .color(TextColor.color(0xFFD700))
                    .decorate(TextDecoration.BOLD))
            .append(Component.text(" to enter this zone").color(TextColor.color(0xAAAAAA)));

    player.showTitle(
        Title.title(
            BLOCKED_TITLE,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(400))));
  }

  private PlayerProfile getProfile(Player player) {
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    return (dc != null) ? dc.getLocal(player) : null;
  }
}
