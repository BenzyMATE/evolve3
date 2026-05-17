package org.benski.evolv3.menus;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.registry.ConfigRegistry;
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

/** Main preferences menu. Shows available setting categories. */
public class PreferencesMenu extends BaseMenu {
  private static final MenuType MENU_TYPE = MenuType.register("preferences");
  private static final LegacyComponentSerializer LEGACY =
      LegacyComponentSerializer.legacyAmpersand();

  private static final int ANIMATIONS_SLOT = 11;
  private static final int ENCHANTS_SLOT = 13;
  private static final int MESSAGES_SLOT = 15;
  private static final int EXPERIMENTAL_SLOT = 22;
  private static final int CLOSE_SLOT = 31;

  public PreferencesMenu(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  protected String getMenuTitle(Player player) {
    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
    return menus.getString(ConfigPaths.Menus.PreferencesMenu.TITLE, "&8Preferences");
  }

  @Override
  protected int getMenuSize() {
    return 36;
  }

  @Override
  protected void onOpen(Player player) {
    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
  }

  @Override
  protected void populateMenu(Player player, Inventory inventory) {
    ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
    ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 36; i++) {
      inventory.setItem(i, isBorder(i, 36) ? black : gray);
    }

    FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);

    inventory.setItem(
        ANIMATIONS_SLOT,
        buildItem(
            mat(
                menus,
                ConfigPaths.Menus.PreferencesMenu.ANIMATIONS_MATERIAL,
                Material.FIREWORK_ROCKET),
            menus.getString(
                ConfigPaths.Menus.PreferencesMenu.ANIMATIONS_NAME, "&#00FFFF⚡ Animations"),
            menus.getStringList(ConfigPaths.Menus.PreferencesMenu.ANIMATIONS_LORE)));

    inventory.setItem(
        ENCHANTS_SLOT,
        buildItem(
            mat(
                menus,
                ConfigPaths.Menus.PreferencesMenu.ENCHANTS_MATERIAL,
                Material.ENCHANTING_TABLE),
            menus.getString(ConfigPaths.Menus.PreferencesMenu.ENCHANTS_NAME, "&#00FFFFEnchants"),
            menus.getStringList(ConfigPaths.Menus.PreferencesMenu.ENCHANTS_LORE)));

    inventory.setItem(
        MESSAGES_SLOT,
        buildItem(
            mat(menus, ConfigPaths.Menus.PreferencesMenu.MESSAGES_MATERIAL, Material.PAPER),
            menus.getString(ConfigPaths.Menus.PreferencesMenu.MESSAGES_NAME, "&#888888✉ Messages"),
            menus.getStringList(ConfigPaths.Menus.PreferencesMenu.MESSAGES_LORE)));

    inventory.setItem(
        EXPERIMENTAL_SLOT,
        buildItem(
            mat(
                menus,
                ConfigPaths.Menus.PreferencesMenu.EXPERIMENTAL_MATERIAL,
                Material.COMMAND_BLOCK),
            menus.getString(
                ConfigPaths.Menus.PreferencesMenu.EXPERIMENTAL_NAME, "&#FF8800⚗ Experimental"),
            menus.getStringList(ConfigPaths.Menus.PreferencesMenu.EXPERIMENTAL_LORE)));

    inventory.setItem(
        CLOSE_SLOT,
        buildItem(
            mat(menus, ConfigPaths.Menus.PreferencesMenu.CLOSE_MATERIAL, Material.BARRIER),
            menus.getString(ConfigPaths.Menus.PreferencesMenu.CLOSE_NAME, "&cClose"),
            menus.getStringList(ConfigPaths.Menus.PreferencesMenu.CLOSE_LORE)));
  }

  @Override
  protected boolean handleClick(Player player, int slot, InventoryClickEvent event) {
    switch (slot) {
      case ANIMATIONS_SLOT -> {
        var m = MenuRegistry.getInstance().get("preferences_animations");
        if (m != null) m.openMenu(player);
      }
      case ENCHANTS_SLOT -> {
        var m = MenuRegistry.getInstance().get("preferences_enchants");
        if (m != null) m.openMenu(player);
      }
      case EXPERIMENTAL_SLOT -> {
        var m = MenuRegistry.getInstance().get("preferences_experimental");
        if (m != null) m.openMenu(player);
      }
      case MESSAGES_SLOT ->
          player.sendMessage(
              formatMessage(
                  player, "%evolv3_prefix% &#888888&oMessages preferences — coming soon."));
      case CLOSE_SLOT -> closeMenu(player);
    }
    return true;
  }

  @Override
  public MenuType getMenuType() {
    return MENU_TYPE;
  }

  private Material mat(FileConfiguration cfg, String path, Material fallback) {
    String val = cfg.getString(path);
    if (val == null) return fallback;
    Material m = Material.getMaterial(val.toUpperCase());
    return m != null ? m : fallback;
  }

  private boolean isBorder(int slot, int size) {
    int col = slot % 9;
    return col == 0 || col == 8 || slot < 9 || slot >= size - 9;
  }

  private ItemStack pane(Material mat) {
    return buildItem(mat, " ", List.of());
  }

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

  private Component ds(String text) {
    return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
  }
}
