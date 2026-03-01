package org.benski.evolv3.animations.pickup;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Magnetic pickup animation.
 * Items hover briefly, then smoothly accelerate toward the player
 * as if pulled by a powerful magnetic force with trailing energy particles.
 */
public class MagneticPickupAnimation extends BasePickupAnimation
{
    private static final int DEFAULT_DURATION = 20;
    private static final float ITEM_SCALE = 0.45f;
    private static final double HOVER_HEIGHT = 0.5;
    private static final double SCATTER_RANGE = 0.4;

    private final List<MagneticItem> magneticItems = new ArrayList<>();

    public MagneticPickupAnimation(JavaPlugin plugin) {
        super(plugin, DEFAULT_DURATION);
    }

    @Override
    public String getAnimationId() {
        return "magnetic";
    }

    @Override
    public String getDisplayName() {
        return "Magnetic";
    }

    @Override
    public String getDescription() {
        return "Items are magnetically pulled toward you with smooth acceleration";
    }

    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null || targetPlayer == null) return;

        World world = origin.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Play magnetic charge sound
        world.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 2.0f);

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            // Random scatter position
            double offsetX = (random.nextDouble() - 0.5) * SCATTER_RANGE * 2;
            double offsetZ = (random.nextDouble() - 0.5) * SCATTER_RANGE * 2;
            Location spawnLoc = origin.clone().add(offsetX, HOVER_HEIGHT, offsetZ);

            ItemDisplay display = world.spawn(spawnLoc, ItemDisplay.class, entity -> {
                entity.setItemStack(item);
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                entity.setPersistent(false);
                entity.setBillboard(ItemDisplay.Billboard.CENTER);

                Collection<Player> viewers = getViewers();
                if (viewers != null) {
                    entity.setVisibleByDefault(false);
                    for (Player viewer : viewers) {
                        viewer.showEntity(plugin, entity);
                    }
                }
            });

            magneticItems.add(new MagneticItem(
                display,
                spawnLoc.clone(),
                new Vector(0, 0, 0),
                random.nextFloat() * 0.1f,
                item
            ));
        }
    }

    @Override
    protected void onTick(double progress) {
        Location origin = getOrigin();
        Location target = getPlayerTargetLocation();
        if (origin == null || target == null || origin.getWorld() == null) return;

        World world = origin.getWorld();

        // Hover phase (0-20%), then acceleration phase (20-100%)
        double pullStrength;
        if (progress < 0.2) {
            pullStrength = 0;
        } else {
            double adjustedProgress = (progress - 0.2) / 0.8;
            pullStrength = easeOutCubic(adjustedProgress);
        }

        for (MagneticItem mItem : magneticItems) {
            if (mItem.display.isDead()) continue;

            // Calculate direction to player
            Vector toPlayer = target.toVector().subtract(mItem.position.toVector());
            double distance = toPlayer.length();

            if (progress < 0.2) {
                // Hover with slight bobbing
                double bobOffset = Math.sin(progress * 50 + mItem.phaseOffset * 20) * 0.05;
                mItem.position.setY(mItem.startPosition.getY() + bobOffset);
            } else {
                // Magnetic pull - accelerate toward player
                if (distance > 0.1) {
                    Vector direction = toPlayer.normalize();
                    double acceleration = 0.08 * pullStrength;

                    // Magnetic acceleration increases as items get closer
                    double magneticBoost = 1 + (1 - Math.min(1, distance / 5)) * 2;
                    acceleration *= magneticBoost;

                    mItem.velocity.add(direction.multiply(acceleration));

                    // Damping for smooth motion
                    mItem.velocity.multiply(0.92);

                    // Limit max velocity
                    if (mItem.velocity.length() > 0.8) {
                        mItem.velocity.normalize().multiply(0.8);
                    }
                }

                mItem.position.add(mItem.velocity);
            }

            // Teleport the display
            mItem.display.teleport(mItem.position);

            // Rotation speeds up as it moves faster
            float rotationSpeed = (float) (mItem.velocity.length() * 5);
            mItem.rotation += rotationSpeed;
            mItem.display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(mItem.rotation, 0, 1, 0),
                new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                new AxisAngle4f(0, 0, 1, 0)
            ));

            // Minimal trail - only when moving fast
            if (progress >= 0.4 && mItem.velocity.length() > 0.15 && Math.random() < 0.2) {
                world.spawnParticle(
                    Particle.DUST,
                    mItem.position,
                    1,
                    0.01, 0.01, 0.01,
                    0,
                    new Particle.DustOptions(Color.fromRGB(100, 180, 255), 0.4f)
                );
            }
        }
    }

    @Override
    protected void onComplete() {
        Location target = getPlayerTargetLocation();
        if (target != null && target.getWorld() != null) {
            World world = target.getWorld();

            // Simple pickup sound
            world.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
        }

        // Cleanup and give items
        for (MagneticItem mItem : magneticItems) {
            if (!mItem.display.isDead()) {
                mItem.display.remove();
            }

            if (targetPlayer != null && targetPlayer.isOnline()) {
                var leftover = targetPlayer.getInventory().addItem(mItem.item);
                if (!leftover.isEmpty() && target != null) {
                    leftover.values().forEach(item ->
                        target.getWorld().dropItem(target, item)
                    );
                }
            }
        }
        magneticItems.clear();
    }

    @Override
    public int getResourceCost() {
        return Math.max(1, items != null ? items.size() : 1);
    }

    private static class MagneticItem {
        final ItemDisplay display;
        final Location startPosition;
        final Location position;
        final Vector velocity;
        final float phaseOffset;
        final ItemStack item;
        float rotation = 0;

        MagneticItem(ItemDisplay display, Location startPosition, Vector velocity, float phaseOffset, ItemStack item) {
            this.display = display;
            this.startPosition = startPosition.clone();
            this.position = startPosition.clone();
            this.velocity = velocity;
            this.phaseOffset = phaseOffset;
            this.item = item;
        }
    }
}
