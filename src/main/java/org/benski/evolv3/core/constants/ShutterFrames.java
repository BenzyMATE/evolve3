package org.benski.evolv3.core.constants;

/**
 * Font glyphs for the 24-frame socket menu shutter animation, mapped in {@code default.json} to
 * {@code shutter_socket_1.png} through {@code shutter_socket_24.png}.
 *
 * <p>Frame 1 is nearly fully transparent (shutter retracted/open); frame 24 is a fully opaque
 * shutter texture (fully closed). The pickaxe and socket menus share this array so both sides of a
 * transition use the exact same frame-to-glyph mapping.
 */
public final class ShutterFrames {
  /** Creates a new ShutterFrames instance. */
  private ShutterFrames() {}

  /** Total number of keyframes in the shutter animation. */
  public static final int FRAME_COUNT = 24;

  /** Glyphs indexed 0-23, corresponding to frames 1-24. */
  public static final String[] CHARS = {
    "⠋", "⠌", "⠍", "⠎", "⠏", "⠐", "⠑", "⠒", "⠓", "⠔", "⠕", "⠖",
    "⠗", "⠘", "⠙", "⠚", "⠛", "⠜", "⠝", "⠞", "⠟", "⠠", "⠡", "⠢"
  };

  /**
   * Returns the glyph for a 1-indexed frame number.
   *
   * @param frame frame number, 1-24
   * @return the corresponding font glyph
   */
  public static String glyph(int frame) {
    return CHARS[frame - 1];
  }
}
