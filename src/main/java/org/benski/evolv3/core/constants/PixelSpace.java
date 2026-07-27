package org.benski.evolv3.core.constants;

/**
 * Composes exact-pixel horizontal spacing for custom GUI title strings, using the {@code space}
 * font provider registered in {@code default.json} (signed powers of two from 1 to 256).
 *
 * <p>Unlike the older {@code 七} / {@code pixel.png} bitmap-repeat trick, a {@code space} provider
 * advance is a documented, exact integer pixel offset at GUI scale 1 — no guessing or per-repeat
 * calibration required. Any integer pixel offset is composed by decomposing it into its binary
 * representation and concatenating the matching signed-magnitude characters.
 */
public final class PixelSpace {
  /** Creates a new PixelSpace instance. */
  private PixelSpace() {}

  private static final int[] MAGNITUDES = {256, 128, 64, 32, 16, 8, 4, 2, 1};
  private static final String[] POSITIVE_CHARS = {"⠮", "⠭", "⠬", "⠫", "⠪", "⠩", "⠨", "⠧", "⠦"};
  private static final String[] NEGATIVE_CHARS = {"⠷", "⠶", "⠵", "⠴", "⠳", "⠲", "⠱", "⠰", "⠯"};

  /**
   * Returns the character sequence that advances the cursor by exactly {@code pixels} (positive or
   * negative) at GUI scale 1.
   *
   * @param pixels the exact pixel offset, positive to move right, negative to move left
   * @return the composed spacer string
   */
  public static String of(int pixels) {
    String[] chars = pixels < 0 ? NEGATIVE_CHARS : POSITIVE_CHARS;
    int remaining = Math.abs(pixels);
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < MAGNITUDES.length; i++) {
      while (remaining >= MAGNITUDES[i]) {
        result.append(chars[i]);
        remaining -= MAGNITUDES[i];
      }
    }
    return result.toString();
  }
}
