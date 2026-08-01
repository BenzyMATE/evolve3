package org.benski.evolv3.animations.progression;

import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import org.benski.evolv3.core.types.EnchantOption;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Portal/rift reskin of {@link EnchantAnimation}.
 *
 * <p>Instead of the strip simply appearing, the player casts a purple-essence spell that tears open
 * a rift on either side of where the strip will travel; the enchant reel then emerges from one
 * rift, crosses the gap, and vanishes into the other before decelerating to a stop on the same
 * three result slots as the classic style. The landing, reveal, and selection choreography are
 * entirely inherited unchanged from {@link EnchantAnimation} — only the intro, the ambient travel
 * effects, and a handful of sounds are swapped.
 *
 * <p>Each rift is a flat, always-facing-the-viewer black panel (a {@link Display.Billboard#CENTER}
 * {@link BlockDisplay}, so no manual orientation math is needed) with a starfield of {@link
 * Particle#GLOW} twinkles inside it and a swirling {@link Particle#REVERSE_PORTAL} shimmer around
 * its rim — the panel gives the "hole" its solidity while the particles sell the "looking into
 * space" read that particles alone can't.
 */
public class RiftAnimation extends EnchantAnimation {
  /** Ticks spent on the cast + rift-open intro before the reel starts flowing. */
  private static final int INTRO_TICKS = 50;

  /** Tick within the intro at which the two rifts tear open. */
  private static final int RIFT_OPEN_TICK = 22;

  /** Ticks for each rift panel to grow from a pinprick to its full size once it tears open. */
  private static final int RIFT_GROW_DURATION = 12;

  /**
   * Distance from the strip centre to each rift, along {@code rightDir} — just past the 4.2
   * threshold where {@link EnchantAnimation} hides strip blocks, so blocks appear to emerge from
   * and vanish into the rift itself rather than popping in mid-air.
   */
  private static final double RIFT_OFFSET = 4.3;

  /** Full width/height of each rift's void panel. */
  private static final float PANEL_SIZE = 1.6f;

  /**
   * Thickness of the void panel — kept small since {@link Display.Billboard#CENTER} always faces
   * the viewer, so depth is barely visible anyway.
   */
  private static final float PANEL_DEPTH = 0.06f;

  /** Radius (in blocks) of the starfield/rim disc drawn in front of each panel. */
  private static final double DISC_RADIUS = 0.78;

  private static final Color CAST_COLOR = Color.fromRGB(0xB3, 0x66, 0xD9);

  private static final Random RAND = new Random();

  private boolean riftsOpened = false;
  private final BlockDisplay[] riftPanels = new BlockDisplay[2];

  /**
   * Creates a rift-style enchant selection animation.
   *
   * @param plugin the plugin instance
   * @param player the player receiving the selection
   * @param enchantOptions the three options to present
   * @param onSelect callback invoked when an option is chosen
   * @param scrollEndTick how many ticks the strip scrolls before stopping, once it starts (controls
   *     animation speed — lower = faster)
   */
  public RiftAnimation(
      JavaPlugin plugin,
      Player player,
      List<EnchantOption> enchantOptions,
      BiConsumer<Player, EnchantOption> onSelect,
      int scrollEndTick) {
    super(plugin, player, enchantOptions, onSelect, scrollEndTick, INTRO_TICKS);
  }

  /** Replaces the classic guardian-particle + dragon-ambient flourish with the spell cast. */
  @Override
  protected void playIntroEffects(World world, Location playerLoc) {
    world.playSound(playerLoc, "custom.magiccastwhoosh", 1.0f, 1.0f);
    world.spawnParticle(Particle.WITCH, player.getEyeLocation(), 30, 0.25, 0.25, 0.25, 0.02);
    world.spawnParticle(
        Particle.DUST,
        player.getEyeLocation(),
        20,
        0.2,
        0.2,
        0.2,
        0,
        new Particle.DustOptions(CAST_COLOR, 1.4f));
  }

  /** Do tick. */
  @Override
  protected void doTick(double progress) {
    int tick = getCurrentTick();
    if (tick <= introTicks) {
      tickIntro(tick);
    }
    super.doTick(progress);
    if (tick > introTicks && tick < scrollEndTick) {
      tickRiftAmbience(tick);
    }
  }

  /** Rising spiral of purple essence from the player toward the strip, then the rifts tear open. */
  private void tickIntro(int tick) {
    World world = player.getWorld();
    if (world == null || stripCenter == null) return;

    double t = Math.min(1.0, (double) tick / introTicks);
    Location swirlCenter = lerpLocation(player.getEyeLocation(), stripCenter, t);
    double angle = tick * 0.6;
    double radius = 0.3 + 0.5 * t;
    world.spawnParticle(
        Particle.DUST,
        swirlCenter
            .clone()
            .add(Math.cos(angle) * radius, Math.sin(angle * 1.3) * 0.2, Math.sin(angle) * radius),
        2,
        0,
        0,
        0,
        0,
        new Particle.DustOptions(CAST_COLOR, 1.1f));

    if (!riftsOpened && tick >= RIFT_OPEN_TICK) {
      riftsOpened = true;
      openRifts(world);
    }
    if (riftsOpened) {
      updateRiftGrow(tick - RIFT_OPEN_TICK);
    }
  }

  /** Spawns the two void-panel rifts on either side of the strip and plays the opening sound. */
  private void openRifts(World world) {
    world.playSound(stripCenter, "custom.portalopenrift", 1.0f, 1.0f);
    world.playSound(stripCenter, "custom.energyfieldloop", 0.6f, 1.0f);

    Location[] rifts = riftLocations();
    for (int i = 0; i < rifts.length; i++) {
      final int idx = i;
      BlockDisplay panel =
          player
              .getWorld()
              .spawn(
                  rifts[idx],
                  BlockDisplay.class,
                  e -> {
                    e.setVisibleByDefault(false);
                    e.setBlock(Material.BLACK_CONCRETE.createBlockData());
                    e.setBrightness(new Display.Brightness(0, 0));
                    e.setBillboard(Display.Billboard.CENTER);
                    e.setPersistent(false);
                    e.setTransformation(panelTransform(0.05f));
                  });
      player.showEntity(plugin, panel);
      riftPanels[idx] = panel;

      spawnRimShimmer(world, rifts[idx], 14);
      spawnStarfield(world, rifts[idx], 10);
    }
  }

  /** Grows each rift panel from a pinprick to full size over {@link #RIFT_GROW_DURATION} ticks. */
  private void updateRiftGrow(int sinceOpen) {
    if (sinceOpen > RIFT_GROW_DURATION) return;
    float t = Math.max(0f, Math.min(1f, sinceOpen / (float) RIFT_GROW_DURATION));
    float scale = Math.max(0.05f, (float) easeOutCubic(t));
    for (BlockDisplay panel : riftPanels) {
      if (panel != null && !panel.isDead()) {
        panel.setTransformation(panelTransform(scale));
      }
    }
  }

  /**
   * Keeps the two rifts shimmering — rim swirl and a light starfield twinkle — while the reel
   * travels between them.
   */
  private void tickRiftAmbience(int tick) {
    World world = player.getWorld();
    if (world == null || stripCenter == null) return;

    for (Location rift : riftLocations()) {
      if (tick % 3 == 0) {
        spawnRimShimmer(world, rift, 3);
      }
      if (tick % 10 == 0) {
        spawnStarfield(world, rift, 2);
      }
    }
    if (tick % 6 == 0) {
      world.playSound(stripCenter, "custom.objectflybywhoosh", 0.35f, 1.3f);
    }
    if ((tick - introTicks) % 40 == 0) {
      world.playSound(stripCenter, "custom.energyfieldloop", 0.4f, 1.0f);
    }
  }

  /**
   * Scatters faint, soft-white {@link Particle#GLOW} points within the rift disc — distant "stars"
   * seen through the opening.
   */
  private void spawnStarfield(World world, Location center, int count) {
    for (int i = 0; i < count; i++) {
      double radius = RAND.nextDouble() * DISC_RADIUS * 0.85;
      double angle = RAND.nextDouble() * 2 * Math.PI;
      world.spawnParticle(Particle.GLOW, discPoint(center, radius, angle), 1, 0, 0, 0, 0);
    }
  }

  /** Swirls {@link Particle#REVERSE_PORTAL} particles around the rift's rim, not its centre. */
  private void spawnRimShimmer(World world, Location center, int count) {
    for (int i = 0; i < count; i++) {
      double angle = RAND.nextDouble() * 2 * Math.PI;
      Location edge = discPoint(center, DISC_RADIUS * 0.95, angle);
      world.spawnParticle(Particle.REVERSE_PORTAL, edge, 1, 0.03, 0.03, 0.03, 0.01);
    }
  }

  /**
   * Returns a point on the disc facing the player at {@code center}, parameterised by polar
   * coordinates in the (rightDir, world-up) plane — a close approximation of the camera-facing
   * plane the billboarded panel actually renders in.
   */
  private Location discPoint(Location center, double radius, double angle) {
    return center
        .clone()
        .add(rightDir.clone().multiply(Math.cos(angle) * radius))
        .add(0, Math.sin(angle) * radius, 0);
  }

  /**
   * Centred, uniformly-scaled flat panel transform (width/height {@link #PANEL_SIZE} at {@code
   * scale}, fixed thin depth).
   */
  private static Transformation panelTransform(float scale) {
    float size = PANEL_SIZE * scale;
    float half = size / 2f;
    float halfDepth = PANEL_DEPTH / 2f;
    return new Transformation(
        new Vector3f(-half, -half, -halfDepth),
        new AxisAngle4f(0, 0, 1, 0),
        new Vector3f(size, size, PANEL_DEPTH),
        new AxisAngle4f(0, 0, 1, 0));
  }

  /** Returns the two rift locations, one on either side of the strip. */
  private Location[] riftLocations() {
    return new Location[] {
      stripCenter.clone().add(rightDir.clone().multiply(-RIFT_OFFSET)),
      stripCenter.clone().add(rightDir.clone().multiply(RIFT_OFFSET))
    };
  }

  /** Plays the traveling-through-space whoosh instead of the classic note-block tick. */
  @Override
  protected void playScrollWhoosh(World world, Location origin, float pitch) {
    world.playSound(origin, "custom.objectflybywhoosh", 0.22f, pitch);
  }

  /** Plays a magic-lock impact instead of the classic beacon/level-up landing sound. */
  @Override
  protected void playLandingSound(World world, Location origin) {
    world.playSound(origin, "custom.magiclockimpact", 1.0f, 1.0f);
  }

  /** Collapses both rifts with a reverse-whoosh before the usual cleanup runs. */
  @Override
  protected void doComplete() {
    World world = player.getWorld();
    if (world != null && stripCenter != null) {
      world.playSound(stripCenter, "custom.vacuumsuckshort", 0.8f, 1.0f);
      for (Location rift : riftLocations()) {
        world.spawnParticle(Particle.REVERSE_PORTAL, rift, 20, 0.2, 0.4, 0.2, 0.3);
      }
    }
    for (BlockDisplay panel : riftPanels) {
      if (panel != null && !panel.isDead()) panel.remove();
    }
    super.doComplete();
  }
}
