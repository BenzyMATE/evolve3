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
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Evolution animation variant — "Starlight Coronation".
 *
 * <p>The calm, majestic counterpart to the other epic styles. After the shared atom cast, a
 * rotating halo of light grows above the impact point and a beam descends from its centre. The
 * current pickaxe fades away as the evolved pickaxe and its reward stone rise slowly through the
 * beam, bathed in light, while smaller rings pulse outward like ripples. The halo then contracts
 * into a single bright flash — the "coronation" — before a serene drift back to the player.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Halo Form ( 16– 50) A rotating ring of light grows above the impact point.
 *   <li>Beam Descend ( 50– 70) A beam of light reaches down from the halo to the ground.
 *   <li>Coronation ( 70–100) The old pickaxe fades as the evolved pickaxe and stone rise slowly
 *       through the beam; ripple rings pulse outward.
 *   <li>Flash (100–108) The halo contracts to nothing in a bright flash; title appears.
 *   <li>Float (108–145) Items hover serenely with starlight.
 *   <li>Return (145–168) Items drift back to the player.
 * </ol>
 */
public class EvolutionStarlightCoronationAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int HALO_END = 50;
  private static final int BEAM_END = 70;
  private static final int CORONATION_END = 100;
  private static final int CALLBACK_TICK = 85;
  private static final int FLASH_END = 108;
  private static final int FLOAT_END = 145;
  private static final int RETURN_END = 168;
  private static final int TOTAL = 171;

  private static final double HALO_HEIGHT = 3.6;
  private static final double HALO_MAX_RADIUS = 1.8;

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

  private Location groundPoint;
  private Location haloCenter;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private ItemDisplay oldPickaxeDisplay;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  private double haloRotation = 0;

  /**
   * Creates the EvolutionStarlightCoronationAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionStarlightCoronationAnimation(
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

    org.bukkit.util.Vector forward = eye.getDirection().setY(0).normalize();
    groundPoint = player.getLocation().clone().add(forward.clone().multiply(3.2));
    haloCenter = groundPoint.clone().add(0, HALO_HEIGHT, 0);
    exitPickaxeFinalPos = groundPoint.clone().add(-0.5, 1.8, 0);
    exitStoneFinalPos = groundPoint.clone().add(0.6, 1.8, 0);
    titlePos = groundPoint.clone().add(0, 4.4, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    oldPickaxeDisplay =
        world.spawn(
            groundPoint.clone().add(0, 1.2, 0),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(heldPickaxe);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.75f));
            });
    player.showEntity(plugin, oldPickaxeDisplay);

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

    haloRotation += 0.07;

    if (currentTick <= HALO_END) {
      tickHaloForm(world);
    } else if (currentTick <= BEAM_END) {
      tickBeamDescend(world);
    } else if (currentTick <= CORONATION_END) {
      tickCoronation(world);
    } else if (currentTick <= FLASH_END) {
      tickFlash(world);
    }

    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }
    if (currentTick == FLASH_END) {
      spawnEvolutionTitle(world);
    }
    if (currentTick > FLASH_END && currentTick <= FLOAT_END) {
      tickFloat(world);
    }
    if (currentTick > FLOAT_END && currentTick <= RETURN_END) {
      tickReturn();
    }
    if (currentTick == RETURN_END) {
      deliverItems(world);
    }
  }

  /** A rotating halo of light grows above the impact point. */
  private void tickHaloForm(World world) {
    double t = (double) currentTick / HALO_END;
    double radius = HALO_MAX_RADIUS * (1 - Math.pow(1 - t, 2));
    spawnRing(world, haloCenter, radius, haloRotation, 14, EvolutionCastEffects.ATOM_GOLD);
    if (currentTick % 6 == 0) {
      world.playSound(haloCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.3f + (float) t * 0.4f);
    }
  }

  /** A beam of light descends from the halo's centre down to the ground. */
  private void tickBeamDescend(World world) {
    double t = (double) (currentTick - HALO_END) / (BEAM_END - HALO_END);
    double beamBottom = haloCenter.getY() - HALO_HEIGHT * t;
    int steps = 10;
    for (int i = 0; i < steps; i++) {
      double frac = (double) i / steps;
      double y = haloCenter.getY() - (haloCenter.getY() - beamBottom) * frac;
      if (y < groundPoint.getY()) continue;
      Location p = groundPoint.clone();
      p.setY(y);
      world.spawnParticle(
          Particle.DUST,
          p,
          1,
          0.05,
          0,
          0.05,
          0,
          new Particle.DustOptions(EvolutionCastEffects.ATOM_GOLD, 1.2f));
    }
    spawnRing(world, haloCenter, HALO_MAX_RADIUS, haloRotation, 10, EvolutionCastEffects.ATOM_GOLD);
    if (currentTick == HALO_END + 1) {
      world.playSound(haloCenter, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
    }
  }

  /**
   * The old pickaxe fades away as the evolved pickaxe and stone rise slowly through the beam,
   * ripple rings pulsing outward.
   */
  private void tickCoronation(World world) {
    double t = (double) (currentTick - BEAM_END) / (CORONATION_END - BEAM_END);

    if (oldPickaxeDisplay != null && !oldPickaxeDisplay.isDead()) {
      float scale = Math.max(0.001f, 0.75f * (float) (1 - t));
      scaleItem(oldPickaxeDisplay, scale);
      if (t >= 1.0) {
        oldPickaxeDisplay.remove();
      }
    }

    if (exitPickaxeDisplay == null && t > 0.1) {
      spawnItems(world);
    }
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      double riseT = Math.min(1.0, (t - 0.1) / 0.9);
      exitPickaxeDisplay.teleport(
          lerp(groundPoint.clone().add(0, 0.4, 0), exitPickaxeFinalPos, riseT));
      scaleItem(exitPickaxeDisplay, (float) riseT * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      double riseT = Math.min(1.0, (t - 0.1) / 0.9);
      exitStoneDisplay.teleport(lerp(groundPoint.clone().add(0, 0.4, 0), exitStoneFinalPos, riseT));
      scaleItem(exitStoneDisplay, (float) riseT * 0.55f);
    }

    spawnRing(world, haloCenter, HALO_MAX_RADIUS, haloRotation, 10, EvolutionCastEffects.ATOM_GOLD);
    if (currentTick % 8 == 0) {
      // Ripple ring pulsing outward from the rising items.
      double rippleRadius = ((currentTick - BEAM_END) % 24) / 24.0 * 2.5;
      spawnRing(
          world,
          groundPoint.clone().add(0, 1.2, 0),
          rippleRadius,
          0,
          16,
          winningRarity.particleColor());
    }
  }

  /** The halo above contracts rapidly to nothing in a bright flash. */
  private void tickFlash(World world) {
    double t = (double) (currentTick - CORONATION_END) / (FLASH_END - CORONATION_END);
    double radius = HALO_MAX_RADIUS * (1 - t);
    spawnRing(world, haloCenter, radius, haloRotation * 3, 16, EvolutionCastEffects.ATOM_GOLD);
    if (currentTick == FLASH_END) {
      world.playSound(haloCenter, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
      world.playSound(haloCenter, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
      player.spawnParticle(Particle.FLASH, haloCenter, 1, 0, 0, 0, 0);
      player.spawnParticle(Particle.END_ROD, haloCenter, 50, 0.4, 0.4, 0.4, 0.15);
      player.spawnParticle(
          Particle.DUST,
          haloCenter,
          60,
          0.4,
          0.4,
          0.4,
          0,
          new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    }
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the ground point. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            groundPoint.clone().add(0, 0.4, 0),
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
            groundPoint.clone().add(0, 0.4, 0),
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

  /** Spawns the evolution title above the crowned items. */
  private void spawnEvolutionTitle(World world) {
    String raw = nextTier.displayName.trim();
    String tierName = (raw.startsWith("&#") && raw.length() >= 8) ? raw.substring(8).trim() : raw;
    TextColor tc = tierTextColor(nextTier);
    Component comp =
        Component.text()
            .append(
                Component.text("✦  EVOLUTION " + nextTier.level)
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
    world.playSound(haloCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
  }

  /** Float phase — items hover serenely with drifting starlight. */
  private void tickFloat(World world) {
    float dy = (float) (Math.sin(currentTick * 0.12) * 0.06);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(exitPickaxeFinalPos.clone().add(0, dy, 0));
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(exitStoneFinalPos.clone().add(0, -dy, 0));
    }
    if (currentTick % 4 == 0) {
      double rx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
      double ry = ThreadLocalRandom.current().nextDouble() * 2.2;
      double rz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
      player.spawnParticle(Particle.GLOW, groundPoint.clone().add(rx, ry + 1, rz), 1, 0, 0, 0, 0);
    }
    if (currentTick % 6 == 0) {
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

  /** Return phase — items and title drift back to the player. */
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
    if (oldPickaxeDisplay != null && !oldPickaxeDisplay.isDead()) oldPickaxeDisplay.remove();
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) exitPickaxeDisplay.remove();
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) exitStoneDisplay.remove();
    if (evolutionTitle != null && !evolutionTitle.isDead()) evolutionTitle.remove();
  }

  /** Spawns a horizontal ring of dust particles at the given radius and rotation offset. */
  private void spawnRing(
      World world, Location center, double radius, double rotation, int count, Color color) {
    for (int i = 0; i < count; i++) {
      double a = rotation + (2 * Math.PI / count) * i;
      Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
      world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.3f));
    }
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
