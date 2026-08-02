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
 * Evolution animation variant — "Runic Sigil".
 *
 * <p>After the shared atom cast, three concentric rune rings etch themselves into the ground around
 * the pickaxe and begin spinning, each faster and at a different tilt than the last. As they lock
 * into alignment they lift off the ground and stack upward into a rotating tower of arcane light —
 * a rune-forge sealing the pickaxe inside. The tower collapses inward in a flash of violet light,
 * and the evolved pickaxe and its reward stone are left standing where it stood.
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Cast ( 0– 16) Shared gold atom burst.
 *   <li>Circle Etch ( 16– 50) Three rune rings draw themselves on the ground and begin spinning.
 *   <li>Tower Rise ( 50– 86) The rings lift and stack into a rotating column of light.
 *   <li>Ignition ( 86) The tower locks and ignites in a burst of violet light.
 *   <li>Emerge ( 86–116) The evolved pickaxe and stone rise from the ignition point.
 *   <li>Float (116–150) Items hover, orbited by slow rune fragments.
 *   <li>Return (150–172) Items sweep back to the player.
 * </ol>
 */
public class EvolutionRunicSigilAnimation implements EvolutionAnimation {
  private static final int CAST_END = 16;
  private static final int ETCH_END = 50;
  private static final int TOWER_END = 86;
  private static final int IGNITE_TICK = 86;
  private static final int CALLBACK_TICK = 86;
  private static final int EMERGE_END = 116;
  private static final int FLOAT_END = 150;
  private static final int RETURN_END = 172;
  private static final int TOTAL = 175;

  private static final double SIGIL_RADIUS = 1.6;
  private static final double TOWER_HEIGHT = 3.6;
  private static final Color ARCANE_VIOLET = Color.fromRGB(150, 60, 230);
  private static final Color ARCANE_TEAL = Color.fromRGB(40, 220, 210);

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
  private Location towerTop;
  private Location exitPickaxeFinalPos;
  private Location exitStoneFinalPos;
  private Location titlePos;

  private ItemDisplay sealedPickaxe;
  private ItemDisplay exitPickaxeDisplay;
  private ItemDisplay exitStoneDisplay;
  private TextDisplay evolutionTitle;

  /**
   * Creates the EvolutionRunicSigilAnimation for the given player.
   *
   * @param plugin the plugin instance
   * @param player the player evolving
   * @param currentTier the tier before evolution
   * @param nextTier the tier after evolution
   * @param winningRarity the rarity of the reward stone that was rolled
   * @param callback invoked once the evolution outcome should be applied
   */
  public EvolutionRunicSigilAnimation(
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
    groundPoint = player.getLocation().clone().add(forward.clone().multiply(3.2));
    towerTop = groundPoint.clone().add(0, TOWER_HEIGHT, 0);
    exitPickaxeFinalPos = towerTop.clone().add(-0.5, 0, 0);
    exitStoneFinalPos = towerTop.clone().add(0.6, 0, 0);
    titlePos = towerTop.clone().add(0, 1.3, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    EvolutionCastEffects.playCastBurst(player, world);

    sealedPickaxe =
        world.spawn(
            groundPoint.clone().add(0, 0.8, 0),
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(heldPickaxe);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.85f));
            });
    player.showEntity(plugin, sealedPickaxe);

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

    if (currentTick <= ETCH_END) {
      tickEtch(world);
    } else if (currentTick < TOWER_END) {
      tickTowerRise(world);
    }

    if (currentTick == IGNITE_TICK) {
      triggerIgnite(world);
    }
    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick > IGNITE_TICK && currentTick <= EMERGE_END) {
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

  /** Three rune rings draw themselves on the ground and begin spinning at different rates. */
  private void tickEtch(World world) {
    double t = (double) currentTick / ETCH_END;
    int pointsDrawn = (int) (t * 28) + 4;
    for (int ring = 0; ring < 3; ring++) {
      double radius = SIGIL_RADIUS * (0.5 + ring * 0.25);
      double spin = currentTick * (0.06 + ring * 0.04) * (ring % 2 == 0 ? 1 : -1);
      Color c = ring == 1 ? ARCANE_TEAL : ARCANE_VIOLET;
      int drawn = Math.min(pointsDrawn, 24);
      for (int i = 0; i < drawn; i++) {
        double a = spin + (i / 24.0) * 2 * Math.PI;
        Location p = groundPoint.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.2f));
      }
    }
    if (currentTick % 8 == 0) {
      world.playSound(groundPoint, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.0f + (float) t * 0.5f);
    }
    if (currentTick % 5 == 0) {
      world.spawnParticle(
          Particle.END_ROD, groundPoint.clone().add(0, 0.9, 0), 1, 0.1, 0.1, 0.1, 0.01);
    }
  }

  /** The rings lift off the ground and stack upward into a rotating column of light. */
  private void tickTowerRise(World world) {
    double t = (double) (currentTick - ETCH_END) / (TOWER_END - ETCH_END);
    for (int ring = 0; ring < 3; ring++) {
      double radius = SIGIL_RADIUS * (0.5 + ring * 0.25) * (1 - t * 0.5);
      double height = TOWER_HEIGHT * t * (0.4 + ring * 0.3);
      double spin = currentTick * (0.15 + ring * 0.08) * (ring % 2 == 0 ? 1 : -1);
      Color c = ring == 1 ? ARCANE_TEAL : ARCANE_VIOLET;
      for (int i = 0; i < 20; i++) {
        double a = spin + (i / 20.0) * 2 * Math.PI;
        Location p = groundPoint.clone().add(Math.cos(a) * radius, height, Math.sin(a) * radius);
        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.3f));
      }
    }
    if (sealedPickaxe != null && !sealedPickaxe.isDead()) {
      sealedPickaxe.teleport(groundPoint.clone().add(0, 0.8 + TOWER_HEIGHT * t * 0.4, 0));
    }
    if (currentTick % 6 == 0) {
      world.playSound(groundPoint, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.1f + (float) t * 0.5f);
    }
  }

  /** The tower locks into alignment and ignites in a burst of violet light. */
  private void triggerIgnite(World world) {
    if (sealedPickaxe != null && !sealedPickaxe.isDead()) sealedPickaxe.remove();

    world.playSound(towerTop, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.3f);
    world.playSound(towerTop, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.2f);
    world.playSound(towerTop, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.1f);

    player.spawnParticle(Particle.END_ROD, towerTop, 60, 0.35, 0.5, 0.35, 0.2);
    player.spawnParticle(
        Particle.DUST,
        towerTop,
        80,
        0.4,
        0.6,
        0.4,
        0,
        new Particle.DustOptions(ARCANE_VIOLET, 2.2f));
    player.spawnParticle(
        Particle.DUST, towerTop, 50, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(ARCANE_TEAL, 1.8f));
    player.spawnParticle(
        Particle.DUST,
        towerTop,
        50,
        0.4,
        0.6,
        0.4,
        0,
        new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    player.spawnParticle(Particle.FLASH, towerTop, 1, 0, 0, 0, 0);

    spawnItems(world);
  }

  /** Spawns the evolved pickaxe and reward stone, scaled to nothing, at the tower's peak. */
  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            towerTop.clone(),
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
            towerTop.clone(),
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

  /** Emerge phase — items rise out of the tower's peak and settle. */
  private void tickEmerge(World world) {
    double t = (double) (currentTick - IGNITE_TICK) / (EMERGE_END - IGNITE_TICK);
    double eased = 1 - Math.pow(1 - t, 2);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(lerp(towerTop, exitPickaxeFinalPos, eased));
      scaleItem(exitPickaxeDisplay, (float) eased * 0.9f);
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(lerp(towerTop, exitStoneFinalPos, eased));
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
                Component.text("✦  EVOLUTION " + nextTier.level)
                    .color(TextColor.color(0x9640E6))
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
    world.playSound(towerTop, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
  }

  /** Float phase — items hover, orbited by slow drifting rune fragments. */
  private void tickFloat(World world) {
    float dy = (float) (Math.sin(currentTick * 0.15) * 0.07);
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) {
      exitPickaxeDisplay.teleport(exitPickaxeFinalPos.clone().add(0, dy, 0));
    }
    if (exitStoneDisplay != null && !exitStoneDisplay.isDead()) {
      exitStoneDisplay.teleport(exitStoneFinalPos.clone().add(0, -dy, 0));
    }
    if (currentTick % 4 == 0) {
      double a = currentTick * 0.1;
      double rx = Math.cos(a) * 1.2;
      double rz = Math.sin(a) * 1.2;
      world.spawnParticle(
          Particle.DUST,
          towerTop.clone().add(rx, 0, rz),
          1,
          0,
          0,
          0,
          0,
          new Particle.DustOptions(ARCANE_VIOLET, 1.1f));
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
    if (sealedPickaxe != null && !sealedPickaxe.isDead()) sealedPickaxe.remove();
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
