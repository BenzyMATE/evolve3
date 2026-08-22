package org.benski.evolv3.enchants.mining;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.enchant.CascadeAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.AoEDropResolver;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.mine.MineManager;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.core.managers.world.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.mining.AoEEnchantSupport.EnchantPreferenceResult;
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
import org.bukkit.util.Vector;

/**
 * Listener for the Cascade custom enchantment.
 *
 * <p>When triggered, fires a chain of explosions that rips through the mine in a random direction.
 * Each link of the chain detonates a sphere of blocks and advances the front further in the same
 * general direction (with slight angular drift per step), creating a cascading tear through the
 * rock.
 *
 * <ul>
 *   <li>Chain length scales 2 â†’ {@code MaxChainLength} with enchant level.
 *   <li>Each link breaks a sphere of radius {@code ExplosionRadius}.
 *   <li>Links fire every {@code StepDelayTicks} ticks so the chain is visually distinct â€” players
 *       watch it rip across the mine.
 *   <li>Fortune, Frostbite resolution, and backpack overflow protection are all applied to every
 *       block broken in every link.
 * </ul>
 */
public class CascadeEnchant implements Listener {
  /** Ticks between successive chain-link detonations. */
  private static final int STEP_DELAY_TICKS = 4;

  private final JavaPlugin plugin;

  /**
   * Creates and registers the CascadeEnchant listener.
   *
   * @param plugin the owning plugin
   */
  public CascadeEnchant(JavaPlugin plugin) {
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
      EnchantData cascade = pickaxe.enchants.getEnchant(Enchants.CASCADE);
      if (cascade != null
          && cascade.enabled
          && cascade.level > 0
          && Utils.chance(cascade.getCurrentChance())) {
        Block origin = event.getBlock();
        triggerCascade(player, origin, pickaxe, cascade);
      }
    }
  }

  /** */
  private void triggerCascade(Player player, Block origin, Pickaxe pickaxe, EnchantData cascade) {
    if (!player.isOnline()) return;

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;
    if (playerProfile.backpack.isFull()) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);

    int maxLevel = config.getInt(ConfigPaths.Enchants.Cascade.MAX_LEVEL, 100);
    int maxChainLen = config.getInt(ConfigPaths.Enchants.Cascade.MAX_CHAIN_LENGTH, 16);
    int stepDistance = config.getInt(ConfigPaths.Enchants.Cascade.STEP_DISTANCE, 5);
    int baseRadius = config.getInt(ConfigPaths.Enchants.Cascade.EXPLOSION_RADIUS, 2);
    int maxRadiusBonus = config.getInt(ConfigPaths.Enchants.Cascade.MAX_RADIUS_BONUS, 3);
    int maxChains = config.getInt(ConfigPaths.Enchants.Cascade.MAX_CHAINS, 3);

    double levelFraction = (double) (cascade.level - 1) / Math.max(1, maxLevel - 1);

    int chainLength = 3 + (int) Math.round(levelFraction * (maxChainLen - 3));
    chainLength = Math.clamp(chainLength, 3, maxChainLen);

    if (CascadeStoneHandler.hasStone(cascade, "Common")) {
      chainLength += 2;
    }

    if (CascadeStoneHandler.hasStone(cascade, "Legendary")) {
      chainLength = Math.min(chainLength * 3, 60);
    }

    int radius = baseRadius + (int) Math.round(levelFraction * maxRadiusBonus);

    int chainCount = 1 + (int) Math.round(levelFraction * (maxChains - 1));
    chainCount = Math.clamp(chainCount, 1, maxChains);

    boolean timeWarp = CascadeStoneHandler.hasStone(cascade, "Epic");
    boolean depthSeeker = CascadeStoneHandler.hasStone(cascade, "Uncommon");
    boolean aftershock = CascadeStoneHandler.hasStone(cascade, "Rare");

    ThreadLocalRandom rng = ThreadLocalRandom.current();
    World world = origin.getWorld();
    int fortuneAmount = resolveFortune(pickaxe);
    EnchantPreferenceResult pref = getEnchantPref(player, config);
    AtomicInteger totalBroken = new AtomicInteger(0);

    AoEDropResolver.startAccumulation(player.getUniqueId());

    final int finalChainLength = chainLength;

    for (int c = 0; c < chainCount; c++) {
      final boolean isFirstChain = (c == 0);

      double yaw = rng.nextDouble() * Math.PI * 2;
      double pitch =
          depthSeeker ? -(rng.nextDouble() * 0.5 + 0.15) : (rng.nextDouble() - 0.5) * 0.4;
      Vector direction = new Vector(Math.cos(yaw), pitch, Math.sin(yaw)).normalize();

      List<Location> stepCenters = new ArrayList<>();
      Location cursor = origin.getLocation().add(0.5, 0.5, 0.5);
      Vector dir = direction.clone();
      stepCenters.add(cursor.clone());

      for (int step = 1; step < finalChainLength; step++) {
        double yDrift = depthSeeker ? -(rng.nextDouble() * 0.12) : (rng.nextDouble() - 0.5) * 0.08;
        dir.add(
                new Vector(
                    (rng.nextDouble() - 0.5) * 0.18, yDrift, (rng.nextDouble() - 0.5) * 0.18))
            .normalize();
        cursor = cursor.clone().add(dir.clone().multiply(stepDistance));
        if (!WorldGuardManager.getInstance().isWithinMineRegion(cursor)) break;
        stepCenters.add(cursor.clone());
      }

      if (stepCenters.isEmpty()) continue;

      final int finalRadius = radius;

      for (int i = 0; i < stepCenters.size(); i++) {
        final Location center = stepCenters.get(i);
        final boolean isLast = isFirstChain && (i == stepCenters.size() - 1);

        long delay = timeWarp ? 0L : (long) i * STEP_DELAY_TICKS;

        new BukkitRunnable() {
          /** Executes this scheduled task. */
          @Override
          public void run() {
            if (!player.isOnline()) {
              cancel();
              return;
            }

            int broken =
                detonateLink(
                    player, world, center, finalRadius, pickaxe, playerProfile, fortuneAmount);
            totalBroken.addAndGet(broken);

            new CascadeAnimation(plugin, pref.sound(), pref.particles())
                .play(center, List.of(player));

            if (isLast) {
              if (totalBroken.get() > 0) {
                AoEDropResolver.flushAccumulation(player, origin, pickaxe);
                showNotification(player, origin, config, pref, totalBroken.get());
              }
              if (aftershock) {
                Block lastBlock =
                    world.getBlockAt(center.getBlockX(), center.getBlockY(), center.getBlockZ());
                int afterLength = Math.max(2, finalChainLength / 2);
                scheduleAftershot(player, lastBlock, pickaxe, afterLength);
              }
            }
          }
        }.runTaskLater(plugin, delay);
      }
    }
  }

  /**
   * Fires a simplified secondary cascade for Rare (Aftershock), using a fixed chain length and no
   * stone effects (to prevent recursive re-triggering).
   */
  private void scheduleAftershot(Player player, Block origin, Pickaxe pickaxe, int length) {
    if (!player.isOnline()) return;
    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;
    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null || playerProfile.backpack.isFull()) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
    int stepDistance = config.getInt(ConfigPaths.Enchants.Cascade.STEP_DISTANCE, 5);
    int baseRadius = config.getInt(ConfigPaths.Enchants.Cascade.EXPLOSION_RADIUS, 2);

    World world = origin.getWorld();
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int fortuneAmount = resolveFortune(pickaxe);

    double yaw = rng.nextDouble() * Math.PI * 2;
    double pitch = (rng.nextDouble() - 0.5) * 0.4;
    Vector dir = new Vector(Math.cos(yaw), pitch, Math.sin(yaw)).normalize();

    List<Location> steps = new ArrayList<>();
    Location cursor = origin.getLocation().add(0.5, 0.5, 0.5);
    steps.add(cursor.clone());
    for (int i = 1; i < length; i++) {
      dir.add(
              new Vector(
                  (rng.nextDouble() - 0.5) * 0.18,
                  (rng.nextDouble() - 0.5) * 0.08,
                  (rng.nextDouble() - 0.5) * 0.18))
          .normalize();
      cursor = cursor.clone().add(dir.clone().multiply(stepDistance));
      if (!WorldGuardManager.getInstance().isWithinMineRegion(cursor)) break;
      steps.add(cursor.clone());
    }

    EnchantPreferenceResult pref = getEnchantPref(player, config);

    for (int i = 0; i < steps.size(); i++) {
      final Location center = steps.get(i);
      new BukkitRunnable() {
        /** Executes this scheduled task. */
        @Override
        public void run() {
          if (!player.isOnline()) {
            cancel();
            return;
          }
          detonateLink(player, world, center, baseRadius, pickaxe, playerProfile, fortuneAmount);
          new CascadeAnimation(plugin, pref.sound(), pref.particles())
              .play(center, List.of(player));
        }
      }.runTaskLater(plugin, (long) i * STEP_DELAY_TICKS);
    }
  }

  /**
   * Breaks a sphere of blocks around {@code center}, adds drops to the backpack, and returns the
   * number of blocks broken.
   */
  private int detonateLink(
      Player player,
      World world,
      Location center,
      int radius,
      Pickaxe pickaxe,
      PlayerProfile playerProfile,
      int fortuneAmount) {
    Backpack backpack = playerProfile.backpack;
    if (backpack.isFull()) return 0;

    int cx = center.getBlockX();
    int cy = center.getBlockY();
    int cz = center.getBlockZ();
    int radiusSq = radius * radius;
    int broken = 0;

    outer:
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          if (x * x + y * y + z * z > radiusSq) continue;

          int remaining = backpack.getMaxSize() - backpack.getTotalItems();
          if (remaining <= 0) break outer;

          Block block = world.getBlockAt(cx + x, cy + y, cz + z);
          if (!AoEDropResolver.isBreakable(block)) continue;
          if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

          Material mat =
              AoEDropResolver.resolve(
                  block, player.getInventory().getItemInMainHand(), plugin, pickaxe);
          if (mat.isAir()) continue;

          MineManager.getInstance().trackBlockAtLocation(block.getLocation());
          block.setType(Material.AIR);

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

    return broken;
  }

  /** Shows notification to the player. */
  private void showNotification(
      Player player,
      Block origin,
      FileConfiguration config,
      EnchantPreferenceResult pref,
      int broken) {
    String brokenStr = String.valueOf(broken);

    if (pref.hologram() && config.getBoolean(ConfigPaths.Enchants.Cascade.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          config
              .getString(
                  ConfigPaths.Enchants.Cascade.HOLOGRAM_MESSAGE,
                  "&#FFAA00&lCASCADE &#CCCCCCChained %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
      AnimationRegistry.getInstance()
          .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
    }

    if (pref.message()) {
      String message =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.CASCADE_TRIGGERED,
                  " &#FFAA00&lCASCADE &#CCCCCCChained %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      player.sendMessage(Utils.formatMessage(player, message));
    }

    if (pref.title()) {
      var msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      String title =
          msgCfg.getString(
              ConfigPaths.Messages.Enchants.CASCADE_TRIGGERED_TITLE, "&#FFAA00&lCascade");
      String subtitle =
          msgCfg
              .getString(
                  ConfigPaths.Messages.Enchants.CASCADE_TRIGGERED_SUBTITLE,
                  "&#CCCCCCChained %blocks% blocks!")
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
    return AoEEnchantSupport.resolveFortune(pickaxe);
  }

  /** Returns the enchant pref. */
  private EnchantPreferenceResult getEnchantPref(Player player, FileConfiguration config) {
    return AoEEnchantSupport.getEnchantPref(
        player,
        config,
        ConfigPaths.Enchants.Cascade.NAME,
        "Cascade",
        ConfigPaths.Enchants.Cascade.HOLOGRAM_DEFAULT_ENABLED);
  }
}
