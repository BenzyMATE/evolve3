package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;

import static org.benski.evolv3.core.helpers.Utils.formatMaterialName;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.registry.AnimationRegistry;
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
import org.benski.evolv3.core.managers.EnchantDropManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener for the Alchemy custom enchantment.
 * Upgrades blocks to a higher tier material when mined.
 * Triggers before Furnace for chained transmutation effects.
 *
 * <p>Trigger chance scales with enchantment level:</p>
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance</li>
 *   <li>Example: Level 50/100 with maxChance 20 = 10% chance</li>
 * </ul>
 *
 * <p>Upgrade recipes are configurable in enchants.yml</p>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class AlchemyEnchant implements Listener
{
    private final FileConfiguration enchantsConfig;
    private final FileConfiguration messagesConfig;
    private final JavaPlugin plugin;

    /**
     * Metadata key for passing upgraded result to other enchants and drop handlers.
     */
    public static final String ALCHEMY_RESULT_KEY = "alchemy_result";

    /**
     * Cached upgrade recipes for performance.
     */
    private final Map<Material, Material> upgradeRecipes = new HashMap<>();

    public AlchemyEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
        this.messagesConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
        loadUpgradeRecipes();
    }

    /**
     * Loads upgrade recipes from configuration.
     */
    private void loadUpgradeRecipes()
    {
        upgradeRecipes.clear();
        ConfigurationSection upgradesSection = enchantsConfig.getConfigurationSection(ConfigPaths.Enchants.Alchemy.UPGRADES);
        if (upgradesSection == null) {
            return;
        }

        for (String key : upgradesSection.getKeys(false)) {
            try {
                Material input = Material.valueOf(key.toUpperCase());
                Material output = Material.valueOf(upgradesSection.getString(key).toUpperCase());
                upgradeRecipes.put(input, output);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid alchemy recipe: " + key + " -> " + upgradesSection.getString(key));
            }
        }
    }

    /**
     * Handles the block break event to trigger alchemy effect.
     * Uses NORMAL priority to run before Furnace (which uses HIGH priority).
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

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) {
                return;
            }

            EnchantData enchant = profile.enchants.getEnchant(Enchants.ALCHEMY);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) {
                return;
            }

            double triggerChance = enchant.getCurrentChance();
            if (!Utils.chance(triggerChance)) {
                return;
            }

            Block block = event.getBlock();
            Material materialToUpgrade = getMaterialToUpgrade(block);

            Material upgradeResult = upgradeRecipes.get(materialToUpgrade);
            if (upgradeResult == null) {
                return;
            }

            EnchantDropManager.set(player.getUniqueId(), upgradeResult);

            playUpgradeEffects(block, player, materialToUpgrade, upgradeResult);
        });
    }

    /**
     * Determines the material to upgrade, accounting for Frostbite frozen blocks.
     *
     * @param block the block being broken
     * @return the material to upgrade
     */
    private Material getMaterialToUpgrade(Block block)
    {
        if (block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) {
            String originalMaterial = block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY).get(0).asString();
            try {
                return Material.valueOf(originalMaterial);
            } catch (IllegalArgumentException e) {
            }
        }

        return block.getDrops().stream()
                .findFirst()
                .map(ItemStack::getType)
                .orElse(block.getType());
    }

    /**
     * Plays visual and audio effects for the alchemy action.
     *
     * @param block         the block being upgraded
     * @param player        the player triggering the effect
     * @param original      the original material
     * @param upgradeResult the upgraded result material
     */
    private void playUpgradeEffects(Block block, Player player, Material original, Material upgradeResult)
    {
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                block.getLocation().add(0.5, 0.5, 0.5),
                10, 0.2, 0.2, 0.2, 0.02);

        player.playSound(block.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        if (enchantsConfig.getBoolean(ConfigPaths.Enchants.Alchemy.HOLOGRAM_ENABLED, true)) {
            String hologramText = enchantsConfig.getString(
                            ConfigPaths.Enchants.Alchemy.HOLOGRAM_MESSAGE,
                            "&7%original% &a> &d%upgrade%")
                    .replace("%original%", formatMaterialName(original))
                    .replace("%upgrade%", formatMaterialName(upgradeResult));
            Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        String chatMessage = messagesConfig.getString(ConfigPaths.Messages.Enchants.ALCHEMY_TRIGGERED);
        if (chatMessage != null && !chatMessage.isEmpty()) {
            chatMessage = chatMessage.replace("%original%", formatMaterialName(original));
            chatMessage = chatMessage.replace("%upgrade%", formatMaterialName(upgradeResult));
            player.sendMessage(Utils.formatMessage(player, chatMessage));
        }
    }

    /**
     * Gets the upgrade result for a material.
     *
     * @param input the input material
     * @return the upgraded output, or null if no recipe exists
     */
    public Material getUpgradeResult(Material input)
    {
        return upgradeRecipes.get(input);
    }
}
