package org.benski.evolv3.models.player;

import org.benski.evolv3.core.abstracts.MongoSubDoc;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.types.PreferenceType;
import org.bson.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores player preferences as an embedded sub-document within PlayerProfile.
 * Simplified to 3 main preference categories: animations, messages, and enchant style.
 */
public class PlayerPreferences extends MongoSubDoc
{
    /**
     * Animation preference - controls visual/sound effects.
     */
    public PreferenceType.Animation animation = PreferenceType.Animation.ENABLED;

    /**
     * Message preference - controls notification display method.
     */
    public PreferenceType.Message message = PreferenceType.Message.CHAT;

    /**
     * Enchant style preference - controls enchanting animation style.
     */
    public PreferenceType.EnchantStyle enchantStyle = PreferenceType.EnchantStyle.CLASSIC;

    /** Which pickup animation plays when a block is mined. */
    public PreferenceType.PickupEffect pickupEffect = PreferenceType.PickupEffect.DEFAULT;

    /** Enchant selection animation speed. */
    public PreferenceType.EnchantSpeed enchantAnimationSpeed = PreferenceType.EnchantSpeed.NORMAL;

    // ── Essence animation component toggles ──────────────────────────────────
    public boolean essenceSound     = true;
    public boolean essenceParticles = true;
    public boolean essenceHologram  = true;
    public boolean essenceGlow      = true;

    // ── Atom animation component toggles ─────────────────────────────────────
    public boolean atomSound     = true;
    public boolean atomParticles = true;
    public boolean atomHologram  = true;
    public boolean atomGlow      = true;

    // ── Per-enchant notification / effect toggles ─────────────────────────
    /** Key = enchant display name (e.g. "Furnace"). Created on first access with defaults. */
    public Map<String, EnchantPreference> enchantPrefs = new HashMap<>();

    /**
     * Returns the preference for the named enchant, creating defaults if absent.
     *
     * @param enchantDisplayName the enchant display name (e.g. "Furnace")
     * @return the preference, never null
     */
    public EnchantPreference getOrCreateEnchantPref(String enchantDisplayName) {
        return enchantPrefs.computeIfAbsent(enchantDisplayName, k -> new EnchantPreference());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document toDocument()
    {
        Document doc = new Document();
        doc.put("animation", animation.name());
        doc.put("message", message.name());
        doc.put("enchantStyle", enchantStyle.name());
        doc.put("pickupEffect", pickupEffect.name());
        doc.put("enchantAnimationSpeed", enchantAnimationSpeed.name());
        doc.put("essenceSound",     essenceSound);
        doc.put("essenceParticles", essenceParticles);
        doc.put("essenceHologram",  essenceHologram);
        doc.put("essenceGlow",      essenceGlow);
        doc.put("atomSound",        atomSound);
        doc.put("atomParticles",    atomParticles);
        doc.put("atomHologram",     atomHologram);
        doc.put("atomGlow",         atomGlow);
        Document enchantPrefsDoc = new Document();
        for (Map.Entry<String, EnchantPreference> entry : enchantPrefs.entrySet()) {
            enchantPrefsDoc.put(entry.getKey(), entry.getValue().toDocument());
        }
        doc.put("enchantPrefs", enchantPrefsDoc);
        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        this.animation = Utils.safeEnumParse(
                document.getString("animation"),
                PreferenceType.Animation.class,
                PreferenceType.Animation.ENABLED
        );

        this.message = Utils.safeEnumParse(
                document.getString("message"),
                PreferenceType.Message.class,
                PreferenceType.Message.CHAT
        );

        this.enchantStyle = Utils.safeEnumParse(
                document.getString("enchantStyle"),
                PreferenceType.EnchantStyle.class,
                PreferenceType.EnchantStyle.CLASSIC
        );

        this.pickupEffect = Utils.safeEnumParse(
                document.getString("pickupEffect"),
                PreferenceType.PickupEffect.class,
                PreferenceType.PickupEffect.DEFAULT
        );

        this.enchantAnimationSpeed = Utils.safeEnumParse(
                document.getString("enchantAnimationSpeed"),
                PreferenceType.EnchantSpeed.class,
                PreferenceType.EnchantSpeed.NORMAL
        );

        this.essenceSound     = getBoolOrDefault(document, "essenceSound",     true);
        this.essenceParticles = getBoolOrDefault(document, "essenceParticles", true);
        this.essenceHologram  = getBoolOrDefault(document, "essenceHologram",  true);
        this.essenceGlow      = getBoolOrDefault(document, "essenceGlow",      true);
        this.atomSound        = getBoolOrDefault(document, "atomSound",        true);
        this.atomParticles    = getBoolOrDefault(document, "atomParticles",    true);
        this.atomHologram     = getBoolOrDefault(document, "atomHologram",     true);
        this.atomGlow         = getBoolOrDefault(document, "atomGlow",         true);
        Document enchantPrefsDoc = document.get("enchantPrefs", Document.class);
        if (enchantPrefsDoc != null) {
            for (String key : enchantPrefsDoc.keySet()) {
                Object val = enchantPrefsDoc.get(key);
                if (val instanceof Document d) {
                    this.enchantPrefs.put(key, EnchantPreference.fromDocument(d));
                }
            }
        }
    }

    /**
     * Checks if animations are enabled for this player.
     * @return true if animations are enabled
     */
    public boolean isAnimationsEnabled()
    {
        return animation == PreferenceType.Animation.ENABLED;
    }

    /**
     * Checks if messages are enabled for this player.
     * @return true if messages are not disabled
     */
    public boolean isMessagesEnabled()
    {
        return message != PreferenceType.Message.DISABLED;
    }

    /**
     * Checks if messages should be displayed as titles.
     * @return true if title messages are preferred
     */
    public boolean usesTitleMessages()
    {
        return message == PreferenceType.Message.TITLE;
    }

    /**
     * Checks if messages should be displayed in chat.
     * @return true if chat messages are preferred
     */
    public boolean usesChatMessages()
    {
        return message == PreferenceType.Message.CHAT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(String parentId, String playerId, Utils utils) {
    }

    private static boolean getBoolOrDefault(Document doc, String key, boolean def) {
        Boolean val = doc.getBoolean(key);
        return val != null ? val : def;
    }
}
