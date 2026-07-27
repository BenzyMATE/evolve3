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
