package org.benski.evolv3;

import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.*;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.hooks.PlaceholderAPIExpansion;
import org.benski.evolv3.menus.*;
import org.benski.evolv3.registry.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** The main class connecting the plugin together. */
public final class Main extends JavaPlugin {
  /**
   * Returns the active plugin instance.
   *
   * @return the Main plugin instance
   */
  public static Main getInstance() {
    return getPlugin(Main.class);
  }

  /**
   * Gets the blue ANSI color code for console logging.
   *
   * @return The blue color code
   */
  public static String getBlue() {
    return "\u001B[34m";
  }

  /**
   * Gets the ANSI reset code for console logging.
   *
   * @return The reset code
   */
  public static String getReset() {
    return "\u001B[0m";
  }

  @Override
  public void onEnable() {
    this.sendConsoleMessage("Evolv3 is starting up...");
    FrostbiteBlockManager.getInstance().initialize(this);
    this.useRegistry();
    this.useMongo();
    this.useHooks();
    this.initializeBlackholes();
    this.initializeZoneBorders();
    BeamManager.getInstance().loadFromConfig();
    LaunchPadManager.getInstance().loadFromConfig();
    this.initializeMines();
    this.initializeSpawners();
    this.restoreEnchantSelections();
    this.restoreEvolutionSelections();
    this.initializeCells();
    Bukkit.getScheduler().runTask(this, this::clearAllMobs);
    this.sendConsoleMessage("Evolv3 has been enabled successfully!");
  }

  @Override
  public void onDisable() {
    this.sendConsoleMessage("Evolv3 is shutting down...");
    clearAllMobs();
    this.saveAllProfiles();
    OmnitoolManager.getInstance().cleanup();
    this.cleanupCells();
    this.cleanupHolograms();
    TaskManager.getInstance().cancelAllTasks();
    this.cleanupRegistries();
    MongoManager.getInstance().close();
    this.sendConsoleMessage("Evolv3 has been disabled. Goodbye!");
  }

  private void saveAllProfiles() {
    ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
    if (armorDataCenter != null) {
      armorDataCenter.saveAll();
    }
    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter != null) {
      playerDataCenter.saveAll();
    }
    PickaxeDataCenter pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    if (pickaxeDataCenter != null) {
      pickaxeDataCenter.saveAll();
    }
  }

  private void cleanupCells() {
    CellHologramManager.getInstance().cleanup();
    CellRentManager.getInstance().cleanup();
    IslandWorldManager.getInstance().cleanup();
    IslandManager.getInstance().cleanup();
    CellManager.getInstance().cleanup();
  }

  private void cleanupHolograms() {
    BlackholeHologramManager.getInstance().cleanup();
    BlackholeManager.getInstance().cleanup();
    MineHologramManager.getInstance().cleanup();
    SpawnerHologramManager.getInstance().cleanup();
    BorderHologramManager.getInstance().cleanup();
    SpawnerManager.getInstance().cleanup();
  }

  /** Cleans up registries in reverse order of initialization. */
  private void cleanupRegistries() {
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
  }

  private void initializeBlackholes() {
    BlackholeManager.getInstance().initialize();
    BlackholeHologramManager.getInstance().initialize();
  }

  /** Initializes zone borders, then holograms (holograms require borders loaded first). */
  private void initializeZoneBorders() {
    BorderManager.getInstance().loadFromConfig();
    BorderHologramManager.getInstance().initialize();
  }

  private void initializeMines() {
    this.extractSchematic("cave.schem");
    CaveManager.getInstance().loadCaveData();
    MineManager.getInstance().loadFromConfig();
  }

  private void initializeSpawners() {
    SpawnerManager.getInstance().initialize();
    SpawnerHologramManager.getInstance().initialize();
  }

  /** Registers the callback provider then restores any in-progress enchant selections. */
  private void restoreEnchantSelections() {
    this.registerEnchantCallbackProvider();
    EnchantSelectionManager.getInstance().loadPendingFromDatabase();
  }

  /** Registers the callback provider then restores any in-progress evolution animations. */
  private void restoreEvolutionSelections() {
    this.registerEvolutionCallbackProvider();
    EvolutionSelectionManager.getInstance().loadPendingFromDatabase();
  }

  private void initializeCells() {
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
  }

  private void useRegistry() {
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
   * Registers the enchant callback provider with {@link EnchantSelectionManager} so that pending
   * enchant selections can be restored after a server restart. Must be called after DataCenters are
   * initialised (i.e. after {@link #useRegistry()}).
   */
  private void registerEnchantCallbackProvider() {
    EnchantSelectionManager.getInstance()
        .registerCallbackProvider(
            pickaxeKey ->
                (player, selected) -> {
                  var dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
                  if (dc == null) return;
                  dc.get(
                      player,
                      profile -> {
                        if (profile == null) return;
                        PickaxeManager.getInstance()
                            .applyEnchantFromSelection(player, selected, profile);
                      });
                });
  }

  /**
   * Registers the evolution callback provider with {@link EvolutionSelectionManager} so that
   * pending evolution animations can be restored after a server restart. Must be called after
   * DataCenters are initialised (i.e. after {@link #useRegistry()}).
   */
  private void registerEvolutionCallbackProvider() {
    EvolutionSelectionManager.getInstance()
        .registerCallbackProvider(
            (player, pickaxeKey, nextTier, winningRarity) ->
                () -> {
                  var dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
                  var playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
                  if (dc == null || playerDC == null) return;
                  dc.get(
                      player,
                      profile -> {
                        if (profile == null) return;
                        var playerProfile = playerDC.getLocal(player);
                        if (playerProfile == null) return;
                        PickaxeManager.getInstance()
                            .applyEvolutionResult(
                                player, profile, playerProfile, nextTier, winningRarity);
                      });
                });
  }

  private void useMongo() {
    var defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    MongoManager.getInstance()
        .initConnection(defaultConfig.getString(ConfigPaths.Default.DATABASE_MONGODB));
  }

  private void useHooks() {
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      new PlaceholderAPIExpansion(this).register();
      getLogger().info("PlaceholderAPI expansion registered.");
    }
  }

  /**
   * Extracts a schematic from the plugin jar's resources/schematics/ directory into the plugin data
   * folder (plugins/Evolv3/schematics/) if it doesn't already exist.
   *
   * @param fileName the schematic file name (e.g. "cave.schem")
   */
  private void extractSchematic(String fileName) {
    java.io.File schematicsDir = new java.io.File(getDataFolder(), "schematics");
    if (!schematicsDir.exists()) schematicsDir.mkdirs();

    java.io.File target = new java.io.File(schematicsDir, fileName);
    if (target.exists()) return;

    java.io.InputStream stream = findSchematicResourceCaseInsensitive(fileName);

    if (stream == null) {
      getLogger().warning("Schematic resource not found in JAR: " + fileName);
      return;
    }

    try (java.io.InputStream in = stream) {
      java.nio.file.Files.copy(in, target.toPath());
      getLogger().info("Extracted schematic: " + fileName);
    } catch (java.io.IOException e) {
      getLogger().warning("Failed to extract schematic " + fileName + ": " + e.getMessage());
    }
  }

  /**
   * Resolves a schematic resource from either "schematics/" or "Schematics/" — JAR resources are
   * case-sensitive and the folder casing varies by build OS.
   */
  private java.io.InputStream findSchematicResourceCaseInsensitive(String fileName) {
    java.io.InputStream stream = getResource("schematics/" + fileName);
    return stream != null ? stream : getResource("Schematics/" + fileName);
  }

  /** Removes every living entity except players from all worlds. */
  private void clearAllMobs() {
    int cleared = 0;
    for (org.bukkit.World world : Bukkit.getWorlds()) {
      for (org.bukkit.entity.Entity entity : world.getEntities()) {
        if (entity instanceof org.bukkit.entity.LivingEntity mob
            && !(mob instanceof org.bukkit.entity.Player)) {
          mob.remove();
          cleared++;
        }
      }
    }
    getLogger().info("[Evolv3] Cleared " + cleared + " entities.");
  }

  /**
   * Sends a console message.
   *
   * @param message The message you want to send.
   */
  public void sendConsoleMessage(String message) {
    getLogger().info(getBlue() + "========================================" + getReset());
    getLogger().info(getBlue() + message + getReset());
    getLogger().info(getBlue() + "========================================" + getReset());
  }
}
