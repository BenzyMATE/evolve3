package org.benski.evolv3.items;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.helpers.FontUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Factory and identifier for the Ancient Resin custom item. Dropped from logs in the Log Mine; used
 * to repair pickaxes.
 */
public final class AncientResin {
    /**
     * PDC key used to tag and identify Ancient Resin items.
     */
    public static final NamespacedKey KEY = new NamespacedKey("evolv3", "ancient_resin");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /**
     * Creates a new AncientResin instance.
     */
    private AncientResin() {
    }

    /**
     * Ds.
     */
    private static Component ds(String message) {
        return LEGACY
                .deserialize(MatrixColorAPI.process(FontUtils.toSmallCaps(message)))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Creates an Ancient Resin ItemStack with the given amount.
     */
    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.HONEYCOMB, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(ds("&#E8890A⬢ &#FFFFFF&lAncient Resin"));
        meta.lore(
                List.of(
                        Component.empty(),
                        ds(" &#AAAAAA A hardened amber sap drawn"),
                        ds(" &#AAAAAA from the heartwood of ancient logs."),
                        Component.empty(),
                        ds(" &#FFFFFF&lUse: &#AAAAAA Repair pickaxes"),
                        Component.empty()));
        Enchantment unbreaking =
                RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT)
                        .get(NamespacedKey.minecraft("unbreaking"));
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
    public static boolean isAncientResin(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
