package org.benski.evolv3.enchants;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.ThorAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.AoEDropResolver;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener for the Thor custom enchantment.
 *
 * <p>When triggered, calls down a lightning strike at the mined block's X/Z position. The bolt
 * scans from the top of the mine region downward to the bottom, breaking every solid block in the
 * 1Ã—1 column and adding their drops to the player's backpack. Air gaps (already-mined sections)
 * are skipped so the bolt passes through holes and continues downward.
 *
 * <p>Rare (Arctic Strike) stone: places Sculk around the impact point. When a player mines that
 * Sculk they receive one random reward.
 */
public class ThorEnchant implements Listener {
  private final JavaPlugin plugin;

  public ThorEnchant(JavaPlugin plugin) {
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
    if (event.getBlock().getType() == Material.SCULK) return;

    PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    if (pickaxeDC == null) return;

    pickaxeDC.get(
        player,
        pickaxeProfile -> {
          if (pickaxeProfile == null || pickaxeProfile.enchants == null) return;

          EnchantData thor = pickaxeProfile.enchants.getEnchant(Enchants.THOR);
          if (thor == null || !thor.enabled || thor.level <= 0) return;

          double triggerChance = thor.getCurrentChance();
          if (ThorStoneHandler.hasStone(thor, "Common")) triggerChance += 2.0;
          if (!Utils.chance(triggerChance)) return;

          Block origin = event.getBlock();

          new BukkitRunnable() {
            @Override
            public void run() {
              strikeThor(player, origin, pickaxeProfile, thor, false);
            }
          }.runTaskLater(plugin, 1L);

          if (ThorStoneHandler.hasStone(thor, "Uncommon")) {
            new BukkitRunnable() {
              @Override
              public void run() {
                strikeThor(player, origin, pickaxeProfile, thor, true);
              }
            }.runTaskLater(plugin, 4L);
          }

          if (ThorStoneHandler.hasStone(thor, "Legendary") && Utils.chance(15.0)) {
            triggerThunderstorm(player, origin, pickaxeProfile, thor);
          }
        });
  }

  /**
   * When Sculk placed by Arctic Strike is mined, award one random reward: a backpack slot, 1
   * essence, 1 atom, or 50 coins.
   */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onSculkBreak(BlockBreakEvent event) {
    if (event.getBlock().getType() != Material.SCULK) return;
    if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) return;

    Player player = event.getPlayer();
    if (!Utils.isPickaxe(player.getInventory().getItemInMainHand().getType())) return;

    event.setDropItems(false);
    event.setExpToDrop(0);

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;
    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    Block block = event.getBlock();
    int roll = ThreadLocalRandom.current().nextInt(4);

    switch (roll) {
      case 0 -> awardBackpackSlot(player, playerProfile, block);
      case 1 -> {
        playerProfile.addEssence(1);
        playerProfile.save(null);
        showSculkReward(player, block, "&#B366D9ðŸ”¥ &#FFFFFF&l+1 &#B366D9Essence");
      }
      case 2 -> {
        PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDC != null) {
          pickaxeDC.get(
              player,
              pp -> {
                if (pp == null) return;
                pp.evolutionAtoms += 1;
                PickaxeManager.getInstance().checkAtoms(pp, player);
                pp.save(null);
                showSculkReward(player, block, "&#FFEE00âš› &#FFFFFF&l+1 &#FFEE00Atom");
              });
        }
      }
      case 3 -> {
        playerProfile.addCoins(50);
        playerProfile.save(null);
        showSculkReward(player, block, "&#FFD700â› &#FFFFFF&l+50 &#FFD700Coins");
      }
    }
  }

  private void awardBackpackSlot(Player player, PlayerProfile playerProfile, Block block) {
    int tierCap = resolveTierCap(player);
    if (playerProfile.backpack.getMaxSize() < tierCap) {
      playerProfile.backpack.setMaxSize(playerProfile.backpack.getMaxSize() + 1);
      BackpackListener.refresh(player);
      playerProfile.save(null);
      showSculkReward(player, block, "&#55FF55ðŸ“¦ &#FFFFFF&l+1 &#55FF55Slot");
    } else {
      playerProfile.addEssence(1);
      playerProfile.save(null);
      showSculkReward(player, block, "&#B366D9ðŸ”¥ &#FFFFFF&l+1 &#B366D9Essence");
    }
  }

  @SuppressWarnings("deprecation")
  private int resolveTierCap(Player player) {
    int fallback = 500;
    ItemStack offhand = player.getInventory().getItemInOffHand();
    if (offhand.getType() == Material.AIR) return fallback;
    ItemMeta meta = offhand.getItemMeta();
    if (meta == null || !meta.hasCustomModelData()) return fallback;
    int modelData = meta.getCustomModelData();
    FileConfiguration defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    String path = ConfigPaths.Default.Backpack.TIERS_PREFIX + modelData;
    return defaultConfig.contains(path) ? defaultConfig.getInt(path, fallback) : fallback;
  }

  private void showSculkReward(Player player, Block block, String text) {
    Location loc = block.getLocation().add(0.5, 1.0, 0.5);
    AnimationRegistry.getInstance().play(new HologramAnimation(plugin, text), loc, List.of(player));
  }

  /**
   * Executes a single Thor column strike.
   *
   * @param isSubStrike {@code true} for secondary strikes (Double Strike, Thunderstorm); prevents
   *     chained Mjolnir procs and recursive Thunderstorms.
   */
  private void strikeThor(
      Player player,
      Block origin,
      PickaxeProfile pickaxeProfile,
      EnchantData thor,
      boolean isSubStrike) {
    if (!player.isOnline()) return;

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
    int maxColumnHeight = config.getInt(ConfigPaths.Enchants.Thor.MAX_COLUMN_HEIGHT, 100);

    World world = origin.getWorld();
    int baseX = origin.getX();
    int baseZ = origin.getZ();

    Location scanLoc = origin.getLocation().clone();
    int topY = origin.getY();
    for (int i = 1; i <= maxColumnHeight; i++) {
      scanLoc.setY(topY + 1);
      if (!WorldGuardManager.getInstance().isWithinMineRegion(scanLoc)) break;
      topY++;
    }

    scanLoc.setY(origin.getY());
    int bottomY = origin.getY();
    for (int i = 1; i <= maxColumnHeight; i++) {
      scanLoc.setY(bottomY - 1);
      if (!WorldGuardManager.getInstance().isWithinMineRegion(scanLoc)) break;
      bottomY--;
    }

    int fortuneAmount = resolveFortune(pickaxeProfile);
    Backpack backpack = playerProfile.backpack;
    if (backpack.isFull()) return;

    boolean isMjolnir =
        !isSubStrike
            && ThorStoneHandler.hasStone(thor, "Epic")
            && ThorStoneHandler.incrementAndCheckMjolnir(player.getUniqueId());

    int[][] offsets =
        isMjolnir
            ? new int[][] {
              {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
            }
            : new int[][] {{0, 0}};

    int broken = 0;
    int iceOriginY = -1;

    AoEDropResolver.startAccumulation(player.getUniqueId());

    for (int[] off : offsets) {
      int colX = baseX + off[0];
      int colZ = baseZ + off[1];

      for (int y = topY; y >= bottomY; y--) {
        int remaining = backpack.getMaxSize() - backpack.getTotalItems();
        if (remaining <= 0) break;

        Block block = world.getBlockAt(colX, y, colZ);
        if (!AoEDropResolver.isBreakable(block)) continue;
        if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

        Material mat =
            AoEDropResolver.resolve(
                block, player.getInventory().getItemInMainHand(), plugin, pickaxeProfile);
        if (mat.isAir()) continue;

        if (iceOriginY < 0 && off[0] == 0 && off[1] == 0) iceOriginY = y;

        MineManager.getInstance().trackBlockAtLocation(block.getLocation());
        block.setType(Material.AIR);

        int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
        backpack.addItem(player, mat, dropCount, false);
        PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
        AoEDropResolver.rollSideEffects(player, block, pickaxeProfile, playerProfile);
        broken++;
      }
    }

    if (ThorStoneHandler.hasStone(thor, "Rare")) {
      int freezeY = iceOriginY >= 0 ? iceOriginY : origin.getY();
      for (int dy = 0; dy >= -4; dy--) {
        for (int fx = -1; fx <= 1; fx++) {
          for (int fz = -1; fz <= 1; fz++) {
            if (fx == 0 && fz == 0) continue;
            Block fb = world.getBlockAt(baseX + fx, freezeY + dy, baseZ + fz);
            if (WorldGuardManager.getInstance().isWithinMineRegion(fb.getLocation())
                && fb.getType().isSolid()) {
              fb.setType(Material.SCULK);
            }
          }
        }
      }
    }

    if (broken > 0) {
      BackpackListener.refresh(player);
      playerProfile.save(null);
    }

    EnchantPreferenceResult pref = getEnchantPref(player, config);
    int columnHeight = topY - bottomY;
    Location strikeOrigin = new Location(world, baseX + 0.5, topY + 0.5, baseZ + 0.5);

    world.strikeLightningEffect(strikeOrigin);

    ThorAnimation anim = new ThorAnimation(plugin, columnHeight, pref.sound, pref.particles);
    AnimationRegistry.getInstance().play(anim, strikeOrigin, List.of(player));

    if (broken > 0) {
      AoEDropResolver.flushAccumulation(player, origin, pickaxeProfile);
      showNotification(player, origin, config, pref, broken);
    }
  }

  /**
   * Legendary (Thunderstorm): scatters 10 sub-strikes at random X/Z positions within the mine
   * bounds, staggered 2 ticks apart.
   */
  private void triggerThunderstorm(
      Player player, Block origin, PickaxeProfile pickaxeProfile, EnchantData thor) {
    int[] bounds = WorldGuardManager.getInstance().getMineRegionXZBounds(origin.getLocation());
    int minX, minZ, maxX, maxZ;
    if (bounds != null) {
      minX = bounds[0];
      minZ = bounds[1];
      maxX = bounds[2];
      maxZ = bounds[3];
    } else {
      minX = origin.getX() - 20;
      minZ = origin.getZ() - 20;
      maxX = origin.getX() + 20;
      maxZ = origin.getZ() + 20;
    }

    ThreadLocalRandom rng = ThreadLocalRandom.current();
    World world = origin.getWorld();
    int originY = origin.getY();

    for (int i = 0; i < 10; i++) {
      int rx = rng.nextInt(minX, maxX + 1);
      int rz = rng.nextInt(minZ, maxZ + 1);
      Block stormBlock = world.getBlockAt(rx, originY, rz);
      if (!WorldGuardManager.getInstance().isWithinMineRegion(stormBlock.getLocation())) continue;

      final Block finalBlock = stormBlock;
      new BukkitRunnable() {
        @Override
        public void run() {
          if (!player.isOnline()) return;
          strikeThor(player, finalBlock, pickaxeProfile, thor, true);
        }
      }.runTaskLater(plugin, 2L + i * 2L);
    }
  }

  private void showNotification(
      Player player,
      Block origin,
      FileConfiguration config,
      EnchantPreferenceResult pref,
      int broken) {
    String brokenStr = String.valueOf(broken);

    if (pref.hologram && config.getBoolean(ConfigPaths.Enchants.Thor.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          config
              .getString(
                  ConfigPaths.Enchants.Thor.HOLOGRAM_MESSAGE,
                  "&#FF55CC&lTHOR &#CCCCCCStruck %blocks% blocks!")
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
                  ConfigPaths.Messages.Enchants.THOR_TRIGGERED,
                  " &#FF55CC&lTHOR &#CCCCCCStruck %blocks% blocks!")
              .replace("%blocks%", brokenStr);
      player.sendMessage(Utils.formatMessage(player, message));
    }

    if (pref.title) {
      var msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      String title =
          msgCfg.getString(ConfigPaths.Messages.Enchants.THOR_TRIGGERED_TITLE, "&#FF55CC&lThor");
      String subtitle =
          msgCfg
              .getString(
                  ConfigPaths.Messages.Enchants.THOR_TRIGGERED_SUBTITLE,
                  "&#CCCCCCStruck %blocks% blocks!")
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

  private int resolveFortune(PickaxeProfile pickaxeProfile) {
    if (pickaxeProfile.enchants == null) return 0;
    EnchantData fortune = pickaxeProfile.enchants.getEnchant(Enchants.FORTUNE);
    if (fortune == null || !fortune.enabled || fortune.level <= 0) return 0;
    return FortuneEnchant.getDropAmount(fortune.level);
  }

  private EnchantPreferenceResult getEnchantPref(Player player, FileConfiguration config) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreferenceResult(false, false, true, true, false);
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreferenceResult(false, false, true, true, false);

    String enchantName = config.getString(ConfigPaths.Enchants.Thor.NAME, "Thor");
    boolean defaultHolo =
        config.getBoolean(ConfigPaths.Enchants.Thor.HOLOGRAM_DEFAULT_ENABLED, false);
    EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

    return new EnchantPreferenceResult(
        pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
  }

  private record EnchantPreferenceResult(
      boolean hologram, boolean message, boolean sound, boolean particles, boolean title) {}
}
