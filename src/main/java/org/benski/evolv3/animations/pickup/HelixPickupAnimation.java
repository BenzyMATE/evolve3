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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helix pickup animation.
 * Items spiral upward in a DNA-like double helix pattern, then
 * converge and fly smoothly toward the player.
 */
public class HelixPickupAnimation extends BasePickupAnimation
{
    private static final int DEFAULT_DURATION = 28;
    private static final float ITEM_SCALE = 0.4f;
    private static final double HELIX_RADIUS = 0.6;
    private static final double HELIX_HEIGHT = 2.0;
    private static final int HELIX_ROTATIONS = 2;

    private final List<HelixItem> helixItems = new ArrayList<>();

    public HelixPickupAnimation(JavaPlugin plugin) {
        super(plugin, DEFAULT_DURATION);
    }

    @Override
    public String getAnimationId() {
        return "helix";
    }

    @Override
    public String getDisplayName() {
        return "Helix";
    }

    @Override
    public String getDescription() {
        return "Items spiral upward in a DNA helix pattern";
    }

    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null || targetPlayer == null) return;

        World world = origin.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Ethereal startup sound
        world.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        world.playSound(origin, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.5f);

        int index = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            // Alternate between two helix strands (offset by PI)
            double baseAngle = (index % 2 == 0) ? 0 : Math.PI;
            double randomOffset = random.nextDouble() * 0.3;

            ItemDisplay display = world.spawn(origin, ItemDisplay.class, entity -> {
                entity.setItemStack(item);
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.1f, 0.1f, 0.1f),
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

            helixItems.add(new HelixItem(display, origin.clone(), baseAngle, randomOffset, item, index));
            index++;
        }
    }

    @Override
    protected void onTick(double progress) {
        Location origin = getOrigin();
        Location target = getPlayerTargetLocation();
        if (origin == null || target == null || origin.getWorld() == null) return;

        World world = origin.getWorld();

        // Phase 1 (0-60%): Helix rise
        // Phase 2 (60-100%): Converge and fly to player
        boolean inHelixPhase = progress < 0.6;
        double phaseProgress;
        double easedProgress;

        if (inHelixPhase) {
            phaseProgress = progress / 0.6;
            easedProgress = easeOutCubic(phaseProgress);
        } else {
            phaseProgress = (progress - 0.6) / 0.4;
            easedProgress = easeInOutCubic(phaseProgress);
        }

        // Calculate helix center that rises during phase 1
        double helixCenterY = origin.getY() + (inHelixPhase ? HELIX_HEIGHT * easedProgress : HELIX_HEIGHT);
        Location helixCenter = origin.clone();
        helixCenter.setY(helixCenterY);

        for (HelixItem hItem : helixItems) {
            if (hItem.display.isDead()) continue;

            Location newPos;
            float scale;

            if (inHelixPhase) {
                // Calculate helix position
                double angle = hItem.baseAngle + (HELIX_ROTATIONS * 2 * Math.PI * phaseProgress);
                double currentRadius = HELIX_RADIUS * (0.3 + 0.7 * Math.sin(phaseProgress * Math.PI));

                double x = helixCenter.getX() + Math.cos(angle) * currentRadius;
                double z = helixCenter.getZ() + Math.sin(angle) * currentRadius;
                double y = helixCenter.getY() + hItem.randomOffset;

                newPos = new Location(world, x, y, z);

                // Scale up during helix phase
                scale = ITEM_SCALE * (float) Math.min(1, phaseProgress * 2);
            } else {
                // Converge to center first, then fly to player
                double convergeProgress = Math.min(1, phaseProgress * 2);
                double flyProgress = Math.max(0, (phaseProgress - 0.3) / 0.7);
                flyProgress = easeOutCubic(flyProgress);

                // First converge to helix center
                Location convergePoint = helixCenter.clone();

                // Then interpolate to player
                double x = lerp(convergePoint.getX(), target.getX(), flyProgress);
                double y = lerp(convergePoint.getY(), target.getY(), flyProgress);
                double z = lerp(convergePoint.getZ(), target.getZ(), flyProgress);

                // Add slight orbit during convergence
                if (convergeProgress < 1) {
                    double orbitAngle = hItem.baseAngle + phaseProgress * Math.PI * 4;
                    double orbitRadius = HELIX_RADIUS * 0.5 * (1 - convergeProgress);
                    x += Math.cos(orbitAngle) * orbitRadius * (1 - flyProgress);
                    z += Math.sin(orbitAngle) * orbitRadius * (1 - flyProgress);
                }

                newPos = new Location(world, x, y, z);
                scale = ITEM_SCALE;
            }

            hItem.display.teleport(newPos);

            // Rotation
            hItem.rotation += inHelixPhase ? 0.15f : 0.25f;
            hItem.display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(hItem.rotation, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
            ));

            // Minimal trail - only occasional during helix phase
            if (inHelixPhase && Math.random() < 0.1) {
                world.spawnParticle(
                    Particle.DUST,
                    newPos,
                    1,
                    0.01, 0.01, 0.01,
                    0,
                    new Particle.DustOptions(
                        hItem.index % 2 == 0
                            ? Color.fromRGB(150, 100, 255)
                            : Color.fromRGB(100, 200, 255),
                        0.4f
                    )
                );
            }
        }
    }

    @Override
    protected void onComplete() {
        Location target = getPlayerTargetLocation();
        if (target != null && target.getWorld() != null) {
            World world = target.getWorld();

            // Simple pickup sound with subtle chime
            world.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
            world.playSound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.2f, 1.5f);
        }

        // Cleanup and give items
        for (HelixItem hItem : helixItems) {
            if (!hItem.display.isDead()) {
                hItem.display.remove();
            }

            if (targetPlayer != null && targetPlayer.isOnline()) {
                var leftover = targetPlayer.getInventory().addItem(hItem.item);
                if (!leftover.isEmpty() && target != null) {
                    leftover.values().forEach(item ->
                        target.getWorld().dropItem(target, item)
                    );
                }
            }
        }
        helixItems.clear();
    }

    @Override
    public int getResourceCost() {
        return Math.max(1, items != null ? items.size() + 1 : 2);
    }

    private static class HelixItem {
        final ItemDisplay display;
        final Location startPosition;
        final double baseAngle;
        final double randomOffset;
        final ItemStack item;
        final int index;
        float rotation = 0;

        HelixItem(ItemDisplay display, Location startPosition, double baseAngle, double randomOffset, ItemStack item, int index) {
            this.display = display;
            this.startPosition = startPosition;
            this.baseAngle = baseAngle;
            this.randomOffset = randomOffset;
            this.item = item;
            this.index = index;
        }
    }
}
