package org.benski.evolv3.animations.progression;

import java.util.List;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Animation for essence discovery effects. Creates a particle that flies up, loops, then travels to
 * the player. Uses client-side particles for performance.
 *
 * <p>The animation has three phases:
 *
 * <ol>
 *   <li>Fly up - particle rises from the block
 *   <li>Loop - particle does a 360° loop in the air
 *   <li>Travel - particle flies to the player
 * </ol>
 */
public class EssenceAnimation extends BaseAnimation {
  private static final int DEFAULT_DURATION = 60;

  private static final int FLY_UP_DURATION = 15;
  private static final int LOOP_DURATION = 25;
  private static final int TRAVEL_DURATION = 20;

  private final int essenceAmount;
  private Player targetPlayer;
  private Location centerLocation;

  private boolean prefSound = true;
  private boolean prefParticles = true;

  /**
   * Creates an essence animation with default settings. Required for registry registration.
   *
   * @param plugin the plugin instance
   */
  public EssenceAnimation(JavaPlugin plugin) {
    this(plugin, 1);
  }

  /**
   * Creates an essence animation with a specific essence amount.
   *
   * @param plugin the plugin instance
   * @param essenceAmount the amount of essence discovered
   */
  public EssenceAnimation(JavaPlugin plugin, int essenceAmount) {
    super(plugin, DEFAULT_DURATION);
    this.essenceAmount = essenceAmount;
  }

  /** {@inheritDoc} */
  @Override
  protected void onStart() {
    Location origin = getOrigin();
    if (origin == null || origin.getWorld() == null) return;

    centerLocation = origin.clone().add(0.5, 0.5, 0.5);

    var viewers = getViewers();
    if (viewers != null && !viewers.isEmpty()) {
      targetPlayer = viewers.iterator().next();
    }

    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc != null) {
      PlayerProfile profile = pdc.getLocal(targetPlayer);
      if (profile != null) {
        PlayerPreferences prefs = profile.preferences;
        prefSound = prefs.essenceSound;
        prefParticles = prefs.essenceParticles;
      }
    }

    if (!prefSound) return;
    targetPlayer.playSound(centerLocation, "custom.burstessence", 0.5f, 1.0f);
  }

  /** {@inheritDoc} */
  @Override
  protected void onTick(double progress) {
    if (targetPlayer == null || !targetPlayer.isOnline()) {
      stop();
      return;
    }

    int currentTick = getCurrentTick();
    Location particleLocation = calculateParticleLocation(currentTick);

    if (!prefParticles) return;
    spawnParticle(particleLocation, Particle.END_ROD, 1, 0.02, 0.02, 0.02, 0.01);
    spawnParticle(particleLocation, Particle.WITCH, 2, 0.1, 0.1, 0.1, 0.02);

    if (currentTick % 2 == 0) {
      spawnParticle(particleLocation, Particle.FALLING_OBSIDIAN_TEAR, 1, 0.05, 0.05, 0.05, 0);
    }

    if (currentTick >= FLY_UP_DURATION
        && currentTick < FLY_UP_DURATION + LOOP_DURATION
        && currentTick % 3 == 0) {
      spawnParticle(particleLocation, Particle.ENCHANT, 2, 0.05, 0.05, 0.05, 0.3);
    }

    if (currentTick == FLY_UP_DURATION + LOOP_DURATION && prefSound) {
      targetPlayer.playSound(centerLocation, "custom.flyingtowardsessence", 1.0f, 1.0f);
    }
  }

  /** {@inheritDoc} */
  @Override
  protected void onComplete() {
    if (targetPlayer == null || !targetPlayer.isOnline()) return;

    playArrivalEffects();
  }

  /**
   * Calculates the particle location based on the current tick.
   *
   * @param currentTick the current animation tick
   * @return the calculated particle location
   */
  private Location calculateParticleLocation(int currentTick) {
    if (currentTick < FLY_UP_DURATION) {
      double progress = (double) currentTick / FLY_UP_DURATION;
      double height = progress * 3.0;
      return centerLocation.clone().add(0, height, 0);

    } else if (currentTick < FLY_UP_DURATION + LOOP_DURATION) {
      int loopTicks = currentTick - FLY_UP_DURATION;
      double loopProgress = (double) loopTicks / LOOP_DURATION;
      double angle = loopProgress * 2 * Math.PI;

      double radius = 1.5;
      double x = Math.cos(angle) * radius;
      double z = Math.sin(angle) * radius;
      double y = 3.0 + Math.sin(angle * 2) * 0.5;

      return centerLocation.clone().add(x, y, z);

    } else {
      int travelTicks = currentTick - FLY_UP_DURATION - LOOP_DURATION;
      double progress = (double) travelTicks / TRAVEL_DURATION;

      Location loopEnd = centerLocation.clone().add(1.5, 3.0, 0);
      Location playerTarget =
          targetPlayer
              .getEyeLocation()
              .add(targetPlayer.getLocation().getDirection().multiply(-0.5).setY(-0.3));

      Vector start = loopEnd.toVector();
      Vector end = playerTarget.toVector();
      Vector current = start.clone().multiply(1 - progress).add(end.clone().multiply(progress));

      return current.toLocation(targetPlayer.getWorld());
    }
  }

  /** Plays arrival effects when the essence reaches the player. */
  private void playArrivalEffects() {
    Location playerLocation =
        targetPlayer
            .getEyeLocation()
            .add(targetPlayer.getLocation().getDirection().multiply(-0.5).setY(-0.3));

    if (prefParticles) {
      spawnParticle(playerLocation, Particle.END_ROD, 3, 0.1, 0.1, 0.1, 0.02);
      spawnParticle(playerLocation, Particle.WITCH, 10, 0.1, 0.1, 0.1, 0.02);
    }

    if (prefSound) {
      targetPlayer.playSound(
          targetPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }
  }

  /** Shows the hologram text if enabled. */
  private void showHologram() {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    boolean hologramEnabled = config.getBoolean(ConfigPaths.Default.Essence.HOLOGRAM_ENABLED, true);

    if (!hologramEnabled) return;

    String message =
        config.getString(
            ConfigPaths.Default.Essence.HOLOGRAM_TEXT, "&5\uD83D\uDD25 &d+%amount% Essence");
    message = message.replace("%amount%", Utils.formatNumber(essenceAmount));

    Location origin = getOrigin();
    if (origin != null) {
      AnimationRegistry.getInstance()
          .play("hologram", origin.clone().add(0.5, 1.5, 0.5), List.of(targetPlayer), message);
    }
  }

  /** Spawns a particle only visible to the target player. */
  private void spawnParticle(
      Location location,
      Particle particle,
      int count,
      double offsetX,
      double offsetY,
      double offsetZ,
      double extra) {
    spawnParticle(location, particle, count, offsetX, offsetY, offsetZ, extra, null);
  }

  /**
   * Spawns a particle with optional data, only visible to the target player.
   *
   * @param <T> the particle data type
   */
  private <T> void spawnParticle(
      Location location,
      Particle particle,
      int count,
      double offsetX,
      double offsetY,
      double offsetZ,
      double extra,
      T data) {
    if (targetPlayer != null && targetPlayer.isOnline()) {
      targetPlayer.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getResourceCost() {
    return 3;
  }
}
