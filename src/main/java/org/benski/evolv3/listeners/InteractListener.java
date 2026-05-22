package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.PrestigeManager;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class InteractListener extends BaseListener {
  private final MenuRegistry menuRegistry = MenuRegistry.getInstance();

  /**
   * Constructor for BaseListener.
   *
   * @param plugin The plugin instance.
   */
  public InteractListener(JavaPlugin plugin) {
    super(plugin);
  }

  /**
   * When a player right-clicks (or shift+right-clicks) while holding a pickaxe. Shift+right-click
   * opens the prestige confirm menu if the pickaxe is eligible; otherwise opens the normal pickaxe
   * menu.
   */
  @EventHandler
  public void openPickaxeMenu(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getAction().isLeftClick()) return;

    var player = event.getPlayer();
    if (!player.getInventory().getItemInMainHand().getType().toString().contains("PICKAXE")) return;

    if (player.isSneaking()) {
      Pickaxe profile = PrestigeManager.getInstance().getLocalProfile(player);
      if (profile != null && PickaxeManager.getInstance().isPrestigeAvailable(profile)) {
        var menu = menuRegistry.get("prestige_confirm");
        if (menu != null) {
          event.setCancelled(true);
          menu.openMenu(player);
        }
        return;
      }
    }

    var menu = menuRegistry.get("pickaxe");
    if (menu != null) {
      menu.openMenu(player);
    }
  }
}
