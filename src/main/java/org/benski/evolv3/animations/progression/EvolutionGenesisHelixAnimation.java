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
 * Evolution animation variant — "Genesis Helix".
 *
 * <p>Literal take on "evolution": after the shared atom cast, the atoms don't converge on a single
 * point — they twine upward as two counter-rotating strands, a double helix unwinding in front of
 * the player. The strands spin faster and faster until they splice together into a single column of
 * light, which bursts and leaves the evolved pickaxe and its reward stone behind.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Helix ( 16– 78) Two strands spiral upward around the current pickaxe, rotating faster as
 *       they climb.
 *   <li>Splice ( 78– 90) Strands accelerate and converge into a single column; bursts at 90.
 *   <li>Emerge ( 90–118) Evolved pickaxe and stone rise out of the splice point.
 *   <li>Float (118–152) Items hover with a rarity-coloured halo.
 *   <li>Return (152–174) Items sweep back to the player.
 * </ol>
 */
public class EvolutionGenesisHelixAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int HELIX_END = 78;
  private static final int SPLICE_TICK = 90;
  private static final int CALLBACK_TICK = 90;
  private static final int EMERGE_END = 118;
  private static final int FLOAT_END = 152;
  private static final int RETURN_END = 174;
  private static final int TOTAL = 177;

  private static final double HELIX_RADIUS = 0.9;
  private static final double HELIX_HEIGHT = 4.2;
  private static final double HELIX_TURNS = 2.5;

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

  private Location helixBase;
  private Location splicePoint;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private ItemDisplay orbitingPickaxe;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  /**
   * Creates the EvolutionGenesisHelixAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionGenesisHelixAnimation(
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
    helixBase = player.getLocation().clone().add(forward.clone().multiply(3.2));
    splicePoint = helixBase.clone().add(0, HELIX_HEIGHT + 0.6, 0);
    exitPickaxeFinalPos = splicePoint.clone().add(-0.5, 0, 0);
    exitStoneFinalPos = splicePoint.clone().add(0.6, 0, 0);
    titlePos = splicePoint.clone().add(0, 1.3, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    orbitingPickaxe =
        world.spawn(
            helixBase.clone().add(0, 0.4, 0),
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

    if (currentTick <= HELIX_END) {
      tickHelix(world);
    } else if (currentTick < SPLICE_TICK) {
      tickSplice(world);
    }

    if (currentTick == SPLICE_TICK) {
      triggerSplice(world);
    }
    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > SPLICE_TICK && currentTick <= EMERGE_END) {
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

  /**
   * Two counter-rotating strands of gold particles spiral upward around the pickaxe, rotating
   * faster the higher they climb.
   */
  private void tickHelix(World world) {
    double t = (double) currentTick / HELIX_END;
    double eased = t * t;
    double height = HELIX_HEIGHT * t;
    double angle = eased * HELIX_TURNS * 2 * Math.PI * 2.2;

    for (int strand = 0; strand < 2; strand++) {
      double a = angle + strand * Math.PI;
      Color c = strand == 0 ? EvolutionCastEffects.ATOM_GOLD : EvolutionCastEffects.DEEP_GOLD;
      for (int trail = 0; trail < 3; trail++) {
        double trailA = a - trail * 0.25;
        double trailH = height - trail * 0.08;
        Location p =
            helixBase
                .clone()
                .add(Math.cos(trailA) * HELIX_RADIUS, trailH, Math.sin(trailA) * HELIX_RADIUS);
        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.3f));
      }
    }
    if (currentTick % 4 == 0) {
      world.spawnParticle(Particle.END_ROD, helixBase.clone().add(0, height, 0), 1, 0, 0, 0, 0.01);
    }
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) {
      orbitingPickaxe.teleport(helixBase.clone().add(0, 0.4 + height * 0.3, 0));
    }
    if (currentTick % 10 == 0) {
      world.playSound(helixBase, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.2f + (float) t * 0.6f);
    }
  }

  /** Strands accelerate hard toward the splice point just before converging. */
  private void tickSplice(World world) {
    double t = (double) (currentTick - HELIX_END) / (SPLICE_TICK - HELIX_END);
    double angle = currentTick * (1.4 + t * 2.0);
    double radius = HELIX_RADIUS * (1 - t);
    for (int strand = 0; strand < 2; strand++) {
      double a = angle + strand * Math.PI;
      Color c = strand == 0 ? EvolutionCastEffects.ATOM_GOLD : EvolutionCastEffects.DEEP_GOLD;
      Location p =
          helixBase.clone().add(Math.cos(a) * radius, HELIX_HEIGHT + t * 0.6, Math.sin(a) * radius);
      world.spawnParticle(Particle.DUST, p, 2, 0, 0, 0, 0, new Particle.DustOptions(c, 1.5f));
    }
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) {
      orbitingPickaxe.teleport(lerp(orbitingPickaxe.getLocation(), splicePoint, 0.15));
      float scale = Math.max(0.001f, 0.75f * (float) (1 - t));
      orbitingPickaxe.setTransformation(itemTransform(scale));
    }
  }

  /** The splice moment — strands collide into a column and burst. */
  private void triggerSplice(World world) {
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) orbitingPickaxe.remove();

    world.playSound(splicePoint, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.6f);
    world.playSound(splicePoint, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.1f);
    world.playSound(splicePoint, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.6f, 1.4f);

    player.spawnParticle(Particle.END_ROD, splicePoint, 70, 0.3, 0.5, 0.3, 0.2);
    player.spawnParticle(
        Particle.DUST,
        splicePoint,
        90,
        0.4,
        0.6,
        0.4,
        0,
        new Particle.DustOptions(EvolutionCastEffects.ATOM_GOLD, 2.4f));
    player.spawnParticle(
        Particle.DUST,
        splicePoint,
        60,
        0.4,
        0.6,
        0.4,
        0,
        new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    player.spawnParticle(Particle.FLASH, splicePoint, 1, 0, 0, 0, 0);

    spawnItems(world);
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the splice point. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            splicePoint.clone(),
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
            splicePoint.clone(),
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

  /** Emerge phase — items rise out of the splice point and scale up. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - SPLICE_TICK) / (EMERGE_END - SPLICE_TICK);
    double eased = 1 - Math.pow(1 - t, 2);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(splicePoint, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(splicePoint, exitStoneFinalPos, eased));
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
                Component.text("⛭  EVOLUTION " + nextTier.level)
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
    world.playSound(splicePoint, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
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
      player.spawnParticle(Particle.GLOW, splicePoint.clone().add(rx, ry, rz), 1, 0, 0, 0, 0);
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
