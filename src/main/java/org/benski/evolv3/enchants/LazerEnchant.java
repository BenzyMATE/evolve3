package org.benski.evolv3.enchants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.LazerAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.AoEDropResolver;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.BackpackListener;
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
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Listener for the Lazer custom enchantment.
 *
 * <p>When triggered, fires a laser beam in a random cardinal direction (N/S/E/W) at the same Y
 * level as the broken block. All breakable blocks along the beam path (up to {@code MaxRange}
 * blocks) are instantly vaporized, their drops added to the player's backpack, and statistics
 * credited. Fortune is applied if the player has that enchantment.
 *
 * <p>The {@link LazerAnimation} is played immediately after breaking, showing a cyan particle beam
 * and flying block-shard entities visible only to the triggering player.
 */
public class LazerEnchant implements Listener {
  private final JavaPlugin plugin;

  /** The four cardinal directions the laser can fire in. */
  private static final Vector[] CARDINAL_DIRECTIONS = {
    new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(0, 0, 1), new Vector(0, 0, -1)
  };

  /** Legendary (Hex Beam): 4 diagonals + up + down. Distinct from Epic's 4 cardinals. */
  private static final Vector[] LEGENDARY_DIRECTIONS = {
    new Vector(1, 0, 1),
    new Vector(-1, 0, 1),
    new Vector(1, 0, -1),
    new Vector(-1, 0, -1),
    new Vector(0, 1, 0),
    new Vector(0, -1, 0),
  };

  /** Epic + Legendary combined: all 10 directions â€” 4 cardinals, 4 diagonals, up, down. */
  private static final Vector[] STAR_DIRECTIONS = {
    new Vector(1, 0, 0),
    new Vector(-1, 0, 0),
    new Vector(0, 0, 1),
    new Vector(0, 0, -1),
    new Vector(1, 0, 1),
    new Vector(-1, 0, 1),
    new Vector(1, 0, -1),
    new Vector(-1, 0, -1),
    new Vector(0, 1, 0),
    new Vector(0, -1, 0),
  };

  public LazerEnchant(JavaPlugin plugin) {
    this.plugin = plugin;
  }

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
      EnchantData lazer = pickaxe.enchants.getEnchant(Enchants.LAZER);
      if (lazer != null && lazer.enabled && lazer.level > 0) {
        double triggerChance = lazer.getCurrentChance();
        if (LazerStoneHandler.hasStone(lazer, "Common")) triggerChance += 2.0;
        if (Utils.chance(triggerChance)) {
          Block origin = event.getBlock();
          fireLazer(player, origin, pickaxe, lazer);
        }
      }
    }
  }

  private void fireLazer(Player player, Block origin, Pickaxe pickaxe, EnchantData lazer) {
    if (!player.isOnline()) return;

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
    int maxRange = config.getInt(ConfigPaths.Enchants.Lazer.MAX_RANGE, 50);
    int fortuneAmount = resolveFortune(pickaxe);

    boolean hasEpic = LazerStoneHandler.hasStone(lazer, "Epic");
    boolean hasLegendary = LazerStoneHandler.hasStone(lazer, "Legendary");
    Vector[] directions;
    if (hasEpic && hasLegendary) {
      directions = STAR_DIRECTIONS;
    } else if (hasLegendary) {
      directions = LEGENDARY_DIRECTIONS;
    } else if (hasEpic) {
      directions = CARDINAL_DIRECTIONS;
    } else {
      directions = new Vector[] {CARDINAL_DIRECTIONS[ThreadLocalRandom.current().nextInt(4)]};
    }

    boolean hasUncommon = LazerStoneHandler.hasStone(lazer, "Uncommon");

    Backpack backpack = playerProfile.backpack;
    if (backpack.isFull()) return;

    EnchantPreferenceResult pref = getEnchantPref(player, config);
    Location beamOrigin = origin.getLocation().clone().add(0.5, 0.5, 0.5);
    int totalBroken = 0;

    AoEDropResolver.startAccumulation(player.getUniqueId());

    for (Vector direction : directions) {
      List<LazerAnimation.ShardBlock> shardBlocks = new ArrayList<>();
      int broken =
          fireBeam(
              player,
              origin,
              direction,
              maxRange,
              pickaxe,
              playerProfile,
              fortuneAmount,
              hasUncommon,
              true,
              shardBlocks);
      totalBroken += broken;

      LazerAnimation anim =
          new LazerAnimation(plugin, direction, shardBlocks, pref.sound(), pref.particles());
      AnimationRegistry.getInstance().play(anim, beamOrigin, List.of(player));
    }

    if (totalBroken > 0) {
      BackpackListener.refresh(player);
      playerProfile.save(null);
    }

    if (LazerStoneHandler.hasStone(lazer, "Rare")
        && LazerStoneHandler.incrementAndCheckIronFist(player.getUniqueId())
        && JackhammerEnchant.getInstance() != null) {
      JackhammerEnchant.getInstance().triggerLayerBreak(player, origin, pickaxe);
    }

    if (totalBroken > 0) {
      AoEDropResolver.flushAccumulation(player, origin, pickaxe);
      showNotification(player, origin, config, pref, totalBroken);
    }
  }

  /**
   * Fires a single laser beam from {@code startPos} in {@code direction} for up to {@code maxSteps}
   * blocks. Returns the number of blocks broken.
   *
   * @param allowRicochet whether the Uncommon (Reflect) stone may redirect this beam once
   */
  private int fireBeam(
      Player player,
      Block startPos,
      Vector direction,
      int maxSteps,
      Pickaxe pickaxe,
      PlayerProfile playerProfile,
      int fortuneAmount,
      boolean hasUncommon,
      boolean allowRicochet,
      List<LazerAnimation.ShardBlock> shardBlocks) {
    int broken = 0;
    Backpack backpack = playerProfile.backpack;

    for (int step = 1; step <= maxSteps; step++) {
      int remaining = backpack.getMaxSize() - backpack.getTotalItems();
      if (remaining <= 0) break;

      Block block =
          startPos
              .getWorld()
              .getBlockAt(
                  startPos.getX() + (int) (direction.getX() * step),
                  startPos.getY() + (int) (direction.getY() * step),
                  startPos.getZ() + (int) (direction.getZ() * step));

      if (!AoEDropResolver.isBreakable(block)) {
        if (!block.getType().isAir()) {
          if (allowRicochet && hasUncommon && Utils.chance(25.0)) {
            Vector newDir = getPerpendicularDirection(direction);
            int remaining2 = maxSteps - step;
            if (remaining2 > 0) {
              broken +=
                  fireBeam(
                      player,
                      block,
                      newDir,
                      remaining2,
                      pickaxe,
                      playerProfile,
                      fortuneAmount,
                      hasUncommon,
                      false,
                      shardBlocks);
            }
          }
          break;
        }
        continue;
      }

      if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) break;

      Material mat =
          AoEDropResolver.resolve(
              block, player.getInventory().getItemInMainHand(), plugin, pickaxe);
      if (mat.isAir()) continue;

      shardBlocks.add(new LazerAnimation.ShardBlock(block.getLocation().clone(), mat));
      MineManager.getInstance().trackBlockAtLocation(block.getLocation());
      block.setType(Material.AIR);

      int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
      backpack.addItem(player, mat, dropCount, false);
      PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
      AoEDropResolver.rollSideEffects(player, block, pickaxe, playerProfile);
      broken++;
    }

    return broken;
  }

  /**
   * Returns a random direction perpendicular to {@code dir} in the horizontal plane (or any
   * cardinal if {@code dir} is vertical).
   */
  private Vector getPerpendicularDirection(Vector dir) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    if (dir.getX() != 0) {
      return rng.nextBoolean() ? new Vector(0, 0, 1) : new Vector(0, 0, -1);
    } else if (dir.getZ() != 0) {
      return rng.nextBoolean() ? new Vector(1, 0, 0) : new Vector(-1, 0, 0);
    } else {
      return CARDINAL_DIRECTIONS[rng.nextInt(4)];
    }
  }

  /** Shows the hologram and/or chat notification after the laser fires. */
  private void showNotification(
      Player player,
      Block origin,
      FileConfiguration config,
      EnchantPreferenceResult pref,
      int broken) {
    String brokenStr = String.valueOf(broken);

    if (pref.hologram() && config.getBoolean(ConfigPaths.Enchants.Lazer.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          config
              .getString(
                  ConfigPaths.Enchants.Lazer.HOLOGRAM_MESSAGE,
                  "&#FF55CC&lLAZER &#CCCCCCVaporized %blocks% blocks!")
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
                  ConfigPaths.Messages.Enchants.LAZER_TRIGGERED,
                  " &#FF55CC&lLAZER &#CCCCCCVaporized %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      player.sendMessage(Utils.formatMessage(player, message));
    }

    if (pref.title()) {
      var msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      String title =
          msgCfg.getString(ConfigPaths.Messages.Enchants.LAZER_TRIGGERED_TITLE, "&#FF55CC&lLazer");
      String subtitle =
          msgCfg
              .getString(
                  ConfigPaths.Messages.Enchants.LAZER_TRIGGERED_SUBTITLE,
                  "&#CCCCCCVaporized %blocks% blocks!")
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

  private int resolveFortune(Pickaxe pickaxe) {
    if (pickaxe.enchants == null) return 0;
    EnchantData fortune = pickaxe.enchants.getEnchant(Enchants.FORTUNE);
    if (fortune == null || !fortune.enabled || fortune.level <= 0) return 0;
    return FortuneEnchant.getDropAmount(fortune.level);
  }

  private EnchantPreferenceResult getEnchantPref(Player player, FileConfiguration config) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreferenceResult(false, false, true, true, false);
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreferenceResult(false, false, true, true, false);

    String enchantName = config.getString(ConfigPaths.Enchants.Lazer.NAME, "Lazer");
    boolean defaultHolo =
        config.getBoolean(ConfigPaths.Enchants.Lazer.HOLOGRAM_DEFAULT_ENABLED, false);
    EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

    return new EnchantPreferenceResult(
        pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
  }

  private record EnchantPreferenceResult(
      boolean hologram, boolean message, boolean sound, boolean particles, boolean title) {}
}
