package org.benski.evolv3.enchants.passive;

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
import org.benski.evolv3.core.managers.world.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
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
 * Listener for the Merchant custom enchantment. Gives a chance to find coins while mining. Works
 * independently of drop-modifying enchants (Alchemy, Furnace, Frostbite) as it never touches
 * EnchantDropManager.
 *
 * <p>Trigger chance scales with enchantment level:
 *
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance
 *   <li>Example: Level 50/100 with maxChance 10 = 5% chance
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class MerchantEnchant implements Listener {
  @lombok.Getter private static MerchantEnchant instance;

  private final FileConfiguration enchantsConfig;
  private final JavaPlugin plugin;

  /**
   * Creates and registers the singleton MerchantEnchant listener.
   *
   * @param plugin the owning plugin
   */
  public MerchantEnchant(JavaPlugin plugin) {
    instance = this;
    this.plugin = plugin;
    this.enchantsConfig = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
  }

  /**
   * Handles block break events to trigger the Merchant effect. Runs at NORMAL priority â€” no
   * interaction with drop enchants needed.
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
    if (playerDC == null) return;
    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) return;
    if (playerProfile.backpack.isFull()) return;

    PlayerDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pickaxeDC == null) return;
    Pickaxe profile = pickaxeDC.getPickaxeForPlayer(player);
    if (profile != null && profile.enchants != null) {
      EnchantData enchant = profile.enchants.getEnchant(Enchants.MERCHANT);
      if (enchant != null && enchant.enabled && enchant.level > 0) {
        double triggerChance = enchant.getCurrentChance();
        if (MerchantStoneHandler.hasStone(enchant, "Common")) triggerChance += 2.0;
        if (Utils.chance(triggerChance)) {
          int coinsMin = enchantsConfig.getInt(ConfigPaths.Enchants.Merchant.COINS_MIN, 1);
          int coinsMax = enchantsConfig.getInt(ConfigPaths.Enchants.Merchant.COINS_MAX, 50);
          double coinsFound = Utils.amount(coinsMin, coinsMax);

          if (MerchantStoneHandler.hasStone(enchant, "Uncommon")) {
            String xpPath =
                ConfigPaths.Default.BLOCK_DEFAULTS
                    + "."
                    + event.getBlock().getType().name()
                    + ".XP";
            int blockXp = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT).getInt(xpPath, 0);
            if (blockXp >= 26) coinsFound *= 2.0;
            else if (blockXp >= 11) coinsFound *= 1.5;
            else if (blockXp >= 1) coinsFound *= 1.2;
          }

          if (MerchantStoneHandler.hasStone(enchant, "Rare")) {
            if (MerchantStoneHandler.isGoldenWindowActive(player.getUniqueId())) {
              coinsFound *= 1.5;
            }
            MerchantStoneHandler.activateGoldenWindow(player.getUniqueId());
          }

          if (MerchantStoneHandler.hasStone(enchant, "Legendary")) coinsFound *= 3.0;

          int finalCoins = (int) Math.max(1, coinsFound);
          playerProfile.addCoins(finalCoins);

          if (MerchantStoneHandler.hasStone(enchant, "Epic")) {
            int essenceBonus = (int) Math.max(1, finalCoins * 0.10);
            playerProfile.addEssence(essenceBonus);
          }

          playerProfile.save(null);
          playEffects(event.getBlock(), player, finalCoins);
        }
      }
    }
  }

  /**
   * Applies the Merchant effect to a single block broken by an AoE enchant. Shares the same logic
   * as the event-driven handler but works with already-resolved profiles.
   */
  public void tryTriggerForAoE(
      Player player,
      Block block,
      Pickaxe pickaxe,
      org.benski.evolv3.models.player.PlayerProfile playerProfile) {
    if (pickaxe == null || pickaxe.enchants == null) return;

    EnchantData enchant = pickaxe.enchants.getEnchant(Enchants.MERCHANT);
    if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

    double triggerChance = enchant.getCurrentChance();
    if (MerchantStoneHandler.hasStone(enchant, "Common")) triggerChance += 2.0;
    if (!Utils.chance(triggerChance)) return;

    int coinsMin = enchantsConfig.getInt(ConfigPaths.Enchants.Merchant.COINS_MIN, 1);
    int coinsMax = enchantsConfig.getInt(ConfigPaths.Enchants.Merchant.COINS_MAX, 50);
    double coinsFound = Utils.amount(coinsMin, coinsMax);

    if (MerchantStoneHandler.hasStone(enchant, "Uncommon")) {
      String xpPath = ConfigPaths.Default.BLOCK_DEFAULTS + "." + block.getType().name() + ".XP";
      int blockXp = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT).getInt(xpPath, 0);
      if (blockXp >= 26) coinsFound *= 2.0;
      else if (blockXp >= 11) coinsFound *= 1.5;
      else if (blockXp >= 1) coinsFound *= 1.2;
    }

    if (MerchantStoneHandler.hasStone(enchant, "Rare")) {
      if (MerchantStoneHandler.isGoldenWindowActive(player.getUniqueId())) {
        coinsFound *= 1.5;
      }
      MerchantStoneHandler.activateGoldenWindow(player.getUniqueId());
    }

    if (MerchantStoneHandler.hasStone(enchant, "Legendary")) coinsFound *= 3.0;

    int finalCoins = (int) Math.max(1, coinsFound);
    playerProfile.addCoins(finalCoins);

    if (MerchantStoneHandler.hasStone(enchant, "Epic")) {
      int essenceBonus = (int) Math.max(1, finalCoins * 0.10);
      playerProfile.addEssence(essenceBonus);
    }

    playerProfile.save(null);
    playEffects(block, player, finalCoins);
  }

  /**
   * Silent variant for AoE accumulation. Applies all logic and awards coins but plays no effects.
   *
   * @return coins awarded this block, or 0 if not triggered
   */
  public int tryTriggerForAoESilent(
      Player player,
      Block block,
      Pickaxe pickaxe,
      org.benski.evolv3.models.player.PlayerProfile playerProfile) {
    if (pickaxe == null || pickaxe.enchants == null) return 0;

    EnchantData enchant = pickaxe.enchants.getEnchant(Enchants.MERCHANT);
    if (enchant == null || !enchant.enabled || enchant.level <= 0) return 0;

    double triggerChance = enchant.getCurrentChance();
    if (MerchantStoneHandler.hasStone(enchant, "Common")) triggerChance += 2.0;
    if (!Utils.chance(triggerChance)) return 0;

    int coinsMin = enchantsConfig.getInt(ConfigPaths.Enchants.Merchant.COINS_MIN, 1);
    int coinsMax = enchantsConfig.getInt(ConfigPaths.Enchants.Merchant.COINS_MAX, 50);
    double coinsFound = Utils.amount(coinsMin, coinsMax);

    if (MerchantStoneHandler.hasStone(enchant, "Uncommon")) {
      String xpPath = ConfigPaths.Default.BLOCK_DEFAULTS + "." + block.getType().name() + ".XP";
      int blockXp = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT).getInt(xpPath, 0);
      if (blockXp >= 26) coinsFound *= 2.0;
      else if (blockXp >= 11) coinsFound *= 1.5;
      else if (blockXp >= 1) coinsFound *= 1.2;
    }
    if (MerchantStoneHandler.hasStone(enchant, "Rare")) {
      if (MerchantStoneHandler.isGoldenWindowActive(player.getUniqueId())) coinsFound *= 1.5;
      MerchantStoneHandler.activateGoldenWindow(player.getUniqueId());
    }
    if (MerchantStoneHandler.hasStone(enchant, "Legendary")) coinsFound *= 3.0;

    int finalCoins = (int) Math.max(1, coinsFound);
    playerProfile.addCoins(finalCoins);

    if (MerchantStoneHandler.hasStone(enchant, "Epic")) {
      playerProfile.addEssence((int) Math.max(1, finalCoins * 0.10));
    }

    return finalCoins;
  }

  /**
   * Plays visual and audio feedback for the coin discovery.
   *
   * @param block the block that was mined
   * @param player the player who triggered the effect
   * @param coinsFound the number of coins found
   */
  private void playEffects(Block block, Player player, int coinsFound) {
    EnchantPreference pref = getEnchantPref(player);
    Location center = block.getLocation().add(0.5, 0.5, 0.5);

    if (pref.particles) {
      block.getWorld().spawnParticle(Particle.CRIT, center, 10, 0.2, 0.2, 0.2, 0.1);
      block.getWorld().spawnParticle(Particle.ENCHANT, center, 15, 0.3, 0.3, 0.3, 0.5);
    }

    if (pref.sound) {
      player.playSound(block.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.8f);
    }

    if (pref.hologram
        && enchantsConfig.getBoolean(ConfigPaths.Enchants.Merchant.HOLOGRAM_ENABLED, true)) {
      String hologramText =
          enchantsConfig
              .getString(
                  ConfigPaths.Enchants.Merchant.HOLOGRAM_MESSAGE,
                  "&#FFD700+%coins% &#CCCCCCâ› Coins!")
              .replace("%coins%", String.valueOf(coinsFound));
      Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
      AnimationRegistry.getInstance()
          .play(new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
    }

    if (pref.message) {
      String chatMessage =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(ConfigPaths.Messages.Enchants.MERCHANT_TRIGGERED);
      if (chatMessage != null && !chatMessage.isEmpty()) {
        chatMessage = chatMessage.replace("%coins%", String.valueOf(coinsFound));
        player.sendMessage(Utils.formatMessage(player, chatMessage));
      }
    }

    if (pref.title) {
      String title =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.MERCHANT_TRIGGERED_TITLE, "&#FFD700&lMerchant");
      String subtitle =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Enchants.MERCHANT_TRIGGERED_SUBTITLE,
                  "&#CCCCCC+%coins% â› Coins!");
      subtitle = subtitle.replace("%coins%", String.valueOf(coinsFound));
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

  /** Returns the enchant pref. */
  private EnchantPreference getEnchantPref(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreference();
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreference();
    String enchantName = enchantsConfig.getString(ConfigPaths.Enchants.Merchant.NAME, "Merchant");
    boolean defaultHologram =
        enchantsConfig.getBoolean(ConfigPaths.Enchants.Merchant.HOLOGRAM_DEFAULT_ENABLED, false);
    return profile.preferences.getOrCreateEnchantPref(enchantName, defaultHologram);
  }
}
