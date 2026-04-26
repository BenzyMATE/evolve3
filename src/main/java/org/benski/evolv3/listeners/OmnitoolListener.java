package org.benski.evolv3.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.OmnitoolManager;
import org.benski.evolv3.core.managers.OmnitoolManager.Form;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles the Omnitool ability lifecycle:
 * <ul>
 *   <li>Transforms a registered pickaxe into an axe/sword when entering the matching region.</li>
 *   <li>Reverts to the pickaxe when leaving the region or disconnecting.</li>
 *   <li>Adds pickaxe XP when breaking logs in axe form.</li>
 *   <li>Plays the pickup animation for mob drops in sword form.</li>
 *   <li>Warns the player when they try to open the pickaxe menu while transformed.</li>
 * </ul>
 */
public class OmnitoolListener extends BaseListener
{
    /** Last known omnitool region per player: "axe", "sword", or null (none). */
    private final Map<UUID, String> lastRegionState = new HashMap<>();

    public OmnitoolListener(JavaPlugin plugin)
    {
        super(plugin);
    }

    // =========================================================================
    // Region tracking
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event)
    {
        Location from = event.getFrom();
        Location to   = event.getTo();

        // Only query WorldGuard when the player crosses a block boundary
        if (from.getBlockX() == to.getBlockX()
         && from.getBlockY() == to.getBlockY()
         && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        WorldGuardManager wg = WorldGuardManager.getInstance();

        boolean inAxe   = wg.isWithinOmnitoolAxeRegion(to);
        boolean inSword = !inAxe && wg.isWithinOmnitoolSwordRegion(to);

        String current = inAxe ? "axe" : inSword ? "sword" : null;
        String last    = lastRegionState.get(player.getUniqueId());
        if (Objects.equals(current, last)) return;

        lastRegionState.put(player.getUniqueId(), current);
        OmnitoolManager omni = OmnitoolManager.getInstance();

        if (current == null)
        {
            // Left all omnitool regions — revert
            if (omni.isInOmnitoolMode(player))
            {
                omni.revertToPickaxe(player);
                playTransformAnimation(player);
                sendActionBar(player, "  &#888888⚒ Omnitool &cDeactivated");
            }
            return;
        }

        Form targetForm = current.equals("axe") ? Form.AXE : Form.SWORD;

        if (omni.isInOmnitoolMode(player))
        {
            // Already transformed — switch between axe and sword regions
            if (omni.getForm(player) != targetForm)
            {
                omni.revertToPickaxe(player);
                if (omni.transformTo(player, targetForm))
                {
                    playTransformAnimation(player);
                    sendTransformMessage(player, targetForm);
                }
            }
        }
        else
        {
            // Not yet transformed — activate
            if (omni.transformTo(player, targetForm))
            {
                playTransformAnimation(player);
                sendTransformMessage(player, targetForm);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        OmnitoolManager.getInstance().revertToPickaxe(player);
        lastRegionState.remove(player.getUniqueId());
    }

    /**
     * Bug #1: Player enters omnitool region without a pickaxe, then switches to one.
     * Movement events won't re-fire since their position hasn't changed, so we
     * catch the slot change here and trigger a transform if needed.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event)
    {
        Player player = event.getPlayer();
        String region = lastRegionState.get(player.getUniqueId());
        if (region == null) return;

        OmnitoolManager omni = OmnitoolManager.getInstance();
        if (omni.isInOmnitoolMode(player)) return;

        Form targetForm = region.equals("axe") ? Form.AXE : Form.SWORD;
        // Delay 1 tick so the inventory reflects the newly selected slot
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (omni.isInOmnitoolMode(player)) return;
            if (omni.transformTo(player, targetForm)) {
                playTransformAnimation(player);
                sendTransformMessage(player, targetForm);
            }
        }, 1L);
    }

    /**
     * Bug #2: When the omnitool sword/axe breaks, clean up the stored state and
     * restore the original pickaxe so it isn't lost.
     */
    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent event)
    {
        Player player = event.getPlayer();
        ItemMeta meta = event.getBrokenItem().getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(OmnitoolManager.OMNITOOL_ITEM_KEY, PersistentDataType.STRING)) return;

        // revertToPickaxe cleans activeForms/storedPickaxes; the omnitool slot is already
        // AIR when this event fires, so the fallback path puts the pickaxe in main hand.
        OmnitoolManager.getInstance().revertToPickaxe(player);
        // Clear region cache so the next movement re-evaluates (will re-transform if still in region)
        lastRegionState.remove(player.getUniqueId());
    }

    // =========================================================================
    // Menu warning
    // =========================================================================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction().isLeftClick()) return;

        Player player = event.getPlayer();
        if (!OmnitoolManager.getInstance().isInOmnitoolMode(player)) return;

        // Only warn when right-clicking with the tagged omnitool item
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return;
        ItemMeta meta = held.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(OmnitoolManager.OMNITOOL_ITEM_KEY, PersistentDataType.STRING)) return;

        sendActionBar(player, "&#FF5555⚠ &#FFFFFFPickaxe menu unavailable.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    // =========================================================================
    // Log break XP (axe form)
    // =========================================================================

    /**
     * Runs at MONITOR priority so other handlers can cancel the event first.
     * When a player in axe form breaks a log, XP is credited to their pickaxe.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBreak(BlockBreakEvent event)
    {
        Player player = event.getPlayer();
        OmnitoolManager omni = OmnitoolManager.getInstance();

        if (!omni.isInOmnitoolMode(player)) return;
        if (omni.getForm(player) != Form.AXE) return;
        if (!isLog(event.getBlock().getType())) return;

        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        int xpAmount = config.getInt("Omnitool.LogBreakXp", 5);
        if (xpAmount <= 0) return;

        PickaxeManager.getInstance().addXp(player, xpAmount);
        PickaxeManager.getInstance().updateLore(player, null);
        sendXpActionBar(player);
    }

    // =========================================================================
    // Mob kill pickup animation (sword form)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event)
    {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        OmnitoolManager omni = OmnitoolManager.getInstance();
        if (!omni.isInOmnitoolMode(killer)) return;
        if (omni.getForm(killer) != Form.SWORD) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        List<ItemStack> nonEmpty = drops.stream()
                .filter(i -> i != null && !i.getType().isAir())
                .toList();
        if (nonEmpty.isEmpty()) return;

        event.getDrops().clear();
        Location loc = entity.getLocation().add(0, 1, 0);

        // Give drops to the player's inventory; overflow falls naturally
        for (ItemStack drop : nonEmpty)
        {
            Map<Integer, ItemStack> overflow = killer.getInventory().addItem(drop.clone());
            if (loc.getWorld() != null)
            {
                for (ItemStack leftover : overflow.values())
                {
                    loc.getWorld().dropItemNaturally(loc, leftover);
                }
            }
        }

        // Credit configured XP to the pickaxe for the kill
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        int xpAmount = config.getInt("Omnitool.MobKillXp", 5);

        // Visual pickup animation — pass the XP amount so the hologram shows the correct value
        PickupAnimationManager.getInstance().playPickupAnimation(
                killer, loc, nonEmpty, nonEmpty.get(0).getType(),
                PreferenceType.DropCategory.NORMAL,
                Math.max(0, xpAmount));

        if (xpAmount > 0) {
            PickaxeManager.getInstance().addXp(killer, xpAmount);
            PickaxeManager.getInstance().updateLore(killer, null);
            sendXpActionBar(killer);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void playTransformAnimation(Player player)
    {
        Location loc = player.getLocation().add(0, 1, 0);
        if (loc.getWorld() == null) return;

        // Spinning ring of witch particles
        for (int i = 0; i < 20; i++)
        {
            double angle = 2 * Math.PI * i / 20;
            loc.getWorld().spawnParticle(
                    Particle.WITCH,
                    loc.clone().add(Math.cos(angle), 0, Math.sin(angle)),
                    2, 0, 0, 0, 0);
        }
        loc.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.3, 0.5, 0.3, 0.5);
        loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE,       1.0f, 1.2f);
        loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 0.8f);
    }

    private void sendTransformMessage(Player player, Form form)
    {
        sendActionBar(player, "  &#888888⚒ Omnitool &aActivated");
    }

    private static void sendActionBar(Player player, String raw)
    {
        String formatted = Utils.formatMessage(player, raw);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(formatted));
    }

    private static boolean isLog(Material material)
    {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || material == Material.BAMBOO_BLOCK;
    }

    private static void sendXpActionBar(Player player)
    {
        FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        String actionBar = config.getString(ConfigPaths.Default.Pickaxe.ACTION_BAR, "");
        if (actionBar.isEmpty()) return;
        String formatted = Utils.formatMessage(player, actionBar);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(formatted));
    }
}
