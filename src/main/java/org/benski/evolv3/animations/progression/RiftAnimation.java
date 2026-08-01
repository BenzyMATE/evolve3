package org.benski.evolv3.animations.progression;

import java.util.List;
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
 * <p>Instead of the strip simply appearing, the player casts a purple-essence spell that tears open
 * a rift on either side of where the strip will travel; the enchant reel then emerges from one
 * rift, crosses the gap, and vanishes into the other before decelerating to a stop on the same
 * three result slots as the classic style. The landing, reveal, and selection choreography are
 * entirely inherited unchanged from {@link EnchantAnimation} — only the intro, the ambient travel
 * effects, and a handful of sounds are swapped.
 */
public class RiftAnimation extends EnchantAnimation {
  /** Ticks spent on the cast + rift-open intro before the reel starts flowing. */
  private static final int INTRO_TICKS = 50;

  /** Tick within the intro at which the two rifts tear open. */
  private static final int RIFT_OPEN_TICK = 22;

  /**
   * Distance from the strip centre to each rift, along {@code rightDir} — just past the 4.2
   * threshold where {@link EnchantAnimation} hides strip blocks, so blocks appear to emerge from
   * and vanish into the rift itself rather than popping in mid-air.
   */
  private static final double RIFT_OFFSET = 4.3;

  private static final Color CAST_COLOR = Color.fromRGB(0xB3, 0x66, 0xD9);

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
  }

  /** Spawns the two rift portals on either side of the strip and plays the opening sound. */
  private void openRifts(World world) {
    world.playSound(stripCenter, "custom.portalopenrift", 1.0f, 1.0f);
    world.playSound(stripCenter, "custom.energyfieldloop", 0.6f, 1.0f);
    for (Location rift : riftLocations()) {
      world.spawnParticle(Particle.REVERSE_PORTAL, rift, 40, 0.35, 0.6, 0.35, 0.6);
      world.spawnParticle(Particle.PORTAL, rift, 25, 0.3, 0.55, 0.3, 0.8);
    }
  }

  /** Keeps the two rift portals shimmering while the reel travels between them. */
  private void tickRiftAmbience(int tick) {
    World world = player.getWorld();
    if (world == null || stripCenter == null) return;

    if (tick % 4 == 0) {
      for (Location rift : riftLocations()) {
        world.spawnParticle(Particle.REVERSE_PORTAL, rift, 4, 0.3, 0.55, 0.3, 0.4);
      }
    }
    if (tick % 6 == 0) {
      world.playSound(stripCenter, "custom.objectflybywhoosh", 0.35f, 1.3f);
    }
    if ((tick - introTicks) % 40 == 0) {
      world.playSound(stripCenter, "custom.energyfieldloop", 0.4f, 1.0f);
    }
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
    super.doComplete();
  }
}
