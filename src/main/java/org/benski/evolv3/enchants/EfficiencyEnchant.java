package org.benski.evolv3.enchants;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Utility class for the Efficiency custom enchantment.
 * This is a passive enchantment that increases mining speed.
 *
 * <p>Unlike active enchants, Efficiency doesn't trigger on events.
 * Instead, it applies an AttributeModifier to the pickaxe that
 * increases BLOCK_BREAK_SPEED.</p>
 *
 * <p>Speed bonus formula: level * speedPerLevel</p>
 * <ul>
 *   <li>speedPerLevel default: 0.08 (8% per level)</li>
 *   <li>Level 1: 8% speed increase</li>
 *   <li>Level 50: 400% speed increase</li>
 *   <li>Level 100: 800% speed increase</li>
 * </ul>
 *
 * <p>This class is registered as a Listener but doesn't handle events.
 * It provides static utility methods for applying speed modifiers.</p>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class EfficiencyEnchant implements Listener
{
    private static final String MODIFIER_KEY = "evolv3_efficiency_speed";
    private static final UUID MODIFIER_UUID = UUID.nameUUIDFromBytes(MODIFIER_KEY.getBytes());

    private final FileConfiguration enchantsConfig;
    private final JavaPlugin plugin;

    public EfficiencyEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.enchantsConfig = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
    }

    /**
     * Applies the efficiency speed modifier to an item's meta, accounting for any
     * socketed Evolution Stones that modify the base calculation.
     *
     * <ul>
     *   <li><b>Common</b>    stone: multiplies final speed bonus by 1.5 (+50%)</li>
     *   <li><b>Legendary</b> stone: doubles {@code speedPerLevel} before multiplying by level</li>
     * </ul>
     *
     * @param meta        the ItemMeta to modify
     * @param level       the efficiency enchantment level
     * @param enchantData the player's EnchantData (may be {@code null} — no stone effects applied)
     * @return true if the modifier was applied successfully
     */
    public static boolean applySpeedModifier(ItemMeta meta, int level, @Nullable EnchantData enchantData)
    {
        if (meta == null || level <= 0) return false;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
        double speedPerLevel = config.getDouble(ConfigPaths.Enchants.Efficiency.SPEED_PER_LEVEL, 0.08);

        // Legendary: each level grants 2× the normal speed bonus
        if (enchantData != null && enchantData.hasStone("Legendary")) {
            speedPerLevel *= 2.0;
        }

        double speedBonus = level * speedPerLevel;

        // Common: flat +1.0 bonus (displayed as +100%)
        if (enchantData != null && enchantData.hasStone("Common")) {
            speedBonus += 1.0;
        }

        removeSpeedModifier(meta);

        meta.addAttributeModifier(
                Attribute.BLOCK_BREAK_SPEED,
                new AttributeModifier(
                        Objects.requireNonNull(NamespacedKey.fromString(MODIFIER_KEY)),
                        speedBonus,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                        EquipmentSlotGroup.MAINHAND
                )
        );
        return true;
    }

    /**
     * Applies the efficiency speed modifier without stone effects.
     *
     * @param meta  the ItemMeta to modify
     * @param level the efficiency enchantment level
     * @return true if the modifier was applied successfully
     */
    public static boolean applySpeedModifier(ItemMeta meta, int level)
    {
        return applySpeedModifier(meta, level, null);
    }

    /**
     * Applies the efficiency speed modifier to an ItemStack.
     *
     * @param item  the item to modify
     * @param level the efficiency enchantment level
     * @return true if the modifier was applied successfully
     */
    public static boolean applySpeedModifier(ItemStack item, int level)
    {
        if (item == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        boolean result = applySpeedModifier(meta, level);
        if (result) {
            item.setItemMeta(meta);
        }
        return result;
    }

    /**
     * Removes the efficiency speed modifier from an item's meta.
     *
     * @param meta the ItemMeta to modify
     */
    public static void removeSpeedModifier(ItemMeta meta)
    {
        if (meta == null || !meta.hasAttributeModifiers()) {
            return;
        }

        var modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers != null) {
            for (AttributeModifier modifier : modifiers) {
                if (modifier.getKey().getKey().equals(MODIFIER_KEY)) {
                    meta.removeAttributeModifier(Attribute.BLOCK_BREAK_SPEED, modifier);
                }
            }
        }
    }

    /**
     * Removes the efficiency speed modifier from an ItemStack.
     *
     * @param item the item to modify
     */
    public static void removeSpeedModifier(ItemStack item)
    {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        removeSpeedModifier(meta);
        item.setItemMeta(meta);
    }

    /**
     * Gets the current efficiency level from an item's attribute modifier.
     *
     * @param item the item to check
     * @return the efficiency level, or 0 if not present
     */
    public static int getAppliedLevel(ItemStack item)
    {
        if (item == null) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers()) {
            return 0;
        }

        var modifiers = meta.getAttributeModifiers(Attribute.BLOCK_BREAK_SPEED);
        if (modifiers == null) {
            return 0;
        }

        for (AttributeModifier modifier : modifiers) {
            if (modifier.getKey().getKey().equals(MODIFIER_KEY)) {
                FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
                double speedPerLevel = config.getDouble(ConfigPaths.Enchants.Efficiency.SPEED_PER_LEVEL, 0.08);
                return (int) Math.round(modifier.getAmount() / speedPerLevel);
            }
        }

        return 0;
    }
}
