package org.benski.evolv3.core.managers.pickaxe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.progression.PrestigeAnimation;
import org.benski.evolv3.animations.progression.PrestigeStoneFlightAnimation;
import org.benski.evolv3.api.EvolveApiClient;
import org.benski.evolv3.api.dto.PendingDataDto;
import org.benski.evolv3.api.dto.PendingType;
import org.benski.evolv3.core.types.PendingPrestigeData;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.menus.pickaxe.PrestigeStoneSelectMenu;
import org.benski.evolv3.models.pickaxe.PendingPrestigeDocument;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Manages active and pending prestige stone-selection animations.
 *
 * <h3>Exploit prevention</h3>
 *
 * <ul>
 *   <li><b>Same stones on rejoin</b> — the three stone rolls are generated once and stored in
 *       {@link PendingPrestigeData} before any animation starts. Disconnecting and reconnecting
 *       shows the exact same three options; the player cannot reroll by leaving.
 *   <li><b>Stone recovery</b> — if the player disconnects after selecting a stone but before the
 *       flight animation completes, the selected index is persisted and the flight is replayed on
 *       rejoin so the prestige executes correctly.
 *   <li><b>Server-restart recovery</b> — pending state is written to the API before the prestige
 *       animation begins. If the server restarts mid-flow the player resumes at the stone-selection
 *       screen with the original rolls on their next login.
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

  /**
   * Players whose prestige animation is currently playing. Used to suppress the stone-menu callback
   * when the animation finishes after the player already disconnected and rejoined.
   */
  private final Set<UUID> activeAnimations = ConcurrentHashMap.newKeySet();

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
   * Starts the full prestige selection flow for a player:
   *
   * <ol>
   *   <li>Stores pending state in memory and persists it to the API so it survives a restart.
   *   <li>Plays the {@link PrestigeAnimation}.
   *   <li>On animation completion, opens the stone-selection menu with the pre-rolled stones.
   * </ol>
   *
   * <p>Call this after verifying prestige eligibility and rolling the stones. The callback passed
   * here is the one that will be invoked when a stone is selected on the current session; on a
   * restart it is rebuilt via the registered {@link PrestigeCallbackProvider}.
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

    activeAnimations.add(uuid);
    PrestigeAnimation.play(
        player,
        nextPrestige,
        () -> {
          if (!player.isOnline() || !activeAnimations.remove(uuid)) return;
          openStoneMenu(player);
        });
  }

  /**
   * Records that the player has clicked a stone at {@code stoneIndex} (0–2) and persists the
   * updated state to the API. Must be called before the flight animation begins.
   *
   * @param player the player
   * @param stoneIndex 0, 1, or 2
   */
  public void onStoneSelected(Player player, int stoneIndex) {
    UUID uuid = player.getUniqueId();
    PendingPrestigeData pending = pendingPrestiges.get(uuid);
    if (pending == null) return;
    pending.setSelectedStoneIndex(stoneIndex);
    savePendingToDatabase(uuid, pending);
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
   * Called when a player disconnects. Persists whatever state is currently in memory so it can be
   * restored on rejoin or after a server restart.
   *
   * @param player the disconnecting player
   */
  public void handlePlayerQuit(Player player) {
    UUID uuid = player.getUniqueId();
    activeAnimations.remove(uuid);
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
   *   <li>If a stone was selected ({@code selectedStoneIndex ≥ 0}): replay the flight animation and
   *       execute the prestige.
   *   <li>Otherwise: open the stone-selection menu with the original rolls.
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

          final Runnable finalCallback = executeCallback;
          ItemStack stoneItem = EvolutionStone.create(roll.rarity(), roll.enchantName());

          PrestigeStoneFlightAnimation.play(
              player,
              stoneItem,
              roll.rarity(),
              () -> {
                if (finalCallback != null) finalCallback.run();
                onPrestigeComplete(player);
              });
        } else {
          pendingPrestiges.put(uuid, pending);
          openStoneMenu(player);
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
    activeAnimations.clear();
    pendingPrestiges.clear();
  }

  /** Opens the stone-selection menu for the player using their pending stone rolls. */
  private void openStoneMenu(Player player) {
    PendingPrestigeData pending = pendingPrestiges.get(player.getUniqueId());
    if (pending == null) return;
    PrestigeStoneSelectMenu stoneMenu =
        (PrestigeStoneSelectMenu) MenuRegistry.getInstance().get("prestige_stone_select");
    if (stoneMenu != null) {
      stoneMenu.openForStones(player, pending.getStoneRolls());
    }
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
