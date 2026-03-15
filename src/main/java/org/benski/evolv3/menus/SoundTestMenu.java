package org.benski.evolv3.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Temporary sound testing GUI.
 * Paginates all Bukkit Sound enum values — click any to play it.
 * Remove this class and SoundCommand when no longer needed.
 */
public class SoundTestMenu implements InventoryHolder
{
    private static final Sound[] ALL_SOUNDS = Sound.values();
    public  static final int PAGE_SIZE = 45;

    private final Inventory inventory;
    private final int page;

    public SoundTestMenu(int page) {
        this.page = page;
        int totalPages = getTotalPages();
        Component title = Component.text("Sounds ")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text("(Page " + (page + 1) + "/" + totalPages + ")")
                        .color(NamedTextColor.GRAY));
        this.inventory = Bukkit.createInventory(this, 54, title);
        populate();
    }

    private void populate() {
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, ALL_SOUNDS.length);

        for (int i = start; i < end; i++) {
            Sound sound = ALL_SOUNDS[i];
            ItemStack item = new ItemStack(Material.NOTE_BLOCK);
            ItemMeta  meta = item.getItemMeta();
            meta.displayName(Component.text(sound.name())
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            inventory.setItem(i - start, item);
        }

        // Fill bottom bar with glass panes
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta  paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.empty());
        pane.setItemMeta(paneMeta);
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, pane);
        }

        int totalPages = getTotalPages();

        if (page > 0) {
            inventory.setItem(45, navButton(Material.ARROW, "◀ Previous", NamedTextColor.GREEN));
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta  infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Page " + (page + 1) + " / " + totalPages)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        if (page < totalPages - 1) {
            inventory.setItem(53, navButton(Material.ARROW, "Next ▶", NamedTextColor.GREEN));
        }
    }

    private static ItemStack navButton(Material material, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(label).color(color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the Sound at the given page + slot, or null if out of range. */
    public static Sound getSoundAt(int page, int slot) {
        int index = page * PAGE_SIZE + slot;
        if (index < 0 || index >= ALL_SOUNDS.length) return null;
        return ALL_SOUNDS[index];
    }

    public static int getTotalPages() {
        return (int) Math.ceil((double) ALL_SOUNDS.length / PAGE_SIZE);
    }

    public int getPage() { return page; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
