package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class OnBreakListener extends BaseListener {

    /**
     * Constructor for BaseListener.
     *
     * @param plugin The plugin instance.
     */
    public OnBreakListener(JavaPlugin plugin) {
        super(plugin);
    }

    /**
     * When a player joins the server.
     * @param event The event that holds player data.
     */
    @EventHandler
    public void OnBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("Welcome to the server");
    }
}
