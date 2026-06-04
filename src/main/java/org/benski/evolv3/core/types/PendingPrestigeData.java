package org.benski.evolv3.core.types;

import java.util.List;
import org.benski.evolv3.core.managers.pickaxe.PrestigeManager;

/**
 * Holds the state of an in-progress or interrupted prestige stone selection.
 *
 * <ul>
 *   <li><b>Same stones on rejoin</b> — the three stone rolls are generated once before the prestige
 *       animation starts and stored here. Disconnecting and reconnecting shows the exact same
 *       slots; the player cannot reroll by leaving.
 *   <li><b>Stone recovery</b> — if the player disconnects after selecting a stone but before the
 *       flight animation completes, the selection is preserved and the flight is replayed on
 *       rejoin.
 *   <li><b>Complete-state tracking</b> — once {@code callbackAlreadyApplied} is set the prestige
 *       has executed; the record can be deleted without taking any further action.
 * </ul>
 */
public class PendingPrestigeData {

  /** The three stone options presented to the player — never re-rolled after creation. */
  private final List<PrestigeManager.StoneRoll> stoneRolls;

  /**
   * Index into {@link #stoneRolls} of the stone the player clicked, or {@code -1} if the player has
   * not yet made a selection.
   */
  private int selectedStoneIndex;

  /** The pickaxe item-data key used to rebuild the execute-prestige callback after a restart. */
  private final String pickaxeKey;

  /**
   * The runnable that executes the prestige (resets pickaxe, delivers stone, etc.). {@code null}
   * when this entry was loaded from the API after a server restart — rebuilt via the registered
   * {@link
   * org.benski.evolv3.core.managers.pickaxe.PrestigeSelectionManager.PrestigeCallbackProvider}.
   */
  private Runnable callback;

  /**
   * {@code true} once {@code executePrestige()} has been called. On rejoin the record is deleted
   * and no further action is taken.
   */
  private boolean callbackAlreadyApplied;

  public PendingPrestigeData(
      List<PrestigeManager.StoneRoll> stoneRolls, String pickaxeKey, Runnable callback) {
    this.stoneRolls = stoneRolls;
    this.pickaxeKey = pickaxeKey;
    this.callback = callback;
    this.selectedStoneIndex = -1;
  }

  public List<PrestigeManager.StoneRoll> getStoneRolls() {
    return stoneRolls;
  }

  public int getSelectedStoneIndex() {
    return selectedStoneIndex;
  }

  public void setSelectedStoneIndex(int index) {
    this.selectedStoneIndex = index;
  }

  public String getPickaxeKey() {
    return pickaxeKey;
  }

  public Runnable getCallback() {
    return callback;
  }

  public void setCallback(Runnable cb) {
    this.callback = cb;
  }

  public boolean isCallbackAlreadyApplied() {
    return callbackAlreadyApplied;
  }

  public void setCallbackAlreadyApplied(boolean applied) {
    this.callbackAlreadyApplied = applied;
  }
}
