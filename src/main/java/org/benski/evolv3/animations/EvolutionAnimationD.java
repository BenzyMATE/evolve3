package org.benski.evolv3.animations;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evolution animation — Dimensional Rift.
 *
 * <p>Phase overview (all times in ticks):</p>
 * <ol>
 *   <li>Form      (  0– 18) Rings grow from scale 0.</li>
 *   <li>Enter     ( 18– 35) Pickaxe flies into rift.</li>
 *   <li>Pulse     ( 35– 38) Rift surges.</li>
 *   <li>Suspense  ( 38– 50) Void particles.</li>
 *   <li>Colorize  ( 50– 92) Rings recolor inner→outer to rarity color.</li>
 *   <li>Revert    ( 92–115) Rings recolor back inner→outer to original.</li>
 *   <li>Stone     ( 92–107) Stone shoots to top-left.</li>
 *   <li>Paper     (109–124) Paper shoots to top-right.</li>
 *   <li>Pickaxe   (126–141) Pickaxe shoots to top-center.</li>
 *   <li>Hold      (141–150) All three items float.</li>
 *   <li>Dissolve  (150–222) Blocks removed 1 per 2 ticks outer→inner; each sends an essence orb.</li>
 *   <li>Callback  (238)     Tier change applied.</li>
 *   <li>Lower     (238–250) Items drift down to rift center.</li>
 *   <li>Return    (252–265) Items fly to player; delivered on arrival.</li>
 * </ol>
 */
public class EvolutionAnimationD
{
    // -------------------------------------------------------------------------
    // Timeline
    // -------------------------------------------------------------------------

    private static final int FORM_END       = 18;
    private static final int ENTER_START    = 18;
    private static final int ENTER_END      = 35;
    private static final int PULSE_START    = 35;
    private static final int PULSE_END      = 38;
    private static final int SUSPENSE_END   = 50;
    private static final int COLORIZE_START = 50;
    private static final int COLORIZE_END   = 92;

    private static final int REVERT_START   = 92;   // starts when stone fires
    private static final int REVERT_END     = 118;

    private static final int STONE_START    = 92;
    private static final int STONE_ARRIVE   = 107;
    private static final int PAPER_START    = 109;
    private static final int PAPER_ARRIVE   = 124;
    private static final int PICKAXE_START  = 126;
    private static final int PICKAXE_ARRIVE = 141;
    private static final int HOLD_END       = 150;

    private static final int DISSOLVE_START = 150;
    // 36 blocks × 2 ticks each = 72 ticks
    private static final int DISSOLVE_END   = 222;
    // Last orb travels at most ~4 blocks at 0.28 blocks/tick ≈ 14 ticks
    private static final int CALLBACK_TICK  = 238;
    private static final int LOWER_START    = 238;
    private static final int LOWER_END      = 250;
    private static final int RETURN_START   = 252;
    private static final int RETURN_END     = 265;
    private static final int TOTAL          = 268;

    // -------------------------------------------------------------------------
    // Ring config
    // -------------------------------------------------------------------------

    /** Local tick within the COLORIZE phase at which all blocks snap to the winning rarity. */
    private static final int        SWEEP_FLASH_LOCAL = 28;

    private static final int[]      DISSOLVE_ORDER  = {2, 1, 0}; // outer→inner for dissolve
    private static final int[]      REVERT_ORDER    = {0, 1, 2}; // inner→outer for revert
    private static final int        TOTAL_BLOCKS    = 8 + 12 + 16; // 36

    private static final int[]      RING_SIZES      = {8,  12, 16};
    private static final double[]   RING_RADII      = {0.70, 1.30, 1.90};
    private static final double[]   RING_SPEEDS     = {0.10, -0.07, 0.05};
    private static final float[]    RING_FULL_SCALES= {0.30f, 0.35f, 0.40f};
    private static final Material[] RING_MATERIALS  = {
            Material.CRYING_OBSIDIAN,
            Material.PURPUR_BLOCK,
            Material.OBSIDIAN
    };
    private static final int NUM_RINGS = RING_SIZES.length;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final JavaPlugin    plugin;
    private final Player        player;
    private final EvolutionTier currentTier;
    private final EvolutionTier nextTier;
    private final RarityConfig  winningRarity;
    private final String        stoneEnchantName;
    private final Runnable      callback;

    private final BlockDisplay[][] ringBlocks    = new BlockDisplay[NUM_RINGS][];
    private final double[]         ringAngles    = new double[NUM_RINGS];
    private final float[]          ringScales    = new float[NUM_RINGS];
    private final boolean[][]      ringColorized = new boolean[NUM_RINGS][];
    private final boolean[][]      ringReverted  = new boolean[NUM_RINGS][];

    private Location riftCenter;
    private Vector   rightDir;
    private Vector   upDir;
    private Vector   forwardDir;
    private float    portalYaw;

    // Item hover positions (above portal)
    private Location stoneFinalPos;
    private Location paperFinalPos;
    private Location pickaxeFinalPos;

    // Item lowered positions (at portal center, just before return)
    private Location stoneLowerPos;
    private Location paperLowerPos;
    private Location pickaxeLowerPos;

    private ItemStack heldPickaxe;
    private int       heldSlot;

    private ItemDisplay currentPickaxeDisplay;
    private Location    pickaxeSpawnLoc;

    private ItemDisplay stoneDisplay;
    private TextDisplay stoneLabelText;
    private ItemDisplay paperDisplay;
    private TextDisplay paperLabelText;
    private ItemDisplay exitPickaxeDisplay;

    private final List<Location[]> flyingOrbs = new ArrayList<>();
    private int         orbsLanded      = 0;
    private int         colorizedCount  = 0;
    private TextDisplay essenceText;
    private TextDisplay evolutionTitle;
    private Location    titleLowerPos;

    private boolean  callbackFired = false;
    private boolean  itemsReturned = false;
    private int      currentTick   = 0;
    private boolean  finished      = false;
    /** Optional callback invoked after items are successfully delivered to an online player. */
    private Runnable onComplete    = null;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public EvolutionAnimationD(@NotNull JavaPlugin plugin,
                               @NotNull Player player,
                               @NotNull EvolutionTier currentTier,
                               @NotNull EvolutionTier nextTier,
                               @NotNull RarityConfig winningRarity,
                               @NotNull Runnable callback)
    {
        this.plugin           = plugin;
        this.player           = player;
        this.currentTier      = currentTier;
        this.nextTier         = nextTier;
        this.winningRarity    = winningRarity;
        this.stoneEnchantName = pickRandomEnchant(player);
        this.callback         = callback;
    }

    /** Picks a random enchant display name from the player's unlocked enchants, or from all enchants if none. */
    private static String pickRandomEnchant(Player player) {
        PickaxeDataCenter pdc = (PickaxeDataCenter) DataCenterRegistry.getInstance().get("pickaxe");
        if (pdc != null) {
            PickaxeProfile profile = pdc.getLocal(player);
            if (profile != null && !profile.enchants.items.isEmpty()) {
                List<String> displayNames = profile.enchants.items.stream()
                        .map(e -> EnchantAnimationConfig.getDisplayName(e.name))
                        .filter(n -> n != null && !n.isEmpty())
                        .toList();
                if (!displayNames.isEmpty()) {
                    return displayNames.get(ThreadLocalRandom.current().nextInt(displayNames.size()));
                }
            }
        }
        List<String> all = EnchantAnimationConfig.getEnchantOrder();
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }

    // -------------------------------------------------------------------------
    // Public state accessors
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the evolution callback has already fired (tier change applied). */
    public boolean isCallbackFired() { return callbackFired; }

    /** Returns {@code true} if the animation has finished or been stopped. */
    public boolean isFinished()      { return finished; }

    /**
     * Returns the pickaxe that was taken from the player's hand when the animation started,
     * or {@code null} if the animation has not started yet.
     */
    public ItemStack getHeldPickaxe() { return heldPickaxe; }

    /**
     * Registers a callback invoked after items are successfully delivered to the player.
     * Only fires when the player is online at delivery time — it is not called if the
     * animation is stopped early or if the player is offline during cleanup.
     *
     * @param onComplete the runnable to invoke on successful delivery
     */
    public void setOnComplete(Runnable onComplete) { this.onComplete = onComplete; }

    /**
     * Stops the animation immediately, removing all spawned entities.
     * Does <em>not</em> fire the evolution callback and does <em>not</em> deliver items
     * to the player — the caller is responsible for handling those.
     */
    public void stop()
    {
        if (finished) return;
        finished = true;
        for (BlockDisplay[] ring : ringBlocks) {
            if (ring == null) continue;
            for (BlockDisplay bd : ring) { if (bd != null && !bd.isDead()) bd.remove(); }
        }
        if (currentPickaxeDisplay != null && !currentPickaxeDisplay.isDead()) currentPickaxeDisplay.remove();
        flyingOrbs.clear();
        removeItemDisplays();
    }

    // -------------------------------------------------------------------------
    // Start
    // -------------------------------------------------------------------------

    public void start()
    {
        Location eyeLoc = player.getEyeLocation();
        World    world  = eyeLoc.getWorld();
        if (world == null) return;

        forwardDir = eyeLoc.getDirection().setY(0).normalize();
        rightDir   = forwardDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        upDir      = new Vector(0, 1, 0);

        riftCenter = eyeLoc.clone().add(forwardDir.clone().multiply(3.5));
        portalYaw  = (float) (Math.atan2(-forwardDir.getX(), forwardDir.getZ()) + Math.PI);

        // Hover positions — above the outer ring top (up*1.90)
        stoneFinalPos   = riftCenter.clone().add(upDir.clone().multiply(3.1)).add(rightDir.clone().multiply(-1.7));
        paperFinalPos   = riftCenter.clone().add(upDir.clone().multiply(3.1)).add(rightDir.clone().multiply(1.7));
        pickaxeFinalPos = riftCenter.clone().add(upDir.clone().multiply(3.8));

        // Lower positions — at rift center level, side by side
        stoneLowerPos   = riftCenter.clone().add(rightDir.clone().multiply(-0.8)).add(upDir.clone().multiply(0.3));
        paperLowerPos   = riftCenter.clone().add(rightDir.clone().multiply(0.8)).add(upDir.clone().multiply(0.3));
        pickaxeLowerPos = riftCenter.clone().add(upDir.clone().multiply(0.5));
        titleLowerPos   = riftCenter.clone().add(upDir.clone().multiply(1.9));

        // Pickaxe enters from directly in front of player at chest height (centered)
        pickaxeSpawnLoc = player.getLocation().clone()
                .add(0, 1.2, 0)
                .add(forwardDir.clone().multiply(1.0));

        heldSlot    = player.getInventory().getHeldItemSlot();
        heldPickaxe = player.getInventory().getItemInMainHand().clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        for (int r = 0; r < NUM_RINGS; r++) {
            ringScales[r]    = 0.001f;
            int size         = RING_SIZES[r];
            ringBlocks[r]    = new BlockDisplay[size];
            ringColorized[r] = new boolean[size];
            ringReverted[r]  = new boolean[size];
            final Material ringMat = RING_MATERIALS[r];

            for (int b = 0; b < size; b++) {
                Location loc = ringBlockLocation(r, b);
                BlockDisplay bd = world.spawn(loc, BlockDisplay.class, e -> {
                    e.setVisibleByDefault(false);
                    e.setBlock(ringMat.createBlockData());
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setPersistent(false);
                    e.setTransformation(centeredTransform(0.001f));
                });
                player.showEntity(plugin, bd);
                ringBlocks[r][b] = bd;
            }
        }

        world.playSound(eyeLoc, Sound.BLOCK_PORTAL_TRIGGER,   0.60f, 0.55f);
        world.playSound(eyeLoc, Sound.BLOCK_PORTAL_TRAVEL,    0.30f, 0.60f);
        world.playSound(eyeLoc, Sound.BLOCK_BEACON_ACTIVATE,  0.80f, 0.45f);
        world.playSound(eyeLoc, Sound.ENTITY_ENDERMAN_STARE,  0.60f, 0.65f);

        new BukkitRunnable() {
            @Override public void run() {
                if (finished) { cancel(); return; }
                if (!player.isOnline()) { cleanup(); cancel(); return; }
                currentTick++;
                tick();
                if (currentTick >= TOTAL) { cleanup(); cancel(); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    private void tick()
    {
        World world = player.getWorld();

        for (int r = 0; r < NUM_RINGS; r++) ringAngles[r] += RING_SPEEDS[r];

        // Scale computation
        for (int r = 0; r < NUM_RINGS; r++) {
            float full = RING_FULL_SCALES[r];
            if (currentTick <= FORM_END) {
                ringScales[r] = full * ((float) currentTick / FORM_END);
            } else if (currentTick >= PULSE_START && currentTick < COLORIZE_START) {
                // Sine-wave breathing during suspense — subtle in/out rhythm
                int local = currentTick - PULSE_START;
                float wave = (float) Math.abs(Math.sin(local * Math.PI / 6.0));
                ringScales[r] = full * (1.0f + 0.20f * wave);
            } else {
                // Sharp outward pulse each time an item is shot from the portal
                float pulse = shootPulse(STONE_START) + shootPulse(PAPER_START) + shootPulse(PICKAXE_START);
                ringScales[r] = full * (1.0f + pulse);
            }
        }

        if (currentTick <= DISSOLVE_END) updateRings();

        // Void particles
        if (currentTick > FORM_END && currentTick < SUSPENSE_END && currentTick % 2 == 0) {
            player.spawnParticle(Particle.END_ROD, riftCenter, 4, 0.15, 0.15, 0.15, 0.04);
            player.spawnParticle(Particle.DUST, riftCenter, 3, 0.20, 0.20, 0.20, 0,
                    new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.5f));
        }

        // --- Pickaxe entry ---
        if (currentTick == ENTER_START) {
            spawnCurrentPickaxe(world);
            world.playSound(riftCenter, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.60f, 1.40f);
        }
        if (currentTick >= ENTER_START && currentTick <= ENTER_END) tickCurrentPickaxeEntry();
        if (currentTick == ENTER_END) {
            if (currentPickaxeDisplay != null && !currentPickaxeDisplay.isDead()) currentPickaxeDisplay.remove();
            player.spawnParticle(Particle.END_ROD, riftCenter, 20, 0.25, 0.25, 0.25, 0.10);
            world.playSound(riftCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 0.70f, 0.80f);
        }

        // --- Colorize inner→outer ---
        if (currentTick >= COLORIZE_START && currentTick <= COLORIZE_END) tickColorize();
        if (currentTick == COLORIZE_START) {
            world.playSound(riftCenter, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.00f, 0.80f);
        }

        // --- Revert back to original materials (starts when stone fires) ---
        if (currentTick >= REVERT_START && currentTick <= REVERT_END) tickRevert();

        // --- Stone → top-left ---
        if (currentTick == STONE_START) {
            spawnStone(world);
            world.playSound(riftCenter, Sound.ENTITY_ENDER_EYE_LAUNCH,    0.80f, 1.20f);
            player.spawnParticle(Particle.DUST, riftCenter, 40, 0.25, 0.25, 0.25, 0,
                    new Particle.DustOptions(winningRarity.particleColor(), 2.5f));
            player.spawnParticle(Particle.END_ROD, riftCenter, 20, 0.30, 0.30, 0.30, 0.10);
        }
        if (currentTick > STONE_START && currentTick <= STONE_ARRIVE)
            tickFlyTo(stoneDisplay, stoneLabelText, riftCenter, stoneFinalPos,
                    currentTick - STONE_START, STONE_ARRIVE - STONE_START, winningRarity.particleColor());
        if (currentTick == STONE_ARRIVE)
            world.playSound(stoneFinalPos, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.70f, 1.40f);

        // --- Paper → top-right ---
        if (currentTick == PAPER_START) {
            spawnPaper(world);
            player.spawnParticle(Particle.END_ROD, riftCenter, 15, 0.20, 0.20, 0.20, 0.08);
            world.playSound(riftCenter, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.70f, 1.00f);
            world.playSound(riftCenter, Sound.ITEM_BOOK_PAGE_TURN,    0.80f, 1.20f);
        }
        if (currentTick > PAPER_START && currentTick <= PAPER_ARRIVE)
            tickFlyTo(paperDisplay, paperLabelText, riftCenter, paperFinalPos,
                    currentTick - PAPER_START, PAPER_ARRIVE - PAPER_START, Color.fromRGB(255, 215, 0));
        if (currentTick == PAPER_ARRIVE)
            world.playSound(paperFinalPos, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.70f, 1.60f);

        // --- Pickaxe → top-center ---
        if (currentTick == PICKAXE_START) {
            spawnExitPickaxe(world);
            world.playSound(riftCenter, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.90f, 0.85f);
            world.playSound(riftCenter, Sound.BLOCK_ANVIL_LAND,       0.45f, 1.40f);
            player.spawnParticle(Particle.DUST, riftCenter, 35, 0.25, 0.25, 0.25, 0,
                    new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
            player.spawnParticle(Particle.END_ROD, riftCenter, 18, 0.30, 0.30, 0.30, 0.10);
        }
        if (currentTick > PICKAXE_START && currentTick <= PICKAXE_ARRIVE)
            tickFlyTo(exitPickaxeDisplay, null, riftCenter, pickaxeFinalPos,
                    currentTick - PICKAXE_START, PICKAXE_ARRIVE - PICKAXE_START, winningRarity.particleColor());
        if (currentTick == PICKAXE_ARRIVE)
            world.playSound(pickaxeFinalPos, Sound.BLOCK_ANVIL_LAND, 0.60f, 1.40f);

        // --- Dissolve portal blocks ---
        if (currentTick == DISSOLVE_START) {
            spawnEssenceText(world);
            world.playSound(riftCenter, Sound.ENTITY_ENDERMAN_STARE, 0.50f, 1.20f);
        }
        if (currentTick >= DISSOLVE_START && currentTick <= DISSOLVE_END) tickDissolve();

        // Advance flying essence orbs every tick
        if (currentTick > DISSOLVE_START) tickFlyingOrbs();

        // --- Callback ---
        if (currentTick == CALLBACK_TICK && !callbackFired) {
            callbackFired = true;
            callback.run();
        }

        // --- Lower items to rift center ---
        if (currentTick > LOWER_START && currentTick <= LOWER_END) tickLower();
        if (currentTick == LOWER_END) spawnEvolutionTitle(world);

        // --- Return items to player ---
        if (currentTick > RETURN_START && currentTick < RETURN_END) tickReturnToPlayer();
        if (currentTick == RETURN_END) deliverItems(world);
    }

    // -------------------------------------------------------------------------
    // Ring rendering
    // -------------------------------------------------------------------------

    private void updateRings()
    {
        for (int r = 0; r < NUM_RINGS; r++) {
            float scale = ringScales[r];
            for (int b = 0; b < ringBlocks[r].length; b++) {
                BlockDisplay bd = ringBlocks[r][b];
                if (bd == null || bd.isDead()) continue;
                bd.teleport(ringBlockLocation(r, b));
                bd.setTransformation(centeredTransform(scale));
            }
        }
    }

    private Location ringBlockLocation(int r, int b)
    {
        double theta = ringAngles[r] + (2 * Math.PI / RING_SIZES[r]) * b;
        return riftCenter.clone()
                .add(rightDir.clone().multiply(Math.cos(theta) * RING_RADII[r]))
                .add(upDir.clone().multiply(Math.sin(theta) * RING_RADII[r]));
    }

    // -------------------------------------------------------------------------
    // Colorize: progressive fill with cycling rarity colors, then synchronized flash
    // -------------------------------------------------------------------------

    /**
     * Colorize phase — progressive fill with cycling rarity colors, then a synchronized flash.
     *
     * <p>For the first {@value SWEEP_FLASH_LOCAL} local ticks, blocks fill in from the
     * inner ring outward while the fill color flicks through all rarities every 3 ticks.
     * Unfilled blocks revert to their original material each tick, so there is a clear
     * lit/unlit boundary advancing across the portal.</p>
     *
     * <p>At local tick {@value SWEEP_FLASH_LOCAL} — when the portal is fully filled —
     * every block snaps to the winning rarity in one synchronized flash, revealing the result.</p>
     */
    private void tickColorize()
    {
        int local = currentTick - COLORIZE_START;

        if (local < SWEEP_FLASH_LOCAL) {
            List<RarityConfig> rarities = EnchantAnimationConfig.getRarities();
            if (rarities.isEmpty()) return;

            // How many blocks are currently "filled" — linear progression inner→outer
            int fillCount = (int) Math.round((double)(local + 1) / SWEEP_FLASH_LOCAL * TOTAL_BLOCKS);
            fillCount = Math.min(fillCount, TOTAL_BLOCKS);

            // Current fill color — flicks through all rarities every 3 ticks
            int colorStep = local / 3;
            RarityConfig current = rarities.get(colorStep % rarities.size());
            Material sweepMat = current.concreteMaterial();

            // Filled blocks get the sweep color; unfilled blocks revert to original material
            int globalIndex = 0;
            for (int r = 0; r < NUM_RINGS; r++) {
                for (int b = 0; b < ringBlocks[r].length; b++) {
                    BlockDisplay bd = ringBlocks[r][b];
                    if (bd != null && !bd.isDead()) {
                        bd.setBlock(globalIndex < fillCount
                                ? sweepMat.createBlockData()
                                : RING_MATERIALS[r].createBlockData());
                    }
                    globalIndex++;
                }
            }

            // Subtle pling + particle burst each time the color flicks
            if (local % 3 == 0) {
                float pitch = Math.min(2.0f, 0.50f + colorStep * 0.12f);
                player.spawnParticle(Particle.DUST, riftCenter, 5, 0.20, 0.20, 0.20, 0,
                        new Particle.DustOptions(current.particleColor(), 1.5f));
                player.playSound(riftCenter, Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, pitch);
            }

        } else if (local == SWEEP_FLASH_LOCAL) {
            // Portal fully filled — snap all blocks to the winning rarity simultaneously
            Material rarityMat = winningRarity.concreteMaterial();
            for (int r = 0; r < NUM_RINGS; r++) {
                for (int b = 0; b < ringBlocks[r].length; b++) {
                    BlockDisplay bd = ringBlocks[r][b];
                    if (bd == null || bd.isDead()) continue;
                    bd.setBlock(rarityMat.createBlockData());
                    ringColorized[r][b] = true; // mark for tickRevert()
                }
            }
            colorizedCount = TOTAL_BLOCKS;
            World world = player.getWorld();
            player.spawnParticle(Particle.DUST, riftCenter, 100, 0.70, 0.70, 0.70, 0,
                    new Particle.DustOptions(winningRarity.particleColor(), 3.5f));
            player.spawnParticle(Particle.END_ROD, riftCenter, 35, 0.50, 0.50, 0.50, 0.15);
            world.playSound(riftCenter, Sound.ENTITY_PLAYER_LEVELUP,          0.90f, 1.50f);
            world.playSound(riftCenter, Sound.BLOCK_AMETHYST_BLOCK_RESONATE,  0.80f, 2.00f);
        }
        // local > SWEEP_FLASH_LOCAL: blocks are already at winning color — nothing to do
    }

    // -------------------------------------------------------------------------
    // Revert: inner→outer back to original materials (runs while stone/paper/pickaxe shoot)
    // -------------------------------------------------------------------------

    private void tickRevert()
    {
        int phaseLen = REVERT_END - REVERT_START;
        int local    = currentTick - REVERT_START;

        int processed = 0;
        for (int r : REVERT_ORDER) {
            int ringDelay = (r) * 6; // small stagger between rings
            int ringLocal = local - ringDelay;
            int size      = RING_SIZES[r];
            for (int b = 0; b < size; b++) {
                if (ringLocal >= 0 && !ringReverted[r][b] && ringColorized[r][b]) {
                    int target = (int) Math.round((double) processed / TOTAL_BLOCKS * phaseLen);
                    if (local >= target) {
                        ringReverted[r][b] = true;
                        BlockDisplay bd = ringBlocks[r][b];
                        if (bd != null && !bd.isDead()) {
                            bd.setBlock(RING_MATERIALS[r].createBlockData());
                        }
                    }
                }
                processed++;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pickaxe entry
    // -------------------------------------------------------------------------

    private void spawnCurrentPickaxe(World world)
    {
        ItemStack visual = heldPickaxe != null && heldPickaxe.getType() != Material.AIR
                ? heldPickaxe.clone() : new ItemStack(currentTier.material);

        currentPickaxeDisplay = world.spawn(pickaxeSpawnLoc, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setItemStack(visual);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(portalFacingTransform(0.85f));
        });
        player.showEntity(plugin, currentPickaxeDisplay);
    }

    private void tickCurrentPickaxeEntry()
    {
        if (currentPickaxeDisplay == null || currentPickaxeDisplay.isDead()) return;
        int    local  = currentTick - ENTER_START;
        double t      = (double) local / (ENTER_END - ENTER_START);
        double eased  = t * t * t;
        Location dest = pickaxeSpawnLoc.toVector().multiply(1 - eased)
                .add(riftCenter.toVector().multiply(eased)).toLocation(player.getWorld());
        currentPickaxeDisplay.teleport(dest);
        player.spawnParticle(Particle.END_ROD, dest, 2, 0.04, 0.04, 0.04, 0.01);
    }

    // -------------------------------------------------------------------------
    // Items that hover around the portal
    // -------------------------------------------------------------------------

    private void spawnStone(World world)
    {
        stoneDisplay = world.spawn(riftCenter, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setItemStack(EvolutionStone.create(winningRarity, stoneEnchantName));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(textTransform(1.6f));
        });
        player.showEntity(plugin, stoneDisplay);

        stoneLabelText = spawnLabel(world, riftCenter,
                Component.text(winningRarity.displayName() + " Evolution Stone")
                        .color(winningRarity.textColor())
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
    }

    private void spawnPaper(World world)
    {
        paperDisplay = world.spawn(riftCenter, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setItemStack(new ItemStack(Material.PAPER));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(textTransform(1.6f));
        });
        player.showEntity(plugin, paperDisplay);

        paperLabelText = spawnLabel(world, riftCenter,
                Component.text("+1 Pickaxe Slot")
                        .color(TextColor.color(0xFFD700))
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
    }

    private void spawnExitPickaxe(World world)
    {
        ItemStack visual = heldPickaxe != null && heldPickaxe.getType() != Material.AIR
                ? heldPickaxe.clone() : new ItemStack(nextTier.material);
        visual.setType(nextTier.material);

        exitPickaxeDisplay = world.spawn(riftCenter, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setItemStack(visual);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(portalFacingTransform(1.15f));
        });
        player.showEntity(plugin, exitPickaxeDisplay);
    }

    private TextDisplay spawnLabel(World world, Location loc, Component text)
    {
        TextDisplay td = world.spawn(loc.clone().add(0, 0.70, 0), TextDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.text(text);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
            e.setShadowed(true);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(textTransform(0.50f));
        });
        player.showEntity(plugin, td);
        return td;
    }

    private void spawnEssenceText(World world)
    {
        essenceText = world.spawn(pickaxeFinalPos.clone().add(0, 0.9, 0), TextDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.text(Component.empty());
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            e.setShadowed(true);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(textTransform(0.70f));
        });
        player.showEntity(plugin, essenceText);
    }

    // -------------------------------------------------------------------------
    // Dissolve: remove blocks outer→inner, 1 per 2 ticks
    // -------------------------------------------------------------------------

    private void tickDissolve()
    {
        int local    = currentTick - DISSOLVE_START;
        int duration = DISSOLVE_END - DISSOLVE_START; // 72 ticks

        int processed = 0;
        for (int ri : DISSOLVE_ORDER) {
            for (int b = 0; b < RING_SIZES[ri]; b++) {
                // Each block spaced 2 ticks apart
                int dissolveTick = processed * 2;
                if (local >= dissolveTick && ringBlocks[ri][b] != null) {
                    dissolveBlock(ri, b);
                }
                processed++;
            }
        }
    }

    private void dissolveBlock(int r, int b)
    {
        BlockDisplay bd = ringBlocks[r][b];
        if (bd == null || bd.isDead()) return;
        Location pos = bd.getLocation().clone();
        bd.remove();
        ringBlocks[r][b] = null;

        player.spawnParticle(Particle.DUST, pos, 5, 0.08, 0.08, 0.08, 0,
                new Particle.DustOptions(winningRarity.particleColor(), 1.2f));
        player.spawnParticle(Particle.END_ROD, pos, 2, 0.04, 0.10, 0.04, 0.02);

        // Crystalline crack as the block shatters, pitch varies per ring
        float pitch = 1.30f + r * 0.15f;
        player.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.75f, pitch);
        player.playSound(pos, Sound.ENTITY_VEX_AMBIENT,          0.50f, 1.90f);

        flyingOrbs.add(new Location[]{ pos, pickaxeFinalPos.clone() });
    }

    // -------------------------------------------------------------------------
    // Essence orbs flying toward the pickaxe (END_ROD + WITCH = essence look)
    // -------------------------------------------------------------------------

    private void tickFlyingOrbs()
    {
        Iterator<Location[]> it = flyingOrbs.iterator();
        while (it.hasNext()) {
            Location[] orb    = it.next();
            Location   pos    = orb[0];
            Location   target = orb[1];
            Vector     dir    = target.toVector().subtract(pos.toVector());

            if (dir.length() < 0.35) {
                orbsLanded++;
                player.spawnParticle(Particle.END_ROD, target, 3, 0.06, 0.06, 0.06, 0.02);
                player.spawnParticle(Particle.WITCH,   target, 5, 0.08, 0.08, 0.08, 0.02);
                player.playSound(target, Sound.ENTITY_VEX_AMBIENT, 0.35f, 1.8f + (orbsLanded % 3) * 0.1f);
                it.remove();
                updateEssenceText();
            } else {
                dir.normalize().multiply(0.28);
                pos.add(dir);
                player.spawnParticle(Particle.END_ROD, pos, 1, 0.02, 0.02, 0.02, 0.01);
                player.spawnParticle(Particle.WITCH,   pos, 1, 0.05, 0.05, 0.05, 0.02);
            }
        }
    }

    private void updateEssenceText()
    {
        if (essenceText == null || essenceText.isDead()) return;
        essenceText.text(Component.text("\uD83D\uDD25 +" + (orbsLanded * 5) + " Essence")
                .color(TextColor.color(0xB366D9))
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
    }

    // -------------------------------------------------------------------------
    // Generic fly-to: item shoots from origin toward target
    // -------------------------------------------------------------------------

    private void tickFlyTo(ItemDisplay display, TextDisplay label,
                           Location origin, Location target,
                           int local, int totalTicks, Color trailColor)
    {
        if (display == null || display.isDead()) return;
        double eased = 1 - Math.pow(1 - (double) local / totalTicks, 3);
        Location dest = origin.toVector().multiply(1 - eased)
                .add(target.toVector().multiply(eased)).toLocation(player.getWorld());
        display.teleport(dest);
        if (label != null && !label.isDead()) label.teleport(dest.clone().add(0, 0.70, 0));
        player.spawnParticle(Particle.DUST, dest, 2, 0.05, 0.05, 0.05, 0,
                new Particle.DustOptions(trailColor, 1.2f));
        player.spawnParticle(Particle.END_ROD, dest, 1, 0.03, 0.03, 0.03, 0.005);
    }

    // -------------------------------------------------------------------------
    // Lower: items drift down to rift center area
    // -------------------------------------------------------------------------

    private void tickLower()
    {
        int local = currentTick - LOWER_START;
        int len   = LOWER_END - LOWER_START;
        double eased = 1 - Math.pow(1 - (double) local / len, 2); // ease-out

        moveDisplay(stoneDisplay,       stoneLabelText,  stoneFinalPos,   stoneLowerPos,   eased);
        moveDisplay(paperDisplay,       paperLabelText,  paperFinalPos,   paperLowerPos,   eased);
        moveDisplay(exitPickaxeDisplay, null,            pickaxeFinalPos, pickaxeLowerPos, eased);
        if (essenceText != null && !essenceText.isDead()) {
            Location dest = pickaxeFinalPos.toVector().multiply(1 - eased)
                    .add(pickaxeLowerPos.toVector().multiply(eased)).toLocation(player.getWorld());
            essenceText.teleport(dest.clone().add(0, 0.9, 0));
        }
    }

    // -------------------------------------------------------------------------
    // Return: items fly from lower positions to player
    // -------------------------------------------------------------------------

    private void tickReturnToPlayer()
    {
        int local = currentTick - RETURN_START;
        int len   = RETURN_END - RETURN_START;
        double eased = Math.pow((double) local / len, 3); // ease-in, accelerates toward player

        Location chest = player.getLocation().add(0, 1.3, 0);
        moveDisplay(stoneDisplay,       stoneLabelText,  stoneLowerPos,   chest, eased);
        moveDisplay(paperDisplay,       paperLabelText,  paperLowerPos,   chest, eased);
        moveDisplay(exitPickaxeDisplay, null,            pickaxeLowerPos, chest, eased);
        if (essenceText != null && !essenceText.isDead()) {
            Location dest = pickaxeLowerPos.toVector().multiply(1 - eased)
                    .add(chest.toVector().multiply(eased)).toLocation(player.getWorld());
            essenceText.teleport(dest.clone().add(0, 0.9, 0));
        }
        if (evolutionTitle != null && !evolutionTitle.isDead()) {
            Location dest = titleLowerPos.toVector().multiply(1 - eased)
                    .add(chest.toVector().multiply(eased)).toLocation(player.getWorld());
            evolutionTitle.teleport(dest);
        }
    }

    private void moveDisplay(ItemDisplay display, TextDisplay label,
                             Location from, Location to, double eased)
    {
        if (display == null || display.isDead()) return;
        Location dest = from.toVector().multiply(1 - eased)
                .add(to.toVector().multiply(eased)).toLocation(player.getWorld());
        display.teleport(dest);
        if (label != null && !label.isDead()) label.teleport(dest.clone().add(0, 0.70, 0));
    }

    // -------------------------------------------------------------------------
    // Deliver items when they reach the player
    // -------------------------------------------------------------------------

    private void deliverItems(World world)
    {
        if (itemsReturned) return;
        itemsReturned = true;

        // Return pickaxe FIRST so addItem cannot accidentally fill the held slot.
        if (heldPickaxe != null && heldPickaxe.getType() != Material.AIR && player.isOnline()) {
            heldPickaxe.setType(nextTier.material);
            ItemStack cur = player.getInventory().getItem(heldSlot);
            if (cur == null || cur.getType() == Material.AIR) {
                player.getInventory().setItem(heldSlot, heldPickaxe);
            } else {
                // Slot was taken; find any free slot or drop
                if (player.getInventory().firstEmpty() >= 0) {
                    player.getInventory().addItem(heldPickaxe);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), heldPickaxe);
                }
            }
        }

        if (!player.isOnline()) {
            // Player disconnected — items will be delivered by EvolutionSelectionManager on rejoin.
            // Do NOT fire onComplete here; the pending entry must survive for item recovery.
            removeItemDisplays();
            return;
        }

        // Stone reward
        player.getInventory().addItem(EvolutionStone.create(winningRarity, stoneEnchantName));

        world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP,         0.80f, 1.10f);
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.60f, 1.40f);
        player.playSound(net.kyori.adventure.sound.Sound.sound(
                Key.key("minecraft", "ui.toast.challenge_complete"),
                Source.MASTER, 1.0f, 1.0f));
        removeItemDisplays();

        // Items delivered successfully — notify the selection manager to clean up
        if (onComplete != null) onComplete.run();
    }

    private void removeItemDisplays()
    {
        if (stoneDisplay       != null && !stoneDisplay.isDead())       stoneDisplay.remove();
        if (stoneLabelText     != null && !stoneLabelText.isDead())     stoneLabelText.remove();
        if (paperDisplay       != null && !paperDisplay.isDead())       paperDisplay.remove();
        if (paperLabelText     != null && !paperLabelText.isDead())     paperLabelText.remove();
        if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) exitPickaxeDisplay.remove();
        if (essenceText        != null && !essenceText.isDead())        essenceText.remove();
        if (evolutionTitle     != null && !evolutionTitle.isDead())     evolutionTitle.remove();
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    private void cleanup()
    {
        if (finished) return;
        finished = true;
        for (BlockDisplay[] ring : ringBlocks) {
            if (ring == null) continue;
            for (BlockDisplay bd : ring) { if (bd != null && !bd.isDead()) bd.remove(); }
        }
        if (currentPickaxeDisplay != null && !currentPickaxeDisplay.isDead()) currentPickaxeDisplay.remove();
        flyingOrbs.clear();
        removeItemDisplays();
        if (!callbackFired) { callbackFired = true; callback.run(); }
        if (!itemsReturned) deliverItems(player.getWorld());
    }

    // -------------------------------------------------------------------------
    // Evolution title
    // -------------------------------------------------------------------------

    private void spawnEvolutionTitle(World world)
    {
        // Strip "&#RRGGBB" prefix (8 chars) from displayName to get the plain tier name
        String raw = nextTier.displayName.trim();
        String tierName = (raw.startsWith("&#") && raw.length() >= 8)
                ? raw.substring(8).trim()
                : raw;
        TextColor tierColor = tierTextColor(nextTier);

        Component title = Component.text()
                .append(Component.text("⛏  EVOLUTION " + nextTier.level)
                        .color(TextColor.color(0x55FFFF))
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.newline())
                .append(Component.text(tierName.toUpperCase())
                        .color(tierColor)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                .build();

        evolutionTitle = world.spawn(titleLowerPos, TextDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.text(title);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            e.setShadowed(true);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(textTransform(0.75f));
        });
        player.showEntity(plugin, evolutionTitle);
    }

    /** Extracts a TextColor from the EvolutionTier's displayName (format: "&#RRGGBB Name"). */
    private static TextColor tierTextColor(EvolutionTier tier)
    {
        String d = tier.displayName.trim();
        if (d.startsWith("&#") && d.length() >= 8) {
            TextColor tc = TextColor.fromHexString("#" + d.substring(2, 8));
            if (tc != null) return tc;
        }
        return TextColor.color(0xCCCCCC);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a short outward pulse contribution for when an item shoots out of the portal.
     * Uses a half-sine envelope peaking at tick 3, decaying to 0 by tick 12.
     *
     * @param popTick the absolute tick at which the item was launched
     * @return scale factor addition (0–0.25)
     */
    private float shootPulse(int popTick)
    {
        int local = currentTick - popTick;
        if (local < 0 || local >= 12) return 0f;
        return (float)(Math.sin(local * Math.PI / 12.0) * 0.25);
    }

    /** Item display in FIXED mode — player-facing, centered at entity origin (no offset). */
    private Transformation portalFacingTransform(float scale)
    {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(portalYaw, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0));
    }

    /** Block display — offset by −scale/2 to center a 1×1×1 block on the entity origin. */
    private static Transformation uniformTransform(float scale)
    {
        return new Transformation(
                new Vector3f(-scale / 2f, -scale / 2f, -scale / 2f),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0));
    }

    /** Text display — plain scale, no offset (text anchors at entity centre). */
    private static Transformation textTransform(float scale)
    {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0));
    }

    private static Transformation centeredTransform(float scale) { return uniformTransform(scale); }
}
