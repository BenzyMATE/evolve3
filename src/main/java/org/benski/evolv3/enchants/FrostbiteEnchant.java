package org.benski.evolv3.enchants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.FrostbiteBlockManager;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener for the Frostbite custom enchantment. Freezes blocks in a radius around the mined block,
 * converting them to PACKED_ICE. Original block material is stored in metadata for restoration.
 *
 * <p>Trigger chance scales with enchantment level:
 *
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance
 *   <li>Example: Level 50/100 with maxChance 15 = 7.5% chance
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class FrostbiteEnchant implements Listener {
  private final FileConfiguration enchantsConfig;
  private final JavaPlugin plugin;

  /** Metadata key for storing original block material. */
  public static final String FROSTBITE_METADATA_KEY = "frostbite_original";

  public FrostbiteEnchant(JavaPlugin plugin) {
    this.plugin = plugin;
    this.enchantsConfig = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
  }

  /**
   * Handles the block break event to trigger frostbite effect.
   *
   * @param event the block break event
   */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    ItemStack item = player.getInventory().getItemInMainHand();

    if (!Utils.isPickaxe(item.getType())) {
      return;
    }

    if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) {
      return;
    }

    String blockName = event.getBlock().getType().name();
    if (blockName.endsWith("_LOG") || blockName.endsWith("_STEM")) {
      return;
    }

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC != null) {
      org.benski.evolv3.models.player.PlayerProfile pp = playerDC.getLocal(player);
      if (pp != null && pp.backpack.isFull()) return;
    }

    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter == null) return;
    Pickaxe profile = playerDataCenter.getPickaxeForPlayer(player);
    if (profile != null && profile.enchants != null) {
      EnchantData enchant = profile.enchants.getEnchant(Enchants.FROSTBITE);
      if (enchant != null && enchant.enabled && enchant.level > 0) {
        Block block = event.getBlock();
        if (block.getType() != Material.PACKED_ICE && !block.hasMetadata(FROSTBITE_METADATA_KEY)) {
          double triggerChance = enchant.getCurrentChance();
          if (FrostbiteStoneHandler.hasStone(enchant, "Uncommon")) triggerChance += 3.0;
          if (Utils.chance(triggerChance)) {
            applyFreezingEffect(block, player, enchant);
          }
        }
      }
    }
  }

  /**
   * Handles block break events on frozen blocks (PACKED_ICE with frostbite metadata). Applies Rare
   * (Ripple) and Epic (Cold Loot) stone effects.
   */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onFrozenBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (!block.hasMetadata(FROSTBITE_METADATA_KEY)) return;

    Player player = event.getPlayer();
    if (!Utils.isPickaxe(player.getInventory().getItemInMainHand().getType())) return;
    if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) return;

    PlayerDataCenter frozenPlayerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (frozenPlayerDC == null) return;
    Pickaxe frozenProfile = frozenPlayerDC.getPickaxeForPlayer(player);
    if (frozenProfile != null && frozenProfile.enchants != null) {
      EnchantData enchant = frozenProfile.enchants.getEnchant(Enchants.FROSTBITE);
      if (enchant != null && enchant.enabled) {
        if (FrostbiteStoneHandler.hasStone(enchant, "Rare")) {
          triggerIcePick(player, block, enchant, frozenProfile);
        }

        if (FrostbiteStoneHandler.hasStone(enchant, "Epic") && Utils.chance(5.0)) {
          awardAtom(player, block);
        }
      }
    }
  }

  /**
   * Applies the freezing effect to blocks around the center block.
   *
   * @param centerBlock the block that was mined
   * @param player the player who triggered the effect
   */
  @SuppressWarnings("deprecation")
  private void applyFreezingEffect(Block centerBlock, Player player, EnchantData enchant) {
    EnchantPreference pref = getEnchantPref(player);
    World world = centerBlock.getWorld();
    int radius = enchantsConfig.getInt(ConfigPaths.Enchants.Frostbite.RADIUS, 1);

    if (FrostbiteStoneHandler.hasStone(enchant, "Common")) radius += 1;
    if (FrostbiteStoneHandler.hasStone(enchant, "Legendary") && Utils.chance(25.0)) {
      triggerShatter(player);
    }

    List<Block> blocksToFreeze = new ArrayList<>();
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          Block block = centerBlock.getRelative(x, y, z);

          if (isFreezeable(block)
              && WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) {
            blocksToFreeze.add(block);
          }
        }
      }
    }

    if (blocksToFreeze.isEmpty()) {
      return;
    }

    Location center = centerBlock.getLocation();
    blocksToFreeze.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(center)));

    if (pref.sound) {
      world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
    }

    final int totalBlocks = blocksToFreeze.size();
    new BukkitRunnable() {
      int index = 0;

      @Override
      public void run() {
        int batchEnd = Math.min(index + 3, blocksToFreeze.size());
        while (index < batchEnd) {
          Block block = blocksToFreeze.get(index);

          Material originalBlockType = block.getType();
          Material dropMaterial = getDropMaterial(block, player);
          block.setMetadata(
              FROSTBITE_METADATA_KEY,
              new FixedMetadataValue(Main.getInstance(), dropMaterial.name()));

          block.setType(Material.PACKED_ICE);
          FrostbiteBlockManager.getInstance()
              .addFrozenBlock(block.getLocation(), originalBlockType);

          if (pref.particles) {
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.SNOWFLAKE, loc, 5, 0.2, 0.2, 0.2, 0.02);
          }

          index++;
        }

        if (index >= blocksToFreeze.size()) {
          this.cancel();

          if (pref.hologram
              && enchantsConfig.getBoolean(ConfigPaths.Enchants.Frostbite.HOLOGRAM_ENABLED, true)) {
            String hologramText =
                enchantsConfig
                    .getString(
                        ConfigPaths.Enchants.Frostbite.HOLOGRAM_MESSAGE,
                        "&#00BFFF&l[Frostbite] &#CCCCCCFroze %blocks% blocks!")
                    .replace("%blocks%", String.valueOf(totalBlocks));
            Location hologramLoc = centerBlock.getLocation().add(0.5, 1.0, 0.5);
            AnimationRegistry.getInstance()
                .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
          }

          if (pref.message) {
            String message =
                ConfigRegistry.getInstance()
                    .get(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.FROSTBITE_TRIGGERED);
            if (message != null && !message.isEmpty()) {
              message = message.replace("%blocks%", String.valueOf(totalBlocks));
              player.sendMessage(Utils.formatMessage(player, message));
            }
          }

          if (pref.title) {
            String title =
                ConfigRegistry.getInstance()
                    .get(ConfigNames.MESSAGES)
                    .getString(
                        ConfigPaths.Messages.Enchants.FROSTBITE_TRIGGERED_TITLE,
                        "&#00BFFF&lFrostbite");
            String subtitle =
                ConfigRegistry.getInstance()
                    .get(ConfigNames.MESSAGES)
                    .getString(
                        ConfigPaths.Messages.Enchants.FROSTBITE_TRIGGERED_SUBTITLE,
                        "&#CCCCCCArea frozen!")
                    .replace("%blocks%", String.valueOf(totalBlocks));
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
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  private EnchantPreference getEnchantPref(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreference();
    org.benski.evolv3.models.player.PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreference();
    String enchantName = enchantsConfig.getString(ConfigPaths.Enchants.Frostbite.NAME, "Frostbite");
    boolean defaultHologram =
        enchantsConfig.getBoolean(ConfigPaths.Enchants.Frostbite.HOLOGRAM_DEFAULT_ENABLED, false);
    return profile.preferences.getOrCreateEnchantPref(enchantName, defaultHologram);
  }

  /**
   * Checks if a block can be frozen.
   *
   * @param block the block to check
   * @return true if the block can be frozen
   */
  private boolean isFreezeable(Block block) {
    Material material = block.getType();

    if (material.isAir() || material == Material.WATER || material == Material.LAVA) {
      return false;
    }

    if (material == Material.PACKED_ICE || block.hasMetadata(FROSTBITE_METADATA_KEY)) {
      return false;
    }

    if (material == Material.BEDROCK || material.getHardness() < 0) {
      return false;
    }

    return true;
  }

  /**
   * Rare (Ice Pick): breaks frozen blocks in a small radius around the mined block, awarding their
   * original ore drops plus rolling for essence and atoms on each block. Also applies the Epic
   * stone's extra 5% atom chance per block.
   */
  @SuppressWarnings("deprecation")
  private void triggerIcePick(Player player, Block center, EnchantData enchant, Pickaxe pickaxe) {
    int radius = 2;
    List<Location> removed = new ArrayList<>();

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    org.benski.evolv3.models.player.PlayerProfile pp = pdc != null ? pdc.getLocal(player) : null;

    FileConfiguration rollConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double baseEssenceChance =
        rollConfig.getDouble(ConfigPaths.Default.Essence.DISCOVERY_CHANCE, 5.0);
    double baseAtomChance = rollConfig.getDouble(ConfigPaths.Default.Atoms.DISCOVERY_CHANCE, 1.0);
    boolean hasEpic = FrostbiteStoneHandler.hasStone(enchant, "Epic");
    boolean essenceChanged = false;

    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          if (x == 0 && y == 0 && z == 0) continue;
          Block block = center.getRelative(x, y, z);
          if (!block.hasMetadata(FROSTBITE_METADATA_KEY)) continue;

          List<org.bukkit.metadata.MetadataValue> meta = block.getMetadata(FROSTBITE_METADATA_KEY);
          if (meta.isEmpty()) continue;

          try {
            Material dropMat = Material.valueOf(meta.get(0).asString());
            if (pp != null && !dropMat.isAir() && dropMat != Material.PACKED_ICE) {
              pp.backpack.addItem(player, dropMat, 1, false);
            }
          } catch (IllegalArgumentException ignored) {
          }

          block.removeMetadata(FROSTBITE_METADATA_KEY, Main.getInstance());
          MineManager.getInstance().trackBlockAtLocation(block.getLocation());
          block.setType(Material.AIR);
          Location blockLoc = block.getLocation();
          center
              .getWorld()
              .spawnParticle(
                  Particle.SNOWFLAKE, blockLoc.clone().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.05);
          removed.add(blockLoc);

          if (Utils.chance(baseEssenceChance)) {
            int min = rollConfig.getInt(ConfigPaths.Default.Essence.DISCOVERY_MIN_AMOUNT, 1);
            int max = rollConfig.getInt(ConfigPaths.Default.Essence.DISCOVERY_MAX_AMOUNT, 5);
            int amount = Utils.amount(min, max);
            if (pp != null) pp.addEssence(amount);
            if (pickaxe != null) {
              pickaxe.essenceVessel.current += amount;
              essenceChanged = true;
            }
            Location animLoc = blockLoc.clone().add(0.5, 0.5, 0.5);
            Location hologramLoc = blockLoc.clone().add(0.5, 1.5, 0.5);
            AnimationRegistry.getInstance().play("essence", animLoc, List.of(player), amount);
            if (pp != null
                && pp.preferences.essenceHologram
                && rollConfig.getBoolean(ConfigPaths.Default.Essence.HOLOGRAM_ENABLED, true)) {
              String text =
                  rollConfig.getString(
                      ConfigPaths.Default.Essence.HOLOGRAM_TEXT,
                      "&#B366D9ðŸ”¥ &#FFFFFF&l+ %amount% &#B366D9Essence");
              text = text.replace("%amount%", Utils.formatNumber(amount));
              AnimationRegistry.getInstance().play("hologram", hologramLoc, List.of(player), text);
            }
          }

          if (Utils.chance(baseAtomChance)) {
            awardAtom(player, block);
          }

          if (hasEpic && Utils.chance(5.0)) {
            awardAtom(player, block);
          }
        }
      }
    }

    if (essenceChanged && pickaxe != null) {
      PickaxeManager.getInstance().checkEssence(pickaxe, player);
      pickaxe.save(null);
    }

    if (!removed.isEmpty()) {
      FrostbiteBlockManager.getInstance().removeFrozenBlocks(removed);
      if (pp != null) BackpackListener.refresh(player);
      player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
    }
  }

  /**
   * Epic (Cryo Atom): awards atoms to the player when they mine a frozen block. Caller is
   * responsible for the 5% chance roll.
   */
  private void awardAtom(Player player, Block block) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int min = config.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MIN_AMOUNT, 1);
    int max = config.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MAX_AMOUNT, 3);
    int amount = Utils.amount(min, max);

    PlayerDataCenter atomPlayerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (atomPlayerDC != null) {
      Pickaxe atomPickaxe = atomPlayerDC.getPickaxeForPlayer(player);
      if (atomPickaxe != null) {
        atomPickaxe.atomVessel.current += amount;
        PickaxeManager.getInstance().checkAtoms(atomPickaxe, player);
        atomPickaxe.save(null);
        EfficiencyStoneHandler.getInstance().onAtomPickup(player);
      }
    }

    Location animLoc = block.getLocation().clone().add(0.5, 0.5, 0.5);
    Location hologramLoc = block.getLocation().clone().add(0.5, 1.5, 0.5);
    AnimationRegistry.getInstance().play("atom", animLoc, List.of(player), amount);

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    org.benski.evolv3.models.player.PlayerProfile pp = pdc != null ? pdc.getLocal(player) : null;
    boolean atomHologramOn = pp == null || pp.preferences.atomHologram;

    if (atomHologramOn && config.getBoolean(ConfigPaths.Default.Atoms.HOLOGRAM_ENABLED, true)) {
      String text =
          config.getString(
              ConfigPaths.Default.Atoms.HOLOGRAM_TEXT,
              "&#FFEE00âš› &#FFFFFF&l+ %amount% &#FFEE00Atoms");
      text = text.replace("%amount%", Utils.formatNumber(amount));
      AnimationRegistry.getInstance().play("hologram", hologramLoc, List.of(player), text);
    }
  }

  /** Uncommon (Shatter): breaks all currently frozen blocks, awarding their drops to the player. */
  @SuppressWarnings("deprecation")
  private void triggerShatter(Player player) {
    Map<String, Material> frozen = FrostbiteBlockManager.getInstance().getLiveCache();
    if (frozen.isEmpty()) return;

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return;
    PlayerProfile pp = pdc.getLocal(player);
    if (pp == null) return;

    List<String> keys = new ArrayList<>(frozen.keySet());
    List<Location> removed = new ArrayList<>();

    for (String key : keys) {
      Location loc = FrostbiteBlockManager.parseKey(key);
      if (loc == null) continue;
      Block block = loc.getBlock();
      if (!block.hasMetadata(FROSTBITE_METADATA_KEY)) continue;

      List<org.bukkit.metadata.MetadataValue> meta = block.getMetadata(FROSTBITE_METADATA_KEY);
      if (meta.isEmpty()) continue;

      try {
        Material dropMat = Material.valueOf(meta.get(0).asString());
        if (!dropMat.isAir() && dropMat != Material.PACKED_ICE) {
          pp.backpack.addItem(player, dropMat, 1, false);
        }
      } catch (IllegalArgumentException ignored) {
      }

      block.removeMetadata(FROSTBITE_METADATA_KEY, Main.getInstance());
      MineManager.getInstance().trackBlockAtLocation(block.getLocation());
      block.setType(Material.AIR);
      loc.getWorld()
          .spawnParticle(
              Particle.SNOWFLAKE, loc.clone().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.05);
      removed.add(loc);
    }

    if (!removed.isEmpty()) {
      FrostbiteBlockManager.getInstance().removeFrozenBlocks(removed);
      player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.7f);
      BackpackListener.refresh(player);
    }
  }

  /**
   * Gets the material that would drop from a block. Falls back to block type if no drops.
   *
   * @param block the block to check
   * @return the drop material
   */
  private Material getDropMaterial(Block block, Player player) {
    return block.getDrops(player.getInventory().getItemInMainHand()).stream()
        .filter(i -> i != null && !i.getType().isAir())
        .findFirst()
        .map(ItemStack::getType)
        .orElse(block.getType());
  }
}
