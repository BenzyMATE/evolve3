package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener that handles player interactions during enchant selection animations.
 * Detects left-clicks to confirm the highlighted enchant choice.
 */
public class EnchantSelectionListener extends BaseListener
{
    public EnchantSelectionListener(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        plugin.getLogger().info("Enchant Selection Listener initialized");
    }

    /**
     * Handles player interaction to confirm enchant selection.
     * Left-click (air or block) confirms the currently highlighted book.
     *
     * @param event the player interact event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Only respond to left-clicks
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        // Check if player has an active selection
        EnchantSelectionManager manager = EnchantSelectionManager.getInstance();
        if (!manager.hasActiveSelection(player)) {
            return;
        }

        // Confirm the selection
        if (manager.confirmSelection(player)) {
            // Cancel the event to prevent breaking blocks during selection
            event.setCancelled(true);
        }
    }

    /**
     * Handles player disconnect to clean up any active animations.
     *
     * @param event the player quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel any active selection when player disconnects
        EnchantSelectionManager manager = EnchantSelectionManager.getInstance();
        if (manager.hasActiveSelection(player)) {
            manager.cancelSelection(player);
        }
    }

    @Override
    public void cleanup() {
        plugin.getLogger().info("Enchant Selection Listener cleaned up");
    }
}
