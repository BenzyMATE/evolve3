package org.benski.evolv3.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.MenuRegistry;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-enchant preference sub-menu. Shows 5 dye toggles for one enchant.
 *
 * <pre>
 * 27-slot layout (3 rows × 9 cols):
 * Row 0: full black border
 * Row 1: [blk][EnchantIcon][Message][Title][Sound][Hologram][Particles][gray][blk]
 * Row 2: [Back][blk × 8]
 * </pre>
 */
public class EnchantOptionMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("preferences_enchant_option");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final int ENCHANT_ICON_SLOT = 10;
    private static final int MESSAGE_SLOT      = 11;
    private static final int TITLE_SLOT        = 12;
    private static final int SOUND_SLOT        = 13;
    private static final int HOLOGRAM_SLOT     = 14;
    private static final int PARTICLES_SLOT    = 15;
    private static final int BACK_SLOT         = 18;

    /** Per-player selected enchant display name. */
    private final Map<UUID, String> selectedEnchant = new HashMap<>();

    public EnchantOptionMenu(JavaPlugin plugin) {
        super(plugin);
    }

    /**
     * Opens this menu configured for a specific enchant.
     *
     * @param player      the player
     * @param enchantName the enchant display name (e.g. "Furnace")
     */
    public void openForEnchant(Player player, String enchantName) {
        selectedEnchant.put(player.getUniqueId(), enchantName);
        openMenu(player);
    }

    @Override
    public void cleanup(Player player) {
        selectedEnchant.remove(player.getUniqueId());
        super.cleanup(player);
    }

    @Override
    protected String getMenuTitle(Player player) {
        String enchantName = selectedEnchant.getOrDefault(player.getUniqueId(), "Enchant");
        return ConfigRegistry.getInstance().get(ConfigNames.MENUS)
                .getString(ConfigPaths.Menus.EnchantOptionMenu.MENU_TITLE, "&8P &7» &8E &7» &8%enchant%")
                .replace("%enchant%", enchantName);
    }

    @Override
    protected int getMenuSize() {
        return 27;
    }

    @Override
    protected void onOpen(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory) {
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack gray  = pane(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, isBorder(i) ? black : gray);
        }

        FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
        String enchantName = selectedEnchant.getOrDefault(player.getUniqueId(), "");

        // Enchant icon (non-clickable label)
        inventory.setItem(ENCHANT_ICON_SLOT, buildEnchantIcon(enchantName));

        // Back button
        inventory.setItem(BACK_SLOT, buildItem(
                mat(menus, ConfigPaths.Menus.EnchantOptionMenu.BACK_MATERIAL, Material.RED_STAINED_GLASS_PANE),
                menus.getString(ConfigPaths.Menus.EnchantOptionMenu.BACK_NAME, "&#AAAAAA← Back"),
                menus.getStringList(ConfigPaths.Menus.EnchantOptionMenu.BACK_LORE)));

        // Resolve per-enchant prefs (defaults if profile unavailable)
        PlayerProfile profile = getProfile(player);
        EnchantPreference pref = (profile != null)
                ? profile.preferences.getOrCreateEnchantPref(enchantName)
                : new EnchantPreference();

        Material enabledMat  = mat(menus, ConfigPaths.Menus.EnchantOptionMenu.ENABLED_MATERIAL,  Material.LIME_DYE);
        Material disabledMat = mat(menus, ConfigPaths.Menus.EnchantOptionMenu.DISABLED_MATERIAL, Material.RED_DYE);

        inventory.setItem(MESSAGE_SLOT,   dyeToggle(
                menus.getString(ConfigPaths.Menus.EnchantOptionMenu.MESSAGE_NAME,   "Message"),
                menus.getStringList(ConfigPaths.Menus.EnchantOptionMenu.MESSAGE_LORE),
                pref.message, enabledMat, disabledMat));

        inventory.setItem(TITLE_SLOT,     dyeToggle(
                menus.getString(ConfigPaths.Menus.EnchantOptionMenu.TITLE_NAME,     "Title"),
                menus.getStringList(ConfigPaths.Menus.EnchantOptionMenu.TITLE_LORE),
                pref.title, enabledMat, disabledMat));

        inventory.setItem(SOUND_SLOT,     dyeToggle(
                menus.getString(ConfigPaths.Menus.EnchantOptionMenu.SOUND_NAME,     "Sound"),
                menus.getStringList(ConfigPaths.Menus.EnchantOptionMenu.SOUND_LORE),
                pref.sound, enabledMat, disabledMat));

        inventory.setItem(HOLOGRAM_SLOT,  dyeToggle(
                menus.getString(ConfigPaths.Menus.EnchantOptionMenu.HOLOGRAM_NAME,  "Hologram"),
                menus.getStringList(ConfigPaths.Menus.EnchantOptionMenu.HOLOGRAM_LORE),
                pref.hologram, enabledMat, disabledMat));

        inventory.setItem(PARTICLES_SLOT, dyeToggle(
                menus.getString(ConfigPaths.Menus.EnchantOptionMenu.PARTICLES_NAME, "Particles"),
                menus.getStringList(ConfigPaths.Menus.EnchantOptionMenu.PARTICLES_LORE),
                pref.particles, enabledMat, disabledMat));
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == BACK_SLOT) {
            var back = MenuRegistry.getInstance().get("preferences_enchants");
            if (back != null) back.openMenu(player);
            return true;
        }

        String enchantName = selectedEnchant.get(player.getUniqueId());
        if (enchantName == null) return true;

        PlayerProfile profile = getProfile(player);
        if (profile == null) return true;

        EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName);
        boolean changed = false;

        if (slot == MESSAGE_SLOT)   { pref.message   = !pref.message;   changed = true; }
        if (slot == TITLE_SLOT)     { pref.title     = !pref.title;     changed = true; }
        if (slot == SOUND_SLOT)     { pref.sound     = !pref.sound;     changed = true; }
        if (slot == HOLOGRAM_SLOT)  { pref.hologram  = !pref.hologram;  changed = true; }
        if (slot == PARTICLES_SLOT) { pref.particles = !pref.particles; changed = true; }

        if (changed) {
            profile.save();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            refresh(player);
        }
        return true;
    }

    @Override
    public MenuType getMenuType() {
        return MENU_TYPE;
    }

    // -------------------------------------------------------------------------

    private ItemStack buildEnchantIcon(String enchantName) {
        Material mat = EnchantAnimationConfig.getEnchantItem(enchantName);
        EnchantOption option = EnchantAnimationConfig.getOption(enchantName);

        String rarityHex = option != null ? option.rarity().textColor().asHexString() : "#AAAAAA";
        String displayName = "&" + rarityHex + "&l" + enchantName;

        List<String> lore = new ArrayList<>();
        if (option != null) {
            lore.add(" &8| &#888888Rarity: &" + rarityHex + option.rarity().displayName());
        }
        return buildItem(mat, displayName, lore);
    }

    private PlayerProfile getProfile(Player player) {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        return pdc != null ? pdc.getLocal(player) : null;
    }

    private boolean isBorder(int slot) {
        int col = slot % 9;
        return col == 0 || col == 8 || slot < 9 || slot >= 18;
    }

    private Material mat(FileConfiguration cfg, String path, Material fallback) {
        String val = cfg.getString(path);
        if (val == null) return fallback;
        Material m = Material.getMaterial(val.toUpperCase());
        return m != null ? m : fallback;
    }

    private ItemStack dyeToggle(String name, List<String> lore, boolean enabled,
                                Material enabledMat, Material disabledMat) {
        Material mat = enabled ? enabledMat : disabledMat;
        String displayName = enabled ? "&a✔ " + name : "&c✘ " + name;
        String statusLine  = enabled ? "&a● Enabled" : "&c● Disabled";
        List<String> resolved = lore.stream()
                .map(line -> line.replace("{status}", statusLine))
                .toList();
        return buildItem(mat, displayName, resolved);
    }

    private ItemStack pane(Material mat) {
        return buildItem(mat, " ", List.of());
    }

    private ItemStack buildItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(ds(name));

        List<Component> loreComponents = new ArrayList<>(lore.size());
        for (String line : lore) {
            loreComponents.add(line.isEmpty() ? Component.empty() : ds(line));
        }
        meta.lore(loreComponents);
        item.setItemMeta(meta);
        return item;
    }

    private Component ds(String text) {
        return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }
}
