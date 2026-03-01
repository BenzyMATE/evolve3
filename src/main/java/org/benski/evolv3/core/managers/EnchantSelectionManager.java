package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.EnchantSelectionAnimation;
import org.benski.evolv3.registry.AnimationRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Manages active enchant selection animations.
 * Tracks which players have active selection screens and handles click confirmations.
 */
public class EnchantSelectionManager
{
    @Getter
    private static final EnchantSelectionManager instance = new EnchantSelectionManager();

    private final JavaPlugin plugin;
    private final Map<UUID, EnchantSelectionAnimation> activeAnimations = new HashMap<>();

    private EnchantSelectionManager() {
        this.plugin = Main.getInstance();
    }

    /**
     * Starts an enchant selection animation for a player.
     *⠌
     * @param player       the player to show the selection
     * @param enchantNames the list of enchant options (should be 3)
     * @param onSelect     callback when an enchant is selected
     * @return true if the animation started successfully
     */
    public boolean startSelection(Player player, List<String> enchantNames, BiConsumer<Player, String> onSelect) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        // Cancel any existing animation for this player
        if (hasActiveSelection(player)) {
            cancelSelection(player);
        }

        // Create the animation with a wrapper callback that cleans up
        BiConsumer<Player, String> wrappedCallback = (p, selected) -> {
            activeAnimations.remove(p.getUniqueId());
            if (onSelect != null) {
                onSelect.accept(p, selected);
            }
        };

        EnchantSelectionAnimation animation = new EnchantSelectionAnimation(
            plugin,
            player,
            enchantNames,
            wrappedCallback
        );

        // Check if we can play within budget
        if (!AnimationRegistry.getInstance().canPlay(animation)) {
            return false;
        }

        // Store the animation reference
        activeAnimations.put(player.getUniqueId(), animation);

        // Play the animation at the player's location
        Location loc = player.getLocation().add(0, 0.5, 0);
        AnimationRegistry.getInstance().play(animation, loc);

        return true;
    }

    /**
     * Confirms the currently highlighted selection for a player.
     *
     * @param player the player
     * @return true if a selection was confirmed
     */
    public boolean confirmSelection(Player player) {
        if (player == null) {
            return false;
        }

        EnchantSelectionAnimation animation = activeAnimations.get(player.getUniqueId());
        if (animation == null) {
            return false;
        }

        animation.confirmSelection();
        return true;
    }

    /**
     * Cancels an active selection animation for a player.
     *
     * @param player the player
     */
    public void cancelSelection(Player player) {
        if (player == null) {
            return;
        }

        EnchantSelectionAnimation animation = activeAnimations.remove(player.getUniqueId());
        if (animation != null && animation.isPlaying()) {
            animation.stop();
        }
    }

    /**
     * Checks if a player has an active selection animation.
     *
     * @param player the player
     * @return true if they have an active selection
     */
    public boolean hasActiveSelection(Player player) {
        if (player == null) {
            return false;
        }

        EnchantSelectionAnimation animation = activeAnimations.get(player.getUniqueId());
        return animation != null && animation.isPlaying();
    }

    /**
     * Gets the active animation for a player.
     *
     * @param player the player
     * @return the animation, or null if none active
     */
    public EnchantSelectionAnimation getActiveAnimation(Player player) {
        if (player == null) {
            return null;
        }
        return activeAnimations.get(player.getUniqueId());
    }

    /**
     * Cleans up all active animations.
     * Called when the plugin is disabled.
     */
    public void cleanup() {
        for (EnchantSelectionAnimation animation : activeAnimations.values()) {
            if (animation != null && animation.isPlaying()) {
                animation.stop();
            }
        }
        activeAnimations.clear();
    }
}
