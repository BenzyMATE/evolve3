package org.benski.evolv3.enchants;

import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.ThorAnimation;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Listener for the Thor custom enchantment.
 *
 * <p>When triggered, calls down a lightning strike at the mined block's X/Z
 * position. The bolt scans from the top of the mine region downward to the
 * bottom, breaking every solid block in the 1×1 column and adding their drops
 * to the player's backpack. Air gaps (already-mined sections) are skipped so
 * the bolt passes through holes and continues downward — meaning the enchant
 * works even when the player is at the very bottom of the mine.</p>
 *
 * <p>Fortune is applied to every block broken. Frostbite-frozen blocks are
 * resolved to their original material before being collected.</p>
 */
public class ThorEnchant implements Listener
{
    private final JavaPlugin plugin;

    public ThorEnchant(JavaPlugin plugin)
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

            EnchantData thor = pickaxeProfile.enchants.getEnchant(Enchants.THOR);
            if (thor == null || !thor.enabled || thor.level <= 0) return;
            if (!Utils.chance(thor.getCurrentChance())) return;

            Block origin = event.getBlock();

            // Wait 1 tick for the primary block break to complete before striking.
            new BukkitRunnable()
            {
                @Override
                public void run() { strikeThor(player, origin, pickaxeProfile); }
            }.runTaskLater(plugin, 1L);
        });
    }

    // -------------------------------------------------------------------------
    // Thor logic
    // -------------------------------------------------------------------------

    private void strikeThor(Player player, Block origin, PickaxeProfile pickaxeProfile)
    {
        if (!player.isOnline()) return;

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) return;

        PlayerProfile playerProfile = playerDC.getLocal(player);
        if (playerProfile == null) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);
        int maxColumnHeight = config.getInt(ConfigPaths.Enchants.Thor.MAX_COLUMN_HEIGHT, 100);

        World world    = origin.getWorld();
        int   baseX    = origin.getX();
        int   baseZ    = origin.getZ();

        // Scan upward to find the top of the mine region at this X/Z.
        Location scanLoc = origin.getLocation().clone();
        int topY = origin.getY();
        for (int i = 1; i <= maxColumnHeight; i++) {
            scanLoc.setY(topY + 1);
            if (!WorldGuardManager.getInstance().isWithinMineRegion(scanLoc)) break;
            topY++;
        }

        // Scan downward to find the bottom of the mine region at this X/Z.
        scanLoc.setY(origin.getY());
        int bottomY = origin.getY();
        for (int i = 1; i <= maxColumnHeight; i++) {
            scanLoc.setY(bottomY - 1);
            if (!WorldGuardManager.getInstance().isWithinMineRegion(scanLoc)) break;
            bottomY--;
        }

        int fortuneAmount = resolveFortune(pickaxeProfile);
        Backpack backpack = playerProfile.backpack;
        if (backpack.isFull()) return;

        List<Material> columnMats = new ArrayList<>();
        int broken = 0;

        // Walk from top to bottom, breaking solid blocks and skipping air.
        for (int y = topY; y >= bottomY; y--) {
            int remaining = backpack.getMaxSize() - backpack.getTotalItems();
            if (remaining <= 0) break;

            Block block = world.getBlockAt(baseX, y, baseZ);
            if (!AoEDropResolver.isBreakable(block)) continue;
            if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

            Material mat = AoEDropResolver.resolve(
                    block, player.getInventory().getItemInMainHand(), plugin, pickaxeProfile);
            if (mat.isAir()) continue;

            columnMats.add(mat);
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

        // Play the lightning animation descending from the top of the column.
        EnchantPreferenceResult pref = getEnchantPref(player, config);
        int columnHeight = topY - bottomY;
        Location strikeOrigin = new Location(world, baseX + 0.5, topY + 0.5, baseZ + 0.5);

        // Strike a fake (no damage, no fire) lightning bolt at the top of the column.
        // Visible to all nearby players — intentional, since Thor is a dramatic world event.
        world.strikeLightningEffect(strikeOrigin);

        ThorAnimation anim = new ThorAnimation(plugin, columnHeight, pref.sound, pref.particles);
        AnimationRegistry.getInstance().play(anim, strikeOrigin, List.of(player));

        if (broken > 0) {
            showNotification(player, origin, config, pref, broken);
        }
    }

    /**
     * Shows the hologram and/or chat/title notification after Thor strikes.
     */
    private void showNotification(Player player, Block origin, FileConfiguration config,
                                  EnchantPreferenceResult pref, int broken)
    {
        String brokenStr = String.valueOf(broken);

        if (pref.hologram && config.getBoolean(ConfigPaths.Enchants.Thor.HOLOGRAM_ENABLED, true)) {
            String hologramText = config.getString(
                            ConfigPaths.Enchants.Thor.HOLOGRAM_MESSAGE,
                            "&#FF55CC&lTHOR &#CCCCCCStruck %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message) {
            String message = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.THOR_TRIGGERED,
                               " &#FF55CC&lTHOR &#CCCCCCStruck %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            player.sendMessage(Utils.formatMessage(player, message));
        }

        if (pref.title) {
            var msgCfg = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
            String title    = msgCfg.getString(ConfigPaths.Messages.Enchants.THOR_TRIGGERED_TITLE,    "&#FF55CC&lThor");
            String subtitle = msgCfg.getString(ConfigPaths.Messages.Enchants.THOR_TRIGGERED_SUBTITLE, "&#CCCCCCStruck %blocks% blocks!")
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

        String enchantName  = config.getString(ConfigPaths.Enchants.Thor.NAME, "Thor");
        boolean defaultHolo = config.getBoolean(ConfigPaths.Enchants.Thor.HOLOGRAM_DEFAULT_ENABLED, false);
        EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

        return new EnchantPreferenceResult(pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
    }

    private record EnchantPreferenceResult(boolean hologram, boolean message,
                                           boolean sound, boolean particles, boolean title) {}
}
