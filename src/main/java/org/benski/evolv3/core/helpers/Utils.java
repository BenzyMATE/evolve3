package org.benski.evolv3.core.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.format.NamedTextColor;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class Utils {
  private static final boolean PLACEHOLDER_API_ENABLED =
      Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

  /** Cached progress-bar symbols — set once after config loads, never change at runtime. */
  private static String PROGRESSBAR_FILLED = "█";

  private static String PROGRESSBAR_EMPTY = "░";

  /**
   * Caches the progress-bar symbols from config so they are not re-read on every block break. Call
   * this once after {@link org.benski.evolv3.registry.ConfigRegistry} has been initialised.
   */
  public static void loadProgressBarSymbols() {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    PROGRESSBAR_FILLED =
        config.getString(ConfigPaths.Default.Pickaxe.PROGRESSBAR_SYMBOL_FILLED, "█");
    PROGRESSBAR_EMPTY = config.getString(ConfigPaths.Default.Pickaxe.PROGRESSBAR_SYMBOL_EMPTY, "░");
  }

  /**
   * Inner class to isolate PlaceholderAPI dependency. This class is only loaded when PlaceholderAPI
   * is confirmed to be available, preventing NoClassDefFoundError when PlaceholderAPI is not
   * installed.
   */
  private static final class PlaceholderHook {
    static String setPlaceholders(Player player, String message) {
      return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, message);
    }
  }

  /**
   * Formats a string by applying placeholders and color codes for a specific player. This method
   * uses PlaceholderAPI to replace placeholders (if available) and MatrixColorAPI to process color
   * codes.
   *
   * @param player The player for whom the message is being formatted. This context is used for
   *     replacing placeholders.
   * @param message The string message to be formatted.
   * @return The fully formatted string with placeholders replaced and color codes applied.
   */
  public static String formatMessage(Player player, String message) {
    if (player != null && message.contains("%evolv3_tool_label%")) {
      message = message.replace("%evolv3_tool_label%", resolveToolLabel(player));
    }
    if (PLACEHOLDER_API_ENABLED) {
      message = PlaceholderHook.setPlaceholders(player, message);
    }
    message = FontUtils.toSmallCaps(message);
    message = MatrixColorAPI.process(message);
    return message;
  }

  /** Resolves tool label. */
  private static String resolveToolLabel(Player player) {
    OmnitoolManager omni = OmnitoolManager.getInstance();
    if (!omni.isInOmnitoolMode(player)) return "Pickaxe";
    OmnitoolManager.Form form = omni.getForm(player);
    if (form == OmnitoolManager.Form.AXE) return "Axe";
    if (form == OmnitoolManager.Form.SWORD) return "Sword";
    return "Pickaxe";
  }

  /**
   * Formats a list of strings by applying placeholders and color codes for a specific player. It
   * iterates through each string in the list and applies the same formatting as the single-string
   * formatMessage method.
   *
   * @param player The player for whom the messages are being formatted. This context is used for
   *     replacing placeholders.
   * @param list The list of strings to be formatted.
   * @return A new list containing the formatted strings. If the input list is null or empty, it
   *     returns a list with a single empty string.
   */
  public static List<String> formatMessage(Player player, List<String> list) {
    if (list == null || list.isEmpty()) return List.of("");
    List<String> formattedList = new ArrayList<>();
    for (String msg : list) {
      formattedList.add(formatMessage(player, msg));
    }
    return formattedList;
  }

  /**
   * Generates a random amount between the specified minimum and maximum values, inclusive.
   *
   * @param min The minimum value (inclusive).
   * @param max The maximum value (inclusive).
   * @return A random integer between min and max.
   */
  public static int amount(int min, int max) {
    if (min > max) throw new IllegalArgumentException("Min cannot be greater than Max");
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  /**
   * Formats a number with K/M/B suffixes and 2 decimal places. e.g. 5 → "5.00", 1500 → "1.50K",
   * 2500000 → "2.50M"
   */
  public static String formatNumber(double amount) {
    if (amount >= 1_000_000_000) return String.format("%.2fB", amount / 1_000_000_000);
    if (amount >= 1_000_000) return String.format("%.2fM", amount / 1_000_000);
    if (amount >= 1_000) return String.format("%.2fK", amount / 1_000);
    return String.format("%.2f", amount);
  }

  /**
   * Formats an integer with K/M/B suffixes and no decimal places. e.g. 500 → "500", 1500 → "1K",
   * 2500000 → "2M"
   */
  public static String formatInt(long amount) {
    if (amount >= 1_000_000_000) return String.format("%dB", amount / 1_000_000_000);
    if (amount >= 1_000_000) return String.format("%dM", amount / 1_000_000);
    if (amount >= 1_000) return String.format("%dK", amount / 1_000);
    return String.valueOf(amount);
  }

  /**
   * Returns {@code true} if the given {@link Material} is any type of pickaxe.
   *
   * <p>Uses an exhaustive enum check rather than a string contains so it is safe against future
   * Material additions whose names may contain "PICKAXE" for unrelated reasons.
   *
   * @param material the material to test
   * @return true if the material is a pickaxe
   */
  public static boolean isPickaxe(Material material) {
    return material == Material.WOODEN_PICKAXE
        || material == Material.STONE_PICKAXE
        || material == Material.IRON_PICKAXE
        || material == Material.COPPER_PICKAXE
        || material == Material.DIAMOND_PICKAXE
        || material == Material.NETHERITE_PICKAXE;
  }

  /**
   * Returns true if the given percentage chance succeeds.
   *
   * @param chancePercent The chance in percentage (e.g., 10 for 10%, 0.01 for 0.01%)
   * @return true if successful, false otherwise
   */
  public static boolean chance(double chancePercent) {
    if (chancePercent <= 0) return false;
    if (chancePercent >= 100) return true;

    double randomValue = ThreadLocalRandom.current().nextDouble(100.0);
    return randomValue < chancePercent;
  }

  /**
   * Builds a colour-coded 8-segment durability bar. Green above 60%, orange 30-60%, red below 30%.
   *
   * @param damage the current item damage value
   * @param maxDurability the maximum durability of the item
   * @return formatted durability bar string with percentage
   */
  public static String buildDurabilityBar(int damage, int maxDurability) {
    if (maxDurability <= 0) return "&#00FF88Full";
    double ratio = Math.max(0.0, Math.min(1.0, 1.0 - (double) damage / maxDurability));
    int filled = (int) Math.round(ratio * 8);
    String barColor = ratio > 0.6 ? "&#00FF88" : ratio > 0.3 ? "&#FFAA00" : "&#FF4444";
    String filledSymbol =
        PROGRESSBAR_FILLED.replaceAll("^(&[#][0-9A-Fa-f]{6}|&[0-9a-fk-orA-FK-OR])+", "");
    String emptySymbol =
        PROGRESSBAR_EMPTY.replaceAll("^(&[#][0-9A-Fa-f]{6}|&[0-9a-fk-orA-FK-OR])+", "");
    return barColor
        + filledSymbol.repeat(filled)
        + "&#444444"
        + emptySymbol.repeat(8 - filled)
        + " "
        + barColor
        + Math.round(ratio * 100)
        + "%";
  }

  /**
   * Generates a progress bar string for visual display using config symbols.
   *
   * @param current The current value
   * @param max The maximum value
   * @return A progress bar string (e.g., "████████░░")
   */
  public static String generateProgressBar(int current, int max) {
    if (max <= 0) return PROGRESSBAR_EMPTY.repeat(10);

    double progress = Math.min(1.0, (double) current / max);
    int filledLength = (int) Math.round(progress * 10);
    int emptyLength = 10 - filledLength;

    String filledSymbol =
        PROGRESSBAR_FILLED.replaceAll("^(&[#][0-9A-Fa-f]{6}|&[0-9a-fk-orA-FK-OR])+", "");
    String dynamicColor = progressBarColor(progress);

    return dynamicColor + filledSymbol.repeat(filledLength) + PROGRESSBAR_EMPTY.repeat(emptyLength);
  }

  /**
   * Interpolates a hex color string for a progress bar. Green (#55FF55) at full → Yellow (#FFFF55)
   * at half → Red (#FF5555) at empty.
   */
  private static String progressBarColor(double progress) {
    int r, g;
    if (progress >= 0.5) {
      double t = (progress - 0.5) / 0.5;
      r = (int) Math.round(85 + (1.0 - t) * 170);
      g = 255;
    } else {
      double t = progress / 0.5;
      r = 255;
      g = (int) Math.round(t * 255);
    }
    return String.format("&#%02X%02X55", r, g);
  }

  /**
   * Generates a customizable progress bar string.
   *
   * @param current The current value
   * @param max The maximum value
   * @param length The total length of the progress bar
   * @param filledChar The character for filled portions
   * @param emptyChar The character for empty portions
   * @return A progress bar string
   */
  public static String generateProgressBar(
      int current, int max, int length, String filledChar, String emptyChar) {
    if (max <= 0) {
      return emptyChar.repeat(length);
    }

    double progress = Math.min(1.0, (double) current / max);
    int filledLength = (int) Math.round(progress * length);
    int emptyLength = length - filledLength;

    return filledChar.repeat(filledLength) + emptyChar.repeat(emptyLength);
  }

  /**
   * Safely parses a string into an enum value, returning a default if the string is null or
   * invalid.
   *
   * @param value the string value to parse
   * @param enumClass the enum class to parse into
   * @param defaultValue the default value to return on failure
   * @param <T> the enum type
   * @return the parsed enum value, or defaultValue if parsing fails
   */
  public static <T extends Enum<T>> T safeEnumParse(
      String value, Class<T> enumClass, T defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return Enum.valueOf(enumClass, value);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  /**
   * Formats a material name into a human-readable title case string. For example, {@code
   * DARK_OAK_PLANKS} becomes {@code "Dark Oak Planks"}.
   *
   * @param material the material to format
   * @return the formatted material name
   */
  public static String formatMaterialName(Material material) {
    String name = material.name().toLowerCase().replace("_", " ");
    String[] words = name.split(" ");
    StringBuilder formatted = new StringBuilder();
    for (String word : words) {
      if (!word.isEmpty()) {
        formatted
            .append(Character.toUpperCase(word.charAt(0)))
            .append(word.substring(1))
            .append(" ");
      }
    }
    return formatted.toString().trim();
  }

  /**
   * Splits a string into lines of at most {@code maxWords} words each.
   *
   * @param text the text to wrap
   * @param maxWords maximum words per line
   * @return list of wrapped lines
   */
  public static List<String> wrapWords(String text, int maxWords) {
    List<String> lines = new ArrayList<>();
    String[] words = text.split(" ");
    StringBuilder chunk = new StringBuilder();
    int count = 0;
    for (String word : words) {
      if (count == maxWords) {
        lines.add(chunk.toString().trim());
        chunk = new StringBuilder();
        count = 0;
      }
      chunk.append(word).append(" ");
      count++;
    }
    if (!chunk.isEmpty()) lines.add(chunk.toString().trim());
    return lines;
  }

  /**
   * Makes a player glow with a specific team color for a set duration. Creates a temporary
   * scoreboard team on every online player's scoreboard (and the main scoreboard) so the colored
   * outline is visible to all viewers.
   *
   * @param player the player to glow
   * @param color the glow outline color
   * @param durationTicks how long the glow lasts in ticks
   * @param plugin plugin instance for scheduling
   */
  public static void glowWithColor(
      Player player, NamedTextColor color, long durationTicks, JavaPlugin plugin) {
    String teamName = "evolv3_glow_" + color.toString();
    Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
    addToGlowTeam(main, teamName, color, player);
    player.setGlowing(true);

    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              removeFromGlowTeam(main, teamName, player);
              if (player.isOnline()) player.setGlowing(false);
            },
            durationTicks);
  }

  /** Adds to glow team. */
  private static void addToGlowTeam(
      Scoreboard scoreboard, String teamName, NamedTextColor color, Player player) {
    Team team = scoreboard.getTeam(teamName);
    if (team == null) {
      team = scoreboard.registerNewTeam(teamName);
      team.color(color);
    }
    team.addEntity(player);
  }

  /** Remove from glow team. */
  private static void removeFromGlowTeam(Scoreboard scoreboard, String teamName, Player player) {
    Team team = scoreboard.getTeam(teamName);
    if (team != null) team.removeEntity(player);
  }
}
