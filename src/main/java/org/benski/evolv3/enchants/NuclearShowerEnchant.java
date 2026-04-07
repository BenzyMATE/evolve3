package org.benski.evolv3.enchants;

import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.NuclearShowerAnimation;
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
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.EnchantPreference;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listener for the Nuclear Shower custom enchantment.
 *
 * <p>When triggered, a toxic cloud forms at the top of the mine and unleashes
 * a radioactive rainstorm. Acid rain eats through the mine from the top down,
 * breaking one horizontal layer at a time across all stream positions before
 * descending to the next — creating a visible wave of dissolution moving
 * downward through the mine.</p>
 *
 * <ul>
 *   <li>Stream count (random XZ positions) scales from 5 at level 1 to
 *       {@code MaxStreams} at max level.</li>
 *   <li>All streams in a layer fire in the same tick; layers are separated by
 *       {@code LAYER_STAGGER_TICKS} so the rain visually descends.</li>
 *   <li>Fortune, Frostbite resolution, and backpack overflow protection apply
 *       to every block collected.</li>
 *   <li>Mine bounds come from WorldGuard when available; falls back to a
 *       configurable {@code MaxScanRadius} square around the mined block.</li>
 * </ul>
 */
public class NuclearShowerEnchant implements Listener
{
    private final JavaPlugin plugin;

    /** Ticks between each layer break. Higher = slower, more visible descent. */
    private static final int RAIN_INTERVAL_TICKS = 5;

    /** Maximum column-height scan in each vertical direction from the origin. */
    private static final int MAX_COLUMN_SCAN = 200;

    public NuclearShowerEnchant(JavaPlugin plugin)
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

            EnchantData shower = pickaxeProfile.enchants.getEnchant(Enchants.NUCLEAR_SHOWER);
            if (shower == null || !shower.enabled || shower.level <= 0) return;

            double triggerChance = shower.getCurrentChance();
            // Uncommon (Unstable): +2% to proc chance
            if (NuclearShowerStoneHandler.hasStone(shower, "Uncommon")) triggerChance += 2.0;
            if (!Utils.chance(triggerChance)) return;

            Block origin = event.getBlock();

            // Wait 1 tick for the primary block break to resolve before the shower begins.
            new BukkitRunnable()
            {
                @Override
                public void run() { triggerShower(player, origin, pickaxeProfile, shower.level, shower, 0); }
            }.runTaskLater(plugin, 1L);
        });
    }

    // -------------------------------------------------------------------------
    // Nuclear Shower logic
    // -------------------------------------------------------------------------

    private void triggerShower(Player player, Block origin, PickaxeProfile pickaxeProfile, int enchantLevel,
                               EnchantData shower, int dominoDepth)
    {
        if (!player.isOnline()) return;

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) return;

        PlayerProfile playerProfile = playerDC.getLocal(player);
        if (playerProfile == null) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);

        int maxLevel      = config.getInt(ConfigPaths.Enchants.NuclearShower.MAX_LEVEL,   100);
        int maxStreams     = config.getInt(ConfigPaths.Enchants.NuclearShower.MAX_STREAMS, 150);
        int maxScanRadius = config.getInt(ConfigPaths.Enchants.NuclearShower.MAX_SCAN_RADIUS, 100);

        double levelFraction = (double)(enchantLevel - 1) / Math.max(1, maxLevel - 1);

        // Stream count scales from 10 at level 1 to maxStreams at max level.
        final int streamCount = Math.max(10, Math.min(
                10 + (int) Math.round(levelFraction * (maxStreams - 10)), maxStreams));

        World world = origin.getWorld();

        // ---- Determine mine Y extents ----------------------------------------
        Location scanLoc = origin.getLocation().clone();
        int topY = origin.getY();
        for (int i = 1; i <= MAX_COLUMN_SCAN; i++) {
            scanLoc.setY(topY + 1);
            if (!WorldGuardManager.getInstance().isWithinMineRegion(scanLoc)) break;
            topY++;
        }
        scanLoc.setY(origin.getY());
        int bottomY = origin.getY();
        for (int i = 1; i <= MAX_COLUMN_SCAN; i++) {
            scanLoc.setY(bottomY - 1);
            if (!WorldGuardManager.getInstance().isWithinMineRegion(scanLoc)) break;
            bottomY--;
        }
        final int finalTopY    = topY;
        final int finalBottomY = bottomY;
        final int columnHeight = topY - bottomY;

        // ---- Determine mine XZ extents ---------------------------------------
        int minX, minZ, maxX, maxZ;
        int[] bounds = WorldGuardManager.getInstance().getMineRegionXZBounds(origin.getLocation());
        if (bounds != null) {
            minX = bounds[0];
            minZ = bounds[1];
            maxX = bounds[2];
            maxZ = bounds[3];
        } else {
            minX = origin.getX() - maxScanRadius;
            minZ = origin.getZ() - maxScanRadius;
            maxX = origin.getX() + maxScanRadius;
            maxZ = origin.getZ() + maxScanRadius;
        }

        // Common (Fallout Zone): expand dissolution radius by ±3 blocks
        if (NuclearShowerStoneHandler.hasStone(shower, "Common")) {
            minX -= 3; minZ -= 3; maxX += 3; maxZ += 3;
        }

        final int finalMinX = minX;
        final int finalMinZ = minZ;
        final int finalMaxX = maxX;
        final int finalMaxZ = maxZ;

        // Depth limit: max 2 layers below the broken block.
        // Legendary (Meltdown) doubles this to 4.
        final int depthLimit = NuclearShowerStoneHandler.hasStone(shower, "Legendary") ? 4 : 2;

        final boolean hasRare  = NuclearShowerStoneHandler.hasStone(shower, "Rare");
        final boolean hasEpic  = NuclearShowerStoneHandler.hasStone(shower, "Epic");

        // Pre-generate fixed XZ stream positions — the same columns are used each layer
        // so the rain visually falls straight down rather than scattering per layer.
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int xzWidth = Math.max(1, finalMaxX - finalMinX + 1);
        int xzDepth = Math.max(1, finalMaxZ - finalMinZ + 1);
        final int[] streamX = new int[streamCount];
        final int[] streamZ = new int[streamCount];
        for (int s = 0; s < streamCount; s++) {
            streamX[s] = finalMinX + rng.nextInt(xzWidth);
            streamZ[s] = finalMinZ + rng.nextInt(xzDepth);
        }

        int fortuneAmount = resolveFortune(pickaxeProfile);
        EnchantPreferenceResult pref = getEnchantPref(player, config);
        AtomicInteger layersDone  = new AtomicInteger(0);
        AtomicInteger totalBroken = new AtomicInteger(0);

        AoEDropResolver.startAccumulation(player.getUniqueId());

        // ---- Repeating task — one Y layer per interval, top → bottom ----------
        // Each tick processes all stream XZ positions at the current Y layer,
        // creating a visible acid wave descending through the mine.
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                int done = layersDone.getAndIncrement();

                if (!player.isOnline() || done >= depthLimit) {
                    cancel();
                    finish();
                    return;
                }

                int y = origin.getY() - done;
                if (y < finalBottomY) {
                    cancel();
                    finish();
                    return;
                }

                for (int s = 0; s < streamCount; s++) {
                    if (playerProfile.backpack.isFull()) break;

                    Block block = world.getBlockAt(streamX[s], y, streamZ[s]);
                    if (!AoEDropResolver.isBreakable(block)) continue;
                    if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

                    Material mat = AoEDropResolver.resolve(
                            block, player.getInventory().getItemInMainHand(), plugin, pickaxeProfile);

                    block.setType(Material.AIR);

                    if (!mat.isAir()) {
                        int remaining = playerProfile.backpack.getMaxSize() - playerProfile.backpack.getTotalItems();
                        if (remaining > 0) {
                            int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
                            playerProfile.backpack.addItem(player, mat, dropCount, false);
                            PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
                            AoEDropResolver.rollSideEffects(player, block, pickaxeProfile, playerProfile);
                        }
                    }

                    // Rare (Essence Fallout): 30% chance each dissolved block grants 1 essence
                    if (hasRare && Utils.chance(30.0)) {
                        playerProfile.addEssence(1);
                    }

                    // Epic (Domino): 2% chance to trigger another Nuclear Shower (max 1 cascade deep)
                    if (hasEpic && dominoDepth == 0 && Utils.chance(2.0)) {
                        final Block dominoOrigin = block;
                        new org.bukkit.scheduler.BukkitRunnable() {
                            @Override public void run() {
                                if (player.isOnline())
                                    triggerShower(player, dominoOrigin, pickaxeProfile, enchantLevel, shower, 1);
                            }
                        }.runTaskLater(plugin, 20L);
                    }

                    totalBroken.incrementAndGet();
                }

                BackpackListener.refresh(player);
            }

            private void finish()
            {
                playerProfile.save(null);
                int broken = totalBroken.get();
                if (broken > 0) {
                    AoEDropResolver.flushAccumulation(player, origin, pickaxeProfile);
                    showNotification(player, origin, config, pref, broken);
                }
            }
        }.runTaskTimer(plugin, 1L, RAIN_INTERVAL_TICKS);

        // ---- Launch animation at the cloud position --------------------------
        int halfWidth  = (finalMaxX - finalMinX) / 2;
        int halfDepth  = (finalMaxZ - finalMinZ) / 2;
        int halfRadius = Math.max(1, Math.max(halfWidth, halfDepth));

        // Cloud spawns 6 blocks above the mine ceiling so it's visibly above the top layer.
        final int CLOUD_OFFSET = 6;
        double cloudX = (finalMinX + finalMaxX) / 2.0 + 0.5;
        double cloudZ = (finalMinZ + finalMaxZ) / 2.0 + 0.5;
        Location cloudLoc = new Location(world, cloudX, finalTopY + CLOUD_OFFSET, cloudZ);

        // Pass cloud-to-floor height so particles fall the full distance.
        int animColumnHeight = columnHeight + CLOUD_OFFSET;
        NuclearShowerAnimation anim = new NuclearShowerAnimation(
                plugin, halfRadius, animColumnHeight, pref.sound(), pref.particles());
        AnimationRegistry.getInstance().play(anim, cloudLoc, List.of(player));
    }

    private void showNotification(Player player, Block origin, FileConfiguration config,
                                  EnchantPreferenceResult pref, int broken)
    {
        String brokenStr = String.valueOf(broken);

        if (pref.hologram() && config.getBoolean(ConfigPaths.Enchants.NuclearShower.HOLOGRAM_ENABLED, true)) {
            String hologramText = config.getString(
                            ConfigPaths.Enchants.NuclearShower.HOLOGRAM_MESSAGE,
                            "&#39FF14&lNUCLEAR SHOWER &#CCCCCCDissolved %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message()) {
            String message = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.NUCLEAR_SHOWER_TRIGGERED,
                               " &#39FF14&lNUCLEAR SHOWER &#CCCCCCDissolved %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            player.sendMessage(Utils.formatMessage(player, message));
        }

        if (pref.title()) {
            var msgCfg = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
            String title    = msgCfg.getString(ConfigPaths.Messages.Enchants.NUCLEAR_SHOWER_TRIGGERED_TITLE, "&#39FF14&lNuclear Shower");
            String subtitle = msgCfg.getString(ConfigPaths.Messages.Enchants.NUCLEAR_SHOWER_TRIGGERED_SUBTITLE, "&#CCCCCCDissolved %blocks% blocks!")
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

        String enchantName  = config.getString(ConfigPaths.Enchants.NuclearShower.NAME, "Nuclear Shower");
        boolean defaultHolo = config.getBoolean(ConfigPaths.Enchants.NuclearShower.HOLOGRAM_DEFAULT_ENABLED, false);
        EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

        return new EnchantPreferenceResult(pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
    }


    private record EnchantPreferenceResult(boolean hologram, boolean message,
                                           boolean sound, boolean particles, boolean title) {}
}
