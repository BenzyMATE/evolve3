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
 * Evolution animation variant — "Storm Vortex".
 *
 * <p>After the shared atom cast, dark storm clouds gather and begin circling the pickaxe in a
 * tightening cyclone. Real lightning strikes down through the vortex (visual-only, harmless) as the
 * storm builds toward its peak, charging the pickaxe with crackling static. On the final and
 * biggest strike the vortex collapses down into the pickaxe, and the evolved pickaxe and its reward
 * stone are left crackling with electricity.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Gather ( 16– 60) Storm clouds swirl into a tightening vortex above the pickaxe.
 *   <li>Strikes ( 60–100) Three lightning strikes hit the vortex, each bigger than the last.
 *   <li>Collapse (100) The vortex collapses down onto the pickaxe in a final flash.
 *   <li>Emerge (100–128) The evolved pickaxe and stone rise, crackling with static.
 *   <li>Float (128–160) Items hover, sparking occasionally.
 *   <li>Return (160–182) Items sweep back to the player.
 * </ol>
 */
public class EvolutionStormVortexAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int GATHER_END = 60;
  private static final int STRIKE_1_TICK = 68;
  private static final int STRIKE_2_TICK = 84;
  private static final int STRIKE_3_TICK = 100;
  private static final int COLLAPSE_TICK = 100;
  private static final int CALLBACK_TICK = 100;
  private static final int EMERGE_END = 128;
  private static final int FLOAT_END = 160;
  private static final int RETURN_END = 182;
  private static final int TOTAL = 185;

  private static final double VORTEX_RADIUS = 2.8;
  private static final double VORTEX_HEIGHT = 3.2;
  private static final Color STORM_GREY = Color.fromRGB(90, 95, 105);
  private static final Color SPARK_BLUE = Color.fromRGB(160, 210, 255);

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
  private Location vortexTop;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private ItemDisplay chargingPickaxe;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  /**
   * Creates the EvolutionStormVortexAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionStormVortexAnimation(
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
    center = player.getLocation().clone().add(forward.clone().multiply(3.2));
    vortexTop = center.clone().add(0, VORTEX_HEIGHT, 0);
    exitPickaxeFinalPos = center.clone().add(-0.5, 1.2, 0);
    exitStoneFinalPos = center.clone().add(0.6, 1.2, 0);
    titlePos = center.clone().add(0, VORTEX_HEIGHT + 0.6, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    chargingPickaxe =
        world.spawn(
            center.clone().add(0, 1.0, 0),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(heldPickaxe);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.85f));
            });
    player.showEntity(plugin, chargingPickaxe);

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

    if (currentTick <= GATHER_END) {
      tickGather(world);
    } else if (currentTick < COLLAPSE_TICK) {
      tickStrikePhase(world);
    }

    if (currentTick == STRIKE_1_TICK) strikeLightning(world, 0.5f);
    if (currentTick == STRIKE_2_TICK) strikeLightning(world, 0.8f);
    if (currentTick == STRIKE_3_TICK) strikeLightning(world, 1.0f);

    if (currentTick == COLLAPSE_TICK) {
      triggerCollapse(world);
    }
    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > COLLAPSE_TICK && currentTick <= EMERGE_END) {
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

  /** Storm clouds swirl into a tightening cyclone above the pickaxe. */
  private void tickGather(World world) {
    double t = (double) currentTick / GATHER_END;
    double radius = VORTEX_RADIUS * (1 - t * 0.3);
    double angle = currentTick * (0.15 + t * 0.5);
    for (int layer = 0; layer < 3; layer++) {
      double height = 0.6 + layer * (VORTEX_HEIGHT - 0.6) / 2;
      double a = angle + layer * 1.3;
      Location p = center.clone().add(Math.cos(a) * radius, height, Math.sin(a) * radius);
      world.spawnParticle(Particle.CLOUD, p, 1, 0.15, 0.1, 0.15, 0.01);
      if (layer == 1) {
        world.spawnParticle(
            Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(STORM_GREY, 1.6f));
      }
    }
    if (currentTick % 6 == 0) {
      world.playSound(center, Sound.WEATHER_RAIN_ABOVE, 0.35f, 0.7f + (float) t * 0.4f);
    }
    if (currentTick % 14 == 0) {
      world.playSound(center, Sound.ENTITY_PHANTOM_FLAP, 0.3f, 0.5f);
    }
  }

  /** Static builds around the vortex between lightning strikes. */
  private void tickStrikePhase(World world) {
    double angle = currentTick * 0.6;
    double radius = VORTEX_RADIUS * 0.65;
    for (int i = 0; i < 2; i++) {
      double a = angle + i * Math.PI;
      Location p = center.clone().add(Math.cos(a) * radius, 1.5, Math.sin(a) * radius);
      world.spawnParticle(Particle.CLOUD, p, 2, 0.2, 0.3, 0.2, 0.01);
    }
    if (chargingPickaxe != null && !chargingPickaxe.isDead() && currentTick % 3 == 0) {
      world.spawnParticle(
          Particle.ELECTRIC_SPARK, center.clone().add(0, 1.0, 0), 2, 0.15, 0.15, 0.15, 0.02);
    }
  }

  /**
   * Strikes real (visual-only, no damage/fire) lightning down through the vortex, growing in
   * intensity, and crackles the pickaxe with static.
   */
  private void strikeLightning(World world, float intensity) {
    world.strikeLightningEffect(vortexTop.clone());
    world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f + intensity * 0.4f, 1.0f);
    world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f + intensity * 0.3f, 1.2f);

    player.spawnParticle(
        Particle.ELECTRIC_SPARK,
        center.clone().add(0, 1.5, 0),
        (int) (20 * intensity),
        0.3,
        1.2,
        0.3,
        0.08);
    player.spawnParticle(
        Particle.DUST,
        center.clone().add(0, 1.0, 0),
        (int) (25 * intensity),
        0.3,
        0.8,
        0.3,
        0,
        new Particle.DustOptions(SPARK_BLUE, 1.8f));

    if (chargingPickaxe != null && !chargingPickaxe.isDead()) {
      float scale = 0.85f + 0.15f * intensity;
      chargingPickaxe.setTransformation(itemTransform(scale));
    }
  }

  /** The vortex collapses down onto the pickaxe in a final flash of light. */
  private void triggerCollapse(World world) {
    if (chargingPickaxe != null && !chargingPickaxe.isDead()) chargingPickaxe.remove();

    world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 0.8f);
    world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.1f);

    player.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);
    player.spawnParticle(
        Particle.ELECTRIC_SPARK, center.clone().add(0, 1.0, 0), 60, 0.4, 0.6, 0.4, 0.1);
    player.spawnParticle(
        Particle.DUST,
        center.clone().add(0, 1.0, 0),
        70,
        0.4,
        0.6,
        0.4,
        0,
        new Particle.DustOptions(SPARK_BLUE, 2.2f));
    player.spawnParticle(
        Particle.DUST,
        center.clone().add(0, 1.0, 0),
        50,
        0.4,
        0.6,
        0.4,
        0,
        new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    player.spawnParticle(Particle.FLASH, center.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);

    spawnItems(world);
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the collapse point. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    Location spawnAt = center.clone().add(0, 1.0, 0);
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            spawnAt,
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
            spawnAt,
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

  /** Emerge phase — items rise from the collapse point, crackling with static. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - COLLAPSE_TICK) / (EMERGE_END - COLLAPSE_TICK);
    double eased = 1 - Math.pow(1 - t, 2);
    Location from = center.clone().add(0, 1.0, 0);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(from, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(from, exitStoneFinalPos, eased));
      scaleItem(exitStoneDisplay, (float) eased * 0.55f);
    }
    if (currentTick % 4 == 0) {
      world.spawnParticle(Particle.ELECTRIC_SPARK, exitPickaxeFinalPos, 3, 0.15, 0.15, 0.15, 0.02);
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
                Component.text("⚡  EVOLUTION " + nextTier.level)
                    .color(TextColor.color(0xA0D2FF))
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

  /** Float phase — items hover, sparking occasionally with static electricity. */
  private void tickFloat(World world) {
    float dy = (float) (Math.sin(currentTick * 0.15) * 0.07);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(exitPickaxeFinalPos.clone().add(0, dy, 0));
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(exitStoneFinalPos.clone().add(0, -dy, 0));
    }
    if (currentTick % 9 == 0) {
      world.spawnParticle(
          Particle.ELECTRIC_SPARK,
          exitPickaxeFinalPos.clone().add(0, dy, 0),
          3,
          0.15,
          0.15,
          0.15,
          0.02);
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
    if (chargingPickaxe != null && !chargingPickaxe.isDead()) chargingPickaxe.remove();
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
