package org.benski.evolv3.animations;

import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseEnchantAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.registry.ConfigRegistry;
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
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * CS:GO-style enchant selection animation.
 *
 * <p>A strip of rarity-coloured concrete blocks scrolls right-to-left and decelerates to stop with
 * three result blocks landing under gold arrow indicators. On landing, all non-result blocks
 * scatter outward like an explosion. Each rarity block then shrinks away while a representative
 * enchant item rises from within and its name is typed out. The player looks at an item and
 * left-clicks to select it. The two unchosen items sink downward one by one, the selected item
 * fades upward, then a dramatic hologram announces the unlock before fading out.
 *
 * <p>All rarity colours, block materials, and enchant icons are driven by {@link
 * EnchantAnimationConfig}, which reads from {@code enchants.yml}.
 */
public class EnchantAnimation extends BaseEnchantAnimation {
  private static final double SPACING = 0.7;
  private static final double INITIAL_OFFSET = 5.0;
  private static final double TOTAL_SCROLL = 22 * SPACING + INITIAL_OFFSET;
  private static final int[] RESULT_INDICES = {20, 22, 24};
  private static final int STRIP_SIZE = 30;

  private static final int SCATTER_DURATION = 18;
  private static final int REVEAL_STAGGER = 14;
  private static final int REVEAL_DURATION = 22;
  private static final int MAX_DURATION = 800;

  private final int scrollEndTick;
  private final int revealStartTick;
  private final int selectionReady;

  private static final float BLOCK_SCALE = 0.52f;
  private static final float ITEM_SCALE = 0.85f;

  private static final Random RAND = new Random();

  private Vector forwardDir;
  private Vector rightDir;
  private Location stripCenter;

  /**
   * -- GETTER -- Returns the item that was in the player's main hand when the animation started.
   * Used by to preserve the pickaxe across a player disconnect.
   *
   * @return the original item, or {@code null} if the animation has not started yet
   */
  @Getter private ItemStack originalItem;

  private final BlockDisplay[] stripBlocks = new BlockDisplay[STRIP_SIZE];
  private final TextDisplay[] arrows = new TextDisplay[3];
  private final TextDisplay[] labels = new TextDisplay[3];
  private final ItemDisplay[] enchantItems = new ItemDisplay[3];
  private final boolean[] revealStarted = new boolean[3];
  private final boolean[] revealDone = new boolean[3];
  private final Location[] resultPos = new Location[3];
  private final Vector[] scatterVels = new Vector[STRIP_SIZE];
  private final boolean[] nonSelRemoved = new boolean[3];
  private final Material[] fillerMaterials = new Material[STRIP_SIZE];

  /** Full per-slot rarity for all 30 strip positions — exposed to the scoreboard. */
  private final RarityConfig[] stripRarities = new RarityConfig[STRIP_SIZE];

  /** TextDisplay entities showing the brief enchant description + ▼ arrow on hover. */
  private final TextDisplay[] descDisplays = new TextDisplay[3];

  /** Animation progress per slot: 0 = hidden, 1 = fully visible. */
  private final float[] descProgress = new float[3];

  /** Whether each slot's description should be animating toward visible. */
  private final boolean[] descTargetIn = new boolean[3];

  private static final float DESC_SCALE = 0.42f;
  private static final float DESC_IN_STEP = 0.14f;
  private static final float DESC_OUT_STEP = 0.20f;

  private boolean labelsSpawned = false;
  private boolean scatterActive = false;

  /**
   * Per-option flag: true when the option is a level-up of an existing enchant; false for a new
   * unlock.
   */
  private final boolean[] isUpgradeOptions = new boolean[3];

  private boolean arrowsRemoved = false;

  /**
   * Creates an enchant selection animation.
   *
   * @param plugin the plugin instance
   * @param player the player receiving the selection
   * @param enchantOptions the three options to present
   * @param onSelect callback invoked when an option is chosen
   * @param scrollEndTick how many ticks the strip scrolls before stopping (controls animation speed
   *     — lower = faster)
   */
  public EnchantAnimation(
      JavaPlugin plugin,
      org.bukkit.entity.Player player,
      List<EnchantOption> enchantOptions,
      BiConsumer<org.bukkit.entity.Player, EnchantOption> onSelect,
      int scrollEndTick) {
    super(plugin, player, enchantOptions, onSelect, MAX_DURATION);
    this.scrollEndTick = scrollEndTick;
    this.revealStartTick = scrollEndTick + SCATTER_DURATION + 4;
    this.selectionReady = revealStartTick + 2 * REVEAL_STAGGER + REVEAL_DURATION + 16;
  }

  @Override
  public int getSelectionReadyTick() {
    return selectionReady;
  }

  @Override
  public double getLinearScrollProgress() {
    return Math.min(1.0, (double) getCurrentTick() / scrollEndTick);
  }

  /**
   * Returns the per-slot {@link RarityConfig} for all {@value STRIP_SIZE} strip positions. Filler
   * slots hold their rolled rarity; result slots hold the actual option rarity. May contain {@code
   * null} entries if called before {@link #doStart()} has run.
   *
   * @return the strip rarity array (length 30)
   */
  @Override
  public RarityConfig[] getStripRarities() {
    return stripRarities;
  }

  @Override
  public Location getOptionPosition(int i) {
    if (i < 0 || i >= RESULT_INDICES.length) return null;
    if (enchantItems[i] != null && !enchantItems[i].isDead()) {
      return enchantItems[i].getLocation();
    }
    if (resultPos[i] != null) return resultPos[i];
    if (stripCenter == null) return null;
    double rightAxis = RESULT_INDICES[i] * SPACING + INITIAL_OFFSET - TOTAL_SCROLL;
    return stripCenter.clone().add(rightDir.clone().multiply(rightAxis));
  }

  @Override
  protected void doStart() {
    Location playerLoc = player.getLocation();
    World world = playerLoc.getWorld();
    if (world == null) return;

    player.spawnParticle(Particle.ELDER_GUARDIAN, playerLoc, 1);

    forwardDir = player.getLocation().getDirection().setY(0).normalize();
    rightDir = forwardDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();

    double eyeOffset = player.getEyeLocation().getY() - playerLoc.getY() - 0.3;
    stripCenter = playerLoc.clone().add(0, eyeOffset, 0).add(forwardDir.clone().multiply(2.8));

    originalItem = player.getInventory().getItemInMainHand().clone();
    if (originalItem.getType() == Material.AIR) {
      originalItem = new ItemStack(Material.DIAMOND_PICKAXE);
    }
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    String origItemId = ItemStackExtensions.getData(originalItem, "id");
    PlayerDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pickaxeDC != null && origItemId != null) {
      Pickaxe profile = pickaxeDC.getPickaxeByItemId(origItemId);
      if (profile != null && profile.enchants != null) {
        for (int r = 0; r < enchantOptions.size(); r++) {
          String internalName = enchantOptions.get(r).name().toLowerCase().replace(" ", "");
          isUpgradeOptions[r] = profile.enchants.getEnchant(internalName) != null;
        }
      }
    }

    for (int i = 0; i < STRIP_SIZE; i++) {
      if (!isResultIndex(i)) {
        RarityConfig rarity = EnchantAnimationConfig.rollRarity();
        if (rarity == null && !EnchantAnimationConfig.getRarities().isEmpty()) {
          rarity = EnchantAnimationConfig.getRarities().get(0);
        }
        fillerMaterials[i] = rarity != null ? rarity.concreteMaterial() : Material.WHITE_CONCRETE;
        stripRarities[i] = rarity;
      }
    }
    for (int r = 0; r < RESULT_INDICES.length; r++) {
      stripRarities[RESULT_INDICES[r]] = enchantOptions.get(r).rarity();
    }

    Location undergroundLoc = stripCenter.clone().add(0, -20, 0);
    for (int i = 0; i < STRIP_SIZE; i++) {
      Material mat = materialForSlot(i);
      final int idx = i;

      BlockDisplay block =
          world.spawn(
              undergroundLoc,
              BlockDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setBlock(mat.createBlockData());
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setTransformation(centeredTransform(BLOCK_SCALE));
              });
      player.showEntity(plugin, block);
      stripBlocks[idx] = block;
    }

    double[] indicatorOffsets = {-1.4, 0.0, 1.4};
    for (int i = 0; i < 3; i++) {
      final int idx = i;
      Location arrowLoc =
          stripCenter.clone().add(rightDir.clone().multiply(indicatorOffsets[idx])).add(0, 0.65, 0);

      TextDisplay arrow =
          world.spawn(
              arrowLoc,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.text(
                    Component.text("\u25BC")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD));
                e.setTransformation(
                    new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.5f, 0.5f, 0.5f),
                        new AxisAngle4f(0, 0, 1, 0)));
              });
      player.showEntity(plugin, arrow);
      arrows[idx] = arrow;
    }

    world.playSound(playerLoc, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.5f);
    world.playSound(playerLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 0.6f);
  }

  @Override
  protected void doTick(double progress) {
    Location playerLoc = player.getLocation();
    World world = playerLoc.getWorld();
    if (world == null) return;

    int tick = getCurrentTick();

    if (!selectionMade) {
      tickScroll(world, tick);
      tickScatter(world, tick);
      tickReveal(world, tick);
      tickIdleParticles(world, tick);
      tickDescriptions();
    } else {
      tickSelectionAnim(world, playerLoc, tick);
    }
  }

  @Override
  protected void onHighlightChanged(int index, boolean highlighted) {
    if (index >= 0 && index < 3) {
      descTargetIn[index] = highlighted;
    }
    if (highlighted && resultPos[index] != null) {
      player.playSound(resultPos[index], Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
    }
  }

  @Override
  protected void doSelect(int index) {
    World world = player.getWorld();

    world.playSound(stripCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
    world.playSound(stripCenter, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 2.0f);
  }

  @Override
  protected void doComplete() {
    for (BlockDisplay b : stripBlocks) {
      if (b != null && !b.isDead()) b.remove();
    }
    for (TextDisplay a : arrows) {
      if (a != null && !a.isDead()) a.remove();
    }
    for (TextDisplay l : labels) {
      if (l != null && !l.isDead()) l.remove();
    }
    for (ItemDisplay i : enchantItems) {
      if (i != null && !i.isDead()) i.remove();
    }
    for (TextDisplay d : descDisplays) {
      if (d != null && !d.isDead()) d.remove();
    }
    if (!itemReturned && player.isOnline() && originalItem != null) {
      player.getInventory().setItemInMainHand(originalItem);
    }
  }

  private void tickScroll(World world, int tick) {
    double scrollProgress = Math.min(1.0, (double) tick / scrollEndTick);
    double scroll = TOTAL_SCROLL * easeInOutQuart(scrollProgress);

    for (int i = 0; i < STRIP_SIZE; i++) {
      BlockDisplay block = stripBlocks[i];
      if (block == null || block.isDead()) continue;
      double rightAxis = i * SPACING + INITIAL_OFFSET - scroll;
      if (Math.abs(rightAxis) > 4.2) {
        block.teleport(stripCenter.clone().add(0, -20, 0));
      } else {
        block.teleport(stripCenter.clone().add(rightDir.clone().multiply(rightAxis)));
      }
    }

    if (tick < scrollEndTick && tick % 2 == 0) {
      float pitch = (float) (2.0 - 1.5 * scrollProgress);
      world.playSound(stripCenter, Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, pitch);
    }

    if (tick == scrollEndTick) {
      world.playSound(stripCenter, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
      world.playSound(stripCenter, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);

      for (int r = 0; r < RESULT_INDICES.length; r++) {
        BlockDisplay block = stripBlocks[RESULT_INDICES[r]];
        if (block != null && !block.isDead()) {
          resultPos[r] = block.getLocation().clone();
        }
      }

      triggerScatter(world);
      spawnRarityLabels(world);
    }
  }

  /** Assigns outward velocities to every visible, non-result block and removes hidden ones. */
  private void triggerScatter(World world) {
    for (int i = 0; i < STRIP_SIZE; i++) {
      if (isResultIndex(i)) continue;
      BlockDisplay block = stripBlocks[i];
      if (block == null || block.isDead()) continue;

      double rightAxis = i * SPACING + INITIAL_OFFSET - TOTAL_SCROLL;
      if (Math.abs(rightAxis) > 4.2) {
        block.remove();
        stripBlocks[i] = null;
        continue;
      }

      double angle = RAND.nextDouble() * 2 * Math.PI;
      double speed = 0.14 + RAND.nextDouble() * 0.22;
      double upward = 0.06 + RAND.nextDouble() * 0.10;
      scatterVels[i] = new Vector(Math.cos(angle) * speed, upward, Math.sin(angle) * speed);
    }
    scatterActive = true;
  }

  /** Moves scattered blocks outward each tick and removes them at the end. */
  private void tickScatter(World world, int tick) {
    if (!scatterActive) return;
    int st = tick - scrollEndTick;
    if (st <= 0 || st > SCATTER_DURATION) return;

    float shrinkT = (float) st / SCATTER_DURATION;

    for (int i = 0; i < STRIP_SIZE; i++) {
      if (isResultIndex(i)) continue;
      BlockDisplay block = stripBlocks[i];
      if (block == null || block.isDead()) continue;
      if (scatterVels[i] == null) continue;

      scatterVels[i].setY(scatterVels[i].getY() - 0.010);
      Location newLoc = block.getLocation().clone().add(scatterVels[i]);
      block.teleport(newLoc);

      float scale = Math.max(BLOCK_SCALE * (1f - shrinkT), 0.001f);
      block.setTransformation(centeredTransform(scale));

      if (st == SCATTER_DURATION) {
        block.remove();
        stripBlocks[i] = null;
      }
    }
  }

  private void tickReveal(World world, int tick) {
    if (!arrowsRemoved && tick >= revealStartTick - 3) {
      arrowsRemoved = true;
      for (TextDisplay arrow : arrows) {
        if (arrow != null && !arrow.isDead()) arrow.remove();
      }
    }

    for (int r = 0; r < 3; r++) {
      int slotStart = revealStartTick + r * REVEAL_STAGGER;
      if (tick < slotStart) continue;

      int revealTick = tick - slotStart;
      if (!revealStarted[r]) {
        revealStarted[r] = true;
        spawnEnchantItem(world, r);
        float pitch = 0.8f + r * 0.15f;
        world.playSound(stripCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, pitch);
      }

      if (revealTick >= REVEAL_DURATION) {
        if (!revealDone[r]) {
          revealDone[r] = true;
          BlockDisplay rb = stripBlocks[RESULT_INDICES[r]];
          if (rb != null && !rb.isDead()) rb.setTransformation(centeredTransform(0.001f));
          if (enchantItems[r] != null && !enchantItems[r].isDead()) {
            enchantItems[r].setTransformation(itemTransform(ITEM_SCALE, 0f));
          }
        }
        tickTypewriter(world, r, revealTick - REVEAL_DURATION);
        continue;
      }

      float t = (float) revealTick / REVEAL_DURATION;

      float blockScale = Math.max(BLOCK_SCALE * (1f - t), 0.001f);
      BlockDisplay rb = stripBlocks[RESULT_INDICES[r]];
      if (rb != null && !rb.isDead()) rb.setTransformation(centeredTransform(blockScale));

      float itemScale = Math.max(ITEM_SCALE * (float) easeOutCubic(t), 0.001f);
      float yOffset = -0.25f * (1f - t);
      if (enchantItems[r] != null && !enchantItems[r].isDead()) {
        enchantItems[r].setTransformation(itemTransform(itemScale, yOffset));
      }
    }
  }

  private void tickTypewriter(World world, int r, int writeTick) {
    if (labels[r] == null || labels[r].isDead()) return;

    String enchantName = FontUtils.toSmallCaps(enchantOptions.get(r).name());
    int chars = Math.min(writeTick / 2 + 1, enchantName.length());
    boolean done = chars >= enchantName.length();

    EnchantOption opt = enchantOptions.get(r);
    Component label =
        Component.text(FontUtils.toSmallCaps(opt.rarity().displayName()))
            .color(opt.rarity().textColor())
            .decorate(TextDecoration.BOLD)
            .append(Component.newline())
            .append(
                Component.text(enchantName.substring(0, chars) + (done ? "" : "\u258c"))
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, false));
    if (done) {
      String statusText = isUpgradeOptions[r] ? "+1 Level" : "Unlock";
      label =
          label
              .append(Component.newline())
              .append(
                  Component.text(FontUtils.toSmallCaps(statusText))
                      .color(opt.rarity().textColor())
                      .decoration(TextDecoration.BOLD, false));
    }
    labels[r].text(label);

    if (writeTick % 2 == 0 && !done) {
      float pitch = 1.4f + (chars / (float) enchantName.length()) * 0.6f;
      world.playSound(stripCenter, Sound.BLOCK_NOTE_BLOCK_PLING, 0.2f, pitch);
    }
    if (done && writeTick == (enchantName.length() - 1) * 2) {
      world.playSound(stripCenter, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f);
    }
  }

  private void tickIdleParticles(World world, int tick) {
    if (tick <= scrollEndTick) return;

    for (int r = 0; r < 3; r++) {
      Location pos = getOptionPosition(r);
      if (pos == null) continue;
      EnchantOption opt = enchantOptions.get(r);

      if (tick % 2 == 0) {
        double angle = tick * 0.18 + r * 2.09;
        for (int p = 0; p < 2; p++) {
          double a = angle + p * Math.PI;
          world.spawnParticle(
              Particle.DUST,
              pos.clone().add(Math.cos(a) * 0.38, Math.sin(a * 0.7) * 0.15, Math.sin(a) * 0.38),
              1,
              0,
              0,
              0,
              0,
              new Particle.DustOptions(opt.rarity().particleColor(), 1.0f));
        }
      }
      if (highlightedIndex == r && tick % 3 == 0) {
        world.spawnParticle(Particle.END_ROD, pos, 2, 0.12, 0.12, 0.12, 0.02);
      }
    }

    if (tick == selectionReady) {
      String promptRaw =
          ConfigRegistry.getInstance()
              .get(ConfigNames.MESSAGES)
              .getString(
                  ConfigPaths.Messages.EnchantAnimation.SELECTION_PROMPT,
                  "&#FFFF55Look at an enchant and &a&lLEFT-CLICK &#FFFF55to claim it!");
      player.sendActionBar(
          LegacyComponentSerializer.legacySection()
              .deserialize(Utils.formatMessage(player, promptRaw)));
    }
  }

  private void tickSelectionAnim(World world, Location playerLoc, int tick) {
    int sp = tick - selectionTick;
    EnchantOption selected = enchantOptions.get(selectedIndex);

    int[] nonSel = getNonSelectedIndices();
    for (int ni = 0; ni < nonSel.length; ni++) {
      int r = nonSel[ni];
      int start = 2 + ni * 8;
      if (sp < start || nonSelRemoved[r]) continue;

      int fp = sp - start;

      ItemDisplay item = enchantItems[r];
      if (item != null && !item.isDead() && fp <= 8) {
        if (resultPos[r] != null) {
          item.teleport(resultPos[r].clone().add(0, -fp * 0.20, 0));
        }
        float scale = ITEM_SCALE * Math.max(0f, 1f - fp / 8f);
        item.setTransformation(itemTransform(Math.max(scale, 0.001f), 0f));
      }

      if (fp == 8) {
        if (item != null && !item.isDead()) {
          item.remove();
          enchantItems[r] = null;
        }
        if (labels[r] != null && !labels[r].isDead()) {
          labels[r].remove();
          labels[r] = null;
        }
        if (descDisplays[r] != null && !descDisplays[r].isDead()) {
          descDisplays[r].remove();
          descDisplays[r] = null;
        }
        nonSelRemoved[r] = true;

        if (resultPos[r] != null) {
          world.spawnParticle(Particle.POOF, resultPos[r], 10, 0.25, 0.25, 0.25, 0.05);
          world.playSound(resultPos[r], Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.8f);
        }
      }
    }

    ItemDisplay selItem = enchantItems[selectedIndex];
    if (sp == 0) {
      if (labels[selectedIndex] != null && !labels[selectedIndex].isDead()) {
        labels[selectedIndex].remove();
        labels[selectedIndex] = null;
      }
      if (descDisplays[selectedIndex] != null && !descDisplays[selectedIndex].isDead()) {
        descDisplays[selectedIndex].remove();
        descDisplays[selectedIndex] = null;
      }
      world.playSound(stripCenter, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.8f);
      world.playSound(stripCenter, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.4f, 2.0f);
    }

    if (selItem != null && !selItem.isDead() && sp <= 25) {
      double t = easeInCubic((double) sp / 25.0);
      Location origin = resultPos[selectedIndex];
      Location target = player.getEyeLocation();

      if (origin != null) {
        selItem.teleport(lerpLocation(origin, target, t));
      }
      selItem.setTransformation(itemTransform(ITEM_SCALE, 0f));
    }

    if (sp == 25) {
      if (selItem != null && !selItem.isDead()) {
        selItem.remove();
        enchantItems[selectedIndex] = null;
      }

      if (!itemReturned && player.isOnline()) {
        itemReturned = true;
        player.getInventory().setItemInMainHand(originalItem);
      }

      world.playSound(playerLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
      world.playSound(playerLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);

      for (int p = 0; p < 40; p++) {
        world.spawnParticle(
            Particle.DUST,
            playerLoc
                .clone()
                .add(
                    (RAND.nextDouble() - 0.5) * 1.8,
                    RAND.nextDouble() * 2.2,
                    (RAND.nextDouble() - 0.5) * 1.8),
            1,
            0,
            0,
            0,
            0,
            new Particle.DustOptions(selected.rarity().particleColor(), 1.8f));
      }
      world.spawnParticle(Particle.ENCHANT, playerLoc.clone().add(0, 1, 0), 60, 0.6, 0.6, 0.6, 1.2);
    }

    if (sp >= 25 && sp <= 55) {
      if ((sp - 25) % 10 == 0) {
        if (sp < 35) {
          String phaseAText = isUpgradeOptions[selectedIndex] ? "+1 LEVEL" : "UNLOCKED";
          player.sendActionBar(
              Component.text(phaseAText)
                  .color(selected.rarity().textColor())
                  .decorate(TextDecoration.BOLD));
        } else {
          player.sendActionBar(
              Component.text(FontUtils.toSmallCaps(selected.name()))
                  .color(NamedTextColor.WHITE)
                  .decorate(TextDecoration.BOLD)
                  .append(
                      Component.text("  \u00B7  ")
                          .color(NamedTextColor.GRAY)
                          .decoration(TextDecoration.BOLD, false))
                  .append(
                      Component.text(FontUtils.toSmallCaps(selected.rarity().displayName()))
                          .color(selected.rarity().textColor())
                          .decoration(TextDecoration.BOLD, false)));
        }
      }

      if (sp % 2 == 0) {
        double angle = (sp - 25) * 0.35;
        for (int p = 0; p < 3; p++) {
          double a = angle + p * (2 * Math.PI / 3);
          world.spawnParticle(
              Particle.DUST,
              playerLoc
                  .clone()
                  .add(Math.cos(a) * 1.1, 0.4 + Math.sin(a * 0.7) * 0.4, Math.sin(a) * 1.1),
              1,
              0,
              0,
              0,
              0,
              new Particle.DustOptions(selected.rarity().particleColor(), 1.3f));
        }
      }

      if (sp % 4 == 0) {
        world.spawnParticle(
            Particle.END_ROD, playerLoc.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
      }
    }

    if (sp == 55) {
      fireCallback();
      new BukkitRunnable() {
        @Override
        public void run() {
          stop();
        }
      }.runTaskLater(plugin, 8L);
    }
  }

  /** Spawns the enchant ItemDisplay for slot {@code r} at its cached result position. */
  private void spawnEnchantItem(World world, int r) {
    if (resultPos[r] == null) return;
    ItemStack enchantItem = EnchantAnimationConfig.createEnchantItem(enchantOptions.get(r).name());
    final int cr = r;

    enchantItems[cr] =
        world.spawn(
            resultPos[r].clone(),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(enchantItem);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
              e.setTransformation(itemTransform(0.001f, -0.25f));
            });
    player.showEntity(plugin, enchantItems[cr]);
  }

  /**
   * Spawns rarity-only TextDisplay labels above the three result blocks, and pre-spawns the
   * (initially hidden) hover-description displays above them.
   */
  private void spawnRarityLabels(World world) {
    if (labelsSpawned) return;
    labelsSpawned = true;

    for (int r = 0; r < RESULT_INDICES.length; r++) {
      if (resultPos[r] == null) continue;
      EnchantOption opt = enchantOptions.get(r);
      Location labelLoc = resultPos[r].clone().add(0, 0.58, 0);
      final int cr = r;

      labels[cr] =
          world.spawn(
              labelLoc,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setBackgroundColor(Color.fromARGB(210, 10, 10, 20));
                e.text(
                    Component.text(FontUtils.toSmallCaps(opt.rarity().displayName()))
                        .color(opt.rarity().textColor())
                        .decorate(TextDecoration.BOLD));
                e.setTransformation(
                    new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.52f, 0.52f, 0.52f),
                        new AxisAngle4f(0, 0, 1, 0)));
              });
      player.showEntity(plugin, labels[cr]);

      String desc = EnchantAnimationConfig.getEnchantDescription(opt.name());
      Location descLoc = resultPos[r].clone().add(0, 1.15, 0);
      Component descText =
          Component.text(desc)
              .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
              .decoration(TextDecoration.ITALIC, false)
              .decoration(TextDecoration.BOLD, false)
              .append(Component.newline())
              .append(
                  Component.text("\u25BC")
                      .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                      .decorate(TextDecoration.BOLD)
                      .decoration(TextDecoration.ITALIC, false));

      descDisplays[cr] =
          world.spawn(
              descLoc,
              TextDisplay.class,
              e -> {
                e.setVisibleByDefault(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                e.setBackgroundColor(Color.fromARGB(180, 10, 10, 20));
                e.text(descText);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setTransformation(
                    new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.001f, 0.001f, 0.001f),
                        new AxisAngle4f(0, 0, 1, 0)));
              });
      player.showEntity(plugin, descDisplays[cr]);
    }
  }

  /** Smoothly scales each description display in/out based on hover state. */
  private void tickDescriptions() {
    if (!labelsSpawned || selectionMade) return;

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

  private Material materialForSlot(int index) {
    for (int r = 0; r < RESULT_INDICES.length; r++) {
      if (index == RESULT_INDICES[r]) {
        return enchantOptions.get(r).rarity().concreteMaterial();
      }
    }
    return fillerMaterials[index] != null ? fillerMaterials[index] : Material.WHITE_CONCRETE;
  }

  /**
   * Centers a BlockDisplay cube of {@code scale} at the entity's world position. BlockDisplays
   * render with the block corner at the entity origin by default; translating by {@code -scale/2}
   * on all axes shifts the visual centre to the entity position, which aligns with the arrow
   * indicators at the same coordinates.
   */
  private static Transformation centeredTransform(float scale) {
    float half = scale / 2f;
    return new Transformation(
        new Vector3f(-half, -half, -half),
        new AxisAngle4f(0, 0, 1, 0),
        new Vector3f(scale, scale, scale),
        new AxisAngle4f(0, 0, 1, 0));
  }

  /**
   * ItemDisplay Transformation with a vertical offset and uniform scale. Items are natively centred
   * at the entity origin — no half-offset needed.
   */
  private static Transformation itemTransform(float scale, float yOffset) {
    return new Transformation(
        new Vector3f(0, yOffset, 0),
        new AxisAngle4f(0, 0, 1, 0),
        new Vector3f(scale, scale, scale),
        new AxisAngle4f(0, 0, 1, 0));
  }

  private static boolean isResultIndex(int i) {
    for (int r : RESULT_INDICES) if (r == i) return true;
    return false;
  }

  private int[] getNonSelectedIndices() {
    int[] result = new int[2];
    int idx = 0;
    for (int r = 0; r < 3 && idx < 2; r++) {
      if (r != selectedIndex) result[idx++] = r;
    }
    return result;
  }

  /** Fast launch, hard brake — exactly like a mechanical reel. */
  private static double easeInOutQuart(double x) {
    return x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
  }
}
