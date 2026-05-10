package org.benski.evolv3;

import org.benski.evolv3.animations.EssenceAnimation;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.core.managers.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.BeamManager;
import org.benski.evolv3.core.managers.BlackholeHologramManager;
import org.benski.evolv3.core.managers.BlackholeManager;
import org.benski.evolv3.core.managers.CellBlockBackupManager;
import org.benski.evolv3.core.managers.CellHologramManager;
import org.benski.evolv3.core.managers.CellManager;
import org.benski.evolv3.core.managers.CellRentManager;
import org.benski.evolv3.core.managers.CellWorldGuardManager;
import org.benski.evolv3.core.managers.IslandManager;
import org.benski.evolv3.core.managers.IslandWorldManager;
import org.benski.evolv3.menus.CellAbandonConfirmMenu;
import org.benski.evolv3.menus.CellEvolveConfirmMenu;
import org.benski.evolv3.menus.CellMemberMenu;
import org.benski.evolv3.menus.CellMemberPermMenu;
import org.benski.evolv3.menus.CellMenu;
import org.benski.evolv3.menus.CellRentInfoMenu;
import org.benski.evolv3.menus.IslandMenu;
import org.benski.evolv3.menus.IslandsMenu;
import org.benski.evolv3.core.managers.BorderHologramManager;
import org.benski.evolv3.core.managers.BorderManager;
import org.benski.evolv3.core.managers.CaveManager;
import org.benski.evolv3.core.managers.LaunchPadManager;
import org.benski.evolv3.core.managers.MineHologramManager;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.SpawnerHologramManager;
import org.benski.evolv3.core.managers.SpawnerManager;
import org.benski.evolv3.core.managers.FrostbiteBlockManager;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.benski.evolv3.core.managers.TaskManager;
import org.benski.evolv3.core.managers.OmnitoolManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.core.helpers.Utils;
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
        // Load blackholes
        BlackholeManager.getInstance().initialize();
        BlackholeHologramManager.getInstance().initialize();
        // Load zone borders from borders.yml
        BorderManager.getInstance().loadFromConfig();
        // Load levitation beams from beams.yml
        BeamManager.getInstance().loadFromConfig();
        // Load launch pads from launchpads.yml
        LaunchPadManager.getInstance().loadFromConfig();
        // Extract cave schematic if not present
        this.extractSchematic("cave.schem");
        // Load mines from mines.yml and cave data
        CaveManager.getInstance().loadCaveData();
        MineManager.getInstance().loadFromConfig();
        // Start border holograms after borders are already loaded
        BorderHologramManager.getInstance().initialize();
        // Load custom spawners
        SpawnerManager.getInstance().initialize();
        SpawnerHologramManager.getInstance().initialize();
        // Register the callback provider so enchant selections can be restored after a server restart
        this.registerEnchantCallbackProvider();
        // Load any enchant selections that were in-progress when the server last stopped
        EnchantSelectionManager.getInstance().loadPendingFromDatabase();
        // Register the callback provider so evolution animations can be restored after a server restart
        this.registerEvolutionCallbackProvider();
        // Load any evolution animations that were in-progress when the server last stopped
        EvolutionSelectionManager.getInstance().loadPendingFromDatabase();
        // Initialize cell and island system
        CellWorldGuardManager.getInstance().initialize();
        CellManager.getInstance().initialize();
        CellRentManager.getInstance().initialize();
        IslandManager.getInstance().initialize();
        IslandWorldManager.getInstance().initialize();
        CellHologramManager.getInstance().initialize();
        new CellMenu(this);
        new CellEvolveConfirmMenu(this);
        new CellAbandonConfirmMenu(this);
        new CellRentInfoMenu(this);
        new CellMemberMenu(this);
        new CellMemberPermMenu(this);
        new IslandMenu(this);
        new IslandsMenu(this);
        // Kill all mobs (including passive animals) left over from before the restart
        Bukkit.getScheduler().runTask(this, this::clearAllMobs);
        this.sendConsoleMessage("Evolv3 has been enabled successfully!");
    }

    /**
     * On disable run these methods.
     */
    @Override
    public void onDisable()
    {
        this.sendConsoleMessage("Evolv3 is shutting down...");
        clearAllMobs();

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

        // Save all pickaxe profiles before shutdown
        PickaxeDataCenter pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDataCenter != null) {
            pickaxeDataCenter.saveAll();
        }

        // Revert any active omnitool players before shutting down
        OmnitoolManager.getInstance().cleanup();

        // Cleanup cell and island system
        CellHologramManager.getInstance().cleanup();
        CellRentManager.getInstance().cleanup();
        IslandWorldManager.getInstance().cleanup();
        IslandManager.getInstance().cleanup();
        CellManager.getInstance().cleanup();
        // Remove mine, spawner, and border holograms before tasks are cancelled
        BlackholeHologramManager.getInstance().cleanup();
        BlackholeManager.getInstance().cleanup();
        MineHologramManager.getInstance().cleanup();
        SpawnerHologramManager.getInstance().cleanup();
        BorderHologramManager.getInstance().cleanup();
        SpawnerManager.getInstance().cleanup();

        // Cancel all scheduled tasks first
        TaskManager.getInstance().cancelAllTasks();

        // Cleanup in reverse order of initialization
        EvolutionSelectionManager.getInstance().cleanup();
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
        Utils.loadProgressBarSymbols();
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
     * Registers the evolution callback provider with {@link EvolutionSelectionManager} so that
     * pending evolution animations can be restored after a server restart.
     * Must be called after DataCenters are initialised (i.e. after {@link #useRegistry()}).
     */
    private void registerEvolutionCallbackProvider() {
        EvolutionSelectionManager.getInstance().registerCallbackProvider(
                (player, pickaxeKey, nextTier, winningRarity) -> () -> {
                    var dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
                    var playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
                    if (dc == null || playerDC == null) return;
                    dc.get(player, profile -> {
                        if (profile == null) return;
                        var playerProfile = playerDC.getLocal(player);
                        if (playerProfile == null) return;
                        PickaxeManager.getInstance().applyEvolutionResult(
                                player, profile, playerProfile, nextTier, winningRarity);
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
     * Extracts a schematic from the plugin jar's resources/schematics/ directory
     * into the plugin data folder (plugins/Evolv3/schematics/) if it doesn't already exist.
     *
     * @param fileName the schematic file name (e.g. "cave.schem")
     */
    private void extractSchematic(String fileName)
    {
        java.io.File schematicsDir = new java.io.File(getDataFolder(), "schematics");
        if (!schematicsDir.exists()) schematicsDir.mkdirs();

        java.io.File target = new java.io.File(schematicsDir, fileName);
        if (target.exists()) return;

        // Try both casings — JAR resources are case-sensitive and the folder
        // may be stored as "schematics/" or "Schematics/" depending on the build OS.
        java.io.InputStream stream = getResource("schematics/" + fileName);
        if (stream == null) stream = getResource("Schematics/" + fileName);

        if (stream == null)
        {
            getLogger().warning("Schematic resource not found in JAR: " + fileName);
            return;
        }

        try (java.io.InputStream in = stream)
        {
            java.nio.file.Files.copy(in, target.toPath());
            getLogger().info("Extracted schematic: " + fileName);
        }
        catch (java.io.IOException e)
        {
            getLogger().warning("Failed to extract schematic " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Static class to get the Main instance.
     * @return A instance of the Main class.
     */
    /** Removes every living entity (mobs and animals) from all worlds, leaving only players. */
    private void clearAllMobs()
    {
        int killed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.LivingEntity
                        && !(entity instanceof org.bukkit.entity.Player)) {
                    entity.remove();
                    killed++;
                }
            }
        }
        getLogger().info("[Evolv3] Cleared " + killed + " entities.");
    }

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
    public void sendConsoleMessage(String message) {
        getLogger().info(getBlue() + "========================================" + getReset());
        getLogger().info(getBlue() + message + getReset());
        getLogger().info(getBlue() + "========================================" + getReset());
    }
}
