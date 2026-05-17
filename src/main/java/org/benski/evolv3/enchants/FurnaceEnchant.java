package org.benski.evolv3.enchants;

import static org.benski.evolv3.core.helpers.Utils.formatMaterialName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantDropManager;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener for the Furnace custom enchantment. Auto-smelts blocks based on configured recipes.
 * Works in conjunction with Alchemy enchant for chained effects.
 *
 * <p>Trigger chance scales with enchantment level:
 *
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance
 *   <li>Example: Level 80/100 with maxChance 40 = 32% chance
 * </ul>
 *
 * <p>Smelting recipes are configurable in enchants.yml
 */
@ExtensionMethod(ItemStackExtensions.class)
public class FurnaceEnchant implements Listener {
  @lombok.Getter private static FurnaceEnchant instance;

  private final FileConfiguration enchantsConfig;
  private final JavaPlugin plugin;

  /**
   * Metadata key for passing smelted result to drop handlers. Used in BlockBreakEvent context to
   * modify drops.
   */
  public static final String FURNACE_RESULT_KEY = "furnace_result";

  /** Cached smelting recipes for performance. */
  private final Map<Material, Material> smeltingRecipes = new HashMap<>();

  /** Rare stone (Alchemical Forge) upgrade chain: smelt result â†’ next tier. */
  private final Map<Material, Material> alchemicalForgeChain = new HashMap<>();

  public FurnaceEnchant(JavaPlugin plugin) {
    instance = this;
    this.plugin = plugin;
    this.enchantsConfig = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
    loadSmeltingRecipes();
    loadAlchemicalForgeChain();
  }

  /** Loads smelting recipes from configuration. */
  private void loadSmeltingRecipes() {
    smeltingRecipes.clear();
    ConfigurationSection recipesSection =
        enchantsConfig.getConfigurationSection(ConfigPaths.Enchants.Furnace.RECIPES);
    if (recipesSection == null) {
      return;
    }

    for (String key : recipesSection.getKeys(false)) {
      try {
        Material input = Material.valueOf(key.toUpperCase());
        Material output = Material.valueOf(recipesSection.getString(key).toUpperCase());
        smeltingRecipes.put(input, output);
      } catch (IllegalArgumentException e) {
        plugin
            .getLogger()
            .warning("Invalid smelting recipe: " + key + " -> " + recipesSection.getString(key));
      }
    }
  }

  /** Loads the Alchemical Forge (Rare stone) upgrade chain from configuration. */
  private void loadAlchemicalForgeChain() {
    alchemicalForgeChain.clear();
    ConfigurationSection section =
        enchantsConfig.getConfigurationSection(ConfigPaths.Enchants.Furnace.ALCHEMICAL_FORGE_CHAIN);
    if (section == null) {
      return;
    }

    for (String key : section.getKeys(false)) {
      try {
        Material input = Material.valueOf(key.toUpperCase());
        Material output = Material.valueOf(section.getString(key).toUpperCase());
        alchemicalForgeChain.put(input, output);
      } catch (IllegalArgumentException e) {
        plugin
            .getLogger()
            .warning(
                "Invalid Alchemical Forge chain entry: " + key + " -> " + section.getString(key));
      }
    }
  }

  /**
   * Handles the block break event to trigger furnace effect.
   *
   * @param event the block break event
   */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

    PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    if (dataCenter == null) return;
    dataCenter.get(
        player,
        profile -> {
          if (profile == null || profile.enchants == null) {
            return;
          }

          EnchantData enchant = profile.enchants.getEnchant(Enchants.FURNACE);
          if (enchant == null || !enchant.enabled || enchant.level <= 0) {
            return;
          }

          boolean hasLegendary = FurnaceStoneHandler.hasStone(enchant, "Legendary");
          if (!hasLegendary) {
            double triggerChance = enchant.getCurrentChance();
            if (!Utils.chance(triggerChance)) {
              return;
            }
          }

          Block block = event.getBlock();
          Material materialToSmelt = getMaterialToSmelt(player, block);

          Material smeltResult = smeltingRecipes.get(materialToSmelt);

          if (smeltResult == null && hasLegendary) {
            smeltResult = materialToSmelt;
          }

          if (smeltResult == null) {
            return;
          }

          if (FurnaceStoneHandler.hasStone(enchant, "Rare") && Utils.chance(20.0)) {
            Material chainResult = alchemicalForgeChain.get(smeltResult);
            if (chainResult != null) smeltResult = chainResult;
          }

          EnchantDropManager.set(player.getUniqueId(), smeltResult);

          if (FurnaceStoneHandler.hasStone(enchant, "Common") && Utils.chance(20.0)) {
            FurnaceStoneHandler.setDoubleSmelt(player.getUniqueId());
          }

          if (FurnaceStoneHandler.hasStone(enchant, "Uncommon")) {
            String xpPath =
                org.benski.evolv3.core.constants.ConfigPaths.Default.BLOCK_DEFAULTS
                    + "."
                    + block.getType().name()
                    + ".XP";
            int blockXp =
                org.benski.evolv3.registry.ConfigRegistry.getInstance()
                    .get(org.benski.evolv3.core.constants.ConfigNames.DEFAULT)
                    .getInt(xpPath, 0);
            int bonus = (int) Math.max(1, blockXp * 0.10);
            profile.xp += bonus;
            PickaxeManager.getInstance().checkLevel(profile, player);
            profile.save(null);
          }

          if (FurnaceStoneHandler.hasStone(enchant, "Epic")) {
            applyFireChain(player, block, hasLegendary);
          }

          playSmeltEffects(block, player, materialToSmelt, smeltResult);
        });
  }

  /**
   * Determines the material to smelt, accounting for Alchemy upgrades and Frostbite. Reads
   * Alchemy's result from {@link EnchantDropManager} for chaining, since Alchemy fires first
   * (NORMAL priority) and has already written its result before Furnace (HIGH).
   *
   * @param player the player breaking the block (used for Fortune/Silk Touch context)
   * @param block the block being broken
   * @return the material to smelt
   */
  private Material getMaterialToSmelt(Player player, Block block) {
    Material alchemyResult = EnchantDropManager.get(player.getUniqueId());
    if (alchemyResult != null) {
      return alchemyResult;
    }

    if (block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) {
      String originalMaterial =
          block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY).get(0).asString();
      try {
        return Material.valueOf(originalMaterial);
      } catch (IllegalArgumentException e) {
      }
    }

    return block.getDrops(player.getInventory().getItemInMainHand()).stream()
        .filter(i -> i != null && !i.getType().isAir())
        .findFirst()
        .map(ItemStack::getType)
        .orElse(block.getType());
  }

  /**
   * Plays visual and audio effects for the smelting action.
   *
   * @param block the block being smelted
   * @param player the player triggering the effect
   * @param original the original material
   * @param smeltResult the smelted result material
   */
  private void playSmeltEffects(
      Block block, Player player, Material original, Material smeltResult) {
    EnchantPreference pref = getEnchantPref(player);

    if (pref.particles) {
      block
          .getWorld()
          .spawnParticle(
              Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.02);
    }

    if (pref.sound) {
      player.playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
    }

    if (pref.hologram
        && enchantsConfig.getBoolean(ConfigPaths.Enchants.Furnace.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          enchantsConfig
              .getString(
                  ConfigPaths.Enchants.Furnace.HOLOGRAM_MESSAGE, "&7%original% &a> &a%smelt%")
              .replace("%original%", formatMaterialName(original))
              .replace("%smelt%", formatMaterialName(smeltResult));
      Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
      AnimationRegistry.getInstance()
          .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
    }

    if (pref.message) {
      String chatMessage =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(ConfigPaths.Messages.Enchants.FURNACE_TRIGGERED);
      if (chatMessage != null && !chatMessage.isEmpty()) {
        chatMessage = chatMessage.replace("%original%", formatMaterialName(original));
        chatMessage = chatMessage.replace("%result%", formatMaterialName(smeltResult));
        player.sendMessage(Utils.formatMessage(player, chatMessage));
      }
    }

    if (pref.title) {
      String title =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.FURNACE_TRIGGERED_TITLE, "&#FF6B35&lFurnace");
      String subtitle =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.FURNACE_TRIGGERED_SUBTITLE,
                  "&#CCCCCC%original% &#FF6B35> &#CCCCCC%result%");
      subtitle =
          subtitle
              .replace("%original%", formatMaterialName(original))
              .replace("%result%", formatMaterialName(smeltResult));
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

  private EnchantPreference getEnchantPref(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreference();
    org.benski.evolv3.models.player.PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreference();
    String enchantName = enchantsConfig.getString(ConfigPaths.Enchants.Furnace.NAME, "Furnace");
    boolean defaultHologram =
        enchantsConfig.getBoolean(ConfigPaths.Enchants.Furnace.HOLOGRAM_DEFAULT_ENABLED, false);
    return profile.preferences.getOrCreateEnchantPref(enchantName, defaultHologram);
  }

  /**
   * Epic (Fire Chain): picks up to 5 random breakable blocks within a radius-3 sphere, breaks them,
   * smelts their drops, and delivers the results to the player's backpack. If Legendary is also
   * active, blocks without a smelt recipe use their own drop material.
   */
  private void applyFireChain(Player player, Block center, boolean hasLegendary) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    org.benski.evolv3.models.player.PlayerProfile pp = pdc != null ? pdc.getLocal(player) : null;
    if (pp == null) return;

    java.util.List<Block> candidates = new java.util.ArrayList<>();
    int radius = 3;
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          if (x == 0 && y == 0 && z == 0) continue;
          Block nearby = center.getRelative(x, y, z);
          if (nearby.getType().isAir()) continue;
          if (nearby.getType() == Material.BEDROCK) continue;
          if (!WorldGuardManager.getInstance().isWithinMineRegion(nearby.getLocation())) continue;
          candidates.add(nearby);
        }
      }
    }

    java.util.Collections.shuffle(candidates);
    int count = Math.min(5, candidates.size());

    for (int i = 0; i < count; i++) {
      Block target = candidates.get(i);
      Material dropMat =
          target.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)
              ? parseFrostbiteMeta(target)
              : target.getDrops(player.getInventory().getItemInMainHand()).stream()
                  .filter(d -> d != null && !d.getType().isAir())
                  .findFirst()
                  .map(ItemStack::getType)
                  .orElse(target.getType());
      Material smeltMat = smeltingRecipes.get(dropMat);
      if (smeltMat == null && hasLegendary) smeltMat = dropMat;
      if (smeltMat == null) continue;

      target
          .getWorld()
          .spawnParticle(
              Particle.FLAME, target.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.05);
      MineManager.getInstance().trackBlockAtLocation(target.getLocation());
      target.setType(Material.AIR);
      pp.backpack.addItem(player, smeltMat, 1, false);
    }

    if (count > 0) {
      BackpackListener.refresh(player);
      player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
    }
  }

  /** Reads the original drop material stored by Frostbite, falling back to the block type. */
  private static Material parseFrostbiteMeta(Block block) {
    try {
      return Material.valueOf(
          block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY).get(0).asString());
    } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
      return block.getType();
    }
  }

  /**
   * Gets the smelting result for a material.
   *
   * @param input the input material
   * @return the smelted output, or null if no recipe exists
   */
  public Material getSmeltResult(Material input) {
    return smeltingRecipes.get(input);
  }

  /**
   * Returns the Alchemical Forge chain result for the given smelted material, or null if no chain
   * entry exists. Used by AoEDropResolver to apply the Rare stone in the AoE resolve path.
   */
  public Material getAlchemicalForgeResult(Material input) {
    return alchemicalForgeChain.get(input);
  }
}
