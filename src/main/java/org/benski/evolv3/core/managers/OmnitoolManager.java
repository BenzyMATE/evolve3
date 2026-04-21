package org.benski.evolv3.core.managers;

import lombok.Getter;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Omnitool ability — temporarily transforming a held pickaxe into a
 * same-tier axe or sword while the player is inside a designated WorldGuard region.
 *
 * <p>The virtual axe/sword is tagged with {@link #OMNITOOL_ITEM_KEY} so it can be
 * identified and removed cleanly when the player leaves the region or disconnects.</p>
 */
public class OmnitoolManager
{
    @Getter
    private static final OmnitoolManager instance = new OmnitoolManager();

    public enum Form { AXE, SWORD }

    /** PDC tag placed on the virtual axe/sword item for identification. */
    public static final NamespacedKey OMNITOOL_ITEM_KEY = new NamespacedKey("evolv3", "omnitool_form");

    private static final Map<Material, Material> PICKAXE_TO_AXE   = new EnumMap<>(Material.class);
    private static final Map<Material, Material> PICKAXE_TO_SWORD = new EnumMap<>(Material.class);

    static {
        PICKAXE_TO_AXE.put(Material.WOODEN_PICKAXE,    Material.WOODEN_AXE);
        PICKAXE_TO_AXE.put(Material.STONE_PICKAXE,     Material.STONE_AXE);
        PICKAXE_TO_AXE.put(Material.IRON_PICKAXE,      Material.IRON_AXE);
        PICKAXE_TO_AXE.put(Material.GOLDEN_PICKAXE,    Material.GOLDEN_AXE);
        PICKAXE_TO_AXE.put(Material.DIAMOND_PICKAXE,   Material.DIAMOND_AXE);
        PICKAXE_TO_AXE.put(Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE);

        PICKAXE_TO_SWORD.put(Material.WOODEN_PICKAXE,    Material.WOODEN_SWORD);
        PICKAXE_TO_SWORD.put(Material.STONE_PICKAXE,     Material.STONE_SWORD);
        PICKAXE_TO_SWORD.put(Material.IRON_PICKAXE,      Material.IRON_SWORD);
        PICKAXE_TO_SWORD.put(Material.GOLDEN_PICKAXE,    Material.GOLDEN_SWORD);
        PICKAXE_TO_SWORD.put(Material.DIAMOND_PICKAXE,   Material.DIAMOND_SWORD);
        PICKAXE_TO_SWORD.put(Material.NETHERITE_PICKAXE, Material.NETHERITE_SWORD);
    }

    private final Map<UUID, Form>      activeForms    = new HashMap<>();
    private final Map<UUID, ItemStack> storedPickaxes = new HashMap<>();

    private OmnitoolManager() {}

    /** Returns true if the item is any pickaxe material. */
    public boolean isPickaxe(ItemStack item)
    {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    public boolean isInOmnitoolMode(Player player) {
        return activeForms.containsKey(player.getUniqueId());
    }

    public Form getForm(Player player) {
        return activeForms.get(player.getUniqueId());
    }

    /** Returns the stored pickaxe item for a player currently in omnitool mode, or null. */
    public ItemStack getStoredPickaxe(Player player) {
        return storedPickaxes.get(player.getUniqueId());
    }

    /**
     * Transforms the player's held pickaxe into the corresponding axe or sword.
     * The display name is built from the Omnitool config template so that the
     * icon character and tool type are both correct.
     *
     * @return false if the held item is not a pickaxe or has no tier mapping
     */
    public boolean transformTo(Player player, Form form)
    {
        // Find the pickaxe anywhere in the inventory — not just main hand.
        // This way the axe/sword replaces the pickaxe in its current slot and
        // nothing else in the player's inventory is disturbed.
        int pickaxeSlot = findPickaxeSlot(player);
        if (pickaxeSlot == -1) return false;

        ItemStack held = player.getInventory().getItem(pickaxeSlot);
        Map<Material, Material> map = form == Form.AXE ? PICKAXE_TO_AXE : PICKAXE_TO_SWORD;
        Material target = map.get(held.getType());
        if (target == null) return false;

        storedPickaxes.put(player.getUniqueId(), held.clone());
        activeForms.put(player.getUniqueId(), form);

        ItemStack transformed = new ItemStack(target);
        ItemMeta origMeta = held.getItemMeta();
        ItemMeta newMeta  = transformed.getItemMeta();
        if (newMeta != null)
        {
            FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            String path        = form == Form.AXE ? "Omnitool.AxeName" : "Omnitool.SwordName";
            String defaultName = form == Form.AXE
                    ? "&#00FFFF\uD83D\uDD28 &#FFFFFF&l%evolv3_player_name% &#00FFFFAxe"
                    : "&#00FFFF\uD83D\uDDE1 &#FFFFFF&l%evolv3_player_name% &#00FFFFSword";
            String template  = config.getString(path, defaultName);
            String formatted = Utils.formatMessage(player, template);
            newMeta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize(formatted)
                    .decoration(TextDecoration.ITALIC, false));

            if (origMeta != null) {
                newMeta.lore(origMeta.lore());
                if (origMeta instanceof Damageable origDmg && newMeta instanceof Damageable newDmg) {
                    newDmg.setDamage(origDmg.getDamage());
                }
            }

            newMeta.getPersistentDataContainer()
                   .set(OMNITOOL_ITEM_KEY, PersistentDataType.STRING, form.name());
            transformed.setItemMeta(newMeta);
        }

        // Replace the pickaxe in its original slot — main hand is untouched if the
        // player has since switched to another item.
        player.getInventory().setItem(pickaxeSlot, transformed);
        return true;
    }

    /**
     * Reverts the player to their stored pickaxe, placing it back in whichever
     * inventory slot the omnitool item currently occupies.  No other slots are touched.
     */
    public void revertToPickaxe(Player player)
    {
        UUID uuid = player.getUniqueId();
        if (!activeForms.containsKey(uuid)) return;

        activeForms.remove(uuid);
        ItemStack original = storedPickaxes.remove(uuid);

        // Find the omnitool item's current slot and swap it for the original pickaxe.
        for (int slot = 0; slot < player.getInventory().getSize(); slot++)
        {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            if (!meta.getPersistentDataContainer().has(OMNITOOL_ITEM_KEY, PersistentDataType.STRING)) continue;

            if (original != null) {
                // Carry durability changes from the omnitool back to the pickaxe
                ItemMeta origMeta = original.getItemMeta();
                if (meta instanceof Damageable toolDmg && origMeta instanceof Damageable origDmg) {
                    origDmg.setDamage(toolDmg.getDamage());
                    original.setItemMeta(origMeta);
                }
                player.getInventory().setItem(slot, original);
            } else {
                player.getInventory().setItem(slot, null);
            }
            return;
        }

        // Omnitool not found in inventory (edge case) — just give the pickaxe back to main hand
        if (original != null) {
            player.getInventory().setItemInMainHand(original);
        }
    }

    /** Finds the first pickaxe slot in the player's inventory, or -1 if none. */
    private int findPickaxeSlot(Player player)
    {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++)
        {
            ItemStack item = player.getInventory().getItem(slot);
            if (isPickaxe(item)) return slot;
        }
        return -1;
    }

    /** Called on plugin disable — cleans up all active forms without attempting to restore items. */
    public void cleanup()
    {
        activeForms.clear();
        storedPickaxes.clear();
    }
}
