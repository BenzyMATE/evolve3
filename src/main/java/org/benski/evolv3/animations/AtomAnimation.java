package org.benski.evolv3.animations;

import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Golden orbital animation for atom discovery effects.
 *
 * <p>More premium than the essence animation: three orbital rings of pure gold
 * dust particles form, hold in a slow orbit, then converge and collapse into a
 * singularity above the block before launching toward the player.</p>
 *
 * <p>Phase overview:</p>
 * <ol>
 *   <li>Ground ring  – a quick expanding gold ring signals the discovery (ticks 0–7)</li>
 *   <li>Orbital hold – three rings orbit at different speeds and planes while rising
 *       and eventually converging to a central point (ticks 8–82)</li>
 *   <li>Collapse     – a single gold flash as the rings meet (ticks 83–87)</li>
 *   <li>Launch       – the condensed point accelerates to the player (ticks 88–104)</li>
 * </ol>
 *
 * <p>Particle types used: {@link Particle#DUST} (gold), {@link Particle#END_ROD}
 * (ambient), {@link Particle#FLASH} (one-off at collapse). Intentionally restrained
 * — roughly the same count per tick as the essence animation, but structured.</p>
 */
public class AtomAnimation extends BaseAnimation {
    private static final int DEFAULT_DURATION = 105;

    private static final int ORBITAL_START = 8;
    private static final int ORBITAL_END = 83;
    private static final int COLLAPSE_END = 88;

    private static final Particle.DustOptions GOLD =
            new Particle.DustOptions(Color.fromRGB(255, 238, 0), 1.3f);

    /**
     * Angular speed (radians/tick) for each of the three orbital rings.
     */
    private static final double[] RING_SPEEDS = {0.10, 0.15, 0.08};

    private final int atomAmount;
    private Player targetPlayer;
    private Location centerLocation;

    /**
     * Per-ring angle accumulators — advanced every tick for smooth continuous motion.
     */
    private final double[] ringAngles = new double[3];

    private boolean prefSound = true;
    private boolean prefParticles = true;

    /**
     * Creates an atom animation with default settings.
     * Required for registry registration.
     *
     * @param plugin the plugin instance
     */
    public AtomAnimation(JavaPlugin plugin) {
        this(plugin, 1);
    }

    /**
     * Creates an atom animation for a specific atom amount.
     *
     * @param plugin     the plugin instance
     * @param atomAmount the number of atoms discovered
     */
    public AtomAnimation(JavaPlugin plugin, int atomAmount) {
        super(plugin, DEFAULT_DURATION);
        this.atomAmount = atomAmount;
    }

    // -------------------------------------------------------------------------
    // BaseAnimation hooks
    // -------------------------------------------------------------------------

    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null) return;

        centerLocation = origin.clone().add(0.5, 0.5, 0.5);

        var viewers = getViewers();
        if (viewers != null && !viewers.isEmpty()) {
            targetPlayer = viewers.iterator().next();
        }
        if (targetPlayer == null || !targetPlayer.isOnline()) return;

        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc != null) {
            PlayerProfile profile = pdc.getLocal(targetPlayer);
            if (profile != null) {
                PlayerPreferences prefs = profile.preferences;
                prefSound = prefs.atomSound;
                prefParticles = prefs.atomParticles;
            }
        }

        ringAngles[0] = 0;
        ringAngles[1] = Math.PI * 2.0 / 3.0;
        ringAngles[2] = Math.PI * 4.0 / 3.0;

        if (!prefSound) return;
        targetPlayer.playSound(centerLocation, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.6f);
        targetPlayer.playSound(centerLocation, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.6f, 0.8f);
    }

    @Override
    protected void onTick(double progress) {
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            stop();
            return;
        }

        if (!prefParticles) return;

        int tick = getCurrentTick();

        if (tick < ORBITAL_START) {
            tickGroundRing(tick);
        } else if (tick < ORBITAL_END) {
            tickOrbital(tick - ORBITAL_START);
        } else if (tick < COLLAPSE_END) {
            tickCollapse(tick - ORBITAL_END);
        } else {
            tickLaunch(tick - COLLAPSE_END);
        }
    }

    @Override
    protected void onComplete() {
        if (targetPlayer == null || !targetPlayer.isOnline()) return;
        if (prefSound) {
            targetPlayer.playSound(targetPlayer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
        }
        showHologram();
    }

    // -------------------------------------------------------------------------
    // Phase 1: Ground ring (ticks 0–7)
    // A clean expanding ring at floor level announces the discovery.
    // -------------------------------------------------------------------------

    private void tickGroundRing(int tick) {
        double radius = ((double) (tick + 1) / ORBITAL_START) * 1.8;
        int points = 12;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            Location pos = centerLocation.clone().add(
                    Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            targetPlayer.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, GOLD);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2: Orbital hold (ticks 8–82)
    // Three rings orbit on different planes (XZ horizontal, XY vertical, YZ vertical).
    // Each ring is represented by two opposing gold dust points that advance per tick —
    // subtle but visually structured. The rings rise and slowly converge as t → 1.
    // -------------------------------------------------------------------------

    private void tickOrbital(int localTick) {
        double t = (double) localTick / (ORBITAL_END - ORBITAL_START); // 0 → 1

        // Radius envelope: materialise → hold → converge
        double radius;
        if (t < 0.20) {
            radius = 1.5 * (t / 0.20);                    // grow
        } else if (t < 0.82) {
            radius = 1.5;                                  // stable
        } else {
            radius = 1.5 * ((1.0 - t) / 0.18);            // converge
        }

        // Height rises from 0.5 to 3.0 over the full phase
        double height = 0.5 + t * 2.5;

        // Advance each ring's angle and place two opposing points
        for (int r = 0; r < 3; r++) {
            ringAngles[r] += RING_SPEEDS[r];
            double a = ringAngles[r];
            for (int p = 0; p < 2; p++) {
                Location pos = orbitalPoint(r, a + p * Math.PI, radius, height);
                targetPlayer.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, GOLD);
            }
        }

        // One ambient END_ROD drifting upward near the block every 5 ticks
        if (localTick % 5 == 0) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Location ambient = centerLocation.clone().add(
                    (rng.nextDouble() - 0.5) * 0.6,
                    0.4 + rng.nextDouble() * 1.4,
                    (rng.nextDouble() - 0.5) * 0.6);
            targetPlayer.spawnParticle(Particle.END_ROD, ambient, 1, 0, 0, 0, 0.005);
        }

        // Midpoint chime
        if (localTick == 37 && prefSound) {
            targetPlayer.playSound(centerLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.9f);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3: Collapse (ticks 83–87)
    // The three rings have converged; a single flash and small burst mark the moment.
    // -------------------------------------------------------------------------

    private void tickCollapse(int localTick) {
        Location apex = centerLocation.clone().add(0, 3.0, 0);

        if (localTick == 0) {
            if (prefParticles) {
                // Tight ring of gold dust radiating outward from the apex
                for (int i = 0; i < 16; i++) {
                    double a = (2 * Math.PI / 16) * i;
                    Location pos = apex.clone().add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5);
                    targetPlayer.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, GOLD);
                }
                // White burst as the singularity collapses (FLASH requires Color data in 1.21.1)
                targetPlayer.spawnParticle(Particle.END_ROD, apex, 10, 0.18, 0.18, 0.18, 0.06);
            }
            if (prefSound) {
                targetPlayer.playSound(centerLocation, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 2.0f);
                targetPlayer.playSound(centerLocation, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
            }
        }

        // A few END_ROD sparks dissipating upward after the flash
        if (localTick > 0) {
            targetPlayer.spawnParticle(Particle.END_ROD, apex, 2, 0.15, 0.15, 0.15, 0.02);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 4: Launch (ticks 88–104)
    // The condensed point eases in toward the player — slow start, fast finish.
    // -------------------------------------------------------------------------

    private void tickLaunch(int localTick) {
        int duration = DEFAULT_DURATION - COLLAPSE_END; // 17
        double t = (double) localTick / duration;
        double eased = t * t; // quadratic ease-in

        Location start = centerLocation.clone().add(0, 3.0, 0);
        Location end = targetPlayer.getEyeLocation()
                .add(targetPlayer.getLocation().getDirection().multiply(-0.5).setY(-0.3));

        Vector pos = start.toVector().multiply(1 - eased).add(end.toVector().multiply(eased));
        Location current = pos.toLocation(targetPlayer.getWorld());

        targetPlayer.spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, GOLD);
        targetPlayer.spawnParticle(Particle.END_ROD, current, 1, 0.03, 0.03, 0.03, 0.005);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a point on one of the three orbital planes.
     *
     * <ul>
     *   <li>Ring 0 — horizontal (XZ plane)</li>
     *   <li>Ring 1 — vertical   (XY plane)</li>
     *   <li>Ring 2 — vertical   (YZ plane)</li>
     * </ul>
     */
    private Location orbitalPoint(int ring, double angle, double radius, double height) {
        return switch (ring) {
            case 0 -> centerLocation.clone().add(
                    Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            case 1 -> centerLocation.clone().add(
                    Math.cos(angle) * radius, height + Math.sin(angle) * radius, 0);
            default -> centerLocation.clone().add(
                    0, height + Math.cos(angle) * radius, Math.sin(angle) * radius);
        };
    }

    /**
     * Shows the floating hologram at the discovery block.
     */
    private void showHologram() {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        if (!config.getBoolean(ConfigPaths.Default.Atoms.HOLOGRAM_ENABLED, true)) return;

        String message = config.getString(ConfigPaths.Default.Atoms.HOLOGRAM_TEXT,
                "&#FFD700⚛ &6+%amount% &7Atom");
        message = message.replace("%amount%", Utils.formatNumber(atomAmount));

        Location origin = getOrigin();
        if (origin != null) {
            AnimationRegistry.getInstance().play(
                    "hologram", origin.clone().add(0.5, 1.5, 0.5), List.of(targetPlayer), message);
        }
    }

    @Override
    public int getResourceCost() {
        return 3;
    }
}
