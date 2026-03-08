package org.benski.evolv3.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Factory and identifier for the Ancient Resin custom item.
 * Dropped from logs in the Log Mine; used to repair pickaxes.
 */
public final class AncientResin
{
    /** PDC key used to tag and identify Ancient Resin items. */
    public static final NamespacedKey KEY = new NamespacedKey("evolv3", "ancient_resin");

    private static final TextColor AMBER  = TextColor.fromHexString("#E8890A");
    private static final TextColor WHITE  = TextColor.fromHexString("#FFFFFF");
    private static final TextColor GRAY   = TextColor.fromHexString("#AAAAAA");

    private AncientResin() {}

    /**
     * Creates an Ancient Resin ItemStack with the given amount.
     */
    public static ItemStack create(int amount)
    {
        ItemStack item = new ItemStack(Material.HONEYCOMB, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("\u2B22 ", AMBER)
                .append(Component.text("Ancient Resin", WHITE).decorate(TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.empty(),
                Component.text(" A hardened amber sap drawn", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(" from the heartwood of ancient logs.", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(" Use: ", WHITE).decorate(TextDecoration.BOLD)
                        .append(Component.text("Repair pickaxes", GRAY).decoration(TextDecoration.BOLD, false))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty()
        ));

        Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        if (unbreaking != null) {
            meta.addEnchant(unbreaking, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns true if the given ItemStack is an Ancient Resin item.
     */
    public static boolean isAncientResin(ItemStack item)
    {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
