package org.benski.evolv3.models.player;

import org.benski.evolv3.core.abstracts.MongoDocument;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.models.backpack.Backpack;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents a player's profile data stored in MongoDB.
 * Contains player-specific settings, currencies, and embedded preferences.
 */
public final class PlayerProfile extends MongoDocument
{
    /**
     * The player's unique identifier (UUID as string).
     */
    public String id;

    /**
     * The UUID of the player (stored to prevent memory leaks).
     */
    public UUID playerUuid;

    /**
     * Flag indicating if the player is currently in an enchanting process.
     */
    public boolean isEnchanting;

    /**
     * Flag indicating if the player is currently evolving their pickaxe.
     */
    public boolean isEvolving;

    /**
     * Maximum number of pickaxes this player can own.
     */
    public int pickaxeLimit;

    /**
     * Player's total essence currency.
     */
    public int essence;

    /**
     * Player's coin balance (internal currency, independent of Vault).
     */
    public double coins;

    /**
     * Player's preferences (embedded sub-document).
     */
    public PlayerPreferences preferences = new PlayerPreferences();

    /**
     * Player's backpack for storing mined items (embedded sub-document).
     */
    public Backpack backpack = new Backpack();

    /**
     * Stored pickaxes per slot. Each entry is "" (empty) or "id:MATERIAL" (stored pickaxe).
     * List always has exactly 9 entries (one per possible storage slot).
     */
    public List<String> storedPickaxes = new ArrayList<>();

    /**
     * Gets the Player object. May return null if the player is offline.
     *
     * @return The Player object or null if offline
     */
    public Player getPlayer()
    {
        return Bukkit.getPlayer(playerUuid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(Player player)
    {
        this.playerUuid = player.getUniqueId();
        this.id = player.getUniqueId().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCollectionName()
    {
        return "Players";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document toDocument()
    {
        Document doc = new Document();
        doc.put("id", id);
        doc.put("isEnchanting", isEnchanting);
        doc.put("isEvolving", isEvolving);
        doc.put("pickaxeLimit", pickaxeLimit);
        doc.put("essence", essence);
        doc.put("coins", coins);

        if (preferences != null) {
            doc.put("preferences", preferences.toDocument());
        }

        if (backpack != null) {
            doc.put("backpack", backpack.toDocument());
        }

        doc.put("storedPickaxes", storedPickaxes);

        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        this.id = document.getString("id");
        this.playerUuid = UUID.fromString(this.id);
        this.isEnchanting = document.getBoolean("isEnchanting", false);
        this.isEvolving = document.getBoolean("isEvolving", false);
        this.pickaxeLimit = document.getInteger("pickaxeLimit", 0);
        this.essence = document.getInteger("essence", 0);
        Double savedCoins = document.getDouble("coins");
        this.coins = savedCoins != null ? savedCoins : 0.0;

        Document prefsDoc = document.get("preferences", Document.class);
        if (prefsDoc != null) {
            if (this.preferences == null) {
                this.preferences = new PlayerPreferences();
            }
            this.preferences.fromDocument(prefsDoc);
        }

        Document backpackDoc = document.get("backpack", Document.class);
        if (backpackDoc != null) {
            if (this.backpack == null) {
                this.backpack = Backpack.createDefault();
            }
            this.backpack.fromDocument(backpackDoc);
        }

        List<String> stored = document.getList("storedPickaxes", String.class);
        this.storedPickaxes = stored != null ? new ArrayList<>(stored) : new ArrayList<>();
        while (this.storedPickaxes.size() < 9) {
            this.storedPickaxes.add("");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIdField()
    {
        return "id";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getIdValue()
    {
        return this.id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(Consumer<Boolean> callback)
    {
        mongoManager.upsertAsync(this)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        LOGGER.severe("Failed to save player profile: " + throwable.getMessage());
                    }
                    if (callback != null) {
                        callback.accept(throwable == null);
                    }
                });
    }

    /**
     * Creates a new PlayerProfile with default values from config.
     *
     * @param player The player to create the profile for
     * @return A new PlayerProfile with defaults applied
     */
    public static PlayerProfile createDefault(Player player)
    {
        PlayerProfile profile = new PlayerProfile();
        profile.initialize(player);

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.DEFAULT);
        profile.pickaxeLimit = config.getInt(ConfigPaths.Default.Player.DEFAULT_PICKAXE_LIMIT, 1);
        profile.essence = config.getInt(ConfigPaths.Default.Player.DEFAULT_ESSENCE, 0);
        profile.coins = config.getDouble(ConfigPaths.Default.Player.DEFAULT_COINS, 0.0);
        profile.isEnchanting = false;
        profile.isEvolving = false;
        profile.preferences = new PlayerPreferences();
        profile.backpack = Backpack.createDefault();
        profile.storedPickaxes = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            profile.storedPickaxes.add("");
        }

        return profile;
    }

    /**
     * Adds essence to the player's total.
     *
     * @param amount The amount to add
     */
    public void addEssence(int amount)
    {
        this.essence += amount;
    }

    /**
     * Removes essence from the player's total.
     *
     * @param amount The amount to remove
     * @return true if the player had enough essence
     */
    public boolean removeEssence(int amount)
    {
        if (this.essence < amount) return false;
        this.essence -= amount;
        return true;
    }

    /**
     * Checks if the player has at least the specified amount of essence.
     *
     * @param amount The amount to check
     * @return true if the player has enough
     */
    public boolean hasEssence(int amount)
    {
        return this.essence >= amount;
    }

    /**
     * Adds coins to the player's balance.
     *
     * @param amount The amount to add
     */
    public void addCoins(double amount)
    {
        this.coins += amount;
    }

    /**
     * Removes coins from the player's balance.
     *
     * @param amount The amount to remove
     * @return true if the player had enough coins
     */
    public boolean removeCoins(double amount)
    {
        if (this.coins < amount) return false;
        this.coins -= amount;
        return true;
    }

    /**
     * Checks if the player can create another pickaxe.
     *
     * @param currentPickaxeCount The player's current number of pickaxes
     * @return true if the player has available slots
     */
    public boolean canCreatePickaxe(int currentPickaxeCount)
    {
        return currentPickaxeCount < pickaxeLimit;
    }

}
