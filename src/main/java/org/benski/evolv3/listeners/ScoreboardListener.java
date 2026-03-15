package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.EvolveScoreboardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages scoreboard lifecycle: sets up the sidebar on join and tears it down on quit.
 * Also initialises {@link EvolveScoreboardManager} and starts its update task.
 */
public class ScoreboardListener extends BaseListener
{
    private final EvolveScoreboardManager scoreboardManager = EvolveScoreboardManager.getInstance();

    public ScoreboardListener(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        scoreboardManager.initialize(plugin);
    }

    @Override
    public void cleanup() {
        scoreboardManager.cleanup();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Delay setup by 1 tick so player and data center state are fully initialised
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                scoreboardManager.setup(event.getPlayer());
                scoreboardManager.update(event.getPlayer());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        scoreboardManager.teardown(event.getPlayer());
    }
}
