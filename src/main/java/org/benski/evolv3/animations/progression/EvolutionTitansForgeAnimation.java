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
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Evolution animation variant — "Titan's Forge".
 *
 * <p>After the shared atom cast, a spectral anvil rises out of the ground and the current pickaxe
 * settles onto it. A massive ghostly hammer swings down from above three times, each strike heavier
 * than the last, sparks showering further each time. The final blow shatters the anvil entirely —
 * out of the embers, the evolved pickaxe and its reward stone rise, still glowing hot.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Anvil Rise ( 16– 34) Spectral anvil materialises; pickaxe settles onto it.
 *   <li>Strike 1 ( 34– 54) Hammer swings down — first, modest blow.
 *   <li>Strike 2 ( 54– 74) Second, harder blow.
 *   <li>Strike 3 ( 74– 94) Final, shattering blow at 94 — anvil breaks apart.
 *   <li>Emerge ( 94–122) Evolved pickaxe and stone rise from the embers, still glowing.
 *   <li>Float (122–156) Items hover with a rarity-coloured halo.
 *   <li>Return (156–178) Items sweep back to the player.
 * </ol>
 */
public class EvolutionTitansForgeAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int ANVIL_END = 34;
  private static final int STRIKE_1_TICK = 54;
  private static final int STRIKE_2_TICK = 74;
  private static final int STRIKE_3_TICK = 94;
  private static final int CALLBACK_TICK = 94;
  private static final int EMERGE_END = 122;
  private static final int FLOAT_END = 156;
  private static final int RETURN_END = 178;
  private static final int TOTAL = 181;

  private static final double HAMMER_HIGH = 6.0;

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

  private Location anvilPos;
  private Location hammerRestPos;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private BlockDisplay anvilDisplay;
  private BlockDisplay hammerDisplay;
  private ItemDisplay pickaxeOnAnvil;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  private int strikesLanded = 0;

  /**
   * Creates the EvolutionTitansForgeAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionTitansForgeAnimation(
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
    anvilPos = player.getLocation().clone().add(forward.clone().multiply(3.2));
    hammerRestPos = anvilPos.clone().add(0, HAMMER_HIGH, 0);
    exitPickaxeFinalPos = anvilPos.clone().add(-0.5, 2.4, 0);
    exitStoneFinalPos = anvilPos.clone().add(0.6, 2.4, 0);
    titlePos = anvilPos.clone().add(0, 3.4, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    anvilDisplay =
        world.spawn(
            anvilPos.clone().add(0, -1.2, 0),
            BlockDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setBlock(Material.ANVIL.createBlockData());
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setTransformation(anvilTransform(0.001f));
            });
    player.showEntity(plugin, anvilDisplay);

    hammerDisplay =
        world.spawn(
            hammerRestPos,
            BlockDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setBlock(Material.NETHERITE_BLOCK.createBlockData());
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setTransformation(hammerTransform());
            });
    player.showEntity(plugin, hammerDisplay);

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

    if (currentTick <= ANVIL_END) {
      tickAnvilRise(world);
    } else if (currentTick <= STRIKE_1_TICK) {
      tickStrikeWindup(world, ANVIL_END, STRIKE_1_TICK, 1);
    } else if (currentTick <= STRIKE_2_TICK) {
      tickStrikeWindup(world, STRIKE_1_TICK, STRIKE_2_TICK, 2);
    } else if (currentTick <= STRIKE_3_TICK) {
      tickStrikeWindup(world, STRIKE_2_TICK, STRIKE_3_TICK, 3);
    }

    if (currentTick == STRIKE_1_TICK && strikesLanded < 1) {
      landStrike(world, 1, 0.6f, false);
    }
    if (currentTick == STRIKE_2_TICK && strikesLanded < 2) {
      landStrike(world, 2, 1.0f, false);
    }
    if (currentTick == STRIKE_3_TICK && strikesLanded < 3) {
      landStrike(world, 3, 1.6f, true);
    }

    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > STRIKE_3_TICK && currentTick <= EMERGE_END) {
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

  /** The anvil rises out of the ground and the current pickaxe settles onto it. */
  private void tickAnvilRise(World world) {
    double t = (double) currentTick / ANVIL_END;
    double eased = 1 - Math.pow(1 - t, 3);
    if (anvilDisplay != null && !anvilDisplay.isDead()) {
      anvilDisplay.teleport(anvilPos.clone().add(0, -1.2 + 1.2 * eased, 0));
      anvilDisplay.setTransformation(anvilTransform((float) eased));
    }
    if (t >= 0.5 && pickaxeOnAnvil == null) {
      pickaxeOnAnvil =
          world.spawn(
              anvilPos.clone().add(0, 0.55, 0),
              ItemDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setItemStack(heldPickaxe);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setTransformation(itemTransform(0f));
              });
      player.showEntity(plugin, pickaxeOnAnvil);
    }
    if (pickaxeOnAnvil != null && !pickaxeOnAnvil.isDead()) {
      double pt = Math.max(0, (t - 0.5) / 0.5);
      scaleItem(pickaxeOnAnvil, 0.7f * (float) pt);
    }
    if (currentTick % 3 == 0) {
      world.spawnParticle(
          Particle.DUST,
          anvilPos.clone().add(0, eased * 0.6, 0),
          3,
          0.25,
          0.15,
          0.25,
          0,
          new Particle.DustOptions(EvolutionCastEffects.ATOM_GOLD, 1.3f));
    }
    if (currentTick == 8) {
      world.playSound(anvilPos, Sound.BLOCK_ANVIL_PLACE, 0.7f, 0.6f);
    }
  }

  /** The hammer winds up above the anvil before its next strike. */
  private void tickStrikeWindup(World world, int start, int end, int strikeNumber) {
    double t = (double) (currentTick - start) / (end - start);
    if (hammerDisplay == null || hammerDisplay.isDead()) return;

    if (t < 0.6) {
      // Raise higher each successive strike for escalating drama.
      double raiseT = t / 0.6;
      double height = HAMMER_HIGH * (0.5 + 0.2 * strikeNumber) * raiseT;
      hammerDisplay.teleport(anvilPos.clone().add(0, height, 0));
    } else {
      // Fast final plunge.
      double plungeT = (t - 0.6) / 0.4;
      double eased = Math.pow(plungeT, 3);
      double startHeight = HAMMER_HIGH * (0.5 + 0.2 * strikeNumber);
      double height = startHeight * (1 - eased);
      hammerDisplay.teleport(anvilPos.clone().add(0, height + 0.6, 0));
      if (currentTick % 2 == 0) {
        world.spawnParticle(Particle.CRIT, hammerDisplay.getLocation(), 3, 0.1, 0.1, 0.1, 0);
      }
    }
  }

  /** A hammer strike lands — sparks, sound, and an escalating anvil glow (or shatter). */
  private void landStrike(World world, int strikeNumber, float intensity, boolean shatter) {
    strikesLanded = strikeNumber;

    world.playSound(
        anvilPos, Sound.BLOCK_ANVIL_LAND, Math.min(1.2f, 0.6f + intensity * 0.3f), 0.7f);
    world.playSound(anvilPos, Sound.BLOCK_ANVIL_USE, 0.8f, 0.9f + strikeNumber * 0.1f);

    for (int i = 0; i < (int) (18 * intensity); i++) {
      double a = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
      double speed = 0.15 + ThreadLocalRandom.current().nextDouble() * 0.2 * intensity;
      Location spark = anvilPos.clone().add(0, 0.6, 0);
      world.spawnParticle(
          Particle.DUST,
          spark,
          1,
          Math.cos(a) * speed,
          0.15 + ThreadLocalRandom.current().nextDouble() * 0.2,
          Math.sin(a) * speed,
          0.05,
          new Particle.DustOptions(EvolutionCastEffects.ATOM_GOLD, 1.4f));
    }
    world.spawnParticle(
        Particle.CRIT, anvilPos.clone().add(0, 0.6, 0), (int) (20 * intensity), 0.4, 0.2, 0.4, 0.1);

    if (shatter) {
      world.playSound(anvilPos, Sound.BLOCK_ANVIL_DESTROY, 1.0f, 0.8f);
      world.playSound(anvilPos, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
      player.spawnParticle(
          Particle.EXPLOSION_EMITTER, anvilPos.clone().add(0, 0.6, 0), 1, 0, 0, 0, 0);
      player.spawnParticle(
          Particle.DUST,
          anvilPos.clone().add(0, 0.6, 0),
          100,
          0.5,
          0.4,
          0.5,
          0,
          new Particle.DustOptions(EvolutionCastEffects.ATOM_GOLD, 2.4f));
      player.spawnParticle(
          Particle.DUST,
          anvilPos.clone().add(0, 0.6, 0),
          60,
          0.5,
          0.4,
          0.5,
          0,
          new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
      if (anvilDisplay != null && !anvilDisplay.isDead()) anvilDisplay.remove();
      if (hammerDisplay != null && !hammerDisplay.isDead()) hammerDisplay.remove();
      if (pickaxeOnAnvil != null && !pickaxeOnAnvil.isDead()) pickaxeOnAnvil.remove();
      spawnItems(world);
    } else if (anvilDisplay != null && !anvilDisplay.isDead()) {
      // Each non-final strike brightens the anvil's glow to build anticipation.
      anvilDisplay.setBrightness(new Display.Brightness(15, 15));
    }
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the anvil's remains. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            anvilPos.clone().add(0, 0.6, 0),
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
            anvilPos.clone().add(0, 0.6, 0),
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

  /** Emerge phase — the evolved items rise from the embers, still glowing hot. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - STRIKE_3_TICK) / (EMERGE_END - STRIKE_3_TICK);
    double eased = 1 - Math.pow(1 - t, 2);
    Location base = anvilPos.clone().add(0, 0.6, 0);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(base, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(base, exitStoneFinalPos, eased));
      scaleItem(exitStoneDisplay, (float) eased * 0.55f);
    }
    if (currentTick % 3 == 0) {
      // Cooling embers — fades from bright gold toward the rarity colour as it "cools".
      Color emberColor = t < 0.5 ? EvolutionCastEffects.ATOM_GOLD : winningRarity.particleColor();
      world.spawnParticle(
          Particle.DUST,
          exitPickaxeFinalPos,
          4,
          0.3,
          0.3,
          0.3,
          0,
          new Particle.DustOptions(emberColor, 1.6f));
      world.spawnParticle(Particle.SMOKE, exitPickaxeFinalPos, 2, 0.15, 0.15, 0.15, 0.01);
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
                Component.text("⚒  EVOLUTION " + nextTier.level)
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
    world.playSound(anvilPos, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
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
      player.spawnParticle(Particle.GLOW, anvilPos.clone().add(rx, ry, rz), 1, 0, 0, 0, 0);
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
    if (anvilDisplay != null && !anvilDisplay.isDead()) anvilDisplay.remove();
    if (hammerDisplay != null && !hammerDisplay.isDead()) hammerDisplay.remove();
    if (pickaxeOnAnvil != null && !pickaxeOnAnvil.isDead()) pickaxeOnAnvil.remove();
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

  /** Centred, uniformly-scaled anvil block transform. */
  private static Transformation anvilTransform(float scale) {
    float half = scale / 2f;
    return new Transformation(
        new Vector3f(-half, 0, -half),
        new AxisAngle4f(),
        new Vector3f(scale, scale, scale),
        new AxisAngle4f());
  }

  /** Flattened, wide hammer-head shaped block transform. */
  private static Transformation hammerTransform() {
    float w = 1.3f;
    float h = 0.55f;
    return new Transformation(
        new Vector3f(-w / 2f, -h / 2f, -w / 2f),
        new AxisAngle4f(),
        new Vector3f(w, h, w),
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
