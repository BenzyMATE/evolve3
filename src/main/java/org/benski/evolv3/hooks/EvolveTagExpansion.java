package org.benski.evolv3.hooks;

import java.util.Map;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.benski.evolv3.Main;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for item rarity/type glyphs and MiniMessage colour tags.
 *
 * <ul>
 *   <li>{@code %evolve_<rarity>_tag%} — raw glyph character for that rarity
 *   <li>{@code %evolve_<type>_tag%} — raw glyph character for that type
 *   <li>{@code %evolve_<rarity>_colour%} — MiniMessage colour tag for that rarity
 * </ul>
 */
public class EvolveTagExpansion extends PlaceholderExpansion {

  private static final Map<String, String> RARITY_GLYPHS =
      Map.of(
          "common", "㋐",
          "uncommon", "㋑",
          "rare", "㋒",
          "epic", "㋓",
          "legendary", "㋔",
          "artifact", "㋕");

  private static final Map<String, String> TYPE_GLYPHS =
      Map.ofEntries(
          Map.entry("item", "㋖"),
          Map.entry("weapon", "㋗"),
          Map.entry("armor", "㋘"),
          Map.entry("tool", "㋙"),
          Map.entry("food", "㋚"),
          Map.entry("currency", "㋛"),
          Map.entry("block", "㋜"),
          Map.entry("furniture", "㋝"),
          Map.entry("hat", "㋞"),
          Map.entry("cosmetic", "㋟"),
          Map.entry("pet", "㋠"),
          Map.entry("accessory", "㋡"),
          Map.entry("decoration", "㋢"),
          Map.entry("other", "㋣"));

  private static final Map<String, String> RARITY_COLOURS =
      Map.of(
          "common", "<grey>",
          "uncommon", "<green>",
          "rare", "<blue>",
          "epic", "<dark_purple>",
          "legendary", "<gold>",
          "artifact", "<red>");

  private final Main plugin;

  /**
   * Creates the expansion and binds it to the plugin instance.
   *
   * @param plugin the main plugin instance
   */
  public EvolveTagExpansion(Main plugin) {
    this.plugin = plugin;
  }

  @Override
  public @NotNull String getIdentifier() {
    return "evolve";
  }

  @Override
  public @NotNull String getAuthor() {
    return "Benski";
  }

  @Override
  public @NotNull String getVersion() {
    return plugin.getPluginMeta().getVersion();
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
    int lastUnderscore = params.lastIndexOf('_');
    if (lastUnderscore == -1) return null;

    String key = params.substring(0, lastUnderscore);
    String suffix = params.substring(lastUnderscore + 1);

    return switch (suffix) {
      case "tag" -> {
        String glyph = RARITY_GLYPHS.get(key);
        yield glyph != null ? glyph : TYPE_GLYPHS.get(key);
      }
      case "colour" -> RARITY_COLOURS.get(key);
      default -> null;
    };
  }
}
