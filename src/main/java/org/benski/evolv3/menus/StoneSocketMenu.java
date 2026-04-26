package org.benski.evolv3.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.StoneEffectRegistry;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Menu for viewing and managing the three evolution stone sockets on a single enchant.
 *
 * <p>Open this menu by shift+left-clicking an enchant slot in {@link PickaxeMenu}.
 * The menu displays three socket slots showing any applied stones along with their
 * passive effect descriptions. Players apply stones from their cursor and remove
 * them with right-click.</p>
 *
 * <h3>Socket interactions</h3>
 * <ul>
 *   <li><b>Left-click on socket with an Evolution Stone on cursor</b> — applies the
 *       stone to that socket. Any previously socketed stone is returned to the
 *       player's inventory.</li>
 *   <li><b>Right-click on an occupied socket</b> — removes the stone and returns it
 *       to the player's inventory.</li>
 *   <li><b>Back button (slot 22)</b> — closes this menu and re-opens the Pickaxe Menu.</li>
 * </ul>
 */
public class StoneSocketMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("stone_socket");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Layout constants
    private static final int SLOT_ENCHANT_HEADER = 4;
    /** All possible socket slot positions (up to MAX_SOCKET_COUNT). Prestige unlocks each in order. */
    private static final int[] SOCKET_SLOTS      = {11, 12, 13, 14, 15};
    private static final int SLOT_BACK           = 22;

    /** Per-player state: which enchant display name this player is currently viewing. */
    private final Map<UUID, String> pendingEnchants = new ConcurrentHashMap<>();

    private final PickaxeDataCenter pickaxeDataCenter;

    public StoneSocketMenu(JavaPlugin plugin) {
        super(plugin);
        pickaxeDataCenter = (PickaxeDataCenter) DataCenterRegistry.getInstance().get("pickaxe");
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Prepares the socket view for the given enchant and opens the menu for the player.
     * The enchant display name is stored per-player and consumed by {@link #populateMenu}.
     *
     * @param player      the player to open the menu for
     * @param displayName the enchant's display name (e.g. "Efficiency", "Nuclear Shower")
     */
    public void openForEnchant(Player player, String displayName) {
        pendingEnchants.put(player.getUniqueId(), displayName);
        openMenu(player);
    }

    // -------------------------------------------------------------------------
    // BaseMenu overrides
    // -------------------------------------------------------------------------

    @Override
    protected String getMenuTitle(Player player) {
        return "&8Sockets";
    }

    @Override
    protected int getMenuSize() {
        return 27;
    }

    @Override
    protected void onOpen(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
    }

    @Override
    protected void onClose(Player player) {
        pendingEnchants.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 1.1f);
    }

    @Override
    protected void populateMenu(Player player, Inventory inventory) {
        String displayName = pendingEnchants.get(player.getUniqueId());
        if (displayName == null) return;

        PickaxeProfile profile = pickaxeDataCenter.getLocal(player);
        if (profile == null) return;

        EnchantData enchant = profile.enchants.getEnchant(displayName.toLowerCase());

        // Fill all slots with a dark glass pane background
        ItemStack filler = buildFiller();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        // Slot 4: Enchant header
        int unlockedSlots = EnchantData.getUnlockedSlotCount(profile.prestige);
        inventory.setItem(SLOT_ENCHANT_HEADER, buildEnchantHeader(player, displayName, enchant, unlockedSlots));

        // Show unlocked socket slots based on pickaxe prestige
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            if (i < unlockedSlots) {
                inventory.setItem(SOCKET_SLOTS[i], buildSocketItem(player, displayName, enchant, i));
            } else {
                inventory.setItem(SOCKET_SLOTS[i], buildLockedSocketItem(player, i));
            }
        }

        // Slot 22: Back button
        inventory.setItem(SLOT_BACK, buildBackButton(player));
    }

    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
        // Back button
        if (slot == SLOT_BACK) {
            closeMenu(player);
            PickaxeMenu pickaxeMenu = (PickaxeMenu) MenuRegistry.getInstance().get("pickaxe");
            if (pickaxeMenu != null) {
                pickaxeMenu.openMenu(player);
            }
            return true;
        }

        // Socket slots
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            if (slot == SOCKET_SLOTS[i]) {
                handleSocketClick(player, i, event);
                return true;
            }
        }

        return true; // cancel all other clicks
    }

    @Override
    public MenuType getMenuType() {
        return MENU_TYPE;
    }

    // -------------------------------------------------------------------------
    // Socket interaction
    // -------------------------------------------------------------------------

    private void handleSocketClick(Player player, int socketIndex, InventoryClickEvent event) {
        String displayName = pendingEnchants.get(player.getUniqueId());
        if (displayName == null) return;

        PickaxeProfile profile = pickaxeDataCenter.getLocal(player);
        if (profile == null) return;

        // Reject clicks on locked slots
        if (socketIndex >= EnchantData.getUnlockedSlotCount(profile.prestige)) return;

        EnchantData enchant = profile.enchants.getEnchant(displayName.toLowerCase());
        if (enchant == null) return;

        if (event.isRightClick()) {
            // Remove stone from socket and return to inventory
            String existing = enchant.socketStones[socketIndex];
            if (existing == null) return;

            RarityConfig rarity = EnchantAnimationConfig.getRarityByName(existing);
            if (rarity != null) {
                ItemStack stone = EvolutionStone.create(rarity, displayName);
                if (player.getInventory().firstEmpty() >= 0) {
                    player.getInventory().addItem(stone);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), stone);
                }
            }
            enchant.socketStones[socketIndex] = null;
            profile.save(null);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.0f);
            refresh(player);
            return;
        }

        // Left-click: apply stone from cursor
        ItemStack cursor = event.getCursor();
        if (!EvolutionStone.isEvolutionStone(cursor)) {
            player.sendMessage(formatMessage(player,
                    "%evolv3_prefix% &#CCCCCCHold an &#00FFFFEvolution Stone &#CCCCCCin your cursor to socket it."));
            return;
        }

        String rarityName = EvolutionStone.getRarityName(cursor);
        if (rarityName == null) return;

        // Validate the stone belongs to this enchant
        String stonEnchant = EvolutionStone.getEnchantName(cursor);
        if (stonEnchant != null && !stonEnchant.equalsIgnoreCase(displayName)) {
            player.sendMessage(formatMessage(player,
                    "%evolv3_prefix% &#CCCCCCThat stone is bound to &#00FFFF" + stonEnchant
                    + "&#CCCCCC, not &#00FFFF" + displayName + "&#CCCCCC."));
            return;
        }

        // Return existing stone to inventory if slot is occupied
        String existingRarity = enchant.socketStones[socketIndex];
        if (existingRarity != null) {
            RarityConfig existing = EnchantAnimationConfig.getRarityByName(existingRarity);
            if (existing != null) {
                ItemStack returnStone = EvolutionStone.create(existing, displayName);
                if (player.getInventory().firstEmpty() >= 0) {
                    player.getInventory().addItem(returnStone);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), returnStone);
                }
            }
        }

        // Prevent the same rarity from being socketed twice (design: one stone per tier)
        for (int i = 0; i < EnchantData.MAX_SOCKET_COUNT; i++) {
            if (i != socketIndex && rarityName.equals(enchant.socketStones[i])) {
                player.sendMessage(formatMessage(player,
                        "%evolv3_prefix% &#CCCCCCYou already have a &#00FFFF" + rarityName
                        + " &#CCCCCCstone socketed in this enchant."));
                return;
            }
        }

        // Apply new stone to socket
        enchant.socketStones[socketIndex] = rarityName;
        profile.save(null);

        // Consume one stone from cursor
        cursor.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.9f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE,  0.6f, 1.2f);
        refresh(player);
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private ItemStack buildEnchantHeader(Player player, String displayName, EnchantData enchant, int unlockedSlots) {
        ItemStack item = EnchantAnimationConfig.createEnchantItem(displayName);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        var option    = EnchantAnimationConfig.getOption(displayName);
        var rarity    = option != null ? option.rarity() : null;
        String rarityHex  = rarity != null ? String.format("%06X", rarity.textColor().value()) : "CCCCCC";
        String cc         = "&#" + rarityHex;
        String rarityName = rarity != null ? rarity.displayName() : "Unknown";

        meta.displayName(format(player, cc + "&l" + displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(format(player, " " + cc + rarityName));
        lore.add(Component.empty());
        lore.add(format(player, " &#FFFFFF&lStatistics"));
        if (enchant != null) {
            lore.add(format(player, " &8| &#888888Level: &#FFFFFF" + enchant.level + " &8/ &7" + enchant.maxLevel));
            lore.add(format(player, " &8| &#888888Sockets: &#FFFFFF" + enchant.getSocketedCount() + " &8/ &7" + unlockedSlots));
        } else {
            lore.add(format(player, " &8| &#FF5555This enchant has not been unlocked."));
        }
        lore.add(Component.empty());
        lore.add(format(player, " &#FFFFFF&lSocket Guide"));
        lore.add(format(player, " &8| &aLeft-Click &#AAAAAAa socket with a stone to apply."));
        lore.add(format(player, " &8| &cRight-Click &#AAAAAAan occupied socket to remove."));
        lore.add(Component.empty());
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSocketItem(Player player, String enchantDisplayName,
                                      EnchantData enchant, int socketIndex) {
        String storedRarity = (enchant != null) ? enchant.socketStones[socketIndex] : null;

        FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.STONES);

        if (storedRarity != null) {
            RarityConfig rarity = EnchantAnimationConfig.getRarityByName(storedRarity);
            if (rarity != null) {
                String rarityHex = String.format("%06X", rarity.textColor().value());
                String internalName = enchantDisplayName.toLowerCase();
                String effectText   = StoneEffectRegistry.getEffect(internalName, storedRarity);

                // Resolve enchant's own rarity color for display
                var enchantOption      = EnchantAnimationConfig.getOption(enchantDisplayName);
                var enchantRarConfig   = enchantOption != null ? enchantOption.rarity() : null;
                String enchantRarityHex = enchantRarConfig != null
                        ? String.format("%06X", enchantRarConfig.textColor().value())
                        : "CCCCCC";

                String stoneName       = StoneEffectRegistry.getName(internalName, storedRarity);
                if (stoneName == null) stoneName = storedRarity + " Evolution Stone";

                String nameTemplate    = cfg != null ? cfg.getString(ConfigPaths.Stones.SocketMenu.FILLED_NAME,          "&#{hex}◆ &l{stone_name}")                        : "&#{hex}◆ &l{stone_name}";
                String rarityTemplate  = cfg != null ? cfg.getString(ConfigPaths.Stones.SocketMenu.FILLED_RARITY_LINE,   " &#{hex}&l{rarity} &#888888Evolution Stone")      : " &#{hex}&l{rarity} &#888888Evolution Stone";
                String effectHeader    = cfg != null ? cfg.getString(ConfigPaths.Stones.SocketMenu.FILLED_EFFECT_HEADER, " &#FFFFFF&lPassive Effect")                       : " &#FFFFFF&lPassive Effect";
                String effectTemplate  = cfg != null ? cfg.getString(ConfigPaths.Stones.SocketMenu.FILLED_EFFECT_LINE,   " &8| &#{hex}{effect}")                            : " &8| &#{hex}{effect}";
                String removeHint      = cfg != null ? cfg.getString(ConfigPaths.Stones.SocketMenu.FILLED_REMOVE_HINT,   " &8| &cRight-Click &#AAAAAAto remove this stone.") : " &8| &cRight-Click &#AAAAAAto remove this stone.";

                ItemStack item = new ItemStack(org.bukkit.Material.END_CRYSTAL);
                ItemMeta  meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(format(player, nameTemplate
                            .replace("{hex}", rarityHex)
                            .replace("{stone_name}", stoneName)));
                    List<Component> lore = new ArrayList<>();
                    lore.add(format(player, rarityTemplate
                            .replace("{hex}", rarityHex)
                            .replace("{rarity}", storedRarity)
                            .replace("{enchant_hex}", enchantRarityHex)
                            .replace("{enchant}", enchantDisplayName)));
                    lore.add(Component.empty());
                    lore.add(format(player, effectHeader));
                    for (String chunk : org.benski.evolv3.core.helpers.Utils.wrapWords(effectText, 5)) {
                        lore.add(format(player, effectTemplate
                                .replace("{hex}", rarityHex)
                                .replace("{effect}", chunk)));
                    }
                    lore.add(Component.empty());
                    lore.add(format(player, removeHint));
                    lore.add(Component.empty());
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                return item;
            }
        }

        // Empty socket
        String emptyName       = cfg != null ? cfg.getString(ConfigPaths.Stones.SocketMenu.EMPTY_NAME,          "&#888888Socket {index} &#444444\u2014 Empty") : "&#888888Socket {index} &#444444\u2014 Empty";
        String howToHeader     = cfg != null ? cfg.getString(ConfigPaths.Stones.SocketMenu.EMPTY_HOW_TO_HEADER, " &#FFFFFF&lHow to Socket")                   : " &#FFFFFF&lHow to Socket";
        List<String> howToLines = cfg != null ? cfg.getStringList(ConfigPaths.Stones.SocketMenu.EMPTY_HOW_TO_LINES) : List.of(" &8| &#888888Hold an &#00FFFFEvolution Stone", " &8| &#888888in your cursor and left-click.");

        ItemStack item = new ItemStack(Material.GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(format(player, emptyName.replace("{index}", String.valueOf(socketIndex + 1))));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(format(player, howToHeader));
            for (String line : howToLines) {
                lore.add(format(player, line));
            }
            lore.add(Component.empty());
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildLockedSocketItem(Player player, int socketIndex) {
        // socketIndex is 0-based; slot i is unlocked at prestige i (getUnlockedSlotCount = 1 + prestige)
        int requiredPrestige = socketIndex;
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(format(player, "&#FF4444&lLocked Socket"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(format(player, " &#888888Requires &#FFD700Prestige " + requiredPrestige));
            lore.add(Component.empty());
            lore.add(format(player, " &#888888Prestige your pickaxe to"));
            lore.add(format(player, " &#888888unlock this socket slot."));
            lore.add(Component.empty());
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildBackButton(Player player) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(format(player, "&c&l\u2190 Back"));
            meta.lore(new ArrayList<>());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildFiller() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Processes hex colour codes via MatrixColorAPI, then deserialises to a
     * non-italic Adventure component.  Use this instead of bare deserialize()
     * for any string that may contain &#RRGGBB codes.
     */
    private Component format(Player player, String message) {
        return LEGACY.deserialize(formatMessage(player, message))
                .decoration(TextDecoration.ITALIC, false);
    }

}
