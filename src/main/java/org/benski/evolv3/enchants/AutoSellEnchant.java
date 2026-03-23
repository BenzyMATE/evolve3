package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.backpack.BackpackItem;
import org.benski.evolv3.models.pickaxe.EnchantData;
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

import java.util.List;

/**
 * Listener for the AutoSell custom enchantment.
 * Gives a chance to automatically sell the entire backpack contents while mining.
 * Uses identical sell prices and helmet multiplier as the manual sell system.
 *
 * <p>Trigger chance scales with enchantment level:</p>
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance</li>
 *   <li>Example: Level 100/100 with maxChance 2.5 = 2.5% chance</li>
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class AutoSellEnchant implements Listener
{
    private final FileConfiguration enchantsConfig;
    private final JavaPlugin plugin;

    public AutoSellEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!item.getType().name().contains("PICKAXE")) {
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

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) return;

            EnchantData enchant = profile.enchants.getEnchant(Enchants.AUTO_SELL);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

            if (!Utils.chance(enchant.getCurrentChance())) return;

            sellBackpack(player, playerProfile, event.getBlock());
        });
    }

    private void sellBackpack(Player player, PlayerProfile playerProfile, Block block)
    {
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

        double finalValue = totalValue * multiplier;
        backpack.removeMarkedItems();
        playerProfile.addCoins(finalValue);
        playerProfile.save(null);

        playEffects(block, player, finalValue, totalItems);
    }

    private void playEffects(Block block, Player player, double coinsEarned, int itemCount)
    {
        EnchantPreference pref = getEnchantPref(player);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        if (pref.particles) {
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 20, 0.4, 0.4, 0.4, 0.0);
            block.getWorld().spawnParticle(Particle.CRIT, center, 10, 0.3, 0.3, 0.3, 0.1);
        }

        // Same sound as manual sell
        if (pref.sound) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        if (pref.hologram && enchantsConfig.getBoolean(ConfigPaths.Enchants.AutoSell.HOLOGRAM_ENABLED, true)) {
            String hologramText = enchantsConfig.getString(
                            ConfigPaths.Enchants.AutoSell.HOLOGRAM_MESSAGE,
                            "&#FFD700⛁ &#FFFFFF+%coins% &#888888(%items% items)")
                    .replace("%coins%", String.format("%.2f", coinsEarned))
                    .replace("%items%", String.valueOf(itemCount));
            Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        // Same sell summary message as manual sell
        if (pref.message) {
            FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
            List<String> sellMessages = messagesConfig.getStringList(ConfigPaths.Messages.Backpack.SELL_ITEMS);
            for (String line : sellMessages) {
                line = line.replace("%total_items%", String.valueOf(itemCount))
                        .replace("%total_amount%", String.format("%.2f", coinsEarned));
                player.sendMessage(Utils.formatMessage(player, line));
            }
        }

        if (pref.title) {
            String title    = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.AUTO_SELL_TRIGGERED_TITLE, "&#FFD700&lAutoSell");
            String subtitle = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.AUTO_SELL_TRIGGERED_SUBTITLE, "&#FFD700⛁ &#CCCCCC+%coins%");
            subtitle = subtitle
                    .replace("%coins%", String.format("%.2f", coinsEarned))
                    .replace("%items%", String.valueOf(itemCount));
            player.sendTitle(
                    Utils.formatMessage(player, title),
                    Utils.formatMessage(player, subtitle),
                    5, 30, 10);
        }
    }

    private double getSellMultiplier(Player player)
    {
        ArmorDataCenter armorDC = DataCenterRegistry.getInstance().getArmorDataCenter();
        if (armorDC == null) return 1.0;
        ArmorProfile armorProfile = armorDC.getLocal(player);
        return armorProfile != null ? armorProfile.getSellMultiplier() : 1.0;
    }

    private EnchantPreference getEnchantPref(Player player)
    {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return new EnchantPreference();
        PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return new EnchantPreference();
        String  enchantName     = enchantsConfig.getString(ConfigPaths.Enchants.AutoSell.NAME, "AutoSell");
        boolean defaultHologram = enchantsConfig.getBoolean(ConfigPaths.Enchants.AutoSell.HOLOGRAM_DEFAULT_ENABLED, false);
        return profile.preferences.getOrCreateEnchantPref(enchantName, defaultHologram, true);
    }
}
