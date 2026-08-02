package org.benski.evolv3.animations.progression;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.StoneEffectRegistry;
import org.benski.evolv3.core.managers.pickaxe.PrestigeManager;
import org.benski.evolv3.items.EvolutionStone;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 * The "epic" prestige animation — a single continuous world-space sequence combining the enchant
 * rift animation's twin-portal-and-look-to-select mechanic ({@link RiftAnimation}) with the
 * evolution slot machine's portal-transform-and-collapse mechanic ({@link
 * EvolutionSlotMachineAnimation}), replacing the old confirm-menu-then-GUI-slot-machine-then-flight
 * flow entirely.
 *
 * <p>The pickaxe is never removed from the player's hand — {@code onSelect} triggers {@link
 * PrestigeManager#executePrestige} as soon as the choice is locked in, which already resets the
 * held pickaxe's material/lore live, so it visibly reforges to wood mid-animation with no fake
 * item-display hand-off needed.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 15) Tier-coloured burst at the player.
 *   <li>Converge/Split ( 15– 35) Essence spirals out to two rift sites.
 *   <li>Rifts Open ( 35– 55) Twin rifts spin open, flanking the player.
 *   <li>Stones Rise ( 55– 75) The three pre-rolled stones rise into view between the rifts.
 *   <li>Selection (75+) Player looks at a stone and left-clicks to choose it (auto-picks the first
 *       option after a long AFK grace period).
 *   <li>Forge ( selection+0–10) Unchosen stones sink and fizzle; the chosen stone is drawn into the
 *       right-hand rift. {@code onSelect} fires here — the prestige is applied immediately.
 *   <li>Burst ( selection+10–40) Tier-coloured explosion and "Prestige N Unlocked" title.
 *   <li>Glory ( selection+40–70) Halo of tier-coloured particles.
 *   <li>Collapse ( selection+70–75) Both rifts collapse in one burst; the stone settles near the
 *       player before the animation finishes.
 * </ol>
 */
public class PrestigeAscensionAnimation {
  private static final int CAST_END = 15;
  private static final int CONVERGE_END = CAST_END + 10;
  private static final int SPLIT_END = CONVERGE_END + 10;
  private static final int RIFTS_OPEN_END = SPLIT_END + 20;
  private static final int STONES_RISE_END = RIFTS_OPEN_END + 20;
  private static final int SELECTION_READY_TICK = STONES_RISE_END;
  private static final int AUTO_SELECT_TIMEOUT = 300;

  private static final double RIFT_OFFSET = 1.7;
  private static final double RIFT_RADIUS = 0.85;
  private static final double[] STONE_OFFSETS = {-0.8, 0.0, 0.8};

  private static final Color VOID_COLOR = Color.fromRGB(4, 4, 12);

  private static final float DESC_SCALE = 0.42f;
  private static final float DESC_IN_STEP = 0.14f;
  private static final float DESC_OUT_STEP = 0.20f;

  private final JavaPlugin plugin;
  private final Player player;
  private final int nextPrestige;
  private final List<PrestigeManager.StoneRoll> stoneRolls;
  private final BiConsumer<Player, PrestigeManager.StoneRoll> onSelect;
  private final Runnable onComplete;
  private final Color accent;

  private boolean finished = false;
  private boolean selectionMade = false;
  private boolean outcomeApplied = false;
  private int selectedIndex = -1;
  private int selectionTick = -1;
  private volatile int highlightedIndex = -1;
  private int currentTick = 0;

  private Vector forwardDir;
  private Vector rightDir;
  private Location center;
  private Location leftRift;
  private Location rightRift;
  private Location titlePos;

  private final ItemDisplay[] stoneDisplays = new ItemDisplay[3];
  private final TextDisplay[] stoneLabels = new TextDisplay[3];
  private final Location[] stonePositions = new Location[3];
  private final boolean[] stoneRemoved = new boolean[3];
  private TextDisplay titleDisplay;

  /** Hover-only description panel per stone, shown while it's the highlighted option. */
  private final TextDisplay[] descDisplays = new TextDisplay[3];

  /** Animation progress per stone's description panel: 0 = hidden, 1 = fully visible. */
  private final float[] descProgress = new float[3];

  /** Whether each stone's description should be animating toward visible. */
  private final boolean[] descTargetIn = new boolean[3];

  /**
   * Creates a prestige ascension animation.
   *
   * @param plugin the plugin instance
   * @param player the player prestiging
   * @param nextPrestige the prestige tier being unlocked (drives the accent colour)
   * @param stoneRolls the three pre-rolled stone options
   * @param onSelect invoked once the player's choice is locked in — applies the actual prestige
   * @param onComplete invoked once the whole animation finishes
   */
  public PrestigeAscensionAnimation(
      @NotNull JavaPlugin plugin,
      @NotNull Player player,
      int nextPrestige,
      @NotNull List<PrestigeManager.StoneRoll> stoneRolls,
      @NotNull BiConsumer<Player, PrestigeManager.StoneRoll> onSelect,
      @NotNull Runnable onComplete) {
    this.plugin = plugin;
    this.player = player;
    this.nextPrestige = nextPrestige;
    this.stoneRolls = stoneRolls;
    this.onSelect = onSelect;
    this.onComplete = onComplete;
    this.accent = accentColor(nextPrestige);
  }

  /** Returns {@code true} once the animation has finished. */
  public boolean isFinished() {
    return finished;
  }

  /** Returns {@code true} once the player has committed to a stone. */
  public boolean isSelectionMade() {
    return selectionMade;
  }

  /** Immediately stops this animation and removes all display entities. */
  public void stop() {
    if (finished) return;
    finished = true;
    removeAllDisplays();
  }

  /** Starts the animation. */
  public void start() {
    Location eye = player.getEyeLocation();
    if (eye.getWorld() == null) return;

    forwardDir = eye.getDirection().setY(0).normalize();
    rightDir = new Vector(forwardDir.getZ(), 0, -forwardDir.getX()).normalize();

    center = player.getLocation().clone().add(forwardDir.clone().multiply(3.2)).add(0, 1.6, 0);
    leftRift = center.clone().add(rightDir.clone().multiply(-RIFT_OFFSET));
    rightRift = center.clone().add(rightDir.clone().multiply(RIFT_OFFSET));
    titlePos = center.clone().add(0, 1.5, 0);

    for (int i = 0; i < 3; i++) {
      stonePositions[i] = center.clone().add(rightDir.clone().multiply(STONE_OFFSETS[i]));
    }

    playCastBurst();

    new BukkitRunnable() {
      @Override
      public void run() {
        if (finished) {
          cancel();
          return;
        }
        if (!player.isOnline()) {
          // Unlike a predetermined outcome (e.g. evolution), a stone hasn't necessarily been
          // chosen yet — applying one here would let a disconnect skip the real choice. The
          // manager's handlePlayerQuit calls stop() and persists/replays this exact state instead,
          // mirroring EnchantSelectionManager's handling of its own interactive selection.
          cancel();
          return;
        }
        currentTick++;
        tick();
      }
    }.runTaskTimer(plugin, 1L, 1L);
  }

  /**
   * Attempts to confirm the currently-highlighted stone. Called by {@code PrestigeListener} on
   * left-click.
   *
   * @return {@code true} if a selection was made
   */
  public boolean confirmSelection() {
    if (selectionMade) return false;
    if (currentTick < SELECTION_READY_TICK) return false;
    if (highlightedIndex < 0) return false;
    applySelection(highlightedIndex);
    return true;
  }

  /** Ticks this component. */
  private void tick() {
    var world = player.getWorld();
    if (world == null) return;

    if (currentTick <= CAST_END) {
      // burst already played at start(); nothing further this phase
    } else if (currentTick <= CONVERGE_END) {
      tickConverge(world);
    } else if (currentTick <= SPLIT_END) {
      tickSplit(world);
    } else if (currentTick <= RIFTS_OPEN_END) {
      tickRiftsOpen(world);
    } else if (!selectionMade) {
      tickRiftAmbience(world);
    }

    if (currentTick > RIFTS_OPEN_END && currentTick <= STONES_RISE_END) {
      tickStonesRise(world);
    } else if (currentTick == STONES_RISE_END + 1) {
      spawnStoneLabels(world);
    }

    if (!selectionMade) {
      if (currentTick >= SELECTION_READY_TICK) {
        tickStoneIdle(world);
        tickHighlightDetection();
        tickDescriptions();
        if (currentTick - SELECTION_READY_TICK >= AUTO_SELECT_TIMEOUT) {
          applySelection(0);
        }
      }
      return;
    }

    tickPostSelection(world);
  }

  /** Gold-equivalent (tier-coloured) essence spirals from the player's eyes toward the centre. */
  private void tickConverge(org.bukkit.World world) {
    int tick = currentTick - CAST_END;
    double t = Math.min(1.0, (double) tick / (CONVERGE_END - CAST_END));
    Location swirlCenter = lerp(player.getEyeLocation(), center, t);
    double angle = tick * 0.6;
    double radius = 0.3 + 0.5 * t;
    world.spawnParticle(
        Particle.DUST,
        swirlCenter
            .clone()
            .add(Math.cos(angle) * radius, Math.sin(angle * 1.3) * 0.2, Math.sin(angle) * radius),
        2,
        0,
        0,
        0,
        0,
        new Particle.DustOptions(accent, 1.1f));
  }

  /** The converged essence splits in two and travels out to each rift site. */
  private void tickSplit(org.bukkit.World world) {
    int sinceSplit = currentTick - CONVERGE_END;
    double t = Math.min(1.0, (double) sinceSplit / (SPLIT_END - CONVERGE_END));
    for (Location rift : new Location[] {leftRift, rightRift}) {
      Location pos = lerp(center, rift, t);
      world.spawnParticle(
          Particle.DUST, pos, 3, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(accent, 1.3f));
      world.spawnParticle(Particle.END_ROD, pos, 1, 0.02, 0.02, 0.02, 0.005);
    }
  }

  /** Both rifts spin open from nothing out to full size. */
  private void tickRiftsOpen(org.bukkit.World world) {
    double t = Math.min(1.0, (double) (currentTick - SPLIT_END) / (RIFTS_OPEN_END - SPLIT_END));
    double radius = RIFT_RADIUS * t;
    double angle = currentTick * 0.9;
    drawRift(world, leftRift, radius, angle);
    drawRift(world, rightRift, radius, -angle);
    if (currentTick == SPLIT_END + 1) {
      world.playSound(center, "custom.portalopenrift", 0.55f, 0.9f);
      world.playSound(center, "custom.portalopenrift", 0.55f, 1.3f);
    }
  }

  /** Keeps both rifts shimmering while the player looks over their options. */
  private void tickRiftAmbience(org.bukkit.World world) {
    for (Location rift : new Location[] {leftRift, rightRift}) {
      if (currentTick % 3 == 0) spawnRimShimmer(world, rift, 3);
      if (currentTick % 6 == 0) spawnVoidCore(world, rift, 2);
      if (currentTick % 10 == 0) spawnStarfield(world, rift, 2);
    }
  }

  /** Draws one rift opening — void core, starfield, and a tier-coloured swirling rim. */
  private void drawRift(org.bukkit.World world, Location rift, double radius, double angle) {
    if (radius <= 0.01) return;
    int points = 10;
    for (int i = 0; i < points; i++) {
      double a = angle + (i / (double) points) * 2 * Math.PI;
      Location p = discPoint(rift, radius, a);
      world.spawnParticle(
          Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(accent, 1.2f));
    }
    spawnVoidCore(world, rift, 2);
    spawnStarfield(world, rift, 1);
  }

  private void spawnVoidCore(org.bukkit.World world, Location rift, int count) {
    for (int i = 0; i < count; i++) {
      double r = ThreadLocalRandom.current().nextDouble() * RIFT_RADIUS * 0.7;
      double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
      world.spawnParticle(
          Particle.DUST,
          discPoint(rift, r, angle),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(VOID_COLOR, 1.6f));
    }
  }

  private void spawnStarfield(org.bukkit.World world, Location rift, int count) {
    for (int i = 0; i < count; i++) {
      double r = ThreadLocalRandom.current().nextDouble() * RIFT_RADIUS * 0.85;
      double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
      world.spawnParticle(Particle.GLOW, discPoint(rift, r, angle), 1, 0, 0, 0, 0);
    }
  }

  private void spawnRimShimmer(org.bukkit.World world, Location rift, int count) {
    for (int i = 0; i < count; i++) {
      double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
      Location edge = discPoint(rift, RIFT_RADIUS * 0.95, angle);
      world.spawnParticle(
          Particle.DUST, edge, 1, 0.03, 0.03, 0.03, 0, new Particle.DustOptions(accent, 1.2f));
    }
  }

  /**
   * A point on the rift's disc, facing the player (plane spanned by {@code forwardDir}/world-up).
   */
  private Location discPoint(Location rift, double radius, double angle) {
    return rift.clone()
        .add(forwardDir.clone().multiply(Math.cos(angle) * radius))
        .add(0, Math.sin(angle) * radius, 0);
  }

  /** The three pre-rolled stones rise into view between the rifts. */
  private void tickStonesRise(org.bukkit.World world) {
    double t =
        Math.min(1.0, (double) (currentTick - RIFTS_OPEN_END) / (STONES_RISE_END - RIFTS_OPEN_END));
    double eased = easeOutCubic(t);

    for (int i = 0; i < 3; i++) {
      if (stoneDisplays[i] == null) {
        PrestigeManager.StoneRoll roll = stoneRolls.get(i);
        ItemStack stack =
            org.benski.evolv3.items.EvolutionStone.create(roll.rarity(), roll.enchantName());
        stoneDisplays[i] =
            world.spawn(
                stonePositions[i].clone().add(0, -1.2, 0),
                ItemDisplay.class,
                e -> {
                  e.setVisibleByDefault(false);
                  e.setItemStack(stack);
                  e.setBrightness(new Display.Brightness(15, 15));
                  e.setPersistent(false);
                  e.setBillboard(Display.Billboard.CENTER);
                  e.setTransformation(itemTransform(0f));
                });
        player.showEntity(plugin, stoneDisplays[i]);
      }
      ItemDisplay display = stoneDisplays[i];
      if (display != null && !display.isDead()) {
        Location from = stonePositions[i].clone().add(0, -1.2, 0);
        display.teleport(lerp(from, stonePositions[i], eased));
        display.setTransformation(itemTransform((float) eased * 0.75f));
      }
    }

    if (currentTick % 4 == 0) {
      world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.4f, 1.3f + (float) t * 0.3f);
    }
  }

  /**
   * Spawns the always-visible stone name above each stone, plus an initially-hidden hover
   * description panel (the stone's effect, wrapped, with a trailing ▼ arrow) that scales in while
   * it's the highlighted option — mirroring {@link EnchantAnimation}'s label/description pair.
   */
  private void spawnStoneLabels(org.bukkit.World world) {
    for (int i = 0; i < 3; i++) {
      PrestigeManager.StoneRoll roll = stoneRolls.get(i);
      final int idx = i;

      Location labelPos = stonePositions[i].clone().add(0, 0.55, 0);
      Component nameText =
          EvolutionStone.buildDisplayName(roll.rarity(), roll.enchantName())
              .decoration(TextDecoration.ITALIC, false);
      stoneLabels[idx] =
          world.spawn(
              labelPos,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.text(nameText);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setBackgroundColor(Color.fromARGB(210, 10, 10, 20));
                e.setPersistent(false);
                e.setTransformation(textTransform(0.6f));
              });
      player.showEntity(plugin, stoneLabels[idx]);

      String effect =
          StoneEffectRegistry.getEffect(
              roll.enchantName().toLowerCase(), roll.rarity().displayName());
      var descBuilder = Component.text();
      for (String chunk : Utils.wrapWords(effect, 5)) {
        descBuilder
            .append(Component.text(chunk).color(TextColor.color(0xCCCCCC)))
            .append(Component.newline());
      }
      descBuilder.append(
          Component.text("▼").color(TextColor.color(0xFFD700)).decorate(TextDecoration.BOLD));
      final Component finalDescText = descBuilder.build().decoration(TextDecoration.ITALIC, false);

      Location descPos = stonePositions[i].clone().add(0, 1.05, 0);
      descDisplays[idx] =
          world.spawn(
              descPos,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.text(finalDescText);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setBackgroundColor(Color.fromARGB(180, 10, 10, 20));
                e.setPersistent(false);
                e.setTransformation(
                    new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.001f, 0.001f, 0.001f),
                        new AxisAngle4f(0, 0, 1, 0)));
              });
      player.showEntity(plugin, descDisplays[idx]);
    }
    world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
  }

  /** Smoothly scales each description panel in/out based on hover state. */
  private void tickDescriptions() {
    for (int r = 0; r < 3; r++) {
      TextDisplay dd = descDisplays[r];
      if (dd == null || dd.isDead()) continue;

      if (descTargetIn[r]) {
        descProgress[r] = Math.min(descProgress[r] + DESC_IN_STEP, 1.0f);
      } else {
        descProgress[r] = Math.max(descProgress[r] - DESC_OUT_STEP, 0.0f);
      }

      float p = descProgress[r];
      float s;
      if (p <= 0f) {
        s = 0.001f;
      } else if (descTargetIn[r]) {
        s = Math.max((float) easeOutBack(p) * DESC_SCALE, 0.001f);
      } else {
        s = Math.max(p * DESC_SCALE, 0.001f);
      }

      dd.setTransformation(
          new Transformation(
              new Vector3f(0, 0, 0),
              new AxisAngle4f(0, 0, 1, 0),
              new Vector3f(s, s, s),
              new AxisAngle4f(0, 0, 1, 0)));
    }
  }

  /** Idle rarity-coloured sparkle around each stone while waiting for a selection. */
  private void tickStoneIdle(org.bukkit.World world) {
    if (currentTick % 2 != 0) return;
    for (int i = 0; i < 3; i++) {
      if (stoneRemoved[i]) continue;
      PrestigeManager.StoneRoll roll = stoneRolls.get(i);
      double angle = currentTick * 0.18 + i * 2.09;
      world.spawnParticle(
          Particle.DUST,
          stonePositions[i]
              .clone()
              .add(Math.cos(angle) * 0.35, Math.sin(angle * 0.7) * 0.12, Math.sin(angle) * 0.35),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(roll.rarity().particleColor(), 1.0f));
    }
  }

  /** Resolves which stone (if any) the player is currently looking at. */
  private void tickHighlightDetection() {
    Location eye = player.getEyeLocation();
    Vector direction = eye.getDirection();

    int newHighlight = -1;
    double bestDot = 0.9;
    for (int i = 0; i < 3; i++) {
      if (stoneRemoved[i]) continue;
      Vector toStone = stonePositions[i].toVector().subtract(eye.toVector());
      if (toStone.lengthSquared() < 1e-6) continue;
      toStone = toStone.normalize();
      double dot = direction.dot(toStone);
      if (dot > bestDot) {
        bestDot = dot;
        newHighlight = i;
      }
    }

    if (newHighlight != highlightedIndex) {
      int prev = highlightedIndex;
      highlightedIndex = newHighlight;
      if (prev >= 0) {
        descTargetIn[prev] = false;
        if (stoneDisplays[prev] != null && !stoneDisplays[prev].isDead()) {
          stoneDisplays[prev].setGlowing(false);
        }
      }
      if (newHighlight >= 0) {
        descTargetIn[newHighlight] = true;
        if (stoneDisplays[newHighlight] != null) {
          stoneDisplays[newHighlight].setGlowing(true);
        }
        player.playSound(stonePositions[newHighlight], Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
      }
    }
  }

  /** Commits to a stone: locks the selection, applies the outcome, and starts the finale. */
  private void applySelection(int index) {
    if (selectionMade) return;
    selectedIndex = index;
    selectionMade = true;
    selectionTick = currentTick;
    var world = player.getWorld();
    if (world != null) {
      world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
    }
  }

  /** Drives every phase from the moment a stone is chosen through to the final delivery. */
  private void tickPostSelection(org.bukkit.World world) {
    int sp = currentTick - selectionTick;

    if (sp <= 10) {
      tickForge(world, sp);
    }
    if (sp == 10 && !outcomeApplied) {
      outcomeApplied = true;
      onSelect.accept(player, stoneRolls.get(selectedIndex));
    }
    if (sp > 10 && sp <= 40) {
      tickBurst(world, sp - 10);
    }
    if (sp > 40 && sp <= 70) {
      tickGlory(world, sp - 40);
    }
    if (sp == 71) {
      collapseRifts(world);
    }
    if (sp == 75) {
      finished = true;
      removeAllDisplays();
      if (onComplete != null) onComplete.run();
    }
  }

  /** Unchosen stones sink and fizzle; the chosen one is drawn into the right-hand rift. */
  private void tickForge(org.bukkit.World world, int sp) {
    double t = sp / 10.0;
    for (int i = 0; i < 3; i++) {
      ItemDisplay display = stoneDisplays[i];
      if (display == null || display.isDead() || stoneRemoved[i]) continue;

      if (i == selectedIndex) {
        display.teleport(lerp(stonePositions[i], rightRift, easeInCubic(t)));
        display.setTransformation(itemTransform(0.75f * (1f - (float) t * 0.4f)));
      } else {
        display.teleport(stonePositions[i].clone().add(0, -t * 1.0, 0));
        display.setTransformation(itemTransform(Math.max(0.001f, 0.75f * (1f - (float) t))));
        if (sp == 10) {
          stoneRemoved[i] = true;
          display.remove();
          stoneDisplays[i] = null;
          world.spawnParticle(Particle.POOF, stonePositions[i], 10, 0.2, 0.2, 0.2, 0.05);
          if (stoneLabels[i] != null && !stoneLabels[i].isDead()) {
            stoneLabels[i].remove();
            stoneLabels[i] = null;
          }
          if (descDisplays[i] != null && !descDisplays[i].isDead()) {
            descDisplays[i].remove();
            descDisplays[i] = null;
          }
        }
      }
    }

    if (sp == 10) {
      ItemDisplay chosen = stoneDisplays[selectedIndex];
      if (chosen != null && !chosen.isDead()) {
        chosen.remove();
        stoneDisplays[selectedIndex] = null;
      }
      if (stoneLabels[selectedIndex] != null && !stoneLabels[selectedIndex].isDead()) {
        stoneLabels[selectedIndex].remove();
        stoneLabels[selectedIndex] = null;
      }
      if (descDisplays[selectedIndex] != null && !descDisplays[selectedIndex].isDead()) {
        descDisplays[selectedIndex].remove();
        descDisplays[selectedIndex] = null;
      }
      world.playSound(rightRift, "custom.burstatoms", 0.5f, 1.3f);
      world.spawnParticle(
          Particle.DUST, rightRift, 25, 0.25, 0.2, 0.25, 0, new Particle.DustOptions(accent, 1.4f));
      world.spawnParticle(Particle.END_ROD, rightRift, 12, 0.2, 0.2, 0.2, 0.08);
    }
  }

  /** Big tier-coloured explosion and the "Prestige N Unlocked" title. */
  private void tickBurst(org.bukkit.World world, int bp) {
    if (bp == 1) {
      Location eye = player.getEyeLocation();
      player.spawnParticle(Particle.TOTEM_OF_UNDYING, eye, 200, 0.9, 1.0, 0.9, 0.3);
      int ringPoints = 24;
      for (int i = 0; i < ringPoints; i++) {
        double angle = Math.toRadians(360.0 / ringPoints * i);
        Location p = eye.clone().add(Math.cos(angle) * 2.6, 0, Math.sin(angle) * 2.6);
        world.spawnParticle(Particle.END_ROD, p, 3, 0.1, 0.2, 0.1, 0.05);
      }
      world.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
      world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.2f);
      world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
      spawnTitle(world);
    }

    if (bp % 2 == 0) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      double angle = rng.nextDouble() * 2 * Math.PI;
      double r = 0.5 + rng.nextDouble() * 1.8;
      double y = (rng.nextDouble() - 0.2) * 2.0;
      player.spawnParticle(
          Particle.DUST,
          player.getEyeLocation().clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(accent, 2.0f));
    }
  }

  /** Slower halo of tier-coloured particles winding down after the burst. */
  private void tickGlory(org.bukkit.World world, int gp) {
    Location eye = player.getEyeLocation();
    int haloPoints = 12;
    double haloAngle = Math.toRadians(gp * 10);
    for (int i = 0; i < haloPoints; i++) {
      double a = haloAngle + Math.toRadians(360.0 / haloPoints * i);
      world.spawnParticle(
          Particle.DUST,
          eye.clone().add(Math.cos(a) * 1.3, 0.3, Math.sin(a) * 1.3),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(accent, 1.3f));
    }
    if (gp % 3 == 0) {
      world.spawnParticle(Particle.ENCHANT, eye, 4, 0.5, 0.6, 0.5, 0.08);
    }
  }

  /** Spawns the floating "Prestige N Unlocked" title above the scene. */
  private void spawnTitle(org.bukkit.World world) {
    Component comp =
        Component.text()
            .append(
                Component.text(FontUtils.toSmallCaps("Prestige " + nextPrestige))
                    .color(TextColor.color(accent.asRGB()))
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false))
            .append(Component.newline())
            .append(
                Component.text(FontUtils.toSmallCaps("Unlocked"))
                    .color(TextColor.color(0xCCCCCC))
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false))
            .build();
    titleDisplay =
        world.spawn(
            titlePos,
            TextDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.text(comp);
              e.setAlignment(TextDisplay.TextAlignment.CENTER);
              e.setBillboard(Display.Billboard.CENTER);
              e.setBackgroundColor(org.bukkit.Color.fromARGB(160, 0, 0, 0));
              e.setShadowed(true);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setTransformation(textTransform(0.85f));
            });
    player.showEntity(plugin, titleDisplay);
  }

  /** Collapses both rifts in a single burst, exactly like the evolution portal's own collapse. */
  private void collapseRifts(org.bukkit.World world) {
    world.playSound(center, "custom.vacuumsuckshort", 0.4f, 1.0f);
    for (Location rift : new Location[] {leftRift, rightRift}) {
      world.spawnParticle(
          Particle.DUST, rift, 20, 0.2, 0.4, 0.2, 0, new Particle.DustOptions(accent, 1.6f));
      world.spawnParticle(Particle.END_ROD, rift, 10, 0.15, 0.3, 0.15, 0.05);
    }
    if (titleDisplay != null && !titleDisplay.isDead()) {
      titleDisplay.remove();
      titleDisplay = null;
    }
    world.spawnParticle(
        Particle.DUST,
        player.getLocation().clone().add(0, 1.2, 0),
        20,
        0.3,
        0.3,
        0.3,
        0,
        new Particle.DustOptions(stoneRolls.get(selectedIndex).rarity().particleColor(), 1.5f));
    world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
  }

  /** Removes every display entity this animation may have spawned. */
  private void removeAllDisplays() {
    for (int i = 0; i < 3; i++) {
      if (stoneDisplays[i] != null && !stoneDisplays[i].isDead()) stoneDisplays[i].remove();
      if (stoneLabels[i] != null && !stoneLabels[i].isDead()) stoneLabels[i].remove();
      if (descDisplays[i] != null && !descDisplays[i].isDead()) descDisplays[i].remove();
    }
    if (titleDisplay != null && !titleDisplay.isDead()) titleDisplay.remove();
  }

  /** Plays the shared tier-coloured cast burst at the player. */
  private void playCastBurst() {
    var world = player.getWorld();
    if (world == null) return;
    Location eye = player.getEyeLocation();
    player.spawnParticle(
        Particle.DUST, eye, 30, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(accent, 1.6f));
    player.spawnParticle(Particle.END_ROD, eye, 20, 0.2, 0.2, 0.2, 0.03);
    world.playSound(player.getLocation(), "custom.magiccastwhoosh", 0.6f, 1.1f);
    world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.6f);
  }

  /**
   * Maps a prestige tier (1-5, clamped) to its accent colour; tier 5 cycles through the rainbow.
   */
  private static Color accentColor(int prestige) {
    int clamped = Math.max(1, Math.min(5, prestige));
    if (clamped == 5) {
      return hsbToColor((System.currentTimeMillis() / 40 % 360) / 360.0f, 1.0f, 1.0f);
    }
    return switch (clamped) {
      case 1 -> Color.fromRGB(0xFF, 0xD7, 0x00);
      case 2 -> Color.fromRGB(0x55, 0xAA, 0xFF);
      case 3 -> Color.fromRGB(0xFF, 0x44, 0x00);
      default -> Color.fromRGB(0xAA, 0x00, 0xFF);
    };
  }

  /** Converts an HSB triple to a Bukkit {@link Color}. */
  private static Color hsbToColor(float h, float s, float b) {
    java.awt.Color awt = java.awt.Color.getHSBColor(h, s, b);
    return Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue());
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

  /** Ease out cubic. */
  private static double easeOutCubic(double x) {
    return 1 - Math.pow(1 - x, 3);
  }

  /** Ease in cubic. */
  private static double easeInCubic(double x) {
    return x * x * x;
  }

  /** Ease out back — slight overshoot, used for the description panel's pop-in. */
  private static double easeOutBack(double x) {
    double c1 = 1.70158;
    double c3 = c1 + 1;
    return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
  }
}
