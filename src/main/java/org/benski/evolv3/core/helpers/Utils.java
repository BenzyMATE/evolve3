package org.benski.evolv3.core.helpers;

import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Utils
{
    private static final boolean PLACEHOLDER_API_ENABLED = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    /**
     * Inner class to isolate PlaceholderAPI dependency.
     * This class is only loaded when PlaceholderAPI is confirmed to be available,
     * preventing NoClassDefFoundError when PlaceholderAPI is not installed.
     */
    private static final class PlaceholderHook {
        static String setPlaceholders(Player player, String message) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, message);
        }
    }

    /**
     * Formats a string by applying placeholders and color codes for a specific player.
     * This method uses PlaceholderAPI to replace placeholders (if available) and MatrixColorAPI to process color codes.
     *
     * @param player  The player for whom the message is being formatted. This context is used for replacing placeholders.
     * @param message The string message to be formatted.
     * @return The fully formatted string with placeholders replaced and color codes applied.
     */
    public static String formatMessage(Player player, String message)
    {
        message = FontUtils.toSmallCaps(message);
        if (PLACEHOLDER_API_ENABLED) {
            message = PlaceholderHook.setPlaceholders(player, message);
        }
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

    /**
     * Generates a progress bar string for visual display using config symbols.
     *
     * @param current The current value
     * @param max     The maximum value
     * @return A progress bar string (e.g., "████████░░")
     */
    public static String generateProgressBar(int current, int max)
    {
        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.DEFAULT);
        String filledChar = config.getString(ConfigPaths.Default.Pickaxe.PROGRESSBAR_SYMBOL_FILLED, "█");
        String emptyChar = config.getString(ConfigPaths.Default.Pickaxe.PROGRESSBAR_SYMBOL_EMPTY, "░");
        return generateProgressBar(current, max, 10, filledChar, emptyChar);
    }

    /**
     * Generates a customizable progress bar string.
     *
     * @param current    The current value
     * @param max        The maximum value
     * @param length     The total length of the progress bar
     * @param filledChar The character for filled portions
     * @param emptyChar  The character for empty portions
     * @return A progress bar string
     */
    public static String generateProgressBar(int current, int max, int length, String filledChar, String emptyChar)
    {
        if (max <= 0) {
            return emptyChar.repeat(length);
        }

        double progress = Math.min(1.0, (double) current / max);
        int filledLength = (int) Math.round(progress * length);
        int emptyLength = length - filledLength;

        return filledChar.repeat(filledLength) + emptyChar.repeat(emptyLength);
    }

    /**
     * Safely parses a string into an enum value, returning a default if the string is null or invalid.
     *
     * @param value        the string value to parse
     * @param enumClass    the enum class to parse into
     * @param defaultValue the default value to return on failure
     * @param <T>          the enum type
     * @return the parsed enum value, or defaultValue if parsing fails
     */
    public static <T extends Enum<T>> T safeEnumParse(String value, Class<T> enumClass, T defaultValue)
    {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * Formats a material name into a human-readable title case string.
     * For example, {@code DARK_OAK_PLANKS} becomes {@code "Dark Oak Planks"}.
     *
     * @param material the material to format
     * @return the formatted material name
     */
    public static String formatMaterialName(Material material)
    {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return formatted.toString().trim();
    }
}
