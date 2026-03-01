package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles player join and quit events.
 * Responsible for loading and unloading player profiles.
 */
public class JoinListener extends BaseListener
{
    /**
     * Constructor for JoinListener.
     *
     * @param plugin The plugin instance.
     */
    public JoinListener(JavaPlugin plugin)
    {
        super(plugin);
    }

    /**
     * Handles player join events.
     * Loads the player's profile from the database or creates a new one.
     *
     * @param event The player join event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinEvent(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();

        PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();

        if (playerDataCenter != null) {
            playerDataCenter.loadOrCreate(player, playerId);
        }

        if (armorDataCenter != null) {
            armorDataCenter.loadOrCreate(player, playerId);
        }

        // Restore any in-progress enchant selection (same options, pickaxe returned)
        EnchantSelectionManager.getInstance().handlePlayerJoin(player);
    }

    /**
     * Handles player quit events.
     * Saves and unloads the player's profile from cache.
     *
     * @param event The player quit event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitEvent(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();

        // Save enchant animation state first, while the player is still technically online
        EnchantSelectionManager.getInstance().handlePlayerQuit(player);

        ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
        PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();

        if (armorDataCenter != null) {
            armorDataCenter.unloadProfile(player);
        }

        if (playerDataCenter != null) {
            playerDataCenter.unloadProfile(player);
        }
    }
}
