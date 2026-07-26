package org.benski.evolv3.items;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.helpers.ItemTagHelper;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.enchant.StoneEffectRegistry;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Factory and identifier for Evolution Stone items.
 *
 * <p>Evolution Stones are earned when a pickaxe evolves. Each stone is pre-assigned to a specific
 * enchant and rarity — the item name reflects this (e.g. "Common Efficiency Stone"). Stones are
 * socketed via Shift+Right-Click on an enchant in the Pickaxe Menu, granting a passive bonus to
 * that enchant.
 *
 * <p>The rarity name and enchant name are encoded in the item's {@link PersistentDataContainer} for
 * reliable identification.
 */
public final class EvolutionStone {
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  /** PDC key storing the rarity name (e.g. "Common", "Legendary"). */
  public static final String PDC_RARITY_KEY = "stone_rarity";

  /** PDC key storing the target enchant display name (e.g. "Efficiency"). */
  public static final String PDC_ENCHANT_KEY = "stone_enchant";

  /** PDC key used to identify this item as an evolution stone. */
  public static final String PDC_TYPE_KEY = "item_type";

  /** Value stored under {@link #PDC_TYPE_KEY} for evolution stones. */
  public static final String TYPE_VALUE = "evolution_stone";

  private static final NamespacedKey KEY_TYPE = new NamespacedKey("evolv3", PDC_TYPE_KEY);
  private static final NamespacedKey KEY_RARITY = new NamespacedKey("evolv3", PDC_RARITY_KEY);
  private static final NamespacedKey KEY_ENCHANT = new NamespacedKey("evolv3", PDC_ENCHANT_KEY);

  /** Creates a new EvolutionStone instance. */
  private EvolutionStone() {}

  /**
   * Creates an Evolution Stone {@link ItemStack} for the given rarity and enchant.
   *
   * @param rarity the rarity of the stone
   * @param enchantDisplayName the enchant this stone is bound to (e.g. "Efficiency")
   * @return a new {@link ItemStack} representing the stone
   */
  public static ItemStack create(RarityConfig rarity, String enchantDisplayName) {
    String rarityKey = rarity.displayName().toLowerCase();
    String rarityGlyph = ItemTagHelper.getRarityGlyph(rarityKey);
    String rarityHex = String.format("%06X", rarity.textColor().value());

    var enchantOption = EnchantAnimationConfig.getOption(enchantDisplayName);
    var enchantRarity = enchantOption != null ? enchantOption.rarity() : null;
    String enchantRarityHex =
        enchantRarity != null ? String.format("%06X", enchantRarity.textColor().value()) : "CCCCCC";

    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.STONES);

    String stoneNameTemplate =
        cfg != null
            ? cfg.getString(ConfigPaths.Stones.Item.NAME, "&#{hex}◆ &l{stone_name}")
            : "&#{hex}◆ &l{stone_name}";
    String rarityLineTemplate =
        cfg != null
            ? cfg.getString(
                ConfigPaths.Stones.Item.RARITY_LINE, " &#{hex}&l{rarity} &#888888Evolution Stone")
            : " &#{hex}&l{rarity} &#888888Evolution Stone";
    String boundTemplate =
        cfg != null
            ? cfg.getString(
                ConfigPaths.Stones.Item.BOUND_TO, " &#888888Bound to &#FFFFFF&l{enchant}")
            : " &#888888Bound to &#FFFFFF&l{enchant}";
    String effectHeader =
        cfg != null
            ? cfg.getString(ConfigPaths.Stones.Item.EFFECT_HEADER, " &#FFFFFF&lEffect")
            : " &#FFFFFF&lEffect";
    String effectTemplate =
        cfg != null
            ? cfg.getString(ConfigPaths.Stones.Item.EFFECT_LINE, " &8| &#CCCCCC{effect}")
            : " &8| &#CCCCCC{effect}";
    List<String> applyHint =
        cfg != null
            ? cfg.getStringList(ConfigPaths.Stones.Item.APPLY_HINT)
            : List.of(
                " &#555555Apply via the Pickaxe Menu.",
                " &#555555Shift+Right-Click an enchant",
                " &#555555slot to socket this stone.");

    String internalName = enchantDisplayName.toLowerCase();
    String stoneName = StoneEffectRegistry.getName(internalName, rarity.displayName());
    if (stoneName == null) stoneName = rarity.displayName() + " Evolution Stone";
    String effectText = StoneEffectRegistry.getEffect(internalName, rarity.displayName());

    ItemStack item = new ItemStack(Material.NETHER_STAR);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    meta.displayName(
        ds(stoneNameTemplate.replace("{hex}", rarityHex).replace("{stone_name}", stoneName)));

    List<Component> lore = new ArrayList<>();
    String rarityLabel = (rarityGlyph != null ? rarityGlyph + " " : "") + rarity.displayName();
    lore.add(ds(rarityLineTemplate.replace("{hex}", rarityHex).replace("{rarity}", rarityLabel)));
    lore.add(
        ds(
            boundTemplate
                .replace("{hex}", rarityHex)
                .replace("{enchant_hex}", enchantRarityHex)
                .replace("{enchant}", enchantDisplayName)));
    lore.add(Component.empty());
    lore.add(ds(effectHeader));
    for (String chunk : org.benski.evolv3.core.helpers.Utils.wrapWords(effectText, 5)) {
      lore.add(ds(effectTemplate.replace("{effect}", chunk)));
    }
    lore.add(Component.empty());
    for (String hint : applyHint) {
      lore.add(ds(hint));
    }
    meta.lore(lore);

    meta.getPersistentDataContainer()
        .set(KEY_RARITY, PersistentDataType.STRING, rarity.displayName());
    meta.getPersistentDataContainer()
        .set(KEY_ENCHANT, PersistentDataType.STRING, enchantDisplayName);
    meta.getPersistentDataContainer().set(KEY_TYPE, PersistentDataType.STRING, TYPE_VALUE);

    Enchantment glow =
        RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
            .get(NamespacedKey.minecraft("unbreaking"));
    if (glow != null) {
      meta.addEnchant(glow, 1, true);
    }
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

    item.setItemMeta(meta);
    ItemTagHelper.applyTooltipStyle(item, rarityKey);
    return item;
  }

  /**
   * Returns {@code true} if the given item is an Evolution Stone (identified via PDC).
   *
   * @param item the item to check (may be null)
   * @return {@code true} if the item is an Evolution Stone
   */
  public static boolean isEvolutionStone(ItemStack item) {
    if (item == null) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;
    return TYPE_VALUE.equals(
        meta.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING));
  }

  /**
   * Returns the rarity name encoded in an Evolution Stone's PDC, or {@code null} if the item is not
   * an Evolution Stone or has no rarity tag.
   *
   * @param item the item to inspect
   * @return the rarity display name (e.g. "Common"), or {@code null}
   */
  public static String getRarityName(ItemStack item) {
    if (item == null) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return null;
    return meta.getPersistentDataContainer().get(KEY_RARITY, PersistentDataType.STRING);
  }

  /**
   * Returns the enchant display name encoded in an Evolution Stone's PDC, or {@code null} if the
   * item is not a stone or has no enchant tag.
   *
   * @param item the item to inspect
   * @return the enchant display name (e.g. "Efficiency"), or {@code null}
   */
  public static String getEnchantName(ItemStack item) {
    if (item == null) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return null;
    return meta.getPersistentDataContainer().get(KEY_ENCHANT, PersistentDataType.STRING);
  }

  /**
   * Deserializes a legacy color string through the full formatting pipeline to a non-italic
   * component.
   */
  private static Component ds(String message) {
    return LEGACY
        .deserialize(MatrixColorAPI.process(FontUtils.toSmallCaps(message)))
        .decoration(TextDecoration.ITALIC, false);
  }
}
