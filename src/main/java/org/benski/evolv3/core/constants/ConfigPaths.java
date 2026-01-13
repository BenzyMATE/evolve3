package org.benski.evolv3.core.constants;

/**
 * Constants for configuration file paths.
 * Use these instead of hardcoded strings to prevent typos and improve refactoring.
 * Organized by configuration file for easy navigation.
 */
public final class ConfigPaths
{
    private ConfigPaths() {
        // Prevent instantiation
    }

    /**
     * Paths for the default configuration file.
     */
    public static final class Default
    {
        private Default() {}

        /**
         * MongoDB connection string path.
         * Example: "mongodb+srv://user:pass@cluster.mongodb.net/"
         */
        public static final String DATABASE_MONGODB = "Database.MongoDB";

        /**
         * Schedule interval configuration paths.
         * Intervals are in ticks (20 ticks = 1 second).
         */
        public static final class Schedules
        {
            private Schedules() {}

            /**
             * Pickaxe check schedule interval in ticks.
             * Default: 100 ticks (5 seconds)
             */
            public static final String PICKAXE_CHECK_INTERVAL = "Schedules.PickaxeCheck.IntervalTicks";
        }
    }

    /**
     * Paths for the messages configuration file.
     * Organized by category for easy navigation.
     */
    public static final class Messages
    {
        private Messages() {}

        /**
         * Pickaxe-related messages.
         */
        public static final class Pickaxe
        {
            private Pickaxe() {}

            /**
             * Message shown when loading a pickaxe profile.
             * Default: "&aLoading your pickaxe profile..."
             */
            public static final String LOADING_PROFILE = "pickaxe.loading-profile";

            /**
             * Message shown when a pickaxe profile is created.
             * Default: "&aPickaxe profile created!"
             */
            public static final String PROFILE_CREATED = "pickaxe.profile-created";

            /**
             * Message shown when a pickaxe profile is saved.
             * Default: "&aPickaxe profile saved!"
             */
            public static final String PROFILE_SAVED = "pickaxe.profile-saved";

            /**
             * Message shown when player is not holding a pickaxe.
             * Default: "&cYou must be holding a pickaxe!"
             */
            public static final String NO_PICKAXE = "pickaxe.no-pickaxe";
        }

        /**
         * General plugin messages.
         */
        public static final class General
        {
            private General() {}

            /**
             * Plugin message prefix.
             * Default: "&8[&6Evolv3&8]&r "
             */
            public static final String PREFIX = "general.prefix";

            /**
             * Message shown when player lacks permission.
             * Default: "&cYou don't have permission to do that!"
             */
            public static final String NO_PERMISSION = "general.no-permission";

            /**
             * Message shown when console tries to run player-only command.
             * Default: "&cThis command can only be used by players!"
             */
            public static final String PLAYER_ONLY = "general.player-only";

            /**
             * Message shown for unknown commands.
             * Default: "&cUnknown command. Use /help for a list of commands."
             */
            public static final String UNKNOWN_COMMAND = "general.unknown-command";

            /**
             * Message shown when configuration is reloaded successfully.
             * Default: "&aConfiguration reloaded successfully!"
             */
            public static final String RELOAD_SUCCESS = "general.reload-success";

            /**
             * Message shown when configuration reload fails.
             * Default: "&cFailed to reload configuration. Check console for errors."
             */
            public static final String RELOAD_FAILED = "general.reload-failed";
        }

        /**
         * Error messages.
         */
        public static final class Errors
        {
            private Errors() {}

            /**
             * Message shown when a database error occurs.
             * Default: "&cA database error occurred. Please contact an administrator."
             */
            public static final String DATABASE_ERROR = "errors.database-error";

            /**
             * Message shown for internal plugin errors.
             * Default: "&cAn internal error occurred. Please try again later."
             */
            public static final String INTERNAL_ERROR = "errors.internal-error";

            /**
             * Message shown when command arguments are invalid.
             * Default: "&cInvalid arguments. Usage: %usage%"
             * Supports %usage% placeholder for command usage.
             */
            public static final String INVALID_ARGUMENTS = "errors.invalid-arguments";
        }
    }
}
