package org.benski.evolv3.core.builders;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builder pattern for creating formatted messages with placeholders and styling.
 * Useful for complex messages with multiple lines, placeholders, and formatting.
 *
 * <p>Example usage:
 * <pre>{@code
 * String message = new MessageBuilder("&6Welcome, {player}!")
 *     .placeholder("player", player.getName())
 *     .build();
 *
 * // Multi-line with prefix
 * new MessageBuilder()
 *     .prefix("&8[&6Shop&8]")
 *     .line("&7Welcome to the shop!")
 *     .line("&7You have &a{coins} &7coins")
 *     .placeholder("coins", playerCoins)
 *     .sendTo(player);
 *
 * // Centering and formatting
 * String centered = new MessageBuilder("&6&lSERVER ANNOUNCEMENT")
 *     .center()
 *     .build();
 * }</pre>
 */
public class MessageBuilder
{
    private final List<String> lines = new ArrayList<>();
    private final Map<String, String> placeholders = new HashMap<>();
    private String prefix = "";
    private String suffix = "";
    private boolean centered = false;
    private static final int CHAT_WIDTH = 60; // Approximate chat width in pixels

    /**
     * Creates a new empty MessageBuilder.
     */
    public MessageBuilder() {
    }

    /**
     * Creates a new MessageBuilder with an initial message.
     *
     * @param message the initial message
     */
    public MessageBuilder(String message) {
        this.lines.add(message);
    }

    /**
     * Adds a line to the message.
     *
     * @param line the line to add
     * @return this builder
     */
    public MessageBuilder line(String line) {
        lines.add(line);
        return this;
    }

    /**
     * Adds multiple lines to the message.
     *
     * @param lines the lines to add
     * @return this builder
     */
    public MessageBuilder lines(String... lines) {
        for (String line : lines) {
            this.lines.add(line);
        }
        return this;
    }

    /**
     * Adds multiple lines from a list.
     *
     * @param lines the lines to add
     * @return this builder
     */
    public MessageBuilder lines(List<String> lines) {
        this.lines.addAll(lines);
        return this;
    }

    /**
     * Adds an empty line.
     *
     * @return this builder
     */
    public MessageBuilder blank() {
        lines.add("");
        return this;
    }

    /**
     * Adds a centered line to the message.
     * The line will be centered independently of other lines.
     *
     * @param line the line to center and add
     * @return this builder
     */
    public MessageBuilder centeredLine(String line) {
        lines.add("__CENTER__" + line);
        return this;
    }

    /**
     * Adds multiple centered lines to the message.
     *
     * @param lines the lines to center and add
     * @return this builder
     */
    public MessageBuilder centeredLines(String... lines) {
        for (String line : lines) {
            this.lines.add("__CENTER__" + line);
        }
        return this;
    }

    /**
     * Sets a prefix that will be prepended to each line.
     *
     * @param prefix the prefix
     * @return this builder
     */
    public MessageBuilder prefix(String prefix) {
        this.prefix = prefix + " ";
        return this;
    }

    /**
     * Sets a suffix that will be appended to each line.
     *
     * @param suffix the suffix
     * @return this builder
     */
    public MessageBuilder suffix(String suffix) {
        this.suffix = " " + suffix;
        return this;
    }

    /**
     * Adds a placeholder replacement.
     *
     * @param key the placeholder key (without braces)
     * @param value the replacement value
     * @return this builder
     */
    public MessageBuilder placeholder(String key, Object value) {
        placeholders.put("{" + key + "}", String.valueOf(value));
        return this;
    }

    /**
     * Adds multiple placeholder replacements.
     *
     * @param placeholders map of placeholder key -> value
     * @return this builder
     */
    public MessageBuilder placeholders(Map<String, String> placeholders) {
        placeholders.forEach((key, value) -> this.placeholders.put("{" + key + "}", value));
        return this;
    }

    /**
     * Replaces a raw string with another (useful for custom replacements).
     *
     * @param target the string to replace
     * @param replacement the replacement string
     * @return this builder
     */
    public MessageBuilder replace(String target, String replacement) {
        placeholders.put(target, replacement);
        return this;
    }

    /**
     * Centers all lines in the chat.
     * Note: Centering is approximate and works best with monospace fonts.
     *
     * @return this builder
     */
    public MessageBuilder center() {
        this.centered = true;
        return this;
    }

    /**
     * Adds a divider line.
     *
     * @param character the character to use for the divider
     * @param color the color code (e.g., "&7")
     * @return this builder
     */
    public MessageBuilder divider(char character, String color) {
        StringBuilder divider = new StringBuilder(color);
        for (int i = 0; i < 40; i++) {
            divider.append(character);
        }
        lines.add(divider.toString());
        return this;
    }

    /**
     * Adds a standard divider line using dashes.
     *
     * @return this builder
     */
    public MessageBuilder divider() {
        return divider('-', "&7");
    }

    /**
     * Applies a custom transformation to all lines.
     *
     * @param transformer the transformation function
     * @return this builder
     */
    public MessageBuilder transform(Function<String, String> transformer) {
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, transformer.apply(lines.get(i)));
        }
        return this;
    }

    /**
     * Builds and returns the formatted message as a single string.
     *
     * @return the formatted message
     */
    public String build() {
        if (lines.isEmpty()) {
            return "";
        }

        if (lines.size() == 1) {
            return formatLine(lines.get(0));
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            result.append(formatLine(lines.get(i)));
            if (i < lines.size() - 1) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Builds and returns the message as a list of formatted strings.
     *
     * @return list of formatted message lines
     */
    public List<String> buildList() {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(formatLine(line));
        }
        return result;
    }

    /**
     * Sends the message to a CommandSender.
     *
     * @param sender the sender to send to
     */
    public void sendTo(CommandSender sender) {
        for (String line : lines) {
            sender.sendMessage(formatLine(line));
        }
    }

    /**
     * Sends the message to a Player.
     *
     * @param player the player to send to
     */
    public void sendTo(Player player) {
        sendTo((CommandSender) player);
    }

    /**
     * Sends the message to multiple CommandSenders.
     *
     * @param senders the senders to send to
     */
    public void sendToAll(CommandSender... senders) {
        for (CommandSender sender : senders) {
            sendTo(sender);
        }
    }

    /**
     * Formats a single line with placeholders, prefix, suffix, and centering.
     *
     * @param line the line to format
     * @return the formatted line
     */
    private String formatLine(String line) {
        // Check if this line should be centered individually
        boolean shouldCenterThisLine = line.startsWith("__CENTER__");
        String formatted = shouldCenterThisLine ? line.substring(10) : line;

        // Apply placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace(entry.getKey(), entry.getValue());
        }

        // Apply prefix and suffix (not for centered lines)
        if (!formatted.isEmpty() && !shouldCenterThisLine) {
            formatted = prefix + formatted + suffix;
        }

        // Apply centering (either from center() or centeredLine())
        if ((centered || shouldCenterThisLine) && !formatted.isEmpty()) {
            formatted = centerText(formatted);
        }

        // Colorize
        return colorize(formatted);
    }

    /**
     * Centers text in the chat (approximate).
     *
     * @param text the text to center
     * @return the centered text
     */
    private String centerText(String text) {
        // Remove color codes for length calculation
        String stripped = text.replaceAll("&[0-9a-fk-or]", "");

        int spaces = (CHAT_WIDTH - stripped.length()) / 2;
        if (spaces <= 0) {
            return text;
        }

        StringBuilder centered = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            centered.append(" ");
        }
        centered.append(text);
        return centered.toString();
    }

    /**
     * Utility method to colorize strings with '&' color codes.
     *
     * @param text the text to colorize
     * @return the colorized text
     */
    private static String colorize(String text) {
        return text.replace('&', '§');
    }

    /**
     * Creates a new MessageBuilder instance (alternative to constructor).
     *
     * @return a new MessageBuilder
     */
    public static MessageBuilder create() {
        return new MessageBuilder();
    }

    /**
     * Creates a new MessageBuilder instance with an initial message.
     *
     * @param message the initial message
     * @return a new MessageBuilder
     */
    public static MessageBuilder create(String message) {
        return new MessageBuilder(message);
    }

    // Static utility methods

    /**
     * Centers a message in the chat and colorizes it.
     * Useful for quick centering without using the builder.
     *
     * @param message the message to center (supports '&' color codes)
     * @return the centered and colorized message
     */
    public static String centerMessage(String message) {
        // Colorize first
        String colored = message.replace('&', '§');

        // Remove color codes for length calculation
        String stripped = colored.replaceAll("§[0-9a-fk-or]", "");

        int spaces = (CHAT_WIDTH - stripped.length()) / 2;
        if (spaces <= 0) {
            return colored;
        }

        StringBuilder centered = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            centered.append(" ");
        }
        centered.append(colored);
        return centered.toString();
    }

    /**
     * Creates a simple divider line.
     *
     * @param character the character to use for the divider
     * @param color the color code (e.g., "&7")
     * @param length the length of the divider
     * @return the divider string
     */
    public static String createDivider(char character, String color, int length) {
        StringBuilder divider = new StringBuilder(colorize(color));
        for (int i = 0; i < length; i++) {
            divider.append(character);
        }
        return divider.toString();
    }

    /**
     * Creates a standard divider line (40 dashes, gray).
     *
     * @return the divider string
     */
    public static String createDivider() {
        return createDivider('-', "&7", 40);
    }
}
