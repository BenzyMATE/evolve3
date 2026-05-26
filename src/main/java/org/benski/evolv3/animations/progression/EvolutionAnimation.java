package org.benski.evolv3.animations.progression;

import org.bukkit.inventory.ItemStack;

/** Public contract shared by all evolution animation variants. */
public interface EvolutionAnimation {
  void start();

  void stop();

  boolean isFinished();

  boolean isCallbackFired();

  ItemStack getHeldPickaxe();

  void setOnComplete(Runnable onComplete);
}
