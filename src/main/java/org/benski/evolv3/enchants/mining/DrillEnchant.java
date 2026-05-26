package org.benski.evolv3.enchants.mining;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.enchant.DrillAnimation;
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
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
 * Listener for the Drill custom enchantment.
 *
 * <p>When triggered, bores a 2Ã—2 shaft straight down one level at a time. Each level is broken,
 * then its shard animation plays, then after a short delay the next level fires â€” so the drilling
 * propagates visually downward. Depth is randomised to 2, 3, or 4 levels per trigger (capped by
 * {@code MaxDepth} in config). Fortune and XP statistics are applied to every extra block broken.
 */
public class DrillEnchant implements Listener {
  private final JavaPlugin plugin;

  /** Ticks between breaking each successive depth level. */
  private static final int LEVEL_DELAY_TICKS = 8;

  /**
   * Creates and registers the DrillEnchant listener.
   *
   * @param plugin the owning plugin
   */
  public DrillEnchant(JavaPlugin plugin) {
    this.plugin = plugin;
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
      EnchantData drill = pickaxe.enchants.getEnchant(Enchants.DRILL);
      if (drill != null && drill.enabled && drill.level > 0) {
        double procChance = drill.getCurrentChance();
        if (DrillStoneHandler.hasStone(drill, "Rare")) procChance += 5.0;
        if (Utils.chance(procChance)) {
          Block origin = event.getBlock();

          new BukkitRunnable() {
            /** Executes this scheduled task. */
            @Override
            public void run() {
              startDrill(player, origin, pickaxe, drill, 0);
            }
          }.runTaskLater(plugin, 1L);
        }
      }
    }
  }

  /**
   * Entry point for the sequential drill. Resolves profiles, rolls a random depth, applies stone
   * effects, and kicks off level 0.
   *
   * @param reDepth 0 = first trigger; 1+ = secondary (Epic parallel) â€” stone chain-triggering
   *     effects (Epic, Legendary) are skipped when reDepth > 0
   */
  private void startDrill(
      Player player, Block origin, Pickaxe pickaxe, EnchantData drill, int reDepth) {
    if (!player.isOnline()) return;

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);

    int maxDepth = config.getInt(ConfigPaths.Enchants.Drill.DEPTH, 4);
    int depth = 2 + ThreadLocalRandom.current().nextInt(Math.min(3, Math.max(1, maxDepth - 1)));

    if (DrillStoneHandler.hasStone(drill, "Common")) depth += 1;

    if (DrillStoneHandler.hasStone(drill, "Legendary")) depth += 5;

    if (reDepth == 0 && DrillStoneHandler.hasStone(drill, "Epic")) {
      Block parallelOrigin = origin.getRelative(3, 0, 0);
      if (WorldGuardManager.getInstance().isWithinMineRegion(parallelOrigin.getLocation())) {
        new BukkitRunnable() {
          /** Executes this scheduled task. */
          @Override
          public void run() {
            startDrill(player, parallelOrigin, pickaxe, drill, 1);
          }
        }.runTaskLater(plugin, 2L);
      }
    }

    int fortuneAmount = resolveFortune(pickaxe);
    AtomicInteger totalBroken = new AtomicInteger(0);

    if (reDepth > 0
        && AoEDropResolver.isBreakable(origin)
        && WorldGuardManager.getInstance().isWithinMineRegion(origin.getLocation())) {
      Material mat =
          AoEDropResolver.resolve(
              origin, player.getInventory().getItemInMainHand(), plugin, pickaxe);
      if (!mat.isAir()) {
        int remaining =
            playerProfile.backpack.getMaxSize() - playerProfile.backpack.getTotalItems();
        if (remaining > 0) {
          MineManager.getInstance().trackBlockAtLocation(origin.getLocation());
          origin.setType(Material.AIR);
          playerProfile.backpack.addItem(
              player, mat, Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining), false);
          PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
          AoEDropResolver.rollSideEffects(player, origin, pickaxe, playerProfile);
          totalBroken.incrementAndGet();
        }
      }
    }

    AoEDropResolver.startAccumulation(player.getUniqueId());

    drillLevel(
        player,
        origin,
        playerProfile,
        pickaxe,
        config,
        0,
        depth,
        fortuneAmount,
        totalBroken,
        drill);
  }

  /**
   * Breaks all breakable blocks at {@code currentLevel}, plays the shard animation, then schedules
   * the next level after {@value LEVEL_DELAY_TICKS} ticks. Shows the hologram/message notification
   * on the final level.
   *
   * @param currentLevel 0 = same Y as the original break; 1+ = levels below
   * @param maxLevel the deepest level to drill
   * @param totalBroken running count of extra blocks broken across all levels
   * @param drill active enchant data (used for stone effects)
   */
  private void drillLevel(
      Player player,
      Block origin,
      PlayerProfile playerProfile,
      Pickaxe pickaxe,
      FileConfiguration config,
      int currentLevel,
      int maxLevel,
      int fortuneAmount,
      AtomicInteger totalBroken,
      EnchantData drill) {
    if (!player.isOnline()) return;

    Backpack backpack = playerProfile.backpack;
    if (backpack.isFull()) {
      AoEDropResolver.flushAccumulation(player, origin, pickaxe);
      if (totalBroken.get() > 0) showNotification(player, origin, config, totalBroken.get());
      return;
    }

    List<Block> levelBlocks = new ArrayList<>(getLevelBlocks(origin, currentLevel));

    if (DrillStoneHandler.hasStone(drill, "Uncommon")) {
      if (currentLevel == 0) {
        levelBlocks.add(origin.getRelative(0, 0, 2));
        levelBlocks.add(origin.getRelative(1, 0, 2));
      } else {
        levelBlocks.add(origin.getRelative(0, -currentLevel, 2));
        levelBlocks.add(origin.getRelative(1, -currentLevel, 2));
      }
    }

    List<Material> levelMats = new ArrayList<>();

    for (Block block : levelBlocks) {
      int remaining = backpack.getMaxSize() - backpack.getTotalItems();
      if (remaining <= 0) break;
      if (!AoEDropResolver.isBreakable(block)) continue;
      if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

      Material mat =
          AoEDropResolver.resolve(
              block, player.getInventory().getItemInMainHand(), plugin, pickaxe);
      if (mat.isAir()) continue;
      levelMats.add(mat);
      MineManager.getInstance().trackBlockAtLocation(block.getLocation());
      block.setType(Material.AIR);

      int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
      backpack.addItem(player, mat, dropCount, false);
      PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
      AoEDropResolver.rollSideEffects(player, block, pickaxe, playerProfile);
      totalBroken.incrementAndGet();
    }

    if (!levelMats.isEmpty()) {
      BackpackListener.refresh(player);
      playerProfile.save(null);

      Location levelCenter = origin.getLocation().add(1.0, 0.5 - currentLevel, 1.0);
      DrillAnimation anim = new DrillAnimation(plugin, levelMats);
      AnimationRegistry.getInstance().play(anim, levelCenter, List.of(player));

      float pitch = 0.6f + ThreadLocalRandom.current().nextFloat() * 0.2f;
      player.playSound(levelCenter, Sound.BLOCK_STONE_BREAK, 0.7f, pitch);
    }

    if (currentLevel < maxLevel) {
      new BukkitRunnable() {
        /** Executes this scheduled task. */
        @Override
        public void run() {
          drillLevel(
              player,
              origin,
              playerProfile,
              pickaxe,
              config,
              currentLevel + 1,
              maxLevel,
              fortuneAmount,
              totalBroken,
              drill);
        }
      }.runTaskLater(plugin, LEVEL_DELAY_TICKS);
    } else {
      AoEDropResolver.flushAccumulation(player, origin, pickaxe);
      if (totalBroken.get() > 0) showNotification(player, origin, config, totalBroken.get());
    }
  }

  /**
   * Returns the blocks belonging to a given drill level. Level 0: the 3 companion blocks at the
   * same Y as origin (origin itself is already air). Level N: all 4 blocks of the 2Ã—2 footprint N
   * levels below origin.
   */
  private List<Block> getLevelBlocks(Block origin, int level) {
    if (level == 0) {
      return List.of(
          origin.getRelative(1, 0, 0), origin.getRelative(0, 0, 1), origin.getRelative(1, 0, 1));
    }
    return List.of(
        origin.getRelative(0, -level, 0),
        origin.getRelative(1, -level, 0),
        origin.getRelative(0, -level, 1),
        origin.getRelative(1, -level, 1));
  }

  /** Shows the hologram and/or chat notification at the end of the full drill sequence. */
  private void showNotification(Player player, Block origin, FileConfiguration config, int broken) {
    EnchantPreferenceResult pref = getEnchantPref(player, config);
    String brokenStr = String.valueOf(broken);

    if (pref.hologram && config.getBoolean(ConfigPaths.Enchants.Drill.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          config
              .getString(
                  ConfigPaths.Enchants.Drill.HOLOGRAM_MESSAGE,
                  "&#00FFFF&lDRILL &#CCCCCCDrilled %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
      AnimationRegistry.getInstance()
          .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
    }

    if (pref.message) {
      String message =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.DRILL_TRIGGERED,
                  " &#00FFFF&lDRILL &#CCCCCCDrilled %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      player.sendMessage(Utils.formatMessage(player, message));
    }

    if (pref.title) {
      var msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      String title =
          msgCfg.getString(ConfigPaths.Messages.Enchants.DRILL_TRIGGERED_TITLE, "&#00FFFF&lDrill");
      String subtitle =
          msgCfg
              .getString(
                  ConfigPaths.Messages.Enchants.DRILL_TRIGGERED_SUBTITLE,
                  "&#CCCCCCDrilled %blocks% blocks!")
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
    if (pdc == null) return new EnchantPreferenceResult(false, false, false);
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreferenceResult(false, false, false);

    String enchantName = config.getString(ConfigPaths.Enchants.Drill.NAME, "Drill");
    boolean defaultHolo =
        config.getBoolean(ConfigPaths.Enchants.Drill.HOLOGRAM_DEFAULT_ENABLED, false);
    org.benski.evolv3.models.player.EnchantPreference pref =
        profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

    return new EnchantPreferenceResult(pref.hologram, pref.message, pref.title);
  }

  /** Enchant preference result. */
  private record EnchantPreferenceResult(boolean hologram, boolean message, boolean title) {}
}
