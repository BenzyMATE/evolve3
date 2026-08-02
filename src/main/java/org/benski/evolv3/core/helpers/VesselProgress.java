package org.benski.evolv3.core.helpers;

import org.benski.evolv3.models.pickaxe.Pickaxe;

/**
 * Generates the animated Unicode progress-icon glyphs for a pickaxe's essence (enchant roll) and
 * atom (evolution) vessels. Shared by every menu that renders these vessels in its title (the
 * pickaxe menu and the stone socket menu) so the icon tables aren't duplicated per-menu.
 */
public final class VesselProgress {
  /** Creates a new VesselProgress instance. */
  private VesselProgress() {}

  /**
   * Milliseconds per animation frame, matching the menus' normal 10-tick title refresh interval.
   * Shared by every caller of {@link #currentFrame()} so their vessel icons stay in step.
   */
  private static final long FRAME_INTERVAL_MILLIS = 500;

  /** Number of frames in the bubble/fill loop before it wraps back to frame 1. */
  private static final int FRAME_COUNT = 5;

  /**
   * Returns the current animation frame (1–{@value #FRAME_COUNT}), derived from wall-clock time
   * rather than call count.
   *
   * <p>Menus that render a vessel icon re-render their title far more often than the normal 10-tick
   * cadence during a shutter transition (every 2 ticks, to keep the shutter itself smooth) — see
   * {@code BaseMenu#runFrameRamp}. A counter that advanced once per call would therefore cycle the
   * vessel animation up to 5x faster while a shutter is open/closing. Deriving the frame from
   * actual elapsed time keeps it moving at the same constant real-world rate no matter how often —
   * or rarely — the caller happens to ask for it.
   *
   * @return the current frame, 1–{@value #FRAME_COUNT}
   */
  public static int currentFrame() {
    return (int) ((System.currentTimeMillis() / FRAME_INTERVAL_MILLIS) % FRAME_COUNT) + 1;
  }

  /**
   * Generates a Unicode character representing the essence progress for a pickaxe profile.
   *
   * @param profile the player's pickaxe profile
   * @param animationFrame the current animation frame (1–5)
   * @return a single Unicode character for the progress icon
   */
  public static String getEssenceProgress(Pickaxe profile, int animationFrame) {
    if (profile.essenceVessel.crystals > 0) {
      return getAnimatedProgressIcon(10, animationFrame);
    }

    int percentage =
        (int)
            Math.round((double) profile.essenceVessel.current / profile.essenceVessel.target * 100);
    int progressStep = Math.min(percentage / 10, 10);

    if (progressStep == 0) {
      return "↛";
    }

    return getAnimatedProgressIcon(progressStep, animationFrame);
  }

  /**
   * Maps a progress step and animation frame to a Unicode progress icon.
   *
   * @param progressStep the current progress level (1–10)
   * @param frame the current animation frame (1–5)
   * @return the corresponding Unicode character
   */
  private static String getAnimatedProgressIcon(int progressStep, int frame) {
    String[][] progressIcons = {
      {"①", "②", "③", "④", "⑤"},
      {"⑥", "⑦", "⑧", "⑨", "⑩"},
      {"⑪", "⑫", "⑬", "⑭", "⑮"},
      {"⑯", "⑰", "⑱", "⑲", "⑳"},
      {"㉑", "㉒", "㉓", "㉔", "㉕"},
      {"㉖", "㉗", "㉘", "㉙", "㉚"},
      {"㉛", "㉜", "㉝", "㉞", "㉟"},
      {"㊱", "㊲", "㊳", "㊴", "㊵"},
      {"㊶", "㊷", "㊸", "㊹", "㊺"},
      {"㊻", "㊼", "㊽", "㊾", "㊿"}
    };

    if (progressStep < 1 || progressStep > 10 || frame < 1 || frame > 5) {
      return "";
    }

    return progressIcons[progressStep - 1][frame - 1];
  }

  /**
   * Generates a Unicode character representing the evolution progress for a pickaxe profile.
   *
   * @param profile the player's pickaxe profile
   * @param animationFrame the current animation frame (1–5)
   * @return a single Unicode character for the evolution progress icon
   */
  public static String getEvolveProgress(Pickaxe profile, int animationFrame) {
    if (profile.atomVessel.crystals > 0) {
      return getAnimatedEvolveProgressIcon(10, animationFrame);
    }

    int percentage =
        (int) Math.round((double) profile.atomVessel.current / profile.atomVessel.target * 100);
    int progressStep = Math.min(percentage / 10, 10);

    if (progressStep == 0) {
      return "⁐";
    }

    return getAnimatedEvolveProgressIcon(progressStep, animationFrame);
  }

  /**
   * Maps a progress step and animation frame to an evolution-specific Unicode icon.
   *
   * @param progressStep the current progress level (1–10)
   * @param frame the current animation frame (1–5)
   * @return the corresponding Unicode character
   */
  private static String getAnimatedEvolveProgressIcon(int progressStep, int frame) {
    String[][] evolveProgressIcons = {
      {"⑾", "⑿", "⒀", "⒁", "⒂"},
      {"⒃", "⒄", "⒅", "⒆", "⒇"},
      {"⒈", "⒉", "⒊", "⒋", "⒌"},
      {"⒍", "⒎", "⒏", "⒐", "⓵"},
      {"⓶", "⓷", "⓸", "⓹", "⓺"},
      {"⓻", "⓼", "⓽", "⓾", "⓿"},
      {"❶", "❷", "❸", "❹", "❺"},
      {"❻", "❼", "❽", "❾", "❿"},
      {"➀", "➁", "➂", "➃", "➄"},
      {"➅", "➆", "➇", "➈", "➉"}
    };

    if (progressStep < 1 || progressStep > 10 || frame < 1 || frame > 5) {
      return "🞨";
    }

    return evolveProgressIcons[progressStep - 1][frame - 1];
  }
}
