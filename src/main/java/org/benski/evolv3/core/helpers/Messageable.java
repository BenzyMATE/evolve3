package org.benski.evolv3.core.helpers;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Static utility for sending formatted messages to players and command senders. Messages are styled
 * to match the server's theme using the global prefix from {@code messages.yml}.
 *
 * <p>Colour scheme:
 *
 * <ul>
 *   <li>{@code &#00FFFF} — cyan accent (success/info labels)
 *   <li>{@code &#CCCCCC} — body text
 *   <li>{@code &#FFFFFF} — highlighted values
 *   <li>{@code &c} / {@code &#FF5555} — errors
 *   <li>{@code &#888888} — muted / secondary text
 * </ul>
 */
public final class Messageable {
  private Messageable() {}

  private static final LegacyComponentSerializer SERIALIZER =
      LegacyComponentSerializer.legacySection();

  /**
   * Processes a raw message string through MatrixColorAPI to convert {@code &#RRGGBB} hex codes and
   * {@code &x} legacy codes into section-sign format.
   */
  private static String colorize(String message) {
    message = FontUtils.toSmallCaps(message);
    return MatrixColorAPI.process(message);
  }

  /**
   * Retrieves the server prefix from messages.yml. Falls back to a hardcoded default if the config
   * isn't loaded yet.
   */
  private static String getPrefix() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    if (cfg != null) {
      return cfg.getString(ConfigPaths.Messages.PREFIX, "&3&lE &8➧ ");
    }
    return "&3&lE &8➧ ";
  }

  /**
   * Retrieves a message from the messages config, replacing the prefix placeholder.
   *
   * @param path the config path (e.g. "mine.mine-created")
   * @return the formatted message string with prefix resolved, or empty string if not found
   */
  public static String getMessage(String path) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    if (cfg == null) return "";
    String raw = cfg.getString(path, "");
    return raw.replace("%evolv3_prefix%", getPrefix());
  }

  /**
   * Sends a styled message from the messages config to a player. Resolves prefix placeholder and
   * deserializes colour codes.
   *
   * @param sender the command sender
   * @param path the config path (e.g. "mine.mine-created")
   */
  public static void sendConfigMessage(CommandSender sender, String path) {
    String msg = getMessage(path);
    if (!msg.isEmpty()) {
      sender.sendMessage(SERIALIZER.deserialize(colorize(msg)));
    }
  }

  /**
   * Sends a styled message from the messages config to a player, applying placeholder replacements
   * first.
   *
   * @param sender the command sender
   * @param path the config path
   * @param placeholders alternating key/value pairs (e.g. "%mine%", "alpha", "%count%", "5")
   */
  public static void sendConfigMessage(CommandSender sender, String path, String... placeholders) {
    String msg = getMessage(path);
    if (msg.isEmpty()) return;

    for (int i = 0; i + 1 < placeholders.length; i += 2) {
      msg = msg.replace(placeholders[i], placeholders[i + 1]);
    }
    sender.sendMessage(SERIALIZER.deserialize(colorize(msg)));
  }

  /**
   * Sends a success message with the server prefix.
   *
   * @param sender the command sender
   * @param message the message to send (may contain legacy colour codes)
   */
  public static void sendSuccess(CommandSender sender, String message) {
    sender.sendMessage(SERIALIZER.deserialize(colorize("  " + getPrefix() + "&#00FFFF" + message)));
  }

  /**
   * Sends an error message with the server prefix.
   *
   * @param sender the command sender
   * @param message the message to send (may contain legacy colour codes)
   */
  public static void sendError(CommandSender sender, String message) {
    sender.sendMessage(SERIALIZER.deserialize(colorize("  " + getPrefix() + "&c" + message)));
  }

  /**
   * Sends an info message with muted styling and server prefix.
   *
   * @param sender the command sender
   * @param message the message to send (may contain legacy colour codes)
   */
  public static void sendInfo(CommandSender sender, String message) {
    sender.sendMessage(SERIALIZER.deserialize(colorize("  " + getPrefix() + "&#CCCCCC" + message)));
  }

  /**
   * Sends a success message with the server prefix.
   *
   * @param player the player
   * @param message the message to send (may contain legacy colour codes)
   */
  public static void sendSuccess(Player player, String message) {
    sendSuccess((CommandSender) player, message);
  }

  /**
   * Sends an error message with the server prefix.
   *
   * @param player the player
   * @param message the message to send (may contain legacy colour codes)
   */
  public static void sendError(Player player, String message) {
    sendError((CommandSender) player, message);
  }

  /**
   * Sends an info message with muted styling and server prefix.
   *
   * @param player the player
   * @param message the message to send (may contain legacy colour codes)
   */
  public static void sendInfo(Player player, String message) {
    sendInfo((CommandSender) player, message);
  }

  /**
   * Sends a raw legacy-formatted message (no prefix added). Processes hex colour codes and legacy
   * codes.
   *
   * @param sender the command sender
   * @param message the message with legacy colour codes
   */
  public static void sendRaw(CommandSender sender, String message) {
    sender.sendMessage(SERIALIZER.deserialize(colorize(message)));
  }
}
