package org.benski.evolv3.models.player;

import org.benski.evolv3.core.abstracts.MongoSubDoc;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.types.PreferenceType;
import org.bson.Document;

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
}
