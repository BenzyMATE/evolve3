package org.benski.evolv3.enchants;

import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.LazerAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.AoEDropResolver;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener for the Lazer custom enchantment.
 *
 * <p>When triggered, fires a laser beam in a random cardinal direction (N/S/E/W)
 * at the same Y level as the broken block. All breakable blocks along the beam
 * path (up to {@code MaxRange} blocks) are instantly vaporized, their drops
 * added to the player's backpack, and statistics credited. Fortune is applied
 * if the player has that enchantment.</p>
 *
 * <p>The {@link LazerAnimation} is played immediately after breaking, showing a
 * cyan particle beam and flying block-shard entities visible only to the triggering
 * player.</p>
 */
public class LazerEnchant implements Listener
{
    private final JavaPlugin plugin;

    /** The four cardinal directions the laser can fire in. */
    private static final Vector[] CARDINAL_DIRECTIONS = {
        new Vector( 1, 0,  0),
        new Vector(-1, 0,  0),
        new Vector( 0, 0,  1),
        new Vector( 0, 0, -1)
    };

    public LazerEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!Utils.isPickaxe(item.getType())) return;
        if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) return;

        String blockName = event.getBlock().getType().name();
        if (blockName.endsWith("_LOG") || blockName.endsWith("_STEM")) return;

        PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDC == null) return;

        pickaxeDC.get(player, pickaxeProfile -> {
            if (pickaxeProfile == null || pickaxeProfile.enchants == null) return;

            EnchantData lazer = pickaxeProfile.enchants.getEnchant(Enchants.LAZER);
            if (lazer == null || !lazer.enabled || lazer.level <= 0) return;
            if (!Utils.chance(lazer.getCurrentChance())) return;

            Block origin = event.getBlock();
            fireLazer(player, origin, pickaxeProfile);
        });
    }

    // -------------------------------------------------------------------------
    // Lazer logic
    // -------------------------------------------------------------------------

    private void fireLazer(Player player, Block origin, PickaxeProfile pickaxeProfile)
    {
        if (!player.isOnline()) return;

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) return;

        PlayerProfile playerProfile = playerDC.getLocal(player);
        if (playerProfile == null) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);

        int maxRange = config.getInt(ConfigPaths.Enchants.Lazer.MAX_RANGE, 50);
        int fortuneAmount = resolveFortune(pickaxeProfile);

        // Pick a random cardinal direction.
        Vector direction = CARDINAL_DIRECTIONS[ThreadLocalRandom.current().nextInt(CARDINAL_DIRECTIONS.length)];

        Backpack backpack = playerProfile.backpack;
        if (backpack.isFull()) return;

        List<LazerAnimation.ShardBlock> shardBlocks = new ArrayList<>();
        int broken = 0;

        // Scan along the beam, breaking blocks as we go.
        for (int dist = 1; dist <= maxRange; dist++) {
            int remaining = backpack.getMaxSize() - backpack.getTotalItems();
            if (remaining <= 0) break;

            Block block = origin.getWorld().getBlockAt(
                    origin.getX() + (int)(direction.getX() * dist),
                    origin.getY(),
                    origin.getZ() + (int)(direction.getZ() * dist)
            );

            if (!AoEDropResolver.isBreakable(block)) {
                // Stop the beam if it hits a protected or unbreakable block (but not air).
                if (!block.getType().isAir()) break;
                continue;
            }

            if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) break;

            Material mat = AoEDropResolver.resolve(
                    block, player.getInventory().getItemInMainHand(), plugin, pickaxeProfile);
            if (mat.isAir()) continue; // safety: block was already removed by another process
            shardBlocks.add(new LazerAnimation.ShardBlock(block.getLocation().clone(), mat));
            block.setType(Material.AIR);

            int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
            backpack.addItem(player, mat, dropCount, false);
            PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
            AoEDropResolver.rollSideEffects(player, block, pickaxeProfile, playerProfile);
            broken++;
        }

        if (broken > 0) {
            BackpackListener.refresh(player);
            playerProfile.save(null);
        }

        // Retrieve enchant preferences (sound, particles, hologram, message).
        EnchantPreferenceResult pref = getEnchantPref(player, config);

        // Play the animation from the beam origin (the broken block's location).
        Location beamOrigin = origin.getLocation().clone().add(0.5, 0.5, 0.5);
        LazerAnimation anim = new LazerAnimation(plugin, direction, shardBlocks, pref.sound, pref.particles);
        AnimationRegistry.getInstance().play(anim, beamOrigin, List.of(player));

        if (broken > 0) {
            showNotification(player, origin, config, pref, broken);
        }
    }

    /**
     * Shows the hologram and/or chat notification after the laser fires.
     */
    private void showNotification(Player player, Block origin, FileConfiguration config,
                                  EnchantPreferenceResult pref, int broken)
    {
        String brokenStr = String.valueOf(broken);

        if (pref.hologram && config.getBoolean(ConfigPaths.Enchants.Lazer.HOLOGRAM_ENABLED, true)) {
            String hologramText = config.getString(
                            ConfigPaths.Enchants.Lazer.HOLOGRAM_MESSAGE,
                            "&#FF55CC&lLAZER &#CCCCCCVaporized %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message) {
            String message = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.LAZER_TRIGGERED,
                               " &#FF55CC&lLAZER &#CCCCCCVaporized %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            player.sendMessage(Utils.formatMessage(player, message));
        }

        if (pref.title) {
            var msgCfg = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
            String title    = msgCfg.getString(ConfigPaths.Messages.Enchants.LAZER_TRIGGERED_TITLE,    "&#FF55CC&lLazer");
            String subtitle = msgCfg.getString(ConfigPaths.Messages.Enchants.LAZER_TRIGGERED_SUBTITLE, "&#CCCCCCVaporized %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            player.sendTitle(Utils.formatMessage(player, title), Utils.formatMessage(player, subtitle), 5, 30, 10);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int resolveFortune(PickaxeProfile pickaxeProfile)
    {
        if (pickaxeProfile.enchants == null) return 0;
        EnchantData fortune = pickaxeProfile.enchants.getEnchant(Enchants.FORTUNE);
        if (fortune == null || !fortune.enabled || fortune.level <= 0) return 0;
        return FortuneEnchant.getDropAmount(fortune.level);
    }

    private EnchantPreferenceResult getEnchantPref(Player player, FileConfiguration config)
    {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return new EnchantPreferenceResult(false, false, true, true, false);
        PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return new EnchantPreferenceResult(false, false, true, true, false);

        String enchantName  = config.getString(ConfigPaths.Enchants.Lazer.NAME, "Lazer");
        boolean defaultHolo = config.getBoolean(ConfigPaths.Enchants.Lazer.HOLOGRAM_DEFAULT_ENABLED, false);
        EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

        return new EnchantPreferenceResult(pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
    }

    private record EnchantPreferenceResult(boolean hologram, boolean message,
                                           boolean sound, boolean particles, boolean title) {}
}
