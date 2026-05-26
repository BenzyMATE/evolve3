package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.registry.MenuRegistryManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.menus.cell.CellAbandonConfirmMenu;
import org.benski.evolv3.menus.cell.CellEvolveConfirmMenu;
import org.benski.evolv3.menus.cell.CellMemberMenu;
import org.benski.evolv3.menus.cell.CellMemberPermMenu;
import org.benski.evolv3.menus.cell.CellMenu;
import org.benski.evolv3.menus.cell.CellRentInfoMenu;
import org.benski.evolv3.menus.island.IslandMenu;
import org.benski.evolv3.menus.island.IslandsMenu;
import org.benski.evolv3.menus.pickaxe.EnchantOptionMenu;
import org.benski.evolv3.menus.pickaxe.EnchantsPreferencesMenu;
import org.benski.evolv3.menus.pickaxe.FurnaceSmeltMenu;
import org.benski.evolv3.menus.pickaxe.PickaxeLimitMenu;
import org.benski.evolv3.menus.pickaxe.PickaxeMenu;
import org.benski.evolv3.menus.pickaxe.PrestigeConfirmMenu;
import org.benski.evolv3.menus.pickaxe.PrestigeStoneSelectMenu;
import org.benski.evolv3.menus.pickaxe.StoneSocketMenu;
import org.benski.evolv3.menus.player.AnimationsMenu;
import org.benski.evolv3.menus.player.ArmorMenu;
import org.benski.evolv3.menus.player.BackpackMenu;
import org.benski.evolv3.menus.player.ExperimentalPreferencesMenu;
import org.benski.evolv3.menus.player.PreferencesMenu;

/** A registry to register menus/guis. */
public class MenuRegistry implements Registry<Menu> {
  @Getter private static final MenuRegistry instance = new MenuRegistry();
  private final MenuRegistryManager registryManager = new MenuRegistryManager(Main.getInstance());

  /** Register menus. Add your menu classes here. */
  private final List<Class<? extends Menu>> menus =
      List.of(
          PickaxeMenu.class,
          ArmorMenu.class,
          BackpackMenu.class,
          PickaxeLimitMenu.class,
          PreferencesMenu.class,
          AnimationsMenu.class,
          EnchantsPreferencesMenu.class,
          EnchantOptionMenu.class,
          FurnaceSmeltMenu.class,
          StoneSocketMenu.class,
          ExperimentalPreferencesMenu.class,
          PrestigeConfirmMenu.class,
          PrestigeStoneSelectMenu.class,
          CellMenu.class,
          CellEvolveConfirmMenu.class,
          CellAbandonConfirmMenu.class,
          CellRentInfoMenu.class,
          CellMemberMenu.class,
          CellMemberPermMenu.class,
          IslandMenu.class,
          IslandsMenu.class);

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    menus.forEach(registryManager::registerMenu);
    registryManager.initializeMenus();
  }

  /** {@inheritDoc} */
  @Override
  public Menu get(String id) {
    return registryManager.getMenu(MenuType.fromIdentifier(id));
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    registryManager.cleanup();
  }
}
