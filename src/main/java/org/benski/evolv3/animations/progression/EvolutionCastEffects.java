package org.benski.evolv3.animations.progression;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Shared "cast" opening beat for evolution animations — gold/atom-coloured magic bursts from the
 * player, mirroring how the rift enchant animation opens with a spell cast. Every evolution style
 * shares this exact signature flourish before diverging into its own unique sequence, the same way
 * every enchant style shares the same landing mechanic.
 */
final class EvolutionCastEffects {
  /** The atom/evolution theme colour — gold, mirroring essence's purple in the enchant styles. */
  static final Color ATOM_GOLD = Color.fromRGB(255, 205, 60);

  /** A deeper gold used for secondary/accent particles alongside {@link #ATOM_GOLD}. */
  static final Color DEEP_GOLD = Color.fromRGB(200, 130, 20);

  private EvolutionCastEffects() {}

  /**
   * Plays the shared cast burst at the player's location.
   *
   * @param player the player casting
   * @param world the world to play effects in
   */
  static void playCastBurst(Player player, World world) {
    Location eye = player.getEyeLocation();
    player.spawnParticle(
        Particle.DUST, eye, 34, 0.28, 0.28, 0.28, 0, new Particle.DustOptions(ATOM_GOLD, 1.6f));
    player.spawnParticle(Particle.END_ROD, eye, 22, 0.22, 0.22, 0.22, 0.03);
    world.playSound(player.getLocation(), "custom.magiccastwhoosh", 0.6f, 1.3f);
    world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.8f);
  }
}
