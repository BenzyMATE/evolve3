package org.benski.evolv3.models.pickaxe;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.function.Consumer;

/**
 * MongoDB document for persisting in-progress evolution animations across server restarts.
 *
 * <p>A document is written when an evolution animation begins and deleted when items are
 * successfully delivered to the player.  On restart, all surviving documents are loaded
 * by {@link org.benski.evolv3.core.managers.EvolutionSelectionManager#loadPendingFromDatabase()}
 * so that affected players receive their items when they rejoin.</p>
 */
public class PendingEvolutionDocument implements MongoCollection
{
    /** The player's UUID string — also the document's primary key. */
    public String playerUuid;

    /** Base64-encoded {@link ItemStack#serializeAsBytes()} of the pickaxe. */
    public String originalItemBase64;

    /** {@link org.benski.evolv3.enums.EvolutionTier#level} before evolution. */
    public int currentTierLevel;

    /** {@link org.benski.evolv3.enums.EvolutionTier#level} after evolution. */
    public int nextTierLevel;

    /** {@link org.benski.evolv3.core.types.RarityConfig#displayName()} of the rolled stone. */
    public String rarityName;

    /** The pickaxe's item-data key, used to rebuild the callback on restart. */
    public String pickaxeKey;

    /**
     * {@code true} if the evolution callback fired before the player disconnected
     * (tier change applied) but items were not yet delivered.
     */
    public boolean callbackAlreadyApplied;

    // -------------------------------------------------------------------------

    @Override public String getCollectionName() { return "PendingEvolutions"; }
    @Override public String getIdField()        { return "playerUuid"; }
    @Override public Object getIdValue()        { return playerUuid; }

    // -------------------------------------------------------------------------

    @Override
    public Document toDocument() {
        Document doc = new Document();
        doc.put("playerUuid",            playerUuid);
        doc.put("originalItemBase64",    originalItemBase64);
        doc.put("currentTierLevel",      currentTierLevel);
        doc.put("nextTierLevel",         nextTierLevel);
        doc.put("rarityName",            rarityName);
        doc.put("pickaxeKey",            pickaxeKey);
        doc.put("callbackAlreadyApplied", callbackAlreadyApplied);
        return doc;
    }

    @Override
    public void fromDocument(Document document) {
        this.playerUuid             = document.getString("playerUuid");
        this.originalItemBase64     = document.getString("originalItemBase64");
        this.currentTierLevel       = document.getInteger("currentTierLevel", 1);
        this.nextTierLevel          = document.getInteger("nextTierLevel",    2);
        this.rarityName             = document.getString("rarityName");
        this.pickaxeKey             = document.getString("pickaxeKey");
        Boolean applied             = document.getBoolean("callbackAlreadyApplied");
        this.callbackAlreadyApplied = applied != null && applied;
    }

    @Override
    public void save(Consumer<Boolean> callback) {
        MongoManager.getInstance().upsertAsync(this)
            .whenComplete((result, throwable) -> {
                if (callback != null) callback.accept(throwable == null);
            });
    }

    @Override
    public void save() { save(null); }

    // -------------------------------------------------------------------------
    // Item serialisation helpers (same pattern as PendingEnchantDocument)
    // -------------------------------------------------------------------------

    public static String serializeItem(ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            Main.getInstance().getLogger().warning(
                "PendingEvolutionDocument: failed to serialize ItemStack: " + e.getMessage());
            return null;
        }
    }

    public static ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (Exception e) {
            Main.getInstance().getLogger().warning(
                "PendingEvolutionDocument: failed to deserialize ItemStack: " + e.getMessage());
            return null;
        }
    }
}
