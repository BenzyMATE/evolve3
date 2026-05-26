package org.benski.evolv3.listeners.player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.player.PickupAnimationManager;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.inventory.BackpackListener;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Redirects food item drops from mob kills into the killer's backpack.
 *
 * <p>Any item that satisfies {@link org.bukkit.Material#isEdible()} is intercepted from the vanilla
 * drop list and silently added to the player's backpack instead. If the backpack is full the item
 * is left in the normal drop list so it still lands on the ground.
 */
public class FoodDropListener extends BaseListener {
  /** Creates and registers the FoodDropListener. @param plugin the owning plugin */
  public FoodDropListener(JavaPlugin plugin) {
    super(plugin);
  }

  /** Called when entity death occurs. */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onEntityDeath(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null) return;

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return;

    PlayerProfile profile = pdc.getLocal(killer);
    if (profile == null) return;

    List<ItemStack> intercepted = new ArrayList<>();
    Iterator<ItemStack> it = event.getDrops().iterator();
    while (it.hasNext()) {
      ItemStack drop = it.next();
      if (drop == null || !drop.getType().isEdible()) continue;

      boolean added = profile.backpack.addItem(killer, drop.getType(), drop.getAmount(), false);
      if (added) {
        intercepted.add(drop);
        it.remove();
      }
    }

    if (!intercepted.isEmpty()) {
      Location loc = event.getEntity().getLocation().add(0, 1, 0);
      PickupAnimationManager.getInstance()
          .playPickupAnimation(
              killer,
              loc,
              intercepted,
              intercepted.get(0).getType(),
              PreferenceType.DropCategory.NORMAL,
              0);
      profile.save(null);
      BackpackListener.refresh(killer);
    }
  }
}
