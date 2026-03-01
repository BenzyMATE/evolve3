package org.benski.evolv3.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.managers.PickaxeManager;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Menu displaying a player's pickaxe statistics, enchant roll controls,
 * and the full enchant grid where unlocked enchants can be upgraded with essence.
 */
public class PickaxeMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("pickaxe");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // -------------------------------------------------------------------------
    // Slot constants
    // -------------------------------------------------------------------------

    private static final int[] EVOLUTION_SLOTS = {29, 38, 47};
    private static final int[] ESSENCE_SLOTS   = {33, 42, 51};
    private static final int   PICKAXE_SLOT    = 40;

    /** 7 columns × 3 rows of enchant slots occupying the top half of the GUI. */
    private static final int[] ENCHANT_SLOTS = {
        1,  2,  3,  4,  5,  6,  7,
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };

    // -------------------------------------------------------------------------

    private final PickaxeDataCenter pickaxeDataCenter;
    private int animationFrame = 1;

    public PickaxeMenu(JavaPlugin plugin) {
        super(plugin);
        pickaxeDataCenter = (PickaxeDataCenter) DataCenterRegistry.getInstance().get("pickaxe");
    }

    // -------------------------------------------------------------------------
    // BaseMenu hooks
    // -------------------------------------------------------------------------

    @Override
    protected void onOpen(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.3f);
    }

    @Override
    protected void onClose(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
    }

    @Override
    protected boolean isDynamicTitle() {
        return true;
    }

    @Override
    protected int getTitleUpdateInterval() {
        return 10;
    }

    @Override
    protected String getMenuTitle(Player player) {
        var pickaxeProfile = pickaxeDataCenter.getLocal(player);
        if (pickaxeProfile == null) {
            return "&cLoading...";
        }

        animationFrame++;
        if (animationFrame > 5) {
            animationFrame = 1;
        }

        String essenceIcon = getEssenceProgress(pickaxeProfile, animationFrame);
        String evolveIcon  = getEvolveProgress(pickaxeProfile, animationFrame);

        return "&f" + "七".repeat(52) + "⒑" + "七".repeat(89)
                + essenceIcon + "七".repeat(105) + evolveIcon;
    }

    @Override
    protected int getMenuSize() {
        return 54;
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory)
    {
        PickaxeProfile profile = pickaxeDataCenter.getLocal(player);
        if (profile == null) return;

        // Enchant grid (slots 1-7, 10-16, 19-25)
        buildEnchantSlots(player, inventory, profile);

        // Slots 29, 38, 47: Evolution statistics
        ItemStack evolutionItem = buildEvolutionItem(player);
        for (int slot : EVOLUTION_SLOTS) {
            inventory.setItem(slot, evolutionItem.clone());
        }

        // Slots 33, 42, 51: Essence & enchant roll statistics
        ItemStack essenceItem = buildEssenceItem(player);
        for (int slot : ESSENCE_SLOTS) {
            inventory.setItem(slot, essenceItem.clone());
        }

        // Slot 40: Clone of the player's held pickaxe (footer stripped)
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.AIR) {
            ItemStack display = held.clone();
            FileConfiguration defaultConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            int footerSize = defaultConfig.getStringList(ConfigPaths.Default.Pickaxe.LORE_FOOTER).size();
            ItemMeta displayMeta = display.getItemMeta();
            if (displayMeta != null && footerSize > 0) {
                List<Component> lore = displayMeta.lore();
                if (lore != null && lore.size() > footerSize) {
                    List<Component> trimmed = new ArrayList<>(lore.subList(0, lore.size() - footerSize));
                    trimmed.add(Component.empty());
                    displayMeta.lore(trimmed);
                    display.setItemMeta(displayMeta);
                }
            }
            inventory.setItem(PICKAXE_SLOT, display);
        }
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event)
    {
        // Enchant grid slots — only respond to slots that have an unlocked enchant
        PickaxeProfile clickProfile = pickaxeDataCenter.getLocal(player);
        if (clickProfile != null) {
            for (int i = 0; i < ENCHANT_SLOTS.length; i++) {
                if (slot == ENCHANT_SLOTS[i]) {
                    if (i < clickProfile.enchants.items.size()) {
                        EnchantData enchant = clickProfile.enchants.items.get(i);
                        handleEnchantSlotClick(player, EnchantAnimationConfig.getDisplayName(enchant.name));
                    }
                    return true;
                }
            }
        }

        // Evolution stat slots — coming soon
        for (int s : EVOLUTION_SLOTS) {
            if (slot == s) {
                FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
                String msg = messages.getString(
                        ConfigPaths.Messages.Pickaxe.MENU_EVOLUTION_COMING_SOON,
                        "%evolv3_prefix% &7Evolution feature coming soon!");
                player.sendMessage(formatMessage(player, msg));
                return true;
            }
        }

        // Essence/enchant roll slots — trigger enchant animation
        for (int s : ESSENCE_SLOTS) {
            if (slot == s) {
                handleEnchantRollClick(player);
                return true;
            }
        }

        return false;
    }

    @Override
    public MenuType getMenuType() {
        return MENU_TYPE;
    }

    // -------------------------------------------------------------------------
    // Enchant grid rendering
    // -------------------------------------------------------------------------

    /**
     * Populates the enchant grid slots with unlocked enchant items, filling from slot 0.
     * Any remaining slots beyond the player's unlocked count are filled with invisible MAP placeholders.
     *
     * @param player    the player viewing the menu
     * @param inventory the inventory to populate
     * @param profile   the player's pickaxe profile
     */
    private void buildEnchantSlots(Player player, Inventory inventory, PickaxeProfile profile)
    {
        List<EnchantData> unlocked = profile.enchants.items;
        FileConfiguration menus    = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

        for (int i = 0; i < ENCHANT_SLOTS.length; i++) {
            int slot = ENCHANT_SLOTS[i];
            if (i < unlocked.size()) {
                EnchantData enchant   = unlocked.get(i);
                String      displayName = EnchantAnimationConfig.getDisplayName(enchant.name);
                inventory.setItem(slot, buildUnlockedEnchantItem(player, displayName, enchant));
            } else {
                inventory.setItem(slot, buildEmptyEnchantSlot(player, menus));
            }
        }
    }

    /**
     * Builds the display item for an enchant the player has unlocked.
     * The skull icon and lore template are read from {@code enchants.yml} via
     * {@link EnchantAnimationConfig}. Tokens in the lore ({@code {level}}, etc.) are
     * replaced with live values before rendering.
     *
     * @param player      the player (for formatting / PlaceholderAPI)
     * @param displayName the enchant's display name (e.g. "Auto-Smelt")
     * @param enchant     the player's current enchant data
     * @return the configured ItemStack
     */
    private ItemStack buildUnlockedEnchantItem(Player player, String displayName, EnchantData enchant)
    {
        var option = EnchantAnimationConfig.getOption(displayName);
        RarityConfig rarity = option != null ? option.rarity() : null;

        // Rarity colour as a legacy &#RRGGBB string — force uppercase hex so MatrixColorAPI
        // can process it (asHexString() returns lowercase, e.g. "#55ffff").
        String rarityHex   = rarity != null ? rarity.textColor().asHexString().toUpperCase() : "#CCCCCC";
        String rarityColor = "&" + rarityHex;       // e.g. "&#55FFFF"
        String rarityName  = rarity != null ? rarity.displayName() : "Unknown";
        String rarityStr   = rarityColor + rarityName;

        int    level    = enchant.level;
        int    maxLevel = enchant.maxLevel;
        double chance   = enchant.getCurrentChance();
        int    cost     = EnchantAnimationConfig.getEnchantCost(displayName) * level;
        String status   = enchant.enabled ? "&#00FF88Enabled" : "&#FF5555Disabled";

        // Read lore template from config and substitute all runtime tokens
        List<String> rawLore = EnchantAnimationConfig.getEnchantLore(displayName);
        List<Component> lore = new ArrayList<>(rawLore.size() + 3);
        for (String line : rawLore) {
            // Blank lines must be Component.empty() to render as a blank lore line reliably.
            if (line.isEmpty()) {
                lore.add(Component.empty());
                continue;
            }
            String processed = line
                    .replace("{rarity}",      rarityStr)
                    .replace("{rarityColor}", rarityColor)
                    .replace("{level}",       String.valueOf(level))
                    .replace("{maxLevel}",    String.valueOf(maxLevel))
                    .replace("{cost}",        String.valueOf(cost))
                    .replace("{chance}",      String.format("%.1f", chance))
                    .replace("{status}",      status);
            lore.add(deserialize(formatMessage(player, processed)));
        }

        // Append max-level indicator when the enchant is fully levelled
        if (enchant.isMaxLevel()) {
            lore.add(Component.empty());
            lore.add(deserialize(formatMessage(player, " &#00AFA3&lMAX LEVEL")));
            lore.add(Component.empty());
        }

        // Skull icon from config texture URL, or material fallback
        ItemStack item = EnchantAnimationConfig.createEnchantItem(displayName);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(deserialize(formatMessage(player, rarityColor + "&l" + displayName)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Builds an invisible MAP placeholder for an empty enchant slot.
     * Uses the LockedEnchant config from menus.yml for name and lore text.
     *
     * @param player      the player (for formatting)
     * @param menusConfig the menus.yml config
     * @return an invisible MAP ItemStack with placeholder text
     */
    private ItemStack buildEmptyEnchantSlot(Player player, FileConfiguration menusConfig)
    {
        String rawName = menusConfig.getString(ConfigPaths.Menus.PickaxeMenu.ENCHANT_LOCKED_NAME, " ");
        List<String> rawLore = menusConfig.getStringList(ConfigPaths.Menus.PickaxeMenu.ENCHANT_LOCKED_LORE);

        List<Component> lore = buildLoreFromConfig(player, rawLore);
        return buildInvisibleMapItem(deserialize(formatMessage(player, rawName)), lore);
    }

    // -------------------------------------------------------------------------
    // Click handlers
    // -------------------------------------------------------------------------

    /**
     * Handles a left-click on an enchant grid slot.
     * If the enchant is unlocked, attempts to spend essence to level it up.
     * If locked, informs the player they need an enchant roll.
     *
     * @param player      the player who clicked
     * @param displayName the enchant's display name for the clicked slot
     */
    private void handleEnchantSlotClick(Player player, String displayName)
    {
        PickaxeProfile profile = pickaxeDataCenter.getLocal(player);
        if (profile == null) return;

        String internalName = displayName.toLowerCase();
        EnchantData enchant = profile.enchants.getEnchant(internalName);
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

        if (enchant == null) {
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_NOT_UNLOCKED,
                    "%evolv3_prefix% &cThis enchant has not been unlocked yet.");
            player.sendMessage(formatMessage(player, msg));
            return;
        }

        var playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDataCenter == null) return;

        var playerProfile = playerDataCenter.getLocal(player);
        if (playerProfile == null) return;

        boolean upgraded = PickaxeManager.getInstance()
                .upgradeEnchantWithEssence(player, profile, playerProfile, displayName);

        if (upgraded) {
            // Refresh the enchant grid so the new level is reflected immediately
            refresh(player);
        }
    }

    /**
     * Handles a click on an essence/enchant roll slot.
     * Deducts one enchant roll and starts the enchant selection animation.
     * The animation callback applies or upgrades the chosen enchant.
     *
     * @param player the player who clicked
     */
    private void handleEnchantRollClick(Player player)
    {
        PickaxeProfile profile = pickaxeDataCenter.getLocal(player);
        if (profile == null) return;

        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

        if (EnchantSelectionManager.getInstance().hasActiveSelection(player)) {
            String msg = messages.getString("enchant-selection.already-active",
                    "&cYou already have an enchant selection active!");
            player.sendMessage(formatMessage(player, msg));
            return;
        }

        if (profile.enchantRolls <= 0) {
            String msg = messages.getString(ConfigPaths.Messages.Pickaxe.ENCHANT_NO_SPINS,
                    "%evolv3_prefix% &cYou need at least 1 enchant spin to use this.");
            player.sendMessage(formatMessage(player, msg));
            return;
        }

        profile.enchantRolls--;
        profile.save();

        closeMenu(player);

        EnchantSelectionManager.getInstance().startSelection(player, profile.key, (p, selected) -> {
            PickaxeDataCenter dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
            if (dc == null) return;
            dc.get(p, updatedProfile -> {
                if (updatedProfile == null) return;
                PickaxeManager.getInstance().applyEnchantFromSelection(p, selected, updatedProfile);
            });
        });
    }

    // -------------------------------------------------------------------------
    // Item builders — essence / evolution panels
    // -------------------------------------------------------------------------

    /**
     * Builds the invisible evolution-statistics item for slots 29, 38, 47.
     *
     * @param player the player viewing the menu
     * @return the built ItemStack
     */
    private ItemStack buildEvolutionItem(Player player)
    {
        FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
        String rawName = menus.getString(ConfigPaths.Menus.PickaxeMenu.EVOLVE_NAME,
                "&#00BFFF⭐ &#FFFFFF&lEvolution");
        List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PickaxeMenu.EVOLVE_LORE);

        List<Component> lore = buildLoreFromConfig(player, rawLore);
        return buildInvisibleMapItem(deserialize(formatMessage(player, rawName)), lore);
    }

    /**
     * Builds the invisible essence/enchant-roll statistics item for slots 33, 42, 51.
     *
     * @param player the player viewing the menu
     * @return the built ItemStack
     */
    private ItemStack buildEssenceItem(Player player)
    {
        FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
        String rawName = menus.getString(ConfigPaths.Menus.PickaxeMenu.ESSENCE_NAME,
                "&#9932CC🔥 &#FFFFFF&lEssence");
        List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PickaxeMenu.ESSENCE_LORE);

        List<Component> lore = buildLoreFromConfig(player, rawLore);
        return buildInvisibleMapItem(deserialize(formatMessage(player, rawName)), lore);
    }

    /**
     * Creates a MAP item with custom model data 1 (invisible via resource pack).
     *
     * @param displayName the display name component
     * @param lore        the lore lines to apply
     * @return the configured ItemStack
     */
    private ItemStack buildInvisibleMapItem(Component displayName, List<Component> lore)
    {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(displayName);
        meta.setCustomModelData(1);
        meta.lore(lore);

        item.setItemMeta(meta);
        ItemStackExtensions.setData(item, "id", "1");
        return item;
    }

    /**
     * Resolves a list of config lore strings via PlaceholderAPI and colour codes.
     *
     * @param player  the player context for placeholder resolution
     * @param rawLore the raw lore list from config
     * @return formatted Adventure components
     */
    private List<Component> buildLoreFromConfig(Player player, List<String> rawLore)
    {
        List<Component> lore = new ArrayList<>(rawLore.size());
        for (String line : rawLore) {
            lore.add(deserialize(formatMessage(player, line)));
        }
        return lore;
    }

    // -------------------------------------------------------------------------
    // Progress icons (title bar)
    // -------------------------------------------------------------------------

    /**
     * Generates a Unicode character representing the essence progress for a pickaxe profile.
     *
     * @param profile        the player's pickaxe profile
     * @param animationFrame the current animation frame (1–5)
     * @return a single Unicode character for the progress icon
     */
    public String getEssenceProgress(PickaxeProfile profile, int animationFrame)
    {
        if (profile.enchantRolls > 0) {
            return getAnimatedProgressIcon(10, animationFrame);
        }

        int percentage   = (int) Math.round((double) profile.currentEssence / profile.targetEssence * 100);
        int progressStep = Math.min(percentage / 10, 10);

        if (progressStep == 0) {
            return "↛";
        }

        return getAnimatedProgressIcon(progressStep, animationFrame);
    }

    /**
     * Maps a progress step and animation frame to a Unicode progress icon.
     *
     * @param progressStep the current progress level (1–10)
     * @param frame        the current animation frame (1–5)
     * @return the corresponding Unicode character
     */
    private String getAnimatedProgressIcon(int progressStep, int frame)
    {
        String[][] progressIcons = {
                {"①", "②", "③", "④", "⑤"},
                {"⑥", "⑦", "⑧", "⑨", "⑩"},
                {"⑪", "⑫", "⑬", "⑭", "⑮"},
                {"⑯", "⑰", "⑱", "⑲", "⑳"},
                {"㉑", "㉒", "㉓", "㉔", "㉕"},
                {"㉖", "㉗", "㉘", "㉙", "㉚"},
                {"㉛", "㉜", "㉝", "㉞", "㉟"},
                {"㊱", "㊲", "㊳", "㊴", "㊵"},
                {"㊶", "㊷", "㊸", "㊹", "㊺"},
                {"㊻", "㊼", "㊽", "㊾", "㊿"}
        };

        if (progressStep < 1 || progressStep > 10 || frame < 1 || frame > 5) {
            return "";
        }

        return progressIcons[progressStep - 1][frame - 1];
    }

    /**
     * Generates a Unicode character representing the evolution progress for a pickaxe profile.
     *
     * @param profile        the player's pickaxe profile
     * @param animationFrame the current animation frame (1–5)
     * @return a single Unicode character for the evolution progress icon
     */
    private String getEvolveProgress(PickaxeProfile profile, int animationFrame)
    {
        if (profile.evolutionRolls > 0) {
            return getAnimatedEvolveProgressIcon(10, animationFrame);
        }

        int percentage   = (int) Math.round((double) profile.evolutionAtoms / profile.atomTarget * 100);
        int progressStep = Math.min(percentage / 10, 10);

        if (progressStep == 0) {
            return "⁐";
        }

        return getAnimatedEvolveProgressIcon(progressStep, animationFrame);
    }

    /**
     * Maps a progress step and animation frame to an evolution-specific Unicode icon.
     *
     * @param progressStep the current progress level (1–10)
     * @param frame        the current animation frame (1–5)
     * @return the corresponding Unicode character
     */
    private String getAnimatedEvolveProgressIcon(int progressStep, int frame)
    {
        String[][] evolveProgressIcons = {
                {"⑾", "⑿", "⒀", "⒁", "⒂"},
                {"⒃", "⒄", "⒅", "⒆", "⒇"},
                {"⒈", "⒉", "⒊", "⒋", "⒌"},
                {"⒍", "⒎", "⒏", "⒐", "⓵"},
                {"⓶", "⓷", "⓸", "⓹", "⓺"},
                {"⓻", "⓼", "⓽", "⓾", "⓿"},
                {"❶", "❷", "❸", "❹", "❺"},
                {"❻", "❼", "❽", "❾", "❿"},
                {"➀", "➁", "➂", "➃", "➄"},
                {"➅", "➆", "➇", "➈", "➉"}
        };

        if (progressStep < 1 || progressStep > 10 || frame < 1 || frame > 5) {
            return "\uD83D\uDFA8";
        }

        return evolveProgressIcons[progressStep - 1][frame - 1];
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Deserialises a legacy colour-code string into a non-italic Adventure component.
     *
     * @param message the legacy-formatted string
     * @return the Adventure component
     */
    private Component deserialize(String message) {
        return LEGACY.deserialize(message).decoration(TextDecoration.ITALIC, false);
    }
}
