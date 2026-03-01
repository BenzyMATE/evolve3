package org.benski.evolv3.menus;

import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.managers.PickaxeManager;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.benski.evolv3.core.helpers.Utils.formatMaterialName;
import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Menu for managing pickaxe storage slots.
 * Players unlock slots by spending wood via the purchase button,
 * then claim a pickaxe for each unlocked slot.
 *
 * <p>Each slot can be in one of five states:</p>
 * <ul>
 *   <li><b>LOCKED</b>   – slot not yet purchased (index &ge; pickaxeLimit)</li>
 *   <li><b>CLAIM</b>    – slot unlocked, no pickaxe claimed yet, no active pickaxe</li>
 *   <li><b>BLOCKED</b>  – slot unlocked, empty, but player already has an active pickaxe</li>
 *   <li><b>ACTIVE</b>   – pickaxe for this slot is currently in the player's inventory (LEAD)</li>
 *   <li><b>STORED</b>   – pickaxe for this slot is stored away (pickaxe item)</li>
 * </ul>
 *
 * <p>Layout (27 slots / 3 rows):</p>
 * <pre>
 *   Row 0 ( 0- 8): border
 *   Row 1 ( 9-17): border [sl0][sl1][sl2][sl3][sl4][sl5][sl6] border
 *   Row 2 (18-26): border, purchase button at slot 22, border
 * </pre>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class PickaxeLimitMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("pickaxe_limit");

    /**
     * Maximum pickaxe storage slots.
     * Configurable between 1 and 9 (PlayerProfile.storedPickaxes holds exactly 9 entries).
     * WOOD_TYPES must have at least MAX_SLOTS entries.
     * To change the cap: update this constant and add/remove WOOD_TYPES entries to match.
     */
    static final int MAX_SLOTS = 7;

    /** Inventory slot index for the purchase / unlock button. */
    private static final int PURCHASE_SLOT = 22;

    /**
     * GUI slot for each storage position.
     * Index maps directly to PlayerProfile.storedPickaxes index.
     */
    private static final int[] SLOT_POSITIONS = {10, 11, 12, 13, 14, 15, 16};

    /**
     * Log type required to unlock each slot.
     * WOOD_TYPES[i] is consumed when purchasing slot i.
     * Must contain at least MAX_SLOTS entries.
     */
    private static final Material[] WOOD_TYPES = {
            Material.OAK_LOG,
            Material.SPRUCE_LOG,
            Material.BIRCH_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG
    };

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final PlayerDataCenter playerDataCenter;
    private final PickaxeDataCenter pickaxeDataCenter;

    public PickaxeLimitMenu(JavaPlugin plugin)
    {
        super(plugin);
        this.playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        this.pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    }

    @Override
    protected String getMenuTitle(Player player)
    {
        return "&8Pickaxe Storage";
    }

    @Override
    protected int getMenuSize()
    {
        return 27;
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory)
    {
        PlayerProfile profile = playerDataCenter.getLocal(player);
        if (profile == null) return;

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, border);
        }

        boolean hasActive = playerHasRegisteredPickaxe(player);
        int unlockedSlots = profile.pickaxeLimit;

        for (int i = 0; i < MAX_SLOTS; i++) {
            int guiSlot = SLOT_POSITIONS[i];

            if (i >= unlockedSlots) {
                inventory.setItem(guiSlot, buildLockedSlot(player, i));
                continue;
            }

            String entry = getStoredEntry(profile, i);
            if (entry.isEmpty()) {
                // Unlocked slot with no pickaxe claimed
                inventory.setItem(guiSlot, hasActive ? buildBlockedSlot(player) : buildClaimSlot(player));
            } else {
                // Slot has a pickaxe — show lead if it's in inventory, pickaxe item if stored
                String pickaxeId = entry.split(":", 2)[0];
                if (pickaxeIsActive(player, pickaxeId)) {
                    inventory.setItem(guiSlot, buildActiveSlot(player, entry));
                } else {
                    inventory.setItem(guiSlot, buildStoredSlot(player, entry));
                }
            }
        }

        if (unlockedSlots < MAX_SLOTS) {
            inventory.setItem(PURCHASE_SLOT, buildPurchaseButton(player, profile, unlockedSlots));
        } else {
            inventory.setItem(PURCHASE_SLOT, buildMaxedButton(player));
        }
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event)
    {
        PlayerProfile profile = playerDataCenter.getLocal(player);
        if (profile == null) return true;

        int slotIndex = getSlotIndex(slot);
        if (slotIndex >= 0) {
            handleStorageSlotClick(player, profile, slotIndex);
            return true;
        }

        if (slot == PURCHASE_SLOT) {
            handlePurchaseClick(player, profile);
            return true;
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Click handlers
    // ─────────────────────────────────────────────────────────────────────────

    private void handleStorageSlotClick(Player player, PlayerProfile profile, int slotIndex)
    {
        if (slotIndex >= profile.pickaxeLimit) {
            player.sendMessage(fmt(player, "&c&lSlot Locked &7— Purchase it first."));
            return;
        }

        String entry = getStoredEntry(profile, slotIndex);

        if (entry.isEmpty()) {
            if (playerHasRegisteredPickaxe(player)) {
                player.sendMessage(fmt(player, "&c&lPickaxe Active &7— Deposit your current pickaxe before claiming another."));
            } else {
                handleClaim(player, profile, slotIndex);
            }
            return;
        }

        String pickaxeId = entry.split(":", 2)[0];
        if (pickaxeIsActive(player, pickaxeId)) {
            handleDeposit(player, pickaxeId);
        } else {
            handleWithdraw(player, entry);
        }
    }

    /**
     * Deposits a specific pickaxe back into storage.
     * Finds it in the player's inventory by ID and removes it.
     * The slot entry is not modified — active/stored state is inventory-derived.
     */
    private void handleDeposit(Player player, String pickaxeId)
    {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.getType().name().contains("PICKAXE")) continue;
            String id = item.getData("id");
            if (!pickaxeId.equals(id)) continue;

            player.getInventory().setItem(i, new ItemStack(Material.AIR));
            player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);
            player.sendMessage(fmt(player, "&a&l[Deposited] &ePickaxe stored."));
            refresh(player);
            return;
        }
        player.sendMessage(fmt(player, "&c&lNot Found &7— Could not find that pickaxe in your inventory."));
    }

    /**
     * Withdraws a stored pickaxe into the player's inventory.
     * The slot entry is not modified — active/stored state is inventory-derived.
     */
    private void handleWithdraw(Player player, String entry)
    {
        if (playerHasRegisteredPickaxe(player)) {
            player.sendMessage(fmt(player, "&c&lPickaxe Active &7— Deposit your current pickaxe before withdrawing another."));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(fmt(player, "&c&lInventory Full &7— Clear space before withdrawing."));
            return;
        }

        String[] parts = entry.split(":", 2);
        String pickaxeId = parts[0];
        Material material = parts.length > 1 ? parseMaterial(parts[1]) : Material.WOODEN_PICKAXE;

        ItemStack pickaxeItem = new ItemStack(material);
        pickaxeItem.setData("id", pickaxeId);

        player.getInventory().addItem(pickaxeItem);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.2f);
        player.sendMessage(formatMessage(player, "&a&l[Withdrawn] &ePickaxe retrieved."));
        refresh(player);
    }

    /**
     * Claims a new pickaxe for the given slot.
     * Force-registers the pickaxe so stats are available immediately.
     */
    private void handleClaim(Player player, PlayerProfile profile, int slotIndex)
    {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(fmt(player, "&c&lInventory Full &7— Clear space before claiming."));
            return;
        }

        String pickaxeId = UUID.randomUUID().toString();
        Material material = Material.WOODEN_PICKAXE;

        // Create the item with the registered ID embedded
        ItemStack pickaxeItem = new ItemStack(material);
        pickaxeItem.setData("id", pickaxeId);

        // Create the pickaxe profile with default values and put it in cache
        PickaxeProfile pickProfile = PickaxeProfile.createDefault(player, pickaxeId);
        pickaxeDataCenter.getAll().put(pickaxeId, pickProfile);
        pickProfile.save(null);

        // Record the slot ownership (permanent — not cleared on withdraw/deposit)
        setStoredEntry(profile, slotIndex, pickaxeId + ":" + material.name());
        profile.save(null);

        player.getInventory().addItem(pickaxeItem);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.sendMessage(formatMessage(player, "&a&l[Claimed] &eYour new pickaxe is ready."));
        refresh(player);
    }

    private void handlePurchaseClick(Player player, PlayerProfile profile)
    {
        int nextSlotIndex = profile.pickaxeLimit;
        if (nextSlotIndex >= MAX_SLOTS) {
            player.sendMessage(fmt(player, "&aAll slots are already unlocked!"));
            return;
        }

        Material requiredWood = WOOD_TYPES[nextSlotIndex];
        int requiredAmount = getWoodCostForSlot(nextSlotIndex);

        if (countMaterialInInventory(player, requiredWood) < requiredAmount) {
            player.sendMessage(fmt(player, "&c&lInsufficient Resources &7— Need &f" + requiredAmount
                    + " &7" + formatMaterialName(requiredWood) + "&7."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        removeMaterialFromInventory(player, requiredWood, requiredAmount);
        profile.increasePickaxeLimit();
        profile.save(null);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.sendMessage(fmt(player, "&a&l[Unlocked] &eSlot " + profile.pickaxeLimit + " is now available."));
        refresh(player);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Item builders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Yellow pane — slot is unlocked and available to claim a new pickaxe.
     */
    private ItemStack buildClaimSlot(Player player)
    {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(fmt(player, " &#FFFFFF&lDescription"));
        lore.add(fmt(player, " &8| &#CCCCCCThis slot is ready for a pickaxe."));
        lore.add(fmt(player, " &8| &#CCCCCCClick to claim your new pickaxe."));
        lore.add("");
        lore.add(fmt(player, " &8| &aLeft-Click &#AAAAAAto claim"));
        lore.add("");

        return new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
                .name(fmt(player, "&#00AFA3⛏ &#FFFFFF&lClaim Pickaxe"))
                .lore(lore)
                .build();
    }

    /**
     * Red pane — slot is unlocked but blocked because the player already has a pickaxe active.
     */
    private ItemStack buildBlockedSlot(Player player)
    {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(fmt(player, " &#FFFFFF&lBlocked"));
        lore.add(fmt(player, " &8| &#CCCCCCYou already have an active pickaxe."));
        lore.add(fmt(player, " &8| &#CCCCCCDeposit it first to claim this slot."));
        lore.add("");

        return new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(fmt(player, "&#FF4444⬡ &#FFFFFF&lSlot Blocked"))
                .lore(lore)
                .build();
    }

    /**
     * LEAD — the pickaxe for this slot is currently in the player's inventory (active).
     * Clicking deposits it back into storage.
     */
    private ItemStack buildActiveSlot(Player player, String entry)
    {
        String[] parts = entry.split(":", 2);
        String pickaxeId = parts[0];
        PickaxeProfile pickProfile = pickaxeDataCenter.getAll().get(pickaxeId);

        ItemStack item = new ItemStack(Material.LEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(SERIALIZER.deserialize(fmt(player, "&#00AFA3⛏ &#FFFFFF&lPickaxe &#00FF88(Active)"))
                .decoration(TextDecoration.ITALIC, false));

        if (pickProfile != null) {
            List<String> footer = List.of("", " &8| &aLeft-Click &#AAAAAAto deposit", "");
            meta.lore(PickaxeManager.getInstance().buildLoreComponents(player, pickProfile, footer));
        } else {
            meta.lore(List.of(
                    Component.empty(),
                    SERIALIZER.deserialize(fmt(player, " &8| &#CCCCCCPickaxe is currently active in your inventory."))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    SERIALIZER.deserialize(fmt(player, " &8| &aLeft-Click &#AAAAAAto deposit"))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty()
            ));
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Pickaxe item — the pickaxe for this slot is stored away.
     * Clicking withdraws it into the player's inventory.
     */
    private ItemStack buildStoredSlot(Player player, String entry)
    {
        String[] parts = entry.split(":", 2);
        String pickaxeId = parts[0];
        Material material = parts.length > 1 ? parseMaterial(parts[1]) : Material.WOODEN_PICKAXE;
        PickaxeProfile pickProfile = pickaxeDataCenter.getAll().get(pickaxeId);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (pickProfile != null) {
            FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            String nameTemplate = config.getString(ConfigPaths.Default.Pickaxe.NAME, "&#00FFFF⛏ Pickaxe");
            String resolvedName = nameTemplate.replace("%evolv3_player_name%", player.getName());
            meta.displayName(SERIALIZER.deserialize(fmt(player, resolvedName))
                    .decoration(TextDecoration.ITALIC, false));

            List<String> footer = List.of("", " &8| &aLeft-Click &#AAAAAAto withdraw", "");
            meta.lore(PickaxeManager.getInstance().buildLoreComponents(player, pickProfile, footer));
        } else {
            meta.displayName(SERIALIZER.deserialize(fmt(player, "&#00FFFF⛏ &#FFFFFF&lStored Pickaxe"))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty(),
                    SERIALIZER.deserialize(fmt(player, " &8| &aLeft-Click &#AAAAAAto withdraw"))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty()
            ));
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    /** Red pane — slot index is beyond the player's current pickaxeLimit. */
    private ItemStack buildLockedSlot(Player player, int slotIndex)
    {
        Material requiredWood = WOOD_TYPES[slotIndex];
        int cost = getWoodCostForSlot(slotIndex);

        List<String> lore = new ArrayList<>();
        lore.add(fmt(player, " &#444444Locked"));
        lore.add("");
        lore.add(fmt(player, " &#FFFFFF&lDescription"));
        lore.add(fmt(player, " &8| &#777777Purchase to unlock this storage slot."));
        lore.add("");
        lore.add(fmt(player, " &#FFFFFF&lRequirements"));
        lore.add(fmt(player, " &8| &#00AFA3Cost: &#FFFFFF" + cost + " " + formatMaterialName(requiredWood)));
        lore.add("");

        return new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(fmt(player, "&#666666⬡ &#666666Locked Slot"))
                .lore(lore)
                .build();
    }

    /** Purchase button — unlocks the next slot in exchange for wood. */
    private ItemStack buildPurchaseButton(Player player, PlayerProfile profile, int nextSlotIndex)
    {
        Material requiredWood = WOOD_TYPES[nextSlotIndex];
        int requiredAmount = getWoodCostForSlot(nextSlotIndex);
        int playerAmount = countMaterialInInventory(player, requiredWood);
        boolean canAfford = playerAmount >= requiredAmount;

        String amountColor = canAfford ? "&#00FF88" : "&#FF5555";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(fmt(player, " &#FFFFFF&lRequirements"));
        lore.add(fmt(player, " &8| &#00AFA3Next Slot: &#FFFFFF" + (nextSlotIndex + 1)));
        lore.add(fmt(player, " &8| &#00AFA3Cost: &#FFFFFF" + requiredAmount + " " + formatMaterialName(requiredWood)));
        lore.add(fmt(player, " &8| &#00AFA3You Have: " + amountColor + playerAmount));
        lore.add("");
        lore.add(canAfford
                ? fmt(player, " &8| &aLeft-Click &#AAAAAAto unlock")
                : fmt(player, " &8| &#FF5555Not enough " + formatMaterialName(requiredWood)));
        lore.add("");

        String buttonName = canAfford
                ? fmt(player, "&#00AFA3⬡ &#FFFFFF&lUnlock Slot " + (nextSlotIndex + 1))
                : fmt(player, "&#FF5555⬡ &#FFFFFF&lInsufficient Resources");

        return new ItemBuilder(canAfford ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK)
                .name(buttonName)
                .lore(lore)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Material helpers (checks both Minecraft inventory and plugin backpack)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the combined amount of a material across the player's Minecraft
     * inventory and their plugin backpack.
     */
    private int countMaterialInInventory(Player player, Material material)
    {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }

        PlayerProfile profile = playerDataCenter.getLocal(player);
        if (profile != null && profile.backpack != null) {
            total += profile.backpack.getMaterialAmount(material);
        }

        return total;
    }

    /**
     * Removes the given amount of a material from the player, drawing from
     * their Minecraft inventory first, then their plugin backpack for any remainder.
     */
    private void removeMaterialFromInventory(Player player, Material material, int amount)
    {
        int remaining = amount;

        // Consume from Minecraft inventory first
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }

        // Consume any remainder from the backpack
        if (remaining > 0) {
            PlayerProfile profile = playerDataCenter.getLocal(player);
            if (profile != null && profile.backpack != null) {
                profile.backpack.consumeMaterial(material, remaining);
            }
        }
    }

    /** Shown at PURCHASE_SLOT when all slots are already unlocked. */
    private ItemStack buildMaxedButton(Player player)
    {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(fmt(player, " &#FFFFFF&lStatus"));
        lore.add(fmt(player, " &8| &#00FF88All pickaxe storage slots are unlocked!"));
        lore.add("");

        return new ItemBuilder(Material.NETHER_STAR)
                .name(fmt(player, "&#00AFA3★ &#FFFFFF&lAll Slots Unlocked"))
                .lore(lore)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    /** Shorthand: run a string through formatMessage for hex colors + small-caps. */
    private static String fmt(Player player, String text)
    {
        return formatMessage(player, text);
    }

    /**
     * Returns true if the player has any registered pickaxe (with a valid ID tag)
     * anywhere in their inventory.
     */
    private boolean playerHasRegisteredPickaxe(Player player)
    {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.getType().name().contains("PICKAXE")) continue;
            String id = item.getData("id");
            if (id != null && !id.isEmpty()) return true;
        }
        return false;
    }

    /**
     * Returns true if a pickaxe with the given ID is currently in the player's inventory.
     */
    private boolean pickaxeIsActive(Player player, String pickaxeId)
    {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.getType().name().contains("PICKAXE")) continue;
            if (pickaxeId.equals(item.getData("id"))) return true;
        }
        return false;
    }

    private int getSlotIndex(int guiSlot)
    {
        for (int i = 0; i < SLOT_POSITIONS.length; i++) {
            if (SLOT_POSITIONS[i] == guiSlot) return i;
        }
        return -1;
    }

    private String getStoredEntry(PlayerProfile profile, int slotIndex)
    {
        if (slotIndex < 0 || slotIndex >= profile.storedPickaxes.size()) return "";
        String entry = profile.storedPickaxes.get(slotIndex);
        return entry != null ? entry : "";
    }

    private void setStoredEntry(PlayerProfile profile, int slotIndex, String value)
    {
        while (profile.storedPickaxes.size() <= slotIndex) {
            profile.storedPickaxes.add("");
        }
        profile.storedPickaxes.set(slotIndex, value != null ? value : "");
    }

    private Material parseMaterial(String name)
    {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.WOODEN_PICKAXE;
        }
    }

    private int getWoodCostForSlot(int slotIndex)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        int baseCost = config.getInt(ConfigPaths.Default.PickaxeStorage.BASE_COST, 64);
        double multiplier = config.getDouble(ConfigPaths.Default.PickaxeStorage.COST_MULTIPLIER, 1.5);
        return (int) (baseCost * Math.pow(multiplier, slotIndex));
    }

    @Override
    public MenuType getMenuType()
    {
        return MENU_TYPE;
    }
}
