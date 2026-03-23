package org.benski.evolv3.enchants;

import org.benski.evolv3.animations.DrillAnimation;
import org.benski.evolv3.animations.HologramAnimation;
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
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listener for the Drill custom enchantment.
 *
 * <p>When triggered, bores a 2×2 shaft straight down one level at a time.
 * Each level is broken, then its shard animation plays, then after a short
 * delay the next level fires — so the drilling propagates visually downward.
 * Depth is randomised to 2, 3, or 4 levels per trigger (capped by {@code MaxDepth}
 * in config). Fortune and XP statistics are applied to every extra block broken.</p>
 */
public class DrillEnchant implements Listener
{
    private final JavaPlugin plugin;

    /** Ticks between breaking each successive depth level. */
    private static final int LEVEL_DELAY_TICKS = 8;

    public DrillEnchant(JavaPlugin plugin)
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

            EnchantData drill = pickaxeProfile.enchants.getEnchant(Enchants.DRILL);
            if (drill == null || !drill.enabled || drill.level <= 0) return;
            if (!Utils.chance(drill.getCurrentChance())) return;

            Block origin = event.getBlock();

            // Wait 1 tick for the primary block break to complete, then start drilling.
            new BukkitRunnable()
            {
                @Override
                public void run() { startDrill(player, origin, pickaxeProfile); }
            }.runTaskLater(plugin, 1L);
        });
    }

    // -------------------------------------------------------------------------
    // Drill logic
    // -------------------------------------------------------------------------

    /**
     * Entry point for the sequential drill. Resolves profiles, rolls a random depth,
     * and kicks off level 0.
     */
    private void startDrill(Player player, Block origin, PickaxeProfile pickaxeProfile)
    {
        if (!player.isOnline()) return;

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) return;

        PlayerProfile playerProfile = playerDC.getLocal(player);
        if (playerProfile == null) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);

        // Random depth: 2, 3, or 4 levels (capped by MaxDepth in config).
        int maxDepth = config.getInt(ConfigPaths.Enchants.Drill.DEPTH, 4);
        int depth = 2 + ThreadLocalRandom.current().nextInt(Math.min(3, Math.max(1, maxDepth - 1)));

        int fortuneAmount = resolveFortune(pickaxeProfile);
        AtomicInteger totalBroken = new AtomicInteger(0);

        drillLevel(player, origin, playerProfile, pickaxeProfile, config, 0, depth, fortuneAmount, totalBroken);
    }

    /**
     * Breaks all breakable blocks at {@code currentLevel}, plays the shard animation,
     * then schedules the next level after {@value LEVEL_DELAY_TICKS} ticks.
     * Shows the hologram/message notification on the final level.
     *
     * @param currentLevel 0 = same Y as the original break; 1+ = levels below
     * @param maxLevel     the deepest level to drill
     * @param totalBroken  running count of extra blocks broken across all levels
     */
    private void drillLevel(Player player, Block origin, PlayerProfile playerProfile,
                            PickaxeProfile pickaxeProfile,
                            FileConfiguration config, int currentLevel, int maxLevel,
                            int fortuneAmount, AtomicInteger totalBroken)
    {
        if (!player.isOnline()) return;

        Backpack backpack = playerProfile.backpack;
        if (backpack.isFull()) {
            if (totalBroken.get() > 0) showNotification(player, origin, config, totalBroken.get());
            return;
        }

        List<Block> levelBlocks  = getLevelBlocks(origin, currentLevel);
        List<Material> levelMats = new ArrayList<>();

        for (Block block : levelBlocks) {
            int remaining = backpack.getMaxSize() - backpack.getTotalItems();
            if (remaining <= 0) break;
            if (!AoEDropResolver.isBreakable(block)) continue;
            if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

            Material mat = AoEDropResolver.resolve(
                    block, player.getInventory().getItemInMainHand(), plugin, pickaxeProfile);
            if (mat.isAir()) continue; // safety: block was already removed by another process
            levelMats.add(mat);
            block.setType(Material.AIR);

            int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
            backpack.addItem(player, mat, dropCount, false);
            PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
            AoEDropResolver.rollSideEffects(player, block, pickaxeProfile, playerProfile);
            totalBroken.incrementAndGet();
        }

        if (!levelMats.isEmpty()) {
            BackpackListener.refresh(player);
            playerProfile.save(null);

            // Centre of the 2×2 at this depth level — animation origin.
            Location levelCenter = origin.getLocation().add(1.0, 0.5 - currentLevel, 1.0);
            DrillAnimation anim = new DrillAnimation(plugin, levelMats);
            AnimationRegistry.getInstance().play(anim, levelCenter, List.of(player));

            float pitch = 0.6f + ThreadLocalRandom.current().nextFloat() * 0.2f;
            player.playSound(levelCenter, Sound.BLOCK_STONE_BREAK, 0.7f, pitch);
        }

        if (currentLevel < maxLevel) {
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    drillLevel(player, origin, playerProfile, pickaxeProfile, config,
                               currentLevel + 1, maxLevel, fortuneAmount, totalBroken);
                }
            }.runTaskLater(plugin, LEVEL_DELAY_TICKS);
        } else {
            if (totalBroken.get() > 0) showNotification(player, origin, config, totalBroken.get());
        }
    }

    /**
     * Returns the blocks belonging to a given drill level.
     * Level 0: the 3 companion blocks at the same Y as origin (origin itself is already air).
     * Level N: all 4 blocks of the 2×2 footprint N levels below origin.
     */
    private List<Block> getLevelBlocks(Block origin, int level)
    {
        if (level == 0) {
            return List.of(
                origin.getRelative(1, 0, 0),
                origin.getRelative(0, 0, 1),
                origin.getRelative(1, 0, 1)
            );
        }
        return List.of(
            origin.getRelative(0, -level, 0),
            origin.getRelative(1, -level, 0),
            origin.getRelative(0, -level, 1),
            origin.getRelative(1, -level, 1)
        );
    }

    /**
     * Shows the hologram and/or chat notification at the end of the full drill sequence.
     */
    private void showNotification(Player player, Block origin, FileConfiguration config, int broken)
    {
        EnchantPreferenceResult pref = getEnchantPref(player, config);
        String brokenStr = String.valueOf(broken);

        if (pref.hologram && config.getBoolean(ConfigPaths.Enchants.Drill.HOLOGRAM_ENABLED, true)) {
            String hologramText = config.getString(
                            ConfigPaths.Enchants.Drill.HOLOGRAM_MESSAGE,
                            "&#00FFFF&lDRILL &#CCCCCCDrilled %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message) {
            String message = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.DRILL_TRIGGERED,
                               " &#00FFFF&lDRILL &#CCCCCCDrilled %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            player.sendMessage(Utils.formatMessage(player, message));
        }

        if (pref.title) {
            var msgCfg = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
            String title    = msgCfg.getString(ConfigPaths.Messages.Enchants.DRILL_TRIGGERED_TITLE,    "&#00FFFF&lDrill");
            String subtitle = msgCfg.getString(ConfigPaths.Messages.Enchants.DRILL_TRIGGERED_SUBTITLE, "&#CCCCCCDrilled %blocks% blocks!")
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
        if (pdc == null) return new EnchantPreferenceResult(false, false, false);
        PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return new EnchantPreferenceResult(false, false, false);

        String enchantName   = config.getString(ConfigPaths.Enchants.Drill.NAME, "Drill");
        boolean defaultHolo  = config.getBoolean(ConfigPaths.Enchants.Drill.HOLOGRAM_DEFAULT_ENABLED, false);
        org.benski.evolv3.models.player.EnchantPreference pref =
                profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

        return new EnchantPreferenceResult(pref.hologram, pref.message, pref.title);
    }

    private record EnchantPreferenceResult(boolean hologram, boolean message, boolean title) {}
}
