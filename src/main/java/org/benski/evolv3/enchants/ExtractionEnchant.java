package org.benski.evolv3.enchants;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for the Extraction custom enchantment. Increases the player's base essence discovery
 * chance while mining.
 *
 * <p>At NORMAL priority, writes the bonus chance to {@link #pendingBonus} so {@code BreakListener}
 * (HIGHEST) can include it in the unified essence roll. This means Extraction enhances the existing
 * essence animation rather than firing a separate one — the player simply sees essence more often.
 *
 * <p>Bonus chance scales with enchantment level:
 *
 * <ul>
 *   <li>Bonus = (level / maxLevel) * maxChance
 *   <li>Example: Level 100/100 with maxChance 10 = +10% bonus
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class ExtractionEnchant implements Listener {
  @Getter private static ExtractionEnchant instance;

  /** Pending bonus chance per player, consumed by BreakListener at HIGHEST priority. */
  private static final Map<UUID, Double> pendingBonus = new ConcurrentHashMap<>();

  /** Pending essence amount multiplier from Rare (2×) or Epic (3×) stone. */
  private static final Map<UUID, Integer> pendingEssenceMultiplier = new ConcurrentHashMap<>();

  /** Flag set when the player has a Legendary stone; BreakListener tracks the 5th-hit proc. */
  private static final Map<UUID, Boolean> pendingLegendaryActive = new ConcurrentHashMap<>();

  public ExtractionEnchant() {
    instance = this;
  }

  /**
   * Returns and removes the pending essence discovery bonus for a player. Called by {@code
   * BreakListener} after all NORMAL/HIGH enchant listeners have fired.
   *
   * @param uuid the player's UUID
   * @return the bonus chance to add (0.0 if Extraction did not trigger)
   */
  public static double popBonus(UUID uuid) {
    Double bonus = pendingBonus.remove(uuid);
    return bonus != null ? bonus : 0.0;
  }

  /**
   * Returns and removes the pending essence amount multiplier for a player. Returns 1 (no
   * multiplier) if not set.
   *
   * @param uuid the player's UUID
   * @return multiplier (1, 2, or 3)
   */
  public static int popEssenceMultiplier(UUID uuid) {
    Integer m = pendingEssenceMultiplier.remove(uuid);
    return m != null ? m : 1;
  }

  /**
   * Returns and removes the Legendary active flag for a player.
   *
   * @param uuid the player's UUID
   * @return true if Legendary stone is socketed this break
   */
  public static boolean popLegendaryActive(UUID uuid) {
    Boolean flag = pendingLegendaryActive.remove(uuid);
    return flag != null && flag;
  }

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

    PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    if (dataCenter == null) return;
    dataCenter.get(
        player,
        profile -> {
          if (profile == null || profile.enchants == null) return;

          EnchantData enchant = profile.enchants.getEnchant(Enchants.EXTRACTION);
          if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

          FileConfiguration enchantsCfg = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
          double maxChance =
              enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_CHANCE, 10.0);
          double maxLevel = enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_LEVEL, 100.0);

          double bonus = (enchant.level / maxLevel) * maxChance;

          if (ExtractionStoneHandler.hasStone(enchant, "Common")) bonus *= 1.15;

          if (ExtractionStoneHandler.hasStone(enchant, "Uncommon")
              && ExtractionStoneHandler.incrementAndCheckSurge(player.getUniqueId())) {
            bonus *= 3.0;
          }

          pendingBonus.put(player.getUniqueId(), bonus);

          if (ExtractionStoneHandler.hasStone(enchant, "Rare")) {
            pendingEssenceMultiplier.put(player.getUniqueId(), 2);
          }

          if (ExtractionStoneHandler.hasStone(enchant, "Epic")) {
            pendingEssenceMultiplier.put(player.getUniqueId(), 3);
          }

          if (ExtractionStoneHandler.hasStone(enchant, "Legendary")) {
            pendingLegendaryActive.put(player.getUniqueId(), true);
          }
        });
  }

  /**
   * Applies the Extraction essence-discovery effect to a single block broken by an AoE enchant.
   * Rolls base essence chance + Extraction bonus, awards essence, and plays animations.
   */
  public void tryTriggerForAoE(
      Player player, Block block, PickaxeProfile pickaxeProfile, PlayerProfile playerProfile) {
    FileConfiguration defaultCfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double baseChance = defaultCfg.getDouble(ConfigPaths.Default.Essence.DISCOVERY_CHANCE, 5.0);

    double extractionBonus = 0.0;
    int essenceMultiplier = 1;
    boolean legendaryActive = false;

    if (pickaxeProfile != null && pickaxeProfile.enchants != null) {
      EnchantData enchant = pickaxeProfile.enchants.getEnchant(Enchants.EXTRACTION);
      if (enchant != null && enchant.enabled && enchant.level > 0) {
        FileConfiguration enchantsCfg = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
        double maxChance = enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_CHANCE, 10.0);
        double maxLevel = enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_LEVEL, 100.0);
        extractionBonus = (enchant.level / maxLevel) * maxChance;

        if (ExtractionStoneHandler.hasStone(enchant, "Common")) extractionBonus *= 1.15;

        if (ExtractionStoneHandler.hasStone(enchant, "Uncommon")
            && ExtractionStoneHandler.incrementAndCheckSurge(player.getUniqueId())) {
          extractionBonus *= 3.0;
        }

        if (ExtractionStoneHandler.hasStone(enchant, "Epic")) essenceMultiplier = 3;
        else if (ExtractionStoneHandler.hasStone(enchant, "Rare")) essenceMultiplier = 2;

        if (ExtractionStoneHandler.hasStone(enchant, "Legendary")) legendaryActive = true;
      }
    }

    if (!Utils.chance(baseChance + extractionBonus)) return;

    if (legendaryActive
        && ExtractionStoneHandler.incrementAndCheckLegendary(player.getUniqueId())) {
      essenceMultiplier = 5;
    }

    int min = defaultCfg.getInt(ConfigPaths.Default.Essence.DISCOVERY_MIN_AMOUNT, 1);
    int max = defaultCfg.getInt(ConfigPaths.Default.Essence.DISCOVERY_MAX_AMOUNT, 5);
    int amount = Utils.amount(min, max) * essenceMultiplier;

    playerProfile.essence += amount;

    Location animLoc = block.getLocation().clone().add(0.5, 0.5, 0.5);
    Location hologramLoc = block.getLocation().clone().add(0.5, 1.5, 0.5);
    AnimationRegistry.getInstance().play("essence", animLoc, List.of(player), amount);

    if (playerProfile.preferences.essenceHologram
        && defaultCfg.getBoolean(ConfigPaths.Default.Essence.HOLOGRAM_ENABLED, true)) {
      String text =
          defaultCfg.getString(
              ConfigPaths.Default.Essence.HOLOGRAM_TEXT,
              "&#B366D9🔥 &#FFFFFF&l+ %amount% &#B366D9Essence");
      text = text.replace("%amount%", Utils.formatNumber(amount));
      AnimationRegistry.getInstance().play("hologram", hologramLoc, List.of(player), text);
    }
  }

  /**
   * Silent variant for large AoE enchants (e.g. Jackhammer). Applies all the same stone/chance
   * logic and awards essence, but plays no animations.
   *
   * @return essence awarded this block, or 0 if not triggered
   */
  public int tryTriggerForAoESilent(
      Player player, Block block, PickaxeProfile pickaxeProfile, PlayerProfile playerProfile) {
    FileConfiguration defaultCfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double baseChance = defaultCfg.getDouble(ConfigPaths.Default.Essence.DISCOVERY_CHANCE, 5.0);

    double extractionBonus = 0.0;
    int essenceMultiplier = 1;
    boolean legendaryActive = false;

    if (pickaxeProfile != null && pickaxeProfile.enchants != null) {
      EnchantData enchant = pickaxeProfile.enchants.getEnchant(Enchants.EXTRACTION);
      if (enchant != null && enchant.enabled && enchant.level > 0) {
        FileConfiguration enchantsCfg = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
        double maxChance = enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_CHANCE, 10.0);
        double maxLevel = enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_LEVEL, 100.0);
        extractionBonus = (enchant.level / maxLevel) * maxChance;

        if (ExtractionStoneHandler.hasStone(enchant, "Common")) extractionBonus *= 1.15;

        if (ExtractionStoneHandler.hasStone(enchant, "Uncommon")
            && ExtractionStoneHandler.incrementAndCheckSurge(player.getUniqueId())) {
          extractionBonus *= 3.0;
        }

        if (ExtractionStoneHandler.hasStone(enchant, "Epic")) essenceMultiplier = 3;
        else if (ExtractionStoneHandler.hasStone(enchant, "Rare")) essenceMultiplier = 2;

        if (ExtractionStoneHandler.hasStone(enchant, "Legendary")) legendaryActive = true;
      }
    }

    if (!Utils.chance(baseChance + extractionBonus)) return 0;

    if (legendaryActive
        && ExtractionStoneHandler.incrementAndCheckLegendary(player.getUniqueId())) {
      essenceMultiplier = 5;
    }

    int min = defaultCfg.getInt(ConfigPaths.Default.Essence.DISCOVERY_MIN_AMOUNT, 1);
    int max = defaultCfg.getInt(ConfigPaths.Default.Essence.DISCOVERY_MAX_AMOUNT, 5);
    int amount = Utils.amount(min, max) * essenceMultiplier;
    playerProfile.essence += amount;
    return amount;
  }
}
