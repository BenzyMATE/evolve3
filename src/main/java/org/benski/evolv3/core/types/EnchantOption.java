package org.benski.evolv3.core.types;

/**
 * An immutable enchant option consisting of a name and its rarity tier.
 *
 * <p>Instances are created by {@link
 * org.benski.evolv3.core.managers.EnchantAnimationConfig#generateRandom(int)} using data loaded
 * from {@code enchants.yml}.
 *
 * @param name the enchant display name (e.g. "Haste II")
 * @param rarity the rarity tier this enchant belongs to
 */
public record EnchantOption(String name, RarityConfig rarity) {
  /**
   * Returns a formatted display string: {@code "Rarity · Enchant Name"}.
   *
   * @return the display string
   */
  public String displayString() {
    return rarity.displayName() + " · " + name;
  }
}
