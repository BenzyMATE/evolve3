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
  public PreferenceType.EnchantStyle enchantStyle = PreferenceType.EnchantStyle.CLASSIC;
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

  public EnchantPreference getOrCreateEnchantPref(String enchantDisplayName) {
    return enchantPrefs.computeIfAbsent(enchantDisplayName, k -> new EnchantPreference());
  }

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

  public boolean isAnimationsEnabled() {
    return animation == PreferenceType.Animation.ENABLED;
  }

  public boolean isMessagesEnabled() {
    return message != PreferenceType.Message.DISABLED;
  }

  public boolean usesTitleMessages() {
    return message == PreferenceType.Message.TITLE;
  }

  public boolean usesChatMessages() {
    return message == PreferenceType.Message.CHAT;
  }
}
