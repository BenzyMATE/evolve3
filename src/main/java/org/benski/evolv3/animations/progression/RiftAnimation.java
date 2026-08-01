package org.benski.evolv3.animations.progression;

import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import org.benski.evolv3.core.types.EnchantOption;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Portal/rift reskin of {@link EnchantAnimation}.
 *
 * <p>Instead of the strip simply appearing, the player casts a purple-essence spell that converges
 * on the strip's centre, splits in two, and travels out to where each rift will sit; each rift then
 * spins open in a swirling vortex before the enchant reel starts flowing between them. The reel
 * emerges from one rift, crosses the gap, and vanishes into the other before decelerating to a stop
 * on the same three result slots as the classic style. The landing, reveal, and selection
 * choreography are entirely inherited unchanged from {@link EnchantAnimation} — only the intro, the
 * ambient travel effects, and a handful of sounds are swapped.
 *
 * <p>Each rift is drawn entirely from particles — a dark void core, a starfield of {@link
 * Particle#GLOW} twinkles, and a swirling {@link Particle#REVERSE_PORTAL} rim — arranged in the
 * plane spanned by {@code forwardDir} and world-up, i.e. facing <em>sideways</em> along the strip's
 * travel axis rather than at the player. That orientation matters: it's what makes blocks look like
 * they punch straight through the opening as they travel, instead of sliding past a flat picture
 * facing the viewer.
 */
public class RiftAnimation extends EnchantAnimation {
  /** Ticks spent on the cast + rift-open intro before the reel starts flowing. */
  private static final int INTRO_TICKS = 50;

  /** Ticks spent on the cast spiralling inward from the player's eyes to the strip's centre. */
  private static final int CAST_CONVERGE_TICKS = 15;

  /** Ticks the split spell takes to travel from the strip's centre out to each rift location. */
  private static final int SPLIT_TRAVEL_TICKS = 10;

  /** Tick within the intro at which the cast has split and reached both rift locations. */
  private static final int SWIRL_START_TICK = CAST_CONVERGE_TICKS + SPLIT_TRAVEL_TICKS;

  /** Ticks the swirling vortex takes to spin each rift open to its full size. */
  private static final int SWIRL_OPEN_TICKS = 20;

  /**
   * Distance from the strip centre to each rift, along {@code rightDir} — just past the 4.2
   * threshold where {@link EnchantAnimation} hides strip blocks, so blocks appear to emerge from
   * and vanish into the rift itself rather than popping in mid-air.
   */
  private static final double RIFT_OFFSET = 4.3;

  /** Radius (in blocks) of the rift opening. */
  private static final double DISC_RADIUS = 0.78;

  private static final Color CAST_COLOR = Color.fromRGB(0xB3, 0x66, 0xD9);
  private static final Color VOID_COLOR = Color.fromRGB(4, 4, 12);

  private static final Random RAND = new Random();

  private boolean riftsOpened = false;

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
    world.playSound(playerLoc, "custom.magiccastwhoosh", 0.5f, 1.0f);
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
    if (riftsOpened && tick >= SWIRL_START_TICK + SWIRL_OPEN_TICKS) {
      tickRiftAmbience(tick);
    }
  }

  /**
   * Drives the three-beat intro: the cast spirals inward to the strip's centre, then splits into
   * two and travels out to each rift location, then each rift spins open in a swirling vortex —
   * only once that's finished do the enchant blocks start flowing (handled by the inherited {@code
   * introTicks} gating).
   */
  private void tickIntro(int tick) {
    World world = player.getWorld();
    if (world == null || stripCenter == null) return;

    if (tick <= CAST_CONVERGE_TICKS) {
      tickCastConverge(world, tick);
    } else if (tick <= SWIRL_START_TICK) {
      tickSpellSplit(world, tick - CAST_CONVERGE_TICKS);
    }

    if (!riftsOpened && tick >= SWIRL_START_TICK) {
      riftsOpened = true;
      openRifts(world);
    }

    int sinceSwirlStart = tick - SWIRL_START_TICK;
    if (riftsOpened && sinceSwirlStart <= SWIRL_OPEN_TICKS) {
      tickSwirlOpen(world, sinceSwirlStart);
    }
  }

  /** Purple essence spirals inward from the player's eyes toward the strip's centre. */
  private void tickCastConverge(World world, int tick) {
    double t = Math.min(1.0, (double) tick / CAST_CONVERGE_TICKS);
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
  }

  /**
   * The converged cast splits into two and travels from the strip's centre out to each rift
   * location, leaving a trail — the "spell splits in two" beat before each portal tears open.
   */
  private void tickSpellSplit(World world, int sinceSplit) {
    double t = Math.min(1.0, (double) sinceSplit / SPLIT_TRAVEL_TICKS);
    for (Location rift : riftLocations()) {
      Location pos = lerpLocation(stripCenter, rift, t);
      world.spawnParticle(
          Particle.DUST, pos, 3, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(CAST_COLOR, 1.3f));
      world.spawnParticle(Particle.END_ROD, pos, 1, 0.02, 0.02, 0.02, 0.005);
    }
  }

  /**
   * Swirling vortex that spins each rift open from nothing out to its full radius, settling into
   * the steady rim/void/starfield ambience once it reaches full size.
   */
  private void tickSwirlOpen(World world, int sinceSwirl) {
    double t = Math.min(1.0, (double) sinceSwirl / SWIRL_OPEN_TICKS);
    double radius = DISC_RADIUS * t;
    double angle = sinceSwirl * 0.9;
    for (Location rift : riftLocations()) {
      world.spawnParticle(
          Particle.REVERSE_PORTAL, discPoint(rift, radius, angle), 2, 0.02, 0.02, 0.02, 0.01);
      world.spawnParticle(
          Particle.DUST,
          discPoint(rift, radius * 0.6, -angle * 1.4),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(CAST_COLOR, 1.0f));
    }
  }

  /** Plays the portal-open sound as each rift begins its swirling opening animation. */
  private void openRifts(World world) {
    world.playSound(stripCenter, "custom.portalopenrift", 0.5f, 1.0f);
  }

  /**
   * Keeps the two rifts shimmering — void core, starfield, and rim swirl — for as long as they're
   * open, including through the reveal and selection phases so they stay visible while the player
   * is choosing. The travelling whoosh only plays while the reel is still actually moving.
   */
  private void tickRiftAmbience(int tick) {
    World world = player.getWorld();
    if (world == null || stripCenter == null) return;

    for (Location rift : riftLocations()) {
      if (tick % 3 == 0) {
        spawnRimShimmer(world, rift, 3);
      }
      if (tick % 6 == 0) {
        spawnVoidCore(world, rift, 2);
      }
      if (tick % 10 == 0) {
        spawnStarfield(world, rift, 2);
      }
    }
    if (tick < scrollEndTick && tick % 6 == 0) {
      world.playSound(stripCenter, "custom.objectflybywhoosh", 0.5f, 1.3f);
    }
  }

  /**
   * Scatters faint, near-black {@link Particle#DUST} points across the rift's opening, giving it
   * depth and darkness instead of reading as empty air.
   */
  private void spawnVoidCore(World world, Location center, int count) {
    for (int i = 0; i < count; i++) {
      double radius = RAND.nextDouble() * DISC_RADIUS * 0.7;
      double angle = RAND.nextDouble() * 2 * Math.PI;
      world.spawnParticle(
          Particle.DUST,
          discPoint(center, radius, angle),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(VOID_COLOR, 1.6f));
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
   * Returns a point on the rift's disc, parameterised by polar coordinates in the plane spanned by
   * {@code forwardDir} and world-up — i.e. facing sideways along the strip's travel axis ({@code
   * rightDir}), so blocks moving along that axis pass straight through the opening instead of past
   * its face.
   */
  private Location discPoint(Location center, double radius, double angle) {
    return center
        .clone()
        .add(forwardDir.clone().multiply(Math.cos(angle) * radius))
        .add(0, Math.sin(angle) * radius, 0);
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
    world.playSound(origin, "custom.objectflybywhoosh", 0.5f, pitch);
  }

  /** Plays a magic-lock impact instead of the classic beacon/level-up landing sound. */
  @Override
  protected void playLandingSound(World world, Location origin) {
    world.playSound(origin, "custom.magiclockimpact", 0.65f, 1.0f);
  }

  /** Collapses both rifts with a reverse-whoosh before the usual cleanup runs. */
  @Override
  protected void doComplete() {
    World world = player.getWorld();
    if (world != null && stripCenter != null) {
      world.playSound(stripCenter, "custom.vacuumsuckshort", 0.4f, 1.0f);
      for (Location rift : riftLocations()) {
        world.spawnParticle(Particle.REVERSE_PORTAL, rift, 20, 0.2, 0.4, 0.2, 0.3);
      }
    }
    super.doComplete();
  }
}
