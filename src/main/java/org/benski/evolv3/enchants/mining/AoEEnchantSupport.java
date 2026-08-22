package org.benski.evolv3.enchants.mining;

import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.passive.FortuneEnchant;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Shared support for the AoE mining enchants (Explosive, Cascade, Vortex, Thor, Lazer, Drill,
 * Jackhammer, Nuclear Shower): resolving the active Fortune drop bonus and reading a player's
 * per-enchant notification preferences. Every AoE enchant applies Fortune and per-enchant
 * hologram/message/sound/particle/title toggles identically; only the enchant's own config path
 * constants differ between callers.
 */
final class AoEEnchantSupport {
  private AoEEnchantSupport() {}

  /**
   * Resolved notification preferences for one AoE enchant trigger, defaulting to sound+particles
   * only (no hologram/message/title) when the player's profile isn't cached.
   */
  record EnchantPreferenceResult(
      boolean hologram, boolean message, boolean sound, boolean particles, boolean title) {}

  /** Returns the flat Fortune drop bonus for the player's pickaxe, or 0 if Fortune isn't active. */
  static int resolveFortune(Pickaxe pickaxe) {
    if (pickaxe.enchants == null) return 0;
    EnchantData fortune = pickaxe.enchants.getEnchant(Enchants.FORTUNE);
    if (fortune == null || !fortune.enabled || fortune.level <= 0) return 0;
    return FortuneEnchant.getDropAmount(fortune.level);
  }

  /**
   * Reads the player's saved hologram/message/sound/particle/title toggles for one enchant,
   * creating a default preference entry on first use.
   *
   * @param namePath config path to the enchant's display name (used as the preference key)
   * @param nameDefault fallback display name if not configured
   * @param hologramDefaultPath config path to whether the hologram defaults to enabled
   */
  static EnchantPreferenceResult getEnchantPref(
      Player player,
      FileConfiguration config,
      String namePath,
      String nameDefault,
      String hologramDefaultPath) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return new EnchantPreferenceResult(false, false, true, true, false);
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return new EnchantPreferenceResult(false, false, true, true, false);

    String enchantName = config.getString(namePath, nameDefault);
    boolean defaultHolo = config.getBoolean(hologramDefaultPath, false);
    EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

    return new EnchantPreferenceResult(
        pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
  }
}
