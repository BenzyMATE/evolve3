package org.benski.evolv3;

import org.benski.evolv3.api.EvolveApiClient;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantSelectionManager;
import org.benski.evolv3.core.managers.enchant.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.mine.FrostbiteBlockManager;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.core.managers.player.PickupAnimationManager;
import org.benski.evolv3.core.managers.world.TaskManager;
import org.benski.evolv3.core.managers.world.WorldGuardManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.hooks.PlaceholderAPIExpansion;
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
    return "[34m";
  }

  /**
   * Gets the ANSI reset code for console logging.
   *
   * @return The reset code
   */
  public static String getReset() {
    return "[0m";
  }

  /** Called when the plugin enables. */
  @Override
  public void onEnable() {
    this.sendConsoleMessage("Evolv3 is starting up...");
    FrostbiteBlockManager.getInstance().initialize();
    this.useRegistry();
    this.useApi();
    this.useHooks();
    this.registerCallbackProviders();
    ManagerRegistry.getInstance().initialize();
    Bukkit.getScheduler().runTask(this, this::clearAllMobs);
    this.sendConsoleMessage("Evolv3 has been enabled successfully!");
  }

  /** Called when the plugin disables. */
  @Override
  public void onDisable() {
    this.sendConsoleMessage("Evolv3 is shutting down...");
    clearAllMobs();
    this.saveAllProfiles();
    TaskManager.getInstance().cancelAllTasks();
    ManagerRegistry.getInstance().cleanup();
    this.cleanupRegistries();
    if (EvolveApiClient.getInstance() != null) {
      EvolveApiClient.getInstance().shutdown();
    }
    this.sendConsoleMessage("Evolv3 has been disabled. Goodbye!");
  }

  /** Saves all profiles. */
  private void saveAllProfiles() {
    ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
    if (armorDataCenter != null) {
      armorDataCenter.saveAll();
    }
    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter != null) {
      playerDataCenter.saveAll();
    }
  }

  /** Use registry. */
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
   * Registers callback providers for {@link EnchantSelectionManager} and {@link
   * EvolutionSelectionManager} so that pending selections can be restored after a server restart.
   * Must be called after DataCenters are initialised (i.e. after {@link #useRegistry()}).
   */
  private void registerCallbackProviders() {
    EnchantSelectionManager.getInstance()
        .registerCallbackProvider(
            pickaxeKey ->
                (player, selected) -> {
                  var dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
                  if (dc == null) return;
                  var profile = dc.getPickaxeForPlayer(player);
                  if (profile == null) return;
                  PickaxeManager.getInstance().applyEnchantFromSelection(player, selected, profile);
                });
    EvolutionSelectionManager.getInstance()
        .registerCallbackProvider(
            (player, pickaxeKey, nextTier, winningRarity) ->
                () -> {
                  var playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
                  if (playerDC == null) return;
                  var profile = playerDC.getPickaxeForPlayer(player);
                  if (profile == null) return;
                  var playerProfile = playerDC.getLocal(player);
                  if (playerProfile == null) return;
                  PickaxeManager.getInstance()
                      .applyEvolutionResult(
                          player, profile, playerProfile, nextTier, winningRarity);
                });
  }

  /** Use api. */
  private void useApi() {
    var defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    String apiUrl =
        defaultConfig.getString(
            ConfigPaths.Default.API_URL,
            "https://evolvemc-d2bjaqeceugth0bw.ukwest-01.azurewebsites.net");
    boolean sslVerify = defaultConfig.getBoolean(ConfigPaths.Default.API_SSL_VERIFY, true);
    new EvolveApiClient(apiUrl, sslVerify);
  }

  /** Use hooks. */
  private void useHooks() {
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      new PlaceholderAPIExpansion(this).register();
      getLogger().info("PlaceholderAPI expansion registered.");
    }
  }

  /** Cleans up registries in reverse order of initialization. */
  private void cleanupRegistries() {
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
