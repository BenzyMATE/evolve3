package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.registry.ListenerRegistryManager;
import org.benski.evolv3.listeners.inventory.BackpackListener;
import org.benski.evolv3.listeners.inventory.InteractListener;
import org.benski.evolv3.listeners.inventory.ScrollListener;
import org.benski.evolv3.listeners.mine.BreakListener;
import org.benski.evolv3.listeners.mine.CellListener;
import org.benski.evolv3.listeners.mine.MineListener;
import org.benski.evolv3.listeners.pickaxe.EnchantListener;
import org.benski.evolv3.listeners.pickaxe.OmnitoolListener;
import org.benski.evolv3.listeners.pickaxe.PickaxeOwnershipListener;
import org.benski.evolv3.listeners.player.FoodDropListener;
import org.benski.evolv3.listeners.player.HungerListener;
import org.benski.evolv3.listeners.player.JoinListener;
import org.benski.evolv3.listeners.player.RankChatListener;
import org.benski.evolv3.listeners.player.ScoreboardListener;
import org.benski.evolv3.listeners.world.BeamListener;
import org.benski.evolv3.listeners.world.BlackholeListener;
import org.benski.evolv3.listeners.world.BorderListener;
import org.benski.evolv3.listeners.world.FurnaceListener;
import org.benski.evolv3.listeners.world.LaunchPadListener;
import org.benski.evolv3.listeners.world.SpawnerListener;

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
