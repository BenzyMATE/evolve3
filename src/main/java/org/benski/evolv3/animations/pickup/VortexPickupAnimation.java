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
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Vortex pickup animation.
 * Items spiral inward in a tornado-like vortex pattern toward the player.
 * Creates a visually striking effect with rotating particles and items.
 */

public class VortexPickupAnimation extends BasePickupAnimation
{
    private static final int DEFAULT_DURATION = 25;
    private static final float ITEM_SCALE = 0.4f;
    private static final double VORTEX_RADIUS = 1.5;
    private static final double VORTEX_HEIGHT = 2.0;
    private static final int ROTATIONS = 2;

    private final List<VortexItem> vortexItems = new ArrayList<>();

    public VortexPickupAnimation(JavaPlugin plugin) {
        super(plugin, DEFAULT_DURATION);
    }

    @Override
    public String getAnimationId() {
        return "vortex";
    }

    @Override
    public String getDisplayName() {
        return "Vortex";
    }

    @Override
    public String getDescription() {
        return "Items spiral in a tornado vortex toward you";
    }

    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null || targetPlayer == null) return;

        World world = origin.getWorld();

        // Play initial whoosh sound
        world.playSound(origin, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.4f);

        // Create ItemDisplay for each item stack
        double angleOffset = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            // Spawn ItemDisplay entity
            ItemDisplay display = world.spawn(origin, ItemDisplay.class, entity -> {
                entity.setItemStack(item);
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                entity.setPersistent(false);
                entity.setBillboard(ItemDisplay.Billboard.CENTER);
                entity.setGlowing(true);

                Collection<Player> viewers = getViewers();
                if (viewers != null) {
                    entity.setVisibleByDefault(false);
                    for (Player viewer : viewers) {
                        viewer.showEntity(plugin, entity);
                    }
                }
            });

            vortexItems.add(new VortexItem(display, origin.clone(), angleOffset, item));
            angleOffset += (2 * Math.PI) / Math.max(1, items.size());
        }
    }

    @Override
    protected void onTick(double progress) {
        Location origin = getOrigin();
        Location target = getPlayerTargetLocation();
        if (origin == null || target == null || origin.getWorld() == null) return;

        World world = origin.getWorld();
        double easedProgress = easeInOutCubic(progress);

        for (VortexItem vItem : vortexItems) {
            if (vItem.display.isDead()) continue;

            // Calculate spiral parameters
            double angle = vItem.startAngle + (ROTATIONS * 2 * Math.PI * progress);
            double currentRadius = VORTEX_RADIUS * (1 - easedProgress);
            double heightOffset = VORTEX_HEIGHT * progress * (1 - progress) * 4;

            // Calculate position on spiral
            double spiralX = Math.cos(angle) * currentRadius;
            double spiralZ = Math.sin(angle) * currentRadius;

            // Interpolate between origin spiral and target
            double x = lerp(origin.getX() + spiralX, target.getX(), easedProgress);
            double y = lerp(origin.getY() + heightOffset, target.getY(), easedProgress);
            double z = lerp(origin.getZ() + spiralZ, target.getZ(), easedProgress);

            Location newPos = new Location(world, x, y, z);
            vItem.display.teleport(newPos);

            // Rotation effect
            float rotationAngle = (float) (angle * 2);
            vItem.display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(rotationAngle, 0, 1, 0),
                new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                new AxisAngle4f(0, 0, 1, 0)
            ));

            // Minimal trail particle - only occasional
            if (progress < 0.85 && Math.random() < 0.15) {
                world.spawnParticle(
                    Particle.DUST,
                    newPos,
                    1,
                    0.02, 0.02, 0.02,
                    0,
                    new Particle.DustOptions(Color.fromRGB(100, 200, 255), 0.5f)
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
            world.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        }

        // Clean up ItemDisplays and give items to player
        for (VortexItem vItem : vortexItems) {
            if (!vItem.display.isDead()) {
                vItem.display.remove();
            }

            // Give item to player
            if (targetPlayer != null && targetPlayer.isOnline()) {
                var leftover = targetPlayer.getInventory().addItem(vItem.item);
                // Drop any items that didn't fit
                if (!leftover.isEmpty() && target != null) {
                    leftover.values().forEach(item ->
                        target.getWorld().dropItem(target, item)
                    );
                }
            }
        }
        vortexItems.clear();
    }

    @Override
    public int getResourceCost() {
        return Math.max(1, items != null ? items.size() : 1);
    }

    private static class VortexItem {
        final ItemDisplay display;
        final Location startPosition;
        final double startAngle;
        final ItemStack item;

        VortexItem(ItemDisplay display, Location startPosition, double startAngle, ItemStack item) {
            this.display = display;
            this.startPosition = startPosition;
            this.startAngle = startAngle;
            this.item = item;
        }
    }
}
