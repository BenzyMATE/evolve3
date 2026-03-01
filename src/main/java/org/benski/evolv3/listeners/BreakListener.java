package org.benski.evolv3.listeners;

import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantDropManager;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.FrostbiteEnchant;
import org.benski.evolv3.managers.PickaxeManager;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles block break events for the pickaxe progression system.
 * Integrates with backpack and enchantment systems.
 *
 * <p>Uses two event handlers at different priorities:</p>
 * <ul>
 *   <li>NORMAL — captures block state and drops while the block is still intact,
 *       validates the break, and cancels early if needed.</li>
 *   <li>HIGHEST — runs after all enchant listeners (Alchemy NORMAL, Furnace HIGH)
 *       have had a chance to set their block metadata, then resolves the final drop,
 *       plays the pickup animation, and updates the backpack.</li>
 * </ul>
 */
public class BreakListener extends BaseListener
{
    private final PickupAnimationManager pickupAnimationManager = PickupAnimationManager.getInstance();
    private final AnimationRegistry animationRegistry = AnimationRegistry.getInstance();
    private final PickaxeManager pickaxeManager = PickaxeManager.getInstance();

    /**
     * Per-player context captured at NORMAL priority, consumed at HIGHEST.
     * Ensures block type and base drop are read while the block is intact.
     */
    private final Map<UUID, BreakContext> pendingBreaks = new HashMap<>();

    private record BreakContext(
        Material blockType,
        ItemStack baseDrop,
        int dropAmount,
        boolean isEnchantDrop,
        Backpack backpack,
        PlayerProfile playerProfile,
        Location animationLocation,
        Location blockLocation
    ) {}

    public BreakListener(JavaPlugin plugin)
    {
        super(plugin);
    }

    /**
     * NORMAL priority: validates the break and captures all block-state data
     * (block type, drops) while the block is guaranteed to still be intact.
     * Cancels early for invalid conditions (wrong tool, wrong region, full backpack).
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBreakCapture(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!isPickaxe(heldItem.getType())) {
            return;
        }

        if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) {
            FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            String message = config.getString(ConfigPaths.Default.NOT_IN_MINE_REGION_MESSAGE, "&c&lYou can only mine in designated mine regions!");
            player.sendActionBar(net.kyori.adventure.text.Component.text(Utils.formatMessage(player, message)));
            return;
        }

        event.setDropItems(false);

        PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDataCenter == null) return;

        PlayerProfile playerProfile = playerDataCenter.getLocal(player);
        if (playerProfile == null) return;

        Backpack backpack = playerProfile.backpack;
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Compute drops while the block is definitely still intact.
        Collection<ItemStack> drops = block.getDrops(heldItem, player);

        // Resolve the actual drop material. For Frostbite-frozen blocks the real drop is stored
        // in metadata and must be read now — metadata does not survive block removal.
        // For normal blocks, derive it from the drops themselves (e.g. STONE → COBBLESTONE).
        final Material dropMaterial;
        if (block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) {
            String stored = block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY).getFirst().asString();
            block.removeMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY, plugin);
            Material parsed = blockType;
            try { parsed = Material.valueOf(stored); } catch (IllegalArgumentException ignored) {}
            dropMaterial = parsed;
        } else {
            dropMaterial = drops.stream().findFirst().map(ItemStack::getType).orElse(blockType);
        }

        if (backpack.isFull() && !backpack.hasMaterial(dropMaterial)) {
            FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            String fullMessage = config.getString(ConfigPaths.Default.Backpack.FULL_MESSAGE, "&c&lBACKPACK FULL");
            player.sendActionBar(net.kyori.adventure.text.Component.text(Utils.formatMessage(player, fullMessage)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            event.setCancelled(true);
            return;
        }

        ItemStack baseDrop = new ItemStack(dropMaterial);
        int dropAmount = drops.isEmpty() ? 1 : drops.stream().mapToInt(ItemStack::getAmount).sum();
        boolean isEnchantDrop = playerProfile.isEnchanting;

        pendingBreaks.put(player.getUniqueId(), new BreakContext(
                blockType,
                baseDrop,
                dropAmount,
                isEnchantDrop,
                backpack,
                playerProfile,
                event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                event.getBlock().getLocation()
        ));
    }

    /**
     * HIGHEST priority: runs after all enchant listeners have fired.
     * Reads enchant metadata (Furnace, Alchemy, Frostbite), resolves the final drop,
     * plays the pickup animation with the correct item, and updates the backpack.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreakFinalize(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        BreakContext ctx = pendingBreaks.remove(player.getUniqueId());
        if (ctx == null) return;

        // If the event was cancelled between NORMAL and HIGHEST, clean up and bail.
        if (event.isCancelled()) {
            clearEnchantState(player.getUniqueId());
            return;
        }

        ItemStack finalDrop = handleEnchantDrops(player.getUniqueId(), ctx.baseDrop());

        pickupAnimationManager.playPickupAnimation(
                player,
                ctx.animationLocation(),
                List.of(finalDrop),
                ctx.blockType()
        );

        ctx.backpack().addItem(player, finalDrop.getType(), ctx.dropAmount(), ctx.isEnchantDrop());

        ctx.playerProfile().isEnchanting = false;

        var pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDataCenter == null) return;
        pickaxeDataCenter.get(player, pickaxeProfile -> {
            if (pickaxeProfile != null) {
                pickaxeManager.addStatistics(player, ctx.blockType(), ctx.playerProfile());
            }
        });

        checkEssenceDiscovery(player, ctx.playerProfile(), ctx.blockLocation());
        checkAtomDiscovery(player, ctx.blockLocation());

        pickaxeManager.recordBreak(player.getUniqueId());

        ctx.playerProfile().save(null);
    }

    /**
     * Checks if the material is a pickaxe.
     */
    private boolean isPickaxe(Material material)
    {
        return material.name().contains("PICKAXE");
    }

    /**
     * Resolves the final drop after enchants have fired.
     * Furnace overwrites Alchemy in {@link EnchantDropManager} when both trigger,
     * so a single pop always yields the correct result.
     * Frostbite drops are already baked into {@code drop} by onBreakCapture.
     *
     * @param playerUuid the player's UUID
     * @param drop       the base drop captured at NORMAL priority
     * @return the resolved final drop item
     */
    private ItemStack handleEnchantDrops(UUID playerUuid, ItemStack drop)
    {
        // Furnace overwrites Alchemy in EnchantDropManager when both trigger,
        // so a single pop() always gives us the correct final result.
        // Frostbite is already resolved in onBreakCapture (NORMAL) before the block breaks.
        Material enchantResult = EnchantDropManager.pop(playerUuid);
        if (enchantResult != null) {
            return new ItemStack(enchantResult);
        }

        return drop;
    }

    /**
     * Clears all enchant state for a player when a break is cancelled.
     * Frostbite metadata is already consumed in onBreakCapture, so only
     * EnchantDropManager needs clearing here.
     */
    private void clearEnchantState(UUID playerUuid)
    {
        EnchantDropManager.pop(playerUuid);
    }

    /**
     * Checks for a rare atom discovery while mining.
     */
    private void checkAtomDiscovery(Player player, Location blockLocation)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        double discoveryChance = config.getDouble(ConfigPaths.Default.Atoms.DISCOVERY_CHANCE, 1.0);

        if (!Utils.chance(discoveryChance)) return;

        int minAmount = config.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MIN_AMOUNT, 1);
        int maxAmount = config.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MAX_AMOUNT, 3);
        int amount    = Utils.amount(minAmount, maxAmount);

        var pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDataCenter == null) return;

        pickaxeDataCenter.get(player, pickaxeProfile -> {
            if (pickaxeProfile != null) {
                pickaxeProfile.evolutionAtoms += amount;
                pickaxeManager.checkAtoms(pickaxeProfile, player);
                pickaxeProfile.save(null);
            }
        });

        animationRegistry.play("atom", blockLocation, List.of(player), amount);
    }

    /**
     * Checks for random essence discovery while mining.
     */
    private void checkEssenceDiscovery(Player player, PlayerProfile playerProfile, Location blockLocation)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        double discoveryChance = config.getDouble("Essence.DiscoveryChance", 5.0);

        if (Utils.chance(discoveryChance)) {
            int minAmount = config.getInt("Essence.DiscoveryMinAmount", 1);
            int maxAmount = config.getInt("Essence.DiscoveryMaxAmount", 5);
            int amount = Utils.amount(minAmount, maxAmount);

            playerProfile.essence += amount;

            var pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
            if (pickaxeDataCenter == null) return;
            pickaxeDataCenter.get(player, pickaxeProfile -> {
                if (pickaxeProfile != null) {
                    pickaxeProfile.currentEssence += amount;
                    pickaxeManager.checkEssence(pickaxeProfile, player);
                    pickaxeProfile.save(null);
                }
            });

            animationRegistry.play("essence", blockLocation, List.of(player), amount);
        }
    }
}
