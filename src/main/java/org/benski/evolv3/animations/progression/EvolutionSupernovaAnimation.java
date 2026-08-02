package org.benski.evolv3.animations.progression;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.items.EvolutionStone;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Evolution animation variant — "Supernova Descent".
 *
 * <p>Opens the same way the rift enchant animation does — a cast of magic (gold instead of purple,
 * to match the atom/evolution colour scheme) shoots out from the player — but everything after that
 * beat is unique to this animation: the cast rises into the sky as a gathering star, then plunges
 * back down as a comet and detonates in a shockwave, out of which the evolved pickaxe and its
 * reward stone emerge.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 18) Gold magic bursts from the player and begins streaming upward.
 *   <li>Ascend (18– 45) The cast rises into the sky as a trailing star; the current pickaxe orbits
 *       the impact point below, waiting.
 *   <li>Charge (45– 65) The star gathers and pulses at its peak, building for the fall.
 *   <li>Descent (65– 85) The star rockets back down as a comet with a burning tail.
 *   <li>Impact ( 85) Detonates in a shockwave — no blocks are harmed, it's all particles/sound.
 *   <li>Emerge ( 85–120) The evolved pickaxe and reward stone rise out of the impact site.
 *   <li>Float (120–155) Items hover with a rarity-coloured halo.
 *   <li>Return (155–178) Items sweep back to the player.
 * </ol>
 */
public class EvolutionSupernovaAnimation implements EvolutionAnimation {
  private static final int CAST_END = 18;
  private static final int ASCEND_END = 45;
  private static final int CHARGE_END = 65;
  private static final int IMPACT_TICK = 85;
  private static final int CALLBACK_TICK = 85;
  private static final int EMERGE_END = 120;
  private static final int FLOAT_END = 155;
  private static final int RETURN_END = 178;
  private static final int TOTAL = 181;

  private static final double RISE_HEIGHT = 22.0;
  private static final double ORBIT_RADIUS = 1.6;

  private static final Color GOLD_DUST = Color.fromRGB(255, 205, 60);
  private static final Color DEEP_GOLD = Color.fromRGB(200, 130, 20);

  private final JavaPlugin plugin;
  private final Player player;
  private final EvolutionTier currentTier;
  private final EvolutionTier nextTier;
  private final RarityConfig winningRarity;
  private final String stoneEnchantName;
  private final Runnable callback;

  private boolean finished = false;
  private boolean callbackFired = false;
  private boolean itemsReturned = false;
  private int currentTick = 0;
  private Runnable onComplete = null;

  private ItemStack heldPickaxe;
  private int heldSlot;

  /** Ground-level point in front of the player where the comet lands and items emerge. */
  private Location impactCenter;

  /** High above {@link #impactCenter}, where the cast gathers before falling. */
  private Location skyPoint;

  private Location pickaxeOrbitCenter;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private BlockDisplay star;
  private ItemDisplay orbitingPickaxe;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  private double orbitAngle = 0;

  /**
   * Creates the EvolutionSupernovaAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionSupernovaAnimation(
      @NotNull JavaPlugin plugin,
      @NotNull Player player,
      @NotNull EvolutionTier currentTier,
      @NotNull EvolutionTier nextTier,
      @NotNull RarityConfig winningRarity,
      @NotNull Runnable callback) {
    this.plugin = plugin;
    this.player = player;
    this.currentTier = currentTier;
    this.nextTier = nextTier;
    this.winningRarity = winningRarity;
    this.stoneEnchantName = pickRandomEnchant(player);
    this.callback = callback;
  }

  /** Returns {@code true} if callback fired. */
  @Override
  public boolean isCallbackFired() {
    return callbackFired;
  }

  /** Returns {@code true} if finished. */
  @Override
  public boolean isFinished() {
    return finished;
  }

  /** Returns the held pickaxe. */
  @Override
  public ItemStack getHeldPickaxe() {
    return heldPickaxe;
  }

  /** Sets the on complete. */
  @Override
  public void setOnComplete(Runnable r) {
    this.onComplete = r;
  }

  /** Stops the current operation. */
  @Override
  public void stop() {
    if (finished) return;
    finished = true;
    removeAllDisplays();
  }

  /** Starts this component. */
  @Override
  public void start() {
    Location eye = player.getEyeLocation();
    World world = eye.getWorld();
    if (world == null) return;

    Vector forward = eye.getDirection().setY(0).normalize();
    impactCenter = player.getLocation().clone().add(forward.clone().multiply(3.5));
    skyPoint = impactCenter.clone().add(0, RISE_HEIGHT, 0);
    pickaxeOrbitCenter = impactCenter.clone().add(0, 1.4, 0);
    exitPickaxeFinalPos = impactCenter.clone().add(-0.5, 2.6, 0);
    exitStoneFinalPos = impactCenter.clone().add(0.6, 2.6, 0);
    titlePos = impactCenter.clone().add(0, 3.7, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    player.spawnParticle(
        Particle.DUST, eye, 30, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(GOLD_DUST, 1.6f));
    player.spawnParticle(Particle.END_ROD, eye, 20, 0.2, 0.2, 0.2, 0.03);
    world.playSound(player.getLocation(), "custom.magiccastwhoosh", 0.6f, 1.3f);
    world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.8f);

    orbitingPickaxe =
        world.spawn(
            pickaxeOrbitCenter,
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(heldPickaxe);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.75f));
            });
    player.showEntity(plugin, orbitingPickaxe);

    star =
        world.spawn(
            player.getLocation().clone().add(0, -3, 0),
            BlockDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setBlock(Material.RAW_GOLD_BLOCK.createBlockData());
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setTransformation(centeredCube(0.001f));
            });
    player.showEntity(plugin, star);

    new BukkitRunnable() {
      /** Executes this scheduled task. */
      @Override
      public void run() {
        if (finished) {
          cancel();
          return;
        }
        if (!player.isOnline()) {
          cleanup();
          cancel();
          return;
        }
        currentTick++;
        tick();
        if (currentTick >= TOTAL) {
          cleanup();
          cancel();
        }
      }
    }.runTaskTimer(plugin, 1L, 1L);
  }

  /** Ticks this component. */
  private void tick() {
    World world = player.getWorld();
    if (world == null) return;

    orbitAngle += 0.09;

    if (currentTick <= CAST_END) {
      tickCast(world);
    } else if (currentTick <= ASCEND_END) {
      tickAscend(world);
    } else if (currentTick <= CHARGE_END) {
      tickCharge(world);
    } else if (currentTick <= IMPACT_TICK) {
      tickDescent(world);
    }

    if (currentTick == IMPACT_TICK) {
      triggerImpact(world);
    }

    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > IMPACT_TICK && currentTick <= EMERGE_END) {
      tickEmerge(world);
    }
    if (currentTick == EMERGE_END) {
      spawnEvolutionTitle(world);
    }
    if (currentTick > EMERGE_END && currentTick <= FLOAT_END) {
      tickFloat(world);
    }
    if (currentTick > FLOAT_END && currentTick <= RETURN_END) {
      tickReturn();
    }
    if (currentTick == RETURN_END) {
      deliverItems(world);
    }
  }

  /** Cast phase — gold magic bursts from the player, feeding the star awake at their feet. */
  private void tickCast(World world) {
    double t = (double) currentTick / CAST_END;
    if (star != null && !star.isDead()) {
      star.teleport(player.getLocation().clone().add(0, -3 + 3 * t, 0));
      float scale = 0.15f + 0.2f * (float) t;
      star.setTransformation(centeredCube(scale));
    }
    if (currentTick % 2 == 0) {
      player.spawnParticle(
          Particle.DUST,
          player.getLocation().clone().add(0, 1, 0),
          4,
          0.3,
          0.3,
          0.3,
          0,
          new Particle.DustOptions(GOLD_DUST, 1.3f));
    }
    if (currentTick == 10) {
      world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.7f);
    }
  }

  /**
   * Ascend phase — the star streams up into the sky while the current pickaxe orbits the impact
   * point below, waiting to be consumed.
   */
  private void tickAscend(World world) {
    double t = (double) (currentTick - CAST_END) / (ASCEND_END - CAST_END);
    double eased = 1 - Math.pow(1 - t, 2);
    if (star != null && !star.isDead()) {
      Location pos = lerp(player.getLocation().add(0, 0.5, 0), skyPoint, eased);
      star.teleport(pos);
      star.setTransformation(centeredCube(0.35f));
      if (currentTick % 2 == 0) {
        world.spawnParticle(
            Particle.DUST, pos, 3, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(GOLD_DUST, 1.4f));
        world.spawnParticle(Particle.END_ROD, pos, 1, 0.05, 0.05, 0.05, 0.01);
      }
    }
    tickOrbit();
    if (currentTick == CAST_END + 8) {
      world.playSound(impactCenter, Sound.ITEM_FIRECHARGE_USE, 0.6f, 0.8f);
    }
  }

  /** Charge phase — the star gathers at the sky point, pulsing and swirling as it builds power. */
  private void tickCharge(World world) {
    double t = (double) (currentTick - ASCEND_END) / (CHARGE_END - ASCEND_END);
    if (star != null && !star.isDead()) {
      float pulse = 0.35f + 0.15f * (float) Math.sin(currentTick * 0.6);
      star.setTransformation(centeredCube(pulse));
    }
    double angle = currentTick * 0.5;
    for (int i = 0; i < 2; i++) {
      double a = angle + i * Math.PI;
      double radius = 0.9 - 0.4 * t;
      Location p =
          skyPoint.clone().add(Math.cos(a) * radius, Math.sin(a * 0.6) * 0.3, Math.sin(a) * radius);
      world.spawnParticle(
          Particle.DUST, p, 2, 0, 0, 0, 0, new Particle.DustOptions(DEEP_GOLD, 1.2f));
    }
    world.spawnParticle(Particle.ELECTRIC_SPARK, skyPoint, 2, 0.4, 0.4, 0.4, 0.02);
    tickOrbit();
    if (currentTick == ASCEND_END + 1) {
      world.playSound(skyPoint, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 0.5f);
    }
    if (currentTick == CHARGE_END - 2) {
      world.playSound(skyPoint, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 0.6f);
    }
  }

  /** Descent phase — the star rockets down from the sky toward the impact point. */
  private void tickDescent(World world) {
    double t = (double) (currentTick - CHARGE_END) / (IMPACT_TICK - CHARGE_END);
    double eased = t * t * t;
    if (star != null && !star.isDead()) {
      Location pos = lerp(skyPoint, impactCenter.clone().add(0, 0.3, 0), eased);
      star.teleport(pos);
      star.setTransformation(centeredCube(0.4f));
      world.spawnParticle(
          Particle.DUST, pos, 4, 0.08, 0.08, 0.08, 0, new Particle.DustOptions(GOLD_DUST, 1.6f));
      world.spawnParticle(Particle.FLAME, pos, 2, 0.05, 0.05, 0.05, 0.01);
    }
    tickOrbit();
    if (currentTick == CHARGE_END + 1) {
      world.playSound(skyPoint, Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.8f, 1.2f);
    }
  }

  /**
   * Keeps the current pickaxe slowly orbiting the impact point through cast/ascend/charge/descent.
   */
  private void tickOrbit() {
    if (orbitingPickaxe == null || orbitingPickaxe.isDead()) return;
    Location pos =
        pickaxeOrbitCenter
            .clone()
            .add(
                Math.cos(orbitAngle) * ORBIT_RADIUS,
                Math.sin(orbitAngle * 1.5) * 0.2,
                Math.sin(orbitAngle) * ORBIT_RADIUS);
    orbitingPickaxe.teleport(pos);
  }

  /**
   * The comet detonates at the impact point in a shockwave — particles and sound only, no blocks
   * are harmed.
   */
  private void triggerImpact(World world) {
    if (star != null && !star.isDead()) star.remove();
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) orbitingPickaxe.remove();

    world.playSound(impactCenter, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);
    world.playSound(impactCenter, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
    world.playSound(impactCenter, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.6f);
    world.playSound(impactCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.35f, 1.6f);

    player.spawnParticle(Particle.EXPLOSION_EMITTER, impactCenter, 2, 0.3, 0.1, 0.3, 0);
    player.spawnParticle(Particle.SONIC_BOOM, impactCenter, 1, 0, 0, 0, 0);
    player.spawnParticle(
        Particle.DUST,
        impactCenter,
        120,
        0.7,
        0.5,
        0.7,
        0,
        new Particle.DustOptions(GOLD_DUST, 2.6f));
    player.spawnParticle(
        Particle.DUST,
        impactCenter,
        70,
        0.7,
        0.5,
        0.7,
        0,
        new Particle.DustOptions(winningRarity.particleColor(), 2.2f));
    player.spawnParticle(Particle.CLOUD, impactCenter, 40, 0.6, 0.3, 0.6, 0.05);

    // Expanding shockwave ring along the ground.
    for (int i = 0; i < 24; i++) {
      double a = (2 * Math.PI / 24) * i;
      Location p = impactCenter.clone().add(Math.cos(a) * 2.2, 0.1, Math.sin(a) * 2.2);
      world.spawnParticle(Particle.CRIT, p, 1, 0, 0, 0, 0);
    }

    spawnItems(world);
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, rising out of the impact. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            impactCenter.clone(),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(pick);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0f));
            });
    player.showEntity(plugin, exitPickaxeDisplay);

    ItemStack stonePreview = EvolutionStone.create(winningRarity, stoneEnchantName);
    exitStoneDisplay =
        world.spawn(
            impactCenter.clone(),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(stonePreview);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0f));
            });
    player.showEntity(plugin, exitStoneDisplay);
  }

  /** Emerge phase — the evolved pickaxe and stone rise out of the impact site and scale up. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - IMPACT_TICK) / (EMERGE_END - IMPACT_TICK);
    double eased = 1 - Math.pow(1 - t, 2);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(impactCenter, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(impactCenter, exitStoneFinalPos, eased));
      scaleItem(exitStoneDisplay, (float) eased * 0.55f);
    }
    if (currentTick % 3 == 0) {
      world.spawnParticle(
          Particle.DUST,
          exitPickaxeFinalPos,
          4,
          0.3,
          0.3,
          0.3,
          0,
          new Particle.DustOptions(winningRarity.particleColor(), 1.6f));
      world.spawnParticle(Particle.END_ROD, exitPickaxeFinalPos, 2, 0.2, 0.2, 0.2, 0.02);
    }
  }

  /** Spawns the evolution title above the emerging items. */
  private void spawnEvolutionTitle(World world) {
    String raw = nextTier.displayName.trim();
    String tierName = (raw.startsWith("&#") && raw.length() >= 8) ? raw.substring(8).trim() : raw;
    TextColor tc = tierTextColor(nextTier);
    Component comp =
        Component.text()
            .append(
                Component.text("☄  EVOLUTION " + nextTier.level)
                    .color(TextColor.color(0xFFD24C))
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false))
            .append(Component.newline())
            .append(
                Component.text(tierName.toUpperCase())
                    .color(tc)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false))
            .build();
    evolutionTitle =
        world.spawn(
            titlePos,
            TextDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.text(comp);
              e.setAlignment(TextDisplay.TextAlignment.CENTER);
              e.setBillboard(Display.Billboard.CENTER);
              e.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
              e.setShadowed(true);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setTransformation(textTransform(0.8f));
            });
    player.showEntity(plugin, evolutionTitle);
    world.playSound(impactCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
  }

  /** Float phase — items hover gently with a rarity-coloured halo of particles. */
  private void tickFloat(World world) {
    float dy = (float) (Math.sin(currentTick * 0.15) * 0.07);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(exitPickaxeFinalPos.clone().add(0, dy, 0));
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(exitStoneFinalPos.clone().add(0, -dy, 0));
    }
    if (currentTick % 3 == 0) {
      double rx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
      double ry = ThreadLocalRandom.current().nextDouble() * 2.2;
      double rz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
      player.spawnParticle(Particle.GLOW, impactCenter.clone().add(rx, ry, rz), 1, 0, 0, 0, 0);
    }
    if (currentTick % 5 == 0) {
      player.spawnParticle(
          Particle.DUST,
          exitPickaxeFinalPos.clone().add(0, dy, 0),
          2,
          0.12,
          0.12,
          0.12,
          0,
          new Particle.DustOptions(winningRarity.particleColor(), 1.3f));
    }
  }

  /** Return phase — items and title sweep back to the player. */
  private void tickReturn() {
    double t = (double) (currentTick - FLOAT_END) / (RETURN_END - FLOAT_END);
    double eased = t * t;
    Location chest = player.getLocation().add(0, 1.3, 0);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(exitPickaxeFinalPos, chest, eased));
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(exitStoneFinalPos, chest, eased));
    }
    if (evolutionTitle != null && !evolutionTitle.isDead()) {
      evolutionTitle.teleport(lerp(titlePos, chest, eased));
    }
  }

  /** Deliver items. */
  @SuppressWarnings("deprecation")
  private void deliverItems(World world) {
    if (itemsReturned) return;
    itemsReturned = true;
    if (heldPickaxe != null && heldPickaxe.getType() != Material.AIR && player.isOnline()) {
      heldPickaxe.setType(nextTier.material);
      ItemStack cur = player.getInventory().getItem(heldSlot);
      if (cur == null || cur.getType() == Material.AIR) {
        player.getInventory().setItem(heldSlot, heldPickaxe);
      } else if (player.getInventory().firstEmpty() >= 0) {
        player.getInventory().addItem(heldPickaxe);
      } else {
        player.getWorld().dropItemNaturally(player.getLocation(), heldPickaxe);
      }
    }
    if (!player.isOnline()) {
      removeAllDisplays();
      return;
    }
    ItemStack stone = EvolutionStone.create(winningRarity, stoneEnchantName);
    var overflow = player.getInventory().addItem(stone);
    overflow.values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
    world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
    world.playSound(player.getLocation(), "custom.vacuumsuckshort", 0.5f, 1.2f);
    removeAllDisplays();
    if (onComplete != null) onComplete.run();
  }

  /** Releases resources held by this component. */
  private void cleanup() {
    if (finished) return;
    finished = true;
    removeAllDisplays();
    if (!callbackFired) {
      callbackFired = true;
      callback.run();
    }
    if (!itemsReturned) deliverItems(player.getWorld());
  }

  /** Remove all display entities. */
  private void removeAllDisplays() {
    if (star != null && !star.isDead()) star.remove();
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) orbitingPickaxe.remove();
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) exitPickaxeDisplay.remove();
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) exitStoneDisplay.remove();
    if (evolutionTitle != null && !evolutionTitle.isDead()) evolutionTitle.remove();
  }

  /** Scale item. */
  private static void scaleItem(ItemDisplay d, float scale) {
    if (d == null || d.isDead()) return;
    d.setTransformation(itemTransform(scale));
  }

  /** Lerp. */
  private static Location lerp(Location a, Location b, double t) {
    return a.toVector().multiply(1 - t).add(b.toVector().multiply(t)).toLocation(a.getWorld());
  }

  /** Centered cube transform for the falling star's BlockDisplay. */
  private static Transformation centeredCube(float scale) {
    float half = scale / 2f;
    return new Transformation(
        new Vector3f(-half, -half, -half),
        new AxisAngle4f(),
        new Vector3f(scale, scale, scale),
        new AxisAngle4f());
  }

  /** Item transform. */
  private static Transformation itemTransform(float scale) {
    return new Transformation(
        new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
  }

  /** Text transform. */
  private static Transformation textTransform(float scale) {
    return new Transformation(
        new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
  }

  /** Tier text color. */
  private static TextColor tierTextColor(EvolutionTier tier) {
    String d = tier.displayName.trim();
    if (d.startsWith("&#") && d.length() >= 8) {
      TextColor tc = TextColor.fromHexString("#" + d.substring(2, 8));
      if (tc != null) return tc;
    }
    return TextColor.color(0xCCCCCC);
  }

  /**
   * Picks a random enchant display name from the player's unlocked enchants, or all enchants if
   * none unlocked.
   */
  private static String pickRandomEnchant(Player player) {
    PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc != null) {
      Pickaxe profile = pdc.getPickaxeForPlayer(player);
      if (profile != null && !profile.enchants.items.isEmpty()) {
        List<String> names =
            profile.enchants.items.stream()
                .map(e -> EnchantAnimationConfig.getDisplayName(e.name))
                .filter(n -> n != null && !n.isEmpty())
                .toList();
        if (!names.isEmpty()) return names.get(ThreadLocalRandom.current().nextInt(names.size()));
      }
    }
    List<String> all = EnchantAnimationConfig.getEnchantOrder();
    if (all.isEmpty()) return "";
    return all.get(ThreadLocalRandom.current().nextInt(all.size()));
  }
}
