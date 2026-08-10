package org.benski.evolv3.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.mine.MineManager;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager;
import org.benski.evolv3.core.managers.player.RankManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for Evolv3 placeholders.
 *
 * <p>General placeholders:
 *
 * <ul>
 *   <li>%evolv3_prefix%
 * </ul>
 *
 * <p>Player placeholders:
 *
 * <ul>
 *   <li>%evolv3_player_name%
 *   <li>%evolv3_player_displayname%
 *   <li>%evolv3_player_uuid%
 *   <li>%evolv3_player_world%
 *   <li>%evolv3_player_health%
 *   <li>%evolv3_player_level%
 *   <li>%evolv3_player_essence%
 *   <li>%evolv3_player_coins%
 * </ul>
 *
 * <p>Pickaxe placeholders:
 *
 * <ul>
 *   <li>%evolv3_pickaxe_level%
 *   <li>%evolv3_pickaxe_xp%
 *   <li>%evolv3_pickaxe_target%
 *   <li>%evolv3_pickaxe_percentage%
 *   <li>%evolv3_pickaxe_progressbar%
 *   <li>%evolv3_pickaxe_evolution%
 *   <li>%evolv3_pickaxe_essence%
 *   <li>%evolv3_pickaxe_target_essence%
 *   <li>%evolv3_pickaxe_essence_percentage%
 *   <li>%evolv3_pickaxe_essence_progressbar%
 *   <li>%evolv3_pickaxe_enchant_rolls%
 *   <li>%evolv3_pickaxe_evolution_rolls%
 *   <li>%evolv3_pickaxe_evolution_atoms%
 *   <li>%evolv3_pickaxe_enchants%
 * </ul>
 *
 * <p>Enchant placeholders:
 *
 * <ul>
 *   <li>%evolv3_enchant_[name]_level%
 *   <li>%evolv3_enchant_[name]_maxlevel%
 *   <li>%evolv3_enchant_[name]_chance%
 *   <li>%evolv3_enchant_[name]_enabled%
 * </ul>
 *
 * <p>Mine placeholders:
 *
 * <ul>
 *   <li>%evolv3_mine_[name]_percentage%
 *   <li>%evolv3_mine_[name]_players%
 *   <li>%evolv3_mine_[name]_blocks_total%
 *   <li>%evolv3_mine_[name]_blocks_mined%
 *   <li>%evolv3_mine_[name]_blocks_remaining%
 *   <li>%evolv3_mine_[name]_reset_interval%
 *   <li>%evolv3_mine_[name]_caves%
 *
 * </ul>
 */
public class PlaceholderAPIExpansion extends PlaceholderExpansion {
    private final Main plugin;

    /**
     * Creates the expansion and binds it to the plugin instance.
     *
     * @param plugin the main plugin instance
     */
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

    /**
     * Persist.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return null;

        if (params.equals("prefix")) {
            FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
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

        if (params.startsWith("rank")) {
            return handleRankPlaceholder(player, params);
        }

        if (params.startsWith("mine_")) {
            return handleMinePlaceholder(params.substring(5));
        }

        if (params.equals("online")) {
            return String.valueOf(Bukkit.getOnlinePlayers().size());
        }

        if (params.equals("max_players")) {
            return String.valueOf(Bukkit.getMaxPlayers());
        }

        if (params.equals("tool_label")) {
            OmnitoolManager omni = OmnitoolManager.getInstance();
            if (omni.isInOmnitoolMode(player)) {
                return switch (omni.getForm(player)) {
                    case AXE -> "Axe";
                    case SWORD -> "Sword";
                    default -> "Pickaxe";
                };
            }
            return "Pickaxe";
        }

        return null;
    }

    /**
     * Handles player-related placeholders.
     *
     * @param player The player
     * @param param  The placeholder parameter (after "player_")
     * @return The placeholder value, or null if not found
     */
    @SuppressWarnings("deprecation")
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
            case "essence" -> {
                PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
                if (dc == null) yield "0";
                PlayerProfile pp = dc.getLocal(player);
                yield pp != null ? Utils.formatNumber(pp.wallet.essence) : "0";
            }
            case "backpack_items" -> {
                PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
                if (dc == null) yield "0";
                PlayerProfile pp = dc.getLocal(player);
                yield pp != null ? Utils.formatInt(pp.backpack.getTotalItems()) : "0";
            }
            case "backpack_size" -> {
                PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
                if (dc == null) yield "0";
                PlayerProfile pp = dc.getLocal(player);
                yield pp != null ? Utils.formatInt(pp.backpack.getMaxSize()) : "0";
            }
            case "coins" -> {
                PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
                if (dc == null) yield "0";
                PlayerProfile pp = dc.getLocal(player);
                yield pp != null ? Utils.formatNumber(pp.wallet.coins) : "0";
            }
            default -> null;
        };
    }

    /**
     * Handles pickaxe-related placeholders.
     *
     * @param player The player
     * @param param  The placeholder parameter (after "pickaxe_")
     * @return The placeholder value, or null if not found
     */
    private String handlePickaxePlaceholder(Player player, String param) {
        if (param.equals("blank")) return "";

        PlayerDataCenter dataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (dataCenter == null) return null;

        Pickaxe profile = dataCenter.getPickaxeForPlayer(player);
        if (profile == null) return "0";

        return switch (param) {
            case "level" -> String.valueOf(profile.level);
            case "xp" -> Utils.formatNumber(profile.xp);
            case "target" -> Utils.formatNumber(profile.target);
            case "evolution" -> String.valueOf(profile.evolution);
            case "essence" -> Utils.formatNumber(profile.essenceVessel.current);
            case "target_essence" -> Utils.formatNumber(profile.essenceVessel.target);
            case "enchant_rolls" -> String.valueOf(profile.essenceVessel.crystals);
            case "evolution_rolls" -> String.valueOf(profile.atomVessel.crystals);
            case "evolution_atoms" -> Utils.formatNumber(profile.atomVessel.current);
            case "enchants" -> formatEnchantsList(profile);
            case "percentage" -> calculatePercentage(profile.xp, profile.target);
            case "progressbar" -> Utils.generateProgressBar(profile.xp, profile.target);
            case "essence_percentage" ->
                    calculatePercentage(profile.essenceVessel.current, profile.essenceVessel.target);
            case "essence_progressbar" ->
                    Utils.generateProgressBar(profile.essenceVessel.current, profile.essenceVessel.target);
            case "atom_target" -> String.valueOf(profile.atomVessel.target);
            case "atom_percentage" -> calculatePercentage(profile.atomVessel.current, profile.atomVessel.target);
            case "atom_progressbar" -> Utils.generateProgressBar(profile.atomVessel.current, profile.atomVessel.target);
            case "prestige" -> String.valueOf(profile.prestige);
            case "blank" -> "";
            default -> null;
        };
    }

    /**
     * Formats the enchants list for display in lore.
     *
     * @param profile The pickaxe profile
     * @return Formatted string of enchants, or "None" message
     */
    private String formatEnchantsList(Pickaxe profile) {
        FileConfiguration enchantsConfig = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);

        if (profile.enchants == null || profile.enchants.isEmpty()) {
            return enchantsConfig.getString(ConfigPaths.Enchants.NO_ENCHANTS, "&cNone");
        }

        StringBuilder sb = new StringBuilder();
        for (EnchantData enchant : profile.enchants.items) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }

            String displayName = EnchantAnimationConfig.getDisplayName(enchant.name);

            var option = EnchantAnimationConfig.getOption(displayName);
            String rarityColor =
                    option != null
                            ? "&" + option.rarity().textColor().asHexString().toUpperCase()
                            : "&#CCCCCC";

            String levelDisplay;
            if (enchant.isMaxLevel()) {
                levelDisplay = enchantsConfig.getString(ConfigPaths.Enchants.MAX_ENCHANT, "&6&lMAX");
            } else {
                levelDisplay = "&#FFFFFF" + enchant.level;
            }

            sb.append(" ")
                    .append(rarityColor)
                    .append(displayName)
                    .append(" &#888888Â» ")
                    .append(levelDisplay);
        }

        return sb.toString();
    }

    /**
     * Calculates the percentage progress between current and target values.
     *
     * @param current the current value
     * @param target  the target value
     * @return the percentage as a formatted string (e.g., "75%")
     */
    private String calculatePercentage(int current, int target) {
        if (target <= 0) return "0%";
        int percentage = (int) Math.round((double) current / target * 100);
        return percentage + "%";
    }

    /**
     * Handles rank-related placeholders.
     *
     * <ul>
     *   <li>{@code %evolv3_rank%} â€" formatted rank prefix (e.g. "&#AAAAAA[A1]")
     *   <li>{@code %evolv3_rank_id%} â€" raw rank id (e.g. "A1")
     *   <li>{@code %evolv3_rank_staff%} â€" formatted staff rank prefix, or "" if none
     *   <li>{@code %evolv3_rank_staff_id%} â€" raw staff rank id, or "" if none
     * </ul>
     *
     * @param player The player
     * @param param  The full placeholder parameter (e.g. "rank", "rank_id")
     * @return The placeholder value, or null if not recognised
     */
    private String handleRankPlaceholder(Player player, String param) {
        PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (dc == null) return "";

        PlayerProfile pp = dc.getLocal(player);
        if (pp == null) return "";

        RankManager rm = RankManager.getInstance();
        String rankId = pp.getRank() != null ? pp.getRank() : rm.getDefaultRank();

        return switch (param) {
            case "rank" -> rm.getRankPrefix(rankId);
            case "rank_id" -> rankId;
            case "rank_staff" -> pp.getStaffRank() != null && !pp.getStaffRank().isEmpty()
                    ? rm.getStaffPrefix(pp.getStaffRank())
                    : "";
            case "rank_staff_id" -> pp.getStaffRank() != null ? pp.getStaffRank() : "";
            default -> null;
        };
    }

    /**
     * Handles mine-related placeholders.
     *
     * <p>Format: {@code mine_<name>_<property>}
     *
     * <p>The mine name may contain underscores â€" the property is always the last segment.
     *
     * @param param the placeholder after "mine_" (e.g. "a_percentage")
     * @return the value, or null if unrecognised
     */
    private String handleMinePlaceholder(String param) {
        int lastUnderscore = param.lastIndexOf('_');
        if (lastUnderscore == -1) return null;

        String mineName = param.substring(0, lastUnderscore);
        String property = param.substring(lastUnderscore + 1);

        MineManager.Mine mine = MineManager.getInstance().getMine(mineName);
        if (mine == null) return null;

        return switch (property) {
            case "percentage" -> String.format("%.2f%%", MineManager.getInstance().getRemainingPercentage(mineName));
            case "players" -> String.valueOf(MineManager.getInstance().getPlayerCount(mineName));
            case "total" -> String.valueOf(mine.totalBlocks());
            case "mined" -> String.valueOf(MineManager.getInstance().getMinedCount(mineName));
            case "remaining" -> String.valueOf(mine.totalBlocks() - MineManager.getInstance().getMinedCount(mineName));
            case "interval" -> String.valueOf(mine.resetInterval());
            case "caves" -> String.valueOf(mine.caveCount());
            case "name" -> mine.name();
            default -> null;
        };
    }

    /**
     * Handles enchant-specific placeholders.
     *
     * @param player The player
     * @param param  The placeholder parameter (after "enchant_")
     * @return The placeholder value, or null if not found
     */
    private String handleEnchantPlaceholder(Player player, String param) {
        PlayerDataCenter dataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (dataCenter == null) return null;

        Pickaxe profile = dataCenter.getPickaxeForPlayer(player);
        if (profile == null || profile.enchants == null) return "0";

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
