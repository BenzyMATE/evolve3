package org.benski.evolv3.core.helpers;

/**
 * Utility for converting plain text to clean Unicode font variants.
 *
 * <p>Designed to plug into the {@link Utils#formatMessage} pipeline:
 * conversion runs <em>before</em> PlaceholderAPI replacement so that
 * static label text is styled while dynamic placeholder values (levels,
 * names, counts) are left untouched.</p>
 *
 * <p>The converter is context-aware and automatically skips:</p>
 * <ul>
 *   <li>{@code &#RRGGBB} hex colour codes (8 chars)</li>
 *   <li>{@code &X} legacy colour/format codes (2 chars)</li>
 *   <li>{@code §X} already-processed section-symbol codes (2 chars)</li>
 *   <li>{@code %placeholder%} PlaceholderAPI tokens</li>
 * </ul>
 *
 * <p>Two font styles are provided:</p>
 * <ul>
 *   <li>{@link #toSmallCaps(String)} – small capital Unicode characters.
 *       Ideal for all lore, chat messages, and action bars.</li>
 *   <li>{@link #toBoldSans(String)} – mathematical bold sans-serif Unicode.
 *       Best for prominent display titles.</li>
 * </ul>
 *
 * <p>Usage (manual, outside the pipeline):</p>
 * <pre>{@code
 * // Produces " &#FFFFFF&lᴅᴇꜱᴄʀɪᴘᴛɪᴏɴ"
 * " &#FFFFFF&l" + FontUtils.toSmallCaps("Description")
 *
 * // Produces " &#FFD700&l𝗘𝘃𝗼𝗹𝘂𝘁𝗶𝗼𝗻"
 * " &#FFD700&l" + FontUtils.toBoldSans("Evolution")
 * }</pre>
 */
public final class FontUtils
{
    private FontUtils() {}

    /**
     * Small capital Unicode characters indexed by alphabet position (0 = A/a, 25 = Z/z).
     * Where no dedicated small cap exists in Unicode the original character is kept.
     */
    private static final char[] SMALL_CAPS_MAP = {
        '\u1D00', // A/a → ᴀ
        '\u0299', // B/b → ʙ
        '\u1D04', // C/c → ᴄ
        '\u1D05', // D/d → ᴅ
        '\u1D07', // E/e → ᴇ
        '\uA730', // F/f → ꜰ
        '\u0262', // G/g → ɢ
        '\u029C', // H/h → ʜ
        '\u026A', // I/i → ɪ
        '\u1D0A', // J/j → ᴊ
        '\u1D0B', // K/k → ᴋ
        '\u029F', // L/l → ʟ
        '\u1D0D', // M/m → ᴍ
        '\u0274', // N/n → ɴ
        '\u1D0F', // O/o → ᴏ
        '\u1D18', // P/p → ᴘ
        '\u01EB', // Q/q → ǫ  (closest available)
        '\u0280', // R/r → ʀ
        '\uA731', // S/s → ꜱ
        '\u1D1B', // T/t → ᴛ
        '\u1D1C', // U/u → ᴜ
        '\u1D20', // V/v → ᴠ
        '\u1D21', // W/w → ᴡ
        'x',      // X/x → x  (no dedicated small cap in Unicode)
        '\u028F', // Y/y → ʏ
        '\u1D22', // Z/z → ᴢ
    };

    /**
     * Converts a string to small capital Unicode characters, intelligently
     * skipping Minecraft colour codes and PlaceholderAPI tokens so they
     * remain fully functional after conversion.
     *
     * <p>Sequences skipped (passed through unchanged):</p>
     * <ul>
     *   <li>{@code &#RRGGBB} — 8-character hex colour codes</li>
     *   <li>{@code &X}       — 2-character legacy colour/format codes</li>
     *   <li>{@code §X}       — 2-character already-processed section codes</li>
     *   <li>{@code %token%}  — PlaceholderAPI placeholder tokens</li>
     * </ul>
     *
     * @param input the raw string (may contain colour codes and placeholders)
     * @return the string with alphabetic text converted to small capitals
     */
    public static String toSmallCaps(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);

            // PlaceholderAPI token: %identifier%
            if (c == '%') {
                int end = input.indexOf('%', i + 1);
                if (end != -1) {
                    sb.append(input, i, end + 1);
                    i = end + 1;
                    continue;
                }
            }

            // & colour codes: &#RRGGBB (8 chars) or &X (2 chars)
            if (c == '&') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '#' && i + 7 < input.length()) {
                        sb.append(input, i, i + 8); // &#RRGGBB
                        i += 8;
                    } else {
                        sb.append(input, i, i + 2); // &X
                        i += 2;
                    }
                } else {
                    sb.append(c);
                    i++;
                }
                continue;
            }

            // § section-symbol codes (after MatrixColorAPI/legacy processing)
            if (c == '§' && i + 1 < input.length()) {
                sb.append(input, i, i + 2);
                i += 2;
                continue;
            }

            // Convert alphabetic characters to small caps
            if (c >= 'A' && c <= 'Z') {
                sb.append(SMALL_CAPS_MAP[c - 'A']);
            } else if (c >= 'a' && c <= 'z') {
                sb.append(SMALL_CAPS_MAP[c - 'a']);
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }

    /**
     * Converts a string to mathematical bold sans-serif Unicode characters
     * (Unicode Mathematical Alphanumeric Symbols block, U+1D5D4–U+1D607).
     *
     * <p>Supports A–Z, a–z, and 0–9. All other characters are preserved unchanged.
     * This method does <em>not</em> skip colour codes — use for raw strings only.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code "Evolution"} → {@code "𝗘𝘃𝗼𝗹𝘂𝘁𝗶𝗼𝗻"}</li>
     *   <li>{@code "Essence"}   → {@code "𝗘𝘀𝘀𝗲𝗻𝗰𝗲"}</li>
     * </ul>
     *
     * @param input the plain text to convert (no colour codes)
     * @return the input with alphanumeric characters replaced by bold sans-serif variants
     */
    public static String toBoldSans(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.appendCodePoint(0x1D5D4 + (c - 'A')); // 𝗔–𝗭
            } else if (c >= 'a' && c <= 'z') {
                sb.appendCodePoint(0x1D5EE + (c - 'a')); // 𝗮–𝘇
            } else if (c >= '0' && c <= '9') {
                sb.appendCodePoint(0x1D7EC + (c - '0')); // 𝟬–𝟵
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Convenience alias for {@link #toSmallCaps(String)}.
     *
     * @param input the raw string
     * @return the input with alphabetic text in small capitals
     */
    public static String clean(String input) {
        return toSmallCaps(input);
    }
}
