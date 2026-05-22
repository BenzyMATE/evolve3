package org.benski.evolv3.enchants;

import java.time.Duration;
import java.util.List;
import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.backpack.BackpackItem;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Particle;
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

/**
 * Listener for the AutoSell custom enchantment. Gives a chance to automatically sell the entire
 * backpack contents while mining. Uses identical sell prices and helmet multiplier as the manual
 * sell system.
 *
 * <p>Trigger chance scales with enchantment level:
 *
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance
 *   <li>Example: Level 100/100 with maxChance 2.5 = 2.5% chance
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class AutoSellEnchant implements Listener {
  private final FileConfiguration enchantsConfig;
  private final JavaPlugin plugin;

  public AutoSellEnchant(JavaPlugin plugin) {
    this.plugin = plugin;
    this.enchantsConfig = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
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

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) return;
    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;

    if (playerProfile.backpack.getItems().isEmpty()) return;

    PlayerDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pickaxeDC == null) return;
    Pickaxe profile = pickaxeDC.getPickaxeForPlayer(player);
    if (profile != null && profile.enchants != null) {
      EnchantData enchant = profile.enchants.getEnchant(Enchants.AUTO_SELL);
      if (enchant != null && enchant.enabled && enchant.level > 0) {
        double triggerChance = enchant.getCurrentChance();

        if (AutoSellStoneHandler.hasStone(enchant, "Uncommon")) {
          triggerChance += 5.0;
        }

        boolean hasLegendary = AutoSellStoneHandler.hasStone(enchant, "Legendary");
        boolean backpackFull = playerProfile.backpack.isFull();

        if (Utils.chance(triggerChance) || (hasLegendary && backpackFull)) {
          if (AutoSellStoneHandler.hasStone(enchant, "Epic")) {
            AutoSellStoneHandler.getInstance().activateGoldRush(player.getUniqueId());
          }

          sellBackpack(player, playerProfile, enchant, event.getBlock());
        }
      }
    }
  }

  private void sellBackpack(
      Player player, PlayerProfile playerProfile, EnchantData enchant, Block block) {
    Backpack backpack = playerProfile.backpack;
    List<BackpackItem> items = backpack.getItems();
    if (items.isEmpty()) return;

    FileConfiguration defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double multiplier = getSellMultiplier(player);
    double totalValue = 0;
    int totalItems = 0;

    for (BackpackItem item : items) {
      String path = ConfigPaths.Default.BLOCK_DEFAULTS + "." + item.getMaterial().name() + ".Sell";
      totalValue += defaultConfig.getDouble(path, 0.0) * item.getAmount();
      totalItems += item.getAmount();
      item.markForDeletion();
    }

    if (AutoSellStoneHandler.hasStone(enchant, "Common")) {
      multiplier *= 1.10;
    }

    if (AutoSellStoneHandler.getInstance().isGoldRushActive(player.getUniqueId())) {
      multiplier *= AutoSellStoneHandler.GOLD_RUSH_MULTIPLIER;
    }

    double finalValue = totalValue * multiplier;
    backpack.removeMarkedItems();
    playerProfile.addCoins(finalValue);

    if (AutoSellStoneHandler.hasStone(enchant, "Rare")) {
      playerProfile.addEssence((int) (finalValue * 0.10));
    }

    playerProfile.save(null);

    playEffects(block, player, finalValue, totalItems);
  }

  private void playEffects(Block block, Player player, double coinsEarned, int itemCount) {
    EnchantPreference pref = getEnchantPref(player);
    Location center = block.getLocation().add(0.5, 0.5, 0.5);

    if (pref.particles) {
      block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 20, 0.4, 0.4, 0.4, 0.0);
      block.getWorld().spawnParticle(Particle.CRIT, center, 10, 0.3, 0.3, 0.3, 0.1);
    }

    if (pref.sound) {
      player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    if (pref.hologram
        && enchantsConfig.getBoolean(ConfigPaths.Enchants.AutoSell.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          enchantsConfig
              .getString(
                  ConfigPaths.Enchants.AutoSell.HOLOGRAM_MESSAGE,
                  "&#FFD700â› &#FFFFFF+%coins% &#888888(%items% items)")
              .replace("%coins%", String.format("%.2f", coinsEarned))
              .replace("%items%", String.valueOf(itemCount));
      Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
      AnimationRegistry.getInstance()
          .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
    }

    if (pref.message) {
      FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
      List<String> sellMessages =
          messagesConfig.getStringList(ConfigPaths.Messages.Backpack.SELL_ITEMS);
      for (String line : sellMessages) {
        line =
            line.replace("%total_items%", String.valueOf(itemCount))
                .replace("%total_amount%", String.format("%.2f", coinsEarned));
        player.sendMessage(Utils.formatMessage(player, line));
      }
    }

    if (pref.title) {
      String title =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.AUTO_SELL_TRIGGERED_TITLE, "&#FFD700&lAutoSell");
      String subtitle =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.AUTO_SELL_TRIGGERED_SUBTITLE,
                  "&#FFD700â› &#CCCCCC+%coins%");
      subtitle =
          subtitle
              .replace("%coins%", String.format("%.2f", coinsEarned))
              .replace("%items%", String.valueOf(itemCount));
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

  private double getSellMultiplier(Player player) {
    ArmorDataCenter armorDC = DataCenterRegistry.getInstance().getArmorDataCenter();
    if (armorDC == null) return 1.0;
    ArmorProfile armorProfile = armorDC.getLocal(player);
    return armorProfile != null ? armorProfile.getSellMultiplier() : 1.0;
  }

  private EnchantPreference getEnchantPref(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreference();
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreference();
    String enchantName = enchantsConfig.getString(ConfigPaths.Enchants.AutoSell.NAME, "AutoSell");
    boolean defaultHologram =
        enchantsConfig.getBoolean(ConfigPaths.Enchants.AutoSell.HOLOGRAM_DEFAULT_ENABLED, false);
    return profile.preferences.getOrCreateEnchantPref(enchantName, defaultHologram, true);
  }
}
