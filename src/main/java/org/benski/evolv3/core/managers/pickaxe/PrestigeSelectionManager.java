package org.benski.evolv3.core.managers.pickaxe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.progression.PrestigeAscensionAnimation;
import org.benski.evolv3.api.EvolveApiClient;
import org.benski.evolv3.api.dto.PendingDataDto;
import org.benski.evolv3.api.dto.PendingType;
import org.benski.evolv3.core.types.PendingPrestigeData;
import org.benski.evolv3.models.pickaxe.PendingPrestigeDocument;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Manages active and pending prestige ascension animations.
 *
 * <h3>Exploit prevention</h3>
 *
 * <ul>
 *   <li><b>Same stones on rejoin</b> — the three stone rolls are generated once and stored in
 *       {@link PendingPrestigeData} before any animation starts. Disconnecting and reconnecting
 *       shows the exact same three options; the player cannot reroll by leaving.
 *   <li><b>Server-restart recovery</b> — pending state is written to the API before the animation
 *       begins. If the server restarts mid-flow the player resumes at the same stone options on
 *       their next login.
 *   <li><b>Selection on disconnect</b> — if the player already chose a stone but the outcome hadn't
 *       been applied yet when they disconnected, it is applied immediately on rejoin — no animation
 *       replay needed, since the pickaxe is never held/hidden by the animation.
 *   <li><b>Roll consumption</b> — the calling code is responsible for verifying prestige
 *       eligibility before calling {@link #startPrestige}. This class makes no attempt to reverse a
 *       prestige on cancel.
 * </ul>
 */
public class PrestigeSelectionManager {
  @Getter private static final PrestigeSelectionManager instance = new PrestigeSelectionManager();

  private final JavaPlugin plugin;
  private final Logger logger;

  /**
   * Persistent pending state. Entries survive disconnects and server restarts; removed only when
   * {@link #onPrestigeComplete} is called or the entry is explicitly cancelled.
   */
  private final Map<UUID, PendingPrestigeData> pendingPrestiges = new ConcurrentHashMap<>();

  /** Animations currently playing. Entries are removed on completion or disconnect. */
  private final Map<UUID, PrestigeAscensionAnimation> activeAnimations = new ConcurrentHashMap<>();

  /**
   * Optional provider for reconstructing the execute-prestige callback after a server restart.
   * Register this before {@link #loadPendingFromDatabase()}.
   */
  private PrestigeCallbackProvider callbackProvider;

  /** Functional interface for rebuilding the execute-prestige callback after a server restart. */
  @FunctionalInterface
  public interface PrestigeCallbackProvider {
    /**
     * Builds the runnable that executes the prestige for the selected stone roll.
     *
     * @param player the player who triggered the prestige
     * @param pickaxeKey the pickaxe's item-data key
     * @param roll the chosen stone roll
     * @return the runnable that applies the prestige result
     */
    Runnable buildCallback(Player player, String pickaxeKey, PrestigeManager.StoneRoll roll);
  }

  private PrestigeSelectionManager() {
    this.plugin = Main.getInstance();
    this.logger = plugin.getLogger();
  }

  /**
   * Registers the provider used to reconstruct the execute-prestige callback after a server
   * restart. Call this during plugin {@code onEnable()}, after data centres are ready and before
   * {@link #loadPendingFromDatabase()}.
   *
   * @param provider the callback provider
   */
  public void registerCallbackProvider(PrestigeCallbackProvider provider) {
    this.callbackProvider = provider;
  }

  /**
   * Starts the prestige ascension animation for a player.
   *
   * <p>Call this after verifying prestige eligibility and rolling the stones. The player picks one
   * of the three stones interactively inside the animation (look + left-click); the outcome is
   * applied the instant a choice is locked in.
   *
   * @param player the player
   * @param pickaxeKey the pickaxe item-data key
   * @param nextPrestige the prestige number being unlocked (drives animation visuals)
   * @param stoneRolls the three pre-rolled stone options
   */
  public void startPrestige(
      Player player,
      String pickaxeKey,
      int nextPrestige,
      List<PrestigeManager.StoneRoll> stoneRolls) {
    UUID uuid = player.getUniqueId();

    PendingPrestigeData pending = new PendingPrestigeData(stoneRolls, pickaxeKey, null);
    pendingPrestiges.put(uuid, pending);
    savePendingToDatabase(uuid, pending);

    startAnimation(player, stoneRolls, nextPrestige);
  }

  /**
   * Attempts to confirm the currently-highlighted stone for a player's active animation.
   *
   * @param player the player
   * @return {@code true} if a selection was made
   */
  public boolean confirmSelection(Player player) {
    if (player == null) return false;
    PrestigeAscensionAnimation animation = activeAnimations.get(player.getUniqueId());
    if (animation == null) return false;
    return animation.confirmSelection();
  }

  /**
   * Checks whether a player currently has an active, not-yet-decided prestige animation.
   *
   * @param player the player
   * @return {@code true} if the animation is playing and awaiting a selection
   */
  public boolean hasActiveSelection(Player player) {
    if (player == null) return false;
    PrestigeAscensionAnimation animation = activeAnimations.get(player.getUniqueId());
    return animation != null && !animation.isFinished() && !animation.isSelectionMade();
  }

  /**
   * Marks the prestige as fully executed and deletes the record from the API. Must be called after
   * {@code executePrestige()} completes.
   *
   * @param player the player
   */
  public void onPrestigeComplete(Player player) {
    UUID uuid = player.getUniqueId();
    pendingPrestiges.remove(uuid);
    deletePendingFromDatabase(uuid);
  }

  /**
   * Called when a player disconnects. Stops their active animation (if any) and persists whatever
   * pending state remains so it can be restored on rejoin or after a server restart.
   *
   * @param player the disconnecting player
   */
  public void handlePlayerQuit(Player player) {
    UUID uuid = player.getUniqueId();
    PrestigeAscensionAnimation animation = activeAnimations.remove(uuid);
    if (animation != null && !animation.isFinished()) {
      animation.stop();
    }
    PendingPrestigeData pending = pendingPrestiges.get(uuid);
    if (pending == null) return;
    savePendingToDatabase(uuid, pending);
  }

  /**
   * Called when a player joins the server.
   *
   * <ul>
   *   <li>If {@code callbackAlreadyApplied}: the prestige already ran — delete the record and do
   *       nothing.
   *   <li>If a stone was selected ({@code selectedStoneIndex ≥ 0}) but not yet applied: apply it
   *       immediately.
   *   <li>Otherwise: replay the animation with the original stone rolls.
   * </ul>
   *
   * @param player the joining player
   */
  public void handlePlayerJoin(Player player) {
    UUID uuid = player.getUniqueId();
    PendingPrestigeData pending = pendingPrestiges.remove(uuid);
    if (pending == null) return;

    new BukkitRunnable() {
      @Override
      public void run() {
        if (!player.isOnline()) return;

        if (pending.isCallbackAlreadyApplied()) {
          deletePendingFromDatabase(uuid);
          return;
        }

        if (pending.getSelectedStoneIndex() >= 0) {
          PrestigeManager.StoneRoll roll =
              pending.getStoneRolls().get(pending.getSelectedStoneIndex());

          Runnable executeCallback = pending.getCallback();
          if (executeCallback == null && callbackProvider != null) {
            executeCallback = callbackProvider.buildCallback(player, pending.getPickaxeKey(), roll);
          }
          if (executeCallback != null) executeCallback.run();
          onPrestigeComplete(player);
        } else {
          pendingPrestiges.put(uuid, pending);
          startAnimation(player, pending.getStoneRolls(), resolveNextPrestige(player));
        }
      }
    }.runTaskLater(plugin, 1L);
  }

  /**
   * Loads all pending prestige entries from the API into memory so they are ready when players
   * rejoin. Call this once during {@code onEnable()}, after the API client is initialised and
   * {@link org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig} has been loaded, and
   * after registering the {@link PrestigeCallbackProvider}.
   */
  public void loadPendingFromDatabase() {
    EvolveApiClient api = EvolveApiClient.getInstance();
    if (api == null) return;
    api.getAllPendingDataAsync()
        .thenAccept(
            dtos -> {
              if (dtos == null) return;
              for (PendingDataDto dto : dtos) {
                if (dto.type != PendingType.PRESTIGE || dto.playerUuid == null) continue;
                UUID uuid;
                try {
                  uuid = UUID.fromString(dto.playerUuid);
                } catch (IllegalArgumentException e) {
                  logger.warning("Invalid UUID in pending prestige: " + dto.playerUuid);
                  continue;
                }
                List<PrestigeManager.StoneRoll> rolls = new ArrayList<>();
                if (dto.optionNames != null) {
                  for (String encoded : dto.optionNames) {
                    PrestigeManager.StoneRoll roll = PendingPrestigeDocument.decodeRoll(encoded);
                    if (roll != null) rolls.add(roll);
                  }
                }
                if (rolls.isEmpty()) {
                  logger.warning(
                      "Pending prestige for " + dto.playerUuid + " has no stone rolls — skipping.");
                  continue;
                }
                PendingPrestigeData pending = new PendingPrestigeData(rolls, dto.pickaxeKey, null);
                pending.setSelectedStoneIndex(dto.selectedStoneIndex);
                pending.setCallbackAlreadyApplied(dto.callbackAlreadyApplied);
                pendingPrestiges.put(uuid, pending);
              }
            });
  }

  /**
   * Stops all active animations and clears in-memory state. Called when the plugin is disabled. API
   * records are left intact so they can be restored on next start.
   */
  public void cleanup() {
    for (PrestigeAscensionAnimation animation : activeAnimations.values()) {
      if (animation != null && !animation.isFinished()) {
        animation.stop();
      }
    }
    activeAnimations.clear();
    pendingPrestiges.clear();
  }

  /**
   * Launches a prestige ascension animation using a pre-determined stone list. Used both for fresh
   * starts and for restoring a session after reconnect.
   */
  private void startAnimation(
      Player player, List<PrestigeManager.StoneRoll> stoneRolls, int nextPrestige) {
    UUID uuid = player.getUniqueId();

    PrestigeAscensionAnimation existing = activeAnimations.remove(uuid);
    if (existing != null && !existing.isFinished()) {
      existing.stop();
    }

    PrestigeAscensionAnimation animation =
        new PrestigeAscensionAnimation(
            plugin,
            player,
            nextPrestige,
            stoneRolls,
            (p, roll) -> applyOutcome(p, stoneRolls, roll),
            () -> activeAnimations.remove(uuid));

    activeAnimations.put(uuid, animation);
    animation.start();
  }

  /**
   * Fires the instant a stone is chosen inside the animation: persists the selection, invokes the
   * real execute-prestige callback, and marks the pending record applied.
   */
  private void applyOutcome(
      Player player, List<PrestigeManager.StoneRoll> stoneRolls, PrestigeManager.StoneRoll roll) {
    UUID uuid = player.getUniqueId();
    PendingPrestigeData pending = pendingPrestiges.get(uuid);

    int index = stoneRolls.indexOf(roll);
    String pickaxeKey = pending != null ? pending.getPickaxeKey() : null;

    if (pending != null) {
      pending.setSelectedStoneIndex(index);
      savePendingToDatabase(uuid, pending);
    }

    Runnable executeCallback = pending != null ? pending.getCallback() : null;
    if (executeCallback == null && callbackProvider != null) {
      executeCallback = callbackProvider.buildCallback(player, pickaxeKey, roll);
    }
    if (executeCallback != null) executeCallback.run();

    if (pending != null) {
      pending.setCallbackAlreadyApplied(true);
      savePendingToDatabase(uuid, pending);
    }
  }

  /** Resolves the prestige tier the player is about to unlock, from their live profile. */
  private int resolveNextPrestige(Player player) {
    Pickaxe profile = PrestigeManager.getInstance().getLocalProfile(player);
    return (profile != null ? profile.prestige : 0) + 1;
  }

  /** Upserts a pending prestige record for the given player to the API. */
  private void savePendingToDatabase(UUID uuid, PendingPrestigeData pending) {
    EvolveApiClient api = EvolveApiClient.getInstance();
    if (api == null) return;
    PendingDataDto dto = new PendingDataDto();
    dto.type = PendingType.PRESTIGE;
    dto.playerUuid = uuid.toString();
    dto.pickaxeKey = pending.getPickaxeKey();
    dto.optionNames =
        pending.getStoneRolls().stream().map(PendingPrestigeDocument::encodeRoll).toList();
    dto.selectedStoneIndex = pending.getSelectedStoneIndex();
    dto.callbackAlreadyApplied = pending.isCallbackAlreadyApplied();
    api.upsertPendingDataAsync(dto);
  }

  /** Deletes the pending prestige record for the given player from the API. */
  private void deletePendingFromDatabase(UUID uuid) {
    EvolveApiClient api = EvolveApiClient.getInstance();
    if (api == null) return;
    api.deletePendingDataAsync(uuid, PendingType.PRESTIGE);
  }
}
