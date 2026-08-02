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
 * Evolution animation variant — "Phoenix Rebirth".
 *
 * <p>After the shared atom cast, the pickaxe combusts — wreathed in climbing flame as it burns down
 * to ash. From the embers a phoenix of fire particles bursts upward and screams skyward, and as the
 * ash scatters away on the wind, the evolved pickaxe and its reward stone are left behind, still
 * smouldering with the last of the fire.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Combustion ( 16– 60) The pickaxe ignites and burns, wreathed in climbing flame.
 *   <li>Ash ( 60– 76) The pickaxe crumbles to ash and embers as the fire consumes it.
 *   <li>Phoenix Rise ( 76) A phoenix of fire erupts upward with a cry.
 *   <li>Emerge ( 76–110) Ash scatters away; the evolved pickaxe and stone rise from the embers.
 *   <li>Float (110–148) Items hover, still smouldering.
 *   <li>Return (148–172) Items sweep back to the player.
 * </ol>
 */
public class EvolutionPhoenixRebirthAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int COMBUST_END = 60;
  private static final int ASH_END = 76;
  private static final int RISE_TICK = 76;
  private static final int CALLBACK_TICK = 76;
  private static final int EMERGE_END = 110;
  private static final int FLOAT_END = 148;
  private static final int RETURN_END = 172;
  private static final int TOTAL = 175;

  private static final Color FLAME_ORANGE = Color.fromRGB(255, 120, 20);
  private static final Color FLAME_RED = Color.fromRGB(220, 40, 10);
  private static final Color EMBER_GOLD = Color.fromRGB(255, 200, 90);

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

  private Location pyrePoint;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private ItemDisplay burningPickaxe;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  /**
   * Creates the EvolutionPhoenixRebirthAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionPhoenixRebirthAnimation(
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
    pyrePoint = player.getLocation().clone().add(forward.clone().multiply(3.2)).add(0, 0.2, 0);
    exitPickaxeFinalPos = pyrePoint.clone().add(-0.5, 1.4, 0);
    exitStoneFinalPos = pyrePoint.clone().add(0.6, 1.4, 0);
    titlePos = pyrePoint.clone().add(0, 2.7, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    burningPickaxe =
        world.spawn(
            pyrePoint.clone().add(0, 0.6, 0),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(heldPickaxe);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.9f));
            });
    player.showEntity(plugin, burningPickaxe);

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

    if (currentTick <= COMBUST_END) {
      tickCombustion(world);
    } else if (currentTick < ASH_END) {
      tickAsh(world);
    }

    if (currentTick == RISE_TICK) {
      triggerPhoenixRise(world);
    }
    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > RISE_TICK && currentTick <= EMERGE_END) {
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

  /** Flame climbs and thickens around the pickaxe as it burns. */
  private void tickCombustion(World world) {
    double t = (double) currentTick / COMBUST_END;
    int flames = 2 + (int) (t * 4);
    for (int i = 0; i < flames; i++) {
      double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
      double radius = 0.15 + ThreadLocalRandom.current().nextDouble() * 0.25;
      double height = ThreadLocalRandom.current().nextDouble() * (0.4 + t * 0.6);
      Location p =
          pyrePoint.clone().add(Math.cos(angle) * radius, 0.4 + height, Math.sin(angle) * radius);
      world.spawnParticle(Particle.FLAME, p, 1, 0, 0.02, 0, 0.0);
    }
    if (currentTick % 2 == 0) {
      world.spawnParticle(Particle.LAVA, pyrePoint.clone().add(0, 0.5, 0), 1, 0.15, 0.1, 0.15, 0);
    }
    if (currentTick % 4 == 0) {
      world.spawnParticle(
          Particle.SMOKE, pyrePoint.clone().add(0, 1.0 + t, 0), 2, 0.1, 0.2, 0.1, 0.01);
    }
    if (currentTick % 6 == 0) {
      world.playSound(pyrePoint, Sound.ITEM_FIRECHARGE_USE, 0.35f, 0.7f + (float) t * 0.5f);
    }
    if (currentTick % 10 == 0) {
      world.playSound(pyrePoint, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.0f);
    }
  }

  /** The pickaxe crumbles to ash and embers as the fire finishes consuming it. */
  private void tickAsh(World world) {
    double t = (double) (currentTick - COMBUST_END) / (ASH_END - COMBUST_END);
    if (burningPickaxe != null && !burningPickaxe.isDead()) {
      float scale = Math.max(0.02f, 0.9f * (float) (1 - t));
      burningPickaxe.setTransformation(itemTransform(scale));
    }
    world.spawnParticle(Particle.ASH, pyrePoint.clone().add(0, 0.6, 0), 3, 0.25, 0.3, 0.25, 0.01);
    world.spawnParticle(
        Particle.DUST,
        pyrePoint.clone().add(0, 0.6, 0),
        2,
        0.2,
        0.2,
        0.2,
        0,
        new Particle.DustOptions(EMBER_GOLD, 1.5f));
    if (currentTick % 3 == 0) {
      world.playSound(pyrePoint, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.4f);
    }
  }

  /** A phoenix of fire erupts skyward from the embers with a cry. */
  private void triggerPhoenixRise(World world) {
    if (burningPickaxe != null && !burningPickaxe.isDead()) burningPickaxe.remove();

    world.playSound(pyrePoint, Sound.ENTITY_GHAST_SHOOT, 0.7f, 0.6f);
    world.playSound(pyrePoint, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.7f);
    world.playSound(pyrePoint, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.5f);
    world.playSound(pyrePoint, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

    // Wing arcs sweeping outward and up, like a bird bursting from the pyre.
    for (int wing = -1; wing <= 1; wing += 2) {
      for (int i = 0; i < 16; i++) {
        double frac = i / 16.0;
        double spread = frac * 1.6;
        double height = Math.sin(frac * Math.PI) * 1.8;
        Location p = pyrePoint.clone().add(wing * spread, 0.6 + height, 0);
        world.spawnParticle(
            Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(FLAME_ORANGE, 1.8f));
      }
    }
    player.spawnParticle(Particle.FLAME, pyrePoint.clone().add(0, 1.5, 0), 60, 0.4, 1.2, 0.4, 0.05);
    player.spawnParticle(
        Particle.DUST,
        pyrePoint.clone().add(0, 1.0, 0),
        70,
        0.4,
        0.9,
        0.4,
        0,
        new Particle.DustOptions(FLAME_RED, 2.2f));
    player.spawnParticle(
        Particle.DUST,
        pyrePoint.clone().add(0, 1.0, 0),
        50,
        0.4,
        0.9,
        0.4,
        0,
        new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    player.spawnParticle(Particle.FLASH, pyrePoint.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);

    spawnItems(world);
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the embers. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            pyrePoint.clone().add(0, 1.0, 0),
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
            pyrePoint.clone().add(0, 1.0, 0),
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

  /** Emerge phase — ash scatters away and the items rise from the embers. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - RISE_TICK) / (EMERGE_END - RISE_TICK);
    double eased = 1 - Math.pow(1 - t, 2);
    Location from = pyrePoint.clone().add(0, 1.0, 0);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(from, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(from, exitStoneFinalPos, eased));
      scaleItem(exitStoneDisplay, (float) eased * 0.55f);
    }
    if (currentTick % 3 == 0) {
      world.spawnParticle(Particle.ASH, pyrePoint.clone(), 4, 0.4, 0.15, 0.4, 0.02);
    }
    if (currentTick % 4 == 0) {
      world.spawnParticle(
          Particle.DUST,
          exitPickaxeFinalPos,
          3,
          0.25,
          0.25,
          0.25,
          0,
          new Particle.DustOptions(EMBER_GOLD, 1.4f));
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
                Component.text("🔥  EVOLUTION " + nextTier.level)
                    .color(TextColor.color(0xFF7A20))
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
    world.playSound(pyrePoint, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
  }

  /** Float phase — items hover, still smouldering with drifting embers. */
  private void tickFloat(World world) {
    float dy = (float) (Math.sin(currentTick * 0.15) * 0.07);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(exitPickaxeFinalPos.clone().add(0, dy, 0));
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(exitStoneFinalPos.clone().add(0, -dy, 0));
    }
    if (currentTick % 4 == 0) {
      double rx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.4;
      double ry = ThreadLocalRandom.current().nextDouble() * 1.6;
      double rz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.4;
      world.spawnParticle(
          Particle.DUST,
          pyrePoint.clone().add(rx, 1.0 + ry, rz),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(FLAME_ORANGE, 1.0f));
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
    if (burningPickaxe != null && !burningPickaxe.isDead()) burningPickaxe.remove();
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
