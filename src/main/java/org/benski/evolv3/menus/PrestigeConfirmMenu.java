package org.benski.evolv3.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.animations.PrestigeAnimation;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.PrestigeManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation GUI opened when a player shift+right-clicks a prestige-eligible pickaxe.
 *
 * <pre>
 * 27-slot layout:
 * Row 0: [border × 9]
 * Row 1: [border][CONFIRM][border][INFO][border][CANCEL][border × 3]
 * Row 2: [border × 9]
 * </pre>
 *
 * <p>All item names and lore are driven from {@code menus.yml} under
 * {@code Prestige Confirm Menu}. Supported tokens:
 * {@code {next_prestige}}, {@code {current_prestige}}, {@code {required_level}},
 * {@code {new_cap}}, {@code {mult}}, {@code {count}}, {@code {plural}}.</p>
 */
public class PrestigeConfirmMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("prestige_confirm");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_INFO    = 13;
    private static final int SLOT_CANCEL  = 15;

    public PrestigeConfirmMenu(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public MenuType getMenuType() { return MENU_TYPE; }

    @Override
    protected String getMenuTitle(Player player) {
        FileConfiguration msgs = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        return msgs.getString(ConfigPaths.Messages.Pickaxe.PRESTIGE_CONFIRM_TITLE, "&8Confirm Prestige");
    }

    @Override
    protected int getMenuSize() { return 27; }

    @Override
    protected void populateMenu(Player player, Inventory inv) {
        ItemStack border = borderPane();
        for (int i = 0; i < 27; i++) {
            if (i != SLOT_CONFIRM && i != SLOT_INFO && i != SLOT_CANCEL) {
                inv.setItem(i, border);
            }
        }

        PickaxeProfile profile  = PrestigeManager.getInstance().getLocalProfile(player);
        int  currentPrestige    = profile != null ? profile.prestige : 0;
        int  nextPrestige       = currentPrestige + 1;
        int  currentCap         = PickaxeManager.getInstance().getLevelCap(currentPrestige);
        int  newCap             = PickaxeManager.getInstance().getLevelCap(nextPrestige);
        double mult             = PickaxeManager.getInstance().getPrestigeMultiplier(nextPrestige);
        boolean isLastPrestige  = nextPrestige >= PickaxeManager.getInstance().getMaxPrestige();
        String newCapStr        = newCap > 0 ? "Level " + newCap : "&#AAFFAA&lNone";
        int socketedCount       = PrestigeManager.getInstance().countSocketedStones(profile);

        FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

        inv.setItem(SLOT_CONFIRM, buildConfirm(player, menus,
                nextPrestige, currentPrestige, currentCap, newCapStr, mult, socketedCount));
        inv.setItem(SLOT_INFO,    buildInfo(player, menus,
                nextPrestige, currentPrestige, currentCap, newCapStr, mult, isLastPrestige));
        inv.setItem(SLOT_CANCEL,  buildCancel(player, menus));
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private ItemStack buildConfirm(Player player, FileConfiguration menus,
                                   int nextPrestige, int currentPrestige,
                                   int currentCap, String newCapStr,
                                   double mult, int socketedCount) {
        String rawName = menus.getString(ConfigPaths.Menus.PrestigeConfirm.CONFIRM_NAME,
                "&#55FF55&l✔ &lConfirm Prestige");
        List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PrestigeConfirm.CONFIRM_LORE);

        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(lc(applyTokens(rawName, nextPrestige, currentPrestige, currentCap, newCapStr, mult)));
        List<Component> lore = buildLore(rawLore, nextPrestige, currentPrestige, currentCap, newCapStr, mult);

        // Append socket warning lines when socketed stones will be returned
        if (socketedCount > 0) {
            List<String> warnLines = menus.getStringList(ConfigPaths.Menus.PrestigeConfirm.SOCKET_WARNING);
            if (warnLines.isEmpty()) {
                lore.add(Component.empty());
                lore.add(lc(" &#FFAA00⚠ &#FFFFFF" + socketedCount + " &#888888socketed stone"
                        + (socketedCount == 1 ? "" : "s") + " will be returned"));
                lore.add(lc("   &#888888to your inventory."));
            } else {
                lore.add(Component.empty());
                String plural = socketedCount == 1 ? "" : "s";
                for (String line : warnLines) {
                    lore.add(lc(line.replace("{count}", String.valueOf(socketedCount))
                                    .replace("{plural}", plural)));
                }
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfo(Player player, FileConfiguration menus,
                                int nextPrestige, int currentPrestige,
                                int currentCap, String newCapStr,
                                double mult, boolean isLastPrestige) {
        String rawName = menus.getString(ConfigPaths.Menus.PrestigeConfirm.INFO_NAME,
                "&#FFD700✪ &lPrestige {next_prestige}");
        List<String> rawLore = menus.getStringList(ConfigPaths.Menus.PrestigeConfirm.INFO_LORE);

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(lc(applyTokens(rawName, nextPrestige, currentPrestige, currentCap, newCapStr, mult)));
        List<Component> lore = buildLore(rawLore, nextPrestige, currentPrestige, currentCap, newCapStr, mult);

        if (isLastPrestige) {
            String finalLine = menus.getString(ConfigPaths.Menus.PrestigeConfirm.INFO_FINAL_LINE,
                    " &#FFD700&lFinal prestige tier.");
            lore.add(Component.empty());
            lore.add(lc(finalLine));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCancel(Player player, FileConfiguration menus) {
        String rawName = menus.getString(ConfigPaths.Menus.PrestigeConfirm.CANCEL_NAME,
                "&#FF4444&l✖ Cancel");
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(lc(rawName));
        meta.lore(new ArrayList<>());
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Component> buildLore(List<String> raw,
                                      int nextPrestige, int currentPrestige,
                                      int currentCap, String newCapStr, double mult) {
        List<Component> lore = new ArrayList<>(raw.size());
        for (String line : raw) {
            if (line.isEmpty()) {
                lore.add(Component.empty());
            } else {
                lore.add(lc(applyTokens(line, nextPrestige, currentPrestige, currentCap, newCapStr, mult)));
            }
        }
        return lore;
    }

    private static String applyTokens(String s, int nextPrestige, int currentPrestige,
                                      int currentCap, String newCapStr, double mult) {
        return s.replace("{next_prestige}",    String.valueOf(nextPrestige))
                .replace("{current_prestige}", String.valueOf(currentPrestige))
                .replace("{required_level}",   String.valueOf(currentCap))
                .replace("{new_cap}",          newCapStr)
                .replace("{mult}",             String.format("%.1f", mult));
    }

    private static ItemStack borderPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    private static Component lc(String text) {
        return LEGACY.deserialize(MatrixColorAPI.process(FontUtils.toSmallCaps(text)))
                .decoration(TextDecoration.ITALIC, false);
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == SLOT_CANCEL) {
            closeMenu(player);
            return true;
        }

        if (slot == SLOT_CONFIRM) {
            PickaxeProfile profile = PrestigeManager.getInstance().getLocalProfile(player);
            if (profile == null) {
                String err = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES)
                        .getString(ConfigPaths.Messages.Pickaxe.PRESTIGE_ERROR,
                                "%evolv3_prefix% &cFailed to process prestige. Please try again.");
                player.sendMessage(Utils.formatMessage(player, err));
                closeMenu(player);
                return true;
            }

            if (!PrestigeManager.getInstance().canPrestige(profile)) {
                String msg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES)
                        .getString(ConfigPaths.Messages.Pickaxe.PRESTIGE_NOT_ELIGIBLE,
                                "%evolv3_prefix% &#CCCCCCYour pickaxe must reach the required level to prestige.");
                player.sendMessage(Utils.formatMessage(player, msg));
                closeMenu(player);
                return true;
            }

            List<PrestigeManager.StoneRoll> stones = PrestigeManager.getInstance().rollPrestigeStones(3);
            closeMenu(player);

            int nextPrestige = profile.prestige + 1;
            PrestigeAnimation.play(player, nextPrestige, () -> {
                if (!player.isOnline()) return;
                PrestigeStoneSelectMenu stoneMenu = (PrestigeStoneSelectMenu)
                        MenuRegistry.getInstance().get("prestige_stone_select");
                if (stoneMenu != null) stoneMenu.openForStones(player, stones);
            });

            return true;
        }

        return true;
    }
}
