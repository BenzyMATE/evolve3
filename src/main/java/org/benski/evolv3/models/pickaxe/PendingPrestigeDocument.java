package org.benski.evolv3.models.pickaxe;

import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.pickaxe.PrestigeManager;
import org.benski.evolv3.core.types.RarityConfig;

/**
 * Serialization helpers for pending prestige state. Stone rolls are encoded as {@code
 * "rarityName|enchantName"} strings so they fit in the shared {@link
 * org.benski.evolv3.api.dto.PendingDataDto#optionNames} list.
 */
public final class PendingPrestigeDocument {

  private PendingPrestigeDocument() {}

  /**
   * Encodes a {@link PrestigeManager.StoneRoll} as a single string suitable for storage in {@link
   * org.benski.evolv3.api.dto.PendingDataDto#optionNames}.
   *
   * @param roll the roll to encode
   * @return a {@code "rarityName|enchantName"} string
   */
  public static String encodeRoll(PrestigeManager.StoneRoll roll) {
    return roll.rarity().displayName() + "|" + roll.enchantName();
  }

  /**
   * Decodes a previously encoded stone roll. Returns {@code null} if the string is malformed or the
   * rarity name is not found in the current config.
   *
   * @param encoded the {@code "rarityName|enchantName"} string
   * @return the decoded roll, or {@code null}
   */
  public static PrestigeManager.StoneRoll decodeRoll(String encoded) {
    if (encoded == null) return null;
    int sep = encoded.indexOf('|');
    if (sep < 0) return null;
    String rarityName = encoded.substring(0, sep);
    String enchantName = encoded.substring(sep + 1);
    RarityConfig rarity = EnchantAnimationConfig.getRarityByName(rarityName);
    if (rarity == null) return null;
    return new PrestigeManager.StoneRoll(rarity, enchantName);
  }
}
