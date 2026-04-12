package org.benski.evolv3.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.BorderManager;
import org.benski.evolv3.core.managers.BorderManager.BorderWall;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Handles wand interactions for border-wall selection, enforces zone movement
 * restrictions, and renders a particle wall so players can see where borders are.
 *
 * <p>Wall particles are rendered every 20 ticks for each player who hasn't
 * unlocked the wall and is within {@link #PERP_TRIGGER_DIST} blocks of it.</p>
 *
 * <p>When a player is blocked, a burst of particles fires from the wall surface
 * toward them, curling around the player to simulate the barrier pushing back.</p>
 */
public class BorderListener extends BaseListener
{
    private static final Component BLOCKED_TITLE = Component.text("BLOCKED")
            .color(TextColor.color(0xFF5555))
            .decorate(TextDecoration.BOLD);

    // Wall particle appearance
    private static final Particle.DustOptions WALL_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.4f);

    /** Perpendicular distance from wall at which the wall becomes visible */
    private static final double PERP_TRIGGER_DIST = 18.0;

    /** Once the wall is visible, show this many blocks along the wall in each direction */
    private static final double ALONG_WALL_RANGE = 50.0;

    /** Horizontal spacing between particle columns (blocks) */
    private static final double PARTICLE_SPACING = 0.9;

    /** Vertical spacing between particle rows (blocks) */
    private static final double PARTICLE_Y_STEP = 0.75;

    public BorderListener(JavaPlugin plugin)
    {
        super(plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 20L, 20L);
    }

    // -------------------------------------------------------------------------
    // Wand — left-click = point 1, right-click = point 2
    // -------------------------------------------------------------------------

    @EventHandler
    public void onWandInteract(PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWand(held)) return;

        event.setCancelled(true);

        Location clicked = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : player.getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
            BorderManager.getInstance().setPoint1(player.getUniqueId(), clicked);
            player.sendMessage(Component.text("§aPoint 1 set at §e"
                    + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ()));
        } else {
            BorderManager.getInstance().setPoint2(player.getUniqueId(), clicked);
            player.sendMessage(Component.text("§aPoint 2 set at §e"
                    + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ()));
        }
    }

    // -------------------------------------------------------------------------
    // Movement enforcement
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event)
    {
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        PlayerProfile profile = getProfile(player);
        String rankId = (profile != null) ? profile.rank : null;

        BorderWall blocking = BorderManager.getInstance().getBlockingWall(
                rankId, from.getX(), from.getZ(), to.getX(), to.getZ());

        if (blocking == null) return;

        event.setCancelled(true);

        // Always play the impact burst, throttle the title separately
        spawnImpactBurst(player, blocking, to);

        if (BorderManager.getInstance().shouldShowTitle(player.getUniqueId())) {
            showBlockedTitle(player, blocking);
        }
    }

    // -------------------------------------------------------------------------
    // Particle wall rendering (1-second tick)
    // -------------------------------------------------------------------------

    private void tickParticles()
    {
        Map<String, List<BorderWall>> allBorders = BorderManager.getInstance().getAllBorders();
        if (allBorders.isEmpty()) return;

        FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
        if (ranksCfg == null) return;

        List<String> order = ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
        if (order.isEmpty()) return;

        String defaultRank = ranksCfg.getString(ConfigPaths.Ranks.DEFAULT_RANK, "A1");

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = getProfile(player);
            String rankId = (profile != null) ? profile.rank : null;
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
     * Renders particles along the wall for one player.
     *
     * <p>The wall is only shown when the player is within {@link #PERP_TRIGGER_DIST}
     * blocks (perpendicular) of the wall. When triggered, every particle column along
     * the wall within {@link #ALONG_WALL_RANGE} blocks of the player is rendered.</p>
     */
    private void renderWallParticles(Player player, BorderWall wall,
                                      double px, double py, double pz)
    {
        // Perpendicular distance from player to the wall line segment
        if (perpDistToWall(wall, px, pz) > PERP_TRIGGER_DIST) return;

        double dx = wall.x2() - wall.x1();
        double dz = wall.z2() - wall.z1();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return;

        int steps = Math.max(1, (int) (length / PARTICLE_SPACING));
        double rangeSq = ALONG_WALL_RANGE * ALONG_WALL_RANGE;

        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            double wx = wall.x1() + t * dx;
            double wz = wall.z1() + t * dz;

            // Only render columns that are within ALONG_WALL_RANGE of the player
            if ((wx - px) * (wx - px) + (wz - pz) * (wz - pz) > rangeSq) continue;

            for (double y = py - 0.5; y <= py + 3.5; y += PARTICLE_Y_STEP) {
                player.spawnParticle(Particle.DUST, wx, y, wz,
                        1, 0.0, 0.0, 0.0, 0.0, WALL_DUST);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Impact burst — fires when the player is blocked
    // -------------------------------------------------------------------------

    /**
     * Spawns a three-part particle effect when a player is pushed back by a wall:
     * <ol>
     *   <li>A burst at the impact point on the wall surface</li>
     *   <li>A scattered trail from the wall to the player</li>
     *   <li>A swirling ring that curls around the player</li>
     * </ol>
     */
    private void spawnImpactBurst(Player player, BorderWall wall, Location blocked)
    {
        double px = blocked.getX();
        double py = blocked.getY();
        double pz = blocked.getZ();

        // Closest point on the wall to where the player tried to move
        double[] impact = closestPointOnWall(wall, px, pz);
        double ix = impact[0];
        double iz = impact[1];

        // 1. Burst at the impact point
        player.spawnParticle(Particle.DUST, ix, py + 1.0, iz,
                25, 0.4, 0.7, 0.4, 0.0, WALL_DUST);

        // 2. Trail from impact point toward the player (8 points)
        for (int i = 1; i <= 8; i++) {
            double t = i / 9.0;
            double cx = ix + t * (px - ix) + (Math.random() - 0.5) * 0.5;
            double cy = py + 0.5 + Math.random() * 1.5;
            double cz = iz + t * (pz - iz) + (Math.random() - 0.5) * 0.5;
            player.spawnParticle(Particle.DUST, cx, cy, cz,
                    2, 0.05, 0.05, 0.05, 0.0, WALL_DUST);
        }

        // 3. Swirling ring curling around the player
        int ringPoints = 16;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (i / (double) ringPoints) * Math.PI * 2.0;
            double r = 0.8 + Math.random() * 0.4;
            // Y varies sinusoidally to give a spinning-helix feel
            double ry = py + 0.5 + (Math.sin(angle * 2) * 0.6 + 0.6);
            player.spawnParticle(Particle.DUST,
                    px + Math.cos(angle) * r,
                    ry,
                    pz + Math.sin(angle) * r,
                    1, 0.0, 0.0, 0.0, 0.0, WALL_DUST);
        }
    }

    // -------------------------------------------------------------------------
    // Quit cleanup
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        BorderManager.getInstance().cleanupPlayer(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /**
     * Perpendicular distance from (px, pz) to the wall line segment.
     */
    private static double perpDistToWall(BorderWall wall, double px, double pz)
    {
        double[] closest = closestPointOnWall(wall, px, pz);
        double ddx = px - closest[0], ddz = pz - closest[1];
        return Math.sqrt(ddx * ddx + ddz * ddz);
    }

    /**
     * Returns the closest point on the wall segment to (px, pz) as [x, z].
     */
    private static double[] closestPointOnWall(BorderWall wall, double px, double pz)
    {
        double dx = wall.x2() - wall.x1(), dz = wall.z2() - wall.z1();
        double len2 = dx * dx + dz * dz;
        if (len2 < 0.001) return new double[]{ wall.x1(), wall.z1() };
        double t = Math.max(0.0, Math.min(1.0,
                ((px - wall.x1()) * dx + (pz - wall.z1()) * dz) / len2));
        return new double[]{ wall.x1() + t * dx, wall.z1() + t * dz };
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void showBlockedTitle(Player player, BorderWall wall)
    {
        Component subtitle = Component.text("Reach rank ")
                .color(TextColor.color(0xAAAAAA))
                .append(Component.text(wall.rankId())
                        .color(TextColor.color(0xFFD700))
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(" to enter this zone")
                        .color(TextColor.color(0xAAAAAA)));

        player.showTitle(Title.title(
                BLOCKED_TITLE, subtitle,
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(400))
        ));
    }

    private boolean isWand(ItemStack item)
    {
        if (item == null || item.getType() != Material.GOLDEN_AXE) return false;
        if (!item.hasItemMeta()) return false;
        var name = item.getItemMeta().displayName();
        if (name == null) return false;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(name).equals("Border Wand");
    }

    private PlayerProfile getProfile(Player player)
    {
        PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        return (dc != null) ? dc.getLocal(player) : null;
    }
}
