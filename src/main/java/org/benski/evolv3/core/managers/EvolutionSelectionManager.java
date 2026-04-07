package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.EvolutionAnimationD;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.core.types.PendingEvolutionData;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.models.pickaxe.PendingEvolutionDocument;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Manages active and pending evolution animations.
 *
 * <h3>Exploit prevention</h3>
 * <ul>
 *   <li><b>Same rarity on rejoin</b> — the stone rarity is rolled once before the animation
 *       starts and stored in {@link PendingEvolutionData}.  Disconnecting and reconnecting
 *       replays the same animation with the same result; the player cannot reroll by leaving.</li>
 *   <li><b>Pickaxe recovery</b> — if a player disconnects while the animation holds their
 *       pickaxe, it is saved in {@link PendingEvolutionData} and restored to their inventory
 *       the next time they join the server.</li>
 *   <li><b>Server-restart recovery</b> — the pending state is persisted to MongoDB so it
 *       survives a full server restart.  Affected players receive their items on rejoin.</li>
 *   <li><b>Mid-callback disconnect</b> — if the tier change callback already fired (evolution
 *       applied) but items were not yet delivered, the {@code callbackAlreadyApplied} flag is
 *       set.  On rejoin, items are delivered directly without replaying the animation.</li>
 *   <li><b>Roll consumption</b> — the calling code is responsible for deducting the evolution
 *       roll from the player's profile <em>before</em> calling {@link #startEvolution}.  This
 *       class makes no attempt to refund rolls, so there is no window to exploit reconnects to
 *       reclaim a roll.</li>
 * </ul>
 */
public class EvolutionSelectionManager
{
    @Getter
    private static final EvolutionSelectionManager instance = new EvolutionSelectionManager();

    private final JavaPlugin plugin;
    private final Logger logger;

    /** Animations currently playing. Entries are removed on completion or disconnect. */
    private final Map<UUID, EvolutionAnimationD> activeAnimations = new ConcurrentHashMap<>();

    /**
     * Persistent pending state — survives animation completion and player disconnects.
     * Entries are removed only when items are delivered or the entry is explicitly cancelled.
     */
    private final Map<UUID, PendingEvolutionData> pendingEvolutions = new ConcurrentHashMap<>();

    /**
     * Optional provider for reconstructing the apply-evolution callback after a server restart.
     * Register this from your evolution code before calling {@link #loadPendingFromDatabase()}.
     */
    private EvolutionCallbackProvider callbackProvider;

    // -------------------------------------------------------------------------
    // Callback provider interface
    // -------------------------------------------------------------------------

    /**
     * Functional interface for rebuilding the evolution-apply callback after a server restart.
     */
    @FunctionalInterface
    public interface EvolutionCallbackProvider {
        /**
         * Builds the callback that applies the evolution result to the pickaxe.
         *
         * @param player       the player who triggered the evolution
         * @param pickaxeKey   the pickaxe's item-data key
         * @param nextTier     the tier the pickaxe is evolving into
         * @param winningRarity the stone rarity that was rolled
         * @return the runnable that applies the evolution
         */
        Runnable buildCallback(Player player, String pickaxeKey,
                               EvolutionTier nextTier, RarityConfig winningRarity);
    }

    // -------------------------------------------------------------------------

    private EvolutionSelectionManager() {
        this.plugin = Main.getInstance();
        this.logger = plugin.getLogger();
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Registers the provider used to reconstruct evolution callbacks after a server restart.
     * Call this during plugin {@code onEnable()}, after data centres are ready and before
     * {@link #loadPendingFromDatabase()}.
     *
     * @param provider the callback provider
     */
    public void registerCallbackProvider(EvolutionCallbackProvider provider) {
        this.callbackProvider = provider;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts an evolution animation for a player with the given pre-rolled rarity.
     *
     * <p>The evolution roll must be deducted by the caller <em>before</em> this method is
     * invoked.  Passing a non-null {@code pickaxeKey} enables full MongoDB persistence across
     * server restarts.</p>
     *
     * @param player       the player
     * @param pickaxeKey   the pickaxe's item-data key (for DB persistence)
     * @param current      the player's current evolution tier
     * @param next         the tier the player is evolving into
     * @param winningRarity the already-rolled stone rarity (will not be rerolled on rejoin)
     * @param callback     the runnable to apply the evolution result
     */
    public void startEvolution(Player player, String pickaxeKey,
                               EvolutionTier current, EvolutionTier next,
                               RarityConfig winningRarity, Runnable callback) {
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
        EvolutionAnimationD animation = activeAnimations.get(player.getUniqueId());
        return animation != null && !animation.isFinished();
    }

    // -------------------------------------------------------------------------
    // Disconnect / reconnect handling
    // -------------------------------------------------------------------------

    /**
     * Called when a player disconnects.
     *
     * <ul>
     *   <li>If the evolution callback already fired, marks the pending entry as
     *       {@code callbackAlreadyApplied} so items are delivered on rejoin.</li>
     *   <li>Otherwise, the pickaxe and rarity are retained in the pending entry so the
     *       animation can be replayed with the same result when the player rejoins.</li>
     * </ul>
     *
     * @param player the disconnecting player
     */
    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        logger.fine("[ESM-DEBUG] handlePlayerQuit: uuid=" + uuid
            + " activeAnimations.size=" + activeAnimations.size()
            + " pendingEvolutions.size=" + pendingEvolutions.size());
        EvolutionAnimationD animation = activeAnimations.remove(uuid);
        if (animation == null) {
            logger.fine("[ESM-DEBUG] handlePlayerQuit: no active animation found for " + uuid);
            return;
        }

        // Capture state BEFORE stopping (stop() sets finished=true)
        boolean alreadyFired  = animation.isCallbackFired();
        ItemStack heldPickaxe = animation.getHeldPickaxe();

        logger.fine("[ESM-DEBUG] handlePlayerQuit: animation found, callbackFired=" + alreadyFired
            + " heldPickaxe=" + (heldPickaxe != null ? heldPickaxe.getType() : "null"));

        // Stop the animation — prevents the timer from calling cleanup() on the next tick
        animation.stop();

        PendingEvolutionData pending = pendingEvolutions.get(uuid);
        if (pending == null) {
            logger.fine("[ESM-DEBUG] handlePlayerQuit: no pending entry found for " + uuid);
            return;
        }

        // Update pickaxe reference (captured by start() synchronously)
        if (heldPickaxe != null) {
            pending.setOriginalItem(heldPickaxe);
        }

        if (alreadyFired) {
            // Tier change was applied but items not yet delivered.
            // The wrapped callback should have already set this flag and saved to DB,
            // but ensure consistency in case of any ordering edge case.
            pending.setCallbackAlreadyApplied(true);
        }

        // Update DB with the latest pickaxe item (captured from the animation) and flag state
        logger.fine("[ESM-DEBUG] handlePlayerQuit: saving pending, callbackAlreadyApplied="
            + pending.isCallbackAlreadyApplied());
        savePendingToDatabase(uuid, pending);
    }

    /**
     * Called when a player joins the server.
     *
     * <p>If a pending evolution exists (in memory from a recent disconnect, or loaded from
     * MongoDB after a server restart):</p>
     * <ul>
     *   <li>If {@code callbackAlreadyApplied}: delivers the upgraded pickaxe and stone
     *       directly without replaying the animation.</li>
     *   <li>Otherwise: restores the pickaxe and replays the animation with the same rarity.</li>
     * </ul>
     *
     * @param player the joining player
     */
    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        logger.fine("[ESM-DEBUG] handlePlayerJoin: uuid=" + uuid
            + " pendingEvolutions.size=" + pendingEvolutions.size());
        PendingEvolutionData pending = pendingEvolutions.remove(uuid);
        if (pending == null) {
            logger.fine("[ESM-DEBUG] handlePlayerJoin: no pending entry for " + uuid);
            return;
        }
        logger.fine("[ESM-DEBUG] handlePlayerJoin: pending found, callbackAlreadyApplied="
            + pending.isCallbackAlreadyApplied()
            + " originalItem=" + (pending.getOriginalItem() != null ? pending.getOriginalItem().getType() : "null"));

        final PendingEvolutionData finalPending = pending;
        new BukkitRunnable() {
            @Override
            public void run() {
                logger.fine("[ESM-DEBUG] handlePlayerJoin BukkitRunnable fired, online=" + player.isOnline());
                if (!player.isOnline()) return;

                if (finalPending.isCallbackAlreadyApplied()) {
                    // Evolution was already applied — just deliver the items
                    deliverItemsToPlayer(player, finalPending);
                    deletePendingFromDatabase(uuid);
                } else {
                    // Restore the pickaxe so the animation can recapture it in start()
                    if (finalPending.getOriginalItem() != null) {
                        player.getInventory().setItemInMainHand(finalPending.getOriginalItem());
                    }

                    // Use the stored callback, or rebuild it from the provider after a restart
                    Runnable callback = finalPending.getCallback();
                    if (callback == null && callbackProvider != null
                            && finalPending.getPickaxeKey() != null) {
                        callback = callbackProvider.buildCallback(
                                player, finalPending.getPickaxeKey(),
                                finalPending.getNextTier(), finalPending.getWinningRarity());
                    }

                    // Replay the animation with the same rarity — no reroll
                    startEvolutionInternal(player, finalPending.getPickaxeKey(),
                            finalPending.getCurrentTier(), finalPending.getNextTier(),
                            finalPending.getWinningRarity(), callback);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // -------------------------------------------------------------------------
    // Database persistence
    // -------------------------------------------------------------------------

    /**
     * Loads all pending evolution entries from MongoDB into memory so they are ready when
     * players rejoin.
     *
     * <p>Call this once during {@code onEnable()}, after MongoDB has connected and
     * {@link EnchantAnimationConfig} has been loaded, and after registering the
     * {@link EvolutionCallbackProvider}.</p>
     */
    public void loadPendingFromDatabase() {
        MongoManager.getInstance().findAllAsync("PendingEvolutions", PendingEvolutionDocument.class)
            .thenAccept(docs -> {
                int count = 0;
                for (PendingEvolutionDocument doc : docs) {
                    if (doc.playerUuid == null) continue;

                    RarityConfig rarity = EnchantAnimationConfig.getRarityByName(doc.rarityName);
                    if (rarity == null) {
                        logger.warning("EvolutionSelectionManager: Unknown rarity '"
                            + doc.rarityName + "' in pending evolution for " + doc.playerUuid
                            + " — skipping.");
                        continue;
                    }

                    EvolutionTier currentTier = EvolutionTier.fromLevel(doc.currentTierLevel);
                    EvolutionTier nextTier    = EvolutionTier.fromLevel(doc.nextTierLevel);
                    ItemStack item = PendingEvolutionDocument.deserializeItem(doc.originalItemBase64);

                    // Callback is null here; handlePlayerJoin will build it from the provider
                    PendingEvolutionData pending = new PendingEvolutionData(
                            item, currentTier, nextTier, rarity, doc.pickaxeKey, null);
                    pending.setCallbackAlreadyApplied(doc.callbackAlreadyApplied);

                    UUID uuid = UUID.fromString(doc.playerUuid);
                    pendingEvolutions.put(uuid, pending);
                    count++;
                }
                if (count > 0) {
                    logger.info("EvolutionSelectionManager: Loaded " + count
                        + " pending evolution(s) from database.");
                }
            })
            .exceptionally(t -> {
                logger.warning("EvolutionSelectionManager: Failed to load pending evolutions"
                    + " from database: " + t.getMessage());
                return null;
            });
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Stops all active animations and clears in-memory state.  Called when the plugin is
     * disabled.  MongoDB documents are left intact so they can be restored on next start.
     */
    public void cleanup() {
        for (EvolutionAnimationD animation : activeAnimations.values()) {
            if (animation != null && !animation.isFinished()) {
                animation.stop();
            }
        }
        activeAnimations.clear();
        pendingEvolutions.clear();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void startEvolutionInternal(Player player, String pickaxeKey,
                                        EvolutionTier current, EvolutionTier next,
                                        RarityConfig winningRarity, Runnable callback) {
        logger.fine("[ESM-DEBUG] startEvolutionInternal: player=" + (player != null ? player.getName() : "null")
            + " pickaxeKey=" + pickaxeKey + " current=" + current + " next=" + next
            + " rarity=" + (winningRarity != null ? winningRarity.displayName() : "null"));
        if (player == null || !player.isOnline()) {
            logger.fine("[ESM-DEBUG] startEvolutionInternal: player null or offline, aborting");
            return;
        }
        UUID uuid = player.getUniqueId();

        // Cancel any running animation
        EvolutionAnimationD existing = activeAnimations.remove(uuid);
        if (existing != null && !existing.isFinished()) {
            existing.stop();
        }

        // Store pending BEFORE starting — guarantees rarity is persisted even if the plugin
        // crashes before the wrapped callback fires
        PendingEvolutionData pending = new PendingEvolutionData(
                null, current, next, winningRarity, pickaxeKey, callback);
        pendingEvolutions.put(uuid, pending);

        // Wrapped callback fires at tick 238 — marks evolution as applied and persists to DB.
        // Does NOT remove the pending entry; that only happens in onComplete (tick 268, items
        // delivered) so a post-238 disconnect can still recover the pickaxe on rejoin.
        Runnable wrappedCallback = () -> {
            PendingEvolutionData p = pendingEvolutions.get(uuid);
            if (p != null) {
                p.setCallbackAlreadyApplied(true);
                savePendingToDatabase(uuid, p); // persist the flag immediately
            }
            if (callback != null) callback.run();
        };

        EvolutionAnimationD animation =
                new EvolutionAnimationD(plugin, player, current, next, winningRarity, wrappedCallback);

        // onComplete fires at tick 268 only when items were actually delivered to an online player.
        // Safe to fully clean up at this point.
        animation.setOnComplete(() -> {
            activeAnimations.remove(uuid);
            pendingEvolutions.remove(uuid);
            deletePendingFromDatabase(uuid);
        });

        activeAnimations.put(uuid, animation);
        animation.start();

        // Capture the held pickaxe AFTER start() — start() takes it synchronously
        ItemStack capturedPickaxe = animation.getHeldPickaxe();
        if (capturedPickaxe != null) {
            pending.setOriginalItem(capturedPickaxe);
        }

        // Persist to MongoDB — ensures recovery after a server restart
        savePendingToDatabase(uuid, pending);
    }

    /**
     * Delivers the upgraded pickaxe and stone to a player whose evolution was applied
     * before they disconnected.
     */
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
        player.getInventory().addItem(
                EvolutionStone.create(pending.getWinningRarity(), pickRandomEnchant(player)));
    }

    /** Picks a random enchant display name from the player's unlocked enchants, or all enchants if none unlocked. */
    private static String pickRandomEnchant(Player player) {
        PickaxeDataCenter pdc = (PickaxeDataCenter) DataCenterRegistry.getInstance().get("pickaxe");
        if (pdc != null) {
            PickaxeProfile profile = pdc.getLocal(player);
            if (profile != null && !profile.enchants.items.isEmpty()) {
                List<String> displayNames = profile.enchants.items.stream()
                        .map(e -> EnchantAnimationConfig.getDisplayName(e.name))
                        .filter(n -> n != null && !n.isEmpty())
                        .toList();
                if (!displayNames.isEmpty()) {
                    return displayNames.get(ThreadLocalRandom.current().nextInt(displayNames.size()));
                }
            }
        }
        List<String> all = EnchantAnimationConfig.getEnchantOrder();
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }

    /** Upserts a {@link PendingEvolutionDocument} for the given player to MongoDB. */
    private void savePendingToDatabase(UUID uuid, PendingEvolutionData pending) {
        PendingEvolutionDocument doc = new PendingEvolutionDocument();
        doc.playerUuid            = uuid.toString();
        doc.originalItemBase64    = PendingEvolutionDocument.serializeItem(pending.getOriginalItem());
        doc.currentTierLevel      = pending.getCurrentTier().level;
        doc.nextTierLevel         = pending.getNextTier().level;
        doc.rarityName            = pending.getWinningRarity().displayName();
        doc.pickaxeKey            = pending.getPickaxeKey();
        doc.callbackAlreadyApplied = pending.isCallbackAlreadyApplied();
        doc.save();
    }

    /** Asynchronously deletes the MongoDB document for the given player UUID. */
    private void deletePendingFromDatabase(UUID uuid) {
        MongoManager.getInstance().deleteAsync("PendingEvolutions", "playerUuid", uuid.toString());
    }
}
