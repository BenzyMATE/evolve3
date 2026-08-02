package org.benski.evolv3.models.player;

import java.util.HashMap;
import java.util.Map;
import org.benski.evolv3.core.types.PreferenceType;

/**
 * Stores player preferences. Serialized to/from the API's flat {@code List<Preference>} via {@link
 * org.benski.evolv3.api.ProfileMapper}.
 */
public class PlayerPreferences {

  public PreferenceType.Animation animation = PreferenceType.Animation.ENABLED;
  public PreferenceType.Message message = PreferenceType.Message.CHAT;
  public PreferenceType.EnchantStyle enchantStyle = PreferenceType.EnchantStyle.PORTAL_RIFT;
  public PreferenceType.EvolutionStyle evolutionStyle = PreferenceType.EvolutionStyle.GENESIS_HELIX;
  public PreferenceType.PickupEffect pickupEffect = PreferenceType.PickupEffect.DEFAULT;
  public PreferenceType.EnchantSpeed enchantAnimationSpeed = PreferenceType.EnchantSpeed.NORMAL;

  public boolean essenceSound = true;
  public boolean essenceParticles = true;
  public boolean essenceHologram = true;
  public boolean essenceGlow = true;

  public boolean atomSound = true;
  public boolean atomParticles = true;
  public boolean atomHologram = true;
  public boolean atomGlow = true;

  public boolean pickaxeMenuAnimation = false;

  public Map<String, EnchantPreference> enchantPrefs = new HashMap<>();

  /**
   * Returns the enchant preference for the given display name, creating a default one if absent.
   */
  public EnchantPreference getOrCreateEnchantPref(String enchantDisplayName) {
    return enchantPrefs.computeIfAbsent(enchantDisplayName, k -> new EnchantPreference());
  }

  /**
   * Returns the enchant preference for the given display name, creating one with the specified
   * hologram default if absent.
   */
  public EnchantPreference getOrCreateEnchantPref(
      String enchantDisplayName, boolean defaultHologram) {
    return enchantPrefs.computeIfAbsent(
        enchantDisplayName,
        k -> {
          EnchantPreference pref = new EnchantPreference();
          pref.hologram = defaultHologram;
          return pref;
        });
  }

  /**
   * Returns the enchant preference for the given display name, creating one with the specified
   * hologram and message defaults if absent.
   */
  public EnchantPreference getOrCreateEnchantPref(
      String enchantDisplayName, boolean defaultHologram, boolean defaultMessage) {
    return enchantPrefs.computeIfAbsent(
        enchantDisplayName,
        k -> {
          EnchantPreference pref = new EnchantPreference();
          pref.hologram = defaultHologram;
          pref.message = defaultMessage;
          return pref;
        });
  }

  /** Returns {@code true} if pickup/enchant animations are enabled for this player. */
  public boolean isAnimationsEnabled() {
    return animation == PreferenceType.Animation.ENABLED;
  }

  /** Returns {@code true} if enchant trigger messages are not disabled. */
  public boolean isMessagesEnabled() {
    return message != PreferenceType.Message.DISABLED;
  }

  /** Returns {@code true} if the player prefers title-style enchant messages. */
  public boolean usesTitleMessages() {
    return message == PreferenceType.Message.TITLE;
  }

  /** Returns {@code true} if the player prefers chat-style enchant messages. */
  public boolean usesChatMessages() {
    return message == PreferenceType.Message.CHAT;
  }
}
