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
 * Evolution animation variant — "Void Collapse".
 *
 * <p>After the shared atom cast, the pickaxe is dragged into a shrinking singularity — a spiral of
 * dark matter that pulls tighter and tighter around it until it vanishes into a single point of
 * absolute black. The point then erupts, tearing the void back open and hurling the evolved pickaxe
 * and its reward stone outward into the light.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Inflow ( 16– 80) Dark matter spirals inward, pickaxe is dragged toward the center and
 *       shrinks.
 *   <li>Singularity ( 80– 92) The point holds at near-nothing, straining, before it gives way.
 *   <li>Erupt ( 92) Violent outward burst; evolved pickaxe and stone are born from the point.
 *   <li>Emerge ( 92–120) Items fly outward from the eruption and settle.
 *   <li>Float (120–154) Items hover, trailing wisps of the void.
 *   <li>Return (154–176) Items sweep back to the player.
 * </ol>
 */
public class EvolutionVoidCollapseAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int INFLOW_END = 80;
  private static final int SINGULARITY_END = 92;
  private static final int ERUPT_TICK = 92;
  private static final int CALLBACK_TICK = 92;
  private static final int EMERGE_END = 120;
  private static final int FLOAT_END = 154;
  private static final int RETURN_END = 176;
  private static final int TOTAL = 179;

  private static final double INFLOW_RADIUS = 3.0;
  private static final Color VOID_PURPLE = Color.fromRGB(90, 10, 140);
  private static final Color VOID_BLACK = Color.fromRGB(18, 4, 28);

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
   * Creates the EvolutionVoidCollapseAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionVoidCollapseAnimation(
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
    center = player.getLocation().clone().add(forward.clone().multiply(3.2)).add(0, 1.6, 0);
    exitPickaxeFinalPos = center.clone().add(-0.5, 0, 0);
    exitStoneFinalPos = center.clone().add(0.6, 0, 0);
    titlePos = center.clone().add(0, 1.3, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    orbitingPickaxe =
        world.spawn(
            center.clone().add(INFLOW_RADIUS, 0, 0),
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

    if (currentTick <= INFLOW_END) {
      tickInflow(world);
    } else if (currentTick < SINGULARITY_END) {
      tickSingularity(world);
    }

    if (currentTick == ERUPT_TICK) {
      triggerErupt(world);
    }
    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > ERUPT_TICK && currentTick <= EMERGE_END) {
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

  /** Dark matter spirals inward, dragging the pickaxe with it as the radius shrinks. */
  private void tickInflow(World world) {
    double t = (double) currentTick / INFLOW_END;
    double eased = t * t * t;
    double radius = INFLOW_RADIUS * (1 - eased);
    double angle = currentTick * (0.35 + eased * 1.6);

    for (int arm = 0; arm < 3; arm++) {
      double a = angle + arm * (2 * Math.PI / 3);
      Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
      world.spawnParticle(
          Particle.DUST, p, 1, 0.03, 0.03, 0.03, 0, new Particle.DustOptions(VOID_PURPLE, 1.4f));
      if (arm == 0) {
        world.spawnParticle(Particle.SQUID_INK, p, 1, 0.02, 0.02, 0.02, 0);
      }
    }
    if (currentTick % 5 == 0) {
      world.spawnParticle(Particle.PORTAL, center, 4, radius * 0.4, 0.3, radius * 0.4, 0.3);
    }
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) {
      double a = angle;
      orbitingPickaxe.teleport(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
      float scale = Math.max(0.02f, 0.75f * (float) (1 - eased));
      orbitingPickaxe.setTransformation(itemTransform(scale));
    }
    if (currentTick % 8 == 0) {
      float pitch = 0.6f + (float) eased * 0.9f;
      world.playSound(center, Sound.ENTITY_ENDERMAN_STARE, 0.35f, pitch);
    }
    if (currentTick % 12 == 0) {
      world.playSound(center, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.4f, 0.7f);
    }
  }

  /** The point strains at near-nothing before it gives way. */
  private void tickSingularity(World world) {
    double t = (double) (currentTick - INFLOW_END) / (SINGULARITY_END - INFLOW_END);
    if (orbitingPickaxe != null && !orbitingPickaxe.isDead()) {
      orbitingPickaxe.remove();
    }
    world.spawnParticle(
        Particle.DUST, center, 3, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(VOID_BLACK, 2.0f));
    if (currentTick % 3 == 0) {
      world.spawnParticle(Particle.SQUID_INK, center, 2, 0.06, 0.06, 0.06, 0);
    }
    world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.5f + (float) t * 0.4f, 0.4f);
  }

  /** The singularity erupts, tearing back open in a burst of light. */
  private void triggerErupt(World world) {
    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
    world.playSound(center, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.7f, 1.6f);
    world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.1f);

    player.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
    player.spawnParticle(Particle.END_ROD, center, 60, 0.3, 0.4, 0.3, 0.25);
    player.spawnParticle(
        Particle.DUST, center, 80, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(VOID_PURPLE, 2.2f));
    player.spawnParticle(
        Particle.DUST,
        center,
        60,
        0.4,
        0.5,
        0.4,
        0,
        new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    player.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);

    spawnItems(world);
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the eruption point. */
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

  /** Emerge phase — items fly outward from the eruption point and settle. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - ERUPT_TICK) / (EMERGE_END - ERUPT_TICK);
    double eased = 1 - Math.pow(1 - t, 3);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(center, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(center, exitStoneFinalPos, eased));
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
                Component.text("⚫  EVOLUTION " + nextTier.level)
                    .color(TextColor.color(0xB35CE8))
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

  /** Float phase — items hover, trailing thin wisps of the void. */
  private void tickFloat(World world) {
    float dy = (float) (Math.sin(currentTick * 0.15) * 0.07);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(exitPickaxeFinalPos.clone().add(0, dy, 0));
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(exitStoneFinalPos.clone().add(0, -dy, 0));
    }
    if (currentTick % 4 == 0) {
      double rx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.2;
      double ry = ThreadLocalRandom.current().nextDouble() * 1.6;
      double rz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.2;
      world.spawnParticle(
          Particle.DUST,
          center.clone().add(rx, ry, rz),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(VOID_PURPLE, 1.1f));
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
