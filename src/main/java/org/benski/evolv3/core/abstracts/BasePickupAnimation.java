package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.core.types.PreferenceType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Base class for pickup animations.
 * Provides functionality specific to item pickup effects when mining blocks.
 *
 * <p>Pickup animations display dropped items visually traveling to the player
 * before being added to their inventory, replacing the default pickup behavior.</p>
 */
public abstract class BasePickupAnimation extends BaseAnimation
{
    protected Player targetPlayer;
    protected List<ItemStack> items;
    protected Material blockMaterial;
    protected PreferenceType.PickupAnimation style       = PreferenceType.PickupAnimation.NORMAL;
    protected PreferenceType.DropCategory   dropCategory = PreferenceType.DropCategory.NORMAL;

    /** Whether to apply a coloured glow outline to the floating item display. */
    protected boolean prefGlow      = true;
    /** Whether to spawn category-specific particles during the float/dissolve phases. */
    protected boolean prefParticles = true;

    /**
     * Constructor for BasePickupAnimation.
     *
     * @param plugin the plugin instance
     * @param duration the animation duration in ticks
     */
    public BasePickupAnimation(JavaPlugin plugin, int duration) {
        super(plugin, duration);
    }

    /**
     * Initializes the pickup animation with item and player data.
     *
     * @param player the player who will receive the items
     * @param items the items to animate
     * @param blockMaterial the material of the block that was broken
     */
    public void initialize(Player player, List<ItemStack> items, Material blockMaterial) {
        this.targetPlayer = player;
        this.items = items;
        this.blockMaterial = blockMaterial;
    }

    /**
     * Sets the visual style for this pickup animation.
     * Must be called before {@link #play} is invoked.
     */
    public void setStyle(PreferenceType.PickupAnimation style) {
        this.style = style;
    }

    /**
     * Sets the drop category (NORMAL / ESSENCE / ATOM) to drive colour and particle effects.
     * Must be called before {@link #play} is invoked.
     */
    public void setDropCategory(PreferenceType.DropCategory category) {
        this.dropCategory = category;
    }

    /** Controls whether a coloured glow outline is applied to the floating item display. */
    public void setPrefGlow(boolean prefGlow) {
        this.prefGlow = prefGlow;
    }

    /** Controls whether category-specific particles spawn during the float/dissolve phases. */
    public void setPrefParticles(boolean prefParticles) {
        this.prefParticles = prefParticles;
    }

    /**
     * Gets the target player for this animation.
     *
     * @return the target player
     */
    public Player getTargetPlayer() {
        return targetPlayer;
    }

    /**
     * Gets the items being animated.
     *
     * @return the items
     */
    public List<ItemStack> getItems() {
        return items;
    }

    /**
     * Gets the block material that was broken.
     *
     * @return the block material
     */
    public Material getBlockMaterial() {
        return blockMaterial;
    }

    /**
     * Gets the unique identifier for this animation type.
     * Used for player preference storage.
     *
     * @return the animation identifier
     */
    public abstract String getAnimationId();

    /**
     * Gets a display name for this animation.
     *
     * @return the display name
     */
    public abstract String getDisplayName();

    /**
     * Gets a description of this animation.
     *
     * @return the description
     */
    public abstract String getDescription();

    /**
     * Gets the current location of the target player's eye level.
     * Used as the destination for item animations.
     *
     * @return the player's eye location, or null if player is invalid
     */
    protected Location getPlayerTargetLocation() {
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            return null;
        }
        return targetPlayer.getLocation().add(0, 0.5, 0);
    }

    /**
     * Applies easing function for smooth animation.
     * Uses ease-out cubic for a natural deceleration effect.
     *
     * @param t the linear progress (0.0 to 1.0)
     * @return the eased progress
     */
    protected double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    /**
     * Applies ease-in-out cubic for smooth acceleration and deceleration.
     *
     * @param t the linear progress (0.0 to 1.0)
     * @return the eased progress
     */
    protected double easeInOutCubic(double t) {
        return t < 0.5
            ? 4 * t * t * t
            : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /**
     * Applies ease-out elastic for a bouncy effect.
     *
     * @param t the linear progress (0.0 to 1.0)
     * @return the eased progress
     */
    protected double easeOutElastic(double t) {
        if (t == 0 || t == 1) return t;
        double c4 = (2 * Math.PI) / 3;
        return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
    }

    /**
     * Applies ease-out back for an overshoot effect.
     *
     * @param t the linear progress (0.0 to 1.0)
     * @return the eased progress
     */
    protected double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    /**
     * Linear interpolation between two values.
     *
     * @param start the start value
     * @param end the end value
     * @param t the interpolation factor (0.0 to 1.0)
     * @return the interpolated value
     */
    protected double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }
}
