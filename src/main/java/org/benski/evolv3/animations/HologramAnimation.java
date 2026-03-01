package org.benski.evolv3.animations;

import lombok.Getter;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.benski.evolv3.core.helpers.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Animation for floating hologram text that rises and fades.
 * Uses Paper's TextDisplay entities for clean text rendering.
 *
 * <p>The hologram floats upward over the animation duration,
 * and is only visible to the target player.</p>
 */
public class HologramAnimation extends BaseAnimation
{
    private static final int DEFAULT_DURATION = 60; // 3 seconds
    private static final double RISE_SPEED = 0.03;

    @Getter
    private final String text;
    private Player targetPlayer;
    private TextDisplay textDisplay;
    private double currentY;

    /**
     * Creates a hologram animation with default settings.
     * Required for registry registration.
     *
     * @param plugin the plugin instance
     */
    public HologramAnimation(JavaPlugin plugin) {
        this(plugin, "&d+1 Essence");
    }

    /**
     * Creates a hologram animation with specific text.
     *
     * @param plugin the plugin instance
     * @param text the text to display (supports color codes)
     */
    public HologramAnimation(JavaPlugin plugin, String text) {
        super(plugin, DEFAULT_DURATION);
        this.text = text;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null) return;

        // Get target player from viewers
        var viewers = getViewers();
        if (viewers != null && !viewers.isEmpty()) {
            targetPlayer = viewers.iterator().next();
        }

        if (targetPlayer == null || !targetPlayer.isOnline()) return;

        currentY = origin.getY();
        String formattedText = Utils.formatMessage(targetPlayer, text);

        // Spawn the text display entity
        textDisplay = origin.getWorld().spawn(origin, TextDisplay.class, display -> {
            display.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(formattedText));
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));

            // Make it only visible to the target player
            display.setVisibleByDefault(false);
            targetPlayer.showEntity(plugin, display);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onTick(double progress) {
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            stop();
            return;
        }

        if (textDisplay == null || textDisplay.isDead()) {
            stop();
            return;
        }

        // Float upward
        currentY += RISE_SPEED;
        Location origin = getOrigin();
        if (origin != null) {
            Location newLocation = origin.clone();
            newLocation.setY(currentY);
            textDisplay.teleport(newLocation);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onComplete() {
        if (textDisplay != null && !textDisplay.isDead()) {
            textDisplay.remove();
        }
        textDisplay = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getResourceCost() {
        return 1; // Low cost for simple text display
    }
}
