package org.benski.evolv3.enchants;

import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.VortexAnimation;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Listener for the Vortex custom enchantment.
 *
 * <p>When triggered, spawns a black hole at the mined block's location. All
 * breakable blocks within a sphere radius are instantly broken, their drops
 * added to the player's backpack with Fortune applied, and the {@link VortexAnimation}
 * plays — pulling block-shard entities visually towards the centre before they
 * are absorbed. The radius scales from 1 to {@code MaxRadius} based on level.</p>
 */
public class VortexEnchant implements Listener
{
    private final JavaPlugin plugin;

    private static final Set<Material> PROTECTED_MATERIALS = Set.of(
        Material.BEDROCK,
        Material.BARRIER,
        Material.COMMAND_BLOCK,
        Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK,
        Material.STRUCTURE_BLOCK,
        Material.STRUCTURE_VOID,
        Material.JIGSAW,
        Material.END_PORTAL_FRAME,
        Material.END_PORTAL,
        Material.NETHER_PORTAL,
        Material.SPAWNER
    );

    public VortexEnchant(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!item.getType().name().contains("PICKAXE")) return;
        if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) return;

        String blockName = event.getBlock().getType().name();
        if (blockName.endsWith("_LOG") || blockName.endsWith("_STEM")) return;

        PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDC == null) return;

        pickaxeDC.get(player, pickaxeProfile -> {
            if (pickaxeProfile == null || pickaxeProfile.enchants == null) return;

            EnchantData vortex = pickaxeProfile.enchants.getEnchant(Enchants.VORTEX);
            if (vortex == null || !vortex.enabled || vortex.level <= 0) return;
            if (!Utils.chance(vortex.getCurrentChance())) return;

            Block origin = event.getBlock();
            triggerVortex(player, origin, pickaxeProfile, vortex.level);
        });
    }

    // -------------------------------------------------------------------------
    // Vortex logic
    // -------------------------------------------------------------------------

    private void triggerVortex(Player player, Block origin, PickaxeProfile pickaxeProfile, int enchantLevel)
    {
        if (!player.isOnline()) return;

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) return;

        PlayerProfile playerProfile = playerDC.getLocal(player);
        if (playerProfile == null) return;

        FileConfiguration config = StorageRegistryManager.getInstance().getConfig(ConfigNames.ENCHANTS);

        int maxLevel  = config.getInt(ConfigPaths.Enchants.Vortex.MAX_LEVEL,  100);
        int maxRadius = config.getInt(ConfigPaths.Enchants.Vortex.MAX_RADIUS, 4);

        // Radius scales 1 → maxRadius with level.
        int radius = Math.max(1, (int) Math.ceil((double) enchantLevel / maxLevel * maxRadius));

        int fortuneAmount = resolveFortune(pickaxeProfile);
        Backpack backpack = playerProfile.backpack;

        if (backpack.isFull()) return;

        List<VortexAnimation.BlockEntry> entries = new ArrayList<>();
        int broken = 0;

        int radiusSq = radius * radius;
        outer:
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radiusSq) continue; // sphere shape
                    if (x == 0 && y == 0 && z == 0) continue;       // already broken by the primary event

                    int remaining = backpack.getMaxSize() - backpack.getTotalItems();
                    if (remaining <= 0) break outer;

                    Block block = origin.getRelative(x, y, z);
                    if (!isBreakable(block)) continue;
                    if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) continue;

                    Material mat = AoEDropResolver.resolve(
                            block, player.getInventory().getItemInMainHand(), plugin, pickaxeProfile);
                    if (mat.isAir()) continue;

                    entries.add(new VortexAnimation.BlockEntry(block.getLocation().clone(), mat));
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

        // Always play the vortex animation even when no extra blocks were broken,
        // so the player still sees the black hole effect on the primary broken block.
        entries.add(0, new VortexAnimation.BlockEntry(origin.getLocation().clone(), origin.getType()));
        EnchantPreferenceResult pref = getEnchantPref(player, config);

        Location center = origin.getLocation().clone().add(0.5, 0.5, 0.5);
        VortexAnimation anim = new VortexAnimation(plugin, entries, pref.sound, pref.particles);
        AnimationRegistry.getInstance().play(anim, center, List.of(player));

        if (broken > 0) {
            showNotification(player, origin, config, pref, broken);
        }
    }

    private void showNotification(Player player, Block origin, FileConfiguration config,
                                  EnchantPreferenceResult pref, int broken)
    {
        String brokenStr = String.valueOf(broken);

        if (pref.hologram && config.getBoolean(ConfigPaths.Enchants.Vortex.HOLOGRAM_ENABLED, true)) {
            String hologramText = config.getString(
                            ConfigPaths.Enchants.Vortex.HOLOGRAM_MESSAGE,
                            "&#FFAA00&lVORTEX &#CCCCCCAbsorbed %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            Location hologramLoc = origin.getLocation().add(0.5, 1.5, 0.5);
            AnimationRegistry.getInstance().play(
                    new HologramAnimation(plugin, hologramText), hologramLoc, List.of(player));
        }

        if (pref.message) {
            String message = StorageRegistryManager.getInstance()
                    .getConfig(ConfigNames.MESSAGES)
                    .getString(ConfigPaths.Messages.Enchants.VORTEX_TRIGGERED,
                               " &#FFAA00&lVORTEX &#CCCCCCAbsorbed %blocks% blocks!")
                    .replace("%blocks%", brokenStr);
            player.sendMessage(Utils.formatMessage(player, message));
        }

        if (pref.title) {
            var msgCfg = StorageRegistryManager.getInstance().getConfig(ConfigNames.MESSAGES);
            String title    = msgCfg.getString(ConfigPaths.Messages.Enchants.VORTEX_TRIGGERED_TITLE,    "&#FFAA00&lVortex");
            String subtitle = msgCfg.getString(ConfigPaths.Messages.Enchants.VORTEX_TRIGGERED_SUBTITLE, "&#CCCCCCAbsorbed %blocks% blocks!")
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

        String enchantName  = config.getString(ConfigPaths.Enchants.Vortex.NAME, "Vortex");
        boolean defaultHolo = config.getBoolean(ConfigPaths.Enchants.Vortex.HOLOGRAM_DEFAULT_ENABLED, false);
        EnchantPreference pref = profile.preferences.getOrCreateEnchantPref(enchantName, defaultHolo);

        return new EnchantPreferenceResult(pref.hologram, pref.message, pref.sound, pref.particles, pref.title);
    }

    private boolean isBreakable(Block block)
    {
        Material m = block.getType();
        if (m.isAir() || m == Material.WATER || m == Material.LAVA) return false;
        if (m == Material.PACKED_ICE && !block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) return false;
        if (PROTECTED_MATERIALS.contains(m)) return false;
        if (m.getHardness() < 0) return false;
        return true;
    }

    private record EnchantPreferenceResult(boolean hologram, boolean message,
                                           boolean sound, boolean particles, boolean title) {}
}
