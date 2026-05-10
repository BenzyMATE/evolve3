package org.benski.evolv3.menus;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.CellRentManager;
import org.benski.evolv3.core.managers.CellRentManager.CellRent;
import org.benski.evolv3.core.types.CellPermission;
import org.benski.evolv3.core.types.MenuType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-member permission editor for cell owners.
 * Shows BUILD / BREAK / CONTAINERS toggles for the selected member.
 */
public class CellMemberPermMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("cell_member_perm_menu");
    @Getter
    private static CellMemberPermMenu instance;

    @Override public MenuType getMenuType() { return MENU_TYPE; }

    private record EditContext(String cellName, UUID memberUuid) {}
    private final Map<UUID, EditContext> contexts = new ConcurrentHashMap<>();

    public CellMemberPermMenu(JavaPlugin plugin) {
        super(plugin);
        instance = this;
    }

    /** Opens the permission editor for a specific member of a specific cell. */
    public void openFor(Player owner, String cellName, UUID memberUuid) {
        contexts.put(owner.getUniqueId(), new EditContext(cellName, memberUuid));
        openMenu(owner);
    }

    @Override protected String getMenuTitle(Player player) {
        EditContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return "Permissions";
        var offline = Bukkit.getOfflinePlayer(ctx.memberUuid());
        String name = offline.getName() != null ? offline.getName() : "Member";
        return "&#00FFFF&l" + name + " &#888888Permissions";
    }

    @Override protected int getMenuSize() { return 27; }

    @Override
    protected void onClose(Player player) {
        contexts.remove(player.getUniqueId());
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory) {
        EditContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) { player.closeInventory(); return; }

        CellRent rent = CellRentManager.getInstance().getRent(ctx.cellName());
        if (rent == null || !rent.members().contains(ctx.memberUuid())) { player.closeInventory(); return; }

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inventory.setItem(i, border);

        var perms = rent.getPermissions(ctx.memberUuid());

        // BUILD — slot 10
        inventory.setItem(10, buildPermToggle(CellPermission.BUILD, perms.contains(CellPermission.BUILD)));

        // BREAK — slot 13
        inventory.setItem(13, buildPermToggle(CellPermission.BREAK, perms.contains(CellPermission.BREAK)));

        // CONTAINERS — slot 16
        inventory.setItem(16, buildPermToggle(CellPermission.CONTAINERS, perms.contains(CellPermission.CONTAINERS)));

        // Back — slot 22
        inventory.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&#CCCCCC&l← Back")
                .lore("&#CCCCCCReturn to members list.")
                .build());
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        event.setCancelled(true);
        EditContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return true;

        if (slot == 22) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(Main.getInstance(),
                    () -> CellMemberMenu.getInstance().openMenu(player), 1L);
            return true;
        }

        CellPermission perm = switch (slot) {
            case 10 -> CellPermission.BUILD;
            case 13 -> CellPermission.BREAK;
            case 16 -> CellPermission.CONTAINERS;
            default -> null;
        };
        if (perm == null) return true;

        CellRent rent = CellRentManager.getInstance().getRent(ctx.cellName());
        if (rent == null) return true;

        boolean current = rent.getPermissions(ctx.memberUuid()).contains(perm);
        CellRentManager.getInstance().setMemberPermission(ctx.cellName(), ctx.memberUuid(), perm, !current);

        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, current ? 0.8f : 1.2f);
        refresh(player);
        return true;
    }

    // =========================================================================

    private ItemStack buildPermToggle(CellPermission perm, boolean enabled) {
        String label = switch (perm) {
            case BUILD      -> "Build Blocks";
            case BREAK      -> "Break Blocks";
            case CONTAINERS -> "Open Containers";
        };
        return new ItemBuilder(enabled ? Material.LIME_DYE : Material.GRAY_DYE)
                .name((enabled ? "&#55FF55" : "&#FF5555") + "&l" + label)
                .lore(
                        "&#888888Currently: " + (enabled ? "&#55FF55Allowed" : "&#FF5555Denied"),
                        "",
                        "&#CCCCCCClick to " + (enabled ? "revoke" : "grant") + "."
                ).build();
    }

    @Override
    protected void onOpen(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.0f);
    }
}
