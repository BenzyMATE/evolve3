package org.benski.evolv3.core.abstracts;

import java.util.List;
import java.util.function.BiConsumer;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.RarityConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Abstract base class for all enchant-selection animation styles.
 *
 * <p>Manages the common lifecycle: click detection, highlight tracking, auto-selection, and the
 * selection-confirmation flow. Subclasses implement the visual style by overriding the {@code
 * do*()} methods.
 */
public abstract class BaseEnchantAnimation extends BaseAnimation {
  /** The player this selection is being shown to. */
  protected final Player player;

  /** The three enchant options the player will choose from. */
  protected final List<EnchantOption> enchantOptions;

  /** Callback invoked once the player (or auto-select) picks an option. */
  protected final BiConsumer<Player, EnchantOption> onSelect;

  /** Index of the chosen option (-1 until a selection is made). */
  protected int selectedIndex = -1;

  /** Whether a selection has been committed. */
  protected boolean selectionMade = false;

  /** Whether the original item has already been returned to the player. */
  protected boolean itemReturned = false;

  /** The tick at which the selection was made (-1 until then). */
  protected int selectionTick = -1;

  /** The option index the player is currently looking at (-1 if none). */
  protected volatile int highlightedIndex = -1;

  /** Background runnable that resolves which option the player is looking at. */
  private BukkitRunnable clickDetector;

  /**
   * Creates a new enchant selection animation.
   *
   * @param plugin the plugin instance
   * @param player the player receiving the selection
   * @param enchantOptions the three options to present
   * @param onSelect callback invoked with the selected option
   * @param duration animation duration in ticks
   */
  protected BaseEnchantAnimation(
      JavaPlugin plugin,
      Player player,
      List<EnchantOption> enchantOptions,
      BiConsumer<Player, EnchantOption> onSelect,
      int duration) {
    super(plugin, duration);
    this.player = player;
    this.enchantOptions = enchantOptions;
    this.onSelect = onSelect;
  }

  /** {@inheritDoc} */
  @Override
  protected final void onStart() {
    if (!player.isOnline()) {
      stop();
      return;
    }
    startClickDetector();
    doStart();
  }

  /** {@inheritDoc} */
  @Override
  protected final void onTick(double progress) {
    if (!player.isOnline()) {
      stop();
      return;
    }
    doTick(progress);

    if (!selectionMade && getCurrentTick() >= duration - 40) {
      selectBook(0);
    }
  }

  /** {@inheritDoc} */
  @Override
  protected final void onComplete() {
    if (clickDetector != null) {
      try {
        clickDetector.cancel();
      } catch (IllegalStateException ignored) {
      }
    }
    doComplete();
  }

  private void startClickDetector() {
    final BaseEnchantAnimation self = this;
    clickDetector =
        new BukkitRunnable() {
          @Override
          public void run() {
            if (!self.isPlaying() || selectionMade || !player.isOnline()) {
              cancel();
              return;
            }

            if (self.getCurrentTick() < self.getSelectionReadyTick()) {
              return;
            }

            Location eye = player.getEyeLocation();
            Vector direction = eye.getDirection();

            int newHighlight = -1;
            double bestDot = 0.9;

            for (int i = 0; i < enchantOptions.size(); i++) {
              Location optionPos = self.getOptionPosition(i);
              if (optionPos == null) continue;

              Vector toOption = optionPos.toVector().subtract(eye.toVector());
              if (toOption.lengthSquared() < 1e-6) continue;
              toOption = toOption.normalize();

              double dot = direction.dot(toOption);
              if (dot > bestDot) {
                bestDot = dot;
                newHighlight = i;
              }
            }

            if (newHighlight != highlightedIndex) {
              int prev = highlightedIndex;
              highlightedIndex = newHighlight;
              if (prev >= 0) {
                self.onHighlightChanged(prev, false);
              }
              if (newHighlight >= 0) {
                self.onHighlightChanged(newHighlight, true);
              }
            }
          }
        };
    clickDetector.runTaskTimer(plugin, 1L, 1L);
  }

  /**
   * Attempts to confirm the selection for the option the player is currently looking at. Does
   * nothing if a selection has already been made or if the animation is not yet ready to accept
   * selections.
   */
  public void confirmSelection() {
    if (selectionMade) return;
    if (getCurrentTick() < getSelectionReadyTick()) return;
    if (highlightedIndex < 0) return;
    selectBook(highlightedIndex);
  }

  /**
   * Forces a specific option to be selected.
   *
   * @param index the option index (0-based)
   */
  public void selectBook(int index) {
    if (selectionMade) return;
    if (index < 0 || index >= enchantOptions.size()) return;

    selectedIndex = index;
    selectionMade = true;
    selectionTick = getCurrentTick();

    doSelect(index);
  }

  /**
   * Returns the tick number at which the player may start confirming a selection.
   *
   * @return the selection-ready tick
   */
  public abstract int getSelectionReadyTick();

  /**
   * Returns the normalized scroll progress of the strip (0.0 = start, 1.0 = fully stopped). Used by
   * the scoreboard to sync the spinning bar deceleration with the 3D animation. Default returns 1.0
   * (already stopped); {@code EnchantAnimation} overrides.
   *
   * @return linear scroll progress in [0, 1]
   */
  public double getLinearScrollProgress() {
    return 1.0;
  }

  /**
   * Returns the per-slot rarity for the animation's concrete strip, or {@code null} if this
   * animation does not have a strip (e.g. non-CS:GO style). Used by the scoreboard to mirror the
   * exact same colours shown in the 3D display.
   *
   * @return rarity array indexed by strip slot, or {@code null}
   */
  public RarityConfig[] getStripRarities() {
    return null;
  }

  /**
   * Returns the world-space position of the given option display. Used by the click detector to
   * resolve where the player is looking.
   *
   * @param index the option index
   * @return the location, or {@code null} if not yet available
   */
  public abstract Location getOptionPosition(int index);

  /** Called once on animation start after basic checks pass. */
  protected abstract void doStart();

  /**
   * Called each tick.
   *
   * @param progress animation progress 0.0 → 1.0
   */
  protected abstract void doTick(double progress);

  /**
   * Called when the player selects an option.
   *
   * @param index the selected option index
   */
  protected abstract void doSelect(int index);

  /** Called when the animation finishes or is stopped. */
  protected abstract void doComplete();

  /**
   * Called when the highlight changes for a specific option. Default implementation is a no-op;
   * subclasses may override for immediate visual feedback.
   *
   * @param index the option index
   * @param highlighted {@code true} when highlighted, {@code false} when un-highlighted
   */
  protected void onHighlightChanged(int index, boolean highlighted) {}

  /**
   * Returns whether the player has committed to a selection. Used by {@link
   * org.benski.evolv3.core.managers.EnchantSelectionManager} to decide whether to fire the callback
   * on disconnect.
   *
   * @return {@code true} if a selection has been confirmed
   */
  public boolean isSelectionMade() {
    return selectionMade;
  }

  /**
   * Invokes the {@link #onSelect} callback if a valid selection was made. Safe to call multiple
   * times — only the first call has any effect since the animation stops after firing it.
   */
  public void fireCallback() {
    if (onSelect != null && selectedIndex >= 0 && selectedIndex < enchantOptions.size()) {
      onSelect.accept(player, enchantOptions.get(selectedIndex));
    }
  }

  /**
   * Marks the original item as already handled externally (saved to {@link
   * org.benski.evolv3.core.types.PendingEnchantData} for restoration on rejoin). Prevents {@code
   * doComplete} from attempting a double-return.
   */
  public void markItemHandled() {
    this.itemReturned = true;
  }

  /**
   * Returns the item that was taken from the player's hand when the animation started. The default
   * implementation returns {@code null}; subclasses that hold an item should override this.
   *
   * @return the original item, or {@code null}
   */
  public ItemStack getOriginalItem() {
    return null;
  }

  /**
   * @return the player this animation belongs to
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * @return the three enchant options presented by this animation
   */
  public List<EnchantOption> getEnchantOptions() {
    return enchantOptions;
  }

  /**
   * @return the index of the currently highlighted option, or -1 if none
   */
  public int getHighlightedIndex() {
    return highlightedIndex;
  }

  /**
   * @return the index of the selected option, or -1 if not yet selected
   */
  public int getSelectedIndex() {
    return selectedIndex;
  }

  /**
   * @return {@code true} once the animation reaches the selection-ready phase
   */
  public boolean isSelectionPhase() {
    return getCurrentTick() >= getSelectionReadyTick();
  }

  protected double easeOutBack(double x) {
    double c1 = 1.70158;
    double c3 = c1 + 1;
    return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
  }

  protected double easeOutCubic(double x) {
    return 1 - Math.pow(1 - x, 3);
  }

  protected double easeInOutCubic(double x) {
    return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
  }

  protected double easeInCubic(double x) {
    return x * x * x;
  }

  /**
   * Linearly interpolates between two locations.
   *
   * @param a start location
   * @param b end location
   * @param t interpolation factor (0 = a, 1 = b)
   * @return the interpolated location
   */
  protected Location lerpLocation(Location a, Location b, double t) {
    return new Location(
        a.getWorld(),
        a.getX() + (b.getX() - a.getX()) * t,
        a.getY() + (b.getY() - a.getY()) * t,
        a.getZ() + (b.getZ() - a.getZ()) * t);
  }
}
