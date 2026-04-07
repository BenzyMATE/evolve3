package org.benski.evolv3.animations;

import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animation for the Nuclear Shower enchantment.
 *
 * <p>Plays from the top-centre of the mine downward. A toxic cloud swirls at
 * the ceiling, then a curtain of acid-green rain descends through the full
 * column height, visually matching the staggered stream of block breaks fired
 * by the enchant logic.</p>
 *
 * <ul>
 *   <li>{@code halfRadius} — half the mine's XZ span, used to scatter rain
 *       particles across the whole footprint.</li>
 *   <li>{@code columnHeight} — vertical span from cloud to mine floor; the
 *       rain front descends across this range over the animation duration.</li>
 * </ul>
 */
public class NuclearShowerAnimation extends BaseAnimation
{
    private static final int DURATION = 70;

    /** Acid green — the signature colour of the nuclear rain. */
    private static final Color ACID_GREEN  = Color.fromRGB(0x39, 0xFF, 0x14);
    /** Faint lime for the cloud swirl — slightly brighter than the drops. */
    private static final Color CLOUD_GREEN = Color.fromRGB(0x80, 0xFF, 0x40);
    /** Toxic yellow-green for the impact burst on completion. */
    private static final Color IMPACT_COLOR = Color.fromRGB(0xCC, 0xFF, 0x00);

    private final int     halfRadius;
    private final int     columnHeight;
    private final boolean sound;
    private final boolean particles;

    private Player targetPlayer;

    // -------------------------------------------------------------------------

    /** Default constructor required for registry instantiation. */
    public NuclearShowerAnimation(JavaPlugin plugin)
    {
        this(plugin, 10, 30, true, true);
    }

    /**
     * @param plugin       the plugin instance
     * @param halfRadius   half the mine's XZ footprint width (used to scatter rain)
     * @param columnHeight vertical distance from cloud to mine floor
     * @param sound        whether to play sounds
     * @param particles    whether to render particles
     */
    public NuclearShowerAnimation(JavaPlugin plugin, int halfRadius, int columnHeight,
                                  boolean sound, boolean particles)
    {
        super(plugin, DURATION);
        this.halfRadius   = Math.max(1, halfRadius);
        this.columnHeight = Math.max(1, columnHeight);
        this.sound        = sound;
        this.particles    = particles;
    }

    // -------------------------------------------------------------------------
    // BaseAnimation hooks
    // -------------------------------------------------------------------------

    @Override
    protected void onStart()
    {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null) return;

        Collection<Player> viewers = getViewers();
        if (viewers != null && !viewers.isEmpty()) {
            targetPlayer = viewers.iterator().next();
        }
        if (targetPlayer == null || !targetPlayer.isOnline()) return;

        if (sound) {
            // Deep radioactive hum as the cloud forms.
            targetPlayer.playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.0f, 0.45f);
            targetPlayer.playSound(origin, Sound.AMBIENT_CAVE, 0.7f, 0.6f);
        }
        if (particles) {
            Particle.DustOptions cloud = new Particle.DustOptions(CLOUD_GREEN, 3.0f);
            // Dense cloud burst overhead.
            targetPlayer.spawnParticle(Particle.DUST, origin, 80,
                    halfRadius * 0.4, 1.0, halfRadius * 0.4, 0, cloud);
            targetPlayer.spawnParticle(Particle.SMOKE, origin, 30,
                    halfRadius * 0.3, 0.5, halfRadius * 0.3, 0.02);
            // First acid drips begin falling immediately.
            Particle.DustOptions drip = new Particle.DustOptions(ACID_GREEN, 1.5f);
            targetPlayer.spawnParticle(Particle.DUST, origin, 25,
                    halfRadius * 0.5, 0.3, halfRadius * 0.5, 0, drip);
        }
    }

    @Override
    protected void onTick(double progress)
    {
        if (targetPlayer == null || !targetPlayer.isOnline()) { stop(); return; }
        if (!particles) return;

        Location origin = getOrigin();
        if (origin == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double frontY = origin.getY() - (progress * columnHeight);

        Particle.DustOptions acidDrop  = new Particle.DustOptions(ACID_GREEN,
                (float) Math.max(0.6, 1.8 - progress));
        Particle.DustOptions cloudSwirl = new Particle.DustOptions(CLOUD_GREEN, 2.0f);
        BlockData limeBlock = Material.LIME_CONCRETE.createBlockData();

        // ---- Cloud swirl at the ceiling ----------------------------------------
        if (progress < 0.85) {
            int cloudCount = (int)(halfRadius * 1.5) + 6;
            double cloudAngle = progress * Math.PI * 10;
            for (int i = 0; i < cloudCount; i++) {
                double angle = cloudAngle + (2.0 * Math.PI * i) / cloudCount;
                double cr    = halfRadius * (0.3 + 0.7 * rng.nextDouble());
                double cx    = origin.getX() + Math.cos(angle) * cr;
                double cz    = origin.getZ() + Math.sin(angle) * cr;
                double cy    = origin.getY() + rng.nextDouble() * 1.2 - 0.6;
                Location cLoc = new Location(origin.getWorld(), cx, cy, cz);
                targetPlayer.spawnParticle(Particle.DUST, cLoc, 1, 0.1, 0.1, 0.1, 0, cloudSwirl);
            }
        }

        // ---- Acid drops falling from the cloud ----------------------------------
        // FALLING_DUST spawned at cloud level so each drop visibly falls under gravity
        // from the cloud all the way down into the mine below.
        int dropCount = Math.max(6, halfRadius * 2);
        for (int i = 0; i < dropCount; i++) {
            double px = origin.getX() + (rng.nextDouble() * 2 - 1) * halfRadius;
            double pz = origin.getZ() + (rng.nextDouble() * 2 - 1) * halfRadius;
            Location cloudDrop = new Location(origin.getWorld(), px, origin.getY() - 0.5, pz);
            targetPlayer.spawnParticle(Particle.FALLING_DUST, cloudDrop, 1,
                    halfRadius * 0.04, 0.1, halfRadius * 0.04, 0, limeBlock);
        }

        // ---- Dense acid impact at the current rain front -----------------------
        int leadCount = Math.max(8, halfRadius * 2);
        for (int i = 0; i < leadCount; i++) {
            double px = origin.getX() + (rng.nextDouble() * 2 - 1) * halfRadius;
            double pz = origin.getZ() + (rng.nextDouble() * 2 - 1) * halfRadius;
            Location drop = new Location(origin.getWorld(), px, frontY, pz);
            targetPlayer.spawnParticle(Particle.DUST, drop, 2, 0.15, 0.3, 0.15, 0, acidDrop);
        }

        // ---- Acid splash sparks where rain meets the floor ---------------------
        if (progress > 0.3) {
            double splashY = origin.getY() - columnHeight;
            int splashCount = (int)(progress * halfRadius * 1.5);
            for (int i = 0; i < splashCount; i++) {
                double px = origin.getX() + (rng.nextDouble() * 2 - 1) * halfRadius;
                double pz = origin.getZ() + (rng.nextDouble() * 2 - 1) * halfRadius;
                Location splash = new Location(origin.getWorld(), px, splashY + 0.1, pz);
                targetPlayer.spawnParticle(Particle.CRIT, splash, 1, 0.3, 0.1, 0.3, 0.04);
            }
        }
    }

    @Override
    protected void onComplete()
    {
        if (targetPlayer == null || !targetPlayer.isOnline()) return;

        Location origin = getOrigin();
        if (origin == null) return;

        Location floor = origin.clone();
        floor.setY(origin.getY() - columnHeight);

        if (sound) {
            targetPlayer.playSound(floor, Sound.BLOCK_SLIME_BLOCK_BREAK, 1.0f, 0.5f);
            targetPlayer.playSound(floor, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 0.7f);
        }
        if (particles) {
            // Final acid splash across the floor.
            Particle.DustOptions impact = new Particle.DustOptions(IMPACT_COLOR, 2.0f);
            targetPlayer.spawnParticle(Particle.DUST, floor, 60,
                    halfRadius * 0.5, 0.4, halfRadius * 0.5, 0, impact);
            targetPlayer.spawnParticle(Particle.CRIT, floor, 40,
                    halfRadius * 0.6, 0.5, halfRadius * 0.6, 0.08);
            // Rising toxic smoke from the carnage.
            targetPlayer.spawnParticle(Particle.SMOKE, floor, 30,
                    halfRadius * 0.4, 0.2, halfRadius * 0.4, 0.04);
        }
    }

    @Override
    public int getResourceCost() {
        return 5;
    }
}
