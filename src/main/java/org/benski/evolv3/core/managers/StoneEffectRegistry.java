package org.benski.evolv3.core.managers;

import java.util.HashMap;
import java.util.Map;

/**
 * Static registry mapping (enchant internal name, rarity name) pairs to
 * their corresponding Evolution Stone passive effect descriptions and unique names.
 *
 * <p>Keys use the format {@code "internalname.Rarity"} (e.g. {@code "efficiency.Common"}).
 * Enchant names are lowercase as stored in {@link org.benski.evolv3.models.pickaxe.EnchantData#name}.
 * Rarity names match the display names from {@code enchants.yml} (e.g. {@code "Common"}).</p>
 */
public final class StoneEffectRegistry
{
    private static final Map<String, String> EFFECTS = new HashMap<>(128);
    private static final Map<String, String> NAMES   = new HashMap<>(128);

    static {
        // ---- Efficiency ----
        reg("efficiency", "Common",    "Quickstone",           "+100% flat break speed bonus");
        reg("efficiency", "Uncommon",  "Chain Rush",           "Mine 10 blocks in a row triggers a 3s burst");
        reg("efficiency", "Rare",      "Frenzy",               "Mine 5 blocks in a row triggers a 5s burst");
        reg("efficiency", "Epic",      "Atomic Rush",          "Picking up an atom doubles speed for 8s");
        reg("efficiency", "Legendary", "Hyperdrive",           "Each level gives 2x the normal speed");

        // ---- Unbreaking ----
        reg("unbreaking", "Common",    "Temper",               "+5% bonus to negate chance");
        reg("unbreaking", "Uncommon",  "Windfall",             "50% chance on negate to grant 1-10 pickaxe XP");
        reg("unbreaking", "Rare",      "Slow Decay",           "Durability drains 2x slower");
        reg("unbreaking", "Epic",      "Restoration",          "Every 50 negate procs fully restores durability");
        reg("unbreaking", "Legendary", "Eternal",              "Pickaxe never loses durability");

        // ---- Alchemy ----
        reg("alchemy", "Common",    "Amplifier",               "+15% chance to proc Alchemy twice on one block");
        reg("alchemy", "Uncommon",  "Overflow",                "+10% to Alchemy trigger chance");
        reg("alchemy", "Rare",      "Shortcut",                "Chance to skip one upgrade tier entirely");
        reg("alchemy", "Epic",      "Transcend",               "Alchemy results have a 30% chance to land within 2 tiers of the maximum");
        reg("alchemy", "Legendary", "Philosopher's Touch",     "Every block you mine is treated as if Alchemy triggered, regardless of proc chance");

        // ---- AutoSell ----
        reg("autosell", "Common",    "Commission",             "+10% coin value on every auto-sold block");
        reg("autosell", "Uncommon",  "Rapid Sale",             "+5% to AutoSell proc chance");
        reg("autosell", "Rare",      "Side Income",            "10% of auto-sell earnings also award essence");
        reg("autosell", "Epic",      "Gold Rush",              "AutoSell triggers a 15s 1.5x coin multiplier each proc");
        reg("autosell", "Legendary", "Full Liquidation",       "When your backpack fills completely, it automatically sells everything regardless of proc chance");

        // ---- Cascade ----
        reg("cascade", "Common",    "Extension",               "+2 blocks added to cascade chain length");
        reg("cascade", "Uncommon",  "Depth Seeker",            "Cascade prioritises spreading downward, following ore veins deeper into the mine");
        reg("cascade", "Rare",      "Aftershock",              "When the cascade ends, a second smaller cascade at half chain length automatically triggers from the last block broken");
        reg("cascade", "Epic",      "Time Warp",               "Cascade chain speed is instant - all blocks in the chain break simultaneously in one tick");
        reg("cascade", "Legendary", "Infinite Loop",           "Cascade has no chain limit and no cooldown - triggers again immediately if matching blocks are still adjacent");

        // ---- Drill ----
        reg("drill", "Common",    "Deep Reach",       "+1 to drill tunnel depth");
        reg("drill", "Uncommon",  "Broad Bore",        "Drill always mines a 3x2 tunnel instead of 2x2");
        reg("drill", "Rare",      "Tuned Head",        "+5% to Drill proc chance");
        reg("drill", "Epic",      "Parallel Strike",   "Fires a second drill simultaneously 3 blocks to the side");
        reg("drill", "Legendary", "Overcharge",        "+5 to drill tunnel depth");

        // ---- Explosive ----
        reg("explosive", "Common",    "Crater",       "+1 to explosion radius");
        reg("explosive", "Uncommon",  "Instability",  "+0.5% to proc chance per block mined without triggering, resets on proc (max +15%)");
        reg("explosive", "Rare",      "Cluster Bomb", "Fires 2 additional smaller blasts at random nearby positions");
        reg("explosive", "Epic",      "Resonance",    "Each proc adds +1 to the next explosion's radius, stacking up to +3 then resetting");
        reg("explosive", "Legendary", "Supernova",    "Every 5th proc doubles the explosion radius");

        // ---- Extraction ----
        reg("extraction", "Common",    "Harvest",          "+15% to essence discovery bonus");
        reg("extraction", "Uncommon",  "Surge",            "Every 10th block mined triples the extraction bonus for that block");
        reg("extraction", "Rare",      "Lucid Shard",      "When essence is discovered, the amount is doubled");
        reg("extraction", "Epic",      "Surge Capacitor",  "When essence is discovered, the amount is tripled");
        reg("extraction", "Legendary", "Fortune's Favour", "Every 5th essence discovery awards 5× the normal amount");

        // ---- Fortune ----
        reg("fortune", "Common",    "Raised Cap",              "+1 to your Fortune multiplier at all levels");
        reg("fortune", "Uncommon",  "Stack",                   "Every 20 Fortune procs grants +1 bonus block on top of your multiplier");
        reg("fortune", "Rare",      "Gem Seeker",              "20% chance per block break to discover essence independently of the base essence chance");
        reg("fortune", "Epic",      "Wild Strike",             "10% chance on any Fortune proc to triple the multiplier for that single block");
        reg("fortune", "Legendary", "Double Down",              "Doubles your entire Fortune drop multiplier");

        // ---- Frostbite ----
        reg("frostbite", "Common",    "Cold Snap",             "+1 block added to freeze radius");
        reg("frostbite", "Uncommon",  "Frost Vein",            "+3% to Frostbite trigger chance");
        reg("frostbite", "Rare",      "Ice Pick",              "Break a small radius of frozen blocks in one hit, revealing all original drops");
        reg("frostbite", "Epic",      "Cryo Atom",             "5% chance to discover an atom when mining a frozen block");
        reg("frostbite", "Legendary", "Blizzard",              "25% chance on each Frostbite proc to instantly shatter all frozen blocks");

        // ---- Furnace ----
        reg("furnace", "Common",    "Hot Batch",               "+20% chance smelted output is doubled");
        reg("furnace", "Uncommon",  "Forge XP",                "Each smelt proc grants 10% of the mined block's base XP as a bonus");
        reg("furnace", "Rare",      "Alchemical Forge",        "Smelted output has 20% chance to also trigger Alchemy");
        reg("furnace", "Epic",      "Fire Chain",              "When Furnace procs, 5 random nearby blocks are broken and their smelted results added to your backpack");
        reg("furnace", "Legendary", "Universal Smelt",         "Furnace always activates regardless of trigger chance, and smelts all block types");

        // ---- Jackhammer ----
        reg("jackhammer", "Common",    "Quick Draw",           "+1% to Jackhammer proc chance");
        reg("jackhammer", "Uncommon",  "Sharp Eye",            "+2% to Jackhammer proc chance");
        reg("jackhammer", "Rare",      "Dissolution Strike",   "10% of blocks broken by Jackhammer are converted into atoms");
        reg("jackhammer", "Epic",      "Blast Combo",          "50% chance to also trigger Explosive in the same area");
        reg("jackhammer", "Legendary", "Depth Charge",         "30% chance on proc to break a second layer below");

        // ---- Lazer ----
        reg("lazer", "Common",    "Marksman",                  "+2% to Lazer proc chance");
        reg("lazer", "Uncommon",  "Reflect",                   "Beam has a 25% chance to ricochet once off a wall");
        reg("lazer", "Rare",      "Iron Fist",                 "Every 10th Lazer proc also fires a Jackhammer proc at the point of impact");
        reg("lazer", "Epic",      "Quad Beam",                 "Lazer fires in all 4 horizontal directions simultaneously");
        reg("lazer", "Legendary", "Hex Beam",                  "Lazer fires in 4 diagonal directions plus up and down. Combined with Epic: fires in all 10 directions simultaneously");

        // ---- Merchant ----
        reg("merchant", "Common",    "Street Eye",             "+2% to Merchant proc chance");
        reg("merchant", "Uncommon",  "Valuation",              "Coin payout scales with ore rarity");
        reg("merchant", "Rare",      "Golden Window",          "Merchant procs trigger a 15s 1.5x coin multiplier window");
        reg("merchant", "Epic",      "Trade Secret",           "10% of Merchant earnings are mirrored as essence");
        reg("merchant", "Legendary", "Tycoon",                 "Merchant payouts are permanently tripled");

        // ---- Nuclear Shower ----
        reg("nuclear shower", "Common",    "Fallout Zone",     "+3 blocks added to dissolution radius");
        reg("nuclear shower", "Uncommon",  "Unstable",         "+2% to Nuclear Shower proc chance");
        reg("nuclear shower", "Rare",      "Essence Fallout",  "Dissolved blocks have a 30% chance to drop essence");
        reg("nuclear shower", "Epic",      "Domino",           "Each dissolved block has a 2% chance to trigger another Nuclear Shower");
        reg("nuclear shower", "Legendary", "Meltdown",         "Nuclear Shower's dissolution effect lasts double the duration");

        // ---- Radioactive ----
        reg("radioactive", "Common",    "Irradiated",              "+1% to atom discovery chance");
        reg("radioactive", "Uncommon",  "Pinpoint",                "Every 5th Radioactive proc guarantees an atom discovery");
        reg("radioactive", "Rare",      "Double Exposure",         "Radioactive procs have a 20% chance to discover x2 atoms");
        reg("radioactive", "Epic",      "Enriched",                "Atoms discovered via Radioactive are doubled");
        reg("radioactive", "Legendary", "Permanent Irradiation",   "Radioactive permanently increases your base atom discovery chance by 5%");

        // ---- Scavenger ----
        reg("scavenger", "Common",    "Extra Pocket",          "Minimum slots found per proc increased by +1");
        reg("scavenger", "Uncommon",  "Mixed Bag",             "Scavenger also has a chance to find essence alongside slots");
        reg("scavenger", "Rare",      "Prospector",            "10% chance to find atoms instead of slots");
        reg("scavenger", "Epic",      "Double Haul",           "Slot gains are doubled for 10s after each Scavenger proc");
        reg("scavenger", "Legendary", "Compounding",           "Permanently multiplies all future Scavenger gains by 2x");

        // ---- Thor ----
        reg("thor", "Common",    "Conductor",                  "+2% to Thor proc chance");
        reg("thor", "Uncommon",  "Double Strike",              "+1 additional lightning strike per proc");
        reg("thor", "Rare",      "Arctic Strike",              "Sculk grows around the impact point — mine it for a random reward: slot, essence, atom, or 50 coins");
        reg("thor", "Epic",      "Mjolnir",                    "Every 10th Thor proc fires a mega-strike hitting a 3x3 area");
        reg("thor", "Legendary", "Thunderstorm",               "Thor occasionally calls a storm - 10 strikes across the visible mine");

        // ---- Vortex ----
        reg("vortex", "Common",    "Attract",                  "+2% to Vortex proc chance");
        reg("vortex", "Uncommon",  "Enhanced Mag",             "+3% to Vortex proc chance");
        reg("vortex", "Rare",      "Orb Collector",            "Each block absorbed by Vortex has a 15% chance to also yield 1 essence");
        reg("vortex", "Epic",      "Black Hole",               "Every 5th Vortex proc fires beams N/E/S/W pulling blocks up to 10 each side into the vortex");
        reg("vortex", "Legendary", "Infinite Pull",            "Vortex pull radius is permanently doubled");
    }

    private StoneEffectRegistry() {}

    private static void reg(String enchant, String rarity, String name, String effect) {
        String key = buildKey(enchant, rarity);
        EFFECTS.put(key, effect);
        NAMES.put(key, name);
    }

    private static String buildKey(String enchantInternalName, String rarityName) {
        return enchantInternalName.toLowerCase() + "." + rarityName;
    }

    /**
     * Returns the passive effect description for the given enchant and stone rarity,
     * or a generic "No effect defined" message if the combination is not registered.
     *
     * @param enchantInternalName the enchant's internal name (e.g. {@code "efficiency"})
     * @param rarityName          the rarity display name (e.g. {@code "Common"})
     * @return the human-readable effect description
     */
    public static String getEffect(String enchantInternalName, String rarityName) {
        if (enchantInternalName == null || rarityName == null) return "No effect defined.";
        return EFFECTS.getOrDefault(buildKey(enchantInternalName, rarityName), "No effect defined.");
    }

    /**
     * Returns the unique name for the given enchant and stone rarity
     * (e.g. {@code "Hyperdrive"} for Efficiency Legendary), or {@code null} if not registered.
     *
     * @param enchantInternalName the enchant's internal name (e.g. {@code "efficiency"})
     * @param rarityName          the rarity display name (e.g. {@code "Legendary"})
     * @return the stone's unique name, or {@code null}
     */
    public static String getName(String enchantInternalName, String rarityName) {
        if (enchantInternalName == null || rarityName == null) return null;
        return NAMES.get(buildKey(enchantInternalName, rarityName));
    }
}
