package org.benski.evolv3.core.helpers;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Renders a player's skin face as Adventure text components using full-block characters.
 *
 * <p>The 8×8 face region is sampled from the player's skin sheet, with the hat layer
 * alpha-composited on top. Each pixel is rendered as two {@code █} characters coloured with exact
 * RGB values via Adventure's {@link TextColor}.
 *
 * <p>The download is performed asynchronously; the callback is always invoked back on the main
 * server thread.
 */
public final class SkinRenderer {
  private static final int FACE_X = 8;
  private static final int FACE_Y = 8;

  private static final int HAT_X = 40;
  private static final int HAT_Y = 8;

  private static final int SIZE = 8;

  private static final String PIXEL = "█";

  private SkinRenderer() {}

  /**
   * Asynchronously fetches the player's skin and renders their 8×8 face. Each element in the
   * returned list is one row of 8 coloured pixels. Falls back to a grey placeholder if the skin
   * cannot be downloaded.
   *
   * @param player the player whose skin to render
   * @param plugin the plugin instance (for scheduler)
   * @param callback invoked on the main thread with the rendered rows
   */
  @SuppressWarnings("deprecation")
  public static void renderAsync(
      Player player, JavaPlugin plugin, Consumer<List<Component>> callback) {
    org.bukkit.profile.PlayerProfile profile = player.getPlayerProfile();

    Bukkit.getScheduler()
        .runTaskAsynchronously(
            plugin,
            () -> {
              List<Component> rows = render(profile);
              Bukkit.getScheduler().runTask(plugin, () -> callback.accept(rows));
            });
  }

  @SuppressWarnings("deprecation")
  private static List<Component> render(org.bukkit.profile.PlayerProfile profile) {
    try {
      URL skinUrl = profile.getTextures().getSkin();
      if (skinUrl == null) {
        return fallback();
      }

      BufferedImage skin = ImageIO.read(skinUrl);
      if (skin == null) {
        return fallback();
      }

      boolean has64Height = skin.getHeight() >= 64;

      List<Component> rows = new ArrayList<>(SIZE);
      for (int y = 0; y < SIZE; y++) {
        Component row = Component.empty();
        for (int x = 0; x < SIZE; x++) {
          int rgb = compositeHat(skin, x, y, has64Height);
          int r = (rgb >> 16) & 0xFF;
          int g = (rgb >> 8) & 0xFF;
          int b = rgb & 0xFF;
          row = row.append(Component.text(PIXEL).color(TextColor.color(r, g, b)));
        }
        rows.add(row);
      }
      return rows;

    } catch (Exception e) {
      return fallback();
    }
  }

  /** Returns the RGB of the face pixel at (x, y) with the hat layer composited on top. */
  private static int compositeHat(BufferedImage skin, int x, int y, boolean has64Height) {
    int faceRgb = skin.getRGB(FACE_X + x, FACE_Y + y) & 0xFFFFFF;

    if (!has64Height) {
      return faceRgb;
    }

    int hatArgb = skin.getRGB(HAT_X + x, HAT_Y + y);
    int alpha = (hatArgb >> 24) & 0xFF;
    if (alpha == 0) {
      return faceRgb;
    }

    float a = alpha / 255f;
    int fR = (faceRgb >> 16) & 0xFF, fG = (faceRgb >> 8) & 0xFF, fB = faceRgb & 0xFF;
    int hR = (hatArgb >> 16) & 0xFF, hG = (hatArgb >> 8) & 0xFF, hB = hatArgb & 0xFF;

    int r = Math.round(hR * a + fR * (1f - a));
    int g = Math.round(hG * a + fG * (1f - a));
    int b = Math.round(hB * a + fB * (1f - a));
    return (r << 16) | (g << 8) | b;
  }

  /** Returns a plain grey 8×8 placeholder used when the skin cannot be loaded. */
  private static List<Component> fallback() {
    List<Component> rows = new ArrayList<>(SIZE);
    Component greyRow = Component.text("█".repeat(SIZE)).color(TextColor.color(0x888888));
    for (int i = 0; i < SIZE; i++) {
      rows.add(greyRow);
    }
    return rows;
  }
}
