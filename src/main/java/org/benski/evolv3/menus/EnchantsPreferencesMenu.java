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
import org.benski.evolv3.registry.ConfigRegistry;
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
 * Paginated list of all enchants for per-enchant preference configuration.
 *
 * <pre>
 * 54-slot layout (6 rows × 9 cols):
 * Row 0: full black border
 * Rows 1-4: [border] [7 enchant slots] [border]
 * Row 5: [Back] [Prev] [blk] [blk] [PageInfo] [blk] [blk] [Next] [blk]
 * </pre>
 */
public class EnchantsPreferencesMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("preferences_enchants");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final int ITEMS_PER_PAGE = 28; // 4 rows × 7 cols
    private static final int BACK_SLOT      = 45;
    private static final int PREV_SLOT      = 46;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_SLOT      = 52;

    /** Content slots: 4 rows × 7 cols (cols 1–7). */
    private static final int[] CONTENT_SLOTS = buildContentSlots();

    /** Per-player current page (0-indexed). */
    private final Map<UUID, Integer> currentPage = new HashMap<>();

    private static int[] buildContentSlots() {
        int[] slots = new int[ITEMS_PER_PAGE];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }

    public EnchantsPreferencesMenu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String getMenuTitle(Player player) {
        return "&8P &7» &8Enchants";
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
    public void cleanup(Player player) {
        currentPage.remove(player.getUniqueId());
        super.cleanup(player);
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory) {
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack gray  = pane(Material.GRAY_STAINED_GLASS_PANE);

        // Gray base
        for (int i = 0; i < 54; i++) inventory.setItem(i, gray);

        // Top and bottom full black rows
        for (int i = 0; i < 9; i++)  inventory.setItem(i, black);
        for (int i = 45; i < 54; i++) inventory.setItem(i, black);

        // Left + right border columns (rows 1-4)
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9,     black);
            inventory.setItem(row * 9 + 8, black);
        }

        FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
        List<String> enchants = EnchantAnimationConfig.getEnchantOrder().stream()
                .filter(name -> !EnchantAnimationConfig.isPassive(name))
                .toList();

        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int totalPages = Math.max(1, (int) Math.ceil(enchants.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        currentPage.put(player.getUniqueId(), page);

        // Populate enchant items
        int startIdx = page * ITEMS_PER_PAGE;
        List<String> enchantItemLore = menus.getStringList(ConfigPaths.Menus.EnchantsMenu.ENCHANT_ITEM_LORE);
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int enchantIdx = startIdx + i;
            if (enchantIdx < enchants.size()) {
                inventory.setItem(CONTENT_SLOTS[i], buildEnchantItem(enchants.get(enchantIdx), enchantItemLore));
            } else {
                inventory.setItem(CONTENT_SLOTS[i], gray);
            }
        }

        // Back button
        inventory.setItem(BACK_SLOT, buildItem(
                mat(menus, ConfigPaths.Menus.EnchantsMenu.BACK_MATERIAL, Material.RED_STAINED_GLASS_PANE),
                menus.getString(ConfigPaths.Menus.EnchantsMenu.BACK_NAME, "&#AAAAAA← Back"),
                menus.getStringList(ConfigPaths.Menus.EnchantsMenu.BACK_LORE)));

        // Prev button
        if (page > 0) {
            inventory.setItem(PREV_SLOT, buildItem(
                    mat(menus, ConfigPaths.Menus.EnchantsMenu.PREV_MATERIAL, Material.ARROW),
                    menus.getString(ConfigPaths.Menus.EnchantsMenu.PREV_NAME, "&#AAAAAA← Previous"),
                    menus.getStringList(ConfigPaths.Menus.EnchantsMenu.PREV_LORE)));
        }

        // Next button
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, buildItem(
                    mat(menus, ConfigPaths.Menus.EnchantsMenu.NEXT_MATERIAL, Material.ARROW),
                    menus.getString(ConfigPaths.Menus.EnchantsMenu.NEXT_NAME, "&#AAAAAA→ Next"),
                    menus.getStringList(ConfigPaths.Menus.EnchantsMenu.NEXT_LORE)));
        }

        // Page info
        String pageInfoName = menus.getString(ConfigPaths.Menus.EnchantsMenu.PAGE_INFO_NAME, "&#888888Page {page} of {total}");
        pageInfoName = pageInfoName.replace("{page}", String.valueOf(page + 1))
                                   .replace("{total}", String.valueOf(totalPages));
        inventory.setItem(PAGE_INFO_SLOT, buildItem(
                mat(menus, ConfigPaths.Menus.EnchantsMenu.PAGE_INFO_MATERIAL, Material.PAPER),
                pageInfoName,
                List.of()));
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == BACK_SLOT) {
            MenuRegistry.getInstance().get("preferences").openMenu(player);
            return true;
        }

        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        List<String> enchants = EnchantAnimationConfig.getEnchantOrder().stream()
                .filter(name -> !EnchantAnimationConfig.isPassive(name))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(enchants.size() / (double) ITEMS_PER_PAGE));

        if (slot == PREV_SLOT && page > 0) {
            currentPage.put(player.getUniqueId(), page - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            refresh(player);
            return true;
        }

        if (slot == NEXT_SLOT && page < totalPages - 1) {
            currentPage.put(player.getUniqueId(), page + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            refresh(player);
            return true;
        }

        // Check if an enchant slot was clicked
        int startIdx = page * ITEMS_PER_PAGE;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (slot == CONTENT_SLOTS[i]) {
                int enchantIdx = startIdx + i;
                if (enchantIdx < enchants.size()) {
                    String enchantName = enchants.get(enchantIdx);
                    EnchantOptionMenu optMenu = (EnchantOptionMenu) MenuRegistry.getInstance().get("preferences_enchant_option");
                    optMenu.openForEnchant(player, enchantName);
                }
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

    private ItemStack buildEnchantItem(String enchantName, List<String> loreTpl) {
        Material mat = EnchantAnimationConfig.getEnchantItem(enchantName);
        EnchantOption option = EnchantAnimationConfig.getOption(enchantName);

        String rarityHex = option != null ? option.rarity().textColor().asHexString() : "#AAAAAA";
        String displayName = "&" + rarityHex + enchantName;

        List<String> lore = loreTpl.stream()
                .map(line -> {
                    if (option != null) {
                        String rarityLabel = "&" + rarityHex + option.rarity().displayName();
                        return line.replace("{rarity}", rarityLabel);
                    }
                    return line.replace("{rarity}", "&#888888Unknown");
                })
                .toList();

        return buildItem(mat, displayName, lore);
    }

    private Material mat(FileConfiguration cfg, String path, Material fallback) {
        String val = cfg.getString(path);
        if (val == null) return fallback;
        Material m = Material.getMaterial(val.toUpperCase());
        return m != null ? m : fallback;
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
