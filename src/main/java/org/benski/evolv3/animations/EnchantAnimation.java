package org.benski.evolv3.animations;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BaseEnchantAnimation;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.RarityConfig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * CS:GO-style enchant selection animation.
 *
 * <p>A strip of rarity-coloured concrete blocks scrolls right-to-left and decelerates
 * to stop with three result blocks landing under gold arrow indicators. On landing, all
 * non-result blocks scatter outward like an explosion. Each rarity block then shrinks
 * away while a representative enchant item rises from within and its name is typed out.
 * The player looks at an item and left-clicks to select it. The two unchosen items sink
 * downward one by one, the selected item fades upward, then a dramatic hologram announces
 * the unlock before fading out.</p>
 *
 * <p>All rarity colours, block materials, and enchant icons are driven by
 * {@link EnchantAnimationConfig}, which reads from {@code enchants.yml}.</p>
 */
public class EnchantAnimation extends BaseEnchantAnimation
{
    // -- Layout ---------------------------------------------------------------
    private static final double SPACING        = 0.7;
    private static final double INITIAL_OFFSET = 5.0;
    private static final double TOTAL_SCROLL   = 22 * SPACING + INITIAL_OFFSET; // 20.4
    private static final int[]  RESULT_INDICES = {20, 22, 24};
    private static final int    STRIP_SIZE     = 30;

    // -- Timing ---------------------------------------------------------------
    // scrollEndTick is instance-based so it can be scaled by player speed preference
    private static final int SCATTER_DURATION  = 18;
    private static final int REVEAL_STAGGER    = 14;
    private static final int REVEAL_DURATION   = 22;
    private static final int MAX_DURATION      = 800;

    private final int scrollEndTick;
    private final int revealStartTick;
    private final int selectionReady;

    // -- Display scales -------------------------------------------------------
    private static final float BLOCK_SCALE = 0.52f;
    private static final float ITEM_SCALE  = 0.85f;

    private static final Random RAND = new Random();

    // -- State ----------------------------------------------------------------
    private Vector    forwardDir;
    private Vector    rightDir;
    private Location  stripCenter;
    /**
     * -- GETTER --
     *  Returns the item that was in the player's main hand when the animation
     *  started.  Used by
     *  to preserve the pickaxe across a player disconnect.
     *
     * @return the original item, or {@code null} if the animation has not started yet
     */
    @Getter
    private ItemStack originalItem;

    private final BlockDisplay[] stripBlocks     = new BlockDisplay[STRIP_SIZE];
    private final TextDisplay[]  arrows          = new TextDisplay[3];
    private final TextDisplay[]  labels          = new TextDisplay[3];
    private final ItemDisplay[]  enchantItems    = new ItemDisplay[3];
    private final boolean[]      revealStarted   = new boolean[3];
    private final boolean[]      revealDone      = new boolean[3];
    private final Location[]     resultPos       = new Location[3];
    private final Vector[]       scatterVels     = new Vector[STRIP_SIZE];
    private final boolean[]      nonSelRemoved   = new boolean[3];
    private final Material[]     fillerMaterials = new Material[STRIP_SIZE];

    private boolean labelsSpawned = false;
    private boolean scatterActive = false;
    private boolean arrowsRemoved = false;

    // -------------------------------------------------------------------------

    /**
     * Creates an enchant selection animation.
     *
     * @param plugin         the plugin instance
     * @param player         the player receiving the selection
     * @param enchantOptions the three options to present
     * @param onSelect       callback invoked when an option is chosen
     * @param scrollEndTick  how many ticks the strip scrolls before stopping
     *                       (controls animation speed — lower = faster)
     */
    public EnchantAnimation(JavaPlugin plugin, org.bukkit.entity.Player player,
                            List<EnchantOption> enchantOptions,
                            BiConsumer<org.bukkit.entity.Player, EnchantOption> onSelect,
                            int scrollEndTick) {
        super(plugin, player, enchantOptions, onSelect, MAX_DURATION);
        this.scrollEndTick  = scrollEndTick;
        this.revealStartTick = scrollEndTick + SCATTER_DURATION + 4;
        this.selectionReady  = revealStartTick + 2 * REVEAL_STAGGER + REVEAL_DURATION + 16;
    }

    // -------------------------------------------------------------------------
    // BaseEnchantAnimation hooks
    // -------------------------------------------------------------------------

    @Override
    public int getSelectionReadyTick() {
        return selectionReady;
    }

    @Override
    public Location getOptionPosition(int i) {
        if (i < 0 || i >= RESULT_INDICES.length) return null;
        if (enchantItems[i] != null && !enchantItems[i].isDead()) {
            return enchantItems[i].getLocation();
        }
        if (resultPos[i] != null) return resultPos[i];
        if (stripCenter == null) return null;
        double rightAxis = RESULT_INDICES[i] * SPACING + INITIAL_OFFSET - TOTAL_SCROLL;
        return stripCenter.clone().add(rightDir.clone().multiply(rightAxis));
    }

    @Override
    protected void doStart() {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        forwardDir = player.getLocation().getDirection().setY(0).normalize();
        rightDir   = forwardDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        double eyeOffset = player.getEyeLocation().getY() - playerLoc.getY() - 0.3;
        stripCenter = playerLoc.clone()
            .add(0, eyeOffset, 0)
            .add(forwardDir.clone().multiply(2.8));

        originalItem = player.getInventory().getItemInMainHand().clone();
        if (originalItem.getType() == Material.AIR) {
            originalItem = new ItemStack(Material.DIAMOND_PICKAXE);
        }
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        // Pre-generate filler colours using rarity weights so the strip reflects real odds
        for (int i = 0; i < STRIP_SIZE; i++) {
            if (!isResultIndex(i)) {
                RarityConfig rarity = EnchantAnimationConfig.rollRarity();
                fillerMaterials[i] = rarity != null ? rarity.concreteMaterial() : Material.WHITE_CONCRETE;
            }
        }

        // Spawn 30 concrete BlockDisplays at pre-scroll positions
        for (int i = 0; i < STRIP_SIZE; i++) {
            Material mat = materialForSlot(i);
            double rightAxis = i * SPACING + INITIAL_OFFSET;
            Location spawnLoc = stripCenter.clone().add(rightDir.clone().multiply(rightAxis));
            final int idx = i;

            BlockDisplay block = world.spawn(spawnLoc, BlockDisplay.class, e -> {
                e.setBlock(mat.createBlockData());
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setTransformation(centeredTransform(BLOCK_SCALE));
            });
            stripBlocks[idx] = block;
        }

        // Spawn 3 fixed gold ▼ arrow indicators above the landing positions
        double[] indicatorOffsets = {-1.4, 0.0, 1.4};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Location arrowLoc = stripCenter.clone()
                .add(rightDir.clone().multiply(indicatorOffsets[idx]))
                .add(0, 0.65, 0);

            TextDisplay arrow = world.spawn(arrowLoc, TextDisplay.class, e -> {
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.text(Component.text("\u25BC")
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
                e.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
            });
            arrows[idx] = arrow;
        }

        world.playSound(playerLoc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.5f);
        world.playSound(playerLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 0.6f);
    }

    @Override
    protected void doTick(double progress) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        int tick = getCurrentTick();

        if (!selectionMade) {
            tickScroll(world, tick);
            tickScatter(world, tick);
            tickReveal(world, tick);
            tickIdleParticles(world, tick);
        } else {
            tickSelectionAnim(world, playerLoc, tick);
        }
    }

    @Override
    protected void doSelect(int index) {
        World world = player.getWorld();

        world.playSound(stripCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
        world.playSound(stripCenter, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 2.0f);
    }

    @Override
    protected void doComplete() {
        for (BlockDisplay b : stripBlocks) { if (b != null && !b.isDead()) b.remove(); }
        for (TextDisplay  a : arrows)      { if (a != null && !a.isDead()) a.remove(); }
        for (TextDisplay  l : labels)      { if (l != null && !l.isDead()) l.remove(); }
        for (ItemDisplay  i : enchantItems){ if (i != null && !i.isDead()) i.remove(); }
        if (!itemReturned && player.isOnline() && originalItem != null) {
            player.getInventory().setItemInMainHand(originalItem);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Scroll  (ticks 0 → scrollEndTick)
    // -------------------------------------------------------------------------

    private void tickScroll(World world, int tick) {
        double scrollProgress = Math.min(1.0, (double) tick / scrollEndTick);
        double scroll = TOTAL_SCROLL * easeInOutQuart(scrollProgress);

        for (int i = 0; i < STRIP_SIZE; i++) {
            BlockDisplay block = stripBlocks[i];
            if (block == null || block.isDead()) continue;
            double rightAxis = i * SPACING + INITIAL_OFFSET - scroll;
            if (Math.abs(rightAxis) > 4.2) {
                block.teleport(stripCenter.clone().add(0, -20, 0));
            } else {
                block.teleport(stripCenter.clone().add(rightDir.clone().multiply(rightAxis)));
            }
        }

        if (tick < scrollEndTick && tick % 2 == 0) {
            float pitch = (float) (2.0 - 1.5 * scrollProgress);
            world.playSound(stripCenter, Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, pitch);
        }

        if (tick == scrollEndTick) {
            world.playSound(stripCenter, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
            world.playSound(stripCenter, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);

            // Cache result positions (block entity origin = visual block centre)
            for (int r = 0; r < RESULT_INDICES.length; r++) {
                BlockDisplay block = stripBlocks[RESULT_INDICES[r]];
                if (block != null && !block.isDead()) {
                    resultPos[r] = block.getLocation().clone();
                }
            }

            triggerScatter(world);
            spawnRarityLabels(world);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2 — Scatter  (ticks scrollEndTick+1 → scrollEndTick+SCATTER_DURATION)
    // -------------------------------------------------------------------------

    /** Assigns outward velocities to every visible, non-result block and removes hidden ones. */
    private void triggerScatter(World world) {
        for (int i = 0; i < STRIP_SIZE; i++) {
            if (isResultIndex(i)) continue;
            BlockDisplay block = stripBlocks[i];
            if (block == null || block.isDead()) continue;

            double rightAxis = i * SPACING + INITIAL_OFFSET - TOTAL_SCROLL;
            if (Math.abs(rightAxis) > 4.2) {
                block.remove();
                stripBlocks[i] = null;
                continue;
            }

            double angle  = RAND.nextDouble() * 2 * Math.PI;
            double speed  = 0.14 + RAND.nextDouble() * 0.22;
            double upward = 0.06 + RAND.nextDouble() * 0.10;
            scatterVels[i] = new Vector(
                Math.cos(angle) * speed,
                upward,
                Math.sin(angle) * speed
            );
        }
        scatterActive = true;
    }

    /** Moves scattered blocks outward each tick and removes them at the end. */
    private void tickScatter(World world, int tick) {
        if (!scatterActive) return;
        int st = tick - scrollEndTick;
        if (st <= 0 || st > SCATTER_DURATION) return;

        float shrinkT = (float) st / SCATTER_DURATION;

        for (int i = 0; i < STRIP_SIZE; i++) {
            if (isResultIndex(i)) continue;
            BlockDisplay block = stripBlocks[i];
            if (block == null || block.isDead()) continue;
            if (scatterVels[i] == null) continue;

            scatterVels[i].setY(scatterVels[i].getY() - 0.010);
            Location newLoc = block.getLocation().clone().add(scatterVels[i]);
            block.teleport(newLoc);

            float scale = Math.max(BLOCK_SCALE * (1f - shrinkT), 0.001f);
            block.setTransformation(centeredTransform(scale));

            if (st == SCATTER_DURATION) {
                block.remove();
                stripBlocks[i] = null;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Reveal  (ticks revealStartTick → selectionReady)
    // Each slot: concrete block shrinks, enchant item rises, name types out
    // -------------------------------------------------------------------------

    private void tickReveal(World world, int tick) {
        // Remove arrows just before the first reveal so they don't overlap the labels
        if (!arrowsRemoved && tick >= revealStartTick - 3) {
            arrowsRemoved = true;
            for (TextDisplay arrow : arrows) {
                if (arrow != null && !arrow.isDead()) arrow.remove();
            }
        }

        for (int r = 0; r < 3; r++) {
            int slotStart = revealStartTick + r * REVEAL_STAGGER;
            if (tick < slotStart) continue;

            int revealTick = tick - slotStart;

            if (!revealStarted[r]) {
                revealStarted[r] = true;
                spawnEnchantItem(world, r);
                float pitch = 0.8f + r * 0.15f;
                world.playSound(stripCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, pitch);
            }

            if (revealTick >= REVEAL_DURATION) {
                if (!revealDone[r]) {
                    revealDone[r] = true;
                    BlockDisplay rb = stripBlocks[RESULT_INDICES[r]];
                    if (rb != null && !rb.isDead()) rb.setTransformation(centeredTransform(0.001f));
                    if (enchantItems[r] != null && !enchantItems[r].isDead()) {
                        enchantItems[r].setTransformation(itemTransform(ITEM_SCALE, 0f));
                    }
                }
                tickTypewriter(world, r, revealTick - REVEAL_DURATION);
                continue;
            }

            float t = (float) revealTick / REVEAL_DURATION;

            float blockScale = Math.max(BLOCK_SCALE * (1f - t), 0.001f);
            BlockDisplay rb = stripBlocks[RESULT_INDICES[r]];
            if (rb != null && !rb.isDead()) rb.setTransformation(centeredTransform(blockScale));

            float itemScale = Math.max(ITEM_SCALE * (float) easeOutCubic(t), 0.001f);
            float yOffset   = -0.25f * (1f - t);
            if (enchantItems[r] != null && !enchantItems[r].isDead()) {
                enchantItems[r].setTransformation(itemTransform(itemScale, yOffset));
            }
        }
    }

    private void tickTypewriter(World world, int r, int writeTick) {
        if (labels[r] == null || labels[r].isDead()) return;

        String enchantName = FontUtils.toSmallCaps(enchantOptions.get(r).name());
        int    chars       = Math.min(writeTick / 2 + 1, enchantName.length());
        boolean done       = chars >= enchantName.length();

        EnchantOption opt = enchantOptions.get(r);
        labels[r].text(
            Component.text(FontUtils.toSmallCaps(opt.rarity().displayName()))
                .color(opt.rarity().textColor())
                .decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text(enchantName.substring(0, chars) + (done ? "" : "\u258c"))
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, false))
        );

        if (writeTick % 2 == 0 && !done) {
            float pitch = 1.4f + (chars / (float) enchantName.length()) * 0.6f;
            world.playSound(stripCenter, Sound.BLOCK_NOTE_BLOCK_PLING, 0.2f, pitch);
        }
        if (done && writeTick == (enchantName.length() - 1) * 2) {
            world.playSound(stripCenter, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f);
        }
    }

    // -------------------------------------------------------------------------
    // Idle particles while waiting for selection
    // -------------------------------------------------------------------------

    private void tickIdleParticles(World world, int tick) {
        if (tick <= scrollEndTick) return;

        for (int r = 0; r < 3; r++) {
            Location pos = getOptionPosition(r);
            if (pos == null) continue;
            EnchantOption opt = enchantOptions.get(r);

            if (tick % 2 == 0) {
                double angle = tick * 0.18 + r * 2.09;
                for (int p = 0; p < 2; p++) {
                    double a = angle + p * Math.PI;
                    world.spawnParticle(Particle.DUST,
                        pos.clone().add(Math.cos(a) * 0.38, Math.sin(a * 0.7) * 0.15, Math.sin(a) * 0.38),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(opt.rarity().particleColor(), 1.0f));
                }
            }
            if (highlightedIndex == r && tick % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, pos, 2, 0.12, 0.12, 0.12, 0.02);
            }
        }

        if (tick == selectionReady) {
            player.sendActionBar(
                Component.text(FontUtils.toSmallCaps("Look at an enchant and ")).color(NamedTextColor.YELLOW)
                    .append(Component.text(FontUtils.toSmallCaps("LEFT-CLICK"))
                        .color(NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD))
                    .append(Component.text(FontUtils.toSmallCaps(" to claim it!")).color(NamedTextColor.YELLOW)));
        }
    }

    // -------------------------------------------------------------------------
    // Phase 4 — Selection animation
    //
    // sp 0:     sounds; remove selected label; selected item begins flying to player
    // sp 2–9:   non-selected[0] sinks downward (8 ticks)
    // sp 10–17: non-selected[1] sinks downward (8 ticks)
    // sp 0–25:  selected item lerps toward player (easeInCubic acceleration)
    // sp 25:    item removed at player position; particle burst; action bar appears
    // sp 25–55: rarity-coloured particles orbit player; action bar refreshed
    // sp 55:    return item + fire callback + schedule stop
    // -------------------------------------------------------------------------

    private void tickSelectionAnim(World world, Location playerLoc, int tick) {
        int sp = tick - selectionTick;
        EnchantOption selected = enchantOptions.get(selectedIndex);

        // --- Remove non-selected items one by one (staggered 8-tick sink) ---
        int[] nonSel = getNonSelectedIndices();
        for (int ni = 0; ni < nonSel.length; ni++) {
            int r     = nonSel[ni];
            int start = 2 + ni * 8;
            if (sp < start || nonSelRemoved[r]) continue;

            int fp = sp - start; // 0 → 8

            ItemDisplay item = enchantItems[r];
            if (item != null && !item.isDead() && fp <= 8) {
                if (resultPos[r] != null) {
                    item.teleport(resultPos[r].clone().add(0, -fp * 0.20, 0));
                }
                float scale = ITEM_SCALE * Math.max(0f, 1f - fp / 8f);
                item.setTransformation(itemTransform(Math.max(scale, 0.001f), 0f));
            }

            if (fp == 8) {
                if (item != null && !item.isDead()) { item.remove(); enchantItems[r] = null; }
                if (labels[r] != null && !labels[r].isDead()) { labels[r].remove(); labels[r] = null; }
                nonSelRemoved[r] = true;

                if (resultPos[r] != null) {
                    world.playSound(resultPos[r], Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.8f);
                }
            }
        }

        // --- Selected item: fly toward player (sp 0–25) ---
        ItemDisplay selItem = enchantItems[selectedIndex];
        if (sp == 0) {
            // Remove the rarity label immediately so it doesn't linger as the item flies away
            if (labels[selectedIndex] != null && !labels[selectedIndex].isDead()) {
                labels[selectedIndex].remove();
                labels[selectedIndex] = null;
            }
            world.playSound(stripCenter, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.8f);
            world.playSound(stripCenter, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.4f, 2.0f);
        }

        if (selItem != null && !selItem.isDead() && sp <= 25) {
            double t = easeInCubic((double) sp / 25.0);
            Location origin = resultPos[selectedIndex];
            Location target = player.getEyeLocation();

            if (origin != null) {
                selItem.teleport(lerpLocation(origin, target, t));
            }
            selItem.setTransformation(itemTransform(ITEM_SCALE, 0f));
        }

        // --- Item reaches player at sp=25: collect burst + return pickaxe ---
        if (sp == 25) {
            if (selItem != null && !selItem.isDead()) { selItem.remove(); enchantItems[selectedIndex] = null; }

            // Give the pickaxe back immediately so the player can keep mining
            if (!itemReturned && player.isOnline()) {
                itemReturned = true;
                player.getInventory().setItemInMainHand(originalItem);
            }

            world.playSound(playerLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
            world.playSound(playerLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);

            // Dense burst of rarity-coloured dust particles
            for (int p = 0; p < 40; p++) {
                world.spawnParticle(Particle.DUST,
                    playerLoc.clone().add(
                        (RAND.nextDouble() - 0.5) * 1.8,
                        RAND.nextDouble() * 2.2,
                        (RAND.nextDouble() - 0.5) * 1.8
                    ),
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(selected.rarity().particleColor(), 1.8f));
            }
            // Enchantment table glyphs flying outward
            world.spawnParticle(Particle.ENCHANT,
                playerLoc.clone().add(0, 1, 0), 60, 0.6, 0.6, 0.6, 1.2);
        }

        // --- Sustained particles + action bar (sp 25–55) ---
        if (sp >= 25 && sp <= 55) {
            // Refresh action bar every 10 ticks so it doesn't fade
            if ((sp - 25) % 10 == 0) {
                if (sp < 35) {
                    // Phase A — announce unlock
                    player.sendActionBar(
                        Component.text(FontUtils.toSmallCaps("\u2726 ENCHANT UNLOCKED \u2726"))
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD));
                } else {
                    // Phase B — show enchant details
                    player.sendActionBar(
                        Component.text("\u2726 ")
                            .color(selected.rarity().textColor())
                            .append(Component.text(FontUtils.toSmallCaps(selected.name()))
                                .color(NamedTextColor.WHITE)
                                .decorate(TextDecoration.BOLD))
                            .append(Component.text("  \u00B7  ")
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.BOLD, false))
                            .append(Component.text(FontUtils.toSmallCaps(selected.rarity().displayName()))
                                .color(selected.rarity().textColor())
                                .decoration(TextDecoration.BOLD, false))
                            .append(Component.text(" \u2726")
                                .color(selected.rarity().textColor())));
                }
            }

            // Orbiting rarity-coloured particles around the player
            if (sp % 2 == 0) {
                double angle = (sp - 25) * 0.35;
                for (int p = 0; p < 3; p++) {
                    double a = angle + p * (2 * Math.PI / 3);
                    world.spawnParticle(Particle.DUST,
                        playerLoc.clone().add(
                            Math.cos(a) * 1.1,
                            0.4 + Math.sin(a * 0.7) * 0.4,
                            Math.sin(a) * 1.1
                        ),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(selected.rarity().particleColor(), 1.3f));
                }
            }

            // Occasional END_ROD sparkles rising from the player
            if (sp % 4 == 0) {
                world.spawnParticle(Particle.END_ROD,
                    playerLoc.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
            }
        }

        // --- Fire callback and schedule stop at sp=55 ---
        if (sp == 55) {
            fireCallback();
            new BukkitRunnable() {
                @Override public void run() { stop(); }
            }.runTaskLater(plugin, 8L);
        }
    }

    // -------------------------------------------------------------------------
    // Spawn helpers
    // -------------------------------------------------------------------------

    /** Spawns the enchant ItemDisplay for slot {@code r} at its cached result position. */
    private void spawnEnchantItem(World world, int r) {
        if (resultPos[r] == null) return;
        ItemStack enchantItem = EnchantAnimationConfig.createEnchantItem(enchantOptions.get(r).name());
        final int cr = r;

        enchantItems[cr] = world.spawn(resultPos[r].clone(), ItemDisplay.class, e -> {
            e.setItemStack(enchantItem);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            e.setTransformation(itemTransform(0.001f, -0.25f));
        });
    }

    /** Spawns rarity-only TextDisplay labels above the three result blocks. */
    private void spawnRarityLabels(World world) {
        if (labelsSpawned) return;
        labelsSpawned = true;

        for (int r = 0; r < RESULT_INDICES.length; r++) {
            if (resultPos[r] == null) continue;
            EnchantOption opt = enchantOptions.get(r);
            Location labelLoc = resultPos[r].clone().add(0, 0.58, 0);
            final int cr = r;

            labels[cr] = world.spawn(labelLoc, TextDisplay.class, e -> {
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setBackgroundColor(Color.fromARGB(210, 10, 10, 20));
                e.text(Component.text(FontUtils.toSmallCaps(opt.rarity().displayName()))
                    .color(opt.rarity().textColor())
                    .decorate(TextDecoration.BOLD));
                e.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.52f, 0.52f, 0.52f),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
            });
        }
    }

    /** Returns the item to the player's hand, fires the callback, and schedules stop. */
    private void returnItemToPlayer(World world, Location playerLoc) {
        if (itemReturned) return;
        itemReturned = true;

        world.playSound(playerLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
        player.getInventory().setItemInMainHand(originalItem);
        fireCallback();

        new BukkitRunnable() {
            @Override
            public void run() { stop(); }
        }.runTaskLater(plugin, 8L);
    }

    // -------------------------------------------------------------------------
    // Material helpers
    // -------------------------------------------------------------------------

    private Material materialForSlot(int index) {
        for (int r = 0; r < RESULT_INDICES.length; r++) {
            if (index == RESULT_INDICES[r]) {
                return enchantOptions.get(r).rarity().concreteMaterial();
            }
        }
        return fillerMaterials[index] != null ? fillerMaterials[index] : Material.WHITE_CONCRETE;
    }

    // -------------------------------------------------------------------------
    // Transformation factories
    // -------------------------------------------------------------------------

    /**
     * Centers a BlockDisplay cube of {@code scale} at the entity's world position.
     * BlockDisplays render with the block corner at the entity origin by default;
     * translating by {@code -scale/2} on all axes shifts the visual centre to the
     * entity position, which aligns with the arrow indicators at the same coordinates.
     */
    private static Transformation centeredTransform(float scale) {
        float half = scale / 2f;
        return new Transformation(
            new Vector3f(-half, -half, -half),
            new AxisAngle4f(0, 0, 1, 0),
            new Vector3f(scale, scale, scale),
            new AxisAngle4f(0, 0, 1, 0)
        );
    }

    /**
     * ItemDisplay Transformation with a vertical offset and uniform scale.
     * Items are natively centred at the entity origin — no half-offset needed.
     */
    private static Transformation itemTransform(float scale, float yOffset) {
        return new Transformation(
            new Vector3f(0, yOffset, 0),
            new AxisAngle4f(0, 0, 1, 0),
            new Vector3f(scale, scale, scale),
            new AxisAngle4f(0, 0, 1, 0)
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isResultIndex(int i) {
        for (int r : RESULT_INDICES) if (r == i) return true;
        return false;
    }

    private int[] getNonSelectedIndices() {
        int[] result = new int[2];
        int idx = 0;
        for (int r = 0; r < 3 && idx < 2; r++) {
            if (r != selectedIndex) result[idx++] = r;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Easing
    // -------------------------------------------------------------------------

    /** Fast launch, hard brake — exactly like a mechanical reel. */
    private static double easeInOutQuart(double x) {
        return x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
    }
}
