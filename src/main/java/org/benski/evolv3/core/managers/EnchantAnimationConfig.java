package org.benski.evolv3.core.managers;

import net.kyori.adventure.text.format.TextColor;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loads and caches enchant animation configuration from {@code enchants.yml}.
 *
 * <p>Call {@link #load()} once after {@link org.benski.evolv3.registry.ConfigRegistry}
 * has been initialised. The static query methods ({@link #rollRarity()},
 * {@link #generateRandom(int)}, {@link #getEnchantItem(String)}) are then
 * safe to call from anywhere.</p>
 */
public final class EnchantAnimationConfig
{
    private static final List<RarityConfig>        RARITIES         = new ArrayList<>();
    private static final Map<String, List<String>> RARITY_POOLS     = new HashMap<>();
    private static final Map<String, Material>     ENCHANT_ITEMS    = new HashMap<>();
    private static final Map<String, RarityConfig> ENCHANT_RARITIES = new HashMap<>();
    private static final List<String>              ENCHANT_ORDER        = new ArrayList<>();
    private static final Map<String, Integer>      ENCHANT_MAX_LEVELS   = new HashMap<>();
    private static final Map<String, Integer>      ENCHANT_COSTS        = new HashMap<>();
    private static final Map<String, String>       ENCHANT_TEXTURES     = new HashMap<>();
    /** Maps lowercase internal name → original display name (e.g. "haste i" → "Haste I"). */
    private static final Map<String, String>       ENCHANT_DISPLAY_NAMES = new HashMap<>();
    private static final Map<String, Double>       ENCHANT_MAX_CHANCES   = new HashMap<>();
    private static final Map<String, List<String>> ENCHANT_LORES         = new HashMap<>();
    private static final java.util.Set<String>     PASSIVE_ENCHANTS      = new java.util.HashSet<>();
    private static int totalWeight = 0;

    private EnchantAnimationConfig() {}

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Reads {@code enchants.yml} and populates all rarity and selection-pool data.
     * Must be called after {@link ConfigRegistry} is initialised.
     */
    public static void load() {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
        if (config == null) return;

        RARITIES.clear();
        RARITY_POOLS.clear();
        ENCHANT_ITEMS.clear();
        ENCHANT_RARITIES.clear();
        ENCHANT_ORDER.clear();
        ENCHANT_MAX_LEVELS.clear();
        ENCHANT_COSTS.clear();
        ENCHANT_TEXTURES.clear();
        ENCHANT_DISPLAY_NAMES.clear();
        ENCHANT_MAX_CHANCES.clear();
        ENCHANT_LORES.clear();
        PASSIVE_ENCHANTS.clear();
        totalWeight = 0;

        loadRarities(config);
        loadSelectionPool(config);
    }

    private static void loadRarities(FileConfiguration config) {
        ConfigurationSection section =
            config.getConfigurationSection(ConfigPaths.Enchants.RARITIES_SECTION);
        if (section == null) return;

        int cumulative = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection rs = section.getConfigurationSection(key);
            if (rs == null) continue;

            String itemStr  = rs.getString(ConfigPaths.Enchants.RARITY_ITEM, "WHITE_CONCRETE");
            String colorStr = rs.getString(ConfigPaths.Enchants.RARITY_COLOR, "#FFFFFF");
            int    chance   = rs.getInt(ConfigPaths.Enchants.RARITY_CHANCE, 0);

            Material mat = Material.matchMaterial(itemStr);
            if (mat == null) mat = Material.WHITE_CONCRETE;

            TextColor textColor = TextColor.fromHexString(colorStr);
            if (textColor == null) textColor = TextColor.fromHexString("#FFFFFF");

            Color particleColor = parseHexColor(colorStr);

            cumulative += chance;
            RARITIES.add(new RarityConfig(key, textColor, particleColor, mat, chance, cumulative));
            RARITY_POOLS.put(key, new ArrayList<>());
        }
        totalWeight = cumulative;
    }

    private static void loadSelectionPool(FileConfiguration config) {
        ConfigurationSection poolSection =
            config.getConfigurationSection(ConfigPaths.Enchants.SELECTION_POOL_SECTION);
        if (poolSection == null) return;

        for (String key : poolSection.getKeys(false)) {
            // The SelectionPool value is simply the rarity name (e.g. "Common").
            String rarityKey = poolSection.getString(key, "Common");

            // Display data lives in the enchant's own top-level config section.
            ConfigurationSection es = config.getConfigurationSection(key);
            if (es == null) continue;

            String       name      = es.getString(ConfigPaths.Enchants.ENCHANT_NAME, key);
            String       itemStr   = es.getString(ConfigPaths.Enchants.ENCHANT_ITEM, "BOOK");
            int          maxLevel  = es.getInt(ConfigPaths.Enchants.ENCHANT_MAX_LEVEL, 100);
            int          cost      = es.getInt(ConfigPaths.Enchants.ENCHANT_COST, 100);
            String       texture   = es.getString(ConfigPaths.Enchants.ENCHANT_TEXTURE, "");
            double       maxChance = es.getDouble(ConfigPaths.Enchants.ENCHANT_MAX_CHANCE, 0.0);
            boolean      passive   = es.getBoolean(ConfigPaths.Enchants.ENCHANT_PASSIVE, false);
            List<String> lore      = es.getStringList(ConfigPaths.Enchants.ENCHANT_LORE);

            Material mat = Material.matchMaterial(itemStr);
            if (mat == null) mat = Material.BOOK;

            ENCHANT_ITEMS.put(name, mat);
            ENCHANT_ORDER.add(name);
            ENCHANT_DISPLAY_NAMES.put(name.toLowerCase(), name);
            if (passive) PASSIVE_ENCHANTS.add(name);
            ENCHANT_MAX_LEVELS.put(name, maxLevel);
            ENCHANT_COSTS.put(name, cost);
            ENCHANT_MAX_CHANCES.put(name, maxChance);
            if (!lore.isEmpty()) {
                ENCHANT_LORES.put(name, lore);
            }
            if (texture != null && !texture.isEmpty()) {
                ENCHANT_TEXTURES.put(name, texture);
            }

            RarityConfig rarity = findRarity(rarityKey);
            if (rarity != null) {
                ENCHANT_RARITIES.put(name, rarity);
                RARITY_POOLS.computeIfAbsent(rarityKey, k -> new ArrayList<>()).add(name);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * Performs a weighted-random rarity roll using the chances defined in config.
     *
     * @return the rolled {@link RarityConfig}, or the first rarity as a fallback
     */
    public static RarityConfig rollRarity() {
        if (RARITIES.isEmpty()) return null;
        int roll = ThreadLocalRandom.current().nextInt(Math.max(totalWeight, 1));
        for (RarityConfig rarity : RARITIES) {
            if (roll < rarity.cumulativeChance()) return rarity;
        }
        return RARITIES.get(0);
    }

    /**
     * Generates a list of random {@link EnchantOption}s from the selection pool.
     *
     * <p>Each option is produced by rolling a rarity first, then picking a random
     * enchant from that rarity's pool, matching the weighted-random feel of the
     * CS:GO-style strip.</p>
     *
     * @param count number of options to generate
     * @return list of randomly selected enchant options
     */
    public static List<EnchantOption> generateRandom(int count) {
        List<EnchantOption> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RarityConfig rarity = rollRarity();
            if (rarity == null) continue;

            List<String> pool = RARITY_POOLS.getOrDefault(rarity.displayName(), Collections.emptyList());
            if (pool.isEmpty()) {
                // Fallback: pick from the full pool if this rarity has no enchants yet
                List<String> allNames = new ArrayList<>(ENCHANT_ITEMS.keySet());
                if (!allNames.isEmpty()) {
                    String name = allNames.get(ThreadLocalRandom.current().nextInt(allNames.size()));
                    RarityConfig fallbackRarity = ENCHANT_RARITIES.getOrDefault(name, rarity);
                    options.add(new EnchantOption(name, fallbackRarity));
                }
                continue;
            }

            String name = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            options.add(new EnchantOption(name, rarity));
        }
        return options;
    }

    /**
     * Returns the representative icon material for a named enchant.
     *
     * @param enchantName the enchant display name (e.g. "Haste I")
     * @return the material, or {@link Material#BOOK} if not found
     */
    public static Material getEnchantItem(String enchantName) {
        return ENCHANT_ITEMS.getOrDefault(enchantName, Material.BOOK);
    }

    /**
     * Returns the {@link EnchantOption} for a named enchant, or {@code null} if
     * the enchant is not in the selection pool.  Used when reconstructing options
     * from a persisted {@link org.benski.evolv3.models.pickaxe.PendingEnchantDocument}.
     *
     * @param enchantName the display name (e.g. "Haste I")
     * @return the option, or {@code null} if not found
     */
    public static EnchantOption getOption(String enchantName) {
        RarityConfig rarity = ENCHANT_RARITIES.get(enchantName);
        if (rarity == null) return null;
        return new EnchantOption(enchantName, rarity);
    }

    /**
     * Returns an unmodifiable view of the loaded rarities in config order.
     *
     * @return list of rarities (Common → Legendary by default)
     */
    public static List<RarityConfig> getRarities() {
        return Collections.unmodifiableList(RARITIES);
    }

    /**
     * Returns {@code true} if at least one rarity was loaded successfully.
     *
     * @return true if config has been loaded
     */
    public static boolean isLoaded() {
        return !RARITIES.isEmpty();
    }

    /**
     * Returns the ordered list of all enchant display names from the selection pool,
     * in the order they appear in {@code enchants.yml}.
     *
     * @return unmodifiable ordered list of enchant display names
     */
    public static List<String> getEnchantOrder() {
        return Collections.unmodifiableList(ENCHANT_ORDER);
    }

    /**
     * Returns {@code true} if the named enchant is passive (no triggered notifications or effects).
     *
     * @param enchantName the enchant display name
     * @return true if passive
     */
    public static boolean isPassive(String enchantName) {
        return PASSIVE_ENCHANTS.contains(enchantName);
    }

    /**
     * Returns the maximum level for a named enchant.
     *
     * @param enchantName the enchant display name (e.g. "Haste I")
     * @return the max level, or 100 as default
     */
    public static int getEnchantMaxLevel(String enchantName) {
        return ENCHANT_MAX_LEVELS.getOrDefault(enchantName, 100);
    }

    /**
     * Returns the essence cost per level for a named enchant.
     * Actual upgrade cost = cost * currentLevel.
     *
     * @param enchantName the enchant display name (e.g. "Haste I")
     * @return the base cost per level, or 100 as default
     */
    public static int getEnchantCost(String enchantName) {
        return ENCHANT_COSTS.getOrDefault(enchantName, 100);
    }

    /**
     * Returns the skull texture URL for a named enchant, or {@code null} if none is set.
     * When non-null, use this URL with {@code PlayerTextures.setSkin()} to create a skull icon.
     *
     * @param enchantName the enchant display name (e.g. "Efficiency")
     * @return the texture URL string, or {@code null}
     */
    public static String getEnchantTexture(String enchantName) {
        return ENCHANT_TEXTURES.get(enchantName);
    }

    /**
     * Returns the maximum trigger chance at max level for a named enchant.
     * Returns {@code 0.0} for passive enchants that are always active.
     *
     * @param enchantName the enchant display name (e.g. "Auto-Smelt")
     * @return the max chance percentage, or 0.0 if passive
     */
    public static double getEnchantMaxChance(String enchantName) {
        return ENCHANT_MAX_CHANCES.getOrDefault(enchantName, 0.0);
    }

    /**
     * Returns the lore template lines for a named enchant as defined in {@code enchants.yml}.
     * Tokens in these strings ({@code {level}}, {@code {maxLevel}}, {@code {cost}},
     * {@code {chance}}, {@code {rarity}}, {@code {status}}) must be replaced by the caller.
     *
     * @param enchantName the enchant display name (e.g. "Haste I")
     * @return the lore lines, or an empty list if not configured
     */
    public static List<String> getEnchantLore(String enchantName) {
        return ENCHANT_LORES.getOrDefault(enchantName, Collections.emptyList());
    }

    /**
     * Converts an internal enchant name (lowercase, as stored in {@link org.benski.evolv3.models.pickaxe.EnchantData})
     * back to the original display name from config (e.g. "haste i" → "Haste I").
     *
     * @param internalName the lowercase internal name
     * @return the display name, or the internalName itself if not found
     */
    public static String getDisplayName(String internalName) {
        return ENCHANT_DISPLAY_NAMES.getOrDefault(internalName.toLowerCase(), internalName);
    }

    /**
     * Creates the representative icon {@link ItemStack} for a named enchant.
     *
     * <p>If the enchant has a skull texture URL configured, a {@link Material#PLAYER_HEAD}
     * with that skin is returned. Otherwise the material icon from config is used,
     * falling back to {@link Material#BOOK}.</p>
     *
     * @param enchantName the enchant display name (e.g. "Haste I")
     * @return a new ItemStack — caller should apply display name and lore separately
     */
    public static ItemStack createEnchantItem(String enchantName) {
        String texture = ENCHANT_TEXTURES.get(enchantName);
        if (texture != null && !texture.isEmpty()) {
            try {
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    UUID uuid = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
                    org.bukkit.profile.PlayerProfile pp = Bukkit.createPlayerProfile(uuid);
                    org.bukkit.profile.PlayerTextures textures = pp.getTextures();
                    textures.setSkin(URI.create(texture).toURL());
                    pp.setTextures(textures);
                    meta.setOwnerProfile(pp);
                    skull.setItemMeta(meta);
                }
                return skull;
            } catch (Exception ignored) {
                // Fall through to material fallback
            }
        }
        Material mat = ENCHANT_ITEMS.getOrDefault(enchantName, Material.BOOK);
        return new ItemStack(mat);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static RarityConfig findRarity(String key) {
        for (RarityConfig r : RARITIES) {
            if (r.displayName().equals(key)) return r;
        }
        return null;
    }

    private static Color parseHexColor(String hex) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }
}
