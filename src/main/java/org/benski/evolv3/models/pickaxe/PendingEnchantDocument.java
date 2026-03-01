package org.benski.evolv3.models.pickaxe;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.MongoCollection;
import org.benski.evolv3.core.managers.MongoManager;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * MongoDB document for persisting in-progress enchant selections across server restarts.
 *
 * <p>A document is written when a pickaxe-keyed enchant selection begins and deleted
 * when the player successfully selects an enchant or the selection is cancelled
 * server-side.  On restart, all surviving documents are loaded by
 * {@link org.benski.evolv3.core.managers.EnchantSelectionManager#loadPendingFromDatabase()}
 * so that affected players see the same options when they rejoin.</p>
 */
public class PendingEnchantDocument implements MongoCollection
{
    /** The player's UUID string — also the document's primary key. */
    public String playerUuid;

    /** Display names of the enchant options presented to the player (e.g. "Haste I"). */
    public List<String> optionNames = new ArrayList<>();

    /** Base64-encoded {@link ItemStack#serializeAsBytes()} of the pickaxe. */
    public String originalItemBase64;

    /**
     * The pickaxe's item-data key.  Stored so the apply-enchant callback can be
     * reconstructed from {@link org.benski.evolv3.core.managers.EnchantSelectionManager.EnchantCallbackProvider}
     * after a restart.
     */
    public String pickaxeKey;

    // -------------------------------------------------------------------------

    @Override
    public String getCollectionName() { return "PendingEnchants"; }

    @Override
    public String getIdField()        { return "playerUuid"; }

    @Override
    public Object getIdValue()        { return playerUuid; }

    // -------------------------------------------------------------------------

    @Override
    public Document toDocument() {
        Document doc = new Document();
        doc.put("playerUuid",         playerUuid);
        doc.put("optionNames",        optionNames);
        doc.put("originalItemBase64", originalItemBase64);
        doc.put("pickaxeKey",         pickaxeKey);
        return doc;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fromDocument(Document document) {
        this.playerUuid         = document.getString("playerUuid");
        List<String> names      = (List<String>) document.get("optionNames");
        this.optionNames        = names != null ? names : new ArrayList<>();
        this.originalItemBase64 = document.getString("originalItemBase64");
        this.pickaxeKey         = document.getString("pickaxeKey");
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
    // Item serialisation helpers
    // -------------------------------------------------------------------------

    /**
     * Serialises an {@link ItemStack} to a Base64 string for MongoDB storage.
     *
     * @param item the item to serialise, may be {@code null}
     * @return Base64 string, or {@code null} on failure
     */
    public static String serializeItem(ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            Main.getInstance().getLogger().warning(
                "PendingEnchantDocument: failed to serialize ItemStack: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserialises an {@link ItemStack} from a Base64 string.
     *
     * @param base64 the Base64 string, may be {@code null}
     * @return the item, or {@code null} on failure
     */
    public static ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (Exception e) {
            Main.getInstance().getLogger().warning(
                "PendingEnchantDocument: failed to deserialize ItemStack: " + e.getMessage());
            return null;
        }
    }
}
