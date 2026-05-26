package org.benski.evolv3.items;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Factory and identifier for Scroll items — physical vouchers that players can carry, trade, and
 * redeem for server rewards.
 *
 * <p>All display text is driven by {@code scrolls.yml}, with hardcoded fallbacks if the config is
 * unavailable. Interaction is handled by ScrollListener.
 */
public final class Scroll {
  public static final NamespacedKey TYPE_KEY = new NamespacedKey("evolv3", "scroll_type");
  public static final NamespacedKey AMOUNT_KEY = new NamespacedKey("evolv3", "scroll_amount");

  public static final String COINS = "COINS";
  public static final String ESSENCE = "ESSENCE";
  public static final String ATOMS = "ATOMS";
  public static final String PICKAXE_XP = "PICKAXE_XP";
  public static final String BACKPACK_SLOTS = "BACKPACK_SLOTS";

  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  /** Creates a new Scroll instance. */
  private Scroll() {}

  /** Creates a Coin Scroll ItemStack redeemable for the given coin amount. */
  public static ItemStack createCoins(int amount) {
    return buildFromConfig(COINS, amount);
  }

  /** Creates an Essence Scroll ItemStack redeemable for the given essence amount. */
  public static ItemStack createEssence(int amount) {
    return buildFromConfig(ESSENCE, amount);
  }

  /** Creates an Atom Scroll ItemStack redeemable for the given atom amount. */
  public static ItemStack createAtoms(int amount) {
    return buildFromConfig(ATOMS, amount);
  }

  /** Creates a Pickaxe XP Scroll ItemStack redeemable for the given XP amount. */
  public static ItemStack createPickaxeXP(int amount) {
    return buildFromConfig(PICKAXE_XP, amount);
  }

  /** Creates a Backpack Slot Scroll ItemStack redeemable for the given number of slots. */
  public static ItemStack createBackpackSlots(int amount) {
    return buildFromConfig(BACKPACK_SLOTS, amount);
  }

  /** Returns true if the given ItemStack is a Scroll. */
  public static boolean isScroll(ItemStack item) {
    return getType(item) != null;
  }

  /** Returns the scroll type string (e.g. {@code "COINS"}) or null if the item is not a scroll. */
  public static String getType(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return null;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return null;
    return meta.getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
  }

  /** Returns the reward amount encoded in the scroll's PDC, or 0 if absent. */
  public static int getAmount(ItemStack item) {
    if (item == null) return 0;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return 0;
    return meta.getPersistentDataContainer()
        .getOrDefault(AMOUNT_KEY, PersistentDataType.INTEGER, 0);
  }

  /** Creates a new scroll of the same type with an updated amount (used for combining). */
  public static ItemStack withAmount(ItemStack original, int newAmount) {
    String type = getType(original);
    if (type == null) return original;
    return switch (type) {
      case COINS -> createCoins(newAmount);
      case ESSENCE -> createEssence(newAmount);
      case ATOMS -> createAtoms(newAmount);
      case PICKAXE_XP -> createPickaxeXP(newAmount);
      case BACKPACK_SLOTS -> createBackpackSlots(newAmount);
      default -> original;
    };
  }

  /** Builds from config. */
  private static ItemStack buildFromConfig(String type, int amount) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.SCROLLS);
    String key = typeConfigKey(type);
    String amountStr = String.valueOf(amount);

    String name =
        (cfg != null)
            ? cfg.getString("scrolls." + key + ".name", fallbackName(type))
            : fallbackName(type);

    List<String> rawLore =
        (cfg != null && cfg.isList("scrolls." + key + ".lore"))
            ? cfg.getStringList("scrolls." + key + ".lore")
            : fallbackLore(type, amount);

    String[] loreLines =
        rawLore.stream()
            .map(l -> l.replace("{amount}", amountStr))
            .map(l -> l.isEmpty() ? null : l)
            .toArray(String[]::new);

    return build(name, type, amount, loreLines);
  }

  /** Type config key. */
  private static String typeConfigKey(String type) {
    return switch (type) {
      case COINS -> "coins";
      case ESSENCE -> "essence";
      case ATOMS -> "atoms";
      case PICKAXE_XP -> "pickaxe-xp";
      case BACKPACK_SLOTS -> "backpack-slots";
      default -> type.toLowerCase().replace("_", "-");
    };
  }

  /** Fallback name. */
  private static String fallbackName(String type) {
    return switch (type) {
      case COINS -> "&#FFD700&lCoin Scroll";
      case ESSENCE -> "&#B366D9&lEssence Scroll";
      case ATOMS -> "&#FFEE00&lAtom Scroll";
      case PICKAXE_XP -> "&#00FFFF&lPickaxe XP Scroll";
      case BACKPACK_SLOTS -> "&#00FFFF&lBackpack Scroll";
      default -> "&fScroll";
    };
  }

  /** Fallback lore. */
  private static List<String> fallbackLore(String type, int amount) {
    return switch (type) {
      case COINS ->
          List.of(
              "&#AAAAAA A redeemable voucher worth",
              "&#FFD700⛁ &#FFFFFF+" + amount + " &#FFD700coins.",
              "",
              "&#FFFFFF&lUse: &#AAAAAA Right-click to redeem.",
              "&#AAAAAA Drag onto another Coin Scroll to combine.");
      case ESSENCE ->
          List.of(
              "&#AAAAAA A redeemable voucher worth",
              "&#B366D9🔥 &#FFFFFF+" + amount + " &#B366D9essence.",
              "",
              "&#FFFFFF&lUse: &#AAAAAA Right-click to redeem.",
              "&#AAAAAA Drag onto another Essence Scroll to combine.");
      case ATOMS ->
          List.of(
              "&#AAAAAA A redeemable voucher worth",
              "&#FFEE00⚛ &#FFFFFF+" + amount + " &#FFEE00atoms.",
              "",
              "&#FFFFFF&lUse: &#AAAAAA Drag onto your pickaxe",
              "&#AAAAAA in your inventory to apply.",
              "&#AAAAAA Drag onto another Atom Scroll to combine.");
      case PICKAXE_XP ->
          List.of(
              "&#AAAAAA A redeemable voucher worth",
              "&#00FFFF⛏ &#FFFFFF+" + amount + " &#00FFFFpickaxe XP.",
              "",
              "&#FFFFFF&lUse: &#AAAAAA Drag onto a pickaxe",
              "&#AAAAAA in your inventory to apply.");
      case BACKPACK_SLOTS ->
          List.of(
              "&#AAAAAA A redeemable voucher worth",
              "&#00FFFF◎ &#FFFFFF+" + amount + " &#00FFFFbackpack slot(s).",
              "",
              "&#FFFFFF&lUse: &#AAAAAA Right-click to redeem.",
              "&#AAAAAA Drag onto another Backpack Scroll to combine.");
      default -> List.of("&#AAAAAA A redeemable voucher worth +" + amount + ".");
    };
  }

  /** Builds the result. */
  private static ItemStack build(String displayName, String type, int amount, String... loreLines) {
    ItemStack item = new ItemStack(Material.PAPER, 1);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    meta.displayName(ds(displayName));

    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());
    for (String line : loreLines) {
      lore.add(line == null ? Component.empty() : ds(" " + line));
    }
    lore.add(Component.empty());
    meta.lore(lore);

    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    meta.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type);
    meta.getPersistentDataContainer().set(AMOUNT_KEY, PersistentDataType.INTEGER, amount);
    item.setItemMeta(meta);
    return item;
  }

  /** Ds. */
  private static Component ds(String raw) {
    return LEGACY
        .deserialize(MatrixColorAPI.process(FontUtils.toSmallCaps(raw)))
        .decoration(TextDecoration.ITALIC, false);
  }
}
