package org.benski.evolv3.animations.progression;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.enchant.StoneEffectRegistry;
import org.benski.evolv3.core.managers.pickaxe.PrestigeManager;
import org.benski.evolv3.core.types.RarityConfig;
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
 * rift animation's twin-portal-and-scrolling-reel mechanic ({@link RiftAnimation}/{@link
 * EnchantAnimation}) with the evolution slot machine's portal-transform-and-collapse mechanic
 * ({@link EvolutionSlotMachineAnimation}), replacing the old confirm-menu-then-GUI-slot-machine
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
 *   <li>Cast — tier-coloured burst at the player.
 *   <li>Converge/Split — essence spirals out to two rift sites.
 *   <li>Rifts Open — twin rifts spin open, flanking the player.
 *   <li>Reel — a strip of Evolution Stones (five visible at a time, evenly spaced) scrolls between
 *       the rifts and decelerates onto three result slots, exactly like {@link EnchantAnimation}'s
 *       strip.
 *   <li>Scatter/Reveal — the non-result stones scatter away; each result's name types out above it.
 *   <li>Selection — player looks at a result and left-clicks to choose it (auto-picks after a long
 *       AFK grace period).
 *   <li>Flight — unchosen stones sink and fizzle; the chosen one flies to the player's eye, exactly
 *       like {@link EnchantAnimation}'s selection flourish. {@code onSelect} fires on arrival — the
 *       prestige is applied immediately.
 *   <li>Burst/Glory — tier-coloured explosion and "Prestige N Unlocked" title.
 *   <li>Collapse — both rifts collapse in one burst; the animation finishes.
 * </ol>
 */
public class PrestigeAscensionAnimation {
  private static final int CAST_END = 15;
  private static final int CONVERGE_END = CAST_END + 10;
  private static final int SPLIT_END = CONVERGE_END + 10;
  private static final int RIFTS_OPEN_END = SPLIT_END + 20;

  /** How many ticks the strip scrolls once it starts, before decelerating to a stop. */
  private static final int SCROLL_DURATION = 90;

  private static final int SCROLL_END_TICK = RIFTS_OPEN_END + SCROLL_DURATION;
  private static final int SCATTER_DURATION = 18;
  private static final int REVEAL_STAGGER = 14;
  private static final int REVEAL_DURATION = 22;
  private static final int REVEAL_START_TICK = SCROLL_END_TICK + SCATTER_DURATION + 4;
  private static final int SELECTION_READY_TICK =
      REVEAL_START_TICK + 2 * REVEAL_STAGGER + REVEAL_DURATION + 10;
  private static final int AUTO_SELECT_TIMEOUT = 300;

  /** Ticks the chosen stone takes to fly from its result slot to the player's eye. */
  private static final int FLIGHT_DURATION = 25;

  private static final double RIFT_RADIUS = 0.85;

  /** Spacing between strip slots — wide enough that ~5 stones are visible at once. */
  private static final double STRIP_SPACING = 1.1;

  private static final double STRIP_INITIAL_OFFSET = 6.0;
  private static final int STRIP_SIZE = 17;
  private static final int[] RESULT_INDICES = {11, 13, 15};

  /** Distance from centre beyond which a strip stone is hidden (culled) rather than rendered. */
  private static final double STRIP_HIDE_RADIUS = 2.9;

  /** Distance from centre to each rift, just past {@link #STRIP_HIDE_RADIUS}. */
  private static final double RIFT_OFFSET = 3.0;

  private static final double STRIP_TOTAL_SCROLL =
      RESULT_INDICES[1] * STRIP_SPACING + STRIP_INITIAL_OFFSET;

  private static final double[] INDICATOR_OFFSETS = {-2 * STRIP_SPACING, 0.0, 2 * STRIP_SPACING};

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
  private boolean scatterTriggered = false;
  private boolean labelsSpawned = false;
  private boolean arrowsRemoved = false;
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

  /** Full strip — index N corresponds to {@link #RESULT_INDICES} for the three real stones. */
  private final ItemDisplay[] stripItems = new ItemDisplay[STRIP_SIZE];

  private final Vector[] scatterVels = new Vector[STRIP_SIZE];
  private final TextDisplay[] arrows = new TextDisplay[3];
  private final TextDisplay[] resultLabels = new TextDisplay[3];
  private final TextDisplay[] descDisplays = new TextDisplay[3];
  private final float[] descProgress = new float[3];
  private final boolean[] descTargetIn = new boolean[3];
  private final Location[] resultPos = new Location[3];
  private final boolean[] revealStarted = new boolean[3];
  private final boolean[] revealDone = new boolean[3];
  private final boolean[] resultRemoved = new boolean[3];
  private TextDisplay titleDisplay;

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

    if (!selectionMade) {
      if (currentTick > RIFTS_OPEN_END && currentTick <= SCROLL_END_TICK) {
        tickScroll(world);
      }
      tickScatter(world);
      tickReveal(world);

      if (currentTick >= SELECTION_READY_TICK) {
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

  /** Tier-coloured essence spirals from the player's eyes toward the centre. */
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

  /** Both rifts spin open from nothing out to full size, and the result indicator arrows appear. */
  private void tickRiftsOpen(org.bukkit.World world) {
    double t = Math.min(1.0, (double) (currentTick - SPLIT_END) / (RIFTS_OPEN_END - SPLIT_END));
    double radius = RIFT_RADIUS * t;
    double angle = currentTick * 0.9;
    drawRift(world, leftRift, radius, angle);
    drawRift(world, rightRift, radius, -angle);
    if (currentTick == SPLIT_END + 1) {
      world.playSound(center, "custom.portalopenrift", 0.55f, 0.9f);
      world.playSound(center, "custom.portalopenrift", 0.55f, 1.3f);
      spawnArrows(world);
    }
  }

  /** Spawns the three gold indicator arrows above the result slots. */
  private void spawnArrows(org.bukkit.World world) {
    for (int i = 0; i < 3; i++) {
      Location arrowLoc =
          center.clone().add(rightDir.clone().multiply(INDICATOR_OFFSETS[i])).add(0, 0.7, 0);
      final int idx = i;
      arrows[idx] =
          world.spawn(
              arrowLoc,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.text(
                    Component.text("▼").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                e.setTransformation(textTransform(0.5f));
              });
      player.showEntity(plugin, arrows[idx]);
    }
  }

  /** Keeps both rifts shimmering while the reel spins/settles and the player looks over results. */
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

  /**
   * Scrolls the strip left-to-right and decelerates it to a stop, exactly like {@link
   * EnchantAnimation}'s reel — five stones visible at a time, evenly spaced, the real three rolls
   * always resting at {@link #RESULT_INDICES} once it lands.
   */
  private void tickScroll(org.bukkit.World world) {
    int tick = currentTick - RIFTS_OPEN_END;
    double scrollProgress = Math.min(1.0, (double) tick / SCROLL_DURATION);
    double scroll = STRIP_TOTAL_SCROLL * easeInOutQuart(scrollProgress);

    for (int i = 0; i < STRIP_SIZE; i++) {
      ItemDisplay item = stripItems[i];
      if (item == null) {
        item = spawnStripItem(world, i);
      }
      if (item == null || item.isDead()) continue;

      double rightAxis = i * STRIP_SPACING + STRIP_INITIAL_OFFSET - scroll;
      if (Math.abs(rightAxis) > STRIP_HIDE_RADIUS) {
        item.teleport(center.clone().add(0, -20, 0));
      } else {
        item.teleport(center.clone().add(rightDir.clone().multiply(rightAxis)));
      }
    }

    if (tick < SCROLL_DURATION && tick % 2 == 0) {
      float pitch = (float) (2.0 - 1.5 * scrollProgress);
      world.playSound(center, "custom.objectflybywhoosh", 0.4f, pitch);
    }

    if (currentTick == SCROLL_END_TICK) {
      world.playSound(center, "custom.magiclockimpact", 0.6f, 1.0f);
      for (int r = 0; r < 3; r++) {
        ItemDisplay item = stripItems[RESULT_INDICES[r]];
        if (item != null && !item.isDead()) {
          resultPos[r] = item.getLocation().clone();
        }
      }
      triggerScatter(world);
    }
  }

  /** Lazily spawns one strip slot's display — a real roll at a result index, otherwise a filler. */
  private ItemDisplay spawnStripItem(org.bukkit.World world, int index) {
    PrestigeManager.StoneRoll roll = rollForSlot(index);
    ItemStack stack = EvolutionStone.create(roll.rarity(), roll.enchantName());
    ItemDisplay display =
        world.spawn(
            center.clone().add(0, -20, 0),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(stack);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.55f));
            });
    player.showEntity(plugin, display);
    stripItems[index] = display;
    return display;
  }

  /** Returns the real roll for a result index, otherwise a random cosmetic filler. */
  private PrestigeManager.StoneRoll rollForSlot(int index) {
    for (int r = 0; r < RESULT_INDICES.length; r++) {
      if (RESULT_INDICES[r] == index) return stoneRolls.get(r);
    }
    List<RarityConfig> rarities = EnchantAnimationConfig.getRarities();
    RarityConfig rarity =
        !rarities.isEmpty()
            ? rarities.get(ThreadLocalRandom.current().nextInt(rarities.size()))
            : stoneRolls.get(0).rarity();
    List<String> enchants = EnchantAnimationConfig.getEnchantOrder();
    String enchant =
        !enchants.isEmpty()
            ? enchants.get(ThreadLocalRandom.current().nextInt(enchants.size()))
            : stoneRolls.get(0).enchantName();
    return new PrestigeManager.StoneRoll(rarity, enchant);
  }

  /** Assigns outward velocities to every visible, non-result strip item. */
  private void triggerScatter(org.bukkit.World world) {
    for (int i = 0; i < STRIP_SIZE; i++) {
      if (isResultIndex(i)) continue;
      ItemDisplay item = stripItems[i];
      if (item == null || item.isDead()) continue;

      double rightAxis = i * STRIP_SPACING + STRIP_INITIAL_OFFSET - STRIP_TOTAL_SCROLL;
      if (Math.abs(rightAxis) > STRIP_HIDE_RADIUS) {
        item.remove();
        stripItems[i] = null;
        continue;
      }

      double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
      double speed = 0.14 + ThreadLocalRandom.current().nextDouble() * 0.22;
      double upward = 0.06 + ThreadLocalRandom.current().nextDouble() * 0.10;
      scatterVels[i] = new Vector(Math.cos(angle) * speed, upward, Math.sin(angle) * speed);
    }
    scatterTriggered = true;
  }

  /** Moves scattered (non-result) strip items outward each tick and removes them at the end. */
  private void tickScatter(org.bukkit.World world) {
    if (!scatterTriggered) return;
    int st = currentTick - SCROLL_END_TICK;
    if (st <= 0 || st > SCATTER_DURATION) return;

    float shrinkT = (float) st / SCATTER_DURATION;
    for (int i = 0; i < STRIP_SIZE; i++) {
      if (isResultIndex(i)) continue;
      ItemDisplay item = stripItems[i];
      if (item == null || item.isDead() || scatterVels[i] == null) continue;

      scatterVels[i].setY(scatterVels[i].getY() - 0.010);
      item.teleport(item.getLocation().clone().add(scatterVels[i]));

      float scale = Math.max(0.55f * (1f - shrinkT), 0.001f);
      item.setTransformation(itemTransform(scale));

      if (st == SCATTER_DURATION) {
        item.remove();
        stripItems[i] = null;
      }
    }
  }

  /** Reveals each result: settles into place and types out its name above it, staggered. */
  private void tickReveal(org.bukkit.World world) {
    if (!arrowsRemoved && currentTick >= REVEAL_START_TICK - 3) {
      arrowsRemoved = true;
      for (TextDisplay arrow : arrows) {
        if (arrow != null && !arrow.isDead()) arrow.remove();
      }
    }
    if (currentTick == REVEAL_START_TICK - 1) {
      spawnDescPanels(world);
    }

    for (int r = 0; r < 3; r++) {
      int slotStart = REVEAL_START_TICK + r * REVEAL_STAGGER;
      if (currentTick < slotStart) continue;

      int revealTick = currentTick - slotStart;
      if (!revealStarted[r]) {
        revealStarted[r] = true;
        float pitch = 0.8f + r * 0.15f;
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, pitch);
      }

      if (revealTick >= REVEAL_DURATION) {
        if (!revealDone[r]) revealDone[r] = true;
        tickTypewriter(world, r, revealTick - REVEAL_DURATION);
        continue;
      }

      float t = (float) revealTick / REVEAL_DURATION;
      ItemDisplay item = stripItems[RESULT_INDICES[r]];
      if (item != null && !item.isDead()) {
        float scale = Math.max(0.55f + 0.2f * (float) easeOutCubic(t), 0.001f);
        item.setTransformation(itemTransform(scale));
      }
    }
  }

  /** Spawns the (initially empty) name label and hidden description panel above each result. */
  private void spawnDescPanels(org.bukkit.World world) {
    if (labelsSpawned) return;
    labelsSpawned = true;

    for (int r = 0; r < 3; r++) {
      if (resultPos[r] == null) continue;
      PrestigeManager.StoneRoll roll = stoneRolls.get(r);
      final int idx = r;

      Location labelPos = resultPos[r].clone().add(0, 0.55, 0);
      resultLabels[idx] =
          world.spawn(
              labelPos,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setBackgroundColor(Color.fromARGB(210, 10, 10, 20));
                e.setPersistent(false);
                e.setTransformation(textTransform(0.6f));
              });
      player.showEntity(plugin, resultLabels[idx]);

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
      final Component descText = descBuilder.build().decoration(TextDecoration.ITALIC, false);

      Location descPos = resultPos[r].clone().add(0, 1.05, 0);
      descDisplays[idx] =
          world.spawn(
              descPos,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.text(descText);
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
  }

  /** Types out the stone's name above its result slot, character by character. */
  private void tickTypewriter(org.bukkit.World world, int r, int writeTick) {
    if (resultLabels[r] == null || resultLabels[r].isDead()) return;

    PrestigeManager.StoneRoll roll = stoneRolls.get(r);
    String rawName = stoneName(roll);
    String name = FontUtils.toSmallCaps(rawName);
    int chars = Math.min(writeTick / 2 + 1, name.length());
    boolean done = chars >= name.length();

    Component label =
        Component.text(name.substring(0, chars) + (done ? "" : "▌"))
            .color(roll.rarity().textColor())
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false);
    resultLabels[r].text(label);

    if (writeTick % 2 == 0 && !done) {
      float pitch = 1.4f + (chars / (float) name.length()) * 0.6f;
      world.playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, 0.2f, pitch);
    }
    if (done && writeTick == (name.length() - 1) * 2) {
      world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.8f);
    }
  }

  /** Returns the stone's unique display name, falling back to "{rarity} Evolution Stone". */
  private String stoneName(PrestigeManager.StoneRoll roll) {
    String name =
        StoneEffectRegistry.getName(roll.enchantName().toLowerCase(), roll.rarity().displayName());
    return name != null ? name : roll.rarity().displayName() + " Evolution Stone";
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

  /** Resolves which result (if any) the player is currently looking at. */
  private void tickHighlightDetection() {
    Location eye = player.getEyeLocation();
    Vector direction = eye.getDirection();

    int newHighlight = -1;
    double bestDot = 0.9;
    for (int i = 0; i < 3; i++) {
      if (resultRemoved[i] || resultPos[i] == null) continue;
      Vector toStone = resultPos[i].toVector().subtract(eye.toVector());
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
        ItemDisplay item = stripItems[RESULT_INDICES[prev]];
        if (item != null && !item.isDead()) item.setGlowing(false);
      }
      if (newHighlight >= 0) {
        descTargetIn[newHighlight] = true;
        ItemDisplay item = stripItems[RESULT_INDICES[newHighlight]];
        if (item != null && !item.isDead()) item.setGlowing(true);
        player.playSound(resultPos[newHighlight], Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
      }
    }
  }

  /** Commits to a stone: locks the selection and starts the flight-to-player finale. */
  private void applySelection(int index) {
    if (selectionMade) return;
    selectedIndex = index;
    selectionMade = true;
    selectionTick = currentTick;
    // Done here rather than gated on tickFlight's sp == 0 — confirmSelection() can fire from a
    // click event at any point between ticks, so sp's first observed value isn't guaranteed to be
    // exactly 0.
    removeResultDisplays(index);
    var world = player.getWorld();
    if (world != null) {
      world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
      world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 2.0f);
    }
  }

  /** Drives every phase from the moment a stone is chosen through to the final delivery. */
  private void tickPostSelection(org.bukkit.World world) {
    int sp = currentTick - selectionTick;

    if (sp <= FLIGHT_DURATION) {
      tickFlight(world, sp);
    }
    if (sp == FLIGHT_DURATION && !outcomeApplied) {
      outcomeApplied = true;
      onSelect.accept(player, stoneRolls.get(selectedIndex));
    }

    int bp = sp - FLIGHT_DURATION;
    if (bp > 0 && bp <= 30) {
      tickBurst(world, bp);
    }
    if (bp > 30 && bp <= 60) {
      tickGlory(world, bp - 30);
    }
    if (bp == 61) {
      collapseRifts(world);
    }
    if (bp == 65) {
      finished = true;
      removeAllDisplays();
      if (onComplete != null) onComplete.run();
    }
  }

  /**
   * Unchosen stones sink and fizzle (staggered, matching {@link EnchantAnimation}); the chosen
   * stone flies from its result slot up to the player's eye.
   */
  private void tickFlight(org.bukkit.World world, int sp) {
    int[] nonSelected = nonSelectedIndices();
    for (int ni = 0; ni < nonSelected.length; ni++) {
      int r = nonSelected[ni];
      int start = 2 + ni * 8;
      if (sp < start || resultRemoved[r]) continue;

      int fp = sp - start;
      ItemDisplay item = stripItems[RESULT_INDICES[r]];
      if (item != null && !item.isDead() && fp <= 8) {
        if (resultPos[r] != null) {
          item.teleport(resultPos[r].clone().add(0, -fp * 0.20, 0));
        }
        float scale = Math.max(0.001f, 0.75f * (1f - fp / 8f));
        item.setTransformation(itemTransform(scale));
      }

      if (fp == 8) {
        resultRemoved[r] = true;
        if (item != null && !item.isDead()) item.remove();
        stripItems[RESULT_INDICES[r]] = null;
        removeResultDisplays(r);
        if (resultPos[r] != null) {
          world.spawnParticle(Particle.POOF, resultPos[r], 10, 0.25, 0.25, 0.25, 0.05);
          world.playSound(resultPos[r], Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.8f);
        }
      }
    }

    ItemDisplay selected = stripItems[RESULT_INDICES[selectedIndex]];
    if (selected != null && !selected.isDead() && sp <= FLIGHT_DURATION) {
      double t = easeInCubic((double) sp / FLIGHT_DURATION);
      Location origin = resultPos[selectedIndex];
      Location target = player.getEyeLocation();
      if (origin != null) {
        selected.teleport(lerp(origin, target, t));
      }
      selected.setTransformation(itemTransform(0.75f));
    }

    if (sp == FLIGHT_DURATION) {
      resultRemoved[selectedIndex] = true;
      if (selected != null && !selected.isDead()) selected.remove();
      stripItems[RESULT_INDICES[selectedIndex]] = null;
    }
  }

  /** Removes the name label and description panel for a result slot, if present. */
  private void removeResultDisplays(int r) {
    if (resultLabels[r] != null && !resultLabels[r].isDead()) {
      resultLabels[r].remove();
      resultLabels[r] = null;
    }
    if (descDisplays[r] != null && !descDisplays[r].isDead()) {
      descDisplays[r].remove();
      descDisplays[r] = null;
    }
  }

  /** Returns the two non-selected result indices. */
  private int[] nonSelectedIndices() {
    int[] result = new int[2];
    int idx = 0;
    for (int r = 0; r < 3 && idx < 2; r++) {
      if (r != selectedIndex) result[idx++] = r;
    }
    return result;
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
              e.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
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
  }

  /** Returns {@code true} if the given strip index is one of the three result slots. */
  private static boolean isResultIndex(int index) {
    for (int r : RESULT_INDICES) if (r == index) return true;
    return false;
  }

  /** Removes every display entity this animation may have spawned. */
  private void removeAllDisplays() {
    for (int i = 0; i < STRIP_SIZE; i++) {
      if (stripItems[i] != null && !stripItems[i].isDead()) stripItems[i].remove();
    }
    for (int r = 0; r < 3; r++) {
      if (arrows[r] != null && !arrows[r].isDead()) arrows[r].remove();
      if (resultLabels[r] != null && !resultLabels[r].isDead()) resultLabels[r].remove();
      if (descDisplays[r] != null && !descDisplays[r].isDead()) descDisplays[r].remove();
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

  /** Fast launch, hard brake — exactly like a mechanical reel. */
  private static double easeInOutQuart(double x) {
    return x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
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
