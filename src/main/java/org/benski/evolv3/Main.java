package org.benski.evolv3;

import org.benski.evolv3.animations.EssenceAnimation;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.core.managers.FrostbiteBlockManager;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.managers.PickaxeManager;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.benski.evolv3.core.managers.TaskManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.hooks.PlaceholderAPIExpansion;
import org.bukkit.Bukkit;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.CommandRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.EnchantRegistry;
import org.benski.evolv3.registry.ListenerRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.benski.evolv3.registry.ScheduleRegistry;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The main class connecting the plugin together.
 */
public final class Main extends JavaPlugin
{
    /**
     * On enable run these methods.
     */
    @Override
    public void onEnable()
    {
        this.sendConsoleMessage("Evolv3 is starting up...");
        FrostbiteBlockManager.getInstance().initialize(this);
        this.useRegistry();
        this.useMongo();
        this.useHooks();
        // Register the callback provider so enchant selections can be restored after a server restart
        this.registerEnchantCallbackProvider();
        // Load any enchant selections that were in-progress when the server last stopped
        EnchantSelectionManager.getInstance().loadPendingFromDatabase();
        this.sendConsoleMessage("Evolv3 has been enabled successfully!");
    }

    /**
     * On disable run these methods.
     */
    @Override
    public void onDisable()
    {
        this.sendConsoleMessage("Evolv3 is shutting down...");

        // Save all armor profiles before shutdown
        ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
        if (armorDataCenter != null) {
            armorDataCenter.saveAll();
        }

        // Save all player profiles before shutdown
        PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDataCenter != null) {
            playerDataCenter.saveAll();
        }

        // Cancel all scheduled tasks first
        TaskManager.getInstance().cancelAllTasks();

        // Cleanup in reverse order of initialization
        EnchantSelectionManager.getInstance().cleanup();
        ScheduleRegistry.getInstance().cleanup();
        PickupAnimationManager.getInstance().cleanup();
        AnimationRegistry.getInstance().cleanup();
        ListenerRegistry.getInstance().cleanup();
        DataCenterRegistry.getInstance().cleanup();
        MenuRegistry.getInstance().cleanup();
        CommandRegistry.getInstance().cleanup();
        EnchantRegistry.getInstance().cleanup();
        ConfigRegistry.getInstance().cleanup();

        // Close MongoDB connection
        MongoManager.getInstance().close();
        this.sendConsoleMessage("Evolv3 has been disabled. Goodbye!");
    }

    /**
     * Initializes the registry's for evolve.
     */
    private void useRegistry()
    {
        ConfigRegistry.getInstance().initialize();
        WorldGuardManager.getInstance().initialize();
        EnchantRegistry.getInstance().initialize();
        CommandRegistry.getInstance().initialize();
        DataCenterRegistry.getInstance().initialize();
        MenuRegistry.getInstance().initialize();
        ListenerRegistry.getInstance().initialize();
        AnimationRegistry.getInstance().initialize();
        PickupAnimationManager.getInstance().initialize();
        ScheduleRegistry.getInstance().initialize();
    }

    /**
     * Registers the enchant callback provider with {@link EnchantSelectionManager} so that
     * pending enchant selections can be restored after a server restart.
     * Must be called after DataCenters are initialised (i.e. after {@link #useRegistry()}).
     */
    private void registerEnchantCallbackProvider() {
        EnchantSelectionManager.getInstance().registerCallbackProvider(pickaxeKey -> (player, selected) -> {
            var dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
            if (dc == null) return;
            dc.get(player, profile -> {
                if (profile == null) return;
                PickaxeManager.getInstance().applyEnchantFromSelection(player, selected, profile);
            });
        });
    }

    /**
     * Initializes the MongoDB for evolve.
     */
    private void useMongo()
    {
        var defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        MongoManager.getInstance().initConnection(defaultConfig.getString(ConfigPaths.Default.DATABASE_MONGODB));
    }

    /**
     * Initializes plugin hooks (PlaceholderAPI, etc.).
     */
    private void useHooks()
    {
        // Initialize PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }
    }

    /**
     * Static class to get the Main instance.
     * @return A instance of the Main class.
     */
    public static Main getInstance()
    {
        return getPlugin(Main.class);
    }

    /**
     * Gets the blue ANSI color code for console logging.
     * @return The blue color code
     */
    public static String getBlue() {
        return "\u001B[34m";
    }

    /**
     * Gets the ANSI reset code for console logging.
     * @return The reset code
     */
    public static String getReset() {
        return "\u001B[0m";
    }

    /**
     * Sends a console message.
     * @param message The message you want to send.
     */
    public void  sendConsoleMessage(String message) {
        getLogger().info(getBlue() + "========================================" + getReset());
        getLogger().info(getBlue() + message + getReset());
        getLogger().info(getBlue() + "========================================" + getReset());
    }
}
