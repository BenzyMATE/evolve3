package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.ListenerRegistryManager;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.listeners.BeamListener;
import org.benski.evolv3.listeners.BlackholeListener;
import org.benski.evolv3.listeners.BorderListener;
import org.benski.evolv3.listeners.BreakListener;
import org.benski.evolv3.listeners.CellListener;
import org.benski.evolv3.listeners.EnchantListener;
import org.benski.evolv3.listeners.FoodDropListener;
import org.benski.evolv3.listeners.FurnaceListener;
import org.benski.evolv3.listeners.HungerListener;
import org.benski.evolv3.listeners.InteractListener;
import org.benski.evolv3.listeners.JoinListener;
import org.benski.evolv3.listeners.LaunchPadListener;
import org.benski.evolv3.listeners.MineListener;
import org.benski.evolv3.listeners.OmnitoolListener;
import org.benski.evolv3.listeners.PickaxeOwnershipListener;
import org.benski.evolv3.listeners.RankChatListener;
import org.benski.evolv3.listeners.ScoreboardListener;
import org.benski.evolv3.listeners.ScrollListener;
import org.benski.evolv3.listeners.SpawnerListener;

/** A registry to register event listeners. */
public class ListenerRegistry implements Registry<BaseListener> {
  @Getter private static final ListenerRegistry instance = new ListenerRegistry();
  private final ListenerRegistryManager registryManager =
      new ListenerRegistryManager(Main.getInstance());

  /** Register listeners here. Add your listener classes to this list. */
  private final List<Class<? extends BaseListener>> listeners =
      List.of(
          JoinListener.class,
          BreakListener.class,
          EnchantListener.class,
          InteractListener.class,
          BackpackListener.class,
          FurnaceListener.class,
          PickaxeOwnershipListener.class,
          ScoreboardListener.class,
          RankChatListener.class,
          BorderListener.class,
          BeamListener.class,
          LaunchPadListener.class,
          MineListener.class,
          SpawnerListener.class,
          OmnitoolListener.class,
          HungerListener.class,
          FoodDropListener.class,
          ScrollListener.class,
          BlackholeListener.class,
          CellListener.class);

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    listeners.forEach(registryManager::registerListener);
    registryManager.initializeListeners();
  }

  /** {@inheritDoc} */
  @Override
  public BaseListener get(String id) {
    return registryManager.getListener(id);
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    registryManager.cleanup();
  }
}
