package org.benski.evolv3.models.player;

/**
 * Per-enchant notification and visual effect preferences. Stored as map entries within {@link
 * PlayerPreferences#enchantPrefs}.
 */
public class EnchantPreference {
  public boolean message = false;
  public boolean title = false;
  public boolean sound = true;
  public boolean hologram = true;
  public boolean particles = true;
  public boolean glow = true;
}
