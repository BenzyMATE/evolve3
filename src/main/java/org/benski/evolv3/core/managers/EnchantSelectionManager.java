package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.EnchantAnimation;
import org.benski.evolv3.core.abstracts.BaseEnchantAnimation;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.PendingEnchantData;
import org.benski.evolv3.models.pickaxe.PendingEnchantDocument;
import org.benski.evolv3.registry.AnimationRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Manages active and pending enchant selection animations.
 *
 * <h3>Exploit prevention</h3>
 * <ul>
 *   <li><b>Same options on rejoin</b> — options are generated once and stored in
 *       {@link PendingEnchantData} before the animation starts. Disconnecting and
 *       reconnecting shows the same three enchants; the player cannot reroll by
 *       leaving.</li>
 *   <li><b>Pickaxe recovery</b> — if a player disconnects while the animation holds
 *       their pickaxe, it is saved into {@link PendingEnchantData} and restored to
 *       their hand the next time they join the server.</li>
 *   <li><b>Server-restart recovery</b> — when a pickaxe key is supplied, the pending
 *       selection is persisted to MongoDB so it survives a full server restart.
 *       The options and pickaxe are restored automatically when the player rejoins.</li>
 *   <li><b>Selection on disconnect</b> — if the player already clicked an enchant
 *       but the animation's callback hadn't fired yet when they disconnected, the
 *       callback is fired immediately so the enchant is applied and the pending
 *       entry is cleared.</li>
 *   <li><b>Roll consumption</b> — the calling code is responsible for deducting the
 *       enchant roll from the player's profile <em>before</em> calling
 *       {@link #startSelection}.  This class makes no attempt to refund rolls on
 *       cancel, so there is no window to exploit reconnects to reclaim a roll.</li>
 * </ul>
 */
public class EnchantSelectionManager
{
    @Getter
    private static final EnchantSelectionManager instance = new EnchantSelectionManager();

    private final JavaPlugin plugin;
    private final Logger logger;

    /** Animations currently playing. Entries are removed on completion or disconnect. */
    private final Map<UUID, BaseEnchantAnimation> activeAnimations = new ConcurrentHashMap<>();

    /**
     * Persistent pending state — survives animation completion and player disconnects.
     * Entries are removed <em>only</em> when the player successfully selects an enchant
     * (callback fires) or when the selection is explicitly cancelled server-side.
     * ConcurrentHashMap is required because {@link #loadPendingFromDatabase()} populates
     * this map from a background DB thread while the main thread may call handlePlayerJoin.
     */
    private final Map<UUID, PendingEnchantData> pendingSelections = new ConcurrentHashMap<>();

    /**
     * Optional provider for reconstructing the apply-enchant callback after a server
     * restart.  Register this from your enchant application code before calling
     * {@link #loadPendingFromDatabase()}.
     */
    private EnchantCallbackProvider callbackProvider;

    // -------------------------------------------------------------------------
    // Callback provider interface
    // -------------------------------------------------------------------------

    /**
     * Functional interface for rebuilding the enchant-apply callback after a server
     * restart, keyed by the pickaxe's item-data identifier.
     */
    @FunctionalInterface
    public interface EnchantCallbackProvider {
        /**
         * Builds the callback that applies the selected enchant to the pickaxe
         * identified by {@code pickaxeKey}.
         *
         * @param pickaxeKey the pickaxe's item-data key
         * @return the apply-enchant callback
         */
        BiConsumer<Player, EnchantOption> buildCallback(String pickaxeKey);
    }

    // -------------------------------------------------------------------------

    private EnchantSelectionManager() {
        this.plugin = Main.getInstance();
        this.logger = plugin.getLogger();
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Registers the provider used to reconstruct enchant callbacks after a server
     * restart.  Call this during plugin {@code onEnable()}, after your data centres
     * are ready, and before {@link #loadPendingFromDatabase()}.
     *
     * @param provider the callback provider
     */
    public void registerCallbackProvider(EnchantCallbackProvider provider) {
        this.callbackProvider = provider;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts an enchant selection animation for a player, generating fresh options.
     *
     * <p>This overload does <em>not</em> persist to MongoDB — suitable for testing
     * or any non-pickaxe context. The enchant roll should be deducted by the caller
     * <em>before</em> this method is invoked.</p>
     *
     * @param player   the player to show the selection to
     * @param onSelect callback invoked with the chosen {@link EnchantOption}
     * @return {@code true} if the animation started successfully
     */
    public boolean startSelection(Player player, BiConsumer<Player, EnchantOption> onSelect) {
        List<EnchantOption> options = EnchantAnimationConfig.generateRandom(3);
        return startSelectionInternal(player, options, onSelect, null);
    }

    /**
     * Starts an enchant selection animation for a player, generating fresh options.
     *
     * <p>Passing a {@code pickaxeKey} enables full server-restart persistence — the
     * pending selection is written to MongoDB and restored automatically when the
     * player reconnects, even after a full server restart. The enchant roll should
     * be deducted by the caller <em>before</em> this method is invoked.</p>
     *
     * @param player     the player to show the selection to
     * @param pickaxeKey the pickaxe's item-data key (used to rebuild the callback on restart)
     * @param onSelect   callback invoked with the chosen {@link EnchantOption}
     * @return {@code true} if the animation started successfully
     */
    public boolean startSelection(Player player, String pickaxeKey,
                                  BiConsumer<Player, EnchantOption> onSelect) {
        List<EnchantOption> options = EnchantAnimationConfig.generateRandom(3);
        return startSelectionInternal(player, options, onSelect, pickaxeKey);
    }

    /**
     * Confirms the currently highlighted selection for a player.
     *
     * @param player the player
     * @return {@code true} if a selection was confirmed
     */
    public boolean confirmSelection(Player player) {
        if (player == null) return false;
        BaseEnchantAnimation animation = activeAnimations.get(player.getUniqueId());
        if (animation == null) return false;
        animation.confirmSelection();
        return true;
    }

    /**
     * Cancels and removes any active or pending selection for a player.
     * Does <em>not</em> refund any consumed enchant rolls.
     *
     * @param player the player
     */
    public void cancelSelection(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        pendingSelections.remove(uuid);
        deletePendingFromDatabase(uuid);
        BaseEnchantAnimation animation = activeAnimations.remove(uuid);
        if (animation != null && animation.isPlaying()) {
            animation.stop();
        }
    }

    /**
     * Checks if a player currently has a running animation.
     *
     * @param player the player
     * @return {@code true} if an animation is actively playing
     */
    public boolean hasActiveSelection(Player player) {
        if (player == null) return false;
        BaseEnchantAnimation animation = activeAnimations.get(player.getUniqueId());
        return animation != null && animation.isPlaying();
    }

    /**
     * Returns the active animation for a player, or {@code null} if none.
     *
     * @param player the player
     * @return the animation, or {@code null}
     */
    public BaseEnchantAnimation getActiveAnimation(Player player) {
        if (player == null) return null;
        return activeAnimations.get(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Disconnect / reconnect handling
    // -------------------------------------------------------------------------

    /**
     * Called when a player disconnects.
     *
     * <ul>
     *   <li>If the player already clicked an enchant, the callback is fired
     *       immediately so the enchant is applied before they leave.</li>
     *   <li>Otherwise, the current options and pickaxe are saved in
     *       {@link PendingEnchantData} so they can be restored on rejoin.
     *       The MongoDB document is already up-to-date from when the animation
     *       started, so no additional DB write is needed here.</li>
     * </ul>
     *
     * @param player the disconnecting player
     */
    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        BaseEnchantAnimation animation = activeAnimations.remove(uuid);
        if (animation == null) return;

        PendingEnchantData pending = pendingSelections.get(uuid);

        // Capture the pickaxe from the animation before stopping it
        if (pending != null && animation instanceof EnchantAnimation enchantAnim) {
            pending.setOriginalItem(enchantAnim.getOriginalItem());
            // Prevent doComplete from trying to return the item since we saved it
            enchantAnim.markItemHandled();
        }

        if (animation.isSelectionMade()) {
            // Player confirmed a choice before leaving — apply it now
            animation.fireCallback();
            pendingSelections.remove(uuid);
            deletePendingFromDatabase(uuid);
        }
        // else: pendingSelections retains the entry; the player will see the same
        //       options when they rejoin, with their pickaxe restored.
        //       The MongoDB document is already persisted from startSelectionInternal.

        animation.stop();
    }

    /**
     * Called when a player joins the server.
     *
     * <p>If a pending selection exists for this player (in memory from a recent
     * disconnect, or loaded from MongoDB after a server restart), their pickaxe is
     * restored and the animation is re-shown with the <em>exact same options</em>.</p>
     *
     * @param player the joining player
     */
    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        PendingEnchantData pending = pendingSelections.remove(uuid);
        if (pending == null) return;

        final PendingEnchantData finalPending = pending;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                // Restore the pickaxe so the animation can recapture it in doStart()
                if (finalPending.getOriginalItem() != null) {
                    player.getInventory().setItemInMainHand(finalPending.getOriginalItem());
                }

                // Use the stored callback, or rebuild it from the provider if null.
                // null means this PendingEnchantData was loaded from MongoDB after a restart
                // and the original lambda is no longer available.
                BiConsumer<Player, EnchantOption> callback = finalPending.getOnSelect();
                if (callback == null && callbackProvider != null
                        && finalPending.getPickaxeKey() != null) {
                    callback = callbackProvider.buildCallback(finalPending.getPickaxeKey());
                }

                // Re-show the animation with the same options — no reroll
                startSelectionInternal(player, finalPending.getOptions(), callback,
                                       finalPending.getPickaxeKey());
            }
        }.runTaskLater(plugin, 1L);
    }

    // -------------------------------------------------------------------------
    // Database persistence
    // -------------------------------------------------------------------------

    /**
     * Loads all pending enchant selections from MongoDB and places them into
     * {@link #pendingSelections} so they are ready when players rejoin.
     *
     * <p>Call this once during {@code onEnable()}, after MongoDB has connected and
     * {@link EnchantAnimationConfig} has been loaded. Any registered
     * {@link EnchantCallbackProvider} should also be set before this call so that
     * players who are already online when the method completes are handled correctly.</p>
     */
    public void loadPendingFromDatabase() {
        MongoManager.getInstance().findAllAsync("PendingEnchants", PendingEnchantDocument.class)
            .thenAccept(docs -> {
                int count = 0;
                for (PendingEnchantDocument doc : docs) {
                    if (doc.playerUuid == null) continue;

                    List<EnchantOption> options = reconstructOptions(doc.optionNames);
                    if (options.isEmpty()) continue;

                    ItemStack item = PendingEnchantDocument.deserializeItem(doc.originalItemBase64);
                    UUID uuid = UUID.fromString(doc.playerUuid);

                    // Callback is null here; handlePlayerJoin will build it from the provider
                    PendingEnchantData pending = new PendingEnchantData(
                        options, null, doc.pickaxeKey);
                    pending.setOriginalItem(item);
                    pendingSelections.put(uuid, pending);
                    count++;
                }
                if (count > 0) {
                    logger.info("EnchantSelectionManager: Loaded " + count
                        + " pending enchant selection(s) from database.");
                }
            })
            .exceptionally(t -> {
                logger.warning("EnchantSelectionManager: Failed to load pending selections"
                    + " from database: " + t.getMessage());
                return null;
            });
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Stops all animations and clears all in-memory state. Called when the plugin
     * is disabled. MongoDB documents are left intact so they can be restored on
     * the next server start.
     */
    public void cleanup() {
        for (BaseEnchantAnimation animation : activeAnimations.values()) {
            if (animation != null && animation.isPlaying()) {
                animation.stop();
            }
        }
        activeAnimations.clear();
        pendingSelections.clear();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Launches an enchant selection animation using a pre-determined option list.
     * Used both for fresh starts and for restoring a session after reconnect.
     *
     * <p>A {@link PendingEnchantDocument} is always upserted to MongoDB after the
     * animation starts, ensuring the pickaxe item and options survive a server restart
     * regardless of whether a {@code pickaxeKey} was provided.</p>
     */
    private boolean startSelectionInternal(Player player, List<EnchantOption> options,
                                           BiConsumer<Player, EnchantOption> onSelect,
                                           String pickaxeKey) {
        if (player == null || !player.isOnline()) return false;

        // Cancel any running animation (but keep pendingSelections — we're about to
        // overwrite it below, so the old entry is superseded)
        BaseEnchantAnimation existing = activeAnimations.remove(player.getUniqueId());
        if (existing != null && existing.isPlaying()) {
            existing.markItemHandled(); // item will be re-taken by the new animation
            existing.stop();
        }

        // Store pending data BEFORE starting — guarantees options are persisted even
        // if the plugin crashes before the wrapped callback fires
        PendingEnchantData pending = new PendingEnchantData(options, onSelect, pickaxeKey);
        pendingSelections.put(player.getUniqueId(), pending);

        // Wrapped callback: clears pending & active on normal completion, deletes from DB
        final UUID uuid = player.getUniqueId();
        BiConsumer<Player, EnchantOption> wrappedCallback = (p, selected) -> {
            activeAnimations.remove(uuid);
            pendingSelections.remove(uuid);
            deletePendingFromDatabase(uuid);
            if (onSelect != null) {
                onSelect.accept(p, selected);
            }
        };

        BaseEnchantAnimation animation =
            new EnchantAnimation(plugin, player, options, wrappedCallback);

        if (!AnimationRegistry.getInstance().canPlay(animation)) {
            pendingSelections.remove(player.getUniqueId());
            return false;
        }

        activeAnimations.put(player.getUniqueId(), animation);
        Location loc = player.getLocation().add(0, 0.5, 0);
        AnimationRegistry.getInstance().play(animation, loc);

        // Capture the original item AFTER play() — doStart() is synchronous, so
        // getOriginalItem() is guaranteed to be populated when play() returns.
        if (!animation.isPlaying()) {
            // Animation stopped immediately (e.g., player went offline during doStart)
            pendingSelections.remove(player.getUniqueId());
            activeAnimations.remove(player.getUniqueId());
            return false;
        }

        ItemStack capturedItem = animation.getOriginalItem();
        if (capturedItem != null) {
            pending.setOriginalItem(capturedItem);
        }

        // Always persist to MongoDB — this guarantees the item and options survive
        // a server restart regardless of whether a pickaxeKey was provided
        savePendingToDatabase(uuid, pending);

        return true;
    }

    /** Upserts a {@link PendingEnchantDocument} for the given player to MongoDB. */
    private void savePendingToDatabase(UUID uuid, PendingEnchantData pending) {
        PendingEnchantDocument doc = new PendingEnchantDocument();
        doc.playerUuid = uuid.toString();
        doc.pickaxeKey = pending.getPickaxeKey();
        for (EnchantOption opt : pending.getOptions()) {
            doc.optionNames.add(opt.name());
        }
        doc.originalItemBase64 = PendingEnchantDocument.serializeItem(pending.getOriginalItem());
        doc.save();
    }

    /** Asynchronously deletes the MongoDB document for the given player UUID. */
    private void deletePendingFromDatabase(UUID uuid) {
        MongoManager.getInstance().deleteAsync("PendingEnchants", "playerUuid", uuid.toString());
    }

    /**
     * Reconstructs a list of {@link EnchantOption}s from stored enchant names.
     * Options whose names no longer exist in the config are skipped with a warning.
     */
    private List<EnchantOption> reconstructOptions(List<String> names) {
        List<EnchantOption> options = new ArrayList<>();
        for (String name : names) {
            EnchantOption opt = EnchantAnimationConfig.getOption(name);
            if (opt != null) {
                options.add(opt);
            } else {
                logger.warning("EnchantSelectionManager: Enchant '" + name
                    + "' no longer exists in config — skipping pending selection option.");
            }
        }
        return options;
    }
}
