package org.benski.evolv3.core.constants;

/**
 * Constants for configuration file names.
 * Use these instead of hardcoded strings to prevent typos and improve refactoring.
 */
public final class ConfigNames
{
    private ConfigNames() {
        // Prevent instantiation
    }

    /**
     * Main configuration file containing database settings and general plugin configuration.
     */
    public static final String DEFAULT = "default";

    /**
     * Messages configuration file containing all player-facing messages.
     */
    public static final String MESSAGES = "messages";

    /**
     * Menus configuration file containing menu layouts and item lore.
     */
    public static final String MENUS = "menus";

    /**
     * Enchantments configuration file containing enchant settings and recipes.
     */
    public static final String ENCHANTS = "enchants";

    /**
     * Ranks configuration file containing prison and staff rank definitions.
     */
    public static final String RANKS = "ranks";
}
