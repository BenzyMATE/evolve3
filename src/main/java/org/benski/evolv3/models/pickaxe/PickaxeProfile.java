package org.benski.evolv3.models.pickaxe;

import org.benski.evolv3.core.abstracts.MongoDocument;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles the pickaxe data on a player's pickaxe.
 */
public class PickaxeProfile extends MongoDocument
{
    /**
     * Defines the key/id of the player's pickaxe.
     */
    public String key;

    /**
     * Defines the custom name of the pickaxe.
     */
    public String name;

    /**
     * Defines the UUID of the owner (stored instead of Player to prevent memory leaks).
     */
    public UUID ownerUuid;

    /**
     * Gets the owner Player object. May return null if the player is offline.
     * @return The Player object or null if offline
     */
    public Player getOwner() {
        return Bukkit.getPlayer(ownerUuid);
    }

    /**
     * Defines the pickaxe's level.
     */
    public int level;

    /**
     * Defines the pickaxe's xp.
     */
    public int xp;

    /**
     * Defines the target xp.
     */
    public int target;

    /**
     * Defines the evolution of the pickaxe.
     */
    public int evolution;

    /**
     * Defines the current essence the player has collected.
     */
    public int currentEssence;

    /**
     * Defines the target essence the player needs to gain.
     */
    public int targetEssence;

    /**
     * Defines the Enchant Rolls the player has.
     */
    public int enchantRolls;

    /**
     * The number of atoms the player has collected toward the next evolution roll.
     */
    public int evolutionAtoms;

    /**
     * The number of atoms required to earn an evolution roll.
     */
    public int atomTarget;

    /**
     * The number of evolution rolls the player has available to spend.
     */
    public int evolutionRolls;

    /**
     * The enchantments on this pickaxe.
     */
    public PickaxeEnchants enchants = new PickaxeEnchants();

    /**
     * Saved durability damage value (0 = full, higher = more damaged).
     * Persisted so durability survives deposit/withdraw cycles.
     */
    public int durability = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(Player player)
    {
        this.ownerUuid = player.getUniqueId();
    }

    /**
     * Creates a new PickaxeProfile with default values from config.
     *
     * @param player The player who owns the pickaxe
     * @param id     The unique identifier for this pickaxe
     * @return A new PickaxeProfile with default values
     */
    public static PickaxeProfile createDefault(Player player, String id)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        PickaxeProfile profile = new PickaxeProfile();

        profile.key = id;
        profile.ownerUuid = player.getUniqueId();
        profile.name = config.getString(ConfigPaths.Default.Pickaxe.NAME, "&bPickaxe");
        profile.level = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_LEVEL, 1);
        profile.xp = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_XP, 0);
        profile.target = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_TARGET, 100);
        profile.evolution = 1;
        profile.currentEssence = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_CURRENT_ESSENCE, 0);
        profile.targetEssence = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ESSENCE_TARGET, 100);
        profile.enchantRolls = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ENCHANT_ROLLS, 0);
        profile.evolutionAtoms = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_EVOLUTION_ATOMS, 0);
        profile.atomTarget = config.getInt(ConfigPaths.Default.Atoms.DEFAULT_ATOM_TARGET, 100);
        profile.evolutionRolls = 0;
        profile.enchants = new PickaxeEnchants();

        return profile;
    }

    /**
     * Gets the progress bar string for XP display.
     *
     * @return A formatted progress bar string
     */
    public String getProgressBar()
    {
        return Utils.generateProgressBar(this.xp, this.target);
    }

    /**
     * Gets the XP progress as a percentage string.
     *
     * @return Progress percentage (e.g., "45%")
     */
    public String getPercentage()
    {
        if (target <= 0) {
            return "0%";
        }
        return Math.round((double) xp / target * 100) + "%";
    }

    /**
     * Gets the progress as a decimal (0.0 to 1.0).
     *
     * @return Progress as decimal
     */
    public double getProgress()
    {
        if (target <= 0) {
            return 0.0;
        }
        return (double) xp / target;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCollectionName()
    {
        return "Pickaxes";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Document toDocument()
    {
        Document doc = new Document();
        doc.put("id", key);
        doc.put("name", name);
        doc.put("owner", ownerUuid.toString());
        doc.put("level", level);
        doc.put("xp", xp);
        doc.put("target", target);
        doc.put("evolution", evolution);
        doc.put("evolutionAtoms", evolutionAtoms);
        doc.put("atomTarget", atomTarget);
        doc.put("evolutionRolls", evolutionRolls);
        doc.put("enchantRolls", enchantRolls);
        doc.put("currentEssence", currentEssence);
        doc.put("targetEssence", targetEssence);
        doc.put("enchants", enchants.toDocument());
        doc.put("durability", durability);
        return doc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromDocument(Document document)
    {
        this.key = document.getString("id");
        this.name = document.getString("name");
        this.ownerUuid = UUID.fromString(document.getString("owner"));
        this.level = document.getInteger("level", 1);
        this.xp = document.getInteger("xp", 0);
        this.target = document.getInteger("target", 100);
        this.evolution = document.getInteger("evolution", 1);
        this.evolutionAtoms = document.getInteger("evolutionAtoms", 0);
        int savedAtomTarget = document.getInteger("atomTarget", 0);
        this.atomTarget = savedAtomTarget > 0 ? savedAtomTarget : 100;
        this.evolutionRolls = document.getInteger("evolutionRolls", 0);
        this.enchantRolls = document.getInteger("enchantRolls", 0);
        this.currentEssence = document.getInteger("currentEssence", 0);
        int savedTargetEssence = document.getInteger("targetEssence", 0);
        this.targetEssence = savedTargetEssence > 0 ? savedTargetEssence
                : ConfigRegistry.getInstance().get(ConfigNames.DEFAULT)
                        .getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ESSENCE_TARGET, 100);

        this.durability = document.getInteger("durability", 0);

        Document enchantsDoc = document.get("enchants", Document.class);
        if (enchantsDoc != null) {
            this.enchants.fromDocument(enchantsDoc);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIdField() {
        return "id";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getIdValue() {
        return this.key;
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
                        super.LOGGER.severe("Failed to save pickaxe profile: " + throwable.getMessage());
                    }
                    if (callback != null) {
                        callback.accept(throwable == null);
                    }
                });
    }
}
