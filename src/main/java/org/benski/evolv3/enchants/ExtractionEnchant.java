package org.benski.evolv3.enchants;

import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for the Extraction custom enchantment.
 * Increases the player's base essence discovery chance while mining.
 *
 * <p>At NORMAL priority, writes the bonus chance to {@link #pendingBonus} so
 * {@code BreakListener} (HIGHEST) can include it in the unified essence roll.
 * This means Extraction enhances the existing essence animation rather than
 * firing a separate one — the player simply sees essence more often.</p>
 *
 * <p>Bonus chance scales with enchantment level:</p>
 * <ul>
 *   <li>Bonus = (level / maxLevel) * maxChance</li>
 *   <li>Example: Level 100/100 with maxChance 10 = +10% bonus</li>
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class ExtractionEnchant implements Listener
{
    @Getter
    private static ExtractionEnchant instance;

    /** Pending bonus chance per player, consumed by BreakListener at HIGHEST priority. */
    private static final Map<UUID, Double> pendingBonus = new ConcurrentHashMap<>();

    public ExtractionEnchant()
    {
        instance = this;
    }

    /**
     * Returns and removes the pending essence discovery bonus for a player.
     * Called by {@code BreakListener} after all NORMAL/HIGH enchant listeners have fired.
     *
     * @param uuid the player's UUID
     * @return the bonus chance to add (0.0 if Extraction did not trigger)
     */
    public static double popBonus(UUID uuid)
    {
        Double bonus = pendingBonus.remove(uuid);
        return bonus != null ? bonus : 0.0;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!Utils.isPickaxe(item.getType())) {
            return;
        }

        if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) {
            return;
        }

        String blockName = event.getBlock().getType().name();
        if (blockName.endsWith("_LOG") || blockName.endsWith("_STEM")) {
            return;
        }

        PickaxeDataCenter dataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dataCenter == null) return;
        dataCenter.get(player, profile -> {
            if (profile == null || profile.enchants == null) return;

            EnchantData enchant = profile.enchants.getEnchant(Enchants.EXTRACTION);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

            double maxChance = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.ENCHANTS)
                    .getDouble(ConfigPaths.Enchants.Extraction.MAX_CHANCE, 10.0);
            double maxLevel  = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.ENCHANTS)
                    .getDouble(ConfigPaths.Enchants.Extraction.MAX_LEVEL, 100.0);

            double bonus = (enchant.level / maxLevel) * maxChance;
            pendingBonus.put(player.getUniqueId(), bonus);
        });
    }

    /**
     * Applies the Extraction essence-discovery effect to a single block broken by an AoE enchant.
     * Rolls base essence chance + Extraction bonus, awards essence, and plays animations.
     */
    public void tryTriggerForAoE(Player player, Block block,
                                  PickaxeProfile pickaxeProfile, PlayerProfile playerProfile)
    {
        FileConfiguration defaultCfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        double baseChance = defaultCfg.getDouble(ConfigPaths.Default.Essence.DISCOVERY_CHANCE, 5.0);

        double extractionBonus = 0.0;
        if (pickaxeProfile != null && pickaxeProfile.enchants != null) {
            EnchantData enchant = pickaxeProfile.enchants.getEnchant(Enchants.EXTRACTION);
            if (enchant != null && enchant.enabled && enchant.level > 0) {
                FileConfiguration enchantsCfg = StorageRegistryManager.getInstance()
                        .getConfig(ConfigNames.ENCHANTS);
                double maxChance = enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_CHANCE, 10.0);
                double maxLevel  = enchantsCfg.getDouble(ConfigPaths.Enchants.Extraction.MAX_LEVEL, 100.0);
                extractionBonus  = (enchant.level / maxLevel) * maxChance;
            }
        }

        if (!Utils.chance(baseChance + extractionBonus)) return;

        int min    = defaultCfg.getInt(ConfigPaths.Default.Essence.DISCOVERY_MIN_AMOUNT, 1);
        int max    = defaultCfg.getInt(ConfigPaths.Default.Essence.DISCOVERY_MAX_AMOUNT, 5);
        int amount = Utils.amount(min, max);

        playerProfile.essence += amount;

        Location animLoc     = block.getLocation().clone().add(0.5, 0.5, 0.5);
        Location hologramLoc = block.getLocation().clone().add(0.5, 1.5, 0.5);
        AnimationRegistry.getInstance().play("essence", animLoc, List.of(player), amount);

        if (playerProfile.preferences.essenceHologram
                && defaultCfg.getBoolean(ConfigPaths.Default.Essence.HOLOGRAM_ENABLED, true)) {
            String text = defaultCfg.getString(ConfigPaths.Default.Essence.HOLOGRAM_TEXT,
                    "&#B366D9🔥 &#FFFFFF&l+ %amount% &#B366D9Essence");
            text = text.replace("%amount%", Utils.formatNumber(amount));
            AnimationRegistry.getInstance().play("hologram", hologramLoc, List.of(player), text);
        }
    }
}
