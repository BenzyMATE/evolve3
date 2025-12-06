package org.benski.evolv3.registry;

import org.apache.commons.lang3.NotImplementedException;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.MenuRegistryManager;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.menus.TestMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * A registry to register menus/guis.
 */
public class MenuRegistry implements Registry<Menu>
{
    private MenuRegistry() {}
    private static final MenuRegistry instance = new MenuRegistry();
    private final MenuRegistryManager registryManager = new MenuRegistryManager(Main.getInstance());

    /**
     * Register your menus here.
     */
    private final List<Class<? extends Menu>> menus = List.of(TestMenu.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        menus.forEach(registryManager::registerMenu);
        registryManager.initializeMenus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Menu get(String id) {
        return registryManager.getMenu(MenuType.fromIdentifier(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        throw new NotImplementedException();
    }

    /**
     * {@inheritDoc}
     */
    public static MenuRegistry getInstance() {
        return instance;
    }
}
