package org.benski.evolv3.enchants.mining;

import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.enchant.JackhammerAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.AoEDropResolver;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.mine.MineManager;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.core.managers.world.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.passive.FortuneEnchant;
import org.benski.evolv3.listeners.inventory.BackpackListener;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener for the Jackhammer custom enchantment.
 *
 * <p>When triggered, obliterates every breakable block on the entire Y-layer of the mine â€” i.e.
 * all blocks at the same Y coordinate as the block the player just mined, within the mine region's
 * XZ footprint.
 *
 * <ul>
 *   <li>Mine bounds are read from the WorldGuard mine region (if enabled). When WorldGuard is
 *       absent, a configurable {@code MaxScanRadius} is used to limit how far the search extends
 *       from the origin block.
 *   <li>Fortune, Frostbite resolution, and backpack overflow protection are all applied to every
 *       block broken.
 *   <li>A shockwave ring animation radiates outward from the origin to the mine edge to sell the
 *       dramatic effect.
 * </ul>
 */
public class JackhammerEnchant implements Listener {
  @lombok.Getter private static JackhammerEnchant instance;

  private final JavaPlugin plugin;

  /**
   * Creates and registers the singleton JackhammerEnchant listener.
   *
   * @param plugin the owning plugin
   */
  public JackhammerEnchant(JavaPlugin plugin) {
    instance = this;
    this.plugin = plugin;
  }

  /**
   * Triggers a Jackhammer layer break from an external source (e.g. Lazer Rare stone). Uses the
   * player's own Jackhammer enchant data if available (so Jackhammer stones apply), otherwise falls
   * back to a stone-free break.
   */
  public void triggerLayerBreak(Player player, Block origin, Pickaxe pickaxe) {
    EnchantData jh =
        pickaxe.enchants != null ? pickaxe.enchants.getEnchant(Enchants.JACKHAMMER) : null;
    smashLayer(player, origin, pickaxe, jh);
  }

  /** Called when block break occurs. */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    ItemStack item = player.getInventory().getItemInMainHand();

    if (!Utils.isPickaxe(item.getType())) return;
    if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) return;

    String blockName = event.getBlock().getType().name();
    if (blockName.endsWith("_LOG") || blockName.endsWith("_STEM")) return;

    PlayerDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pickaxeDC == null) return;

    Pickaxe pickaxe = pickaxeDC.getPickaxeForPlayer(player);
    if (pickaxe != null && pickaxe.enchants != null) {
      EnchantData jackhammer = pickaxe.enchants.getEnchant(Enchants.JACKHAMMER);
      if (jackhammer != null && jackhammer.enabled && jackhammer.level > 0) {
        double triggerChance = jackhammer.getCurrentChance();
        if (JackhammerStoneHandler.hasStone(jackhammer, "Common")) triggerChance += 1.0;
        if (JackhammerStoneHandler.hasStone(jackhammer, "Uncommon")) triggerChance += 2.0;
        if (Utils.chance(triggerChance)) {
          Block origin = event.getBlock();

          new BukkitRunnable() {
            /** Executes this scheduled task. */
            @Override
            public void run() {
              smashLayer(player, origin, pickaxe, jackhammer);
            }
          }.runTaskLater(plugin, 1L);
        }
      }
    }
  }

  /** Smash layer. */
  private void smashLayer(Player player, Block origin, Pickaxe pickaxe, EnchantData jackhammer) {
    if (!player.isOnline()) return;

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
    int maxScanRadius = config.getInt(ConfigPaths.Enchants.Jackhammer.MAX_SCAN_RADIUS, 100);

    World world = origin.getWorld();
    int targetY = origin.getY();

    boolean hasLegendary =
        jackhammer != null && JackhammerStoneHandler.hasStone(jackhammer, "Legendary");
    boolean hasRare = jackhammer != null && JackhammerStoneHandler.hasStone(jackhammer, "Rare");

    int minX, minZ, maxX, maxZ;
    int[] bounds = WorldGuardManager.getInstance().getMineRegionXZBounds(origin.getLocation());
    if (bounds != null) {
      minX = bounds[0];
      minZ = bounds[1];
      maxX = bounds[2];
      maxZ = bounds[3];
    } else {
      minX = origin.getX() - maxScanRadius;
      minZ = origin.getZ() - maxScanRadius;
      maxX = origin.getX() + maxScanRadius;
      maxZ = origin.getZ() + maxScanRadius;
    }

    int animRadius = computeAnimRadius(origin.getX(), origin.getZ(), minX, minZ, maxX, maxZ);
    EnchantPreferenceResult pref = getEnchantPref(player, config);
    Location originCenter = origin.getLocation().add(0.5, 0.5, 0.5);
    JackhammerAnimation anim =
        new JackhammerAnimation(plugin, animRadius, pref.sound(), pref.particles());
    AnimationRegistry.getInstance().play(anim, originCenter, List.of(player));

    Backpack backpack = playerProfile.backpack;
    if (backpack.isFull()) return;

    int fortuneAmount = resolveFortune(pickaxe);
    int broken = 0;

    int layerCount = 1;
    if (hasLegendary && Utils.chance(30.0)) layerCount = 2;

    AoEDropResolver.startAccumulation(player.getUniqueId());

    outer:
    for (int layer = 0; layer < layerCount; layer++) {
      int layerY = targetY - layer;
      for (int x = minX; x <= maxX; x++) {
        for (int z = minZ; z <= maxZ; z++) {
          int remaining = backpack.getMaxSize() - backpack.getTotalItems();
          if (remaining <= 0) break outer;

          Block block = world.getBlockAt(x, layerY, z);
          if (!AoEDropResolver.isBreakable(block)) continue;
          if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

          Material mat =
              AoEDropResolver.resolve(
                  block, player.getInventory().getItemInMainHand(), plugin, pickaxe);
          if (mat.isAir()) continue;

          MineManager.getInstance().trackBlockAtLocation(block.getLocation());
          block.setType(Material.AIR);

          if (hasRare && Utils.chance(5.0)) {
            pickaxe.atomVessel.current += 1;
            AoEDropResolver.addAccumulatedAtoms(player.getUniqueId(), 1);
            broken++;
            continue;
          }

          int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
          backpack.addItem(player, mat, dropCount, false);
          PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
          AoEDropResolver.rollSideEffects(player, block, pickaxe, playerProfile);
          broken++;
        }
      }
    }

    if (broken > 0) {
      BackpackListener.refresh(player);
      playerProfile.save(null);
    }

    if (jackhammer != null
        && JackhammerStoneHandler.hasStone(jackhammer, "Epic")
        && Utils.chance(50.0)
        && ExplosiveEnchant.getInstance() != null) {
      ExplosiveEnchant.getInstance().triggerBlastCombo(player, origin, pickaxe, playerProfile);
    }

    if (broken > 0) {
      AoEDropResolver.flushAccumulation(player, origin, pickaxe);
      showNotification(player, origin, config, pref, broken);
    }
  }

  /** Returns the distance (rounded up) from the origin to the farthest XZ corner of the region. */
  private int computeAnimRadius(int ox, int oz, int minX, int minZ, int maxX, int maxZ) {
    double d1 = Math.sqrt(Math.pow(ox - minX, 2) + Math.pow(oz - minZ, 2));
    double d2 = Math.sqrt(Math.pow(ox - maxX, 2) + Math.pow(oz - minZ, 2));
    double d3 = Math.sqrt(Math.pow(ox - minX, 2) + Math.pow(oz - maxZ, 2));
    double d4 = Math.sqrt(Math.pow(ox - maxX, 2) + Math.pow(oz - maxZ, 2));
    return (int) Math.ceil(Math.max(Math.max(d1, d2), Math.max(d3, d4)));
  }

  /** Shows notification to the player. */
  private void showNotification(
      Player player,
      Block origin,
      FileConfiguration config,
      EnchantPreferenceResult pref,
      int broken) {
    String brokenStr = String.valueOf(broken);

    if (pref.hologram()
        && config.getBoolean(ConfigPaths.Enchants.Jackhammer.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          config
              .getString(
                  ConfigPaths.Enchants.Jackhammer.HOLOGRAM_MESSAGE,
                  "&#FFAA00&lJACKHAMMER &#CCCCCCSmashed %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      Location hologramLoc = origin.getLocation().add(0.5, 2.5, 0.5);
      AnimationRegistry.getInstance()
          .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
    }

    if (pref.message()) {
      String message =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.JACKHAMMER_TRIGGERED,
                  " &#FFAA00&lJACKHAMMER &#CCCCCCSmashed %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      player.sendMessage(Utils.formatMessage(player, message));
    }

    if (pref.title()) {
      var msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      String title =
          msgCfg.getString(
              ConfigPaths.Messages.Enchants.JACKHAMMER_TRIGGERED_TITLE, "&#FFAA00&lJackhammer");
      String subtitle =
          msgCfg
              .getString(
                  ConfigPaths.Messages.Enchants.JACKHAMMER_TRIGGERED_SUBTITLE,
                  "&#CCCCCCSmashed %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      player.showTitle(
          Title.title(
              LegacyComponentSerializer.legacySection()
                  .deserialize(Utils.formatMessage(player, title)),
              LegacyComponentSerializer.legacySection()
                  .deserialize(Utils.formatMessage(player, subtitle)),
              Title.Times.times(
                  Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(500))));
    }
  }

  /** Resolves fortune. */
  private int resolveFortune(Pickaxe pickaxe) {
    if (pickaxe.enchants == null) return 0;
    EnchantData fortune = pickaxe.enchants.getEnchant(Enchants.FORTUNE);
    if (fortune == null || !fortune.enabled || fortune.level <= 0) return 0;
    return FortuneEnchant.getDropAmount(fortune.level);
  }

  /** Returns the enchant pref. */
  private EnchantPreferenceResult getEnchantPref(Player player, FileConfiguration config) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreferenceResult(false, false, true, true, false);
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreferenceResult(false, false, true, true, false);

    String enchantName = config.getString(ConfigPaths.Enchants.Jackhammer.NAME, "Jackhammer");
    boolean defaultHolo =
        config.getBoolean(ConfigPaths.Enchants.Jackhammer.HOLOGRAM_DEFAULT_ENABLED, false);
    EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

    return new EnchantPreferenceResult(
        pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
  }

  /** Enchant preference result. */
  private record EnchantPreferenceResult(
      boolean hologram, boolean message, boolean sound, boolean particles, boolean title) {}
}
