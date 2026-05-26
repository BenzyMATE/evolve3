package org.benski.evolv3.animations.progression;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.Main;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * World-space prestige animation played before the stone selection screen.
 *
 * <h3>Timeline (100 ticks / 5 s)</h3>
 *
 * <ol>
 *   <li><b>Charge (0–29)</b>: Contracting WITCH ring at feet collapses inward. DRAGON_BREATH mist
 *       and accent-coloured DUST gather energy. Rising-pitch chimes.
 *   <li><b>Column (30–49)</b>: Double helix of SOUL_FIRE_FLAME rises to head height. ELDER_GUARDIAN
 *       flash fires at tick 30. ENCHANT sparkles swirl.
 *   <li><b>Burst (tick 50)</b>: TOTEM_OF_UNDYING explosion + FLASH + END_ROD shockwave ring.
 *       Challenge-complete + warden-sonic-boom sounds. Title overlay appears.
 *   <li><b>Glory (51–89)</b>: Rotating accent-dust halo at eye level. END_ROD rain from above.
 *       Sustained ENCHANT sparkles and a second sound punch at tick 75.
 *   <li><b>Fade (90–99)</b>: Residual END_ROD particles wind down before callback fires.
 * </ol>
 *
 * <p>Tier-specific accent colours: Gold (1), Blue (2), Red-orange (3), Purple (4), Rainbow (5).
 */
public final class PrestigeAnimation {
  /** Creates a new PrestigeAnimation instance. */
  private PrestigeAnimation() {}

  /**
   * Plays the prestige animation for the player, then invokes {@code onDone} when complete.
   *
   * @param prestige the prestige tier (1–5, clamped automatically)
   */
  public static void play(Player player, int prestige, Runnable onDone) {
    int clamped = Math.max(1, Math.min(5, prestige));
    new PrestigeRunnable(player, clamped, onDone).runTaskTimer(Main.getInstance(), 0L, 1L);
  }

  private static final class PrestigeRunnable extends BukkitRunnable {
    private final Player player;
    private final int prestige;
    private final Runnable onDone;

    private static final int DURATION = 100;
    private static final int CHARGE_END = 30;
    private static final int COLUMN_END = 50;
    private static final int BURST_TICK = 50;
    private static final int GLORY_END = 90;

    private int tick = 0;

    PrestigeRunnable(Player player, int prestige, Runnable onDone) {
      this.player = player;
      this.prestige = prestige;
      this.onDone = onDone;
    }

    /** Executes this scheduled task. */
    @Override
    public void run() {
      if (!player.isOnline() || tick >= DURATION) {
        cancel();
        if (onDone != null) onDone.run();
        return;
      }

      Location feet = player.getLocation();
      Location eye = player.getEyeLocation();
      Color accent = accentColor();

      if (tick < CHARGE_END) {
        phaseCharge(feet, accent);
      } else if (tick < COLUMN_END) {
        phaseColumn(feet, eye, accent);
      } else if (tick == BURST_TICK) {
        phaseBurst(feet, eye, accent);
      } else if (tick <= GLORY_END) {
        phaseGlory(feet, eye, accent);
      } else {
        phaseFade(feet);
      }

      tick++;
    }

    /** Phase charge. */
    private void phaseCharge(Location feet, Color accent) {
      double progress = (double) tick / CHARGE_END;
      double radius = 3.0 - progress * 2.5;
      int points = 16;

      for (int i = 0; i < points; i++) {
        double angle = Math.toRadians(360.0 / points * i + tick * 8);
        feet.getWorld()
            .spawnParticle(
                Particle.WITCH,
                feet.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius),
                1,
                0,
                0,
                0,
                0);
      }

      if (tick % 3 == 0) {
        feet.getWorld()
            .spawnParticle(
                Particle.FALLING_OBSIDIAN_TEAR,
                feet.clone().add(0, 0.3, 0),
                4,
                0.4,
                0.05,
                0.4,
                0.01);
      }

      if (tick % 2 == 0) {
        double a = Math.toRadians(tick * 18);
        spawnColored(
            feet.clone().add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius), accent, 1.5f);
      }

      if (tick % 6 == 0) {
        float pitch = 0.5f + (float) tick / CHARGE_END;
        player.playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, pitch);
      }
    }

    /** Phase column. */
    private void phaseColumn(Location feet, Location eye, Color accent) {
      if (tick == CHARGE_END) {
        player.spawnParticle(Particle.ELDER_GUARDIAN, feet, 1);
        player.playSound(feet, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9f, 0.6f);
        player.playSound(feet, Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 1.1f);
      }

      double t = (double) (tick - CHARGE_END) / (COLUMN_END - CHARGE_END);
      double columnHeight = t * 3.0;

      for (int arm = 0; arm < 2; arm++) {
        double baseAngle = Math.toRadians(tick * 22 + arm * 180);
        double hRadius = 0.6;
        for (int h = 0; h < 5; h++) {
          double hy = (double) h / 5 * columnHeight;
          feet.getWorld()
              .spawnParticle(
                  Particle.GLOW,
                  feet.clone()
                      .add(Math.cos(baseAngle) * hRadius, hy, Math.sin(baseAngle) * hRadius),
                  1,
                  0.04,
                  0.04,
                  0.04,
                  0);
        }
      }

      if (tick % 2 == 0) {
        double a = Math.toRadians(tick * 27);
        spawnColored(
            feet.clone().add(Math.cos(a) * 0.7, columnHeight * 0.7, Math.sin(a) * 0.7),
            accent,
            1.8f);
      }

      if (tick % 3 == 0) {
        feet.getWorld()
            .spawnParticle(Particle.ENCHANT, feet.clone().add(0, 1.0, 0), 8, 0.6, 1.0, 0.6, 0.1);
      }

      if (tick == CHARGE_END + 10) {
        player.playSound(feet, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.5f);
      }
    }

    /** Phase burst. */
    private void phaseBurst(Location feet, Location eye, Color accent) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();

      eye.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, eye, 250, 0.9, 1.1, 0.9, 0.35);

      int ringPoints = 24;
      for (int i = 0; i < ringPoints; i++) {
        double angle = Math.toRadians(360.0 / ringPoints * i);
        double x = Math.cos(angle) * 2.8;
        double z = Math.sin(angle) * 2.8;
        eye.getWorld()
            .spawnParticle(Particle.END_ROD, eye.clone().add(x, 0, z), 4, 0.1, 0.2, 0.1, 0.06);
      }

      for (int i = 0; i < 40; i++) {
        double angle = rng.nextDouble() * 2 * Math.PI;
        double r = 0.5 + rng.nextDouble() * 1.8;
        double y = (rng.nextDouble() - 0.2) * 2.0;
        spawnColored(eye.clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r), accent, 2.5f);
      }

      player.playSound(feet, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
      player.playSound(feet, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.2f);
      player.playSound(feet, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);

      showPrestigeTitle(player, prestige);
    }

    /** Phase glory. */
    private void phaseGlory(Location feet, Location eye, Color accent) {
      int haloPoints = 14;
      double haloAngle = Math.toRadians(tick * 12);
      for (int i = 0; i < haloPoints; i++) {
        double a = haloAngle + Math.toRadians(360.0 / haloPoints * i);
        spawnColored(eye.clone().add(Math.cos(a) * 1.4, 0.4, Math.sin(a) * 1.4), accent, 1.4f);
      }

      if (tick % 2 == 0) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double rx = (rng.nextDouble() - 0.5) * 3.5;
        double rz = (rng.nextDouble() - 0.5) * 3.5;
        feet.getWorld()
            .spawnParticle(Particle.END_ROD, feet.clone().add(rx, 4.0, rz), 1, 0, 0, 0, 0.01);
      }

      if (tick % 3 == 0) {
        eye.getWorld().spawnParticle(Particle.ENCHANT, eye, 5, 0.5, 0.6, 0.5, 0.09);
      }

      if (tick == 75) {
        player.playSound(feet, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 0.8f);
        player.playSound(feet, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 1.8f);
      }
    }

    /** Phase fade. */
    private void phaseFade(Location feet) {
      if (tick % 3 == 0) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double rx = (rng.nextDouble() - 0.5) * 2.5;
        double rz = (rng.nextDouble() - 0.5) * 2.5;
        feet.getWorld()
            .spawnParticle(Particle.END_ROD, feet.clone().add(rx, 2.5, rz), 1, 0, 0, 0, 0.005);
      }
    }

    /** Shows prestige title to the player. */
    private void showPrestigeTitle(Player player, int prestige) {
      Component titleComp =
          Component.text("✪ PRESTIGE " + prestige)
              .color(TextColor.fromHexString("#FFD700"))
              .decorate(TextDecoration.BOLD);
      Component subComp =
          Component.text("Your pickaxe has transcended.").color(TextColor.fromHexString("#888888"));
      player.showTitle(
          Title.title(
              titleComp,
              subComp,
              Title.Times.times(
                  Duration.ofMillis(200), Duration.ofMillis(2800), Duration.ofMillis(800))));
    }

    /** Accent color. */
    private Color accentColor() {
      if (prestige == 5) return hsbToColor((tick % 60) / 60.0f, 1.0f, 1.0f);
      return switch (prestige) {
        case 1 -> Color.fromRGB(0xFF, 0xD7, 0x00);
        case 2 -> Color.fromRGB(0x55, 0xAA, 0xFF);
        case 3 -> Color.fromRGB(0xFF, 0x44, 0x00);
        case 4 -> Color.fromRGB(0xAA, 0x00, 0xFF);
        default -> Color.fromRGB(0xFF, 0xD7, 0x00);
      };
    }

    /** Hsb to color. */
    private static Color hsbToColor(float h, float s, float b) {
      h = h % 1.0f;
      int i = (int) (h * 6);
      float f = h * 6 - i;
      float p = b * (1 - s);
      float q = b * (1 - f * s);
      float t = b * (1 - (1 - f) * s);
      float r, g, bl;
      switch (i % 6) {
        case 0 -> {
          r = b;
          g = t;
          bl = p;
        }
        case 1 -> {
          r = q;
          g = b;
          bl = p;
        }
        case 2 -> {
          r = p;
          g = b;
          bl = t;
        }
        case 3 -> {
          r = p;
          g = q;
          bl = b;
        }
        case 4 -> {
          r = t;
          g = p;
          bl = b;
        }
        default -> {
          r = b;
          g = p;
          bl = q;
        }
      }
      return Color.fromRGB((int) (r * 255), (int) (g * 255), (int) (bl * 255));
    }

    /** Spawns colored. */
    private void spawnColored(Location loc, Color color, float size) {
      loc.getWorld()
          .spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));
    }
  }
}
