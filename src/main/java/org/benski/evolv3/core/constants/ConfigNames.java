package org.benski.evolv3.core.constants;

/**
 * Constants for configuration file names. Use these instead of hardcoded strings to prevent typos
 * and improve refactoring.
 */
public final class ConfigNames {
  private ConfigNames() {}

  /** Main configuration file containing database settings and general plugin configuration. */
  public static final String DEFAULT = "default";

  /** Messages configuration file containing all player-facing messages. */
  public static final String MESSAGES = "messages";

  /** Menus configuration file containing menu layouts and item lore. */
  public static final String MENUS = "menus";

  /** Enchantments configuration file containing enchant settings and recipes. */
  public static final String ENCHANTS = "enchants";

  /** Ranks configuration file containing prison and staff rank definitions. */
  public static final String RANKS = "ranks";

  /** Evolution Stones configuration file containing stone item and socket menu strings. */
  public static final String STONES = "stones";

  /** Prison zone borders — stored in {@code localdata/borders.yml}. */
  public static final String BORDERS = "localdata/borders";

  /** Levitation beams — stored in {@code localdata/beams.yml}. */
  public static final String BEAMS = "localdata/beams";

  /** Launch pads — stored in {@code localdata/launchpads.yml}. */
  public static final String LAUNCHPADS = "localdata/launchpads";

  /** Mines configuration file. */
  public static final String MINES = "mines";

  /** Spawners configuration file containing spawner defaults and hologram settings. */
  public static final String SPAWNERS = "spawners";

  /** Scrolls configuration file containing scroll display names and lore. */
  public static final String SCROLLS = "scrolls";
}
