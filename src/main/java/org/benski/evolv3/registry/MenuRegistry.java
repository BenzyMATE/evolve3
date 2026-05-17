package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.MenuRegistryManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.menus.AnimationsMenu;
import org.benski.evolv3.menus.ArmorMenu;
import org.benski.evolv3.menus.BackpackMenu;
import org.benski.evolv3.menus.EnchantOptionMenu;
import org.benski.evolv3.menus.EnchantsPreferencesMenu;
import org.benski.evolv3.menus.ExampleConfigMenu;
import org.benski.evolv3.menus.ExperimentalPreferencesMenu;
import org.benski.evolv3.menus.FurnaceSmeltMenu;
import org.benski.evolv3.menus.PickaxeLimitMenu;
import org.benski.evolv3.menus.PickaxeMenu;
import org.benski.evolv3.menus.PreferencesMenu;
import org.benski.evolv3.menus.PrestigeConfirmMenu;
import org.benski.evolv3.menus.PrestigeStoneSelectMenu;
import org.benski.evolv3.menus.StoneSocketMenu;
import org.benski.evolv3.menus.TestMenu;

/** A registry to register menus/guis. */
public class MenuRegistry implements Registry<Menu> {
  @Getter private static final MenuRegistry instance = new MenuRegistry();
  private final MenuRegistryManager registryManager = new MenuRegistryManager(Main.getInstance());

  /** Register menus. Add your menu classes here. */
  private final List<Class<? extends Menu>> menus =
      List.of(
          TestMenu.class,
          ExampleConfigMenu.class,
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
          PrestigeStoneSelectMenu.class);

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
