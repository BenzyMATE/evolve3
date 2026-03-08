package org.benski.evolv3.core.constants;

/**
 * Internal enchant key constants used for database lookups and pickaxe profile access.
 *
 * <p>These lowercase strings match the {@code name} field stored in
 * {@link org.benski.evolv3.models.pickaxe.EnchantData} documents.
 * Use these constants instead of raw string literals to prevent typos
 * and make refactoring straightforward.</p>
 *
 * <p>For config section lookups (e.g. {@code "Frostbite.Radius"})
 * use the path constants defined in {@link ConfigPaths.Enchants}.</p>
 */
public final class Enchants
{
    private Enchants() {}

    /** Internal key for the Efficiency enchantment. */
    public static final String EFFICIENCY = "efficiency";

    /** Internal key for the Frostbite enchantment. */
    public static final String FROSTBITE  = "frostbite";

    /** Internal key for the Furnace enchantment. */
    public static final String FURNACE    = "furnace";

    /** Internal key for the Alchemy enchantment. */
    public static final String ALCHEMY    = "alchemy";

    /** Internal key for the Unbreaking enchantment. */
    public static final String UNBREAKING = "unbreaking";

    /** Internal key for the Explosive enchantment (registered via Paper enchantment API). */
    public static final String EXPLOSIVE  = "explosive";
}
