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
}
