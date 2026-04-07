package org.benski.evolv3.core.types;

import org.benski.evolv3.enums.EvolutionTier;
import org.bukkit.inventory.ItemStack;

/**
 * Holds the state of an in-progress or interrupted evolution animation.
 *
 * <p>Created when an evolution animation begins and kept alive across
 * player disconnects so that:</p>
 * <ul>
 *   <li>The player's pickaxe can be restored on rejoin.</li>
 *   <li>The same stone rarity is shown again — preventing players from
 *       disconnecting to reroll for a better rarity.</li>
 *   <li>If the tier change was already applied when the player disconnected,
 *       the upgraded pickaxe and stone are delivered on rejoin without
 *       replaying the animation.</li>
 * </ul>
 *
 * <p>Entries are removed from
 * {@link org.benski.evolv3.core.managers.EvolutionSelectionManager} only when
 * items are successfully delivered to the player or the entry is explicitly
 * cancelled server-side.</p>
 */
public class PendingEvolutionData
{
    /** The pickaxe taken from the player's hand when the animation started. */
    private ItemStack originalItem;

    /** The player's evolution tier when the animation began. */
    private final EvolutionTier currentTier;

    /** The tier the player is evolving to. */
    private final EvolutionTier nextTier;

    /** The rarity rolled for this evolution — fixed to prevent reroll exploits. */
    private final RarityConfig winningRarity;

    /**
     * The pickaxe's item-data key.  Non-null when started with a key, enabling
     * full MongoDB persistence across server restarts.
     */
    private final String pickaxeKey;

    /**
     * The callback that applies the evolution result (tier change, essence, etc.).
     * {@code null} when this entry was loaded from MongoDB after a restart —
     * the manager rebuilds it via {@link org.benski.evolv3.core.managers.EvolutionSelectionManager.EvolutionCallbackProvider}.
     */
    private Runnable callback;

    /**
     * {@code true} if the evolution callback already fired (tier change applied) but
     * items were not yet delivered to the player.  On rejoin, the manager skips the
     * animation and delivers the pickaxe + stone directly.
     */
    private boolean callbackAlreadyApplied;

    public PendingEvolutionData(ItemStack originalItem,
                                EvolutionTier currentTier,
                                EvolutionTier nextTier,
                                RarityConfig winningRarity,
                                String pickaxeKey,
                                Runnable callback)
    {
        this.originalItem  = originalItem;
        this.currentTier   = currentTier;
        this.nextTier      = nextTier;
        this.winningRarity = winningRarity;
        this.pickaxeKey    = pickaxeKey;
        this.callback      = callback;
    }

    public ItemStack     getOriginalItem()           { return originalItem; }
    public void          setOriginalItem(ItemStack i) { this.originalItem = i; }

    public EvolutionTier getCurrentTier()            { return currentTier; }
    public EvolutionTier getNextTier()               { return nextTier; }
    public RarityConfig  getWinningRarity()          { return winningRarity; }
    public String        getPickaxeKey()             { return pickaxeKey; }

    public Runnable      getCallback()               { return callback; }
    public void          setCallback(Runnable cb)    { this.callback = cb; }

    public boolean       isCallbackAlreadyApplied()                    { return callbackAlreadyApplied; }
    public void          setCallbackAlreadyApplied(boolean applied)    { this.callbackAlreadyApplied = applied; }
}
