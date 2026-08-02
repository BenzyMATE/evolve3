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
 * Evolution animation variant — "Elemental Convergence".
 *
 * <p>After the shared atom cast, four elemental pillars — fire, water, earth and air — erupt from
 * the four cardinal points around the pickaxe. They spiral inward, their colours bleeding into one
 * another, faster and faster, until they collide at the centre and fuse into a single blinding
 * burst of white-gold light. The evolved pickaxe and its reward stone are left behind, with traces
 * of all four elements swirling briefly around them before settling into the reward's own colour.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Pillars ( 16– 46) Four elemental pillars rise at the cardinal points.
 *   <li>Convergence ( 46– 84) The pillars spiral inward toward the centre, accelerating.
 *   <li>Fusion ( 84) The elements collide and fuse in a burst of white-gold light.
 *   <li>Emerge ( 84–114) The evolved pickaxe and stone rise from the fusion point.
 *   <li>Float (114–148) Items hover, traced briefly by fading elemental wisps.
 *   <li>Return (148–170) Items sweep back to the player.
 * </ol>
 */
public class EvolutionElementalConvergenceAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int PILLAR_END = 46;
  private static final int CONVERGE_END = 84;
  private static final int FUSION_TICK = 84;
  private static final int CALLBACK_TICK = 84;
  private static final int EMERGE_END = 114;
  private static final int FLOAT_END = 148;
  private static final int RETURN_END = 170;
  private static final int TOTAL = 173;

  private static final double PILLAR_RADIUS = 3.4;
  private static final double PILLAR_HEIGHT = 2.6;

  private static final Color FIRE_COLOR = Color.fromRGB(255, 90, 20);
  private static final Color WATER_COLOR = Color.fromRGB(40, 130, 240);
  private static final Color EARTH_COLOR = Color.fromRGB(120, 80, 40);
  private static final Color AIR_COLOR = Color.fromRGB(230, 230, 240);
  private static final Color FUSION_COLOR = Color.fromRGB(255, 235, 160);

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

  private Location center;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private ItemDisplay orbitingPickaxe;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  /**
   * Creates the EvolutionElementalConvergenceAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionElementalConvergenceAnimation(
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

  @Override
  public boolean isCallbackFired() {
    return callbackFired;
  }

  @Override
  public boolean isFinished() {
    return finished;
  }

  @Override
  public ItemStack getHeldPickaxe() {
    return heldPickaxe;
  }

  @Override
  public void setOnComplete(Runnable r) {
    this.onComplete = r;
  }

  @Override
  public void stop() {
    if (finished) return;
    finished = true;
    removeAllDisplays();
  }

  @Override
  public void start() {
    Location eye = player.getEyeLocation();
    World world = eye.getWorld();
    if (world == null) return;

    Vector forward = eye.getDirection().setY(0).normalize();
    center = player.getLocation().clone().add(forward.clone().multiply(3.2)).add(0, 1.2, 0);
    exitPickaxeFinalPos = center.clone().add(-0.5, 0, 0);
    exitStoneFinalPos = center.clone().add(0.6, 0, 0);
    titlePos = center.clone().add(0, 1.3, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    orbitingPickaxe =
        world.spawn(
            center.clone(),
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

    new BukkitRunnable() {
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

    if (currentTick <= PILLAR_END) {
      tickPillars(world);
    } else if (currentTick < CONVERGE_END) {
      tickConverge(world);
    }

    if (currentTick == FUSION_TICK) {
      triggerFusion(world);
    }
    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > FUSION_TICK && currentTick <= EMERGE_END) {
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

  /** Four elemental pillars rise at the cardinal points around the pickaxe. */
  private void tickPillars(World world) {
    double t = (double) currentTick / PILLAR_END;
    double height = PILLAR_HEIGHT * t;
    for (int cardinal = 0; cardinal < 4; cardinal++) {
      double angle = cardinal * (Math.PI / 2);
      Location base =
          center
              .clone()
              .add(Math.cos(angle) * PILLAR_RADIUS, -1.2, Math.sin(angle) * PILLAR_RADIUS);
      Particle particle = elementParticle(cardinal);
      Color color = elementColor(cardinal);
      for (int i = 0; i < 3; i++) {
        double h = height * (0.3 + i * 0.35);
        Location p = base.clone().add(0, h, 0);
        if (particle == Particle.DUST) {
          world.spawnParticle(
              particle, p, 1, 0.08, 0.05, 0.08, 0, new Particle.DustOptions(color, 1.3f));
        } else {
          world.spawnParticle(particle, p, 1, 0.08, 0.05, 0.08, 0.01);
        }
      }
    }
    if (currentTick % 8 == 0) {
      world.playSound(center, Sound.AMBIENT_CAVE, 0.4f, 1.0f);
    }
  }

  /** The pillars spiral inward toward the centre, accelerating and blending colours. */
  private void tickConverge(World world) {
    double t = (double) (currentTick - PILLAR_END) / (CONVERGE_END - PILLAR_END);
    double eased = t * t;
    double radius = PILLAR_RADIUS * (1 - eased);
    double angle = currentTick * (0.2 + eased * 1.8);
    for (int cardinal = 0; cardinal < 4; cardinal++) {
      double a = angle + cardinal * (Math.PI / 2);
      double h = -1.2 + (0.9 + 1.2) * eased;
      Location p = center.clone().add(Math.cos(a) * radius, h, Math.sin(a) * radius);
      Particle particle = elementParticle(cardinal);
      Color color = elementColor(cardinal);
      if (particle == Particle.DUST) {
        world.spawnParticle(particle, p, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.4f));
      } else {
        world.spawnParticle(particle, p, 1, 0.03, 0.03, 0.03, 0.01);
      }
    }
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) {
      float scale = Math.max(0.02f, 0.75f * (float) (1 - eased));
      orbitingPickaxe.setTransformation(itemTransform(scale));
    }
    if (currentTick % 10 == 0) {
      world.playSound(center, Sound.BLOCK_CONDUIT_AMBIENT, 0.4f, 1.0f + (float) t * 0.6f);
    }
  }

  /** The elements collide and fuse into a single blinding burst. */
  private void triggerFusion(World world) {
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) orbitingPickaxe.remove();

    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
    world.playSound(center, Sound.ITEM_TOTEM_USE, 0.7f, 1.2f);
    world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.1f);

    player.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
    player.spawnParticle(
        Particle.DUST, center, 70, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(FUSION_COLOR, 2.3f));
    player.spawnParticle(Particle.END_ROD, center, 50, 0.35, 0.5, 0.35, 0.2);
    player.spawnParticle(
        Particle.DUST,
        center,
        50,
        0.4,
        0.5,
        0.4,
        0,
        new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    player.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);

    spawnItems(world);
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the fusion point. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            center.clone(),
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
            center.clone(),
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

  /** Emerge phase — items rise out of the fusion point and settle. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - FUSION_TICK) / (EMERGE_END - FUSION_TICK);
    double eased = 1 - Math.pow(1 - t, 2);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(center, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(center, exitStoneFinalPos, eased));
      scaleItem(exitStoneDisplay, (float) eased * 0.55f);
    }
    if (currentTick <= FUSION_TICK + 14) {
      // Traces of the four elements still swirl briefly before fading.
      int cardinal = currentTick % 4;
      double a = currentTick * 0.5;
      Location p = exitPickaxeFinalPos.clone().add(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6);
      Particle particle = elementParticle(cardinal);
      Color color = elementColor(cardinal);
      if (particle == Particle.DUST) {
        world.spawnParticle(particle, p, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0f));
      } else {
        world.spawnParticle(particle, p, 1, 0.02, 0.02, 0.02, 0.005);
      }
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
                Component.text("⚛  EVOLUTION " + nextTier.level)
                    .color(TextColor.color(0xFFE8A0))
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
    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
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
      player.spawnParticle(Particle.GLOW, center.clone().add(rx, ry, rz), 1, 0, 0, 0, 0);
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
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) orbitingPickaxe.remove();
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) exitPickaxeDisplay.remove();
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) exitStoneDisplay.remove();
    if (evolutionTitle != null && !evolutionTitle.isDead()) evolutionTitle.remove();
  }

  /**
   * Returns the particle type representing the given cardinal's element (0=fire, 1=water, 2=earth,
   * 3=air).
   */
  private static Particle elementParticle(int cardinal) {
    return switch (cardinal) {
      case 0 -> Particle.FLAME;
      case 1 -> Particle.DRIPPING_WATER;
      case 2 -> Particle.DUST;
      default -> Particle.CLOUD;
    };
  }

  /** Returns the colour representing the given cardinal's element, used only for DUST particles. */
  private static Color elementColor(int cardinal) {
    return switch (cardinal) {
      case 0 -> FIRE_COLOR;
      case 1 -> WATER_COLOR;
      case 2 -> EARTH_COLOR;
      default -> AIR_COLOR;
    };
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
