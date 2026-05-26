package org.benski.evolv3.core.helpers;

/**
 * Utility for converting plain text to clean Unicode font variants.
 *
 * <p>Designed to plug into the {@link Utils#formatMessage} pipeline: conversion runs
 * <em>before</em> PlaceholderAPI replacement so that static label text is styled while dynamic
 * placeholder values (levels, names, counts) are left untouched.
 *
 * <p>The converter is context-aware and automatically skips:
 *
 * <ul>
 *   <li>{@code &#RRGGBB} hex colour codes (8 chars)
 *   <li>{@code &X} legacy colour/format codes (2 chars)
 *   <li>{@code §X} already-processed section-symbol codes (2 chars)
 *   <li>{@code %placeholder%} PlaceholderAPI tokens
 * </ul>
 *
 * <p>Two font styles are provided:
 *
 * <ul>
 *   <li>{@link #toSmallCaps(String)} – small capital Unicode characters. Ideal for all lore, chat
 *       messages, and action bars.
 *   <li>{@link #toBoldSans(String)} – mathematical bold sans-serif Unicode. Best for prominent
 *       display titles.
 * </ul>
 *
 * <p>Usage (manual, outside the pipeline):
 *
 * <pre>{@code
 * " &#FFFFFF&l" + FontUtils.toSmallCaps("Description")
 *
 *
 * " &#FFD700&l" + FontUtils.toBoldSans("Evolution")
 * }</pre>
 */
public final class FontUtils {
  /** Creates a new FontUtils instance. */
  private FontUtils() {}

  /**
   * Small capital Unicode characters indexed by alphabet position (0 = A/a, 25 = Z/z). Where no
   * dedicated small cap exists in Unicode the original character is kept.
   */
  private static final char[] SMALL_CAPS_MAP = {
    '\u1D00', '\u0299', '\u1D04', '\u1D05', '\u1D07', '\uA730', '\u0262', '\u029C', '\u026A',
    '\u1D0A', '\u1D0B', '\u029F', '\u1D0D', '\u0274', '\u1D0F', '\u1D18', '\u01EB', '\u0280',
    '\uA731', '\u1D1B', '\u1D1C', '\u1D20', '\u1D21', 'x', '\u028F', '\u1D22',
  };

  /**
   * Converts a string to small capital Unicode characters, intelligently skipping Minecraft colour
   * codes and PlaceholderAPI tokens so they remain fully functional after conversion.
   *
   * <p>Sequences skipped (passed through unchanged):
   *
   * <ul>
   *   <li>{@code &#RRGGBB} — 8-character hex colour codes
   *   <li>{@code &X} — 2-character legacy colour/format codes
   *   <li>{@code §X} — 2-character already-processed section codes
   *   <li>{@code %token%} — PlaceholderAPI placeholder tokens
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

      if (c == '%') {
        int end = input.indexOf('%', i + 1);
        if (end != -1) {
          sb.append(input, i, end + 1);
          i = end + 1;
          continue;
        }
      }

      if (c == '&') {
        if (i + 1 < input.length()) {
          char next = input.charAt(i + 1);
          if (next == '#' && i + 7 < input.length()) {
            sb.append(input, i, i + 8);
            i += 8;
          } else {
            sb.append(input, i, i + 2);
            i += 2;
          }
        } else {
          sb.append(c);
          i++;
        }
        continue;
      }

      if (c == '§' && i + 1 < input.length()) {
        sb.append(input, i, i + 2);
        i += 2;
        continue;
      }

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
   * Converts a string to mathematical bold sans-serif Unicode characters (Unicode Mathematical
   * Alphanumeric Symbols block, U+1D5D4–U+1D607).
   *
   * <p>Supports A–Z, a–z, and 0–9. All other characters are preserved unchanged. This method does
   * <em>not</em> skip colour codes — use for raw strings only.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code "Evolution"} → {@code "𝗘𝘃𝗼𝗹𝘂𝘁𝗶𝗼𝗻"}
   *   <li>{@code "Essence"} → {@code "𝗘𝘀𝘀𝗲𝗻𝗰𝗲"}
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
        sb.appendCodePoint(0x1D5D4 + (c - 'A'));
      } else if (c >= 'a' && c <= 'z') {
        sb.appendCodePoint(0x1D5EE + (c - 'a'));
      } else if (c >= '0' && c <= '9') {
        sb.appendCodePoint(0x1D7EC + (c - '0'));
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
