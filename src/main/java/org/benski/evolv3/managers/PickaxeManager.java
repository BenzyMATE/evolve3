package org.benski.evolv3.managers;

import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.enchants.EfficiencyEnchant;
import org.benski.evolv3.enums.ArmorType;
import org.benski.evolv3.models.armor.ArmorItem;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manager class responsible for all pickaxe-related operations and progression.
 * Handles pickaxe statistics, leveling, essence management, evolution checks,
 * and lore updates.
 *
 * <p>This is a singleton manager accessed via {@link #getInstance()}.</p>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class PickaxeManager
{
    @Getter
    private static final PickaxeManager instance = new PickaxeManager();

    /** Tracks the last time (ms) each player broke a block, used to debounce lore updates. */
    private final Map<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();

    private static final long MINING_COOLDOWN_MS = 2000;

    private PickaxeManager() {}

    /**
     * Records that a player just broke a block.
     * Used by the lore-update debounce to avoid updating the held item mid-swing.
     *
     * @param uuid the player's UUID
     */
    public void recordBreak(UUID uuid) {
        lastBreakTime.put(uuid, System.currentTimeMillis());
    }

    /**
     * Returns true if the player broke a block within the last {@value MINING_COOLDOWN_MS} ms.
     * Lore updates should be skipped while this returns true.
     *
     * @param uuid the player's UUID
     * @return true if actively mining
     */
    public boolean isActiveMining(UUID uuid) {
        Long last = lastBreakTime.get(uuid);
        return last != null && (System.currentTimeMillis() - last) < MINING_COOLDOWN_MS;
    }

    private FileConfiguration getDefaultConfig() {
        return ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    }

    private FileConfiguration getMessagesConfig() {
        return ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    }

    private FileConfiguration getEnchantsConfig() {
        return ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
    }

    /**
     * Adds statistics to a player's pickaxe when mining a block.
     * Handles XP gain with armor multipliers, level checks, and lore updates.
     *
     * @param player        the player who mined
     * @param blockType     the type of block mined
     * @param playerProfile the player's profile for armor multipliers (optional)
     */
    public void addStatistics(Player player, Material blockType, PlayerProfile playerProfile)
    {
        if (blockType == Material.AIR) {
            return;
        }

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        dataCenter.get(player, profile -> {
            if (profile == null) {
                return;
            }

            String xpPath = ConfigPaths.Default.BLOCK_DEFAULTS + "." + blockType.name() + ".XP";
            int xpToAdd = getDefaultConfig().getInt(xpPath, 0);

            if (xpToAdd <= 0) {
                return;
            }

            double xpMultiplier = getLeggingsMultiplier(player);
            xpToAdd = (int) Math.round(xpToAdd * xpMultiplier);

            profile.xp += xpToAdd;

            checkLevel(profile, player, playerProfile);

            String actionBar = getDefaultConfig().getString(ConfigPaths.Default.Pickaxe.ACTION_BAR, "");
            if (!actionBar.isEmpty()) {
                String formatted = Utils.formatMessage(player, actionBar);
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(formatted));
            }
        });
    }

    /**
     * Gets the leggings XP multiplier for a player.
     *
     * @param player the player
     * @return the multiplier (1.0 if no armor or disabled)
     */
    private double getLeggingsMultiplier(Player player)
    {
        ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
        ArmorProfile armorProfile = armorDataCenter.getLocal(player);

        if (armorProfile != null) {
            return armorProfile.getMiningMultiplier();
        }
        return 1.0;
    }

    /**
     * Gets the chestplate essence multiplier for a player.
     *
     * @param player the player
     * @return the multiplier (1.0 if no armor or disabled)
     */
    private double getChestplateMultiplier(Player player)
    {
        ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
        ArmorProfile armorProfile = armorDataCenter.getLocal(player);

        if (armorProfile != null) {
            return armorProfile.getEssenceMultiplier();
        }
        return 1.0;
    }

    /**
     * Checks if the player should level up and handles the level up process.
     *
     * @param profile the pickaxe profile
     * @param player  the owner player
     */
    public void checkLevel(PickaxeProfile profile, Player player)
    {
        checkLevel(profile, player, null);
    }

    /**
     * Checks if the player should level up and handles the level up process.
     * Applies chestplate essence multiplier when provided.
     *
     * @param profile       the pickaxe profile
     * @param player        the owner player
     * @param playerProfile the player's profile for armor integration (optional)
     */
    public void checkLevel(PickaxeProfile profile, Player player, PlayerProfile playerProfile)
    {
        if (profile.xp < profile.target) {
            return;
        }

        // Level up!
        profile.level++;
        profile.xp -= profile.target;

        // Calculate new target with multiplier
        double xpMultiplier = getDefaultConfig().getDouble(ConfigPaths.Default.Pickaxe.XP_MULTIPLIER, 1.15);
        profile.target = Math.max(1, (int) (profile.target * xpMultiplier));

        // Calculate essence reward with chestplate multiplier
        int essenceReward = calculateEssenceReward(profile.level);
        double chestplateMultiplier = getChestplateMultiplier(player);
        essenceReward = (int) Math.round(essenceReward * chestplateMultiplier);

        // Add essence to pickaxe
        profile.currentEssence += essenceReward;

        // Also add to player profile if provided
        if (playerProfile != null) {
            playerProfile.essence += essenceReward;

            // Award XP to chestplate for essence collection
            ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
            ArmorProfile armorProfile = armorDataCenter.getLocal(player);
            if (armorProfile != null) {
                ArmorItem chestplate = armorProfile.getArmor(ArmorType.CHESTPLATE);
                if (chestplate != null && chestplate.isEnabled()) {
                    chestplate.addXp(essenceReward);
                }
            }
        }

        checkEssence(profile, player);

        // Award atoms for this level-up
        int atomReward = getDefaultConfig().getInt(ConfigPaths.Default.Atoms.LEVEL_UP_REWARD, 2);
        profile.evolutionAtoms += atomReward;

        // Check atom threshold for evolution
        checkAtoms(profile, player);

        // Play level up effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Send level up message
        String levelUpMsg = getMessagesConfig().getString("pickaxe.level-up",
                "&a&l[Level Up] &ePickaxe is now level &b%level%&e! &d+%essence% essence");
        levelUpMsg = levelUpMsg
                .replace("%level%", String.valueOf(profile.level))
                .replace("%essence%", String.valueOf(essenceReward));
        player.sendMessage(Utils.formatMessage(player, levelUpMsg));

        // Refresh lore immediately so the new level/stats are visible
        updateLore(player, null);

        // Save profile
        profile.save(null);
    }

    /**
     * Checks if the essence threshold is met and awards an enchant roll if so.
     *
     * <p>Each time the threshold is hit the target is multiplied by
     * {@code EssenceMultiplier} (default 1.5), capped at {@code EssenceTargetCap}
     * (default 10 000). Starting target is 100.</p>
     *
     * @param profile the pickaxe profile
     * @param player  the owner player
     */
    public void checkEssence(PickaxeProfile profile, Player player)
    {
        if (profile.currentEssence < profile.targetEssence) {
            return;
        }

        profile.enchantRolls++;
        profile.currentEssence -= profile.targetEssence;

        double multiplier = getDefaultConfig().getDouble(ConfigPaths.Default.Pickaxe.ESSENCE_MULTIPLIER, 1.5);
        int    cap        = getDefaultConfig().getInt(ConfigPaths.Default.Pickaxe.ESSENCE_TARGET_CAP, 10000);
        profile.targetEssence = Math.min(cap, Math.max(1, (int)(profile.targetEssence * multiplier)));

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    /**
     * Checks if the atom threshold has been reached and awards an evolution roll if so.
     * Follows the same pattern as {@link #checkEssence}: subtract the target,
     * increment the roll counter, and scale the next target.
     *
     * <p>Evolution rolls are spent by the player through the pickaxe menu to
     * advance the pickaxe's evolution stage.</p>
     *
     * @param profile the pickaxe profile
     * @param player  the owner player
     */
    public void checkAtoms(PickaxeProfile profile, Player player)
    {
        if (profile.evolutionAtoms < profile.atomTarget) {
            return;
        }

        profile.evolutionRolls++;
        profile.evolutionAtoms -= profile.atomTarget;

        // Scale the next atom target
        double multiplier = getDefaultConfig().getDouble(ConfigPaths.Default.Atoms.ATOM_TARGET_MULTIPLIER, 1.5);
        profile.atomTarget = Math.max(1, (int) (profile.atomTarget * multiplier));

        // Notify the player
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        String msg = getMessagesConfig().getString(ConfigPaths.Messages.Pickaxe.EVOLVE_SUCCESS,
                "%evolv3_prefix% &6&l[EVOLUTION ROLL] &eYou earned an evolution roll!");
        player.sendMessage(Utils.formatMessage(player, msg));
    }

    /**
     * Calculates the essence reward for a level up.
     * Formula: random between ((level-1)*increment+1) and (level*increment)
     *
     * @param level the current level
     * @return the essence reward amount
     */
    private int calculateEssenceReward(int level)
    {
        int increment = getDefaultConfig().getInt(ConfigPaths.Default.Essence.TIER_INCREMENT, 10);
        int maxCap = getDefaultConfig().getInt(ConfigPaths.Default.Essence.MAX_CAP, 1000);

        int minAmount = Math.min(((level - 1) * increment) + 1, maxCap);
        int maxAmount = Math.min(level * increment, maxCap);

        return ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
    }

    /**
     * Applies an enchant obtained from the selection animation to a pickaxe profile.
     * If the player already has the enchant and it is not at max level, it is upgraded
     * by one level. If the player does not have it, it is unlocked at level 1.
     *
     * <p>The profile is saved and the pickaxe lore is refreshed after the change.</p>
     *
     * @param player   the player who selected the enchant
     * @param selected the enchant option returned by the animation
     * @param profile  the player's current pickaxe profile
     */
    public void applyEnchantFromSelection(Player player, EnchantOption selected, PickaxeProfile profile) {
        String internalName = selected.name().toLowerCase();
        int    maxLevel     = EnchantAnimationConfig.getEnchantMaxLevel(selected.name());

        FileConfiguration messages = getMessagesConfig();
        EnchantData existing = profile.enchants.getEnchant(internalName);

        if (existing != null) {
            if (existing.isMaxLevel()) {
                // Already at max — notify but don't consume the roll (caller already deducted it)
                String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_MAX_LEVEL,
                        "%evolv3_prefix% &7Your enchant is already at maximum level.");
                msg = msg.replace("%enchant_name%", selected.name())
                         .replace("%max_level%", String.valueOf(existing.maxLevel));
                player.sendMessage(Utils.formatMessage(player, msg));
            } else {
                existing.level++;
                String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_UPGRADE_SUCCESS,
                        "%evolv3_prefix% &7Upgraded %enchant_name% to level %new_level%!");
                msg = msg.replace("%enchant_name%", selected.name())
                         .replace("%new_level%", String.valueOf(existing.level));
                player.sendMessage(Utils.formatMessage(player, msg));
            }
        } else {
            // Unlock as a new enchant at level 1
            double maxChance = EnchantAnimationConfig.getEnchantMaxChance(selected.name());
            EnchantData newEnchant = new EnchantData(internalName, 1, maxLevel, maxChance);
            profile.enchants.setEnchant(newEnchant);
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_UNLOCKED,
                    "%evolv3_prefix% &#B366D9&l[ENCHANT] &#CCCCCCYou unlocked &#FFFFFF%enchant_name%&#CCCCCC!");
            msg = msg.replace("%enchant_name%", selected.name());
            player.sendMessage(Utils.formatMessage(player, msg));
        }

        profile.save(null);
        updateLore(player, null);
    }

    /**
     * Upgrades an enchant on a pickaxe by spending essence from the player's profile.
     * Deducts {@code cost * currentLevel} essence and increments the enchant level by 1.
     *
     * @param player        the player upgrading the enchant
     * @param profile       the player's pickaxe profile
     * @param playerProfile the player's overall profile (essence source)
     * @param displayName   the enchant's display name as it appears in the selection pool
     * @return {@code true} if the upgrade succeeded, {@code false} otherwise
     */
    public boolean upgradeEnchantWithEssence(Player player, PickaxeProfile profile,
                                              PlayerProfile playerProfile, String displayName) {
        String internalName = displayName.toLowerCase();
        EnchantData enchant = profile.enchants.getEnchant(internalName);
        FileConfiguration messages = getMessagesConfig();

        if (enchant == null) {
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_NOT_UNLOCKED,
                    "%evolv3_prefix% &cThis enchant has not been unlocked yet.");
            player.sendMessage(Utils.formatMessage(player, msg));
            return false;
        }

        if (enchant.isMaxLevel()) {
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_MAX_LEVEL,
                    "%evolv3_prefix% &7%enchant_name% is already at maximum level %max_level%.");
            msg = msg.replace("%enchant_name%", displayName)
                     .replace("%max_level%", String.valueOf(enchant.maxLevel));
            player.sendMessage(Utils.formatMessage(player, msg));
            return false;
        }

        int cost = EnchantAnimationConfig.getEnchantCost(displayName) * enchant.level;

        if (playerProfile.essence < cost) {
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_INSUFFICIENT_ESSENCE,
                    "%evolv3_prefix% &7You need &5🔥 &d%required_essence% &7essence to upgrade &f%enchant_name%&7.");
            msg = msg.replace("%required_essence%", String.valueOf(cost))
                     .replace("%enchant_name%", displayName);
            player.sendMessage(Utils.formatMessage(player, msg));
            return false;
        }

        playerProfile.essence -= cost;
        enchant.level++;

        profile.save(null);
        playerProfile.save(null);
        updateLore(player, null);

        String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_UPGRADE_SUCCESS,
                "%evolv3_prefix% &7Successfully upgraded &f%enchant_name% &7to level &e%new_level%&7!");
        msg = msg.replace("%enchant_name%", displayName)
                 .replace("%new_level%", String.valueOf(enchant.level));
        player.sendMessage(Utils.formatMessage(player, msg));

        return true;
    }

    /**
     * Builds lore components for a specific pickaxe profile directly, bypassing the
     * PAPI main-hand lookup. Intended for displaying stored pickaxes in the storage menu.
     *
     * <p>All pickaxe-specific PAPI placeholders are replaced with values read directly
     * from the provided profile, so PAPI never needs to look at the player's held item.</p>
     *
     * @param player      the viewing player (used for color formatting only)
     * @param profile     the pickaxe profile to render
     * @param footerLines replacement footer lines (overrides the config LoreFooter)
     * @return Adventure Components ready to set as item lore
     */
    public List<Component> buildLoreComponents(Player player, PickaxeProfile profile, List<String> footerLines)
    {
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        List<String> loreTemplate = getDefaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE);
        List<String> rawLore = new ArrayList<>();

        for (String line : loreTemplate) {
            if (line.contains("%evolv3_pickaxe_enchants%")) {
                handleEnchantsLore(profile, rawLore, player);
            } else {
                rawLore.add(Utils.formatMessage(player, substitutePlaceholders(line, profile)));
            }
        }

        for (String line : footerLines) {
            rawLore.add(Utils.formatMessage(player, substitutePlaceholders(line, profile)));
        }

        List<Component> components = new ArrayList<>();
        for (String line : rawLore) {
            if (line.isEmpty()) {
                components.add(Component.empty());
            } else {
                components.add(serializer.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
        }
        return components;
    }

    /**
     * Replaces all pickaxe-specific PAPI placeholders with values taken directly from
     * the profile. Call this before {@link Utils#formatMessage} when the pickaxe is not
     * in the player's main hand, so PAPI does not attempt to read the wrong item.
     *
     * @param line    the template line containing placeholders
     * @param profile the profile whose values should be substituted
     * @return the line with all known pickaxe placeholders replaced
     */
    private String substitutePlaceholders(String line, PickaxeProfile profile)
    {
        Player owner = profile.getOwner();
        String ownerName = owner != null ? owner.getName() : "";
        return line
                .replace("%evolv3_player_name%", ownerName)
                .replace("%evolv3_pickaxe_level%", String.valueOf(profile.level))
                .replace("%evolv3_pickaxe_xp%", String.valueOf(profile.xp))
                .replace("%evolv3_pickaxe_target%", String.valueOf(profile.target))
                .replace("%evolv3_pickaxe_percentage%", profile.getPercentage())
                .replace("%evolv3_pickaxe_progressbar%", profile.getProgressBar())
                .replace("%evolv3_pickaxe_essence%", String.valueOf(profile.currentEssence))
                .replace("%evolv3_pickaxe_target_essence%", String.valueOf(profile.targetEssence))
                .replace("%evolv3_pickaxe_enchant_rolls%", String.valueOf(profile.enchantRolls))
                .replace("%evolv3_pickaxe_evolution_atoms%", String.valueOf(profile.evolutionAtoms))
                .replace("%evolv3_pickaxe_atom_target%", String.valueOf(profile.atomTarget))
                .replace("%evolv3_pickaxe_evolution_rolls%", String.valueOf(profile.evolutionRolls));
    }

    /**
     * Updates the lore of a player's pickaxe with current stats and enchants.
     *
     * @param player    the player
     * @param itemStack the item to update, or null for main hand
     */
    public void updateLore(Player player, ItemStack itemStack)
    {
        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        dataCenter.get(player, profile -> {
            if (profile == null) {
                return;
            }

            ItemStack item = itemStack != null ? itemStack : player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                return;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }

            List<String> loreTemplate = getDefaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE);
            List<String> loreFooter = getDefaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE_FOOTER);
            List<String> formattedLore = new ArrayList<>();

            for (String line : loreTemplate) {
                if (line.contains("%evolv3_pickaxe_enchants%")) {
                    handleEnchantsLore(profile, formattedLore, player);
                } else {
                    formattedLore.add(Utils.formatMessage(player, line));
                }
            }

            for (String line : loreFooter) {
                formattedLore.add(Utils.formatMessage(player, line));
            }

            List<Component> loreComponents = new ArrayList<>();
            LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
            for (String line : formattedLore) {
                // Blank lines must use Component.empty() to render reliably as a blank lore line
                if (line.isEmpty()) {
                    loreComponents.add(Component.empty());
                } else {
                    loreComponents.add(serializer.deserialize(line).decoration(TextDecoration.ITALIC, false));
                }
            }
            meta.lore(loreComponents);

            String name = getDefaultConfig().getString(ConfigPaths.Default.Pickaxe.NAME, "&bPickaxe");
            String formattedName = Utils.formatMessage(player, name);
            meta.displayName(serializer.deserialize(formattedName).decoration(TextDecoration.ITALIC, false));

            EnchantData efficiency = profile.enchants.getEnchant(Enchants.EFFICIENCY);
            if (efficiency != null && efficiency.level > 0) {
                EfficiencyEnchant.applySpeedModifier(meta, efficiency.level);
            } else {
                EfficiencyEnchant.removeSpeedModifier(meta);
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        });
    }

    /**
     * Handles the enchants placeholder in lore, replacing it with one line per enchant.
     * Each enchant line is coloured with the enchant's rarity colour and includes the level.
     *
     * @param profile the pickaxe profile
     * @param newLore the list to add formatted lines to
     * @param player  the player for formatting
     */
    private void handleEnchantsLore(PickaxeProfile profile, List<String> newLore, Player player)
    {
        if (profile.enchants == null || profile.enchants.isEmpty()) {
            String noEnchants = getEnchantsConfig().getString(ConfigPaths.Enchants.NO_ENCHANTS, "&cNone");
            newLore.add(Utils.formatMessage(player, " " + noEnchants));
            return;
        }

        for (EnchantData enchant : profile.enchants.items) {
            String displayName = EnchantAnimationConfig.getDisplayName(enchant.name);

            // Resolve rarity colour — uppercase hex required for MatrixColorAPI
            var option = EnchantAnimationConfig.getOption(displayName);
            String rarityColor = option != null
                    ? "&" + option.rarity().textColor().asHexString().toUpperCase()
                    : "&#CCCCCC";

            String levelDisplay;
            if (enchant.isMaxLevel()) {
                levelDisplay = getEnchantsConfig().getString(ConfigPaths.Enchants.MAX_ENCHANT, "&6&lMAX");
            } else {
                levelDisplay = String.valueOf(enchant.level);
            }

            // " [rarity-color][Name] [level]"
            String enchantLine = " " + rarityColor + displayName + " " + levelDisplay;
            newLore.add(Utils.formatMessage(player, enchantLine));
        }
    }
}
