package org.benski.evolv3.core.types;

/** Defines all player preference types and their available options. */
public class PreferenceType {
  /** Category of a pickup drop — used to drive animation colour and particle effects. */
  public enum DropCategory {
    /** Normal mined block — uses the ore-matched colour. */
    NORMAL,
    /** Essence bonus triggered — purple aura and particles. */
    ESSENCE,
    /** Atom bonus triggered — gold aura and particles. */
    ATOM
  }

  /**
   * Pickup animation style — used internally by animation classes to control visual detail (not
   * directly exposed as a user preference).
   */
  public enum PickupAnimation {
    NORMAL("Normal", "Full animation with particles"),
    SIMPLE("Simple", "Simplified particle effect"),
    TEXT_ONLY("Text Only", "Text notification only"),
    DISABLED("Disabled", "No animation");

    private final String displayName;
    private final String description;

    PickupAnimation(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    /** Returns the display name. */
    public String getDisplayName() {
      return displayName;
    }

    /** Returns the description. */
    public String getDescription() {
      return description;
    }
  }

  /**
   * Player-facing pickup effect preference. Controls which animation (if any) plays when a block is
   * mined.
   */
  public enum PickupEffect {
    DEFAULT("Default", "Block dissolves into your backpack"),
    SIMPLE("Simple", "Item fades directly to your hand"),
    DISABLED("Disabled", "Items go straight to backpack");

    private final String displayName;
    private final String description;

    PickupEffect(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    /** Returns the display name. */
    public String getDisplayName() {
      return displayName;
    }

    /** Returns the description. */
    public String getDescription() {
      return description;
    }
  }

  /** Speed tiers for the enchant selection animation. */
  public enum EnchantSpeed {
    VERY_SLOW("Very Slow"),
    SLOW("Slow"),
    NORMAL("Normal"),
    FAST("Fast"),
    VERY_FAST("Very Fast");

    public static final EnchantSpeed DEFAULT = NORMAL;

    private final String displayName;

    EnchantSpeed(String displayName) {
      this.displayName = displayName;
    }

    /** Returns the display name. */
    public String getDisplayName() {
      return displayName;
    }
  }

  /** Animation preference - controls visual and sound effects during gameplay. */
  public enum Animation {
    ENABLED("All Effects", "Full animations with particles and sounds"),
    DISABLED("No Effects", "Disable all animation effects");

    private final String displayName;
    private final String description;

    Animation(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    /** Returns the display name. */
    public String getDisplayName() {
      return displayName;
    }

    /** Returns the description. */
    public String getDescription() {
      return description;
    }
  }

  /** Message preference - controls how level-up and notification messages are displayed. */
  public enum Message {
    CHAT("Chat Messages", "Receive notifications in chat"),
    TITLE("Title Messages", "Receive notifications as screen titles"),
    DISABLED("No Messages", "Disable all notification messages");

    private final String displayName;
    private final String description;

    Message(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    /** Returns the display name. */
    public String getDisplayName() {
      return displayName;
    }

    /** Returns the description. */
    public String getDescription() {
      return description;
    }
  }

  /** Enchant style preference - controls the visual style of enchanting animations. */
  public enum EnchantStyle {
    CLASSIC("Classic", "Traditional enchanting animation"),
    GHOST_WISPS("Ghost Wisps", "Ethereal wisp effects"),
    PORTAL_RIFT("Portal Rift", "Dimensional rift effects");

    private final String displayName;
    private final String description;

    EnchantStyle(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    /** Returns the display name. */
    public String getDisplayName() {
      return displayName;
    }

    /** Returns the description. */
    public String getDescription() {
      return description;
    }
  }

  /** Evolution style preference - controls the visual style of the pickaxe evolution animation. */
  public enum EvolutionStyle {
    CRYSTAL_ASCENSION("Crystal Ascension", "Amethyst shards converge and shatter into a nova"),
    SUPERNOVA("Supernova Descent", "A gathered star falls from the sky in a cataclysmic impact");

    private final String displayName;
    private final String description;

    EvolutionStyle(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    /** Returns the display name. */
    public String getDisplayName() {
      return displayName;
    }

    /** Returns the description. */
    public String getDescription() {
      return description;
    }
  }
}
