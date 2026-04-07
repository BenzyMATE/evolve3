package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.AoEDropResolver;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.meta.ItemMeta;
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
    @lombok.Getter
    private static ScavengerEnchant instance;

    private final FileConfiguration enchantsConfig;
    private final JavaPlugin plugin;

    public ScavengerEnchant(JavaPlugin plugin)
    {
        instance = this;
        this.plugin = plugin;
        this.enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
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

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) return;

            EnchantData enchant = profile.enchants.getEnchant(Enchants.SCAVENGER);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

            double triggerChance = enchant.getCurrentChance();
            if (!Utils.chance(triggerChance)) return;

            // Rare (Prospector): 10% chance to award atoms instead of slots
            if (ScavengerStoneHandler.hasStone(enchant, "Rare") && Utils.chance(10.0)) {
                int atoms = Utils.amount(1, 2);
                profile.evolutionAtoms += atoms;
                PickaxeManager.getInstance().checkAtoms(profile, player);
                profile.save(null);
                return;
            }

            if (playerProfile.backpack.isFull()) return;
            int tierCap = getTierCap(player);
            if (playerProfile.backpack.getMaxSize() >= tierCap) return;

            int slotsMin = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MIN, 1);
            int slotsMax = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MAX, 3);

            // Common (Extra Pocket): +1 slotsMin
            if (ScavengerStoneHandler.hasStone(enchant, "Common")) slotsMin += 1;

            int slotsFound = Utils.amount(slotsMin, slotsMax);

            // Epic (Double Haul): double slots if window is active, then activate/refresh
            if (ScavengerStoneHandler.isDoubleHaulActive(player.getUniqueId())) slotsFound *= 2;
            if (ScavengerStoneHandler.hasStone(enchant, "Epic")) {
                ScavengerStoneHandler.activateDoubleHaul(player.getUniqueId());
            }

            // Legendary (Compounding): always 2x slots
            if (ScavengerStoneHandler.hasStone(enchant, "Legendary")) slotsFound *= 2;

            // Cap so the backpack never exceeds the tier maximum
            slotsFound = Math.min(slotsFound, tierCap - playerProfile.backpack.getMaxSize());

            playerProfile.backpack.setMaxSize(playerProfile.backpack.getMaxSize() + slotsFound);
            playerProfile.save(null);
            BackpackListener.refresh(player);

            // Uncommon (Mixed Bag): 50% chance to also award 1-5 essence
            if (ScavengerStoneHandler.hasStone(enchant, "Uncommon") && Utils.chance(50.0)) {
                int essence = Utils.amount(1, 5);
                playerProfile.addEssence(essence);
                String essenceText = enchantsConfig.getString(
                        ConfigPaths.Enchants.Scavenger.MIXED_BAG_MESSAGE, "&#AA00FF+%essence% Essence!");
                if (essenceText != null && !essenceText.isEmpty()) {
                    Location hologramLoc = event.getBlock().getLocation().add(0.5, 1.8, 0.5);
                    AnimationRegistry.getInstance().play(
                            new HologramAnimation(plugin,
                                    essenceText.replace("%essence%", String.valueOf(essence))),
                            hologramLoc, List.of(player));
                }
            }

            playEffects(event.getBlock(), player, slotsFound);
        });
    }

    /**
     * Applies the Scavenger effect to a single block broken by an AoE enchant.
     * Shares the same logic as the event-driven handler but works with already-resolved profiles.
     */
    public void tryTriggerForAoE(Player player, Block block,
                                 PickaxeProfile pickaxeProfile,
                                 PlayerProfile playerProfile)
    {
        if (pickaxeProfile == null || pickaxeProfile.enchants == null) return;

        EnchantData enchant = pickaxeProfile.enchants.getEnchant(Enchants.SCAVENGER);
        if (enchant == null || !enchant.enabled || enchant.level <= 0) return;
        if (!Utils.chance(enchant.getCurrentChance())) return;

        // Rare (Prospector): 10% chance to award atoms instead of slots
        if (ScavengerStoneHandler.hasStone(enchant, "Rare") && Utils.chance(10.0)) {
            int atoms = Utils.amount(1, 2);
            pickaxeProfile.evolutionAtoms += atoms;
            PickaxeManager.getInstance().checkAtoms(pickaxeProfile, player);
            pickaxeProfile.save(null);
            return;
        }

        if (playerProfile.backpack.isFull()) return;
        int tierCap = getTierCap(player);
        if (playerProfile.backpack.getMaxSize() >= tierCap) return;

        int slotsMin = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MIN, 1);
        int slotsMax = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MAX, 3);

        // Common (Extra Pocket): +1 slotsMin
        if (ScavengerStoneHandler.hasStone(enchant, "Common")) slotsMin += 1;

        int slotsFound = Utils.amount(slotsMin, slotsMax);

        // Epic (Double Haul): double slots if window is active, then activate/refresh
        if (ScavengerStoneHandler.isDoubleHaulActive(player.getUniqueId())) slotsFound *= 2;
        if (ScavengerStoneHandler.hasStone(enchant, "Epic")) {
            ScavengerStoneHandler.activateDoubleHaul(player.getUniqueId());
        }

        // Legendary (Compounding): always 2x slots
        if (ScavengerStoneHandler.hasStone(enchant, "Legendary")) slotsFound *= 2;

        slotsFound = Math.min(slotsFound, tierCap - playerProfile.backpack.getMaxSize());

        playerProfile.backpack.setMaxSize(playerProfile.backpack.getMaxSize() + slotsFound);
        playerProfile.save(null);
        BackpackListener.refresh(player);

        // Uncommon (Mixed Bag): 50% chance to also award 1-5 essence
        if (ScavengerStoneHandler.hasStone(enchant, "Uncommon") && Utils.chance(50.0)) {
            int essence = Utils.amount(1, 5);
            playerProfile.addEssence(essence);
            String essenceText = enchantsConfig.getString(
                    ConfigPaths.Enchants.Scavenger.MIXED_BAG_MESSAGE, "&#AA00FF+%essence% Essence!");
            if (essenceText != null && !essenceText.isEmpty()) {
                Location hologramLoc = block.getLocation().add(0.5, 1.8, 0.5);
                AnimationRegistry.getInstance().play(
                        new HologramAnimation(plugin,
                                essenceText.replace("%essence%", String.valueOf(essence))),
                        hologramLoc, List.of(player));
            }
        }

        playEffects(block, player, slotsFound);
    }

    /**
     * Silent variant for AoE accumulation. Applies all logic and awards slots but plays no effects.
     *
     * @return slots awarded this block, or 0 if not triggered / already at cap
     */
    public int tryTriggerForAoESilent(Player player, Block block,
                                      PickaxeProfile pickaxeProfile, PlayerProfile playerProfile)
    {
        if (pickaxeProfile == null || pickaxeProfile.enchants == null) return 0;

        EnchantData enchant = pickaxeProfile.enchants.getEnchant(Enchants.SCAVENGER);
        if (enchant == null || !enchant.enabled || enchant.level <= 0) return 0;
        if (!Utils.chance(enchant.getCurrentChance())) return 0;

        // Rare (Prospector): 10% chance to award atoms — accumulate via outer acc buffer
        if (ScavengerStoneHandler.hasStone(enchant, "Rare") && Utils.chance(10.0)) {
            int atoms = Utils.amount(1, 2);
            pickaxeProfile.evolutionAtoms += atoms;
            AoEDropResolver.addAccumulatedAtoms(player.getUniqueId(), atoms);
            pickaxeProfile.save(null);
            return 0;
        }

        if (playerProfile.backpack.isFull()) return 0;
        int tierCap = getTierCap(player);
        if (playerProfile.backpack.getMaxSize() >= tierCap) return 0;

        int slotsMin = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MIN, 1);
        int slotsMax = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.SLOTS_MAX, 3);
        if (ScavengerStoneHandler.hasStone(enchant, "Common")) slotsMin += 1;

        int slotsFound = Utils.amount(slotsMin, slotsMax);
        if (ScavengerStoneHandler.isDoubleHaulActive(player.getUniqueId())) slotsFound *= 2;
        if (ScavengerStoneHandler.hasStone(enchant, "Epic")) ScavengerStoneHandler.activateDoubleHaul(player.getUniqueId());
        if (ScavengerStoneHandler.hasStone(enchant, "Legendary")) slotsFound *= 2;
        slotsFound = Math.min(slotsFound, tierCap - playerProfile.backpack.getMaxSize());

        playerProfile.backpack.setMaxSize(playerProfile.backpack.getMaxSize() + slotsFound);
        playerProfile.save(null);
        BackpackListener.refresh(player);
        return slotsFound;
    }

    /**
     * Returns the maximum backpack slots allowed based on the player's current backpack tier.
     * Reads the CustomModelData from the player's offhand item and looks up the configured cap.
     * Falls back to {@code Scavenger.MaxTotalSlots} if no matching tier is found.
     *
     * @param player the player
     * @return the max slot cap for their current backpack tier
     */
    private int getTierCap(Player player)
    {
        int fallback = enchantsConfig.getInt(ConfigPaths.Enchants.Scavenger.MAX_TOTAL_SLOTS, 500);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == Material.AIR) return fallback;

        ItemMeta meta = offhand.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return fallback;

        int modelData = meta.getCustomModelData();
        FileConfiguration defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        String path = ConfigPaths.Default.Backpack.TIERS_PREFIX + modelData;
        if (!defaultConfig.contains(path)) return fallback;

        return defaultConfig.getInt(path, fallback);
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
            String chatMessage = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES).getString(ConfigPaths.Messages.Enchants.SCAVENGER_TRIGGERED);
            if (chatMessage != null && !chatMessage.isEmpty()) {
                chatMessage = chatMessage.replace("%slots%", String.valueOf(slotsFound));
                player.sendMessage(Utils.formatMessage(player, chatMessage));
            }
        }

        if (pref.title) {
            String title    = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES).getString(ConfigPaths.Messages.Enchants.SCAVENGER_TRIGGERED_TITLE, "&#FFD700&lScavenger");
            String subtitle = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES).getString(ConfigPaths.Messages.Enchants.SCAVENGER_TRIGGERED_SUBTITLE, "&#CCCCCC+%slots% Backpack Slots!");
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
