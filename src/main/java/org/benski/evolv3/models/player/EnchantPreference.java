package org.benski.evolv3.models.player;

import org.bson.Document;

/**
 * Per-enchant notification and visual effect preferences.
 * Stored as a map entry within {@link PlayerPreferences#enchantPrefs}.
 */
public class EnchantPreference
{
    public boolean message   = true;
    public boolean title     = false;
    public boolean sound     = true;
    public boolean hologram  = true;
    public boolean particles = true;

    public Document toDocument() {
        Document doc = new Document();
        doc.put("message",   message);
        doc.put("title",     title);
        doc.put("sound",     sound);
        doc.put("hologram",  hologram);
        doc.put("particles", particles);
        return doc;
    }

    public static EnchantPreference fromDocument(Document doc) {
        EnchantPreference pref = new EnchantPreference();
        pref.message   = getBool(doc, "message",   true);
        pref.title     = getBool(doc, "title",     false);
        pref.sound     = getBool(doc, "sound",     true);
        pref.hologram  = getBool(doc, "hologram",  true);
        pref.particles = getBool(doc, "particles", true);
        return pref;
    }

    private static boolean getBool(Document doc, String key, boolean def) {
        Boolean val = doc.getBoolean(key);
        return val != null ? val : def;
    }
}
