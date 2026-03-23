package org.benski.evolv3.enchants;

import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.PickaxeManager;
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
 * Listener for the Radioactive custom enchantment.
 * Increases the player's base atom discovery chance while mining.
 *
 * <p>At NORMAL priority, writes the bonus chance to {@link #pendingBonus} so
 * {@code BreakListener} (HIGHEST) can include it in the unified atom roll.
 * Radioactive enhances the existing atom animation rather than firing a
 * separate one — the player simply discovers atoms more often.</p>
 *
 * <p>Bonus chance scales with enchantment level:</p>
 * <ul>
 *   <li>Bonus = (level / maxLevel) * maxChance</li>
 *   <li>Example: Level 100/100 with maxChance 7.5 = +7.5% bonus</li>
 * </ul>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class RadioactiveEnchant implements Listener
{
    @Getter
    private static RadioactiveEnchant instance;

    /** Pending bonus chance per player, consumed by BreakListener at HIGHEST priority. */
    private static final Map<UUID, Double> pendingBonus = new ConcurrentHashMap<>();

    public RadioactiveEnchant()
    {
        instance = this;
    }

    /**
     * Returns and removes the pending atom discovery bonus for a player.
     * Called by {@code BreakListener} after all NORMAL/HIGH enchant listeners have fired.
     *
     * @param uuid the player's UUID
     * @return the bonus chance to add (0.0 if Radioactive did not trigger)
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

            EnchantData enchant = profile.enchants.getEnchant(Enchants.RADIOACTIVE);
            if (enchant == null || !enchant.enabled || enchant.level <= 0) return;

            double maxChance = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.ENCHANTS)
                    .getDouble(ConfigPaths.Enchants.Radioactive.MAX_CHANCE, 7.5);
            double maxLevel = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.ENCHANTS)
                    .getDouble(ConfigPaths.Enchants.Radioactive.MAX_LEVEL, 100.0);

            double bonus = (enchant.level / maxLevel) * maxChance;
            pendingBonus.put(player.getUniqueId(), bonus);
        });
    }

    /**
     * Applies the Radioactive atom-discovery effect to a single block broken by an AoE enchant.
     * Rolls base atom chance + Radioactive bonus, awards atoms, and plays animations.
     */
    public void tryTriggerForAoE(Player player, Block block,
                                  PickaxeProfile pickaxeProfile, PlayerProfile playerProfile)
    {
        FileConfiguration defaultCfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        double baseChance = defaultCfg.getDouble(ConfigPaths.Default.Atoms.DISCOVERY_CHANCE, 1.0);

        double radioactiveBonus = 0.0;
        if (pickaxeProfile != null && pickaxeProfile.enchants != null) {
            EnchantData enchant = pickaxeProfile.enchants.getEnchant(Enchants.RADIOACTIVE);
            if (enchant != null && enchant.enabled && enchant.level > 0) {
                FileConfiguration enchantsCfg = StorageRegistryManager.getInstance()
                        .getConfig(ConfigNames.ENCHANTS);
                double maxChance = enchantsCfg.getDouble(ConfigPaths.Enchants.Radioactive.MAX_CHANCE, 7.5);
                double maxLevel  = enchantsCfg.getDouble(ConfigPaths.Enchants.Radioactive.MAX_LEVEL, 100.0);
                radioactiveBonus = (enchant.level / maxLevel) * maxChance;
            }
        }

        if (!Utils.chance(baseChance + radioactiveBonus)) return;

        int min    = defaultCfg.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MIN_AMOUNT, 1);
        int max    = defaultCfg.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MAX_AMOUNT, 3);
        int amount = Utils.amount(min, max);

        pickaxeProfile.evolutionAtoms += amount;
        PickaxeManager.getInstance().checkAtoms(pickaxeProfile, player);

        Location animLoc     = block.getLocation().clone().add(0.5, 0.5, 0.5);
        Location hologramLoc = block.getLocation().clone().add(0.5, 1.5, 0.5);
        AnimationRegistry.getInstance().play("atom", animLoc, List.of(player), amount);

        boolean atomHologramOn = playerProfile == null || playerProfile.preferences.atomHologram;
        if (atomHologramOn && defaultCfg.getBoolean(ConfigPaths.Default.Atoms.HOLOGRAM_ENABLED, true)) {
            String text = defaultCfg.getString(ConfigPaths.Default.Atoms.HOLOGRAM_TEXT,
                    "&#FFEE00⚛ &#FFFFFF&l+ %amount% &#FFEE00Atoms");
            text = text.replace("%amount%", Utils.formatNumber(amount));
            AnimationRegistry.getInstance().play("hologram", hologramLoc, List.of(player), text);
        }
    }
}
