package org.benski.evolv3.animations;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.types.RarityConfig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * World-space animation triggered when the player selects a prestige stone.
 *
 * <p>Uses a single {@link ItemDisplay} entity whose position is driven entirely
 * through {@link Transformation} translation — this keeps the entity anchored at
 * the player's feet so client-side interpolation ({@code interpolationDuration=2})
 * can smooth every tick's movement without hard teleports.</p>
 *
 * <h3>Timeline</h3>
 * <ol>
 *   <li><b>Orbit (0–54)</b>: Stone spirals inward from 3 → 0.8 blocks radius over ~1.8 circles
 *       with a gentle height bob. Rising-pitch amethyst chimes + rarity-colour dust trail.</li>
 *   <li><b>Zoom (55–69)</b>: Exponential ease toward player's eye height (35% of gap per tick),
 *       spin accelerates, scale shrinks.</li>
 *   <li><b>Impact (tick 69)</b>: Burst of dust + enchant + end-rod particles, level-up sounds.</li>
 *   <li><b>Done (tick 70)</b>: Entity removed; {@code onDone} fires (prestige executes).</li>
 * </ol>
 */
public final class PrestigeStoneFlightAnimation
{
    private static final int ORBIT_TICKS = 55;
    private static final int ZOOM_TICKS  = 15;
    private static final int TOTAL       = ORBIT_TICKS + ZOOM_TICKS;

    private static final float  ITEM_SCALE       = 1.5f;
    private static final double START_RADIUS     = 3.0;
    private static final double END_ORBIT_RADIUS = 0.8;
    private static final double ORBIT_HEIGHT     = 1.4;
    private static final double ORBIT_ROTATIONS  = 1.8;

    private PrestigeStoneFlightAnimation() {}

    // -------------------------------------------------------------------------

    public static void play(Player player, ItemStack stone, RarityConfig rarity, Runnable onDone) {
        Location feet = player.getLocation();

        // Angle toward the player's facing direction (orbit starts in front)
        double yawRad    = Math.toRadians(feet.getYaw());
        double startAngle = -yawRad + Math.PI / 2.0;

        // Spawn the entity at the player's feet; configure inside the consumer
        // so Paper includes the initial state in the spawn packet (entity is visible immediately).
        ItemDisplay display;
        try {
            display = feet.getWorld().spawn(feet, ItemDisplay.class, d -> {
                d.setItemStack(stone);
                d.setViewRange(64f);
                d.setInterpolationDuration(2);
                d.setInterpolationDelay(0);
                // Position the item in front of the player via translation offset
                float tx = (float)(START_RADIUS * Math.cos(startAngle));
                float tz = (float)(START_RADIUS * Math.sin(startAngle));
                d.setTransformation(new Transformation(
                        new Vector3f(tx, (float) ORBIT_HEIGHT, tz),
                        new Quaternionf(),
                        new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                        new Quaternionf()
                ));
            });
        } catch (Exception e) {
            // Entity couldn't be spawned — fire callback immediately so prestige still works
            if (onDone != null) onDone.run();
            return;
        }

        Color color = rarityColor(rarity);
        player.playSound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);

        // --- animation ---
        new BukkitRunnable() {
            float spinAngle = 0f;
            // Current translation offset (world-space relative to entity root = player feet)
            float curTx = (float)(START_RADIUS * Math.cos(startAngle));
            float curTy = (float) ORBIT_HEIGHT;
            float curTz = (float)(START_RADIUS * Math.sin(startAngle));
            int   tick  = 0;

            @Override
            public void run() {
                if (!player.isOnline() || display.isDead()) {
                    if (!display.isDead()) display.remove();
                    cancel();
                    if (onDone != null) onDone.run(); // ensure prestige still fires
                    return;
                }

                if (tick >= TOTAL) {
                    display.remove();
                    cancel();
                    if (onDone != null) onDone.run();
                    return;
                }

                // Keep entity root at player's feet so the translation offset is always relative
                display.teleport(player.getLocation());

                float scale;
                if (tick < ORBIT_TICKS) {
                    double t      = (double) tick / ORBIT_TICKS;
                    double radius = START_RADIUS + (END_ORBIT_RADIUS - START_RADIUS) * t;
                    double angle  = startAngle + t * 2 * Math.PI * ORBIT_ROTATIONS;
                    double bob    = Math.sin(t * Math.PI * 5) * 0.2;

                    curTx = (float)(radius * Math.cos(angle));
                    curTy = (float)(ORBIT_HEIGHT + bob);
                    curTz = (float)(radius * Math.sin(angle));
                    scale = ITEM_SCALE;

                    // Rising chime every 8 ticks
                    if (tick % 8 == 0) {
                        float pitch = 0.7f + ((float) tick / ORBIT_TICKS) * 0.8f;
                        Location soundLoc = player.getLocation().add(curTx, curTy, curTz);
                        player.playSound(soundLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, pitch);
                    }
                } else {
                    // Zoom: exponential ease toward eye-height offset (0, eyeDelta, 0)
                    float eyeDeltaY = (float)(player.getEyeLocation().getY() - player.getLocation().getY());
                    curTx += (0f        - curTx) * 0.35f;
                    curTy += (eyeDeltaY - curTy) * 0.35f;
                    curTz += (0f        - curTz) * 0.35f;

                    // Shrink linearly to 0 so the item is invisible before it reaches the face
                    float zoomT = (float)(tick - ORBIT_TICKS) / ZOOM_TICKS;
                    scale = Math.max(0f, ITEM_SCALE * (1f - zoomT));
                }

                // Spin faster during zoom
                spinAngle += (tick >= ORBIT_TICKS) ? (float)(Math.PI / 3) : (float)(Math.PI / 10);

                display.setTransformation(new Transformation(
                        new Vector3f(curTx, curTy, curTz),
                        new Quaternionf().rotateY(spinAngle),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                ));
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(2);

                // Trail particles at the item's world position
                if (tick % 2 == 0) {
                    Location particleLoc = player.getLocation().add(curTx, curTy, curTz);
                    particleLoc.getWorld().spawnParticle(
                            Particle.DUST, particleLoc, 3,
                            0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(color, 1.5f)
                    );
                    if (tick % 6 == 0 && tick < ORBIT_TICKS) {
                        Location particleLoc2 = player.getLocation().add(curTx, curTy, curTz);
                        particleLoc2.getWorld().spawnParticle(
                                Particle.ENCHANT, particleLoc2, 6, 0.3, 0.3, 0.3, 0.05
                        );
                    }
                }

                // Impact burst fires early in the zoom (item is still ~67% scale and clearly visible)
                if (tick == ORBIT_TICKS + 5) {
                    Location eye = player.getEyeLocation();
                    player.getWorld().spawnParticle(
                            Particle.DUST, eye, 40, 0.4, 0.4, 0.4, 0,
                            new Particle.DustOptions(color, 3f)
                    );
                    player.getWorld().spawnParticle(Particle.ENCHANT, eye, 50, 0.5, 0.5, 0.5, 0.3);
                    player.getWorld().spawnParticle(Particle.END_ROD,  eye, 15, 0.4, 0.4, 0.4, 0.1);
                    player.playSound(eye, Sound.ENTITY_PLAYER_LEVELUP,        1.0f, 1.5f);
                    player.playSound(eye, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 1.0f);
                }

                tick++;
            }
        }.runTaskTimer(Main.getInstance(), 0L, 1L);
    }

    // -------------------------------------------------------------------------

    private static Color rarityColor(RarityConfig rarity) {
        int argb = rarity.textColor().value();
        return Color.fromRGB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }
}
