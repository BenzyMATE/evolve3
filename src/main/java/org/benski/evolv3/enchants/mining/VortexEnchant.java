package org.benski.evolv3.enchants.mining;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.enchant.VortexAnimation;
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
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener for the Vortex custom enchantment.
 *
 * <p>When triggered, spawns a black hole at the mined block's location. All breakable blocks within
 * a sphere radius are instantly broken, their drops added to the player's backpack with Fortune
 * applied, and the {@link VortexAnimation} plays â€” pulling block-shard entities visually towards
 * the centre before they are absorbed. The radius scales from 1 to {@code MaxRadius} based on
 * level.
 */
public class VortexEnchant implements Listener {
  private final JavaPlugin plugin;

  /**
   * Creates and registers the VortexEnchant listener.
   *
   * @param plugin the owning plugin
   */
  public VortexEnchant(JavaPlugin plugin) {
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
      EnchantData vortex = pickaxe.enchants.getEnchant(Enchants.VORTEX);
      if (vortex != null && vortex.enabled && vortex.level > 0) {
        double triggerChance = vortex.getCurrentChance();
        if (VortexStoneHandler.hasStone(vortex, "Common")) triggerChance += 2.0;
        if (VortexStoneHandler.hasStone(vortex, "Uncommon")) triggerChance += 3.0;
        if (Utils.chance(triggerChance)) {
          Block origin = event.getBlock();
          triggerVortex(player, origin, pickaxe, vortex);
        }
      }
    }
  }

  /** Triggers vortex. */
  private void triggerVortex(Player player, Block origin, Pickaxe pickaxe, EnchantData vortex) {
    if (!player.isOnline()) return;

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);

    int maxLevel = config.getInt(ConfigPaths.Enchants.Vortex.MAX_LEVEL, 100);
    int maxRadius = config.getInt(ConfigPaths.Enchants.Vortex.MAX_RADIUS, 4);

    int radius = Math.max(1, (int) Math.ceil((double) vortex.level / maxLevel * maxRadius));

    if (VortexStoneHandler.hasStone(vortex, "Legendary")) radius *= 2;

    int fortuneAmount = resolveFortune(pickaxe);
    Backpack backpack = playerProfile.backpack;

    if (backpack.isFull()) return;

    boolean isBlackHole =
        VortexStoneHandler.hasStone(vortex, "Epic")
            && VortexStoneHandler.incrementAndCheckBlackHole(player.getUniqueId());

    List<VortexAnimation.BlockEntry> entries = new ArrayList<>();
    int broken = 0;

    AoEDropResolver.startAccumulation(player.getUniqueId());

    int radiusSq = radius * radius;
    outer:
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          if (x * x + y * y + z * z > radiusSq) continue;
          if (x == 0 && y == 0 && z == 0) continue;

          int remaining = backpack.getMaxSize() - backpack.getTotalItems();
          if (remaining <= 0) break outer;

          Block block = origin.getRelative(x, y, z);
          if (!AoEDropResolver.isBreakable(block)) continue;
          if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

          Material mat =
              AoEDropResolver.resolve(
                  block, player.getInventory().getItemInMainHand(), plugin, pickaxe);
          if (mat.isAir()) continue;

          entries.add(new VortexAnimation.BlockEntry(block.getLocation().clone(), mat));
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

    if (isBlackHole) {
      int[][] cardinalDirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
      for (int[] dir : cardinalDirs) {
        for (int dist = 1; dist <= 10; dist++) {
          Block armBlock = origin.getRelative(dir[0] * dist, 0, dir[1] * dist);
          if (!WorldGuardManager.getInstance().isWithinMineRegion(armBlock.getLocation())) break;
          if (!AoEDropResolver.isBreakable(armBlock)) continue;

          int remaining = backpack.getMaxSize() - backpack.getTotalItems();
          if (remaining <= 0) break;

          Material mat =
              AoEDropResolver.resolve(
                  armBlock, player.getInventory().getItemInMainHand(), plugin, pickaxe);
          if (mat.isAir()) continue;

          entries.add(new VortexAnimation.BlockEntry(armBlock.getLocation().clone(), mat));
          MineManager.getInstance().trackBlockAtLocation(armBlock.getLocation());
          armBlock.setType(Material.AIR);

          int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
          backpack.addItem(player, mat, dropCount, false);
          PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
          AoEDropResolver.rollSideEffects(player, armBlock, pickaxe, playerProfile);
          broken++;
        }
      }
    }

    if (VortexStoneHandler.hasStone(vortex, "Rare") && broken > 0) {
      PlayerDataCenter pdc2 = DataCenterRegistry.getInstance().getPlayerDataCenter();
      if (pdc2 != null) {
        PlayerProfile pp2 = pdc2.getLocal(player);
        if (pp2 != null) {
          int essenceGained = 0;
          for (int i = 0; i < broken; i++) {
            if (Utils.chance(15.0)) essenceGained++;
          }
          if (essenceGained > 0) pp2.addEssence(essenceGained);
        }
      }
    }

    if (broken > 0) {
      BackpackListener.refresh(player);
      playerProfile.save(null);
    }

    entries.add(0, new VortexAnimation.BlockEntry(origin.getLocation().clone(), origin.getType()));
    EnchantPreferenceResult pref = getEnchantPref(player, config);

    Location animCenter = origin.getLocation().clone().add(0.5, 0.5, 0.5);
    VortexAnimation anim = new VortexAnimation(plugin, entries, pref.sound(), pref.particles());
    AnimationRegistry.getInstance().play(anim, animCenter, List.of(player));

    if (broken > 0) {
      AoEDropResolver.flushAccumulation(player, origin, pickaxe);
      showNotification(player, origin, config, pref, broken);
    }
  }

  /** Shows notification to the player. */
  private void showNotification(
      Player player,
      Block origin,
      FileConfiguration config,
      EnchantPreferenceResult pref,
      int broken) {
    String brokenStr = String.valueOf(broken);

    if (pref.hologram() && config.getBoolean(ConfigPaths.Enchants.Vortex.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          config
              .getString(
                  ConfigPaths.Enchants.Vortex.HOLOGRAM_MESSAGE,
                  "&#FFAA00&lVORTEX &#CCCCCCAbsorbed %blocks% blocks!")
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
                  ConfigPaths.Messages.Enchants.VORTEX_TRIGGERED,
                  " &#FFAA00&lVORTEX &#CCCCCCAbsorbed %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      player.sendMessage(Utils.formatMessage(player, message));
    }

    if (pref.title()) {
      var msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      String title =
          msgCfg.getString(
              ConfigPaths.Messages.Enchants.VORTEX_TRIGGERED_TITLE, "&#FFAA00&lVortex");
      String subtitle =
          msgCfg
              .getString(
                  ConfigPaths.Messages.Enchants.VORTEX_TRIGGERED_SUBTITLE,
                  "&#CCCCCCAbsorbed %blocks% blocks!")
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
        ConfigPaths.Enchants.Vortex.NAME,
        "Vortex",
        ConfigPaths.Enchants.Vortex.HOLOGRAM_DEFAULT_ENABLED);
  }
}
