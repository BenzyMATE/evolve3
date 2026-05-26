package org.benski.evolv3.listeners.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.world.BlackholeManager;
import org.benski.evolv3.core.managers.world.BlackholeManager.Blackhole;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Handles blackhole rendering, proximity effects, gravitational pull, and teleportation.
 *
 * <p>Performance model: - renderCenter() is called ONCE per blackhole per tick and broadcasts via
 * world.spawnParticle(). Particle cost is O(blackholes), not O(blackholes × players). - Per-player
 * proximity effects (ring around player, potion effects, pull) are applied only to players
 * confirmed in range. - The outer loop is per-blackhole; player checks are built as a lazy list to
 * avoid iterating over all online players when no one is near.
 */
public class BlackholeListener extends BaseListener {
  private static final double PULL_STRENGTH = 0.05;
  private static final long TELEPORT_COOLDOWN = 5_000L;
  private static final long TELEPORT_DELAY = 10L;

  private static final Color DISK_INNER_A = Color.fromRGB(255, 255, 200);
  private static final Color DISK_INNER_B = Color.fromRGB(255, 160, 30);
  private static final Color DISK_MID_A = Color.fromRGB(255, 80, 0);
  private static final Color DISK_MID_B = Color.fromRGB(255, 180, 0);
  private static final Color DISK_OUTER_A = Color.fromRGB(180, 20, 0);
  private static final Color DISK_OUTER_B = Color.fromRGB(255, 60, 0);
  private static final Color HORIZON_A = Color.fromRGB(200, 50, 255);
  private static final Color HORIZON_B = Color.fromRGB(50, 0, 100);
  private static final Color RING_POLAR_1A = Color.fromRGB(180, 30, 255);
  private static final Color RING_POLAR_1B = Color.fromRGB(60, 0, 130);
  private static final Color RING_POLAR_2A = Color.fromRGB(230, 100, 255);
  private static final Color RING_POLAR_2B = Color.fromRGB(80, 0, 160);
  private static final Color SPIRAL_FROM = Color.fromRGB(8, 0, 18);
  private static final Color SPIRAL_TO = Color.fromRGB(185, 0, 245);
  private static final Color JET_A = Color.fromRGB(180, 230, 255);
  private static final Color JET_B = Color.fromRGB(255, 255, 255);

  private int tick = 0;
  private final Map<UUID, Map<String, Long>> blackholeCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> pendingTeleport = new ConcurrentHashMap<>();

  /**
   * Creates and registers the BlackholeListener and starts the effects tick scheduler. @param
   * plugin the owning plugin
   */
  public BlackholeListener(JavaPlugin plugin) {
    super(plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tickEffects, 10L, 3L);
  }

  /** Ticks effects. */
  private void tickEffects() {
    tick++;
    Collection<Blackhole> all = BlackholeManager.getInstance().getAll();
    if (all.isEmpty()) return;

    for (Blackhole bh : all) {
      World bw = Bukkit.getWorld(bh.world());
      if (bw == null) continue;

      double er2 = bh.effectRadius() * bh.effectRadius();
      List<Player> inEffect = null;

      for (Player p : bw.getPlayers()) {
        double dx = p.getX() - bh.cx();
        double dy = p.getY() + 1.0 - bh.cy();
        double dz = p.getZ() - bh.cz();
        if (dx * dx + dy * dy + dz * dz <= er2) {
          if (inEffect == null) inEffect = new ArrayList<>(4);
          inEffect.add(p);
        }
      }

      if (tick % 50 == 0) {
        Location src = new Location(bw, bh.cx(), bh.cy(), bh.cz());
        bw.playSound(src, Sound.AMBIENT_CAVE, 2.5f, 0.25f);
        bw.playSound(src, Sound.BLOCK_PORTAL_AMBIENT, 1.8f, 0.40f);
      }
      if (tick % 110 == 0) {
        bw.playSound(
            new Location(bw, bh.cx(), bh.cy(), bh.cz()), Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.35f);
      }

      if (inEffect == null) continue;

      renderCenter(bw, bh);

      double er = bh.effectRadius();
      double tr2 = bh.teleportRadius() * bh.teleportRadius();

      for (Player player : inEffect) {
        double px = player.getX();
        double py = player.getY() + 1.0;
        double pz = player.getZ();

        double dx = px - bh.cx(), dy = py - bh.cy(), dz = pz - bh.cz();
        double dist2 = dx * dx + dy * dy + dz * dz;
        double dist = Math.sqrt(dist2);
        double t = 1.0 - (dist / er);

        applyProximityEffects(player, px, py, pz, t);

        if (dist2 <= tr2) {
          boolean isPending = Boolean.TRUE.equals(pendingTeleport.get(player.getUniqueId()));
          if (!isPending) applyPull(player, px, py, pz, bh);
          applyZone3(player, px, py, pz);
        }
      }
    }
  }

  /** Renders center. */
  private void renderCenter(World bw, Blackhole bh) {
    double cx = bh.cx(), cy = bh.cy(), cz = bh.cz();
    double tpr = bh.teleportRadius();
    double dr = Math.max(tpr * 0.75, 2.0);

    if (tick % 2 == 0) {
      var jetDust = new Particle.DustTransition(JET_A, JET_B, 1.2f);
      for (int j = 1; j <= 7; j++) {
        double h = j * dr * 0.55;
        double spread = Math.max(0.15, h * 0.06);
        double ox = (Math.random() - 0.5) * spread;
        double oz = (Math.random() - 0.5) * spread;
        bw.spawnParticle(Particle.END_ROD, cx + ox, cy + h, cz + oz, 1, 0, 0, 0, 0);
        bw.spawnParticle(Particle.END_ROD, cx + ox, cy - h + 1.5, cz + oz, 1, 0, 0, 0, 0);
        if (j <= 3)
          bw.spawnParticle(
              Particle.DUST_COLOR_TRANSITION,
              cx + ox * 2,
              cy + h,
              cz + oz * 2,
              1,
              0,
              0,
              0,
              0,
              jetDust);
      }
      bw.spawnParticle(Particle.ELECTRIC_SPARK, cx, cy + 0.8, cz, 6, 0.25, 0.15, 0.25, 0.18);
    }

    var innerDust = new Particle.DustTransition(DISK_INNER_A, DISK_INNER_B, 1.8f);
    var midDust = new Particle.DustTransition(DISK_MID_A, DISK_MID_B, 1.5f);
    var outerDust = new Particle.DustTransition(DISK_OUTER_A, DISK_OUTER_B, 1.2f);

    for (int i = 0; i < 12; i++) {
      double a = (tick * 0.28 + i * (Math.PI * 2.0 / 12)) % (Math.PI * 2);
      double r = dr * (0.35 + Math.random() * 0.12);
      bw.spawnParticle(
          Particle.DUST_COLOR_TRANSITION,
          cx + Math.cos(a) * r,
          cy + 0.5 + (Math.random() - 0.5) * 0.25,
          cz + Math.sin(a) * r,
          1,
          0,
          0,
          0,
          0,
          innerDust);
    }
    for (int i = 0; i < 14; i++) {
      double a = (-tick * 0.20 + i * (Math.PI * 2.0 / 14)) % (Math.PI * 2);
      double r = dr * (0.62 + Math.random() * 0.14);
      bw.spawnParticle(
          Particle.DUST_COLOR_TRANSITION,
          cx + Math.cos(a) * r,
          cy + 0.5 + (Math.random() - 0.5) * 0.45,
          cz + Math.sin(a) * r,
          1,
          0,
          0,
          0,
          0,
          midDust);
    }
    for (int i = 0; i < 16; i++) {
      double a = (tick * 0.13 + i * (Math.PI * 2.0 / 16)) % (Math.PI * 2);
      double r = dr * (0.88 + Math.random() * 0.16);
      bw.spawnParticle(
          Particle.DUST_COLOR_TRANSITION,
          cx + Math.cos(a) * r,
          cy + 0.5 + (Math.random() - 0.5) * 0.7,
          cz + Math.sin(a) * r,
          1,
          0,
          0,
          0,
          0,
          outerDust);
    }
    if (tick % 2 == 0) {
      for (int i = 0; i < 5; i++) {
        double a = Math.random() * Math.PI * 2;
        double r = dr * (0.25 + Math.random() * 0.75);
        bw.spawnParticle(
            Particle.FLAME,
            cx + Math.cos(a) * r,
            cy + 0.3,
            cz + Math.sin(a) * r,
            1,
            0.05,
            0.08,
            0.05,
            0.01);
      }
    }

    {
      var ring1Dust = new Particle.DustTransition(HORIZON_A, HORIZON_B, 2.2f);
      var ring2Dust = new Particle.DustTransition(RING_POLAR_1A, RING_POLAR_1B, 2.0f);
      var ring3Dust = new Particle.DustTransition(RING_POLAR_2A, RING_POLAR_2B, 1.8f);

      int n1 = Math.min(24, Math.max(16, (int) (tpr * 2.0)));
      int n2 = Math.min(20, Math.max(14, (int) (tpr * 1.6)));
      int n3 = Math.min(18, Math.max(12, (int) (tpr * 1.4)));

      double pulse = 1.0 + Math.sin(tick * 0.35) * 0.30;
      for (int i = 0; i < n1; i++) {
        double a = (tick * 0.07 + i * (Math.PI * 2.0 / n1)) % (Math.PI * 2);
        bw.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            cx + Math.cos(a) * tpr,
            cy + pulse,
            cz + Math.sin(a) * tpr,
            1,
            0,
            0,
            0,
            0,
            ring1Dust);
      }

      double az2 = tick * 0.018;
      double ct2 = Math.cos(Math.toRadians(65)), st2 = Math.sin(Math.toRadians(65));
      double u2x = -Math.sin(az2), u2z = Math.cos(az2);
      double v2x = ct2 * Math.cos(az2), v2y = -st2, v2z = ct2 * Math.sin(az2);
      for (int i = 0; i < n2; i++) {
        double a = (-tick * 0.055 + i * (Math.PI * 2.0 / n2)) % (Math.PI * 2);
        double ca = Math.cos(a), sa = Math.sin(a);
        bw.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            cx + tpr * (ca * u2x + sa * v2x),
            cy + 1.0 + tpr * (sa * v2y),
            cz + tpr * (ca * u2z + sa * v2z),
            1,
            0,
            0,
            0,
            0,
            ring2Dust);
      }

      double az3 = -tick * 0.013 + Math.PI * 0.5;
      double ct3 = Math.cos(Math.toRadians(55)), st3 = Math.sin(Math.toRadians(55));
      double u3x = -Math.sin(az3), u3z = Math.cos(az3);
      double v3x = ct3 * Math.cos(az3), v3y = -st3, v3z = ct3 * Math.sin(az3);
      for (int i = 0; i < n3; i++) {
        double a = (tick * 0.04 + i * (Math.PI * 2.0 / n3)) % (Math.PI * 2);
        double ca = Math.cos(a), sa = Math.sin(a);
        bw.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            cx + tpr * (ca * u3x + sa * v3x),
            cy + 1.0 + tpr * (sa * v3y),
            cz + tpr * (ca * u3z + sa * v3z),
            1,
            0,
            0,
            0,
            0,
            ring3Dust);
      }
    }

    if (tick % 3 == 0) {
      double ha = Math.random() * Math.PI * 2;
      double hh = 0.5 + Math.random() * 2.0;
      bw.spawnParticle(
          Particle.CRIT,
          cx + Math.cos(ha) * tpr,
          cy + hh,
          cz + Math.sin(ha) * tpr,
          4,
          0.3,
          0.25,
          0.3,
          0.12);
      bw.spawnParticle(
          Particle.ENCHANT,
          cx + Math.cos(ha) * tpr,
          cy + hh,
          cz + Math.sin(ha) * tpr,
          3,
          0.5,
          0.4,
          0.5,
          0.5);
    }

    {
      var inflowDust = new Particle.DustTransition(SPIRAL_FROM, SPIRAL_TO, 1.2f);
      double rMax = dr * 2.2, kD = 0.38;
      for (int s = 0; s < 3; s++) {
        double base = s * (Math.PI * 2.0 / 3) + tick * 0.028;
        for (int i = 0; i < 8; i++) {
          double prog = i / 7.0;
          double r = rMax * Math.exp(-kD * prog * 2.5);
          if (r < 0.35) break;
          double a = base + prog * Math.PI * 1.8;
          bw.spawnParticle(
              Particle.DUST_COLOR_TRANSITION,
              cx + Math.cos(a) * r,
              cy + 0.4 + Math.sin(prog * Math.PI) * 0.5,
              cz + Math.sin(a) * r,
              1,
              0,
              0,
              0,
              0,
              inflowDust);
        }
      }
    }

    if (tick % 2 == 0) {
      for (int i = 0; i < 10; i++) {
        double a = (tick * 0.16 + i * (Math.PI * 2.0 / 10)) % (Math.PI * 2);
        bw.spawnParticle(
            Particle.PORTAL,
            cx + Math.cos(a) * dr * 0.7,
            cy + 1.0 + Math.sin(a) * dr * 0.7,
            cz + (Math.random() - 0.5) * 0.3,
            1,
            0,
            0,
            0,
            0);
      }
    }
    if (tick % 3 == 0) {
      for (int i = 0; i < 8; i++) {
        double a = (-tick * 0.11 + i * (Math.PI * 2.0 / 8)) % (Math.PI * 2);
        bw.spawnParticle(
            Particle.PORTAL,
            cx + (Math.random() - 0.5) * 0.3,
            cy + 1.0 + Math.sin(a) * dr * 0.6,
            cz + Math.cos(a) * dr * 0.6,
            1,
            0,
            0,
            0,
            0);
      }
    }

    bw.spawnParticle(
        Particle.REVERSE_PORTAL, cx, cy + 1.0, cz, 10, dr * 0.18, 0.7, dr * 0.18, 0.03);
    bw.spawnParticle(Particle.SQUID_INK, cx, cy + 0.6, cz, 4, dr * 0.12, 0.35, dr * 0.12, 0.005);

    if (tick % 3 == 0) {
      for (int i = 0; i < 4; i++) {
        double a = Math.random() * Math.PI * 2;
        double r = dr * (0.9 + Math.random() * 0.8);
        bw.spawnParticle(
            Particle.SOUL,
            cx + Math.cos(a) * r,
            cy + Math.random() * 2.5,
            cz + Math.sin(a) * r,
            1,
            0.03,
            0.07,
            0.03,
            0);
      }
    }
    if (tick % 4 == 0) {
      for (int i = 0; i < 5; i++) {
        double a = Math.random() * Math.PI * 2, r = dr * 0.4 * Math.random();
        bw.spawnParticle(
            Particle.SOUL_FIRE_FLAME,
            cx + Math.cos(a) * r,
            cy + 0.1,
            cz + Math.sin(a) * r,
            1,
            0.08,
            0,
            0.08,
            0.02);
      }
    }

    if (tick % 5 == 0) {
      double starR = Math.min(bh.effectRadius() * 0.5, 20.0);
      for (int i = 0; i < 6; i++) {
        double a = Math.random() * Math.PI * 2;
        double r = starR * (0.45 + Math.random() * 0.5);
        bw.spawnParticle(
            Particle.GLOW,
            cx + Math.cos(a) * r,
            cy + (Math.random() - 0.5) * starR * 0.4,
            cz + Math.sin(a) * r,
            1,
            0,
            0,
            0,
            0);
      }
    }

    if (tick % 4 == 0)
      bw.spawnParticle(
          Particle.LARGE_SMOKE, cx, cy + dr * 0.5, cz, 3, dr * 0.3, dr * 0.2, dr * 0.3, 0.02);
    if (tick % 3 == 0)
      bw.spawnParticle(Particle.ASH, cx, cy + 1.5, cz, 5, dr * 0.7, 1.5, dr * 0.7, 0.025);
  }

  /** Applies proximity effects. */
  private void applyProximityEffects(Player player, double px, double py, double pz, double t) {
    int ringCount = (int) (3 + t * 8);
    double ringR = 2.0 + (1.0 - t) * 1.0;
    for (int i = 0; i < ringCount; i++) {
      double a = (tick * 0.24 + i * (Math.PI * 2.0 / ringCount)) % (Math.PI * 2);
      player.spawnParticle(
          Particle.PORTAL,
          px + Math.cos(a) * ringR,
          py + (Math.random() - 0.5) * 0.8,
          pz + Math.sin(a) * ringR,
          1,
          0,
          0,
          0,
          0,
          null,
          true);
    }

    if (tick % 4 == 0 && t > 0.1) {
      int ashN = (int) (t * 5);
      if (ashN > 0)
        player.spawnParticle(Particle.ASH, px, py, pz, ashN, 1.5, 0.8, 1.5, 0.01, null, true);
    }

    if (t > 0.35) {
      if (tick % 4 == 0) {
        double sa = Math.random() * Math.PI * 2;
        player.spawnParticle(
            Particle.SOUL,
            px + Math.cos(sa) * 2.0,
            py + Math.random() * 1.5,
            pz + Math.sin(sa) * 2.0,
            1,
            0,
            0,
            0,
            0,
            null,
            true);
      }
      player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 25, 0, true, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 25, 0, true, false));
    }

    if (t > 0.65) applyZone3(player, px, py, pz);
  }

  /** Applies zone3. */
  private void applyZone3(Player player, double px, double py, double pz) {
    if (tick % 2 == 0) {
      player.spawnParticle(Particle.REVERSE_PORTAL, px, py, pz, 8, 1.7, 1.1, 1.7, 0.06, null, true);
      player.spawnParticle(
          Particle.SOUL_FIRE_FLAME, px, py - 0.5, pz, 4, 1.2, 0.3, 1.2, 0.025, null, true);
    }
    if (tick % 5 == 0)
      player.spawnParticle(Particle.ELECTRIC_SPARK, px, py, pz, 8, 1.6, 1.0, 1.6, 0.15, null, true);
    if (tick % 4 == 0)
      player.spawnParticle(Particle.END_ROD, px, py, pz, 4, 0.9, 0.5, 0.9, 0.06, null, true);
    if (tick % 5 == 0)
      player.spawnParticle(Particle.SQUID_INK, px, py, pz, 3, 0.9, 0.6, 0.9, 0.01, null, true);

    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 25, 1, true, false));
    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 25, 1, true, false));
    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, true, false));

    if (tick % 40 == 0)
      player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.5f);
    if (tick % 65 == 0)
      player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.5f, 0.75f);
  }

  /** Applies pull. */
  private void applyPull(Player player, double px, double py, double pz, Blackhole bh) {
    double dx = bh.cx() - px, dy = bh.cy() - py, dz = bh.cz() - pz;
    if (dx * dx + dy * dy + dz * dz < 0.01) return;
    player.setVelocity(
        player.getVelocity().add(new Vector(dx, dy, dz).normalize().multiply(PULL_STRENGTH)));
  }

  /** Called when player move occurs. */
  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    Location to = event.getTo();
    if (to == null) return;

    Player player = event.getPlayer();
    UUID uid = player.getUniqueId();

    if (Boolean.TRUE.equals(pendingTeleport.get(uid))) return;

    long now = System.currentTimeMillis();
    double px = to.getX(), pz = to.getZ();
    World pw = to.getWorld();

    for (Blackhole bh : BlackholeManager.getInstance().getAll()) {
      World bw = Bukkit.getWorld(bh.world());
      if (bw == null || !bw.equals(pw)) continue;
      if (bh.linkedTo() == null) continue;

      Map<String, Long> perBh = blackholeCooldowns.get(uid);
      if (perBh != null) {
        Long last = perBh.get(bh.name());
        if (last != null && now - last < TELEPORT_COOLDOWN) continue;
      }

      double dx = px - bh.cx();
      double dy = (to.getY() + 1.0) - bh.cy();
      double dz = pz - bh.cz();
      if (dx * dx + dy * dy + dz * dz > bh.teleportRadius() * bh.teleportRadius()) continue;

      Blackhole dest = BlackholeManager.getInstance().get(bh.linkedTo());
      if (dest == null) continue;
      World dw = Bukkit.getWorld(dest.world());
      if (dw == null) continue;

      Location target = randomSpawnNear(dest, dw, to.getYaw(), to.getPitch());
      if (target == null) continue;

      blackholeCooldowns.computeIfAbsent(uid, k -> new ConcurrentHashMap<>()).put(bh.name(), now);
      pendingTeleport.put(uid, true);

      Location origin = to.clone();
      Bukkit.getScheduler()
          .runTaskLater(
              Main.getInstance(),
              () -> {
                pendingTeleport.remove(uid);
                origin
                    .getWorld()
                    .spawnParticle(Particle.REVERSE_PORTAL, origin, 80, 1.2, 1.8, 1.2, 0.12);
                origin
                    .getWorld()
                    .spawnParticle(Particle.ELECTRIC_SPARK, origin, 20, 0.8, 0.5, 0.8, 0.15);
                origin.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                origin.getWorld().playSound(origin, Sound.ENTITY_WITHER_DEATH, 0.5f, 1.9f);
                player.teleport(target);
                dw.spawnParticle(Particle.PORTAL, target, 100, 1.2, 1.8, 1.2, 0.2);
                dw.spawnParticle(Particle.END_ROD, target, 30, 1.0, 1.5, 1.0, 0.08);
                dw.spawnParticle(Particle.FLAME, target, 20, 0.8, 0.4, 0.8, 0.05);
                player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
                player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.8f);
              },
              TELEPORT_DELAY);

      break; // NOPMD - intentional find-first loop
    }
  }

  /** Called when player quit occurs. */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    UUID uid = event.getPlayer().getUniqueId();
    blackholeCooldowns.remove(uid);
    pendingTeleport.remove(uid);
  }

  /** Random spawn near. */
  @Nullable
  @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
  private Location randomSpawnNear(Blackhole dest, World world, float yaw, float pitch) {
    double cx = dest.cx(), cy = dest.cy(), cz = dest.cz();
    double baseR = dest.teleportRadius() + 2.5;
    int searchY = (int) cy;
    double angleOffset = Math.random() * Math.PI * 2;

    for (int ring = 0; ring < 5; ring++) {
      double r = baseR + ring * 3.0;
      for (int i = 0; i < 16; i++) {
        double angle = angleOffset + i * (Math.PI * 2.0 / 16);
        int bx = (int) Math.floor(cx + Math.cos(angle) * r);
        int bz = (int) Math.floor(cz + Math.sin(angle) * r);

        for (int by = searchY + 30; by >= searchY - 30; by--) {
          org.bukkit.block.Block floor = world.getBlockAt(bx, by, bz);
          if (!floor.getType().isSolid()) continue;
          if (world.getBlockAt(bx, by + 1, bz).getType().isSolid()) continue;
          if (world.getBlockAt(bx, by + 2, bz).getType().isSolid()) continue;
          return new Location(
              world, bx + 0.5, by + 1, bz + 0.5, yaw, pitch); // NOPMD - intentional find-first loop
        }
      }
    }
    return null;
  }
}
