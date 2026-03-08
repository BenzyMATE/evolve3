package org.benski.evolv3.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Animations preferences sub-menu.
 *
 * <pre>
 * 54-slot layout (6 rows × 9 cols) — vertical option columns:
 *
 *       col0   col1              col2  col3              col4  col5             col6  col7              col8
 * Row0: [blk]  [⬡ Pickup Anim]  [sep] [⏱ Enchant Speed] [sep] [⚛ Atom FX]    [sep] [🔥 Essence FX]   [border]
 * Row1: [bdr]  [Default        ] [sep] [Very Slow      ]  [sep] [Sound Toggle ] [sep] [Sound Toggle  ] [border]
 * Row2: [bdr]  [Simple         ] [sep] [Slow           ]  [sep] [Particles Tog.] [sep] [Particles Tog.] [border]
 * Row3: [bdr]  [Disabled       ] [sep] [Normal         ]  [sep] [Hologram Tog.] [sep] [Hologram Tog. ] [border]
 * Row4: [bdr]  [gray           ] [sep] [Fast           ]  [sep] [Glow Toggle  ] [sep] [Glow Toggle   ] [border]
 * Row5: [Back] [blk            ] [blk] [Very Fast      ]  [blk] [blk          ] [blk] [blk           ] [blk   ]
 * </pre>
 */
public class AnimationsMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("preferences_animations");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Back button — bottom-left corner
    private static final int BACK_SLOT      = 45;
    private static final int PICKUP_LABEL   = 1;
    private static final int SPEED_LABEL    = 3;
    private static final int ATOM_LABEL     = 5;
    private static final int ESSENCE_LABEL  = 7;

    // Col 1 — Pickup animation effect (rows 1-3)
    private static final int PICKUP_DEFAULT  = 10;
    private static final int PICKUP_SIMPLE   = 19;
    private static final int PICKUP_DISABLED = 28;

    // Col 3 — Enchant speed (rows 1-5)
    private static final int[] SPEED_SLOTS = {12, 21, 30, 39, 48};

    // Col 5 — Atom FX toggles (rows 1-4)
    private static final int ATOM_SOUND     = 14;
    private static final int ATOM_PARTICLES = 23;
    private static final int ATOM_HOLOGRAM  = 32;
    private static final int ATOM_GLOW      = 41;

    // Col 7 — Essence FX toggles (rows 1-4)
    private static final int ESSENCE_SOUND     = 16;
    private static final int ESSENCE_PARTICLES = 25;
    private static final int ESSENCE_HOLOGRAM  = 34;
    private static final int ESSENCE_GLOW      = 43;

    // Separator columns
    private static final int[] SEP_COLS = {2, 4, 6};

    public AnimationsMenu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String getMenuTitle(Player player) {
        return "&8P &7» &8Animations";
    }

    @Override
    protected int getMenuSize() {
        return 54;
    }

    @Override
    protected void onOpen(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory) {
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack gray  = pane(Material.GRAY_STAINED_GLASS_PANE);

        // Fill with gray base
        for (int i = 0; i < 54; i++) inventory.setItem(i, gray);

        // Top + bottom full black rows
        for (int i = 0; i < 9; i++)  inventory.setItem(i, black);
        for (int i = 45; i < 54; i++) inventory.setItem(i, black);

        // Left + right border columns (rows 1-4)
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9,     black);
            inventory.setItem(row * 9 + 8, black);
        }

        // Separator columns (cols 2, 4, 6 — rows 1-5)
        for (int col : SEP_COLS) {
            for (int row = 1; row <= 5; row++) {
                inventory.setItem(row * 9 + col, black);
            }
        }

        FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

        // Back button (bottom-left)
        inventory.setItem(BACK_SLOT, buildItem(
                mat(menus, ConfigPaths.Menus.AnimationsMenu.BACK_MATERIAL, Material.RED_STAINED_GLASS_PANE),
                menus.getString(ConfigPaths.Menus.AnimationsMenu.BACK_NAME, "&#AAAAAA← Back"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.BACK_LORE)));

        PlayerPreferences prefs = getPrefs(player);

        // ── Col 1: Pickup Animation ──────────────────────────────────────────
        inventory.setItem(PICKUP_LABEL, buildItem(
                mat(menus, ConfigPaths.Menus.AnimationsMenu.PICKUP_LABEL_MATERIAL, Material.COMPARATOR),
                menus.getString(ConfigPaths.Menus.AnimationsMenu.PICKUP_LABEL_NAME, "&#00FFFF⬡ Pickup Animation"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.PICKUP_LABEL_LORE)
        ));
        Material pickupMat    = mat(menus, ConfigPaths.Menus.AnimationsMenu.PICKUP_MATERIAL, Material.ENDER_PEARL);
        Material pickupSelMat = mat(menus, ConfigPaths.Menus.AnimationsMenu.PICKUP_SELECTED_MATERIAL, Material.ENDER_EYE);
        PreferenceType.PickupEffect effect = prefs != null ? prefs.pickupEffect : PreferenceType.PickupEffect.DEFAULT;
        inventory.setItem(PICKUP_DEFAULT,  radioOption(
                menus.getString(ConfigPaths.Menus.AnimationsMenu.PICKUP_DEFAULT_NAME, "Default"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.PICKUP_DEFAULT_LORE),
                effect == PreferenceType.PickupEffect.DEFAULT, pickupSelMat, pickupMat));
        inventory.setItem(PICKUP_SIMPLE,   radioOption(
                menus.getString(ConfigPaths.Menus.AnimationsMenu.PICKUP_SIMPLE_NAME, "Simple"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.PICKUP_SIMPLE_LORE),
                effect == PreferenceType.PickupEffect.SIMPLE, pickupSelMat, pickupMat));
        inventory.setItem(PICKUP_DISABLED, radioOption(
                menus.getString(ConfigPaths.Menus.AnimationsMenu.PICKUP_DISABLED_NAME, "Disabled"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.PICKUP_DISABLED_LORE),
                effect == PreferenceType.PickupEffect.DISABLED, pickupSelMat, pickupMat));

        // ── Col 3: Enchant Speed ─────────────────────────────────────────────
        inventory.setItem(SPEED_LABEL, buildItem(
                mat(menus, ConfigPaths.Menus.AnimationsMenu.SPEED_LABEL_MATERIAL, Material.CLOCK),
                menus.getString(ConfigPaths.Menus.AnimationsMenu.SPEED_LABEL_NAME, "&#FFEE00⏱ Enchant Speed"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.SPEED_LABEL_LORE)
        ));
        Material speedMat    = mat(menus, ConfigPaths.Menus.AnimationsMenu.SPEED_MATERIAL, Material.ENDER_PEARL);
        Material speedSelMat = mat(menus, ConfigPaths.Menus.AnimationsMenu.SPEED_SELECTED_MATERIAL, Material.ENDER_EYE);
        List<String> speedOptionLore = menus.getStringList(ConfigPaths.Menus.AnimationsMenu.SPEED_OPTION_LORE);
        PreferenceType.EnchantSpeed currentSpeed = prefs != null ? prefs.enchantAnimationSpeed : PreferenceType.EnchantSpeed.NORMAL;
        PreferenceType.EnchantSpeed[] speeds = PreferenceType.EnchantSpeed.values();
        for (int i = 0; i < speeds.length && i < SPEED_SLOTS.length; i++) {
            inventory.setItem(SPEED_SLOTS[i], radioOption(speeds[i].getDisplayName(), speedOptionLore, currentSpeed == speeds[i], speedSelMat, speedMat));
        }

        // ── Col 5: Atom FX ───────────────────────────────────────────────────
        inventory.setItem(ATOM_LABEL, buildItem(
                mat(menus, ConfigPaths.Menus.AnimationsMenu.ATOM_LABEL_MATERIAL, Material.GOLD_NUGGET),
                menus.getString(ConfigPaths.Menus.AnimationsMenu.ATOM_LABEL_NAME, "&#FFEE00⚛ Atom FX"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ATOM_LABEL_LORE)
        ));
        Material atomOn  = mat(menus, ConfigPaths.Menus.AnimationsMenu.ATOM_ENABLED_MATERIAL, Material.LIME_DYE);
        Material atomOff = mat(menus, ConfigPaths.Menus.AnimationsMenu.ATOM_DISABLED_MATERIAL, Material.RED_DYE);
        boolean aSound = prefs == null || prefs.atomSound;
        boolean aPart  = prefs == null || prefs.atomParticles;
        boolean aHolo  = prefs == null || prefs.atomHologram;
        boolean aGlow  = prefs == null || prefs.atomGlow;
        inventory.setItem(ATOM_SOUND,     dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ATOM_SOUND_NAME, "Sound"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ATOM_SOUND_LORE), aSound, atomOn, atomOff));
        inventory.setItem(ATOM_PARTICLES, dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ATOM_PARTICLES_NAME, "Particles"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ATOM_PARTICLES_LORE), aPart, atomOn, atomOff));
        inventory.setItem(ATOM_HOLOGRAM,  dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ATOM_HOLOGRAM_NAME, "Hologram"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ATOM_HOLOGRAM_LORE), aHolo, atomOn, atomOff));
        inventory.setItem(ATOM_GLOW,      dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ATOM_GLOW_NAME, "Glow"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ATOM_GLOW_LORE), aGlow, atomOn, atomOff));

        // ── Col 7: Essence FX ────────────────────────────────────────────────
        inventory.setItem(ESSENCE_LABEL, buildItem(
                mat(menus, ConfigPaths.Menus.AnimationsMenu.ESSENCE_LABEL_MATERIAL, Material.BLAZE_POWDER),
                menus.getString(ConfigPaths.Menus.AnimationsMenu.ESSENCE_LABEL_NAME, "&#B366D9🔥 Essence FX"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ESSENCE_LABEL_LORE)
        ));
        Material essOn  = mat(menus, ConfigPaths.Menus.AnimationsMenu.ESSENCE_ENABLED_MATERIAL, Material.LIME_DYE);
        Material essOff = mat(menus, ConfigPaths.Menus.AnimationsMenu.ESSENCE_DISABLED_MATERIAL, Material.RED_DYE);
        boolean eSound = prefs == null || prefs.essenceSound;
        boolean ePart  = prefs == null || prefs.essenceParticles;
        boolean eHolo  = prefs == null || prefs.essenceHologram;
        boolean eGlow  = prefs == null || prefs.essenceGlow;
        inventory.setItem(ESSENCE_SOUND,     dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ESSENCE_SOUND_NAME, "Sound"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ESSENCE_SOUND_LORE), eSound, essOn, essOff));
        inventory.setItem(ESSENCE_PARTICLES, dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ESSENCE_PARTICLES_NAME, "Particles"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ESSENCE_PARTICLES_LORE), ePart, essOn, essOff));
        inventory.setItem(ESSENCE_HOLOGRAM,  dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ESSENCE_HOLOGRAM_NAME, "Hologram"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ESSENCE_HOLOGRAM_LORE), eHolo, essOn, essOff));
        inventory.setItem(ESSENCE_GLOW,      dyeToggle(menus.getString(ConfigPaths.Menus.AnimationsMenu.ESSENCE_GLOW_NAME, "Glow"),
                menus.getStringList(ConfigPaths.Menus.AnimationsMenu.ESSENCE_GLOW_LORE), eGlow, essOn, essOff));
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == BACK_SLOT) {
            MenuRegistry.getInstance().get("preferences").openMenu(player);
            return true;
        }

        PlayerProfile profile = getProfile(player);
        if (profile == null) return true;
        PlayerPreferences prefs = profile.preferences;

        // ── Pickup effect ──────────────────────────────────────────────────
        if (slot == PICKUP_DEFAULT)  { prefs.pickupEffect = PreferenceType.PickupEffect.DEFAULT;  save(player, profile); return true; }
        if (slot == PICKUP_SIMPLE)   { prefs.pickupEffect = PreferenceType.PickupEffect.SIMPLE;   save(player, profile); return true; }
        if (slot == PICKUP_DISABLED) { prefs.pickupEffect = PreferenceType.PickupEffect.DISABLED; save(player, profile); return true; }

        // ── Essence FX toggles ─────────────────────────────────────────────
        if (slot == ESSENCE_SOUND)     { prefs.essenceSound     = !prefs.essenceSound;     save(player, profile); return true; }
        if (slot == ESSENCE_PARTICLES) { prefs.essenceParticles = !prefs.essenceParticles; save(player, profile); return true; }
        if (slot == ESSENCE_HOLOGRAM)  { prefs.essenceHologram  = !prefs.essenceHologram;  save(player, profile); return true; }
        if (slot == ESSENCE_GLOW)      { prefs.essenceGlow      = !prefs.essenceGlow;      save(player, profile); return true; }

        // ── Atom FX toggles ────────────────────────────────────────────────
        if (slot == ATOM_SOUND)     { prefs.atomSound     = !prefs.atomSound;     save(player, profile); return true; }
        if (slot == ATOM_PARTICLES) { prefs.atomParticles = !prefs.atomParticles; save(player, profile); return true; }
        if (slot == ATOM_HOLOGRAM)  { prefs.atomHologram  = !prefs.atomHologram;  save(player, profile); return true; }
        if (slot == ATOM_GLOW)      { prefs.atomGlow      = !prefs.atomGlow;      save(player, profile); return true; }

        // ── Enchant speed ──────────────────────────────────────────────────
        PreferenceType.EnchantSpeed[] speeds = PreferenceType.EnchantSpeed.values();
        for (int i = 0; i < SPEED_SLOTS.length; i++) {
            if (slot == SPEED_SLOTS[i]) {
                prefs.enchantAnimationSpeed = speeds[i];
                save(player, profile);
                return true;
            }
        }

        return true;
    }

    @Override
    public MenuType getMenuType() {
        return MENU_TYPE;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void save(Player player, PlayerProfile profile) {
        profile.save();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        refresh(player);
    }

    private PlayerPreferences getPrefs(Player player) {
        PlayerProfile profile = getProfile(player);
        return profile != null ? profile.preferences : null;
    }

    private PlayerProfile getProfile(Player player) {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        return pdc != null ? pdc.getLocal(player) : null;
    }

    /**
     * Builds a radio-style selection button.
     * Unselected = Ender Pearl, Selected = Ender Eye with enchant glint.
     * Pass an empty string for {@code description} to omit the description line.
     */
    private Material mat(FileConfiguration cfg, String path, Material fallback) {
        String val = cfg.getString(path);
        if (val == null) return fallback;
        Material m = Material.getMaterial(val.toUpperCase());
        return m != null ? m : fallback;
    }

    /**
     * Builds a radio-style selection button.
     * Unselected = unselectedMat, Selected = selectedMat with enchant glint.
     * Use {@code {status}} in the lore list as a placeholder for the selected/unselected line.
     */
    private ItemStack radioOption(String name, List<String> lore, boolean selected,
                                  Material selectedMat, Material unselectedMat) {
        Material mat = selected ? selectedMat : unselectedMat;
        String displayName = selected ? "&#00FFFF✔ " + name : "&#AAAAAA" + name;
        String statusLine = selected ? "&#00FFFF● Selected" : " &8| &aLeft-Click &#AAAAAAto select";
        List<String> resolved = lore.stream()
                .map(line -> line.replace("{status}", statusLine))
                .toList();
        ItemStack item = buildItem(mat, displayName, resolved);
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Builds a dye-style toggle button.
     * Use {@code {status}} in the lore list as a placeholder for the enabled/disabled line.
     */
    private ItemStack dyeToggle(String name, List<String> lore, boolean enabled,
                                Material enabledMat, Material disabledMat) {
        Material mat = enabled ? enabledMat : disabledMat;
        String displayName = enabled ? "&a✔ " + name : "&c✘ " + name;
        String statusLine = enabled ? "&a● Enabled" : "&c● Disabled";
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
