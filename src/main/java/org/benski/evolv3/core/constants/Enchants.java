package org.benski.evolv3.core.constants;

/**
 * Internal enchant key constants used for database lookups and pickaxe profile access.
 *
 * <p>These lowercase strings match the {@code name} field stored in {@link
 * org.benski.evolv3.models.pickaxe.EnchantData} documents. Use these constants instead of raw
 * string literals to prevent typos and make refactoring straightforward.
 *
 * <p>For config section lookups (e.g. {@code "Frostbite.Radius"}) use the path constants defined in
 * {@link ConfigPaths.Enchants}.
 */
public final class Enchants {
  private Enchants() {}

  /** Internal key for the Efficiency enchantment. */
  public static final String EFFICIENCY = "efficiency";

  /** Internal key for the Frostbite enchantment. */
  public static final String FROSTBITE = "frostbite";

  /** Internal key for the Furnace enchantment. */
  public static final String FURNACE = "furnace";

  /** Internal key for the Alchemy enchantment. */
  public static final String ALCHEMY = "alchemy";

  /** Internal key for the Unbreaking enchantment. */
  public static final String UNBREAKING = "unbreaking";

  /** Internal key for the Explosive enchantment. */
  public static final String EXPLOSIVE = "explosive";

  /** Internal key for the Scavenger enchantment. */
  public static final String SCAVENGER = "scavenger";

  /** Internal key for the Merchant enchantment. */
  public static final String MERCHANT = "merchant";

  /** Internal key for the Extraction enchantment. */
  public static final String EXTRACTION = "extraction";

  /** Internal key for the Fortune enchantment. */
  public static final String FORTUNE = "fortune";

  /** Internal key for the Radioactive enchantment. */
  public static final String RADIOACTIVE = "radioactive";

  /** Internal key for the AutoSell enchantment. */
  public static final String AUTO_SELL = "autosell";

  /** Internal key for the Drill enchantment. */
  public static final String DRILL = "drill";

  /** Internal key for the Lazer enchantment. */
  public static final String LAZER = "lazer";

  /** Internal key for the Thor enchantment. */
  public static final String THOR = "thor";

  /** Internal key for the Vortex enchantment. */
  public static final String VORTEX = "vortex";

  /** Internal key for the Cascade enchantment. */
  public static final String CASCADE = "cascade";

  /** Internal key for the Jackhammer enchantment. */
  public static final String JACKHAMMER = "jackhammer";

  /** Internal key for the Nuclear Shower enchantment. */
  public static final String NUCLEAR_SHOWER = "nuclearshower";
}
