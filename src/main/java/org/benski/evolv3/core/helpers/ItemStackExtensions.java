package org.benski.evolv3.core.helpers;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension methods for ItemStack to handle persistent data operations.
 * Provides a cleaner API for working with ItemStack NBT data.
 */
public class ItemStackExtensions
{
    /**
     * Sets persistent data onto an ItemStack.
     *
     * @param item The ItemStack to modify
     * @param key The key to set in the item's persistent data container
     * @param value The value associated with the key
     */
    public static void setData(ItemStack item, String key, String value)
    {
        if (item == null || key == null || value == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey namespacedKey = new NamespacedKey(Main.getInstance(), key);
        container.set(namespacedKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    /**
     * Retrieves persistent data from an ItemStack using a key.
     *
     * @param item The ItemStack to inspect
     * @param key The key to retrieve from the item's persistent data container
     * @return The value associated with the key, or an empty string if not found
     */
    public static String getData(ItemStack item, String key)
    {
        if (item == null || key == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey namespacedKey = new NamespacedKey(Main.getInstance(), key);
        return container.getOrDefault(namespacedKey, PersistentDataType.STRING, "");
    }

    /**
     * Gets an enchantment from the registry by its namespaced key.
     *
     * @param namespace The namespace (e.g., "evolv3", "minecraft")
     * @param key The enchantment key (e.g., "explosive", "sharpness")
     * @return The enchantment, or null if not found
     */
    @Nullable
    public static Enchantment getEnchantmentByKey(String namespace, String key) {
        TypedKey<Enchantment> enchantKey = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key(namespace, key));
        return RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
            .get(enchantKey);
    }

    /**
     * Gets a custom Evolv3 enchantment from the registry.
     * Shorthand for getEnchantmentByKey("evolv3", key).
     *
     * @param key The enchantment key (e.g., "explosive")
     * @return The enchantment, or null if not found
     */
    @Nullable
    public static Enchantment getCustomEnchantment(String key) {
        return getEnchantmentByKey("evolv3", key);
    }

    /**
     * Adds an enchantment to an ItemStack by its namespaced key.
     *
     * @param item The ItemStack to enchant
     * @param namespace The namespace (e.g., "evolv3", "minecraft")
     * @param key The enchantment key (e.g., "explosive", "sharpness")
     * @param level The enchantment level
     * @return true if the enchantment was added successfully, false otherwise
     */
    public static boolean addEnchantmentByKey(ItemStack item, String namespace, String key, int level) {
        if (item == null) return false;
        Enchantment enchant = getEnchantmentByKey(namespace, key);
        if (enchant == null) return false;
        item.addEnchantment(enchant, level);
        return true;
    }

    /**
     * Adds a custom Evolv3 enchantment to an ItemStack.
     * Shorthand for addEnchantmentByKey(item, "evolv3", key, level).
     *
     * @param item The ItemStack to enchant
     * @param key The enchantment key (e.g., "explosive")
     * @param level The enchantment level
     * @return true if the enchantment was added successfully, false otherwise
     */
    public static boolean addCustomEnchantment(ItemStack item, String key, int level) {
        return addEnchantmentByKey(item, "evolv3", key, level);
    }

    /**
     * Gets the level of an enchantment on an ItemStack by its namespaced key.
     *
     * @param item The ItemStack to check
     * @param namespace The namespace (e.g., "evolv3", "minecraft")
     * @param key The enchantment key (e.g., "explosive", "sharpness")
     * @return The enchantment level, or 0 if not present
     */
    public static int getEnchantmentLevel(ItemStack item, String namespace, String key) {
        if (item == null) return 0;
        Enchantment enchant = getEnchantmentByKey(namespace, key);
        if (enchant == null) return 0;
        return item.getEnchantmentLevel(enchant);
    }

    /**
     * Gets the level of a custom Evolv3 enchantment on an ItemStack.
     * Shorthand for getEnchantmentLevel(item, "evolv3", key).
     *
     * @param item The ItemStack to check
     * @param key The enchantment key (e.g., "explosive")
     * @return The enchantment level, or 0 if not present
     */
    public static int getCustomEnchantmentLevel(ItemStack item, String key) {
        return getEnchantmentLevel(item, "evolv3", key);
    }

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    /**
     * Updates the display name and lore of an ItemStack using the pickaxe config.
     * Delegates to {@link org.benski.evolv3.core.managers.PickaxeManager#updateLore} so that
     * the enchant list is rendered as separate lore lines (not a single PAPI-joined string).
     *
     * @param item    The ItemStack to update
     * @param profile The pickaxe profile containing the data
     */
    public static void updateLore(ItemStack item, PickaxeProfile profile) {
        if (item == null || item.getType() == Material.AIR || profile == null) return;
        org.benski.evolv3.core.managers.PickaxeManager.getInstance().updateLore(profile.getOwner(), item);
    }

    /**
     * Updates the lore of an ItemStack using a custom lore template from config.
     * Supports PlaceholderAPI placeholders and color codes.
     *
     * @param item The ItemStack to update
     * @param profile The pickaxe profile containing the data
     * @param configPath The config path to the lore list
     */
    public static void updateLore(ItemStack item, PickaxeProfile profile, String configPath)
    {
        if (item == null || item.getType() == Material.AIR || profile == null) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.DEFAULT);
        List<String> loreTemplate = config.getStringList(configPath);

        if (loreTemplate.isEmpty()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> formattedLore = new ArrayList<>();
        for (String line : loreTemplate) {
            String formatted = Utils.formatMessage(profile.getOwner(), line);
            formattedLore.add(SERIALIZER.deserialize(formatted).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(formattedLore);
        item.setItemMeta(meta);
    }
}
