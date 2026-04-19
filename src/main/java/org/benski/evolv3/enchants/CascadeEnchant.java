package org.benski.evolv3.enchants;

import org.benski.evolv3.animations.CascadeAnimation;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.AoEDropResolver;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.benski.evolv3.core.managers.MineManager;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listener for the Cascade custom enchantment.
 *
 * <p>When triggered, fires a chain of explosions that rips through the mine in a
 * random direction. Each link of the chain detonates a sphere of blocks and
 * advances the front further in the same general direction (with slight angular
 * drift per step), creating a cascading tear through the rock.</p>
 *
 * <ul>
 *   <li>Chain length scales 2 → {@code MaxChainLength} with enchant level.</li>
 *   <li>Each link breaks a sphere of radius {@code ExplosionRadius}.</li>
 *   <li>Links fire every {@code StepDelayTicks} ticks so the chain is
 *       visually distinct — players watch it rip across the mine.</li>
 *   <li>Fortune, Frostbite resolution, and backpack overflow protection are all
 *       applied to every block broken in every link.</li>
 * </ul>
 */
public class CascadeEnchant implements Listener
{
    private final JavaPlugin plugin;


    /** Ticks between successive chain-link detonations. */
    private static final int STEP_DELAY_TICKS = 4;

    public CascadeEnchant(JavaPlugin plugin)
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

            EnchantData cascade = pickaxeProfile.enchants.getEnchant(Enchants.CASCADE);
            if (cascade == null || !cascade.enabled || cascade.level <= 0) return;
            if (!Utils.chance(cascade.getCurrentChance())) return;

            Block origin = event.getBlock();
            triggerCascade(player, origin, pickaxeProfile, cascade, 0);
        });
    }

    // -------------------------------------------------------------------------
    // Cascade logic
    // -------------------------------------------------------------------------

    /**
     * @param reDepth how many times cascade has re-triggered (used by Legendary Infinite Loop
     *                and Rare Aftershock to prevent uncontrolled recursion; cap is 2)
     */
    private void triggerCascade(Player player, Block origin, PickaxeProfile pickaxeProfile,
                                 EnchantData cascade, int reDepth)
    {
        if (!player.isOnline()) return;

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) return;

        PlayerProfile playerProfile = playerDC.getLocal(player);
        if (playerProfile == null) return;
        if (playerProfile.backpack.isFull()) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);

        int maxLevel       = config.getInt(ConfigPaths.Enchants.Cascade.MAX_LEVEL,          100);
        int maxChainLen    = config.getInt(ConfigPaths.Enchants.Cascade.MAX_CHAIN_LENGTH,   16);
        int stepDistance   = config.getInt(ConfigPaths.Enchants.Cascade.STEP_DISTANCE,       5);
        int baseRadius     = config.getInt(ConfigPaths.Enchants.Cascade.EXPLOSION_RADIUS,    2);
        int maxRadiusBonus = config.getInt(ConfigPaths.Enchants.Cascade.MAX_RADIUS_BONUS,    3);
        int maxChains      = config.getInt(ConfigPaths.Enchants.Cascade.MAX_CHAINS,          3);

        double levelFraction = (double)(cascade.level - 1) / Math.max(1, maxLevel - 1);

        // Chain length scales from 3 at level 1 to maxChainLen at max level.
        int chainLength = 3 + (int) Math.round(levelFraction * (maxChainLen - 3));
        chainLength = Math.max(3, Math.min(chainLength, maxChainLen));

        // Common (Extension): +2 chain length
        if (CascadeStoneHandler.hasStone(cascade, "Common")) {
            chainLength += 2;
        }

        // Legendary (Infinite Loop): triple chain length, capped at 60
        if (CascadeStoneHandler.hasStone(cascade, "Legendary")) {
            chainLength = Math.min(chainLength * 3, 60);
        }

        // Sphere radius grows with level: base at level 1, base+maxRadiusBonus at max level.
        int radius = baseRadius + (int) Math.round(levelFraction * maxRadiusBonus);

        // Number of simultaneous chains: 1 at low levels, maxChains at max level.
        int chainCount = 1 + (int) Math.round(levelFraction * (maxChains - 1));
        chainCount = Math.max(1, Math.min(chainCount, maxChains));

        // Epic (Time Warp): all links fire at tick 0 simultaneously
        boolean timeWarp = CascadeStoneHandler.hasStone(cascade, "Epic");
        // Uncommon (Depth Seeker): bias chain direction downward
        boolean depthSeeker = CascadeStoneHandler.hasStone(cascade, "Uncommon");
        // Rare (Aftershock): second half-length cascade from last link (only on first trigger)
        boolean aftershock = reDepth == 0 && CascadeStoneHandler.hasStone(cascade, "Rare");

        ThreadLocalRandom rng      = ThreadLocalRandom.current();
        World             world    = origin.getWorld();
        int fortuneAmount          = resolveFortune(pickaxeProfile);
        EnchantPreferenceResult pref = getEnchantPref(player, config);
        AtomicInteger totalBroken  = new AtomicInteger(0);

        if (reDepth == 0) AoEDropResolver.startAccumulation(player.getUniqueId());

        final int finalChainLength = chainLength;

        // Fire chainCount independent chains in different random directions.
        // Only the first chain triggers the notification so it isn't shown repeatedly.
        for (int c = 0; c < chainCount; c++) {
            final boolean isFirstChain = (c == 0);

            // Pick an independent random direction for each chain.
            double yaw = rng.nextDouble() * Math.PI * 2;
            // Uncommon (Depth Seeker): always bias downward; otherwise random pitch
            double pitch = depthSeeker
                    ? -(rng.nextDouble() * 0.5 + 0.15)
                    : (rng.nextDouble() - 0.5) * 0.4;
            Vector direction = new Vector(Math.cos(yaw), pitch, Math.sin(yaw)).normalize();

            // Pre-compute link centres with slight angular drift per step.
            List<Location> stepCenters = new ArrayList<>();
            Location cursor = origin.getLocation().add(0.5, 0.5, 0.5);
            Vector dir = direction.clone();
            stepCenters.add(cursor.clone());

            for (int step = 1; step < finalChainLength; step++) {
                // Uncommon (Depth Seeker): stronger downward drift per step
                double yDrift = depthSeeker
                        ? -(rng.nextDouble() * 0.12)
                        : (rng.nextDouble() - 0.5) * 0.08;
                dir.add(new Vector(
                        (rng.nextDouble() - 0.5) * 0.18,
                        yDrift,
                        (rng.nextDouble() - 0.5) * 0.18
                )).normalize();
                cursor = cursor.clone().add(dir.clone().multiply(stepDistance));
                if (!WorldGuardManager.getInstance().isWithinMineRegion(cursor)) break;
                stepCenters.add(cursor.clone());
            }

            if (stepCenters.isEmpty()) continue;

            final int finalRadius = radius;

            // Schedule each link.
            // Epic (Time Warp): all at tick 0; otherwise stagger by STEP_DELAY_TICKS.
            for (int i = 0; i < stepCenters.size(); i++) {
                final Location center = stepCenters.get(i);
                final boolean isLast  = isFirstChain && (i == stepCenters.size() - 1);

                long delay = timeWarp ? 0L : (long) i * STEP_DELAY_TICKS;

                new BukkitRunnable()
                {
                    @Override
                    public void run()
                    {
                        if (!player.isOnline()) { cancel(); return; }

                        int broken = detonateLink(player, world, center, finalRadius,
                                                 pickaxeProfile, playerProfile, fortuneAmount);
                        totalBroken.addAndGet(broken);

                        // Play directly (bypassing registry budget check) so particles
                        // are never silently dropped when many chain links fire at once.
                        new CascadeAnimation(plugin, pref.sound, pref.particles)
                                .play(center, List.of(player));

                        if (isLast) {
                            if (totalBroken.get() > 0) {
                                AoEDropResolver.flushAccumulation(player, origin, pickaxeProfile);
                                showNotification(player, origin, config, pref, totalBroken.get());
                            }
                            // Rare (Aftershock): fire a second cascade at half length from last link
                            if (aftershock) {
                                Block lastBlock = world.getBlockAt(center.getBlockX(),
                                        center.getBlockY(), center.getBlockZ());
                                int afterLength = Math.max(2, finalChainLength / 2);
                                EnchantData afterCascade = new EnchantData(
                                        cascade.name, cascade.level, cascade.maxLevel, cascade.maxChance);
                                // Don't carry Rare stone into aftershock to prevent chaining
                                scheduleAftershot(player, lastBlock, pickaxeProfile, afterCascade, afterLength);
                            }
                        }
                    }
                }.runTaskLater(plugin, delay);
            }
        }
    }

    /**
     * Fires a simplified secondary cascade for Rare (Aftershock), using a fixed chain length
     * and no stone effects (to prevent recursive re-triggering).
     */
    private void scheduleAftershot(Player player, Block origin, PickaxeProfile pickaxeProfile,
                                    EnchantData cascade, int length)
    {
        if (!player.isOnline()) return;
        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) return;
        PlayerProfile playerProfile = playerDC.getLocal(player);
        if (playerProfile == null || playerProfile.backpack.isFull()) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
        int stepDistance = config.getInt(ConfigPaths.Enchants.Cascade.STEP_DISTANCE, 5);
        int baseRadius   = config.getInt(ConfigPaths.Enchants.Cascade.EXPLOSION_RADIUS, 2);

        World world = origin.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int fortuneAmount = resolveFortune(pickaxeProfile);

        double yaw = rng.nextDouble() * Math.PI * 2;
        double pitch = (rng.nextDouble() - 0.5) * 0.4;
        Vector dir = new Vector(Math.cos(yaw), pitch, Math.sin(yaw)).normalize();

        List<Location> steps = new ArrayList<>();
        Location cursor = origin.getLocation().add(0.5, 0.5, 0.5);
        steps.add(cursor.clone());
        for (int i = 1; i < length; i++) {
            dir.add(new Vector(
                    (rng.nextDouble() - 0.5) * 0.18,
                    (rng.nextDouble() - 0.5) * 0.08,
                    (rng.nextDouble() - 0.5) * 0.18
            )).normalize();
            cursor = cursor.clone().add(dir.clone().multiply(stepDistance));
            if (!WorldGuardManager.getInstance().isWithinMineRegion(cursor)) break;
            steps.add(cursor.clone());
        }

        EnchantPreferenceResult pref = getEnchantPref(player, config);

        for (int i = 0; i < steps.size(); i++) {
            final Location center = steps.get(i);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) { cancel(); return; }
                    detonateLink(player, world, center, baseRadius,
                            pickaxeProfile, playerProfile, fortuneAmount);
                    new CascadeAnimation(plugin, pref.sound, pref.particles)
                            .play(center, List.of(player));
                }
            }.runTaskLater(plugin, (long) i * STEP_DELAY_TICKS);
        }
    }

    /**
     * Breaks a sphere of blocks around {@code center}, adds drops to the
     * backpack, and returns the number of blocks broken.
     */
    private int detonateLink(Player player, World world, Location center, int radius,
                             PickaxeProfile pickaxeProfile, PlayerProfile playerProfile,
                             int fortuneAmount)
    {
        Backpack backpack = playerProfile.backpack;
        if (backpack.isFull()) return 0;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int radiusSq = radius * radius;
        int broken = 0;

        outer:
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radiusSq) continue;

                    int remaining = backpack.getMaxSize() - backpack.getTotalItems();
                    if (remaining <= 0) break outer;

                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (!AoEDropResolver.isBreakable(block)) continue;
                    if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

                    Material mat = AoEDropResolver.resolve(
                            block, player.getInventory().getItemInMainHand(), plugin, pickaxeProfile);
                    if (mat.isAir()) continue;

                    MineManager.getInstance().trackBlockAtLocation(block.getLocation());
                    block.setType(Material.AIR);

                    int dropCount = Math.min(fortuneAmount > 0 ? fortuneAmount : 1, remaining);
                    backpack.addItem(player, mat, dropCount, false);
                    PickaxeManager.getInstance().addStatistics(player, mat, playerProfile);
                    AoEDropResolver.rollSideEffects(player, block, pickaxeProfile, playerProfile);
                    broken++;
                }
            }
        }

        if (broken > 0) {
            BackpackListener.refresh(player);
            playerProfile.save(null);
        }

        return broken;
    }

    private void showNotification(Player player, Block origin, FileConfiguration config,
                                  EnchantPreferenceResult pref, int broken)
    {
        String brokenStr = String.valueOf(broken);

        if (pref.hologram && config.getBoolean(ConfigPaths.Enchants.Cascade.HOLOGRAM_ENABLED, true)) {
            String hologramText = config.getString(
                            ConfigPaths.Enchants.Cascade.HOLOGRAM_MESSAGE,
                            "&#FFAA00&lCASCADE &#CCCCCCChained %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message) {
            String message = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.CASCADE_TRIGGERED,
                               " &#FFAA00&lCASCADE &#CCCCCCChained %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            player.sendMessage(Utils.formatMessage(player, message));
        }

        if (pref.title) {
            var msgCfg = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
            String title    = msgCfg.getString(ConfigPaths.Messages.Enchants.CASCADE_TRIGGERED_TITLE,    "&#FFAA00&lCascade");
            String subtitle = msgCfg.getString(ConfigPaths.Messages.Enchants.CASCADE_TRIGGERED_SUBTITLE, "&#CCCCCCChained %blocks% blocks!")
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

        String enchantName  = config.getString(ConfigPaths.Enchants.Cascade.NAME, "Cascade");
        boolean defaultHolo = config.getBoolean(ConfigPaths.Enchants.Cascade.HOLOGRAM_DEFAULT_ENABLED, false);
        EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

        return new EnchantPreferenceResult(pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
    }

    private record EnchantPreferenceResult(boolean hologram, boolean message,
                                           boolean sound, boolean particles, boolean title) {}
}
