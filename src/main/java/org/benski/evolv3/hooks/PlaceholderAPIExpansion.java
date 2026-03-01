package org.benski.evolv3.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for Evolv3 placeholders.
 * <p>General placeholders:</p>
 * <ul>
 *   <li>%evolv3_prefix%</li>
 * </ul>
 * <p>Player placeholders:</p>
 * <ul>
 *   <li>%evolv3_player_name%</li>
 *   <li>%evolv3_player_displayname%</li>
 *   <li>%evolv3_player_uuid%</li>
 *   <li>%evolv3_player_world%</li>
 *   <li>%evolv3_player_health%</li>
 *   <li>%evolv3_player_level%</li>
 * </ul>
 * <p>Pickaxe placeholders:</p>
 * <ul>
 *   <li>%evolv3_pickaxe_level%</li>
 *   <li>%evolv3_pickaxe_xp%</li>
 *   <li>%evolv3_pickaxe_target%</li>
 *   <li>%evolv3_pickaxe_percentage%</li>
 *   <li>%evolv3_pickaxe_progressbar%</li>
 *   <li>%evolv3_pickaxe_evolution%</li>
 *   <li>%evolv3_pickaxe_essence%</li>
 *   <li>%evolv3_pickaxe_target_essence%</li>
 *   <li>%evolv3_pickaxe_essence_percentage%</li>
 *   <li>%evolv3_pickaxe_essence_progressbar%</li>
 *   <li>%evolv3_pickaxe_enchant_rolls%</li>
 *   <li>%evolv3_pickaxe_evolution_rolls%</li>
 *   <li>%evolv3_pickaxe_evolution_atoms%</li>
 *   <li>%evolv3_pickaxe_enchants%</li>
 * </ul>
 * <p>Enchant placeholders:</p>
 * <ul>
 *   <li>%evolv3_enchant_[name]_level%</li>
 *   <li>%evolv3_enchant_[name]_maxlevel%</li>
 *   <li>%evolv3_enchant_[name]_chance%</li>
 *   <li>%evolv3_enchant_[name]_enabled%</li>
 * </ul>
 */
public class PlaceholderAPIExpansion extends PlaceholderExpansion
{
    private final Main plugin;

    public PlaceholderAPIExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "evolv3";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Benski";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return null;

        if (params.equals("prefix")) {
            FileConfiguration messagesConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
            return messagesConfig.getString(ConfigPaths.Messages.PREFIX, "&8[&bEvolv3&8]");
        }

        if (params.startsWith("player_")) {
            return handlePlayerPlaceholder(player, params.substring(7));
        }

        if (params.startsWith("pickaxe_")) {
            return handlePickaxePlaceholder(player, params.substring(8));
        }

        if (params.startsWith("enchant_")) {
            return handleEnchantPlaceholder(player, params.substring(8));
        }

        return null;
    }

    /**
     * Handles player-related placeholders.
     *
     * @param player The player
     * @param param The placeholder parameter (after "player_")
     * @return The placeholder value, or null if not found
     */
    private String handlePlayerPlaceholder(Player player, String param) {
        return switch (param) {
            case "name" -> player.getName();
            case "displayname" -> player.getDisplayName();
            case "uuid" -> player.getUniqueId().toString();
            case "world" -> player.getWorld().getName();
            case "health" -> String.valueOf((int) player.getHealth());
            case "max_health" -> String.valueOf((int) player.getMaxHealth());
            case "food" -> String.valueOf(player.getFoodLevel());
            case "level" -> String.valueOf(player.getLevel());
            case "exp" -> String.valueOf((int) (player.getExp() * 100));
            default -> null;
        };
    }

    /**
     * Handles pickaxe-related placeholders.
     *
     * @param player The player
     * @param param The placeholder parameter (after "pickaxe_")
     * @return The placeholder value, or null if not found
     */
    private String handlePickaxePlaceholder(Player player, String param) {
        PickaxeDataCenter dataCenter = (PickaxeDataCenter) DataCenterRegistry.getInstance().get("pickaxe");
        if (dataCenter == null) return null;

        PickaxeProfile profile = dataCenter.getLocal(player);
        if (profile == null) return "0";

        return switch (param) {
            case "level" -> String.valueOf(profile.level);
            case "xp" -> String.valueOf(profile.xp);
            case "target" -> String.valueOf(profile.target);
            case "evolution" -> String.valueOf(profile.evolution);
            case "essence" -> String.valueOf(profile.currentEssence);
            case "target_essence" -> String.valueOf(profile.targetEssence);
            case "enchant_rolls" -> String.valueOf(profile.enchantRolls);
            case "evolution_rolls" -> String.valueOf(profile.evolutionRolls);
            case "evolution_atoms" -> String.valueOf(profile.evolutionAtoms);
            case "enchants" -> formatEnchantsList(profile);
            case "percentage" -> calculatePercentage(profile.xp, profile.target);
            case "progressbar" -> Utils.generateProgressBar(profile.xp, profile.target);
            case "essence_percentage" -> calculatePercentage(profile.currentEssence, profile.targetEssence);
            case "essence_progressbar" -> Utils.generateProgressBar(profile.currentEssence, profile.targetEssence);
            case "atom_target" -> String.valueOf(profile.atomTarget);
            case "atom_percentage" -> calculatePercentage(profile.evolutionAtoms, profile.atomTarget);
            case "atom_progressbar" -> Utils.generateProgressBar(profile.evolutionAtoms, profile.atomTarget);
            default -> null;
        };
    }

    /**
     * Formats the enchants list for display in lore.
     *
     * @param profile The pickaxe profile
     * @return Formatted string of enchants, or "None" message
     */
    private String formatEnchantsList(PickaxeProfile profile) {
        FileConfiguration enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);

        if (profile.enchants == null || profile.enchants.isEmpty()) {
            return enchantsConfig.getString(ConfigPaths.Enchants.NO_ENCHANTS, "&cNone");
        }

        StringBuilder sb = new StringBuilder();
        for (EnchantData enchant : profile.enchants.items) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }

            String displayName = EnchantAnimationConfig.getDisplayName(enchant.name);

            // Rarity colour — uppercase hex required for MatrixColorAPI
            var option = EnchantAnimationConfig.getOption(displayName);
            String rarityColor = option != null
                    ? "&" + option.rarity().textColor().asHexString().toUpperCase()
                    : "&#CCCCCC";

            String levelDisplay;
            if (enchant.isMaxLevel()) {
                levelDisplay = enchantsConfig.getString(ConfigPaths.Enchants.MAX_ENCHANT, "&6&lMAX");
            } else {
                levelDisplay = "&#FFFFFF" + enchant.level;
            }

            sb.append(" ").append(rarityColor).append(displayName).append(" &#888888» ").append(levelDisplay);
        }

        return sb.toString();
    }

    /**
     * Calculates the percentage progress between current and target values.
     *
     * @param current the current value
     * @param target the target value
     * @return the percentage as a formatted string (e.g., "75%")
     */
    private String calculatePercentage(int current, int target) {
        if (target <= 0) return "0%";
        int percentage = (int) Math.round((double) current / target * 100);
        return percentage + "%";
    }

    /**
     * Handles enchant-specific placeholders.
     *
     * @param player The player
     * @param param The placeholder parameter (after "enchant_")
     * @return The placeholder value, or null if not found
     */
    private String handleEnchantPlaceholder(Player player, String param) {
        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return null;

        PickaxeProfile profile = dataCenter.getLocal(player);
        if (profile == null || profile.enchants == null) return "0";

        // Parse enchant name and property (e.g., "frostbite_level" -> name="frostbite", prop="level")
        int lastUnderscore = param.lastIndexOf('_');
        if (lastUnderscore == -1) return null;

        String enchantName = param.substring(0, lastUnderscore);
        String property = param.substring(lastUnderscore + 1);

        EnchantData enchant = profile.enchants.getEnchant(enchantName);
        if (enchant == null) return "0";

        return switch (property) {
            case "level" -> String.valueOf(enchant.level);
            case "maxlevel" -> String.valueOf(enchant.maxLevel);
            case "chance" -> String.format("%.1f", enchant.getCurrentChance());
            case "enabled" -> enchant.enabled ? "Enabled" : "Disabled";
            default -> null;
        };
    }
}
