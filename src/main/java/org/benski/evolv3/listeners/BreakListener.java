package org.benski.evolv3.listeners;

import org.benski.evolv3.animations.pickup.DissolvePickupAnimation;
import org.benski.evolv3.listeners.BackpackListener;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.items.AncientResin;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantDropManager;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.FrostbiteEnchant;
import org.benski.evolv3.managers.PickaxeManager;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
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
            event.setCancelled(true);
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

        if (backpack.isFull()) {
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

        // Log blocks mined with a pickaxe go through the log-mine path (resin roll, no essence/atoms)
        if (isLog(ctx.blockType())) {
            handlePickaxeLogBreak(player, ctx, finalDrop);
            return;
        }

        // Pre-roll essence and atom chances so the animation can reflect the outcome visually.
        FileConfiguration rollConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        boolean essenceHit = Utils.chance(rollConfig.getDouble("Essence.DiscoveryChance", 5.0));
        boolean atomHit    = Utils.chance(rollConfig.getDouble(ConfigPaths.Default.Atoms.DISCOVERY_CHANCE, 1.0));

        PreferenceType.DropCategory dropCategory = essenceHit ? PreferenceType.DropCategory.ESSENCE
                                                 : atomHit    ? PreferenceType.DropCategory.ATOM
                                                 : PreferenceType.DropCategory.NORMAL;

        pickupAnimationManager.playPickupAnimation(
                player,
                ctx.animationLocation(),
                List.of(finalDrop),
                ctx.blockType(),
                dropCategory
        );

        ctx.backpack().addItem(player, finalDrop.getType(), ctx.dropAmount(), ctx.isEnchantDrop());
        BackpackListener.refresh(player);

        ctx.playerProfile().isEnchanting = false;

        var pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDataCenter == null) return;
        pickaxeDataCenter.get(player, pickaxeProfile -> {
            if (pickaxeProfile != null) {
                pickaxeManager.addStatistics(player, ctx.blockType(), ctx.playerProfile());
            }
        });

        // If the player is using the dissolve animation, delay secondary animations so they
        // fire at the dissolve phase (when the block visually explodes) rather than immediately.
        boolean isDissolve = ctx.playerProfile().preferences.pickupEffect == PreferenceType.PickupEffect.DEFAULT;

        // Essence/atom animations are controlled per-component inside the animation classes.
        // Only check whether any essence/atom FX is worth running at all (not all disabled).
        boolean showEssence = ctx.playerProfile().preferences.essenceSound
                || ctx.playerProfile().preferences.essenceParticles
                || ctx.playerProfile().preferences.essenceGlow
                || ctx.playerProfile().preferences.essenceHologram;
        boolean showAtom    = ctx.playerProfile().preferences.atomSound
                || ctx.playerProfile().preferences.atomParticles
                || ctx.playerProfile().preferences.atomGlow
                || ctx.playerProfile().preferences.atomHologram;

        // hologram always shows at the mine block location; animation starts from float top when dissolving
        final Location hologramLoc = ctx.blockLocation().clone().add(0.5, 1.5, 0.5);

        if (isDissolve) {
            long delay = DissolvePickupAnimation.SECONDARY_ANIM_DELAY;
            // float top ≈ block floor level + FLOAT_HEIGHT; block floor is blockLocation.y, center is +0.5,
            // so origin passed to animation adds (0.5,0.5,0.5) inside — we subtract 0.5 from y so it lands at float top
            final Location floatTop = ctx.blockLocation().clone().add(0, DissolvePickupAnimation.FLOAT_HEIGHT - 0.5, 0);
            if (essenceHit && showEssence) {
                final PlayerProfile delayedProfile = ctx.playerProfile();
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> checkEssenceDiscovery(player, delayedProfile, floatTop, hologramLoc), delay);
            }
            if (atomHit && showAtom) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> checkAtomDiscovery(player, floatTop, hologramLoc), delay);
            }
        } else {
            if (essenceHit && showEssence) checkEssenceDiscovery(player, ctx.playerProfile(), ctx.blockLocation(), hologramLoc);
            if (atomHit    && showAtom)    checkAtomDiscovery(player, ctx.blockLocation(), hologramLoc);
        }

        pickaxeManager.recordBreak(player.getUniqueId());

        ctx.playerProfile().save(null);
    }

    /**
     * Handles bare-hand log breaks in mine regions.
     * Plays the player's chosen pickup animation for the log drop, then rolls
     * a chance for Ancient Resin — if successful, plays the resin extraction
     * animation which physically drops the item on completion.
     *
     * <p>Pickaxe breaks are handled by {@link #onBreakCapture} / {@link #onBreakFinalize}
     * and are intentionally not affected here.</p>
     *
     * <p>ignoreCancelled = false so this fires even if WorldGuard or another plugin
     * cancelled the event first — we explicitly allow bare-hand log breaks in mine regions.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onLogBreak(BlockBreakEvent event)
    {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Material blockType = block.getType();

        if (!isLog(blockType)) return;

        // Skip if holding a pickaxe — that break is handled by onBreakCapture/onBreakFinalize
        if (isPickaxe(player.getInventory().getItemInMainHand().getType())) return;

        if (!WorldGuardManager.getInstance().isWithinMineRegion(block.getLocation())) return;

        PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDataCenter == null) return;

        PlayerProfile playerProfile = playerDataCenter.getLocal(player);
        if (playerProfile == null) return;

        Backpack backpack = playerProfile.backpack;

        // Suppress vanilla drops — all checks passed, we own this break
        event.setCancelled(false); // un-cancel if WorldGuard blocked it
        event.setDropItems(false);

        if (backpack.isFull()) {
            FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
            String fullMessage = cfg.getString(ConfigPaths.Default.Backpack.FULL_MESSAGE, "&c&lBACKPACK FULL");
            player.sendActionBar(net.kyori.adventure.text.Component.text(Utils.formatMessage(player, fullMessage)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            event.setCancelled(true);
            return;
        }

        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        // Play the player's preferred pickup animation for the log
        pickupAnimationManager.playPickupAnimation(
                player,
                center,
                List.of(new ItemStack(blockType)),
                blockType,
                PreferenceType.DropCategory.NORMAL
        );

        backpack.addItem(player, blockType, 1, false);
        BackpackListener.refresh(player);
        playerProfile.save(null);

        // Roll Ancient Resin drop chance
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        double dropChance = config.getDouble(ConfigPaths.Default.AncientResin.DROP_CHANCE, 25.0);
        if (!Utils.chance(dropChance)) return;

        // Play the resin extraction animation — it adds the item to inventory on completion
        animationRegistry.play("resin", center, List.of(player));
    }

    /**
     * Handles a log broken with a pickaxe — mirrors bare-hand log behaviour:
     * plays the pickup animation, adds the log to the backpack, rolls for Ancient Resin,
     * and skips essence/atom discovery entirely.
     */
    private void handlePickaxeLogBreak(Player player, BreakContext ctx, ItemStack finalDrop)
    {
        pickupAnimationManager.playPickupAnimation(
                player,
                ctx.animationLocation(),
                List.of(finalDrop),
                ctx.blockType(),
                PreferenceType.DropCategory.NORMAL
        );

        ctx.backpack().addItem(player, finalDrop.getType(), ctx.dropAmount(), false);
        BackpackListener.refresh(player);
        ctx.playerProfile().isEnchanting = false;

        var pickaxeDataCenter = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (pickaxeDataCenter != null) {
            pickaxeDataCenter.get(player, pickaxeProfile -> {
                if (pickaxeProfile != null) {
                    pickaxeManager.addStatistics(player, ctx.blockType(), ctx.playerProfile());
                }
            });
        }

        // Roll Ancient Resin chance
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        double dropChance = config.getDouble(ConfigPaths.Default.AncientResin.DROP_CHANCE, 25.0);
        if (Utils.chance(dropChance)) {
            animationRegistry.play("resin", ctx.animationLocation(), List.of(player));
        }

        pickaxeManager.recordBreak(player.getUniqueId());
        ctx.playerProfile().save(null);
    }

    /**
     * Applies the Unbreaking enchant: rolls a chance to negate pickaxe durability damage.
     * Chance = (enchantLevel / maxLevel) * maxChance
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event)
    {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        String pickaxeId = ItemStackExtensions.getData(item, "id");
        if (pickaxeId == null || pickaxeId.isEmpty()) return;

        PickaxeDataCenter dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        if (dc == null) return;

        PickaxeProfile profile = dc.getAll().get(pickaxeId);
        if (profile == null) return;

        EnchantData unbreaking = profile.enchants.getEnchant(Enchants.UNBREAKING);
        if (unbreaking == null || !unbreaking.enabled || unbreaking.level <= 0) return;

        double negateChance = unbreaking.getCurrentChance();
        if (Utils.chance(negateChance)) {
            event.setCancelled(true);
        }
    }

    /**
     * Checks if the material is a log or stem.
     */
    private boolean isLog(Material material)
    {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM");
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
     * Applies atom discovery rewards and animation. Caller is responsible for the chance roll.
     *
     * @param animLoc     where the atom particle animation originates (float top for dissolve)
     * @param hologramLoc where the hologram text appears (original mine block)
     */
    private void checkAtomDiscovery(Player player, Location animLoc, Location hologramLoc)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);

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

        animationRegistry.play("atom", animLoc, List.of(player), amount);

        PlayerDataCenter pdcAtom = DataCenterRegistry.getInstance().getPlayerDataCenter();
        boolean atomHologramOn = pdcAtom == null || pdcAtom.getLocal(player) == null
                || pdcAtom.getLocal(player).preferences.atomHologram;

        if (atomHologramOn && config.getBoolean(ConfigPaths.Default.Atoms.HOLOGRAM_ENABLED, true)) {
            String text = config.getString(ConfigPaths.Default.Atoms.HOLOGRAM_TEXT, "&#FFEE00⚛ &#FFFFFF&l+ %amount% &#FFEE00Atoms");
            text = text.replace("%amount%", String.valueOf(amount));
            animationRegistry.play("hologram", hologramLoc, List.of(player), text);
        }
    }

    /**
     * Applies essence discovery rewards and animation. Caller is responsible for the chance roll.
     *
     * @param animLoc     where the essence particle animation originates (float top for dissolve)
     * @param hologramLoc where the hologram text appears (original mine block)
     */
    private void checkEssenceDiscovery(Player player, PlayerProfile playerProfile, Location animLoc, Location hologramLoc)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);

        int minAmount = config.getInt(ConfigPaths.Default.Essence.DISCOVERY_MIN_AMOUNT, 1);
        int maxAmount = config.getInt(ConfigPaths.Default.Essence.DISCOVERY_MAX_AMOUNT, 5);
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

        animationRegistry.play("essence", animLoc, List.of(player), amount);

        if (playerProfile.preferences.essenceHologram
                && config.getBoolean(ConfigPaths.Default.Essence.HOLOGRAM_ENABLED, true)) {
            String text = config.getString(ConfigPaths.Default.Essence.HOLOGRAM_TEXT, "&#B366D9🔥 &#FFFFFF&l+ %amount% &#B366D9Essence");
            text = text.replace("%amount%", String.valueOf(amount));
            animationRegistry.play("hologram", hologramLoc, List.of(player), text);
        }
    }
}
