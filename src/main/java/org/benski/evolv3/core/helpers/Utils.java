package org.benski.evolv3.core.helpers;

import me.clip.placeholderapi.PlaceholderAPI;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Utils
{
    /**
     * Formats a string by applying placeholders and color codes for a specific player.
     * This method uses PlaceholderAPI to replace placeholders and MatrixColorAPI to process color codes.
     *
     * @param player  The player for whom the message is being formatted. This context is used for replacing placeholders.
     * @param message The string message to be formatted.
     * @return The fully formatted string with placeholders replaced and color codes applied.
     */
    public static String formatMessage(Player player, String message)
    {
        message = PlaceholderAPI.setPlaceholders(player, message);
        message = MatrixColorAPI.process(message);
        return message;
    }

    /**
     * Formats a list of strings by applying placeholders and color codes for a specific player.
     * It iterates through each string in the list and applies the same formatting as the single-string formatMessage method.
     *
     * @param player The player for whom the messages are being formatted. This context is used for replacing placeholders.
     * @param list   The list of strings to be formatted.
     * @return A new list containing the formatted strings. If the input list is null or empty, it returns a list with a single empty string.
     */
    public static List<String> formatMessage(Player player, List<String> list)
    {
        if (list == null || list.isEmpty()) return List.of("");
        List<String> formattedList = new ArrayList<>();
        for (String msg : list) {
            formattedList.add(formatMessage(player, msg));
        }
        return formattedList;
    }

    /**
     * Sets persistent data onto an item.
     * @param item The item to modify.
     * @param key The key to set in the item's persistent data container.
     * @param value The value associated with the key.
     */
    public static void setItemData(ItemStack item, String key, String value)
    {
        if (item == null || key == null || value == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey namespacedKey = new NamespacedKey(Main.getInstance(), key);
        container.set(namespacedKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    /**
     * Retrieves persistent data from an item using a key.
     * @param item The item to inspect.
     * @param key The key to retrieve from the item's persistent data container.
     * @return The value associated with the key, or null if not found.
     */
    public static String getItemData(ItemStack item, String key) {
        if (item == null || key == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey namespacedKey = new NamespacedKey(Main.getInstance(), key);
        return container.getOrDefault(namespacedKey, PersistentDataType.STRING, "");
    }

    /**
     * Generates a random amount between the specified minimum and maximum values, inclusive.
     * @param min The minimum value (inclusive).
     * @param max The maximum value (inclusive).
     * @return A random integer between min and max.
     */
    public static int amount(int min, int max)
    {
        if (min > max) throw new IllegalArgumentException("Min cannot be greater than Max");
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Returns true if the given percentage chance succeeds.
     * @param chancePercent The chance in percentage (e.g., 10 for 10%, 0.01 for 0.01%)
     * @return true if successful, false otherwise
     */
    public static boolean chance(double chancePercent)
    {
        if (chancePercent <= 0) return false;
        if (chancePercent >= 100) return true;

        double randomValue = ThreadLocalRandom.current().nextDouble(100.0);
        return randomValue < chancePercent;
    }

    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }

        return item;
    }
}
