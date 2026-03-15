package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;

import static org.benski.evolv3.core.helpers.Utils.formatMaterialName;
import org.benski.evolv3.core.managers.EnchantDropManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.EnchantPreference;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener for the Furnace custom enchantment.
 * Auto-smelts blocks based on configured recipes.
 * Works in conjunction with Alchemy enchant for chained effects.
 *
 * <p>Trigger chance scales with enchantment level:</p>
 * <ul>
 *   <li>Chance = (level / maxLevel) * maxChance</li>
 *   <li>Example: Level 80/100 with maxChance 40 = 32% chance</li>
 * </ul>
 *
 * <p>Smelting recipes are configurable in enchants.yml</p>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class FurnaceEnchant implements Listener
{
    private final FileConfiguration enchantsConfig;
    private final FileConfiguration messagesConfig;
    private final JavaPlugin plugin;

    /**
     * Metadata key for passing smelted result to drop handlers.
     * Used in BlockBreakEvent context to modify drops.
     */
    public static final String FURNACE_RESULT_KEY = "furnace_result";

    /**
     * Cached smelting recipes for performance.
     */
    private final Map<Material, Material> smeltingRecipes = new HashMap<>();

    public FurnaceEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
        this.messagesConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
        loadSmeltingRecipes();
    }

    /**
     * Loads smelting recipes from configuration.
     */
    private void loadSmeltingRecipes()
    {
        smeltingRecipes.clear();
        ConfigurationSection recipesSection = enchantsConfig.getConfigurationSection(ConfigPaths.Enchants.Furnace.RECIPES);
        if (recipesSection == null) {
            return;
        }

        for (String key : recipesSection.getKeys(false)) {
            try {
                Material input = Material.valueOf(key.toUpperCase());
                Material output = Material.valueOf(recipesSection.getString(key).toUpperCase());
                smeltingRecipes.put(input, output);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid smelting recipe: " + key + " -> " + recipesSection.getString(key));
            }
        }
    }

    /**
     * Handles the block break event to trigger furnace effect.
     *
     * @param event the block break event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        if (playerDC != null) {
            org.benski.evolv3.models.player.PlayerProfile pp = playerDC.getLocal(player);
            if (pp != null && pp.backpack.isFull()) return;
        }

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) {
                return;
            }

            EnchantData enchant = profile.enchants.getEnchant(Enchants.FURNACE);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) {
                return;
            }

            double triggerChance = enchant.getCurrentChance();
            if (!Utils.chance(triggerChance)) {
                return;
            }

            Block block = event.getBlock();
            Material materialToSmelt = getMaterialToSmelt(player.getUniqueId(), block);

            Material smeltResult = smeltingRecipes.get(materialToSmelt);
            if (smeltResult == null) {
                return;
            }

            EnchantDropManager.set(player.getUniqueId(), smeltResult);

            playSmeltEffects(block, player, materialToSmelt, smeltResult);
        });
    }

    /**
     * Determines the material to smelt, accounting for Alchemy upgrades and Frostbite.
     * Reads Alchemy's result from {@link EnchantDropManager} for chaining, since Alchemy
     * fires first (NORMAL priority) and has already written its result before Furnace (HIGH).
     *
     * @param playerUuid the UUID of the player breaking the block
     * @param block      the block being broken
     * @return the material to smelt
     */
    private Material getMaterialToSmelt(java.util.UUID playerUuid, Block block)
    {
        // Alchemy already wrote its result — chain off it.
        Material alchemyResult = EnchantDropManager.get(playerUuid);
        if (alchemyResult != null) {
            return alchemyResult;
        }

        if (block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) {
            String originalMaterial = block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY).get(0).asString();
            try {
                return Material.valueOf(originalMaterial);
            } catch (IllegalArgumentException e) {
            }
        }

        return block.getDrops().stream()
                .filter(i -> i != null && !i.getType().isAir())
                .findFirst()
                .map(ItemStack::getType)
                .orElse(block.getType());
    }

    /**
     * Plays visual and audio effects for the smelting action.
     *
     * @param block        the block being smelted
     * @param player       the player triggering the effect
     * @param original     the original material
     * @param smeltResult  the smelted result material
     */
    private void playSmeltEffects(Block block, Player player, Material original, Material smeltResult)
    {
        EnchantPreference pref = getEnchantPref(player);

        if (pref.particles) {
            block.getWorld().spawnParticle(Particle.FLAME,
                    block.getLocation().add(0.5, 0.5, 0.5),
                    10, 0.2, 0.2, 0.2, 0.02);
        }

        if (pref.sound) {
            player.playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
        }

        if (pref.hologram && enchantsConfig.getBoolean(ConfigPaths.Enchants.Furnace.HOLOGRAM_ENABLED, true)) {
            String hologramText = enchantsConfig.getString(
                            ConfigPaths.Enchants.Furnace.HOLOGRAM_MESSAGE,
                            "&7%original% &a> &a%smelt%")
                    .replace("%original%", formatMaterialName(original))
                    .replace("%smelt%", formatMaterialName(smeltResult));
            Location hologramLoc = block.getLocation().add(0.5, 1.0, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message) {
            String chatMessage = messagesConfig.getString(ConfigPaths.Messages.Enchants.FURNACE_TRIGGERED);
            if (chatMessage != null && !chatMessage.isEmpty()) {
                chatMessage = chatMessage.replace("%original%", formatMaterialName(original));
                chatMessage = chatMessage.replace("%result%", formatMaterialName(smeltResult));
                player.sendMessage(Utils.formatMessage(player, chatMessage));
            }
        }

        if (pref.title) {
            String title    = messagesConfig.getString(ConfigPaths.Messages.Enchants.FURNACE_TRIGGERED_TITLE, "&#FF6B35&lFurnace");
            String subtitle = messagesConfig.getString(ConfigPaths.Messages.Enchants.FURNACE_TRIGGERED_SUBTITLE, "&#CCCCCC%original% &#FF6B35> &#CCCCCC%result%");
            subtitle = subtitle.replace("%original%", formatMaterialName(original))
                               .replace("%result%", formatMaterialName(smeltResult));
            player.sendTitle(
                    Utils.formatMessage(player, title),
                    Utils.formatMessage(player, subtitle),
                    5, 30, 10);
        }
    }

    private EnchantPreference getEnchantPref(Player player) {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return new EnchantPreference();
        org.benski.evolv3.models.player.PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return new EnchantPreference();
        String enchantName = enchantsConfig.getString(ConfigPaths.Enchants.Furnace.NAME, "Furnace");
        return profile.preferences.getOrCreateEnchantPref(enchantName);
    }

    /**
     * Gets the smelting result for a material.
     *
     * @param input the input material
     * @return the smelted output, or null if no recipe exists
     */
    public Material getSmeltResult(Material input)
    {
        return smeltingRecipes.get(input);
    }
}
