package org.benski.evolv3.core.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/** Utility for applying rarity/type glyphs and tooltip styles to items via the resourcepack. */
public final class ItemTagHelper {

  private static final Logger LOGGER = Logger.getLogger("Evolv3");

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
          Map.entry("other", "㋣"),
          Map.entry("evolution_stone", "㋤"),
          Map.entry("enchant", "㋥"),
          Map.entry("omnitool", "㋦"));

  private static final Set<String> ALL_GLYPHS;

  static {
    var glyphs = new java.util.HashSet<String>();
    glyphs.addAll(RARITY_GLYPHS.values());
    glyphs.addAll(TYPE_GLYPHS.values());
    ALL_GLYPHS = Set.copyOf(glyphs);
  }

  private ItemTagHelper() {}

  /**
   * Returns the raw glyph character for the given rarity key, suitable for embedding in legacy
   * colour strings.
   *
   * @param rarity the rarity key (e.g. "common", "legendary") — case-insensitive
   * @return the single-character glyph string, or null if the rarity is unknown
   */
  @Nullable
  public static String getRarityGlyph(String rarity) {
    if (rarity == null) return null;
    return RARITY_GLYPHS.get(rarity.toLowerCase());
  }

  /**
   * Returns the raw glyph character for the given type key, suitable for embedding in legacy colour
   * strings.
   *
   * @param type the type key (e.g. "weapon", "other") — case-insensitive
   * @return the single-character glyph string, or null if the type is unknown
   */
  @Nullable
  public static String getTypeGlyph(String type) {
    if (type == null) return null;
    return TYPE_GLYPHS.get(type.toLowerCase());
  }

  /**
   * Returns the rarity glyph as a white, non-italic Adventure Component.
   *
   * @param rarity the rarity key (e.g. "common", "rare")
   * @return the glyph Component, or null if the rarity is unknown
   */
  @Nullable
  public static Component getRarityTag(String rarity) {
    if (rarity == null) {
      LOGGER.warning("[ItemTagHelper] getRarityTag called with null rarity");
      return null;
    }
    String glyph = RARITY_GLYPHS.get(rarity.toLowerCase());
    if (glyph == null) {
      LOGGER.warning("[ItemTagHelper] Unknown rarity: " + rarity);
      return null;
    }
    return Component.text(glyph)
        .color(NamedTextColor.WHITE)
        .decoration(TextDecoration.ITALIC, false);
  }

  /**
   * Returns the type glyph as a white, non-italic Adventure Component.
   *
   * @param type the type key (e.g. "weapon", "armor")
   * @return the glyph Component, or null if the type is unknown
   */
  @Nullable
  public static Component getTypeTag(String type) {
    if (type == null) {
      LOGGER.warning("[ItemTagHelper] getTypeTag called with null type");
      return null;
    }
    String glyph = TYPE_GLYPHS.get(type.toLowerCase());
    if (glyph == null) {
      LOGGER.warning("[ItemTagHelper] Unknown type: " + type);
      return null;
    }
    return Component.text(glyph)
        .color(NamedTextColor.WHITE)
        .decoration(TextDecoration.ITALIC, false);
  }

  /**
   * Prepends a {@code [rarityGlyph][typeGlyph]} tag line to the item lore, replacing any existing
   * tag line (detected by the presence of a known glyph in the first lore line).
   *
   * @param item the item to modify
   * @param rarity the rarity key
   * @param type the type key
   * @return the modified item, or the original item unchanged on invalid input
   */
  @Nullable
  public static ItemStack applyTags(@Nullable ItemStack item, String rarity, String type) {
    if (item == null) return null;

    Component rarityTag = getRarityTag(rarity);
    Component typeTag = getTypeTag(type);
    if (rarityTag == null || typeTag == null) return item;

    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    List<Component> existing = meta.lore();
    List<Component> lore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();

    if (!lore.isEmpty() && isTagLine(lore.get(0))) {
      lore.remove(0);
    }

    lore.add(0, rarityTag.append(typeTag));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  /**
   * Sets the {@code tooltip_style} item component to the rarity's matching resourcepack style key.
   *
   * @param item the item to modify
   * @param rarity the rarity key (must match a resourcepack tooltip_style name exactly)
   * @return the modified item, or the original item unchanged on invalid input
   */
  @Nullable
  public static ItemStack applyTooltipStyle(@Nullable ItemStack item, String rarity) {
    if (item == null) return null;
    if (rarity == null || !RARITY_GLYPHS.containsKey(rarity.toLowerCase())) {
      LOGGER.warning("[ItemTagHelper] applyTooltipStyle: unknown rarity: " + rarity);
      return item;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.setTooltipStyle(NamespacedKey.minecraft(rarity.toLowerCase()));
    item.setItemMeta(meta);
    return item;
  }

  /**
   * Convenience method that applies both the glyph tag line and tooltip style in one call.
   *
   * @param item the item to modify
   * @param rarity the rarity key
   * @param type the type key
   * @return the modified item
   */
  @Nullable
  public static ItemStack applyAll(@Nullable ItemStack item, String rarity, String type) {
    ItemStack tagged = applyTags(item, rarity, type);
    return applyTooltipStyle(tagged, rarity);
  }

  /** Returns true if the lore line contains any known glyph character. */
  private static boolean isTagLine(Component line) {
    String plain = PlainTextComponentSerializer.plainText().serialize(line);
    for (String glyph : ALL_GLYPHS) {
      if (plain.contains(glyph)) return true;
    }
    return false;
  }
}
