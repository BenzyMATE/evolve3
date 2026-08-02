package org.benski.evolv3.menus.player;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Experimental preferences sub-menu.
 *
 * <pre>
 * 27-slot layout (3 rows × 9 cols):
 *
 * Row 0: full black border
 * Row 1: [border][gray][gray][gray][Pickaxe Anim toggle][gray][gray][gray][border]
 * Row 2: full black border, back button at slot 22
 * </pre>
 */
public class ExperimentalPreferencesMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("preferences_experimental");
  private static final LegacyComponentSerializer LEGACY =
      LegacyComponentSerializer.legacyAmpersand();

  private static final int PICKAXE_ANIM_SLOT = 13;
  private static final int BACK_SLOT = 22;

  /** Creates and registers the ExperimentalPreferencesMenu. @param plugin the owning plugin */
  public ExperimentalPreferencesMenu(JavaPlugin plugin) {
    super(plugin);
  }

  /** Returns the menu title. */
  @Override
  protected String getMenuTitle(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    return menus.getString(ConfigPaths.Menus.ExperimentalMenu.TITLE, "&8P &7» &8Experimental");
  }

  /** Returns the menu size. */
  @Override
  protected int getMenuSize() {
    return 27;
  }

  /** Called when open occurs. */
  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
  }

  /** Populate menu. */
  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
    ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE);

    for (int i = 0; i < 27; i++) {
      inventory.setItem(i, isBorder(i, 27) ? black : gray);
    }

    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

    inventory.setItem(
        BACK_SLOT,
        buildItem(
            mat(
                menus,
                ConfigPaths.Menus.AnimationsMenu.BACK_MATERIAL,
                Material.RED_STAINED_GLASS_PANE),
            menus.getString(ConfigPaths.Menus.AnimationsMenu.BACK_NAME, "&#AAAAAA← Back"),
            menus.getStringList(ConfigPaths.Menus.AnimationsMenu.BACK_LORE)));

    PlayerPreferences prefs = getPrefs(player);
    boolean animEnabled = prefs != null && prefs.pickaxeMenuAnimation;

    Material onMat =
        mat(menus, ConfigPaths.Menus.ExperimentalMenu.PICKAXE_ANIM_ENABLED_MAT, Material.LIME_DYE);
    Material offMat =
        mat(menus, ConfigPaths.Menus.ExperimentalMenu.PICKAXE_ANIM_DISABLED_MAT, Material.RED_DYE);
    String name =
        menus.getString(
            ConfigPaths.Menus.ExperimentalMenu.PICKAXE_ANIM_NAME, "&#FF8800Pickaxe Menu Animation");
    List<String> lore = menus.getStringList(ConfigPaths.Menus.ExperimentalMenu.PICKAXE_ANIM_LORE);

    inventory.setItem(PICKAXE_ANIM_SLOT, dyeToggle(name, lore, animEnabled, onMat, offMat));
  }

  /** Handles click. */
  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    if (slot == BACK_SLOT) {
      var back = MenuRegistry.getInstance().get("preferences");
      if (back != null) back.openMenu(player);
      return true;
    }

    if (slot == PICKAXE_ANIM_SLOT) {
      PlayerProfile profile = getProfile(player);
      if (profile != null) {
        profile.preferences.pickaxeMenuAnimation = !profile.preferences.pickaxeMenuAnimation;
        profile.save();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        refresh(player);
      }
      return true;
    }

    return true;
  }

  /** Returns the menu type. */
  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  /** Returns the prefs. */
  private PlayerPreferences getPrefs(Player player) {
    PlayerProfile profile = getProfile(player);
    return profile != null ? profile.preferences : null;
  }

  /** Returns the profile. */
  private PlayerProfile getProfile(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    return pdc != null ? pdc.getLocal(player) : null;
  }

  /** Dye toggle. */
  private ItemStack dyeToggle(
      String name, List<String> lore, boolean enabled, Material enabledMat, Material disabledMat) {
    Material mat = enabled ? enabledMat : disabledMat;
    String displayName = enabled ? "&a✔ " + name : "&c✘ " + name;
    String statusLine = enabled ? "&a● Enabled" : "&c● Disabled";
    List<String> resolved =
        lore.stream().map(line -> line.replace("{status}", statusLine)).toList();
    return buildItem(mat, displayName, resolved);
  }

  /** Mat. */
  private Material mat(FileConfiguration cfg, String path, Material fallback) {
    String val = cfg.getString(path);
    if (val == null) return fallback;
    Material m = Material.getMaterial(val.toUpperCase());
    return m != null ? m : fallback;
  }

  /** Returns {@code true} if border. */
  private boolean isBorder(int slot, int size) {
    int col = slot % 9;
    return col == 0 || col == 8 || slot < 9 || slot >= size - 9;
  }

  /** Pane. */
  private ItemStack pane(Material mat) {
    return buildItem(mat, " ", List.of());
  }

  /** Builds item. */
  private ItemStack buildItem(Material material, String name, List<String> lore) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    meta.displayName(ds(name));

    List<Component> loreComponents = new ArrayList<>(lore.size());
    for (String line : lore) {
      loreComponents.add(line.isEmpty() ? Component.empty() : ds(line));
    }
    meta.lore(loreComponents);
    item.setItemMeta(meta);
    return item;
  }

  /** Ds. */
  private Component ds(String text) {
    return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
  }
}
