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
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
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
 * Listener for the Alchemy custom enchantment. Upgrades blocks to a higher tier material when
 * mined. Triggers before Furnace for chained transmutation effects.
 *
 * <p>Trigger chance scales with enchantment level:
 *
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance
 *   <li>Example: Level 50/100 with maxChance 20 = 10% chance
 * </ul>
 *
 * <p>Upgrade recipes are configurable in enchants.yml
 */
@ExtensionMethod(ItemStackExtensions.class)
public class AlchemyEnchant implements Listener {
  @lombok.Getter private static AlchemyEnchant instance;

  private final FileConfiguration enchantsConfig;
  private final JavaPlugin plugin;

  /** Metadata key for passing upgraded result to other enchants and drop handlers. */
  public static final String ALCHEMY_RESULT_KEY = "alchemy_result";

  /** Cached upgrade recipes for performance. */
  private final Map<Material, Material> upgradeRecipes = new HashMap<>();

  public AlchemyEnchant(JavaPlugin plugin) {
    instance = this;
    this.plugin = plugin;
    this.enchantsConfig = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
    loadUpgradeRecipes();
  }

  /** Loads upgrade recipes from configuration. */
  private void loadUpgradeRecipes() {
    upgradeRecipes.clear();
    ConfigurationSection upgradesSection =
        enchantsConfig.getConfigurationSection(ConfigPaths.Enchants.Alchemy.UPGRADES);
    if (upgradesSection == null) {
      return;
    }

    for (String key : upgradesSection.getKeys(false)) {
      try {
        Material input = Material.valueOf(key.toUpperCase());
        Material output = Material.valueOf(upgradesSection.getString(key).toUpperCase());
        upgradeRecipes.put(input, output);
      } catch (IllegalArgumentException e) {
        plugin
            .getLogger()
            .warning("Invalid alchemy recipe: " + key + " -> " + upgradesSection.getString(key));
      }
    }
  }

  /**
   * Handles the block break event to trigger alchemy effect. Uses NORMAL priority to run before
   * Furnace (which uses HIGH priority).
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

    PlayerDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pickaxeDC == null) return;
    Pickaxe profile = pickaxeDC.getPickaxeForPlayer(player);
    if (profile != null && profile.enchants != null) {
      EnchantData enchant = profile.enchants.getEnchant(Enchants.ALCHEMY);
      if (enchant != null && enchant.enabled && enchant.level > 0) {
        double triggerChance = enchant.getCurrentChance();

        if (AlchemyStoneHandler.hasStone(enchant, "Uncommon")) {
          triggerChance += 10.0;
        }

        boolean legendary = AlchemyStoneHandler.hasStone(enchant, "Legendary");
        if (legendary || Utils.chance(triggerChance)) {
          Block block = event.getBlock();
          Material materialToUpgrade = getMaterialToUpgrade(block, player);

          Material upgradeResult = upgradeRecipes.get(materialToUpgrade);
          if (upgradeResult != null) {
            if (AlchemyStoneHandler.hasStone(enchant, "Common") && Utils.chance(15.0)) {
              Material tier2 = upgradeRecipes.get(upgradeResult);
              if (tier2 != null) upgradeResult = tier2;
            }

            if (AlchemyStoneHandler.hasStone(enchant, "Rare") && Utils.chance(20.0)) {
              Material tier2 = upgradeRecipes.get(upgradeResult);
              if (tier2 != null) upgradeResult = tier2;
            }

            if (AlchemyStoneHandler.hasStone(enchant, "Epic") && Utils.chance(30.0)) {
              upgradeResult = getWithinTwosOfMax(upgradeResult);
            }

            EnchantDropManager.set(player.getUniqueId(), upgradeResult);

            playUpgradeEffects(block, player, materialToUpgrade, upgradeResult);
          }
        }
      }
    }
  }

  /**
   * Determines the material to upgrade, accounting for Frostbite frozen blocks.
   *
   * @param block the block being broken
   * @param player the player breaking the block (used for Fortune/Silk Touch context)
   * @return the material to upgrade
   */
  private Material getMaterialToUpgrade(Block block, Player player) {
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
   * Plays visual and audio effects for the alchemy action.
   *
   * @param block the block being upgraded
   * @param player the player triggering the effect
   * @param original the original material
   * @param upgradeResult the upgraded result material
   */
  private void playUpgradeEffects(
      Block block, Player player, Material original, Material upgradeResult) {
    EnchantPreference pref = getEnchantPref(player);

    if (pref.particles) {
      block
          .getWorld()
          .spawnParticle(
              Particle.HAPPY_VILLAGER,
              block.getLocation().add(0.5, 0.5, 0.5),
              10,
              0.2,
              0.2,
              0.2,
              0.02);
    }

    if (pref.sound) {
      player.playSound(block.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    if (pref.hologram
        && enchantsConfig.getBoolean(ConfigPaths.Enchants.Alchemy.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          enchantsConfig
              .getString(
                  ConfigPaths.Enchants.Alchemy.HOLOGRAM_MESSAGE, "&7%original% &a> &d%upgrade%")
              .replace("%original%", formatMaterialName(original))
              .replace("%upgrade%", formatMaterialName(upgradeResult));
      Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
      AnimationRegistry.getInstance()
          .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
    }

    if (pref.message) {
      String chatMessage =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(ConfigPaths.Messages.Enchants.ALCHEMY_TRIGGERED);
      if (chatMessage != null && !chatMessage.isEmpty()) {
        chatMessage = chatMessage.replace("%original%", formatMaterialName(original));
        chatMessage = chatMessage.replace("%upgrade%", formatMaterialName(upgradeResult));
        player.sendMessage(Utils.formatMessage(player, chatMessage));
      }
    }

    if (pref.title) {
      String title =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.ALCHEMY_TRIGGERED_TITLE, "&#B366D9&lAlchemy");
      String subtitle =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.ALCHEMY_TRIGGERED_SUBTITLE,
                  "&#CCCCCC%original% &#B366D9> &#CCCCCC%upgrade%");
      subtitle =
          subtitle
              .replace("%original%", formatMaterialName(original))
              .replace("%upgrade%", formatMaterialName(upgradeResult));
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
    String enchantName = enchantsConfig.getString(ConfigPaths.Enchants.Alchemy.NAME, "Alchemy");
    return profile.preferences.getOrCreateEnchantPref(enchantName);
  }

  /**
   * Gets the upgrade result for a material.
   *
   * @param input the input material
   * @return the upgraded output, or null if no recipe exists
   */
  public Material getUpgradeResult(Material input) {
    return upgradeRecipes.get(input);
  }

  /**
   * Traces the upgrade chain from {@code start} to find the result that is at most 2 tiers below
   * the end of the chain (Epic Transcend stone effect). Guards against circular recipes with a
   * 20-step limit.
   *
   * @param start the current upgrade result
   * @return a material within 2 tiers of the chain's maximum
   */
  private Material getWithinTwosOfMax(Material start) {
    java.util.List<Material> chain = new java.util.ArrayList<>();
    chain.add(start);
    Material current = start;
    int guard = 0;
    while (guard++ < 20) {
      Material next = upgradeRecipes.get(current);
      if (next == null || chain.contains(next)) break;
      chain.add(next);
      current = next;
    }
    int targetIndex = Math.max(0, chain.size() - 2);
    return chain.get(targetIndex);
  }
}
