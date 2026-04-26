package org.benski.evolv3.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.CaveManager;
import org.benski.evolv3.core.managers.CaveManager.CaveBounds;
import org.benski.evolv3.core.managers.MineHologramManager;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.MineManager.Mine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks block breaks inside mines for percentage-based auto-reset,
 * and detects player entry/exit from cave regions.
 *
 * <p>Block break tracking runs at {@link EventPriority#MONITOR} to ensure
 * the event was not cancelled by another listener before counting it.</p>
 */
public class MineListener extends BaseListener
{
    /** Players currently inside a cave region — prevents repeated entry messages. */
    private final Set<UUID> playersInCave = ConcurrentHashMap.newKeySet();


    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    public MineListener(JavaPlugin plugin)
    {
        super(plugin);
    }

    // -------------------------------------------------------------------------
    // Orphaned hologram cleanup
    // -------------------------------------------------------------------------

    /**
     * Two responsibilities when a chunk loads:
     * <ol>
     *   <li>Purge any leftover PDC-tagged TextDisplay entities that were saved
     *       to disk by the old {@code setPersistent(true)} build.  The startup
     *       purge only catches already-loaded chunks; this catches everything
     *       else as players explore.</li>
     *   <li>Spawn the mine hologram for this chunk if one is due — this is the
     *       primary spawn path for chunks not loaded during plugin init.</li>
     * </ol>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event)
    {
        // Step 1: remove old persistent hologram entities from chunk data
        for (org.bukkit.entity.Entity e : event.getChunk().getEntities())
        {
            if (!(e instanceof TextDisplay td)) continue;
            if (td.getPersistentDataContainer()
                  .has(MineHologramManager.PDC_KEY, PersistentDataType.STRING))
            {
                td.remove();
            }
        }

        // Step 2: spawn holograms whose position falls in this newly-loaded chunk
        MineHologramManager.getInstance().onChunkLoad(event.getChunk());
    }

    // -------------------------------------------------------------------------
    // Block break tracking
    // -------------------------------------------------------------------------

    /**
     * Runs at MONITOR priority so it only counts breaks that were not cancelled
     * by other listeners (backpack full, permission, etc.).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakMonitor(BlockBreakEvent event)
    {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        Mine mine = MineManager.getInstance().getMineAt(loc);
        if (mine == null) return;

        // Notify the manager — it handles percentage checks and auto-reset
        MineManager.getInstance().onBlockMined(mine.name());
    }

    // -------------------------------------------------------------------------
    // Cave entry / exit detection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event)
    {
        // Only check on block changes for performance
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean wasInCave = playersInCave.contains(uuid);
        boolean isInCave = false;

        for (Map.Entry<String, CaveBounds> entry : CaveManager.getInstance().getAllCaveRegions().entrySet())
        {
            if (entry.getValue().contains(to))
            {
                isInCave = true;
                break;
            }
        }

        if (isInCave && !wasInCave)
        {
            playersInCave.add(uuid);
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.8f);

            String title = MineManager.getInstance().getMessage("cave-entry");
            String subtitle = MineManager.getInstance().getMessage("cave-entry-subtitle");
            showTitle(player, title, subtitle);

            // Broadcast a floating hologram animation to all players in the mine
            playCaveDiscoveryAnimation(player, to);
        }
        else if (!isInCave && wasInCave)
        {
            playersInCave.remove(uuid);
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.9f);

            String title = MineManager.getInstance().getMessage("cave-exit");
            String subtitle = MineManager.getInstance().getMessage("cave-exit-subtitle");
            showTitle(player, title, subtitle);
        }
    }

    // -------------------------------------------------------------------------
    // Cave chest looting — despawn cave when all items are taken
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event)
    {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();

        // Resolve the chest location — handles both single and double chests
        Location chestLoc;
        Object holder = inventory.getHolder();
        if (holder instanceof Chest chest)
        {
            chestLoc = chest.getLocation();
        }
        else if (holder instanceof DoubleChest doubleChest)
        {
            // Use the left-side block location as the canonical key
            chestLoc = ((Chest) doubleChest.getLeftSide()).getLocation();
        }
        else
        {
            return;
        }

        String caveKey = CaveManager.getInstance().getCaveKeyForChest(chestLoc);
        if (caveKey == null) return;

        // Check if the chest is empty (all items taken)
        boolean empty = true;
        for (ItemStack item : inventory.getContents())
        {
            if (item != null && item.getType() != Material.AIR)
            {
                empty = false;
                break;
            }
        }

        if (!empty) return;

        // All items claimed — despawn the cave
        String mineName = CaveManager.getInstance().getMineName(caveKey);
        CaveManager.getInstance().despawnCave(caveKey);

        // Remove from in-cave tracking since the cave no longer exists
        playersInCave.remove(player.getUniqueId());

        // Teleport player to the mine spawn
        if (mineName != null)
        {
            MineManager.Mine mine = MineManager.getInstance().getMine(mineName);
            if (mine != null)
            {
                Location spawn = mine.spawnLocation();
                if (spawn == null) spawn = mine.centreTop();
                if (spawn != null) player.teleport(spawn);
            }
        }

        // Show title and play sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        String title = MineManager.getInstance().getMessage("cave-looted");
        String subtitle = MineManager.getInstance().getMessage("cave-looted-subtitle");
        showTitle(player, title, subtitle);
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        playersInCave.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void cleanup()
    {
        playersInCave.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showTitle(Player player, String titleText, String subtitleText)
    {
        Component title = LEGACY.deserialize(net.matrixcreations.libraries.MatrixColorAPI.process(FontUtils.toSmallCaps(titleText)));
        Component subtitle = LEGACY.deserialize(net.matrixcreations.libraries.MatrixColorAPI.process(FontUtils.toSmallCaps(subtitleText)));
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1400), Duration.ofMillis(400))));
    }

    /**
     * Spawns a floating TextDisplay above the mine's hologram that rises
     * upward over 3 seconds, visible to all players on the server, then
     * removes itself — announcing that a cave was discovered.
     */
    private void playCaveDiscoveryAnimation(Player discoverer, Location caveLoc)
    {
        // Find which cave and mine this location belongs to
        String caveKey = CaveManager.getInstance().getCaveKeyAt(caveLoc);
        if (caveKey == null) return;

        String mineName = CaveManager.getInstance().getMineName(caveKey);
        if (mineName == null) return;

        Mine mine = MineManager.getInstance().getMine(mineName);
        if (mine == null) return;

        // Spawn above the mine's info hologram (which sits at maxY + offsetY).
        // Using getHologramLocation() ensures we match the exact hologram position,
        // then offset upward so the discovery text floats visibly above it.
        Location spawnLoc = MineHologramManager.getInstance().getHologramLocation(mine);
        if (spawnLoc == null) return;
        spawnLoc.add(0, 4, 0); // float above the mine hologram text

        // Build the display text
        FileConfiguration msgCfg = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        String raw = "&#FFD700&l⛏ %player% &#FFD700&lfound a cave!";
        if (msgCfg != null)
        {
            raw = msgCfg.getString("mine.cave-discovered-broadcast", raw);
        }
        raw = raw.replace("%player%", discoverer.getName());
        String processed = net.matrixcreations.libraries.MatrixColorAPI.process(FontUtils.toSmallCaps(raw));
        net.kyori.adventure.text.Component text = LEGACY.deserialize(processed);

        // Spawn the entity — visible to everyone by default
        org.bukkit.entity.TextDisplay display = spawnLoc.getWorld().spawn(
                spawnLoc, org.bukkit.entity.TextDisplay.class, td ->
        {
            td.text(text);
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            td.setDefaultBackground(false);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            td.setVisibleByDefault(true);
            td.setPersistent(false);
            td.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    new org.joml.AxisAngle4f(0, 0, 0, 1),
                    new org.joml.Vector3f(3.5f, 3.5f, 3.5f),
                    new org.joml.AxisAngle4f(0, 0, 0, 1)
            ));
        });

        // Rise upward over 60 ticks (3 s), then remove
        final double[] y = { spawnLoc.getY() };
        final int[] tick = { 0 };
        Bukkit.getScheduler().runTaskTimer(plugin, task ->
        {
            if (!display.isValid() || tick[0] >= 60)
            {
                display.remove();
                task.cancel();
                return;
            }
            y[0] += 0.05;
            Location newLoc = display.getLocation();
            newLoc.setY(y[0]);
            display.teleport(newLoc);
            tick[0]++;
        }, 1L, 1L);
    }
}
