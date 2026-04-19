package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.LaunchPadManager;
import org.benski.evolv3.core.managers.LaunchPadManager.LaunchPad;
import org.benski.evolv3.core.helpers.Messageable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles launch pad interactions: selection mode for admins, launching
 * players who step on registered pads, cloud trails, and ambient particles.
 *
 * <p><b>Selection modes:</b></p>
 * <ul>
 *   <li>{@code SET} — right-click a pressure plate to register it as a launch pad</li>
 *   <li>{@code REMOVE} — right-click a registered pad to remove it</li>
 * </ul>
 */
public class LaunchPadListener extends BaseListener
{
    /** Selection mode types. */
    public enum SelectionMode { SET, REMOVE }

    /** Per-player active selection mode (null = not in selection mode). */
    private static final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();

    /** Players currently on launch cooldown. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /** Players protected from fall damage after a launch. */
    private final Set<UUID> fallProtected = ConcurrentHashMap.newKeySet();

    /** Players currently showing a cloud trail. */
    private final Set<UUID> activeTrails = ConcurrentHashMap.newKeySet();

    /**
     * Session data for a player in selection mode.
     *
     * @param mode         SET or REMOVE
     * @param forwardPower forward power for pads being created (only used in SET mode)
     * @param upwardPower  upward power for pads being created (only used in SET mode)
     */
    public record SelectionSession(SelectionMode mode, double forwardPower, double upwardPower) {}

    public LaunchPadListener(JavaPlugin plugin)
    {
        super(plugin);

        // Ambient particle ticker
        int ambientTick = LaunchPadManager.getInstance().getAmbientParticleTick();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAmbientParticles, 20L, ambientTick);
    }

    // -------------------------------------------------------------------------
    // Public API for command to manage sessions
    // -------------------------------------------------------------------------

    /** Puts a player into SET selection mode with the given powers. */
    public static void startSetMode(UUID uuid, double forwardPower, double upwardPower)
    {
        sessions.put(uuid, new SelectionSession(SelectionMode.SET, forwardPower, upwardPower));
    }

    /** Puts a player into REMOVE selection mode. */
    public static void startRemoveMode(UUID uuid)
    {
        sessions.put(uuid, new SelectionSession(SelectionMode.REMOVE, 0, 0));
    }

    /** Cancels any active selection mode. Returns true if the player was in a mode. */
    public static boolean cancelMode(UUID uuid)
    {
        return sessions.remove(uuid) != null;
    }

    /** Checks if the player is in any selection mode. */
    public static boolean isInMode(UUID uuid)
    {
        return sessions.containsKey(uuid);
    }

    // -------------------------------------------------------------------------
    // Selection mode — right-click pressure plates
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        SelectionSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Must be a pressure plate
        if (!isPressurePlate(block.getType())) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.NOT_PRESSURE_PLATE);
            return;
        }

        event.setCancelled(true);

        if (session.mode() == SelectionMode.SET) {
            handleSetClick(player, block, session);
        } else {
            handleRemoveClick(player, block);
        }
    }

    private void handleSetClick(Player player, Block block, SelectionSession session)
    {
        LaunchPad existing = LaunchPadManager.getInstance().getPad(block.getLocation());
        if (existing != null) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.ALREADY_EXISTS);
            return;
        }

        LaunchPad pad = LaunchPadManager.getInstance().addPad(
                block.getLocation(), session.forwardPower(), session.upwardPower());

        if (pad != null) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.PAD_CREATED,
                    "%x%", String.valueOf(pad.x()),
                    "%y%", String.valueOf(pad.y()),
                    "%z%", String.valueOf(pad.z()),
                    "%forward%", String.valueOf(pad.forwardPower()),
                    "%upward%", String.valueOf(pad.upwardPower()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        } else {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.CREATE_FAILED);
        }
    }

    private void handleRemoveClick(Player player, Block block)
    {
        if (LaunchPadManager.getInstance().removePad(block.getLocation())) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.PAD_REMOVED,
                    "%x%", String.valueOf(block.getX()),
                    "%y%", String.valueOf(block.getY()),
                    "%z%", String.valueOf(block.getZ()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        } else {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.NO_PAD_AT_LOCATION);
        }
    }

    // -------------------------------------------------------------------------
    // Launch — player steps on a registered pad
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event)
    {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();

        // Don't launch players in selection mode
        if (sessions.containsKey(player.getUniqueId())) return;

        Block below = player.getLocation().getBlock();
        if (!isPressurePlate(below.getType())) return;

        LaunchPad pad = LaunchPadManager.getInstance().getPad(below.getLocation());
        if (pad == null) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        long cooldownMs = LaunchPadManager.getInstance().getCooldownMs();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return;

        cooldowns.put(player.getUniqueId(), now);
        launchPlayer(player, pad);
    }

    /**
     * Launches the player forward in the direction they're facing.
     */
    private void launchPlayer(Player player, LaunchPad pad)
    {
        // Get the player's horizontal facing direction
        Vector direction = player.getLocation().getDirection();
        direction.setY(0).normalize().multiply(pad.forwardPower());
        direction.setY(pad.upwardPower());

        player.setVelocity(direction);

        // Sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);

        // Fall damage protection
        UUID uuid = player.getUniqueId();
        int noFallTicks = LaunchPadManager.getInstance().getNoFallDamageTicks();
        fallProtected.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtected.remove(uuid), noFallTicks);

        // Cloud trail
        startCloudTrail(player);
    }

    // -------------------------------------------------------------------------
    // Cloud trail
    // -------------------------------------------------------------------------

    private void startCloudTrail(Player player)
    {
        LaunchPadManager mgr = LaunchPadManager.getInstance();
        if (!mgr.isTrailEnabled()) return;

        UUID uuid = player.getUniqueId();
        if (activeTrails.contains(uuid)) return;
        activeTrails.add(uuid);

        int particleCount = mgr.getTrailParticleCount();
        int durationTicks = mgr.getTrailDurationTicks();

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () ->
        {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.isOnGround())
            {
                activeTrails.remove(uuid);
                return;
            }

            // Cloud trail at the player's feet
            p.getWorld().spawnParticle(
                    Particle.CLOUD,
                    p.getLocation().add(0, 0.2, 0),
                    particleCount,
                    0.25, 0.1, 0.25,
                    0.02,
                    null,
                    true
            );
        }, 2L, 1L).getTaskId();

        // Cancel after max duration
        Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            Bukkit.getScheduler().cancelTask(taskId);
            activeTrails.remove(uuid);
        }, durationTicks);
    }

    // -------------------------------------------------------------------------
    // Ambient particles around registered pads
    // -------------------------------------------------------------------------

    private void tickAmbientParticles()
    {
        int count = LaunchPadManager.getInstance().getAmbientParticleCount();
        for (LaunchPad pad : LaunchPadManager.getInstance().getAllPads().values())
        {
            org.bukkit.World world = Bukkit.getWorld(pad.world());
            if (world == null) continue;

            // Only render if players are nearby (within 48 blocks)
            Location center = new Location(world, pad.x() + 0.5, pad.y() + 0.3, pad.z() + 0.5);
            boolean hasNearby = false;
            for (Player p : world.getPlayers())
            {
                if (p.getLocation().distanceSquared(center) < 48.0 * 48.0)
                {
                    hasNearby = true;
                    break;
                }
            }
            if (!hasNearby) continue;

            // Gentle cloud wisps floating up from the pad
            world.spawnParticle(
                    Particle.CLOUD,
                    center,
                    count,
                    0.3, 0.15, 0.3,  // spread
                    0.01,             // slow upward drift
                    null,
                    true
            );
        }
    }

    // -------------------------------------------------------------------------
    // Fall damage protection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event)
    {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (fallProtected.contains(player.getUniqueId()))
        {
            event.setCancelled(true);
            fallProtected.remove(player.getUniqueId());
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        UUID uuid = event.getPlayer().getUniqueId();
        cooldowns.remove(uuid);
        fallProtected.remove(uuid);
        activeTrails.remove(uuid);
        sessions.remove(uuid);
    }

    @Override
    public void cleanup()
    {
        cooldowns.clear();
        fallProtected.clear();
        activeTrails.clear();
        sessions.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if the material is any type of pressure plate.
     */
    private static boolean isPressurePlate(Material mat)
    {
        return Tag.PRESSURE_PLATES.isTagged(mat);
    }
}
