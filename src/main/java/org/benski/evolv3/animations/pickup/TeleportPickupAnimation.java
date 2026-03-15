package org.benski.evolv3.animations.pickup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BasePickupAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Teleport pickup animation.
 * Supports three visual styles controlled by {@link PreferenceType.PickupAnimation}:
 * <ul>
 *   <li>NORMAL – item display with spin + XP text, full duration.</li>
 *   <li>SIMPLE – item display without rotation, faster linear fade.</li>
 *   <li>TEXT_ONLY – XP text hologram only, no item display.</li>
 * </ul>
 */
public class TeleportPickupAnimation extends BasePickupAnimation
{
    private static final int DEFAULT_DURATION = 15;
    /** Effective speed multiplier for SIMPLE mode — finishes ~1.5× faster. */
    private static final double SIMPLE_SPEED_MULT = 1.5;

    private static final float  ITEM_SCALE    = 0.45f;
    private static final float  TEXT_SCALE    = 0.5f;
    private static final float  TEXT_ONLY_SCALE = 0.65f;
    private static final double TEXT_OFFSET_Y = 0.4;

    private static final String ESSENCE_TEAM = "evo_essence";
    private static final String ATOM_TEAM    = "evo_atom";

    private final List<TeleportItem> teleportItems = new ArrayList<>();
    @Nullable private Team glowTeam;

    public TeleportPickupAnimation(JavaPlugin plugin) {
        super(plugin, DEFAULT_DURATION);
    }

    /** {@inheritDoc} */
    @Override
    public String getAnimationId() {
        return "teleport";
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
        return "Teleport";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Items phase out and teleport directly to you";
    }

    /** {@inheritDoc} */
    @Override
    protected void onStart() {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null || targetPlayer == null) return;

        World world = origin.getWorld();
        Collection<Player> viewers = getViewers();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            Location spawnLoc = origin.clone();
            final int xpAmount;
            if (blockMaterial != null) {
                String xpPath = ConfigPaths.Default.BLOCK_DEFAULTS + "." + blockMaterial.name() + ".XP";
                xpAmount = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT).getInt(xpPath, 0);
            } else {
                xpAmount = 0;
            }

            // Item display — not spawned in TEXT_ONLY mode
            @Nullable ItemDisplay display = null;
            if (style != PreferenceType.PickupAnimation.TEXT_ONLY) {
                display = world.spawn(spawnLoc, ItemDisplay.class, entity -> {
                    entity.setItemStack(item);
                    entity.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                        new AxisAngle4f(0, 0, 1, 0)
                    ));
                    entity.setPersistent(false);
                    entity.setBillboard(ItemDisplay.Billboard.CENTER);
                    applyViewers(entity, viewers, plugin);
                });

                // Coloured glow for ESSENCE / ATOM drops
                if (prefGlow && dropCategory != PreferenceType.DropCategory.NORMAL && display != null) {
                    Scoreboard board = targetPlayer != null
                            ? targetPlayer.getScoreboard()
                            : Bukkit.getScoreboardManager().getMainScoreboard();
                    NamedTextColor glowColor = dropCategory == PreferenceType.DropCategory.ESSENCE
                            ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.YELLOW;
                    String teamName = dropCategory == PreferenceType.DropCategory.ESSENCE ? ESSENCE_TEAM : ATOM_TEAM;
                    glowTeam = board.getTeam(teamName);
                    if (glowTeam == null) {
                        glowTeam = board.registerNewTeam(teamName);
                    }
                    glowTeam.color(glowColor);
                    glowTeam.addEntry(display.getUniqueId().toString());
                    display.setGlowing(true);
                }
            }

            // Text display — always spawned
            float textScale = style == PreferenceType.PickupAnimation.TEXT_ONLY ? TEXT_ONLY_SCALE : TEXT_SCALE;
            Location textLoc = spawnLoc.clone().add(0, TEXT_OFFSET_Y, 0);
            TextDisplay textDisplay = world.spawn(textLoc, TextDisplay.class, entity -> {
                entity.text(Component.text("+" + xpAmount + " XP")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD));
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(textScale, textScale, textScale),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                entity.setPersistent(false);
                entity.setBillboard(TextDisplay.Billboard.CENTER);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setShadowed(true);
                applyViewers(entity, viewers, plugin);
            });

            teleportItems.add(new TeleportItem(display, textDisplay, spawnLoc.clone(), item));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onTick(double progress) {
        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null) return;

        // SIMPLE mode finishes faster by scaling progress up
        double effectiveProgress = style == PreferenceType.PickupAnimation.SIMPLE
                ? Math.min(progress * SIMPLE_SPEED_MULT, 1.0) : progress;
        double easedProgress = easeInOutCubic(effectiveProgress);

        for (TeleportItem tItem : teleportItems) {
            // Text display
            if (!tItem.textDisplay.isDead()) {
                float ts = (style == PreferenceType.PickupAnimation.TEXT_ONLY ? TEXT_ONLY_SCALE : TEXT_SCALE)
                           * (float) (1 - easedProgress);
                double riseY = style == PreferenceType.PickupAnimation.TEXT_ONLY ? 0.9 : 0.6;
                Location floatBase = tItem.startPosition.clone().add(0, easedProgress * riseY, 0);
                Location textPos = floatBase.clone().add(0, TEXT_OFFSET_Y * (1 - easedProgress * 0.3), 0);
                tItem.textDisplay.teleport(textPos);
                tItem.textDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(ts, ts, ts),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
            }

            // Item display (NORMAL and SIMPLE only)
            if (tItem.display != null && !tItem.display.isDead()) {
                float scale = ITEM_SCALE * (float) (1 - easedProgress);
                Location floatPos = tItem.startPosition.clone().add(0, easedProgress * 0.6, 0);
                tItem.display.teleport(floatPos);

                if (style == PreferenceType.PickupAnimation.NORMAL) {
                    // Full rotation effect
                    tItem.rotation += 0.15f;
                    tItem.display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(tItem.rotation, 0, 1, 0),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0, 0, 1, 0)
                    ));
                } else {
                    // SIMPLE: no rotation, just scale down
                    tItem.display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0, 0, 1, 0)
                    ));
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onComplete() {
        for (TeleportItem tItem : teleportItems) {
            if (tItem.display != null) {
                if (glowTeam != null) glowTeam.removeEntry(tItem.display.getUniqueId().toString());
                if (!tItem.display.isDead()) tItem.display.remove();
            }
            if (!tItem.textDisplay.isDead()) tItem.textDisplay.remove();
        }
        teleportItems.clear();
        glowTeam = null;
    }

    /** {@inheritDoc} */
    @Override
    public int getResourceCost() {
        return Math.max(2, items != null ? items.size() * 2 : 2);
    }

    // -------------------------------------------------------------------------

    private static <T extends org.bukkit.entity.Entity> void applyViewers(
            T entity, Collection<Player> viewers, JavaPlugin plugin) {
        if (viewers == null) return;
        entity.setVisibleByDefault(false);
        for (Player viewer : viewers) {
            viewer.showEntity(plugin, entity);
        }
    }

    private static class TeleportItem {
        @Nullable final ItemDisplay display;
        final TextDisplay textDisplay;
        final Location startPosition;
        final ItemStack item;
        float rotation = 0;

        TeleportItem(@Nullable ItemDisplay display, TextDisplay textDisplay,
                     Location startPosition, ItemStack item) {
            this.display = display;
            this.textDisplay = textDisplay;
            this.startPosition = startPosition;
            this.item = item;
        }
    }
}
