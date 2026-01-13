package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class OnJoinListener extends BaseListener {

    /**
     * Constructor for BaseListener.
     *
     * @param plugin The plugin instance.
     */
    public OnJoinListener(JavaPlugin plugin) {
        super(plugin);
    }

    /**
     * When a player joins the server.
     * @param event The event that holds player data.
     */
    @EventHandler
    public void onJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("Welcome to the server");
    }
}
