package org.benski.evolv3.animations.pickup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BasePickupAnimation;
import org.benski.evolv3.core.types.PreferenceType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collection;

/**
 * Dissolve pickup animation — three phases:
 *
 * <ol>
 *   <li><b>Fall</b> (ticks 0–11): a real Item entity is dropped so Minecraft handles
 *       the physics naturally. The player cannot pick it up.</li>
 *   <li><b>Float</b> (ticks 12–44): the item entity is swapped for an ItemDisplay that
 *       spins and rises. ESSENCE fires enchant sparks + purple dust. ATOM fires gold
 *       dust + END_ROD sparks. NORMAL trails light smoke.</li>
 *   <li><b>Dissolve</b> (ticks 45–60): the glow is removed, the ItemDisplay spins faster
 *       and shrinks to nothing while a ring burst fires, then particles spiral to the player.</li>
 * </ol>
 */
public class DissolvePickupAnimation extends BasePickupAnimation
{
    private static final int DURATION       = 60;
    private static final int FALL_TICKS     = 12;
    private static final int FLOAT_TICKS    = 33;
    private static final int DISSOLVE_TICKS = DURATION - FALL_TICKS - FLOAT_TICKS; // 15

    /**
     * Ticks after animation start when the dissolve phase begins.
     * BreakListener uses this to delay secondary animations (essence/atom) so they fire
     * at the correct visual moment rather than immediately when the block breaks.
     */
    public static final int SECONDARY_ANIM_DELAY = FALL_TICKS + FLOAT_TICKS; // 45

    private static final float  ITEM_SCALE   = 0.34f;
    private static final float  TEXT_SCALE   = 0.40f;
    public  static final double FLOAT_HEIGHT = 4.0;
    private static final double SPIN_SPEED   = 0.22; // radians per tick

    private static final String ESSENCE_TEAM = "evo_essence";
    private static final String ATOM_TEAM    = "evo_atom";

    private static final Color ESSENCE_COLOR = Color.fromRGB(160, 50, 230);
    private static final Color ATOM_COLOR    = Color.fromRGB(255, 238, 0);

    @Nullable private org.bukkit.entity.Item droppedItem; // real item during fall phase
    @Nullable private ItemDisplay            itemDisplay;
    @Nullable private TextDisplay            textDisplay;
    @Nullable private Location               floatOrigin; // where float starts (item landed)
    @Nullable private Team                   glowTeam;

    private boolean transitioned = false;
    private boolean glowRemoved  = false;
    private double  spinAngle    = 0;

    public DissolvePickupAnimation(JavaPlugin plugin) {
        super(plugin, DURATION);
    }

    @Override public String getAnimationId() { return "dissolve"; }
    @Override public String getDisplayName() { return "Dissolve"; }
    @Override public String getDescription() { return "Block drops, floats up spinning, then dissolves"; }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null || targetPlayer == null) return;

        if (style == PreferenceType.PickupAnimation.TEXT_ONLY) return;

        ItemStack displayItem = firstItem();
        if (displayItem == null) return;

        Collection<Player> viewers = getViewers();

        // Drop a real item entity so Minecraft's physics handles the fall naturally
        droppedItem = origin.getWorld().dropItem(origin, displayItem);
        droppedItem.setVelocity(new Vector(0, 0, 0)); // fall straight down, no random toss
        droppedItem.setPickupDelay(Integer.MAX_VALUE); // nobody can ever pick it up
        droppedItem.setPersistent(false);
        droppedItem.setInvulnerable(true);
        if (viewers != null) {
            droppedItem.setVisibleByDefault(false);
            for (Player v : viewers) v.showEntity(plugin, droppedItem);
        }
    }

    @Override
    protected void onTick(double progress) {
        if (style == PreferenceType.PickupAnimation.TEXT_ONLY) {
            tickTextOnly(progress);
            return;
        }

        int tick = getCurrentTick();

        // During fall phase the real item handles itself — nothing to do
        if (tick < FALL_TICKS) return;

        // One-time swap: remove real item, spawn ItemDisplay at its landed position
        if (!transitioned) {
            transitioned = true;
            doTransition();
        }

        int localTick = tick - FALL_TICKS;
        if (localTick < FLOAT_TICKS) {
            tickFloat(localTick);
        } else {
            tickDissolve(localTick - FLOAT_TICKS);
        }
    }

    @Override
    protected void onComplete() {
        if (!glowRemoved && itemDisplay != null && glowTeam != null) {
            glowTeam.removeEntry(itemDisplay.getUniqueId().toString());
        }
        removeEntity(droppedItem);
        removeEntity(itemDisplay);
        removeEntity(textDisplay);
        droppedItem = null;
        itemDisplay = null;
        textDisplay = null;
        glowTeam    = null;
    }

    @Override
    public int getResourceCost() { return 4; }

    // =========================================================================
    // Transition: real item → ItemDisplay
    // =========================================================================

    private void doTransition() {
        if (droppedItem != null && !droppedItem.isDead()) {
            floatOrigin = droppedItem.getLocation().clone();
            droppedItem.remove();
            droppedItem = null;
        } else {
            // Fallback if item was already removed externally
            Location origin = getOrigin();
            floatOrigin = origin != null ? origin.clone().subtract(0, 0.5, 0) : null;
        }

        if (floatOrigin == null || floatOrigin.getWorld() == null) return;

        World world = floatOrigin.getWorld();
        Collection<Player> viewers = getViewers();
        ItemStack displayItem = firstItem();
        if (displayItem == null) return;

        itemDisplay = world.spawn(floatOrigin, ItemDisplay.class, e -> {
            e.setItemStack(displayItem);
            e.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            e.setPersistent(false);
            e.setBillboard(ItemDisplay.Billboard.FIXED); // FIXED so our Y-axis spin is applied
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(2);
            applyViewers(e, viewers);
        });

        // Coloured outline glow for ESSENCE / ATOM
        if (prefGlow && dropCategory != PreferenceType.DropCategory.NORMAL && itemDisplay != null) {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            String teamName = dropCategory == PreferenceType.DropCategory.ESSENCE ? ESSENCE_TEAM : ATOM_TEAM;
            glowTeam = board.getTeam(teamName);
            if (glowTeam == null) {
                glowTeam = board.registerNewTeam(teamName);
                glowTeam.color(dropCategory == PreferenceType.DropCategory.ESSENCE
                        ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.YELLOW);
            }
            glowTeam.addEntry(itemDisplay.getUniqueId().toString());
            itemDisplay.setGlowing(true);
        }

        int xp = totalXp();
        textDisplay = world.spawn(floatOrigin.clone().add(0, 0.65, 0), TextDisplay.class, e -> {
            e.text(Component.text("+" + xp + " XP")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD));
            e.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            e.setPersistent(false);
            e.setBillboard(TextDisplay.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            applyViewers(e, viewers);
        });
    }

    // =========================================================================
    // Float phase — spins upward, particles shoot out for ESSENCE/ATOM
    // =========================================================================

    private void tickFloat(int localTick) {
        if (floatOrigin == null || itemDisplay == null || itemDisplay.isDead()) return;
        World world = floatOrigin.getWorld();
        if (world == null) return;

        double t   = (double) localTick / FLOAT_TICKS;
        double y   = easeOutCubic(t) * FLOAT_HEIGHT;
        Location pos = floatOrigin.clone().add(0, y, 0);

        spinAngle += SPIN_SPEED;

        itemDisplay.teleport(pos);
        itemDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) spinAngle, 0, 1, 0),
                new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                new AxisAngle4f(0, 0, 1, 0)
        ));

        if (textDisplay != null && !textDisplay.isDead()) {
            textDisplay.teleport(pos.clone().add(0, 0.65, 0));
        }

        // Particles that shoot out of the block as it floats upward
        switch (dropCategory) {
            case ESSENCE -> {
                if (prefParticles) {
                    // Enchant sparks burst outward every 2 ticks — essence leaking out
                    if (localTick % 2 == 0) {
                        spawnParticle(world, Particle.ENCHANT, pos, 4, 0.22, 0.08, 0.22, 0.18, null);
                    }
                    // Two opposing purple dust particles orbit outward continuously
                    Particle.DustOptions dust = new Particle.DustOptions(ESSENCE_COLOR, 1.0f);
                    double a = spinAngle * 1.3;
                    spawnParticle(world, Particle.DUST, pos.clone().add(0.30 * Math.cos(a), 0, 0.30 * Math.sin(a)), 1, 0, 0, 0, 0, dust);
                    spawnParticle(world, Particle.DUST, pos.clone().add(0.30 * Math.cos(a + Math.PI), 0, 0.30 * Math.sin(a + Math.PI)), 1, 0, 0, 0, 0, dust);
                }
            }
            case ATOM -> {
                if (prefParticles) {
                    // END_ROD sparks every 2 ticks — atomic energy releasing
                    if (localTick % 2 == 0) {
                        spawnParticle(world, Particle.END_ROD, pos, 2, 0.18, 0.08, 0.18, 0.04, null);
                    }
                    // Gold dust orbiting outward
                    Particle.DustOptions dust = new Particle.DustOptions(ATOM_COLOR, 1.0f);
                    double a = spinAngle * 1.5;
                    spawnParticle(world, Particle.DUST, pos.clone().add(0.30 * Math.cos(a), 0, 0.30 * Math.sin(a)), 1, 0, 0, 0, 0, dust);
                    spawnParticle(world, Particle.DUST, pos.clone().add(0.30 * Math.cos(a + Math.PI), 0, 0.30 * Math.sin(a + Math.PI)), 1, 0, 0, 0, 0, dust);
                }
            }
            default -> {
                // Subtle smoke trail every 4 ticks
                if (localTick % 4 == 0) {
                    spawnParticle(world, Particle.SMOKE, pos, 1, 0.05, 0.02, 0.05, 0.01, null);
                }
            }
        }
    }

    // =========================================================================
    // Dissolve phase — glow removed, spins faster and shrinks, then spirals
    // =========================================================================

    private void tickDissolve(int localTick) {
        if (itemDisplay == null || itemDisplay.isDead() || floatOrigin == null) return;
        World world = floatOrigin.getWorld();
        if (world == null) return;

        double t      = (double) localTick / DISSOLVE_TICKS;
        Location top  = floatOrigin.clone().add(0, FLOAT_HEIGHT, 0);

        // On the very first dissolve tick: strip glow and fire the burst ring
        if (localTick == 0) {
            removeGlow();
            fireDissolveBurst(top, world);
        }

        // Spin faster and shrink to nothing
        spinAngle += SPIN_SPEED * 2.0;
        float scale = ITEM_SCALE * (float) (1.0 - t);
        itemDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) spinAngle, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));

        // Fade text simultaneously
        float ts = TEXT_SCALE * (float) (1.0 - t);
        if (textDisplay != null && !textDisplay.isDead()) {
            textDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(ts, ts, ts),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
        }

        // Dissolve dust
        if (dropCategory != PreferenceType.DropCategory.NORMAL) {
            Particle.DustOptions dust = new Particle.DustOptions(categoryColor(), 0.8f);
            spawnParticle(world, Particle.DUST, top, 2, 0.12, 0.12, 0.12, 0, dust);
        }

        // Spiral to player in the second half of dissolve
        if (t > 0.4 && style != PreferenceType.PickupAnimation.SIMPLE) {
            double spiralT = (t - 0.4) / 0.6;
            Particle.DustOptions dust = new Particle.DustOptions(categoryColor(), 0.6f);
            spawnSpiralParticle(world, top, spiralT, dust);
        }
    }

    private void fireDissolveBurst(Location pos, World world) {
        Color effectColor = categoryColor();
        Particle.DustOptions dust = new Particle.DustOptions(effectColor, 1.2f);

        // Ring of dust
        for (int i = 0; i < 12; i++) {
            double angle = (2 * Math.PI / 12) * i;
            Location p = pos.clone().add(0.38 * Math.cos(angle), 0, 0.38 * Math.sin(angle));
            spawnParticle(world, Particle.DUST, p, 1, 0, 0.02, 0, 0, dust);
        }

        switch (dropCategory) {
            case ESSENCE -> {
                spawnParticle(world, Particle.ENCHANT, pos.clone().add(0, 0.1, 0), 18, 0.30, 0.20, 0.30, 0.12, null);
                world.playSound(pos, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.6f);
                world.playSound(pos, Sound.BLOCK_GLASS_BREAK, 0.18f, 2.0f);
            }
            case ATOM -> {
                spawnParticle(world, Particle.END_ROD, pos.clone().add(0, 0.1, 0), 10, 0.28, 0.18, 0.28, 0.08, null);
                world.playSound(pos, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.5f, 2.0f);
                world.playSound(pos, Sound.BLOCK_GLASS_BREAK, 0.18f, 2.0f);
            }
            default -> {
                spawnParticle(world, Particle.SMOKE, pos.clone().add(0, 0.1, 0), 5, 0.08, 0.04, 0.08, 0.01, null);
                world.playSound(pos, Sound.BLOCK_GLASS_BREAK, 0.22f, 1.9f);
            }
        }
    }

    // =========================================================================
    // TEXT_ONLY mode
    // =========================================================================

    private void tickTextOnly(double progress) {
        Location origin = getOrigin();
        if (origin == null) return;

        if (textDisplay == null) {
            World world = origin.getWorld();
            if (world == null) return;
            int xp = totalXp();
            Collection<Player> viewers = getViewers();
            textDisplay = world.spawn(origin, TextDisplay.class, e -> {
                e.text(Component.text("+" + xp + " XP").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                e.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE), new AxisAngle4f(0, 0, 1, 0)));
                e.setPersistent(false);
                e.setBillboard(TextDisplay.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                applyViewers(e, viewers);
            });
        }
        if (textDisplay == null || textDisplay.isDead()) return;

        float scale = TEXT_SCALE * (float) (1.0 - progress);
        textDisplay.teleport(origin.clone().add(0, progress * 1.2, 0));
        textDisplay.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale), new AxisAngle4f(0, 0, 1, 0)));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void removeGlow() {
        if (glowRemoved) return;
        glowRemoved = true;
        if (itemDisplay != null && !itemDisplay.isDead()) itemDisplay.setGlowing(false);
        if (glowTeam != null && itemDisplay != null) glowTeam.removeEntry(itemDisplay.getUniqueId().toString());
    }

    private void spawnSpiralParticle(World world, Location from, double t, Particle.DustOptions dust) {
        if (targetPlayer == null || !targetPlayer.isOnline()) return;
        Location target = targetPlayer.getLocation().add(0, 1.0, 0);
        Location core   = lerpLoc(from, target, t);
        double radius   = 0.22 * (1.0 - t);
        double angle    = t * Math.PI * 5;
        spawnParticle(world, Particle.DUST,
                core.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle)),
                1, 0, 0, 0, 0, dust);
    }

    private Color categoryColor() {
        return switch (dropCategory) {
            case ESSENCE -> ESSENCE_COLOR;
            case ATOM    -> ATOM_COLOR;
            case NORMAL  -> Color.fromRGB(70, 70, 70);
        };
    }

    @Nullable
    private ItemStack firstItem() {
        if (items == null) return null;
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) return it;
        return null;
    }

    private int totalXp() {
        if (items == null) return 0;
        int sum = 0;
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) sum += it.getAmount();
        return sum;
    }

    private <E extends Entity> void applyViewers(E entity, @Nullable Collection<Player> viewers) {
        if (viewers == null) return;
        entity.setVisibleByDefault(false);
        for (Player viewer : viewers) viewer.showEntity(plugin, entity);
    }

    private <D> void spawnParticle(World world, Particle particle, Location loc,
                                   int count, double ox, double oy, double oz, double speed, @Nullable D data) {
        Collection<Player> viewers = getViewers();
        if (viewers == null) {
            world.spawnParticle(particle, loc, count, ox, oy, oz, speed, data);
        } else {
            for (Player viewer : viewers) {
                viewer.spawnParticle(particle, loc, count, ox, oy, oz, speed, data);
            }
        }
    }

    private Location lerpLoc(Location a, Location b, double t) {
        return new Location(a.getWorld(),
                lerp(a.getX(), b.getX(), t),
                lerp(a.getY(), b.getY(), t),
                lerp(a.getZ(), b.getZ(), t));
    }

    private static void removeEntity(@Nullable Entity e) {
        if (e != null && !e.isDead()) e.remove();
    }
}
