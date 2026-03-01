package org.benski.evolv3.core.types;

/**
 * Defines all player preference types and their available options.
 * Simplified preference system with 3 main categories.
 */
public class PreferenceType
{
    /**
     * Animation preference - controls visual and sound effects during gameplay.
     */
    public enum Animation
    {
        ENABLED("All Effects", "Full animations with particles and sounds"),
        DISABLED("No Effects", "Disable all animation effects");

        private final String displayName;
        private final String description;

        Animation(String displayName, String description)
        {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Message preference - controls how level-up and notification messages are displayed.
     */
    public enum Message
    {
        CHAT("Chat Messages", "Receive notifications in chat"),
        TITLE("Title Messages", "Receive notifications as screen titles"),
        DISABLED("No Messages", "Disable all notification messages");

        private final String displayName;
        private final String description;

        Message(String displayName, String description)
        {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Enchant style preference - controls the visual style of enchanting animations.
     */
    public enum EnchantStyle
    {
        CLASSIC("Classic", "Traditional enchanting animation"),
        GHOST_WISPS("Ghost Wisps", "Ethereal wisp effects"),
        PORTAL_RIFT("Portal Rift", "Dimensional rift effects");

        private final String displayName;
        private final String description;

        EnchantStyle(String displayName, String description)
        {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
}
