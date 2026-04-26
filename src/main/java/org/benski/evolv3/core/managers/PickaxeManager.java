package org.benski.evolv3.core.managers;

import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.enchants.EfficiencyEnchant;
import org.benski.evolv3.enums.ArmorType;
import org.benski.evolv3.models.armor.ArmorItem;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
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
     * Adds XP directly to a player's pickaxe profile, bypassing block lookups.
     * Used by stone effects that grant bonus XP on proc.
     *
     * @param player the player
     * @param amount the XP amount to add
     */
    public void addXp(Player player, int amount) {
        PickaxeDataCenter dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        PickaxeProfile profile = dc.getLocal(player);
        if (profile == null) return;
        profile.xp += amount;
        checkLevel(profile, player);
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

        while (profile.xp >= profile.target) {
            // Level up!
            int oldLevel = profile.level;
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

            // Play level up effects (client-side sound via Adventure key — not in server Sound registry)
            player.playSound(net.kyori.adventure.sound.Sound.sound(
                    net.kyori.adventure.key.Key.key("minecraft", "ui.toast.goal_complete"),
                    net.kyori.adventure.sound.Sound.Source.MASTER,
                    1.0f, 1.0f));

            // Trigger scoreboard level-up flash animation
            org.benski.evolv3.core.managers.EvolveScoreboardManager.getInstance()
                    .triggerLevelUpAnimation(player, profile.level);

            // Send level up message (multi-line, centered)
            List<String> levelUpLines = getMessagesConfig().getStringList(ConfigPaths.Messages.Pickaxe.LEVEL_UP);
            if (levelUpLines.isEmpty()) {
                Main.getInstance().getLogger().warning(
                        "Missing messages.yml key: " + ConfigPaths.Messages.Pickaxe.LEVEL_UP);
                levelUpLines = List.of("Level up! Now level %level%.");
            }
            for (String line : levelUpLines) {
                String processed = line
                        .replace("%old_level%", String.valueOf(oldLevel))
                        .replace("%level%", String.valueOf(profile.level))
                        .replace("%essence%", String.valueOf(essenceReward))
                        .replace("%atoms%", String.valueOf(atomReward));
                player.sendMessage(Utils.formatMessage(player, processed));
            }

            // Refresh lore so the new level/stats are visible after each level-up
            updateLore(player, null);
        }

        // Save once after all level-ups are processed
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

        double multiplier = getDefaultConfig().getDouble(ConfigPaths.Default.Pickaxe.ESSENCE_MULTIPLIER, 1.5);
        int    cap        = getDefaultConfig().getInt(ConfigPaths.Default.Pickaxe.ESSENCE_TARGET_CAP, 10000);

        while (profile.currentEssence >= profile.targetEssence) {
            profile.enchantRolls++;
            profile.currentEssence -= profile.targetEssence;
            profile.targetEssence = Math.min(cap, Math.max(1, (int)(profile.targetEssence * multiplier)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        }
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

        double multiplier = getDefaultConfig().getDouble(ConfigPaths.Default.Atoms.ATOM_TARGET_MULTIPLIER, 1.5);
        String msg = getMessagesConfig().getString(ConfigPaths.Messages.Pickaxe.EVOLVE_SUCCESS,
                "%evolv3_prefix% &6&l[EVOLUTION ROLL] &eYou earned an evolution roll!");

        while (profile.evolutionAtoms >= profile.atomTarget) {
            profile.evolutionRolls++;
            profile.evolutionAtoms -= profile.atomTarget;

            // Scale the next atom target
            profile.atomTarget = Math.max(1, (int) (profile.atomTarget * multiplier));

            // Notify the player
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.sendMessage(Utils.formatMessage(player, msg));
        }
    }

    // -------------------------------------------------------------------------
    // Evolution
    // -------------------------------------------------------------------------

    /**
     * Validates whether a player can evolve their pickaxe, sending an error message
     * and playing a failure sound if not.
     *
     * @param player  the player attempting to evolve
     * @param profile the pickaxe profile to check
     * @return true if evolution is possible, false if blocked
     */
    public boolean validateEvolution(@org.jetbrains.annotations.NotNull Player player,
                                     @org.jetbrains.annotations.NotNull PickaxeProfile profile)
    {
        FileConfiguration messages = getMessagesConfig();
        EvolutionTier current = EvolutionTier.fromLevel(profile.evolution);

        if (current.isMax()) {
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.EVOLVE_MAX_TIER,
                    "%evolv3_prefix% &#CCCCCCYour pickaxe is already at the highest tier.");
            player.sendMessage(Utils.formatMessage(player, msg));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return false;
        }

        if (profile.evolutionRolls <= 0) {
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.EVOLVE_INSUFFICIENT_SPINS,
                    "%evolv3_prefix% &#CCCCCCNot enough Evolution Rolls.");
            player.sendMessage(Utils.formatMessage(player, msg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        return true;
    }

    /**
     * Applies the result of an evolution after the animation completes.
     *
     * <p>Updates the pickaxe tier, material, lore, and sends the success message
     * including the stone rarity reward. Should only be called from the
     * {@link org.benski.evolv3.animations.EvolutionAnimation} callback.</p>
     *
     * @param player        the owning player
     * @param profile       the pickaxe profile to update
     * @param playerProfile the player's profile (holds stored-pickaxe entries)
     * @param next          the tier the pickaxe is advancing to
     * @param stone         the evolution stone rarity awarded
     */
    public void applyEvolutionResult(@org.jetbrains.annotations.NotNull Player player,
                                     @org.jetbrains.annotations.NotNull PickaxeProfile profile,
                                     @org.jetbrains.annotations.NotNull PlayerProfile playerProfile,
                                     @org.jetbrains.annotations.NotNull EvolutionTier next,
                                     @org.jetbrains.annotations.NotNull org.benski.evolv3.core.types.RarityConfig stone)
    {
        profile.evolution = next.level;

        // Keep the player's persisted highestEvolution in sync so the storage menu
        // shows the correct slot count even when this pickaxe is put into storage.
        if (next.level > playerProfile.highestEvolution) {
            playerProfile.highestEvolution = next.level;
        }

        updateStoredPickaxeMaterial(playerProfile, profile.key, next.material);

        // Update held item material if this pickaxe is currently in the player's hand
        ItemStack held = player.getInventory().getItemInMainHand();
        String heldId = ItemStackExtensions.getData(held, "id");
        if (profile.key.equals(heldId)) {
            held.setType(next.material);
            player.getInventory().setItemInMainHand(held);
        }

        // Award essence reward
        int essenceReward = getDefaultConfig().getInt(
                ConfigPaths.Messages.Pickaxe.EVOLVE_ESSENCE_REWARD, 250);
        profile.currentEssence  += essenceReward;
        playerProfile.essence   += essenceReward;
        checkEssence(profile, player);

        profile.save(null);
        playerProfile.save(null);
        updateLore(player, null);

        // Build rarity color tag (&#RRGGBB format)
        String rarityHex   = String.format("%06X", stone.textColor().value());
        String stoneColor  = "&#" + rarityHex;

        // Send multi-line result message (mirrors the level-up format)
        FileConfiguration messages = getMessagesConfig();
        List<String> resultLines = messages.getStringList(ConfigPaths.Messages.Pickaxe.EVOLVE_RESULT);
        if (resultLines.isEmpty()) {
            // Fallback single-line if config missing
            String fallback = "%evolv3_prefix% &#FFD700&l[Evolution] &#CCCCCCPickaxe evolved to %tier%&#CCCCCC!";
            player.sendMessage(Utils.formatMessage(player, fallback.replace("%tier%", next.displayName)));
        } else {
            for (String line : resultLines) {
                String formatted = line
                        .replace("%tier%",              next.displayName)
                        .replace("%evolution_level%",   String.valueOf(next.level))
                        .replace("%essence%",           String.valueOf(essenceReward))
                        .replace("%stone_color%",       stoneColor)
                        .replace("%stone_rarity%",      stone.displayName());
                player.sendMessage(Utils.formatMessage(player, formatted));
            }
        }
    }

    /**
     * Updates the material stored in the {@code storedPickaxes} list entry for the
     * given pickaxe ID. The entry format is {@code "pickaxeId:MATERIAL_NAME"}.
     */
    private void updateStoredPickaxeMaterial(@org.jetbrains.annotations.NotNull PlayerProfile playerProfile,
                                             @org.jetbrains.annotations.NotNull String pickaxeId,
                                             @org.jetbrains.annotations.NotNull Material material)
    {
        List<String> stored = playerProfile.storedPickaxes;
        for (int i = 0; i < stored.size(); i++) {
            String entry = stored.get(i);
            if (entry != null && entry.startsWith(pickaxeId + ":")) {
                stored.set(i, pickaxeId + ":" + material.name());
                return;
            }
        }
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
        String internalName = selected.name().toLowerCase().replace(" ", "");
        int    maxLevel     = EnchantAnimationConfig.getEnchantMaxLevel(selected.name());

        FileConfiguration messages = getMessagesConfig();
        EnchantData existing = profile.enchants.getEnchant(internalName);

        if (existing != null) {
            if (existing.isMaxLevel()) {
                // Already at max — notify via action bar since the animation has ended by now
                String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_MAX_LEVEL,
                        "%evolv3_prefix% &7Your enchant is already at maximum level.");
                msg = msg.replace("%enchant_name%", selected.name())
                         .replace("%max_level%", String.valueOf(existing.maxLevel));
                player.sendMessage(Utils.formatMessage(player, msg));
            } else {
                existing.level++;
                // Result already communicated by the animation's action bar (✦ +1 LEVEL)
            }
        } else {
            // Unlock as a new enchant at level 1
            double maxChance = EnchantAnimationConfig.getEnchantMaxChance(selected.name());
            EnchantData newEnchant = new EnchantData(internalName, 1, maxLevel, maxChance);
            profile.enchants.setEnchant(newEnchant);
            // Result already communicated by the animation's action bar (✦ UNLOCKED)
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
        String internalName = displayName.toLowerCase().replace(" ", "");
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
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
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
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

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
        return buildLoreComponents(player, profile, footerLines, "");
    }

    public List<Component> buildLoreComponents(Player player, PickaxeProfile profile,
                                               List<String> footerLines, String durabilityBar)
    {
        List<String> loreTemplate = getDefaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE);
        List<String> rawLore = buildFormattedLoreStrings(player, profile, loreTemplate, durabilityBar);

        for (String line : footerLines) {
            rawLore.add(Utils.formatMessage(player, substitutePlaceholders(line, profile, durabilityBar)));
        }

        return toComponents(rawLore);
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
    private String substitutePlaceholders(String line, PickaxeProfile profile, String durabilityBar)
    {
        Player owner = profile.getOwner();
        String ownerName = owner != null ? owner.getName() : "";
        return line
                .replace("%evolv3_player_name%", ownerName)
                .replace("%evolv3_pickaxe_level%", String.valueOf(profile.level))
                .replace("%evolv3_pickaxe_xp%", Utils.formatNumber(profile.xp))
                .replace("%evolv3_pickaxe_target%", Utils.formatNumber(profile.target))
                .replace("%evolv3_pickaxe_percentage%", profile.getPercentage())
                .replace("%evolv3_pickaxe_progressbar%", profile.getProgressBar())
                .replace("%evolv3_pickaxe_durability_bar%", durabilityBar)
                .replace("%evolv3_pickaxe_essence%", Utils.formatNumber(profile.currentEssence))
                .replace("%evolv3_pickaxe_target_essence%", Utils.formatNumber(profile.targetEssence))
                .replace("%evolv3_pickaxe_enchant_rolls%", Utils.formatNumber(profile.enchantRolls))
                .replace("%evolv3_pickaxe_evolution_atoms%", Utils.formatNumber(profile.evolutionAtoms))
                .replace("%evolv3_pickaxe_atom_target%", Utils.formatNumber(profile.atomTarget))
                .replace("%evolv3_pickaxe_evolution_rolls%", Utils.formatNumber(profile.evolutionRolls));
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
            if (profile == null) return;
            ItemStack item = itemStack != null ? itemStack : player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) return;
            applyLoreAndName(player, profile, item);
        });
    }

    /**
     * Scans the player's full inventory for a pickaxe and updates its lore.
     * Used on join when the player may not be holding the pickaxe in main hand
     * (e.g. they joined while inside an Omnitool zone on a previous session).
     */
    public void updateLoreFromInventory(Player player)
    {
        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++)
        {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!item.getType().name().endsWith("_PICKAXE")) continue;
            dataCenter.get(player, profile -> {
                if (profile == null) return;
                applyLoreAndName(player, profile, item);
            });
            return;
        }
    }

    /**
     * Directly applies lore, display name, and enchant modifiers to an item using a known profile.
     * Unlike {@link #updateLore}, this does not require the item to be in the player's main hand.
     *
     * @param player  the player (used for placeholder resolution)
     * @param profile the pickaxe profile to read stats from
     * @param item    the item to update in place
     */
    public void applyLoreAndName(Player player, PickaxeProfile profile, ItemStack item)
    {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Compute durability bar from the actual item's current damage
        int damage = meta instanceof Damageable dmg ? dmg.getDamage() : 0;
        String durabilityBar = Utils.buildDurabilityBar(damage, item.getType().getMaxDurability());

        List<String> loreTemplate = getDefaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE);
        List<String> loreFooter   = getDefaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE_FOOTER);

        List<String> formattedLore = buildFormattedLoreStrings(player, profile, loreTemplate, durabilityBar);

        // Insert omnitool identifier tag as the very first lore line (directly under the item name)
        String omnitoolTag = getDefaultConfig().getString(ConfigPaths.Default.Pickaxe.OMNITOOL_LORE_TAG, "&7omnitool");
        formattedLore.add(0, Utils.formatMessage(player, omnitoolTag));

        formattedLore.add("");
        String durabilityLabel = getDefaultConfig().getString(ConfigPaths.Default.Pickaxe.DURABILITY_LABEL, " &8| &#00FFFFDurability:");
        formattedLore.add(Utils.formatMessage(player, durabilityLabel));
        String barPrefix = getDefaultConfig().getString(ConfigPaths.Default.Pickaxe.DURABILITY_BAR_PREFIX, " &8| ");
        formattedLore.add(Utils.formatMessage(player, barPrefix + durabilityBar));

        for (String line : loreFooter) {
            formattedLore.add(Utils.formatMessage(player, substitutePlaceholders(line, profile, durabilityBar)));
        }

        meta.lore(toComponents(formattedLore));

        String name = getDefaultConfig().getString(ConfigPaths.Default.Pickaxe.NAME, "&bPickaxe");
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(Utils.formatMessage(player, name))
                .decoration(TextDecoration.ITALIC, false));

        EnchantData efficiency = profile.enchants.getEnchant(Enchants.EFFICIENCY);
        if (efficiency != null && efficiency.level > 0) {
            EfficiencyEnchant.applySpeedModifier(meta, efficiency.level, efficiency);
        } else {
            EfficiencyEnchant.removeSpeedModifier(meta);
        }

        // Apply glow effect when the pickaxe has at least one enchant unlocked
        Enchantment glowEnchant = ItemStackExtensions.getEnchantmentByKey("minecraft", "unbreaking");
        if (glowEnchant != null) {
            if (!profile.enchants.isEmpty()) {
                meta.addEnchant(glowEnchant, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.removeEnchant(glowEnchant);
            }
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }

    /**
     * Builds the list of formatted lore strings from a template, expanding the enchants
     * placeholder inline. Used by both {@link #buildLoreComponents} and {@link #applyLoreAndName}
     * to avoid duplicating the iteration logic.
     */
    private List<String> buildFormattedLoreStrings(Player player, PickaxeProfile profile,
                                                    List<String> template, String durabilityBar) {
        List<String> result = new ArrayList<>();
        for (String line : template) {
            if (line.contains("%evolv3_pickaxe_enchants%")) {
                handleEnchantsLore(profile, result, player);
            } else {
                result.add(Utils.formatMessage(player, substitutePlaceholders(line, profile, durabilityBar)));
            }
        }
        return result;
    }

    /**
     * Converts a list of legacy-colour strings to Adventure {@link Component}s with italic suppressed.
     */
    private List<Component> toComponents(List<String> lines) {
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line.isEmpty()) {
                components.add(Component.empty());
            } else {
                components.add(serializer.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
        }
        return components;
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
            newLore.add(Utils.formatMessage(player, " &8| " + noEnchants));
            return;
        }

        List<EnchantData> sorted = profile.enchants.items.stream()
                .sorted(java.util.Comparator.comparingInt(e ->
                        EnchantAnimationConfig.getEnchantRarityIndex(
                                EnchantAnimationConfig.getDisplayName(e.name))))
                .toList();

        for (EnchantData enchant : sorted) {
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

            // Build stone socket icons — only filled sockets
            StringBuilder stoneIconsBuilder = new StringBuilder();
            for (String stoneRarity : enchant.socketStones) {
                if (stoneRarity != null) {
                    RarityConfig stoneRarConfig = EnchantAnimationConfig.getRarityByName(stoneRarity);
                    String sHex = stoneRarConfig != null
                            ? String.format("%06X", stoneRarConfig.textColor().value())
                            : "FFFFFF";
                    stoneIconsBuilder.append("&#").append(sHex).append("◆");
                }
            }
            String stoneIcons = stoneIconsBuilder.length() > 0 ? stoneIconsBuilder + " " : "";

            // " &8| [stone icons][rarity-color][Name] [level]"
            String enchantLine = " &8| " + stoneIcons + rarityColor + displayName + " " + levelDisplay;
            newLore.add(Utils.formatMessage(player, enchantLine));
        }
    }
}
