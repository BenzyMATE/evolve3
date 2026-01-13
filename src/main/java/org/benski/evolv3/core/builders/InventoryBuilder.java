package org.benski.evolv3.core.builders;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder pattern for creating simple Inventories (GUIs) with a fluent API.
 * Use this for one-off inventories that don't need the full BaseMenu system.
 *
 * <p>Example usage:
 * <pre>{@code
 * Inventory gui = new InventoryBuilder("&6My Shop", 27)
 *     .item(10, new ItemBuilder(Material.DIAMOND).name("&bDiamonds").build())
 *     .item(13, new ItemBuilder(Material.EMERALD).name("&aEmeralds").build())
 *     .item(16, new ItemBuilder(Material.GOLD_INGOT).name("&eGold").build())
 *     .fillBorder(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build())
 *     .build();
 *
 * player.openInventory(gui);
 * }</pre>
 */
public class InventoryBuilder
{
    private final String title;
    private final int size;
    private final InventoryHolder holder;
    private final Map<Integer, ItemStack> items = new HashMap<>();

    /**
     * Creates a new InventoryBuilder with the specified title and size.
     *
     * @param title the inventory title (supports '&' color codes)
     * @param size the inventory size (must be multiple of 9, between 9 and 54)
     */
    public InventoryBuilder(String title, int size) {
        this(title, size, null);
    }

    /**
     * Creates a new InventoryBuilder with the specified title, size, and holder.
     *
     * @param title the inventory title (supports '&' color codes)
     * @param size the inventory size (must be multiple of 9, between 9 and 54)
     * @param holder the inventory holder
     */
    public InventoryBuilder(String title, int size, InventoryHolder holder) {
        this.title = colorize(title);
        this.size = size;
        this.holder = holder;

        if (size % 9 != 0 || size < 9 || size > 54) {
            throw new IllegalArgumentException("Inventory size must be a multiple of 9, between 9 and 54");
        }
    }

    /**
     * Sets an item at the specified slot.
     *
     * @param slot the slot index (0-53)
     * @param item the item to set
     * @return this builder
     */
    public InventoryBuilder item(int slot, ItemStack item) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("Slot " + slot + " is out of bounds for inventory size " + size);
        }
        items.put(slot, item);
        return this;
    }

    /**
     * Sets items in a row.
     *
     * @param row the row number (0-5)
     * @param items the items to set in the row (max 9 items)
     * @return this builder
     */
    public InventoryBuilder row(int row, ItemStack... items) {
        if (row < 0 || row >= (size / 9)) {
            throw new IllegalArgumentException("Row " + row + " is out of bounds for inventory size " + size);
        }

        int startSlot = row * 9;
        for (int i = 0; i < items.length && i < 9; i++) {
            if (items[i] != null) {
                this.items.put(startSlot + i, items[i]);
            }
        }
        return this;
    }

    /**
     * Sets items in a column.
     *
     * @param column the column number (0-8)
     * @param items the items to set in the column (max 6 items)
     * @return this builder
     */
    public InventoryBuilder column(int column, ItemStack... items) {
        if (column < 0 || column >= 9) {
            throw new IllegalArgumentException("Column must be between 0 and 8");
        }

        int rows = size / 9;
        for (int i = 0; i < items.length && i < rows; i++) {
            if (items[i] != null) {
                this.items.put((i * 9) + column, items[i]);
            }
        }
        return this;
    }

    /**
     * Fills the entire inventory with the specified item.
     *
     * @param item the item to fill with
     * @return this builder
     */
    public InventoryBuilder fill(ItemStack item) {
        for (int i = 0; i < size; i++) {
            items.put(i, item);
        }
        return this;
    }

    /**
     * Fills the border of the inventory with the specified item.
     * Border includes top row, bottom row, left column, and right column.
     *
     * @param item the item to fill the border with
     * @return this builder
     */
    public InventoryBuilder fillBorder(ItemStack item) {
        int rows = size / 9;

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            items.put(i, item); // Top row
            if (rows > 1) {
                items.put((rows - 1) * 9 + i, item); // Bottom row
            }
        }

        // Left and right columns (excluding corners already set)
        for (int i = 1; i < rows - 1; i++) {
            items.put(i * 9, item); // Left column
            items.put(i * 9 + 8, item); // Right column
        }

        return this;
    }

    /**
     * Fills empty slots with the specified item.
     * Only fills slots that don't already have an item.
     *
     * @param item the item to fill empty slots with
     * @return this builder
     */
    public InventoryBuilder fillEmpty(ItemStack item) {
        for (int i = 0; i < size; i++) {
            if (!items.containsKey(i)) {
                items.put(i, item);
            }
        }
        return this;
    }

    /**
     * Sets multiple items at once using a map of slot -> item.
     *
     * @param items the items to set
     * @return this builder
     */
    public InventoryBuilder items(Map<Integer, ItemStack> items) {
        this.items.putAll(items);
        return this;
    }

    /**
     * Clears the item at the specified slot.
     *
     * @param slot the slot to clear
     * @return this builder
     */
    public InventoryBuilder clear(int slot) {
        items.remove(slot);
        return this;
    }

    /**
     * Clears all items from the builder.
     *
     * @return this builder
     */
    public InventoryBuilder clearAll() {
        items.clear();
        return this;
    }

    /**
     * Builds and returns the final Inventory.
     *
     * @return the constructed Inventory
     */
    public Inventory build() {
        Inventory inventory = holder != null
                ? Bukkit.createInventory(holder, size, title)
                : Bukkit.createInventory(null, size, title);

        // Set all items
        items.forEach(inventory::setItem);

        return inventory;
    }

    /**
     * Utility method to colorize strings with '&' color codes.
     *
     * @param text the text to colorize
     * @return the colorized text
     */
    private String colorize(String text) {
        return text.replace('&', '§');
    }

    /**
     * Creates a new InventoryBuilder instance (alternative to constructor).
     *
     * @param title the inventory title
     * @param size the inventory size
     * @return a new InventoryBuilder
     */
    public static InventoryBuilder of(String title, int size) {
        return new InventoryBuilder(title, size);
    }

    /**
     * Creates a new InventoryBuilder instance with holder (alternative to constructor).
     *
     * @param title the inventory title
     * @param size the inventory size
     * @param holder the inventory holder
     * @return a new InventoryBuilder
     */
    public static InventoryBuilder of(String title, int size, InventoryHolder holder) {
        return new InventoryBuilder(title, size, holder);
    }
}
