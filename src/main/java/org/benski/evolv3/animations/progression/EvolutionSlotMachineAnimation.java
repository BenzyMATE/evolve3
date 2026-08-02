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
 * Evolution animation variant — "Jackpot Reels".
 *
 * <p>After the shared gold atom cast, twin portals tear open — one above, one below — and the
 * pickaxe lifts out of the player's hand, floating up into the top portal and vanishing inside it.
 * Between the two portals, three reels of spinning stones flicker through every rarity like a slot
 * machine, each reel locking into place in turn until all three show the same colour down the
 * middle: a win. The top row of stones is sucked up into the top portal, the bottom row is sucked
 * down into the bottom portal, and the two stones flanking the centre fizzle away — leaving only
 * the winning stone, whose name is revealed. The portals then release the pickaxe, now evolved,
 * back out of the bottom portal to rest beside its prize, and both fly back to the player.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Portals Open ( 16– 36) Twin portals swirl open above and below.
 *   <li>Ascend ( 36– 58) The held pickaxe floats up into the top portal and is sucked in.
 *   <li>Spin ( 58–106) Three reels of stones spin, locking one by one.
 *   <li>Reveal (106) The middle row matches — a win. Tier change applied here.
 *   <li>Suck (106–128) Top row sucked up, bottom row sucked down, side stones fizzle out.
 *   <li>Stone Reveal (130) The surviving stone's name is revealed.
 *   <li>Descend (130–158) The evolved pickaxe emerges from the bottom portal; portals collapse.
 *   <li>Float (158–188) Both items hover.
 *   <li>Return (188–208) Both items sweep back to the player.
 * </ol>
 */
public class EvolutionSlotMachineAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int PORTAL_OPEN_END = 36;
  private static final int ASCEND_END = 58;
  private static final int COL1_STOP = 82;
  private static final int COL2_STOP = 94;
  private static final int COL3_STOP = 106;
  private static final int REVEAL_TICK = 106;
  private static final int CALLBACK_TICK = 106;
  private static final int SUCK_END = 128;
  private static final int STONE_REVEAL_TICK = 130;
  private static final int DESCEND_END = 158;
  private static final int EMERGE_END = 158;
  private static final int FLOAT_END = 188;
  private static final int RETURN_END = 208;
  private static final int TOTAL = 211;

  private static final double PORTAL_RADIUS = 1.3;
  private static final double TOP_OFFSET = 3.0;
  private static final double BOTTOM_OFFSET = -2.4;
  private static final double[] COLUMN_OFFSETS = {-0.9, 0.0, 0.9};
  private static final double[] ROW_OFFSETS = {0.7, 0.0, -0.7};

  private static final Color PORTAL_VOID = Color.fromRGB(20, 14, 4);

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

  private Vector forwardDir;
  private Vector rightDir;
  private Location machineCenter;
  private Location topPortal;
  private Location bottomPortal;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location stoneNamePos;
  private Location titlePos;

  private ItemDisplay ascendingPickaxe;
  private final ItemDisplay[][] reels = new ItemDisplay[3][3];
  private final boolean[] columnLocked = new boolean[3];
  private ItemDisplay centerStoneDisplay;
  private ItemDisplay emergingPickaxe;
  private TextDisplay stoneNameText;
  private TextDisplay evolutionTitle;

  private boolean reelsSpawned = false;
  private boolean emergingSpawned = false;

  /**
   * Creates the EvolutionSlotMachineAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionSlotMachineAnimation(
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

    forwardDir = eye.getDirection().setY(0).normalize();
    rightDir = new Vector(forwardDir.getZ(), 0, -forwardDir.getX()).normalize();

    machineCenter =
        player.getLocation().clone().add(forwardDir.clone().multiply(3.2)).add(0, 1.2, 0);
    topPortal = machineCenter.clone().add(0, TOP_OFFSET, 0);
    bottomPortal = machineCenter.clone().add(0, BOTTOM_OFFSET, 0);
    exitPickaxeFinalPos = offset(machineCenter, -0.6, 0);
    exitStoneFinalPos = offset(machineCenter, 0.6, 0);
    stoneNamePos = machineCenter.clone().add(0, 0.9, 0);
    titlePos = machineCenter.clone().add(0, 1.6, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    ascendingPickaxe =
        world.spawn(
            player.getLocation().clone().add(0, 1.1, 0),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(heldPickaxe);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.85f));
            });
    player.showEntity(plugin, ascendingPickaxe);

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

  /** Returns a location offset from {@code base} along {@code rightDir} and vertically. */
  private Location offset(Location base, double sideways, double up) {
    return base.clone().add(rightDir.clone().multiply(sideways)).add(0, up, 0);
  }

  /** Ticks this component. */
  private void tick() {
    World world = player.getWorld();
    if (world == null) return;

    if (currentTick <= PORTAL_OPEN_END) {
      tickPortalOpen(world);
    } else if (currentTick <= DESCEND_END) {
      tickPortalAmbience(world);
    }

    if (currentTick > PORTAL_OPEN_END && currentTick <= ASCEND_END) {
      tickAscend(world);
    }
    if (currentTick == ASCEND_END) {
      triggerPickaxeSuckedIn(world);
    }

    if (currentTick > ASCEND_END && currentTick <= COL3_STOP) {
      tickReelSpin(world);
    }
    if (currentTick == REVEAL_TICK) {
      triggerReveal(world);
    }
    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > REVEAL_TICK && currentTick <= SUCK_END) {
      tickSuck(world);
    }
    if (currentTick == STONE_REVEAL_TICK) {
      spawnStoneNameText(world);
    }

    if (currentTick > STONE_REVEAL_TICK && currentTick <= DESCEND_END) {
      tickDescend(world);
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

  /** Twin portals swirl open, one above and one below the machine's centre. */
  private void tickPortalOpen(World world) {
    double t = (double) currentTick / PORTAL_OPEN_END;
    double radius = PORTAL_RADIUS * t;
    double angle = currentTick * 0.9;
    drawPortalRing(world, topPortal, radius, angle);
    drawPortalRing(world, bottomPortal, radius, -angle);
    if (currentTick == 4) {
      world.playSound(machineCenter, "custom.portalopenrift", 0.55f, 0.9f);
      world.playSound(machineCenter, "custom.portalopenrift", 0.55f, 1.3f);
    }
  }

  /** Keeps both portals shimmering steadily while the machine does its work. */
  private void tickPortalAmbience(World world) {
    if (currentTick % 3 == 0) {
      drawPortalRing(world, topPortal, PORTAL_RADIUS, currentTick * 0.5);
      drawPortalRing(world, bottomPortal, PORTAL_RADIUS, -currentTick * 0.5);
    }
  }

  /** Draws a single ring of portal particles at the given radius and rotation. */
  private void drawPortalRing(World world, Location center, double radius, double angle) {
    if (radius <= 0.01) return;
    int points = 10;
    for (int i = 0; i < points; i++) {
      double a = angle + (i / (double) points) * 2 * Math.PI;
      Location p = discPoint(center, radius, a);
      world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0.02, 0.02, 0.02, 0.01);
      if (i % 2 == 0) {
        world.spawnParticle(
            Particle.DUST,
            p,
            1,
            0,
            0,
            0,
            0,
            new Particle.DustOptions(EvolutionCastEffects.ATOM_GOLD, 1.2f));
      }
    }
    Location core = discPoint(center, radius * 0.4, -angle * 1.3);
    world.spawnParticle(
        Particle.DUST, core, 2, 0.06, 0.06, 0.06, 0, new Particle.DustOptions(PORTAL_VOID, 1.6f));
    world.spawnParticle(
        Particle.GLOW, discPoint(center, radius * 0.7, angle * 0.6), 1, 0.05, 0.05, 0.05, 0);
  }

  /** Returns a point on a horizontal disc centred at {@code center}, facing up/down. */
  private Location discPoint(Location center, double radius, double angle) {
    return center
        .clone()
        .add(forwardDir.clone().multiply(Math.cos(angle) * radius))
        .add(rightDir.clone().multiply(Math.sin(angle) * radius));
  }

  /** The held pickaxe floats up and forward from the player, into the top portal. */
  private void tickAscend(World world) {
    double t = (double) (currentTick - PORTAL_OPEN_END) / (ASCEND_END - PORTAL_OPEN_END);
    double eased = t * t;
    Location start = player.getLocation().clone().add(0, 1.1, 0);
    if (ascendingPickaxe != null && !ascendingPickaxe.isDead()) {
      ascendingPickaxe.teleport(lerp(start, topPortal, eased));
      float scale = Math.max(0.05f, 0.85f * (float) (1 - eased * 0.6));
      ascendingPickaxe.setTransformation(itemTransform(scale));
    }
    if (currentTick % 6 == 0) {
      world.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.3f, 1.3f);
    }
  }

  /** The pickaxe vanishes into the top portal with a vacuum burst, and the reels power on. */
  private void triggerPickaxeSuckedIn(World world) {
    if (ascendingPickaxe != null && !ascendingPickaxe.isDead()) ascendingPickaxe.remove();
    world.playSound(topPortal, "custom.vacuumsuckshort", 0.5f, 1.3f);
    world.spawnParticle(Particle.REVERSE_PORTAL, topPortal, 30, 0.3, 0.2, 0.3, 0.3);
    spawnReels(world);
  }

  /** Spawns the 3x3 grid of spinning reel stones between the two portals. */
  private void spawnReels(World world) {
    if (reelsSpawned) return;
    reelsSpawned = true;
    List<RarityConfig> allRarities = EnchantAnimationConfig.getRarities();
    for (int col = 0; col < 3; col++) {
      for (int row = 0; row < 3; row++) {
        Location pos = offset(machineCenter, COLUMN_OFFSETS[col], ROW_OFFSETS[row]);
        RarityConfig rarity = randomRarity(allRarities);
        ItemStack stack = EvolutionStone.create(rarity, stoneEnchantName);
        final int r = row;
        reels[col][row] =
            world.spawn(
                pos,
                ItemDisplay.class,
                e -> {
                  e.setVisibleByDefault(false);
                  e.setItemStack(stack);
                  e.setBrightness(new Display.Brightness(15, 15));
                  e.setPersistent(false);
                  e.setBillboard(Display.Billboard.CENTER);
                  e.setTransformation(itemTransform(r == 1 ? 0.6f : 0.45f));
                });
        player.showEntity(plugin, reels[col][row]);
      }
    }
    world.playSound(machineCenter, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.5f, 1.4f);
  }

  /**
   * Spins any not-yet-locked reels, flickering through random rarities, and locks columns on
   * schedule.
   */
  private void tickReelSpin(World world) {
    List<RarityConfig> allRarities = EnchantAnimationConfig.getRarities();

    if (!columnLocked[0] && currentTick >= COL1_STOP) lockColumn(0, world, allRarities);
    if (!columnLocked[1] && currentTick >= COL2_STOP) lockColumn(1, world, allRarities);
    if (!columnLocked[2] && currentTick >= COL3_STOP) lockColumn(2, world, allRarities);

    if (currentTick % 2 != 0) return;
    for (int col = 0; col < 3; col++) {
      if (columnLocked[col]) continue;
      for (int row = 0; row < 3; row++) {
        ItemDisplay d = reels[col][row];
        if (d == null || d.isDead()) continue;
        RarityConfig rarity = randomRarity(allRarities);
        d.setItemStack(EvolutionStone.create(rarity, stoneEnchantName));
      }
      if (currentTick % 4 == 0) {
        world.playSound(
            offset(machineCenter, COLUMN_OFFSETS[col], 0), Sound.BLOCK_LEVER_CLICK, 0.25f, 1.6f);
      }
    }
  }

  /**
   * Locks a reel column into its final rarities — the middle row always shows the winning rarity.
   */
  private void lockColumn(int col, World world, List<RarityConfig> allRarities) {
    columnLocked[col] = true;
    RarityConfig top = randomRarity(allRarities);
    RarityConfig bottom = randomRarity(allRarities);
    RarityConfig[] finalRarities = {top, winningRarity, bottom};
    for (int row = 0; row < 3; row++) {
      ItemDisplay d = reels[col][row];
      if (d == null || d.isDead()) continue;
      d.setItemStack(EvolutionStone.create(finalRarities[row], stoneEnchantName));
      d.setTransformation(itemTransform(row == 1 ? 0.72f : 0.5f));
    }
    world.playSound(
        offset(machineCenter, COLUMN_OFFSETS[col], 0), Sound.BLOCK_ANVIL_LAND, 0.35f, 1.5f);
    world.playSound(
        offset(machineCenter, COLUMN_OFFSETS[col], 0),
        Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
        0.3f,
        1.0f);
  }

  /** All three reels agree — a win. Flashes the middle row and applies the evolution outcome. */
  private void triggerReveal(World world) {
    world.playSound(machineCenter, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    world.playSound(machineCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
    for (int col = 0; col < 3; col++) {
      Location pos = offset(machineCenter, COLUMN_OFFSETS[col], ROW_OFFSETS[1]);
      player.spawnParticle(
          Particle.DUST,
          pos,
          20,
          0.15,
          0.15,
          0.15,
          0,
          new Particle.DustOptions(winningRarity.particleColor(), 1.8f));
      player.spawnParticle(Particle.FLASH, pos, 1, 0, 0, 0, 0);
    }
  }

  /** Top row is sucked into the top portal, bottom row into the bottom portal, sides fizzle out. */
  private void tickSuck(World world) {
    double t = (double) (currentTick - REVEAL_TICK) / (SUCK_END - REVEAL_TICK);
    double eased = t * t;

    for (int col = 0; col < 3; col++) {
      ItemDisplay topStone = reels[col][0];
      if (topStone != null && !topStone.isDead()) {
        Location base = offset(machineCenter, COLUMN_OFFSETS[col], ROW_OFFSETS[0]);
        topStone.teleport(lerp(base, topPortal, eased));
        scaleItem(topStone, Math.max(0.02f, 0.5f * (float) (1 - eased)));
      }
      ItemDisplay bottomStone = reels[col][2];
      if (bottomStone != null && !bottomStone.isDead()) {
        Location base = offset(machineCenter, COLUMN_OFFSETS[col], ROW_OFFSETS[2]);
        bottomStone.teleport(lerp(base, bottomPortal, eased));
        scaleItem(bottomStone, Math.max(0.02f, 0.5f * (float) (1 - eased)));
      }
    }

    ItemDisplay leftMid = reels[0][1];
    if (leftMid != null && !leftMid.isDead()) {
      scaleItem(leftMid, Math.max(0.001f, 0.72f * (float) (1 - eased)));
      if (currentTick % 3 == 0) {
        world.spawnParticle(
            Particle.SMOKE,
            offset(machineCenter, COLUMN_OFFSETS[0], ROW_OFFSETS[1]),
            2,
            0.1,
            0.1,
            0.1,
            0.01);
      }
    }
    ItemDisplay rightMid = reels[2][1];
    if (rightMid != null && !rightMid.isDead()) {
      scaleItem(rightMid, Math.max(0.001f, 0.72f * (float) (1 - eased)));
      if (currentTick % 3 == 0) {
        world.spawnParticle(
            Particle.SMOKE,
            offset(machineCenter, COLUMN_OFFSETS[2], ROW_OFFSETS[1]),
            2,
            0.1,
            0.1,
            0.1,
            0.01);
      }
    }

    if (currentTick == SUCK_END) {
      for (int col = 0; col < 3; col++) {
        if (reels[col][0] != null && !reels[col][0].isDead()) reels[col][0].remove();
        if (reels[col][2] != null && !reels[col][2].isDead()) reels[col][2].remove();
      }
      if (leftMid != null && !leftMid.isDead()) leftMid.remove();
      if (rightMid != null && !rightMid.isDead()) rightMid.remove();
      world.playSound(topPortal, "custom.vacuumsuckshort", 0.5f, 1.1f);
      world.playSound(bottomPortal, "custom.vacuumsuckshort", 0.5f, 0.8f);
      world.playSound(machineCenter, Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.5f);

      centerStoneDisplay = reels[1][1];
      reels[1][1] = null;
    }
  }

  /** Reveals the surviving stone's name above it. */
  private void spawnStoneNameText(World world) {
    RarityConfig r = winningRarity;
    Component comp =
        Component.text()
            .append(
                Component.text(r.displayName().toUpperCase() + " STONE")
                    .color(r.textColor())
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false))
            .append(Component.newline())
            .append(
                Component.text("✧ " + stoneEnchantName)
                    .color(TextColor.color(0xCCCCCC))
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false))
            .build();
    stoneNameText =
        world.spawn(
            stoneNamePos,
            TextDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.text(comp);
              e.setAlignment(TextDisplay.TextAlignment.CENTER);
              e.setBillboard(Display.Billboard.CENTER);
              e.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
              e.setShadowed(true);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setTransformation(textTransform(0.75f));
            });
    player.showEntity(plugin, stoneNameText);
    world.playSound(machineCenter, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    player.spawnParticle(Particle.END_ROD, machineCenter, 20, 0.2, 0.3, 0.2, 0.05);
  }

  /**
   * The evolved pickaxe rises from the bottom portal as the stone slides aside to make room, while
   * both portals collapse.
   */
  @SuppressWarnings("deprecation")
  private void tickDescend(World world) {
    double t = (double) (currentTick - STONE_REVEAL_TICK) / (DESCEND_END - STONE_REVEAL_TICK);
    double eased = 1 - Math.pow(1 - t, 2);

    if (!emergingSpawned) {
      emergingSpawned = true;
      ItemStack pick = heldPickaxe.clone();
      pick.setType(nextTier.material);
      emergingPickaxe =
          world.spawn(
              bottomPortal.clone(),
              ItemDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setItemStack(pick);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setTransformation(itemTransform(0f));
              });
      player.showEntity(plugin, emergingPickaxe);
      world.playSound(bottomPortal, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.6f, 1.3f);
    }

    if (emergingPickaxe != null && !emergingPickaxe.isDead()) {
      emergingPickaxe.teleport(lerp(bottomPortal, exitPickaxeFinalPos, eased));
      scaleItem(emergingPickaxe, (float) eased * 0.9f);
    }
    if (centerStoneDisplay != null && !centerStoneDisplay.isDead()) {
      centerStoneDisplay.teleport(lerp(machineCenter, exitStoneFinalPos, eased));
      scaleItem(centerStoneDisplay, 0.72f);
    }
    if (stoneNameText != null && !stoneNameText.isDead()) {
      Location from = stoneNamePos;
      Location to = exitStoneFinalPos.clone().add(0, 0.9, 0);
      stoneNameText.teleport(lerp(from, to, eased));
    }

    double closeT = Math.min(1.0, t * 1.3);
    double radius = PORTAL_RADIUS * (1 - closeT);
    if (radius > 0.05) {
      drawPortalRing(world, topPortal, radius, currentTick * 0.7);
      drawPortalRing(world, bottomPortal, radius, -currentTick * 0.7);
    } else if (currentTick % 10 == 0) {
      world.playSound(machineCenter, "custom.vacuumsuckshort", 0.3f, 1.4f);
    }

    if (currentTick % 4 == 0) {
      world.spawnParticle(
          Particle.DUST,
          exitPickaxeFinalPos,
          3,
          0.2,
          0.2,
          0.2,
          0,
          new Particle.DustOptions(winningRarity.particleColor(), 1.5f));
    }
  }

  /** Spawns the evolution title above both finished items. */
  private void spawnEvolutionTitle(World world) {
    String raw = nextTier.displayName.trim();
    String tierName = (raw.startsWith("&#") && raw.length() >= 8) ? raw.substring(8).trim() : raw;
    TextColor tc = tierTextColor(nextTier);
    Component comp =
        Component.text()
            .append(
                Component.text("🎰  EVOLUTION " + nextTier.level)
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
    world.playSound(machineCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
  }

  /** Float phase — both items hover gently with a rarity-coloured halo of particles. */
  private void tickFloat(World world) {
    float dy = (float) (Math.sin(currentTick * 0.15) * 0.07);
    if (emergingPickaxe != null && !emergingPickaxe.isDead()) {
      emergingPickaxe.teleport(exitPickaxeFinalPos.clone().add(0, dy, 0));
    }
    if (centerStoneDisplay != null && !centerStoneDisplay.isDead()) {
      centerStoneDisplay.teleport(exitStoneFinalPos.clone().add(0, -dy, 0));
    }
    if (stoneNameText != null && !stoneNameText.isDead()) {
      stoneNameText.teleport(exitStoneFinalPos.clone().add(0, 0.9 - dy, 0));
    }
    if (currentTick % 3 == 0) {
      double rx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
      double ry = ThreadLocalRandom.current().nextDouble() * 2.0;
      double rz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
      player.spawnParticle(Particle.GLOW, machineCenter.clone().add(rx, ry, rz), 1, 0, 0, 0, 0);
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

  /** Return phase — items and text sweep back to the player. */
  private void tickReturn() {
    double t = (double) (currentTick - FLOAT_END) / (RETURN_END - FLOAT_END);
    double eased = t * t;
    Location chest = player.getLocation().add(0, 1.3, 0);
    if (emergingPickaxe != null && !emergingPickaxe.isDead()) {
      emergingPickaxe.teleport(lerp(exitPickaxeFinalPos, chest, eased));
    }
    if (centerStoneDisplay != null && !centerStoneDisplay.isDead()) {
      centerStoneDisplay.teleport(lerp(exitStoneFinalPos, chest, eased));
    }
    if (stoneNameText != null && !stoneNameText.isDead()) {
      stoneNameText.teleport(lerp(exitStoneFinalPos.clone().add(0, 0.9, 0), chest, eased));
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
    if (ascendingPickaxe != null && !ascendingPickaxe.isDead()) ascendingPickaxe.remove();
    for (int col = 0; col < 3; col++) {
      for (int row = 0; row < 3; row++) {
        ItemDisplay d = reels[col][row];
        if (d != null && !d.isDead()) d.remove();
      }
    }
    if (centerStoneDisplay != null && !centerStoneDisplay.isDead()) centerStoneDisplay.remove();
    if (emergingPickaxe != null && !emergingPickaxe.isDead()) emergingPickaxe.remove();
    if (stoneNameText != null && !stoneNameText.isDead()) stoneNameText.remove();
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

  /** Picks a random rarity from the given list. */
  private static RarityConfig randomRarity(List<RarityConfig> all) {
    return all.get(ThreadLocalRandom.current().nextInt(all.size()));
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
