package org.benski.evolv3.core.abstracts;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.interfaces.Menu;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Abstract base class for menu implementations. Provides common functionality for inventory-based
 * menus. Implements InventoryHolder to allow menus to be identified by their inventory. Supports
 * both static and dynamic titles with configurable refresh intervals.
 */
public abstract class BaseMenu implements Menu, InventoryHolder, Listener {
  protected final JavaPlugin plugin;
  protected final Map<UUID, Inventory> openInventories = new ConcurrentHashMap<>();
  private final Map<UUID, BukkitTask> titleUpdateTasks = new ConcurrentHashMap<>();

  /**
   * Constructs a BaseMenu and registers this instance as a Bukkit event listener.
   *
   * @param plugin the owning plugin
   */
  @SuppressWarnings("this-escape")
  public BaseMenu(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  /**
   * Gets the menu title for the specified player. Override to provide dynamic titles.
   *
   * @param player the player viewing the menu
   * @return the menu title
   */
  protected abstract String getMenuTitle(Player player);

  /**
   * Gets the menu size (number of slots). Must be a multiple of 9, between 9 and 54.
   *
   * @return the menu size
   */
  protected abstract int getMenuSize();

  /**
   * Populates the menu inventory with items. Called when the menu is opened or refreshed.
   *
   * @param player the player viewing the menu
   * @param inventory the inventory to populate
   */
  protected abstract void populateMenu(Player player, Inventory inventory);

  /**
   * Handles click events within the menu. Return true to cancel the click event.
   *
   * @param player the player who clicked
   * @param slot the slot that was clicked
   * @param event the click event
   * @return true to cancel the event
   */
  protected abstract boolean handleClick(Player player, int slot, InventoryClickEvent event);

  /**
   * Determines whether this menu uses a dynamic title. Override and return true to enable dynamic
   * title updates.
   *
   * @return true if the title should update dynamically, false for static title
   */
  protected boolean isDynamicTitle() {
    return false;
  }

  /**
   * Determines whether this menu uses a dynamic title for a specific player. Override to gate the
   * animation on a per-player preference. Defaults to {@link #isDynamicTitle()}.
   *
   * @param player the player opening the menu
   * @return true if the title should update dynamically for this player
   */
  protected boolean isDynamicTitle(Player player) {
    return isDynamicTitle();
  }

  /**
   * Gets the interval in ticks between title updates. Only used when {@link #isDynamicTitle()}
   * returns true. Default is 20 ticks (1 second).
   *
   * @return the title update interval in ticks
   */
  protected int getTitleUpdateInterval() {
    return 20;
  }

  /**
   * Called when the menu is opened for a player. Override to play sounds or run other on-open
   * logic.
   *
   * @param player the player opening the menu
   */
  protected void onOpen(Player player) {}

  /**
   * Called when the menu is closed for a player. Override to play sounds or run other on-close
   * logic.
   *
   * @param player the player closing the menu
   */
  protected void onClose(Player player) {}

  /** {@inheritDoc} */
  @Override
  public void openMenu(Player player) {
    Inventory inventory =
        Bukkit.createInventory(
            this,
            getMenuSize(),
            LegacyComponentSerializer.legacySection()
                .deserialize(formatMessage(player, getMenuTitle(player))));

    populateMenu(player, inventory);

    openInventories.put(player.getUniqueId(), inventory);

    player.openInventory(inventory);

    onOpen(player);

    if (isDynamicTitle(player)) {
      startTitleUpdateTask(player);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void refresh(Player player) {
    Inventory inventory = openInventories.get(player.getUniqueId());
    if (inventory != null) {
      inventory.clear();
      populateMenu(player, inventory);

      player.updateInventory();
    } else {
      openMenu(player);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMenuOpen(Player player) {
    return openInventories.containsKey(player.getUniqueId());
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup(Player player) {
    openInventories.remove(player.getUniqueId());
    stopTitleUpdateTask(player);
  }

  /**
   * Starts the dynamic title update task for a player. Exposed as {@code protected} so subclasses
   * driving their own short-lived animation (e.g. a shutter transition) can pause this periodic
   * task first with {@link #stopTitleUpdateTask} and restart it afterward, instead of having both
   * run concurrently and fight over the title.
   *
   * @param player the player to start updates for
   */
  protected void startTitleUpdateTask(Player player) {
    stopTitleUpdateTask(player);

    int interval = getTitleUpdateInterval();

    BukkitTask task =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  if (!player.isOnline() || !isMenuOpen(player)) {
                    stopTitleUpdateTask(player);
                    return;
                  }

                  updateTitle(player);
                },
                interval,
                interval);

    titleUpdateTasks.put(player.getUniqueId(), task);
  }

  /**
   * Stops the dynamic title update task for a player. See {@link #startTitleUpdateTask} for why
   * this is {@code protected}.
   *
   * @param player the player to stop updates for
   */
  protected void stopTitleUpdateTask(Player player) {
    BukkitTask task = titleUpdateTasks.remove(player.getUniqueId());
    if (task != null && !task.isCancelled()) {
      task.cancel();
    }
  }

  /**
   * Updates the title of the menu for a player. Uses Paper's InventoryView.setTitle() method.
   *
   * @param player the player whose menu title should be updated
   */
  @SuppressWarnings("deprecation")
  protected void updateTitle(Player player) {
    Inventory inventory = openInventories.get(player.getUniqueId());
    if (inventory == null) {
      return;
    }

    if (player.getOpenInventory().getTopInventory() != inventory) {
      return;
    }

    String newTitle = formatMessage(player, getMenuTitle(player));

    try {
      player.getOpenInventory().setTitle(newTitle);
    } catch (Exception e) {
      plugin.getLogger().warning("Error setting title: " + e.getMessage());
    }
  }

  /**
   * Plays the shutter transition sound. Call once at the start of any shutter animation — closing
   * or opening, forward or via a back button — so the audio always lines up with the visual sweep.
   *
   * @param player the player to play the sound for
   */
  protected void playShutterSound(Player player) {
    player.playSound(player.getLocation(), "custom.shutter", 1.0f, 1.0f);
  }

  /**
   * Runs a one-shot frame ramp from {@code startFrame} to {@code endFrame} (inclusive), invoking
   * {@code onFrame} every {@code stepTicks} and {@code onComplete} once after the final frame is
   * processed. Used to drive multi-step title animations (e.g. a shutter transition) — unlike
   * {@link #startTitleUpdateTask}, this is a finite ramp rather than an infinite fixed-interval
   * loop, and the caller controls exactly what happens each frame instead of it always calling
   * {@link #getMenuTitle}.
   *
   * @param player the player to animate for
   * @param startFrame the first frame value passed to {@code onFrame}
   * @param endFrame the last frame value passed to {@code onFrame} (inclusive); may be less than
   *     {@code startFrame} to ramp downward
   * @param stepTicks ticks between frames
   * @param onFrame invoked with the current frame number on every step, including the first
   * @param onComplete invoked once after the final frame has been processed
   * @return the scheduled task
   */
  protected BukkitTask runFrameRamp(
      Player player,
      int startFrame,
      int endFrame,
      int stepTicks,
      IntConsumer onFrame,
      Runnable onComplete) {
    int direction = endFrame >= startFrame ? 1 : -1;

    return new BukkitRunnable() {
      int frame = startFrame;

      @Override
      public void run() {
        if (!player.isOnline()) {
          cancel();
          return;
        }

        onFrame.accept(frame);

        if (frame == endFrame) {
          cancel();
          onComplete.run();
          return;
        }

        frame += direction;
      }
    }.runTaskTimer(plugin, 0L, stepTicks);
  }

  /**
   * Not supported — this menu manages per-player inventories. Use {@link #openInventories} to
   * access a specific player's inventory.
   */
  @Override
  public Inventory getInventory() {
    throw new UnsupportedOperationException(
        "BaseMenu manages per-player inventories. Use openInventories.get(playerUuid) instead.");
  }

  /** Event handler for inventory close. Cleans up resources when a player closes the menu. */
  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }

    if (!(event.getInventory().getHolder() instanceof BaseMenu menu)) {
      return;
    }

    if (menu != this) {
      return;
    }

    onClose(player);
    cleanup(player);
  }

  /**
   * Determines whether clicks in the player's own (bottom) inventory should be cancelled while this
   * menu is open. Defaults to {@code true} — the menu is fully locked down, which is correct for
   * almost every menu. Override to return {@code false} for a menu that needs the player to pick
   * up/place items from their own inventory while it's open (e.g. dragging an item onto the cursor
   * to socket it). Shift-clicks in the player's own inventory are always cancelled regardless,
   * since Bukkit's default shift-click handling would otherwise quick-move the item into an
   * arbitrary top-inventory slot, bypassing {@link #handleClick} entirely.
   *
   * @return {@code true} to cancel all bottom-inventory clicks (default), {@code false} to allow
   *     plain (non-shift) pickup/place clicks there
   */
  protected boolean cancelPlayerInventoryClicks() {
    return true;
  }

  /**
   * Event handler for inventory clicks. Always cancels top-inventory interaction and routes it to
   * {@link #handleClick}. Bottom (player) inventory clicks are cancelled too unless {@link
   * #cancelPlayerInventoryClicks} opts out for this menu.
   */
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }

    Inventory topInventory = player.getOpenInventory().getTopInventory();
    if (!(topInventory.getHolder() instanceof BaseMenu menu)) {
      return;
    }

    if (menu != this) {
      return;
    }

    if (!openInventories.containsKey(player.getUniqueId())) {
      return;
    }

    event.setCancelled(true);

    if (event.getClickedInventory() == topInventory) {
      try {
        handleClick(player, event.getSlot(), event);
      } catch (Exception e) {
        String msg =
            ConfigRegistry.getInstance()
                .get(ConfigNames.MESSAGES)
                .getString(
                    ConfigPaths.Messages.Errors.INTERNAL_ERROR,
                    "&cSomething went wrong. Please try again.");
        player.sendMessage(formatMessage(player, msg));
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error handling menu click", e);
      }
    } else if (event.getClickedInventory() != null
        && !cancelPlayerInventoryClicks()
        && !event.isShiftClick()) {
      event.setCancelled(false);
    }
  }

  /**
   * Sends a success message to the player.
   *
   * @param player the player
   * @param message the message
   */
  protected void sendSuccess(Player player, String message) {
    Messageable.sendSuccess(player, message);
  }

  /**
   * Sends an error message to the player.
   *
   * @param player the player
   * @param message the message
   */
  protected void sendError(Player player, String message) {
    Messageable.sendError(player, message);
  }

  /**
   * Sends an info message to the player.
   *
   * @param player the player
   * @param message the message
   */
  protected void sendInfo(Player player, String message) {
    Messageable.sendInfo(player, message);
  }

  /**
   * Closes the menu for a player.
   *
   * @param player the player
   */
  protected void closeMenu(Player player) {
    player.closeInventory();
    cleanup(player);
  }
}
