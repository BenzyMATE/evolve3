package org.benski.evolv3.core.builders;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Builder pattern for creating ItemStacks with a fluent API. Makes item creation more readable and
 * maintainable.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * ItemStack sword = new ItemBuilder(Material.DIAMOND_SWORD)
 *     .name("&6Legendary Sword")
 *     .lore("&7A powerful weapon", "&7from ancient times")
 *     .enchant(Enchantment.DAMAGE_ALL, 5)
 *     .enchant(Enchantment.FIRE_ASPECT, 2)
 *     .unbreakable(true)
 *     .build();
 * }</pre>
 */
public class ItemBuilder {
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private final ItemStack itemStack;
  private final ItemMeta itemMeta;

  /**
   * Creates a new ItemBuilder with the specified material.
   *
   * @param material the material for the item
   */
  public ItemBuilder(Material material) {
    this(new ItemStack(material));
  }

  /**
   * Creates a new ItemBuilder with the specified material and amount.
   *
   * @param material the material for the item
   * @param amount the stack size
   */
  public ItemBuilder(Material material, int amount) {
    this(new ItemStack(material, amount));
  }

  /**
   * Creates a new ItemBuilder from an existing ItemStack. Useful for modifying existing items.
   *
   * @param itemStack the base item stack
   */
  public ItemBuilder(ItemStack itemStack) {
    this.itemStack = itemStack.clone();
    this.itemMeta = this.itemStack.getItemMeta();
  }

  /**
   * Sets the display name of the item. Supports color codes with '&'.
   *
   * @param name the display name
   * @return this builder
   */
  public ItemBuilder name(String name) {
    if (itemMeta != null) {
      itemMeta.displayName(deserialize(colorize(name)));
    }
    return this;
  }

  /**
   * Sets the lore of the item. Supports color codes with '&'.
   *
   * @param lore the lore lines
   * @return this builder
   */
  public ItemBuilder lore(String... lore) {
    if (itemMeta != null) {
      List<Component> loreList = new ArrayList<>();
      for (String line : lore) {
        loreList.add(line.isEmpty() ? Component.empty() : deserialize(colorize(line)));
      }
      itemMeta.lore(loreList);
    }
    return this;
  }

  /**
   * Sets the lore of the item. Supports color codes with '&'.
   *
   * @param lore the lore lines
   * @return this builder
   */
  public ItemBuilder lore(List<String> lore) {
    if (itemMeta != null) {
      List<Component> loreList = new ArrayList<>();
      for (String line : lore) {
        loreList.add(line.isEmpty() ? Component.empty() : deserialize(colorize(line)));
      }
      itemMeta.lore(loreList);
    }
    return this;
  }

  /**
   * Adds a line to the existing lore. Supports color codes with '&'.
   *
   * @param line the lore line to add
   * @return this builder
   */
  public ItemBuilder addLoreLine(String line) {
    if (itemMeta != null) {
      List<Component> existing = itemMeta.lore();
      List<Component> lore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
      lore.add(line.isEmpty() ? Component.empty() : deserialize(colorize(line)));
      itemMeta.lore(lore);
    }
    return this;
  }

  /**
   * Adds multiple lines to the existing lore. Supports color codes with '&'.
   *
   * @param lines the lore lines to add
   * @return this builder
   */
  public ItemBuilder addLoreLines(String... lines) {
    if (itemMeta != null) {
      List<Component> existing = itemMeta.lore();
      List<Component> lore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
      for (String line : lines) {
        lore.add(line.isEmpty() ? Component.empty() : deserialize(colorize(line)));
      }
      itemMeta.lore(lore);
    }
    return this;
  }

  /**
   * Adds an enchantment to the item.
   *
   * @param enchantment the enchantment to add
   * @param level the enchantment level
   * @return this builder
   */
  public ItemBuilder enchant(Enchantment enchantment, int level) {
    if (itemMeta != null) {
      itemMeta.addEnchant(enchantment, level, true);
    }
    return this;
  }

  /**
   * Removes an enchantment from the item.
   *
   * @param enchantment the enchantment to remove
   * @return this builder
   */
  public ItemBuilder removeEnchant(Enchantment enchantment) {
    if (itemMeta != null) {
      itemMeta.removeEnchant(enchantment);
    }
    return this;
  }

  /**
   * Sets the item amount.
   *
   * @param amount the stack size
   * @return this builder
   */
  public ItemBuilder amount(int amount) {
    itemStack.setAmount(amount);
    return this;
  }

  /**
   * Sets the item's damage value (reduces durability). Higher values mean more damage/wear on the
   * item.
   *
   * @param damage the damage value to apply
   * @return this builder
   */
  public ItemBuilder damage(int damage) {
    if (itemMeta instanceof Damageable damageable) {
      damageable.setDamage(damage);
    }
    return this;
  }

  /**
   * Sets the item's damage to maximum, making it appear nearly broken. Useful for visual effects or
   * special item displays.
   *
   * @return this builder
   */
  public ItemBuilder maxDamage() {
    if (itemMeta instanceof Damageable damageable) {
      damageable.setDamage(itemStack.getType().getMaxDurability() - 1);
    }
    return this;
  }

  /**
   * Makes the item unbreakable.
   *
   * @param unbreakable true to make unbreakable
   * @return this builder
   */
  public ItemBuilder unbreakable(boolean unbreakable) {
    if (itemMeta != null) {
      itemMeta.setUnbreakable(unbreakable);
    }
    return this;
  }

  /**
   * Adds item flags to hide certain attributes.
   *
   * @param flags the flags to add
   * @return this builder
   */
  public ItemBuilder flags(ItemFlag... flags) {
    if (itemMeta != null) {
      itemMeta.addItemFlags(flags);
    }
    return this;
  }

  /**
   * Removes item flags.
   *
   * @param flags the flags to remove
   * @return this builder
   */
  public ItemBuilder removeFlags(ItemFlag... flags) {
    if (itemMeta != null) {
      itemMeta.removeItemFlags(flags);
    }
    return this;
  }

  /**
   * Hides all item attributes (enchantments, attributes, etc.).
   *
   * @return this builder
   */
  public ItemBuilder hideAll() {
    if (itemMeta != null) {
      itemMeta.addItemFlags(ItemFlag.values());
    }
    return this;
  }

  /**
   * Adds an enchantment glow effect to the item without showing enchantment text. Applies a hidden
   * enchantment to create the glint effect.
   *
   * @return this builder
   */
  public ItemBuilder glow() {
    if (itemMeta != null) {
      Enchantment unbreaking =
          RegistryAccess.registryAccess()
              .getRegistry(RegistryKey.ENCHANTMENT)
              .get(NamespacedKey.minecraft("unbreaking"));
      if (unbreaking != null) {
        itemMeta.addEnchant(unbreaking, 1, true);
      }
      itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    return this;
  }

  /**
   * Sets the custom model data (for resource packs).
   *
   * @param data the custom model data value
   * @return this builder
   */
  @SuppressWarnings("deprecation")
  public ItemBuilder customModelData(int data) {
    if (itemMeta != null) {
      itemMeta.setCustomModelData(data);
    }
    return this;
  }

  /**
   * Allows custom modification of the ItemMeta. Useful for version-specific or complex meta
   * operations.
   *
   * @param metaConsumer consumer to modify the meta
   * @return this builder
   */
  public ItemBuilder meta(Consumer<ItemMeta> metaConsumer) {
    if (itemMeta != null) {
      metaConsumer.accept(itemMeta);
    }
    return this;
  }

  /**
   * Provides access to the PersistentDataContainer for NBT storage.
   *
   * @param pdcConsumer consumer to modify the PDC
   * @return this builder
   */
  public ItemBuilder pdc(Consumer<PersistentDataContainer> pdcConsumer) {
    if (itemMeta != null) {
      pdcConsumer.accept(itemMeta.getPersistentDataContainer());
    }
    return this;
  }

  /**
   * Builds and returns the final ItemStack.
   *
   * @return the constructed ItemStack
   */
  public ItemStack build() {
    if (itemMeta != null) {
      itemStack.setItemMeta(itemMeta);
    }
    return itemStack;
  }

  /**
   * Utility method to colorize strings with '&' color codes.
   *
   * @param text the text to colorize
   * @return the colorized text
   */
  private String colorize(String text) {
    text = org.benski.evolv3.core.helpers.FontUtils.toSmallCaps(text);
    text = net.matrixcreations.libraries.MatrixColorAPI.process(text);
    return text.replace('&', '§');
  }

  private Component deserialize(String text) {
    return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
  }

  /**
   * Creates a new ItemBuilder instance (alternative to constructor).
   *
   * @param material the material
   * @return a new ItemBuilder
   */
  public static ItemBuilder of(Material material) {
    return new ItemBuilder(material);
  }

  /**
   * Creates a new ItemBuilder instance with amount (alternative to constructor).
   *
   * @param material the material
   * @param amount the stack size
   * @return a new ItemBuilder
   */
  public static ItemBuilder of(Material material, int amount) {
    return new ItemBuilder(material, amount);
  }

  /**
   * Creates a new ItemBuilder from an existing ItemStack (alternative to constructor).
   *
   * @param itemStack the base item
   * @return a new ItemBuilder
   */
  public static ItemBuilder from(ItemStack itemStack) {
    return new ItemBuilder(itemStack);
  }
}
