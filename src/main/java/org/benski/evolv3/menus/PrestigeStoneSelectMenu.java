package org.benski.evolv3.menus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.animations.PrestigeStoneAnimation;
import org.benski.evolv3.animations.PrestigeStoneFlightAnimation;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.PrestigeManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GUI for selecting one of three prestige stone rewards, with a slot-machine style animation that
 * settles on the rolled stones before allowing a selection.
 *
 * <pre>
 * 27-slot layout:
 * Row 0: [border × 9]
 * Row 1: [border][stone1][border × 2][stone2][border × 2][stone3][border]
 * Row 2: [border × 9]
 * </pre>
 *
 * <p>Open via {@link #openForStones(Player, List)} after the prestige animation completes.
 */
public class PrestigeStoneSelectMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("prestige_stone_select");
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private static final int SLOT_STONE_1 = 10;
  private static final int SLOT_STONE_2 = 13;
  private static final int SLOT_STONE_3 = 16;
  private static final int[] STONE_SLOTS = {SLOT_STONE_1, SLOT_STONE_2, SLOT_STONE_3};

  private final Map<UUID, List<PrestigeManager.StoneRoll>> pendingStones =
      new ConcurrentHashMap<>();
  private final Set<UUID> animating = ConcurrentHashMap.newKeySet();

  public PrestigeStoneSelectMenu(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  @Override
  protected String getMenuTitle(Player player) {
    FileConfiguration msgs = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    return msgs.getString(
        ConfigPaths.Messages.Pickaxe.PRESTIGE_STONE_SELECT_TITLE, "&#FFD700✦ Choose Your Stone");
  }

  @Override
  protected int getMenuSize() {
    return 27;
  }

  /**
   * Stores the rolled stones for this player, opens the menu, and starts the slot-machine
   * animation. The player cannot click until the animation finishes.
   */
  public void openForStones(Player player, List<PrestigeManager.StoneRoll> stones) {
    pendingStones.put(player.getUniqueId(), stones);
    animating.add(player.getUniqueId());
    openMenu(player);

    Inventory inv = player.getOpenInventory().getTopInventory();
    PrestigeStoneAnimation.play(
        player, inv, stones, STONE_SLOTS, () -> animating.remove(player.getUniqueId()));
  }

  @Override
  protected void populateMenu(Player player, Inventory inv) {
    ItemStack border = borderPane();
    for (int i = 0; i < 27; i++) {
      inv.setItem(i, border);
    }
    ItemStack spinner = spinnerPlaceholder();
    for (int slot : STONE_SLOTS) {
      inv.setItem(slot, spinner);
    }
  }

  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    if (animating.contains(player.getUniqueId())) return true;

    List<PrestigeManager.StoneRoll> stones = pendingStones.get(player.getUniqueId());
    if (stones == null || stones.size() < 3) {
      closeMenu(player);
      return true;
    }

    PrestigeManager.StoneRoll chosen = null;
    if (slot == SLOT_STONE_1) chosen = stones.get(0);
    else if (slot == SLOT_STONE_2) chosen = stones.get(1);
    else if (slot == SLOT_STONE_3) chosen = stones.get(2);

    if (chosen == null) return true;

    final PrestigeManager.StoneRoll finalChosen = chosen;
    pendingStones.remove(player.getUniqueId());
    closeMenu(player);

    PrestigeStoneFlightAnimation.play(
        player,
        EvolutionStone.create(finalChosen.rarity(), finalChosen.enchantName()),
        finalChosen.rarity(),
        () -> executePrestige(player, finalChosen));
    return true;
  }

  @Override
  protected void onClose(Player player) {
    animating.remove(player.getUniqueId());
    pendingStones.remove(player.getUniqueId());
  }

  private void executePrestige(Player player, PrestigeManager.StoneRoll roll) {
    Pickaxe pickaxeProfile = PrestigeManager.getInstance().getLocalProfile(player);
    if (pickaxeProfile == null) {
      String err =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.Pickaxe.PRESTIGE_ERROR,
                  "%evolv3_prefix% &cFailed to process prestige. Please try again.");
      player.sendMessage(Utils.formatMessage(player, err));
      return;
    }

    PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDC == null) {
      player.sendMessage(
          Utils.formatMessage(
              player, "%evolv3_prefix% &cPlayer data unavailable. Please try again."));
      return;
    }

    PlayerProfile playerProfile = playerDC.getLocal(player);
    if (playerProfile == null) {
      player.sendMessage(
          Utils.formatMessage(
              player, "%evolv3_prefix% &cPlayer profile unavailable. Please try again."));
      return;
    }

    PrestigeManager.getInstance().executePrestige(player, pickaxeProfile, playerProfile, roll);
  }

  private static ItemStack spinnerPlaceholder() {
    ItemStack item = new ItemStack(Material.NETHER_STAR);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(lc("&#888888Rolling..."));
      meta.lore(List.of());
      item.setItemMeta(meta);
    }
    return item;
  }

  private static ItemStack borderPane() {
    ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    ItemMeta meta = pane.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.empty());
      pane.setItemMeta(meta);
    }
    return pane;
  }

  private static Component lc(String text) {
    return LEGACY
        .deserialize(
            MatrixColorAPI.process(org.benski.evolv3.core.helpers.FontUtils.toSmallCaps(text)))
        .decoration(TextDecoration.ITALIC, false);
  }
}
