package org.benski.evolv3.menus;

import lombok.Getter;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.IslandManager;
import org.benski.evolv3.core.managers.IslandManager.IslandData;
import org.benski.evolv3.core.managers.IslandWorldManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Island land expansion menu — 5×9 grid (slots 0-44) with a management footer (slots 45-53).
 *
 * <p>Grid slots map to 11×11 land parcels within the island plot.
 * Purchased parcels show as GRASS_BLOCK (or a player-skull if the player is standing there).
 * Adjacent unpurchased parcels show as LIME_STAINED_GLASS_PANE (buyable).
 * Non-adjacent parcels show as GRAY_STAINED_GLASS_PANE (locked).
 */
public class IslandMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("island_menu");
    @Getter
    private static IslandMenu instance;

    @Override public MenuType getMenuType() { return MENU_TYPE; }

    // Footer slot assignments
    private static final int SLOT_LOCK     = 46;
    private static final int SLOT_TELEPORT = 47;
    private static final int SLOT_INFO     = 49;
    private static final int SLOT_VISITS   = 52;

    public IslandMenu(JavaPlugin plugin) {
        super(plugin);
        instance = this;
    }

    @Override protected String getMenuTitle(Player player) { return "&#00FFFF&l🏝 Your Island"; }
    @Override protected int getMenuSize() { return 54; }

    // =========================================================================
    // Population
    // =========================================================================

    @Override
    protected void populateMenu(Player player, Inventory inventory) {
        IslandData island = IslandManager.getInstance().get(player.getUniqueId());
        if (island == null) { player.closeInventory(); return; }

        Set<Integer> purchased = island.purchasedSlots();
        int playerParcel = getPlayerParcel(player, island);

        // --- Grid (slots 0-44) ---
        for (int slot = 0; slot < 45; slot++) {
            if (purchased.contains(slot)) {
                inventory.setItem(slot, buildPurchasedItem(player, slot, slot == playerParcel));
            } else if (isAdjacentToPurchased(slot, purchased)) {
                inventory.setItem(slot, buildBuyableItem(slot));
            } else {
                inventory.setItem(slot, buildLockedItem());
            }
        }

        // --- Footer border ---
        ItemStack border = new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);

        // Lock toggle
        inventory.setItem(SLOT_LOCK, new ItemBuilder(island.locked() ? Material.BARRIER : Material.OAK_DOOR)
                .name(island.locked() ? "&#FF5555&l🔒 Locked" : "&#55FF55&l🔓 Unlocked")
                .lore("&#CCCCCCClick to " + (island.locked() ? "unlock" : "lock") + " your island.")
                .build());

        // Teleport home
        inventory.setItem(SLOT_TELEPORT, new ItemBuilder(Material.ENDER_PEARL)
                .name("&#00FFFF&l⚡ Teleport Home")
                .lore("&#CCCCCCTeleport to your island spawn.")
                .build());

        // Island info
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.MAP)
                .name("&#00FFFF&lIsland Info")
                .lore(
                        "&#888888Owner: &#FFFFFF" + island.ownerName(),
                        "&#888888Parcels: &#FFFFFF" + purchased.size() + " / 45",
                        "&#888888Members: &#FFFFFF" + island.members().size(),
                        "&#888888Locked: " + (island.locked() ? "&#FF5555Yes" : "&#55FF55No"),
                        "&#888888Open for visits: " + (island.openForVisits() ? "&#55FF55Yes" : "&#FF5555No")
                ).build());

        // Visits toggle
        inventory.setItem(SLOT_VISITS, new ItemBuilder(island.openForVisits() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(island.openForVisits() ? "&#55FF55&l✔ Open for Visits" : "&#888888&l✗ Closed to Visitors")
                .lore("&#CCCCCCToggle whether visitors can browse via &#FFFFFF/islands&#CCCCCC.")
                .build());
    }

    // =========================================================================
    // Click handling
    // =========================================================================

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        event.setCancelled(true);
        IslandData island = IslandManager.getInstance().get(player.getUniqueId());
        if (island == null) { player.closeInventory(); return true; }

        if (slot < 45) {
            if (!island.purchasedSlots().contains(slot) && isAdjacentToPurchased(slot, island.purchasedSlots())) {
                handleBuyParcel(player, island, slot);
            }
        } else {
            switch (slot) {
                case SLOT_TELEPORT -> handleTeleport(player, island);
                case SLOT_LOCK     -> handleLockToggle(player, island);
                case SLOT_VISITS   -> handleVisitToggle(player, island);
            }
        }
        return true;
    }

    // =========================================================================
    // Action handlers
    // =========================================================================

    private void handleTeleport(Player player, IslandData island) {
        Location loc = IslandWorldManager.getInstance().getSpawnLocation(island);
        if (loc == null) { Messageable.sendError(player, "Island world not loaded."); return; }
        player.closeInventory();
        player.teleport(loc);
        IslandWorldManager.getInstance().setPlayerBorder(player, island);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        Messageable.sendSuccess(player, "Teleported to your island.");
    }

    private void handleLockToggle(Player player, IslandData island) {
        IslandManager.getInstance().update(island.withLocked(!island.locked()));
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 1f);
        refresh(player);
    }

    private void handleVisitToggle(Player player, IslandData island) {
        IslandData updated = island.withOpenForVisits(!island.openForVisits());
        IslandManager.getInstance().update(updated);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 1.2f);
        refresh(player);
        Messageable.sendSuccess(player, "Island is now " +
                (updated.openForVisits() ? "&#55FF55open for visits" : "&#FF5555closed to visitors") + "&#00FFFF.");
    }

    private void handleBuyParcel(Player player, IslandData island, int slot) {
        PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (dc == null) return;
        PlayerProfile profile = dc.getLocal(player);
        if (profile == null) return;

        double cost = IslandManager.PARCEL_BUY_COST;
        if (profile.coins < cost) {
            Messageable.sendError(player, "Not enough coins. Need &#FFD700" + (int) cost + " coins.");
            return;
        }
        profile.removeCoins(cost);
        profile.save(null);

        Set<Integer> newPurchased = new HashSet<>(island.purchasedSlots());
        newPurchased.add(slot);
        IslandManager.getInstance().update(island.withPurchasedSlots(newPurchased));

        int col = slot % IslandManager.GRID_COLS;
        int row = slot / IslandManager.GRID_COLS;
        IslandWorldManager.getInstance().buildParcel(island, col, row);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Messageable.sendSuccess(player, "Parcel purchased! &#888888(" + newPurchased.size() + "/45)");
        refresh(player);
    }

    // =========================================================================
    // Item builders
    // =========================================================================

    private ItemStack buildPurchasedItem(Player player, int slot, boolean isPlayerHere) {
        if (isPlayerHere) {
            ItemStack skull = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&#00FFFF&lYou Are Here")
                    .lore(
                            "&#888888Parcel: &#FFFFFF" + (slot + 1),
                            "&#888888Size: &#FFFFFF" + IslandManager.PARCEL_SIZE + "x" + IslandManager.PARCEL_SIZE + " blocks",
                            "",
                            "&#CCCCCCThis is your current location."
                    ).build();
            if (skull.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()));
                skull.setItemMeta(sm);
            }
            return skull;
        }
        return new ItemBuilder(Material.GRASS_BLOCK)
                .name("&#55FF55&lPurchased Parcel")
                .lore(
                        "&#888888Parcel: &#FFFFFF" + (slot + 1),
                        "&#888888Size: &#FFFFFF" + IslandManager.PARCEL_SIZE + "x" + IslandManager.PARCEL_SIZE + " blocks"
                ).build();
    }

    private ItemStack buildBuyableItem(int slot) {
        return new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&#55FF55&l✔ Buy This Parcel")
                .lore(
                        "&#888888Parcel: &#FFFFFF" + (slot + 1),
                        "&#888888Size: &#FFFFFF" + IslandManager.PARCEL_SIZE + "x" + IslandManager.PARCEL_SIZE + " blocks",
                        "",
                        "&#888888Cost: &#FFD700" + (int) IslandManager.PARCEL_BUY_COST + " coins",
                        "&#CCCCCCClick to purchase!"
                ).build();
    }

    private ItemStack buildLockedItem() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name("&#888888&lLocked Parcel")
                .lore("&#CCCCCCExpand adjacent parcels first.")
                .build();
    }

    // =========================================================================
    // Grid helpers
    // =========================================================================

    private boolean isAdjacentToPurchased(int slot, Set<Integer> purchased) {
        int col = slot % IslandManager.GRID_COLS;
        int row = slot / IslandManager.GRID_COLS;
        if (col > 0 && purchased.contains(slot - 1)) return true;
        if (col < IslandManager.GRID_COLS - 1 && purchased.contains(slot + 1)) return true;
        if (row > 0 && purchased.contains(slot - IslandManager.GRID_COLS)) return true;
        if (row < IslandManager.GRID_ROWS - 1 && purchased.contains(slot + IslandManager.GRID_COLS)) return true;
        return false;
    }

    /** Returns the parcel slot the player is currently standing in, or -1 if not on their island. */
    private int getPlayerParcel(Player player, IslandData island) {
        World islandWorld = IslandWorldManager.getInstance().getWorld();
        if (islandWorld == null || !player.getWorld().equals(islandWorld)) return -1;
        int relX = (int)(player.getX() - island.worldX());
        int relZ = (int)(player.getZ() - island.worldZ());
        if (relX < 0 || relZ < 0) return -1;
        int col = relX / IslandManager.PARCEL_SIZE;
        int row = relZ / IslandManager.PARCEL_SIZE;
        if (col >= IslandManager.GRID_COLS || row >= IslandManager.GRID_ROWS) return -1;
        return row * IslandManager.GRID_COLS + col;
    }

    // =========================================================================
    // Lifecycle hook
    // =========================================================================

    @Override
    protected void onOpen(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.6f, 1f);
    }
}
