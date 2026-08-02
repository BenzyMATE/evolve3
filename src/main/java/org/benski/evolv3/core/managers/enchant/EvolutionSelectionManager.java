package org.benski.evolv3.core.managers.enchant;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.progression.EvolutionAnimation;
import org.benski.evolv3.animations.progression.EvolutionAnimation3;
import org.benski.evolv3.animations.progression.EvolutionElementalConvergenceAnimation;
import org.benski.evolv3.animations.progression.EvolutionGenesisHelixAnimation;
import org.benski.evolv3.animations.progression.EvolutionPhoenixRebirthAnimation;
import org.benski.evolv3.animations.progression.EvolutionRunicSigilAnimation;
import org.benski.evolv3.animations.progression.EvolutionSlotMachineAnimation;
import org.benski.evolv3.animations.progression.EvolutionStarlightCoronationAnimation;
import org.benski.evolv3.animations.progression.EvolutionStormVortexAnimation;
import org.benski.evolv3.animations.progression.EvolutionSupernovaAnimation;
import org.benski.evolv3.animations.progression.EvolutionTitansForgeAnimation;
import org.benski.evolv3.animations.progression.EvolutionVoidCollapseAnimation;
import org.benski.evolv3.api.EvolveApiClient;
import org.benski.evolv3.api.dto.PendingDataDto;
import org.benski.evolv3.api.dto.PendingType;
import org.benski.evolv3.core.types.PendingEvolutionData;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.models.pickaxe.PendingEvolutionDocument;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Manages active and pending evolution animations.
 *
 * <h3>Exploit prevention</h3>
 *
 * <ul>
 *   <li><b>Same rarity on rejoin</b> — the stone rarity is rolled once before the animation starts
 *       and stored in {@link PendingEvolutionData}. Disconnecting and reconnecting replays the same
 *       animation with the same result; the player cannot reroll by leaving.
 *   <li><b>Pickaxe recovery</b> — if a player disconnects while the animation holds their pickaxe,
 *       it is saved in {@link PendingEvolutionData} and restored to their inventory the next time
 *       they join the server.
 *   <li><b>Server-restart recovery</b> — the pending state is persisted to the API so it survives a
 *       full server restart. Affected players receive their items on rejoin.
 *   <li><b>Mid-callback disconnect</b> — if the tier change callback already fired (evolution
 *       applied) but items were not yet delivered, the {@code callbackAlreadyApplied} flag is set.
 *       On rejoin, items are delivered directly without replaying the animation.
 *   <li><b>Roll consumption</b> — the calling code is responsible for deducting the evolution roll
 *       from the player's profile <em>before</em> calling {@link #startEvolution}. This class makes
 *       no attempt to refund rolls, so there is no window to exploit reconnects to reclaim a roll.
 * </ul>
 */
public class EvolutionSelectionManager {
  @Getter private static final EvolutionSelectionManager instance = new EvolutionSelectionManager();

  private final JavaPlugin plugin;
  private final Logger logger;

  /** Animations currently playing. Entries are removed on completion or disconnect. */
  private final Map<UUID, EvolutionAnimation> activeAnimations = new ConcurrentHashMap<>();

  /**
   * Persistent pending state — survives animation completion and player disconnects. Entries are
   * removed only when items are delivered or the entry is explicitly cancelled.
   */
  private final Map<UUID, PendingEvolutionData> pendingEvolutions = new ConcurrentHashMap<>();

  /**
   * Optional provider for reconstructing the apply-evolution callback after a server restart.
   * Register this from your evolution code before calling {@link #loadPendingFromDatabase()}.
   */
  private EvolutionCallbackProvider callbackProvider;

  /** Functional interface for rebuilding the evolution-apply callback after a server restart. */
  @FunctionalInterface
  public interface EvolutionCallbackProvider {
    /**
     * Builds the callback that applies the evolution result to the pickaxe.
     *
     * @param player the player who triggered the evolution
     * @param pickaxeKey the pickaxe's item-data key
     * @param nextTier the tier the pickaxe is evolving into
     * @param winningRarity the stone rarity that was rolled
     * @return the runnable that applies the evolution
     */
    Runnable buildCallback(
        Player player, String pickaxeKey, EvolutionTier nextTier, RarityConfig winningRarity);
  }

  /** Creates a new EvolutionSelectionManager instance. */
  private EvolutionSelectionManager() {
    this.plugin = Main.getInstance();
    this.logger = plugin.getLogger();
  }

  /**
   * Registers the provider used to reconstruct evolution callbacks after a server restart. Call
   * this during plugin {@code onEnable()}, after data centres are ready and before {@link
   * #loadPendingFromDatabase()}.
   *
   * @param provider the callback provider
   */
  public void registerCallbackProvider(EvolutionCallbackProvider provider) {
    this.callbackProvider = provider;
  }

  /**
   * Starts an evolution animation for a player with the given pre-rolled rarity.
   *
   * <p>The evolution roll must be deducted by the caller <em>before</em> this method is invoked.
   * Passing a non-null {@code pickaxeKey} enables full the API persistence across server restarts.
   *
   * @param player the player
   * @param pickaxeKey the pickaxe's item-data key (for DB persistence)
   * @param current the player's current evolution tier
   * @param next the tier the player is evolving into
   * @param winningRarity the already-rolled stone rarity (will not be rerolled on rejoin)
   * @param callback the runnable to apply the evolution result
   */
  public void startEvolution(
      Player player,
      String pickaxeKey,
      EvolutionTier current,
      EvolutionTier next,
      RarityConfig winningRarity,
      Runnable callback) {
    startEvolutionInternal(player, pickaxeKey, current, next, winningRarity, callback);
  }

  /**
   * Returns {@code true} if the player currently has an active evolution animation.
   *
   * @param player the player
   * @return {@code true} if an animation is actively playing
   */
  public boolean hasActiveEvolution(Player player) {
    if (player == null) return false;
    EvolutionAnimation animation = activeAnimations.get(player.getUniqueId());
    return animation != null && !animation.isFinished();
  }

  /**
   * Called when a player disconnects.
   *
   * <ul>
   *   <li>If the evolution callback already fired, marks the pending entry as {@code
   *       callbackAlreadyApplied} so items are delivered on rejoin.
   *   <li>Otherwise, the pickaxe and rarity are retained in the pending entry so the animation can
   *       be replayed with the same result when the player rejoins.
   * </ul>
   *
   * @param player the disconnecting player
   */
  public void handlePlayerQuit(Player player) {
    UUID uuid = player.getUniqueId();
    EvolutionAnimation animation = activeAnimations.remove(uuid);
    if (animation == null) return;

    boolean alreadyFired = animation.isCallbackFired();
    ItemStack heldPickaxe = animation.getHeldPickaxe();

    animation.stop();

    PendingEvolutionData pending = pendingEvolutions.get(uuid);
    if (pending == null) return;

    if (heldPickaxe != null) {
      pending.setOriginalItem(heldPickaxe);
    }

    if (alreadyFired) {
      pending.setCallbackAlreadyApplied(true);
    }

    savePendingToDatabase(uuid, pending);
  }

  /**
   * Called when a player joins the server.
   *
   * <p>If a pending evolution exists (in memory from a recent disconnect, or loaded from the API
   * after a server restart):
   *
   * <ul>
   *   <li>If {@code callbackAlreadyApplied}: delivers the upgraded pickaxe and stone directly
   *       without replaying the animation.
   *   <li>Otherwise: restores the pickaxe and replays the animation with the same rarity.
   * </ul>
   *
   * @param player the joining player
   */
  public void handlePlayerJoin(Player player) {
    UUID uuid = player.getUniqueId();
    PendingEvolutionData pending = pendingEvolutions.remove(uuid);
    if (pending == null) return;

    final PendingEvolutionData finalPending = pending;
    new BukkitRunnable() {
      /** Executes this scheduled task. */
      @Override
      public void run() {
        if (!player.isOnline()) return;

        if (finalPending.isCallbackAlreadyApplied()) {
          deliverItemsToPlayer(player, finalPending);
          deletePendingFromDatabase(uuid);
        } else {
          if (finalPending.getOriginalItem() != null) {
            player.getInventory().setItemInMainHand(finalPending.getOriginalItem());
          }

          Runnable callback = finalPending.getCallback();
          if (callback == null
              && callbackProvider != null
              && finalPending.getPickaxeKey() != null) {
            callback =
                callbackProvider.buildCallback(
                    player,
                    finalPending.getPickaxeKey(),
                    finalPending.getNextTier(),
                    finalPending.getWinningRarity());
          }

          startEvolutionInternal(
              player,
              finalPending.getPickaxeKey(),
              finalPending.getCurrentTier(),
              finalPending.getNextTier(),
              finalPending.getWinningRarity(),
              callback);
        }
      }
    }.runTaskLater(plugin, 1L);
  }

  /**
   * Loads all pending evolution entries from the API into memory so they are ready when players
   * rejoin.
   *
   * <p>Call this once during {@code onEnable()}, after the API client is initialised and {@link
   * EnchantAnimationConfig} has been loaded, and after registering the {@link
   * EvolutionCallbackProvider}.
   */
  public void loadPendingFromDatabase() {
    EvolveApiClient api = EvolveApiClient.getInstance();
    if (api == null) return;
    api.getAllPendingDataAsync()
        .thenAccept(
            dtos -> {
              if (dtos == null) return;
              for (PendingDataDto dto : dtos) {
                if (dto.type != PendingType.EVOLUTION || dto.playerUuid == null) continue;
                UUID uuid;
                try {
                  uuid = UUID.fromString(dto.playerUuid);
                } catch (IllegalArgumentException e) {
                  logger.warning("Invalid UUID in pending evolution: " + dto.playerUuid);
                  continue;
                }
                EvolutionTier current = EvolutionTier.fromLevel(dto.currentTierLevel);
                EvolutionTier next = EvolutionTier.fromLevel(dto.nextTierLevel);
                if (current == null || next == null) {
                  logger.warning("Unknown tier levels in pending evolution for " + dto.playerUuid);
                  continue;
                }
                RarityConfig rarity = EnchantAnimationConfig.getRarityByName(dto.rarityName);
                if (rarity == null) {
                  logger.warning(
                      "Unknown rarity '"
                          + dto.rarityName
                          + "' in pending evolution for "
                          + dto.playerUuid);
                  continue;
                }
                PendingEvolutionData pending =
                    new PendingEvolutionData(null, current, next, rarity, dto.pickaxeKey, null);
                ItemStack item = PendingEvolutionDocument.deserializeItem(dto.originalItemBase64);
                if (item != null) pending.setOriginalItem(item);
                pending.setCallbackAlreadyApplied(dto.callbackAlreadyApplied);
                pendingEvolutions.put(uuid, pending);
              }
            });
  }

  /**
   * Stops all active animations and clears in-memory state. Called when the plugin is disabled. the
   * API documents are left intact so they can be restored on next start.
   */
  public void cleanup() {
    for (EvolutionAnimation animation : activeAnimations.values()) {
      if (animation != null && !animation.isFinished()) {
        animation.stop();
      }
    }
    activeAnimations.clear();
    pendingEvolutions.clear();
  }

  /** Starts evolution internal. */
  private void startEvolutionInternal(
      Player player,
      String pickaxeKey,
      EvolutionTier current,
      EvolutionTier next,
      RarityConfig winningRarity,
      Runnable callback) {
    if (player == null || !player.isOnline()) return;
    UUID uuid = player.getUniqueId();

    EvolutionAnimation existing = activeAnimations.remove(uuid);
    if (existing != null && !existing.isFinished()) {
      existing.stop();
    }

    PendingEvolutionData pending =
        new PendingEvolutionData(null, current, next, winningRarity, pickaxeKey, callback);
    pendingEvolutions.put(uuid, pending);

    Runnable wrappedCallback =
        () -> {
          PendingEvolutionData p = pendingEvolutions.get(uuid);
          if (p != null) {
            p.setCallbackAlreadyApplied(true);
            savePendingToDatabase(uuid, p);
          }
          if (callback != null) callback.run();
        };

    EvolutionAnimation animation =
        switch (resolveEvolutionStyle(player)) {
          case SUPERNOVA ->
              new EvolutionSupernovaAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case GENESIS_HELIX ->
              new EvolutionGenesisHelixAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case TITANS_FORGE ->
              new EvolutionTitansForgeAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case STARLIGHT_CORONATION ->
              new EvolutionStarlightCoronationAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case VOID_COLLAPSE ->
              new EvolutionVoidCollapseAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case PHOENIX_REBIRTH ->
              new EvolutionPhoenixRebirthAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case RUNIC_SIGIL ->
              new EvolutionRunicSigilAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case ELEMENTAL_CONVERGENCE ->
              new EvolutionElementalConvergenceAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case STORM_VORTEX ->
              new EvolutionStormVortexAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          case SLOT_MACHINE ->
              new EvolutionSlotMachineAnimation(
                  plugin, player, current, next, winningRarity, wrappedCallback);
          default ->
              new EvolutionAnimation3(
                  plugin, player, current, next, winningRarity, wrappedCallback);
        };

    animation.setOnComplete(
        () -> {
          activeAnimations.remove(uuid);
          pendingEvolutions.remove(uuid);
          deletePendingFromDatabase(uuid);
        });

    activeAnimations.put(uuid, animation);
    animation.start();

    ItemStack capturedPickaxe = animation.getHeldPickaxe();
    if (capturedPickaxe != null) {
      pending.setOriginalItem(capturedPickaxe);
    }

    savePendingToDatabase(uuid, pending);
  }

  /**
   * Delivers the upgraded pickaxe and stone to a player whose evolution was applied before they
   * disconnected.
   */
  @SuppressWarnings("deprecation")
  private void deliverItemsToPlayer(Player player, PendingEvolutionData pending) {
    ItemStack pickaxe = pending.getOriginalItem();
    if (pickaxe != null && pickaxe.getType() != Material.AIR) {
      pickaxe.setType(pending.getNextTier().material);
      if (player.getInventory().firstEmpty() >= 0) {
        player.getInventory().addItem(pickaxe);
      } else {
        player.getWorld().dropItemNaturally(player.getLocation(), pickaxe);
      }
    }
    player
        .getInventory()
        .addItem(EvolutionStone.create(pending.getWinningRarity(), pickRandomEnchant(player)));
  }

  /**
   * Maps the player's evolution style preference to which {@link EvolutionAnimation} implementation
   * {@link #startEvolutionInternal} constructs. Defaults to {@link
   * PreferenceType.EvolutionStyle#SLOT_MACHINE} if the preference is unavailable.
   */
  private PreferenceType.EvolutionStyle resolveEvolutionStyle(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) return PreferenceType.EvolutionStyle.SLOT_MACHINE;
    PlayerProfile profile = pdc.getLocal(player);
    if (profile == null) return PreferenceType.EvolutionStyle.SLOT_MACHINE;
    return profile.preferences.evolutionStyle;
  }

  /**
   * Picks a random enchant display name from the player's unlocked enchants, or all enchants if
   * none unlocked.
   */
  private static String pickRandomEnchant(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc != null) {
      Pickaxe profile = pdc.getPickaxeForPlayer(player);
      if (profile != null && !profile.enchants.items.isEmpty()) {
        List<String> displayNames =
            profile.enchants.items.stream()
                .map(e -> EnchantAnimationConfig.getDisplayName(e.name))
                .filter(n -> n != null && !n.isEmpty())
                .toList();
        if (!displayNames.isEmpty()) {
          return displayNames.get(ThreadLocalRandom.current().nextInt(displayNames.size()));
        }
      }
    }
    List<String> all = EnchantAnimationConfig.getEnchantOrder();
    if (all.isEmpty()) return "";
    return all.get(ThreadLocalRandom.current().nextInt(all.size()));
  }

  /** Upserts a pending evolution record for the given player to the API. */
  private void savePendingToDatabase(UUID uuid, PendingEvolutionData pending) {
    EvolveApiClient api = EvolveApiClient.getInstance();
    if (api == null) return;
    PendingDataDto dto = new PendingDataDto();
    dto.type = PendingType.EVOLUTION;
    dto.playerUuid = uuid.toString();
    dto.originalItemBase64 = PendingEvolutionDocument.serializeItem(pending.getOriginalItem());
    dto.currentTierLevel = pending.getCurrentTier().level;
    dto.nextTierLevel = pending.getNextTier().level;
    dto.rarityName = pending.getWinningRarity().displayName();
    dto.pickaxeKey = pending.getPickaxeKey();
    dto.callbackAlreadyApplied = pending.isCallbackAlreadyApplied();
    api.upsertPendingDataAsync(dto);
  }

  /** Deletes the pending evolution record for the given player from the API. */
  private void deletePendingFromDatabase(UUID uuid) {
    EvolveApiClient api = EvolveApiClient.getInstance();
    if (api == null) return;
    api.deletePendingDataAsync(uuid, PendingType.EVOLUTION);
  }
}
