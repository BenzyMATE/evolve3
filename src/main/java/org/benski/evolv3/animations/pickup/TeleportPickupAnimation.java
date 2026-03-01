package org.benski.evolv3.animations.pickup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Teleport pickup animation.
 * Items and XP text shrink and float upward before disappearing.
 * Clean, minimal animation for quick mining feedback.
 */
public class TeleportPickupAnimation extends BasePickupAnimation
{
    private static final int DEFAULT_DURATION = 15;
    private static final float ITEM_SCALE = 0.45f;
    private static final float TEXT_SCALE = 0.5f;
    private static final double TEXT_OFFSET_Y = 0.4;

    private final List<TeleportItem> teleportItems = new ArrayList<>();

    public TeleportPickupAnimation(JavaPlugin plugin) {
        super(plugin, DEFAULT_DURATION);
    }

    @Override
    public String getAnimationId() {
        return "teleport";
    }

    @Override
    public String getDisplayName() {
        return "Teleport";
    }

    @Override
    public String getDescription() {
        return "Items phase out and teleport directly to you";
    }

    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null || targetPlayer == null) return;

        World world = origin.getWorld();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            // Spawn exactly at block center
            Location spawnLoc = origin.clone();

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
                entity.setGlowing(true);

                Collection<Player> viewers = getViewers();
                if (viewers != null) {
                    entity.setVisibleByDefault(false);
                    for (Player viewer : viewers) {
                        viewer.showEntity(plugin, entity);
                    }
                }
            });

            // Create text display showing XP gain
            int xpAmount = item.getAmount(); // TODO: Calculate actual XP from pickaxe level system
            String itemText = "+" + xpAmount + " XP";
            Location textLoc = spawnLoc.clone().add(0, TEXT_OFFSET_Y, 0);

            TextDisplay textDisplay = world.spawn(textLoc, TextDisplay.class, entity -> {
                entity.text(Component.text(itemText)
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD));
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                entity.setPersistent(false);
                entity.setBillboard(TextDisplay.Billboard.CENTER);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setShadowed(true);

                Collection<Player> viewers = getViewers();
                if (viewers != null) {
                    entity.setVisibleByDefault(false);
                    for (Player viewer : viewers) {
                        viewer.showEntity(plugin, entity);
                    }
                }
            });

            teleportItems.add(new TeleportItem(
                display,
                textDisplay,
                spawnLoc.clone(),
                item
            ));
        }
    }

    @Override
    protected void onTick(double progress) {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null) return;

        // Simple shrink and fade animation
        double easedProgress = easeInOutCubic(progress);

        for (TeleportItem tItem : teleportItems) {
            if (tItem.display.isDead()) continue;

            // Shrink both item and text
            float scale = ITEM_SCALE * (float) (1 - easedProgress);
            float textScale = TEXT_SCALE * (float) (1 - easedProgress);

            // Slight upward float
            Location floatPos = tItem.startPosition.clone().add(0, easedProgress * 0.6, 0);
            tItem.display.teleport(floatPos);

            // Text follows above item
            if (!tItem.textDisplay.isDead()) {
                Location textPos = floatPos.clone().add(0, TEXT_OFFSET_Y * (1 - easedProgress * 0.3), 0);
                tItem.textDisplay.teleport(textPos);
                tItem.textDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(textScale, textScale, textScale),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
            }

            // Rotate item
            tItem.rotation += 0.15f;
            tItem.display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(tItem.rotation, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
            ));
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

        // Cleanup and give items
        for (TeleportItem tItem : teleportItems) {
            if (!tItem.display.isDead()) {
                tItem.display.remove();
            }
            if (!tItem.textDisplay.isDead()) {
                tItem.textDisplay.remove();
            }

            if (targetPlayer != null && targetPlayer.isOnline()) {
                var leftover = targetPlayer.getInventory().addItem(tItem.item);
                if (!leftover.isEmpty() && target != null) {
                    leftover.values().forEach(item ->
                        target.getWorld().dropItem(target, item)
                    );
                }
            }
        }
        teleportItems.clear();
    }

    @Override
    public int getResourceCost() {
        // Each item has an ItemDisplay + TextDisplay
        return Math.max(2, items != null ? items.size() * 2 : 2);
    }

    private static class TeleportItem {
        final ItemDisplay display;
        final TextDisplay textDisplay;
        final Location startPosition;
        final ItemStack item;
        float rotation = 0;

        TeleportItem(ItemDisplay display, TextDisplay textDisplay, Location startPosition, ItemStack item) {
            this.display = display;
            this.textDisplay = textDisplay;
            this.startPosition = startPosition;
            this.item = item;
        }
    }
}
