package org.benski.evolv3.animations.pickup;

import org.bukkit.Location;
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
 * Arc pickup animation.
 * Items launch upward and arc gracefully through the air in a parabolic
 * trajectory toward the player, like a basketball swish.
 */
public class ArcPickupAnimation extends BasePickupAnimation
{
    private static final int DEFAULT_DURATION = 22;
    private static final float ITEM_SCALE = 0.4f;
    private static final double ARC_HEIGHT = 2.5;
    private static final double INITIAL_SPREAD = 0.3;

    private final List<ArcItem> arcItems = new ArrayList<>();

    public ArcPickupAnimation(JavaPlugin plugin) {
        super(plugin, DEFAULT_DURATION);
    }

    @Override
    public String getAnimationId() {
        return "arc";
    }

    @Override
    public String getDisplayName() {
        return "Arc";
    }

    @Override
    public String getDescription() {
        return "Items arc gracefully through the air toward you";
    }

    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null || targetPlayer == null) return;

        World world = origin.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Subtle launch sound
        world.playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.3f, 1.8f);

        Location target = getPlayerTargetLocation();
        if (target == null) return;

        double distance = origin.distance(target);
        double baseArcHeight = ARC_HEIGHT + (distance * 0.3);

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            // Slight random offset at start
            double offsetX = (random.nextDouble() - 0.5) * INITIAL_SPREAD * 2;
            double offsetZ = (random.nextDouble() - 0.5) * INITIAL_SPREAD * 2;
            Location spawnLoc = origin.clone().add(offsetX, 0.2, offsetZ);

            // Randomize arc height slightly
            double arcHeight = baseArcHeight + (random.nextDouble() - 0.5) * 0.8;

            // Random curve offset for visual variety
            double curveOffset = (random.nextDouble() - 0.5) * 0.6;

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

            arcItems.add(new ArcItem(display, spawnLoc.clone(), arcHeight, curveOffset, item));
        }
    }

    @Override
    protected void onTick(double progress) {
        Location origin = getOrigin();
        Location target = getPlayerTargetLocation();
        if (origin == null || target == null || origin.getWorld() == null) return;

        World world = origin.getWorld();
        double easedProgress = easeOutCubic(progress);

        for (ArcItem aItem : arcItems) {
            if (aItem.display.isDead()) continue;

            // Calculate parabolic arc
            // y = -4h(t-0.5)^2 + h where h is max height, t is progress
            double arcMultiplier = -4 * aItem.arcHeight * Math.pow(progress - 0.5, 2) + aItem.arcHeight;

            // Linear interpolation for X and Z
            double x = lerp(aItem.startPosition.getX(), target.getX(), easedProgress);
            double z = lerp(aItem.startPosition.getZ(), target.getZ(), easedProgress);

            // Y follows the arc
            double baseY = lerp(aItem.startPosition.getY(), target.getY(), easedProgress);
            double y = baseY + arcMultiplier;

            // Add slight curve offset perpendicular to travel direction
            Vector toTarget = target.toVector().subtract(origin.toVector()).normalize();
            Vector perpendicular = new Vector(-toTarget.getZ(), 0, toTarget.getX());
            double curveFactor = Math.sin(progress * Math.PI) * aItem.curveOffset;
            x += perpendicular.getX() * curveFactor;
            z += perpendicular.getZ() * curveFactor;

            Location newPos = new Location(world, x, y, z);
            aItem.display.teleport(newPos);

            // Calculate velocity direction for rotation
            aItem.rotation += 0.3f;

            // Tilt based on arc direction
            float tiltAngle = (float) ((progress - 0.5) * Math.PI * 0.5);
            aItem.display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(aItem.rotation, 0, 1, 0),
                new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                new AxisAngle4f(tiltAngle, 1, 0, 0)
            ));

            // Store position for next frame
            aItem.lastPosition = newPos.clone();
        }
    }

    @Override
    protected void onComplete() {
        Location target = getPlayerTargetLocation();
        if (target != null && target.getWorld() != null) {
            World world = target.getWorld();

            // Simple pickup sound
            world.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
        }

        // Cleanup and give items
        for (ArcItem aItem : arcItems) {
            if (!aItem.display.isDead()) {
                aItem.display.remove();
            }

            if (targetPlayer != null && targetPlayer.isOnline()) {
                var leftover = targetPlayer.getInventory().addItem(aItem.item);
                if (!leftover.isEmpty() && target != null) {
                    leftover.values().forEach(item ->
                        target.getWorld().dropItem(target, item)
                    );
                }
            }
        }
        arcItems.clear();
    }

    @Override
    public int getResourceCost() {
        return Math.max(1, items != null ? items.size() : 1);
    }

    private static class ArcItem {
        final ItemDisplay display;
        final Location startPosition;
        final double arcHeight;
        final double curveOffset;
        final ItemStack item;
        Location lastPosition;
        float rotation = 0;

        ArcItem(ItemDisplay display, Location startPosition, double arcHeight, double curveOffset, ItemStack item) {
            this.display = display;
            this.startPosition = startPosition;
            this.lastPosition = startPosition.clone();
            this.arcHeight = arcHeight;
            this.curveOffset = curveOffset;
            this.item = item;
        }
    }
}
