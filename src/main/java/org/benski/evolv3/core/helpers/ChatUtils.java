package org.benski.evolv3.core.helpers;

/**
 * Utility methods for formatting Minecraft chat messages.
 */
public class ChatUtils {

    /** Half the usable pixel width of Minecraft's default chat box. */
    private static final int CENTER_PX = 154;

    /** Total pixel width of a space character, including the 1-pixel gap. */
    private static final int SPACE_WIDTH = 4;

    private ChatUtils() {}

    /**
     * Returns the pixel width of {@code c} in Minecraft's default font,
     * excluding the 1-pixel inter-character gap.
     */
    private static int baseCharWidth(char c, boolean bold) {
        int w = switch (c) {
            case ' '                                             -> 3;
            case 'i', '!', ',', '.', ':', ';', '\'', '|', '`' -> 1;
            case 'l', 'I'                                       -> 2;
            case 'f', 'k', 'r', 't'                            -> 4;
            default                                             -> 5;
        };
        return bold ? w + 1 : w;
    }

    /**
     * Calculates the total pixel width of a message string, stripping color and
     * format codes before measuring. Tracks bold state via {@code &l} / {@code §l}.
     *
     * <p>Supported code formats:</p>
     * <ul>
     *   <li>Hex color: {@code &#RRGGBB} (8 characters)</li>
     *   <li>Legacy: {@code &x} or {@code §x} (2 characters)</li>
     * </ul>
     */
    private static int pixelWidth(String message) {
        int width = 0;
        boolean bold = false;
        int i = 0;
        while (i < message.length()) {
            char c = message.charAt(i);

            // Hex color code: &#RRGGBB — skip all 8 characters
            if (c == '&' && i + 7 < message.length() && message.charAt(i + 1) == '#') {
                i += 8;
                continue;
            }

            // Legacy color/format code: &x or §x — skip 2 characters, track bold
            if ((c == '&' || c == '\u00A7') && i + 1 < message.length()) {
                char code = Character.toLowerCase(message.charAt(i + 1));
                if (code == 'l') {
                    bold = true;
                } else if ("0123456789abcdefr".indexOf(code) >= 0) {
                    bold = false; // color codes reset bold
                }
                i += 2;
                continue;
            }

            // Regular character: add pixel width plus the 1-pixel inter-character gap
            width += baseCharWidth(c, bold) + 1;
            i++;
        }
        return width;
    }

    /**
     * Centers a message horizontally in the Minecraft chat box by prepending spaces.
     * Color codes are stripped for width measurement but preserved in the returned string.
     *
     * @param message the raw (unformatted) message — may contain {@code &x} or {@code &#RRGGBB} codes
     * @return the message padded with leading spaces so it appears centered in chat
     */
    public static String center(String message) {
        if (message == null || message.isEmpty()) return message;
        int toCompensate = CENTER_PX - pixelWidth(message) / 2;
        int spaces = Math.max(0, toCompensate / SPACE_WIDTH);
        return " ".repeat(spaces) + message;
    }
}
