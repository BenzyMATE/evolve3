package org.benski.evolv3.animations;

import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single-link animation for the Cascade enchantment.
 *
 * <p>Plays a quick fiery burst at one explosion point in the chain. Multiple
 * instances are played in rapid succession by {@code CascadeEnchant} (one per
 * chain link), so this animation is intentionally particle-only — no
 * {@link org.bukkit.entity.BlockDisplay} entities — to keep the cost low when
 * several fire within a fraction of a second of each other.</p>
 *
 * <p>Visually distinct from {@link ExplosionAnimation}: the palette leans
 * orange/red (fire) rather than the grey/stone look of a generic explosion.</p>
 */
public class CascadeAnimation extends BaseAnimation
{
    private static final int DURATION = 12;

    private final boolean sound;
    private final boolean particles;
    private Player targetPlayer;

    // -------------------------------------------------------------------------

    /** Default constructor required for registry instantiation. */
    public CascadeAnimation(JavaPlugin plugin)
    {
        this(plugin, true, true);
    }

    /**
     * @param plugin    the plugin instance
     * @param sound     whether to play the impact sound for this chain link
     * @param particles whether to render the burst particles
     */
    public CascadeAnimation(JavaPlugin plugin, boolean sound, boolean particles)
    {
        super(plugin, DURATION);
        this.sound     = sound;
        this.particles = particles;
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
            // Slightly randomised pitch per link so the chain sounds like rapid
            // distinct detonations rather than one sustained noise.
            float pitch = 0.85f + ThreadLocalRandom.current().nextFloat() * 0.35f;
            targetPlayer.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, pitch);
        }

        if (particles) {
            // Central detonation flash.
            targetPlayer.spawnParticle(Particle.EXPLOSION, origin, 2, 0.15, 0.15, 0.15, 0);

            // Fiery outward scatter — orange dust.
            Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.8f);
            targetPlayer.spawnParticle(Particle.DUST, origin, 20, 0.5, 0.5, 0.5, 0, orange);

            // Lava sparks flying outward.
            targetPlayer.spawnParticle(Particle.LAVA, origin, 8, 0.4, 0.4, 0.4, 0.1);

            // Flame particles — tight cluster at centre.
            targetPlayer.spawnParticle(Particle.FLAME, origin, 12, 0.3, 0.3, 0.3, 0.08);
        }
    }

    @Override
    protected void onTick(double progress)
    {
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            stop();
            return;
        }
        if (!particles) return;

        Location origin = getOrigin();
        if (origin == null) return;

        // Dissipating smoke trail as the blast settles.
        targetPlayer.spawnParticle(Particle.SMOKE, origin, 2,
                0.3 * (1 - progress), 0.3 * (1 - progress), 0.3 * (1 - progress), 0.02);
    }

    @Override
    protected void onComplete() { /* particle-only — nothing to clean up */ }

    @Override
    public int getResourceCost() { return 1; }
}
