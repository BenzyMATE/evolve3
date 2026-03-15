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
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
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
 * Listener for the Scavenger custom enchantment.
 * Gives a chance to discover extra backpack slots while mining.
 * Works independently of drop-modifying enchants (Alchemy, Furnace, Frostbite)
 * as it never touches EnchantDropManager.
 *
 * <p>Trigger chance scales with enchantment level:</p>
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance</li>
 *   <li>Example: Level 50/100 with maxChance 15 = 7.5% chance</li>
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class ScavengerEnchant implements Listener
{
    private final FileConfiguration enchantsConfig;
    private final FileConfiguration messagesConfig;
    private final JavaPlugin plugin;

    public ScavengerEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
        this.messagesConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
    }

    /**
     * Handles block break events to trigger the Scavenger effect.
     * Runs at NORMAL priority — no interaction with drop enchants needed.
     *
     * @param event the block break event
     */
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
        if (playerProfile.backpack.isFull()) return;

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) return;

            EnchantData enchant = profile.enchants.getEnchant(Enchants.SCAVENGER);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

            double triggerChance = enchant.getCurrentChance();
            if (!Utils.chance(triggerChance)) return;

            int maxTotalSlots = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.MAX_TOTAL_SLOTS, 500);
            if (playerProfile.backpack.getMaxSize() >= maxTotalSlots) return;

            int slotsMin   = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MIN, 1);
            int slotsMax   = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MAX, 3);
            int slotsFound = Utils.amount(slotsMin, slotsMax);

            // Cap so the backpack never exceeds the server maximum
            slotsFound = Math.min(slotsFound, maxTotalSlots - playerProfile.backpack.getMaxSize());

            playerProfile.backpack.setMaxSize(playerProfile.backpack.getMaxSize() + slotsFound);
            playerProfile.save(null);
            BackpackListener.refresh(player);

            playEffects(event.getBlock(), player, slotsFound);
        });
    }

    /**
     * Plays visual and audio feedback for the slot discovery.
     *
     * @param block      the block that was mined
     * @param player     the player who triggered the effect
     * @param slotsFound the number of slots discovered
     */
    private void playEffects(Block block, Player player, int slotsFound)
    {
        EnchantPreference pref = getEnchantPref(player);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        if (pref.particles) {
            block.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 8, 0.2, 0.2, 0.2, 0.05);
        }

        if (pref.sound) {
            player.playSound(block.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
        }

        if (pref.hologram && enchantsConfig.getBoolean(ConfigPaths.Enchants.Scavenger.HOLOGRAM_ENABLED, true)) {
            String hologramText = enchantsConfig.getString(
                            ConfigPaths.Enchants.Scavenger.HOLOGRAM_MESSAGE,
                            "&#FFD700+%slots% &#CCCCCCBackpack Slots!")
                    .replace("%slots%", String.valueOf(slotsFound));
            Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message) {
            String chatMessage = messagesConfig.getString(ConfigPaths.Messages.Enchants.SCAVENGER_TRIGGERED);
            if (chatMessage != null && !chatMessage.isEmpty()) {
                chatMessage = chatMessage.replace("%slots%", String.valueOf(slotsFound));
                player.sendMessage(Utils.formatMessage(player, chatMessage));
            }
        }

        if (pref.title) {
            String title    = messagesConfig.getString(ConfigPaths.Messages.Enchants.SCAVENGER_TRIGGERED_TITLE, "&#FFD700&lScavenger");
            String subtitle = messagesConfig.getString(ConfigPaths.Messages.Enchants.SCAVENGER_TRIGGERED_SUBTITLE, "&#CCCCCC+%slots% Backpack Slots!");
            subtitle = subtitle.replace("%slots%", String.valueOf(slotsFound));
            player.sendTitle(
                    Utils.formatMessage(player, title),
                    Utils.formatMessage(player, subtitle),
                    5, 30, 10);
        }
    }

    private EnchantPreference getEnchantPref(Player player)
    {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return new EnchantPreference();
        PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return new EnchantPreference();
        String enchantName = enchantsConfig.getString(ConfigPaths.Enchants.Scavenger.NAME, "Scavenger");
        return profile.preferences.getOrCreateEnchantPref(enchantName);
    }
}
