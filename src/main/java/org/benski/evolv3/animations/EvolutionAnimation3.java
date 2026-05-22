package org.benski.evolv3.animations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Evolution animation variant 3 — "Crystal Ascension".
 *
 * <p>Phase overview:
 *
 * <ol>
 *   <li>Grow ( 0– 40) 8 amethyst shards erupt from the ground and rotate upward as the pickaxe
 *       rises through the centre.
 *   <li>Converge (40– 70) Shards arc inward and stack together above the pickaxe.
 *   <li>Shatter (70– 82) Crystal formation explodes in a purple nova.
 *   <li>Emerge (82–102) Stone, paper and pickaxe scale up inside the nova cloud.
 *   <li>Float (102–140) Items oscillate gently with rarity particle trail.
 *   <li>Return (140–158) Items sweep back to the player.
 * </ol>
 */
public class EvolutionAnimation3 implements EvolutionAnimation {
  private static final int GROW_END = 40;
  private static final int CONVERGE_END = 70;
  private static final int SHATTER_END = 82;
  private static final int EMERGE_END = 102;
  private static final int FLOAT_END = 140;
  private static final int RETURN_END = 158;
  private static final int CALLBACK_TICK = 73;
  private static final int TOTAL = 161;

  private static final int NUM_SHARDS = 8;
  private static final double SHARD_RING = 2.1;

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

  private Location crystalCenter;

  private final List<BlockDisplay> shards = new ArrayList<>();
  private double shardRotation = 0;

  private ItemDisplay pickaxeDisplay;
  private Location pickaxeStartLoc;
  private Location pickaxeHoverPos;

  private ItemDisplay exitPickaxeDisplay;
  private TextDisplay evolutionTitle;

  private Location exitPickaxeFinalPos;
  private Location titlePos;

  public EvolutionAnimation3(
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
    shards.forEach(
        b -> {
          if (b != null && !b.isDead()) b.remove();
        });
    shards.clear();
    if (pickaxeDisplay != null && !pickaxeDisplay.isDead()) pickaxeDisplay.remove();
    removeItemDisplays();
  }

  @Override
  public void start() {
    Location eye = player.getEyeLocation();
    World world = eye.getWorld();
    if (world == null) return;

    player.spawnParticle(Particle.ELDER_GUARDIAN, player.getLocation(), 1);

    Vector forward = eye.getDirection().setY(0).normalize();
    crystalCenter = eye.clone().add(forward.multiply(3.5)).subtract(0, 0.5, 0);

    pickaxeHoverPos = crystalCenter.clone().add(0, 2.8, 0);
    exitPickaxeFinalPos = crystalCenter.clone().add(0, 3.8, 0);
    titlePos = crystalCenter.clone().add(0, 2.1, 0);

    heldSlot = player.getInventory().getHeldItemSlot();
    heldPickaxe = player.getInventory().getItemInMainHand().clone();
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

    pickaxeStartLoc =
        player.getLocation().clone().add(0, 1.3, 0).add(forward.clone().normalize().multiply(1.0));

    pickaxeDisplay =
        world.spawn(
            pickaxeStartLoc,
            ItemDisplay.class,
            e -> {
              e.setVisibleByDefault(false);
              e.setItemStack(heldPickaxe);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setPersistent(false);
              e.setBillboard(Display.Billboard.CENTER);
              e.setTransformation(itemTransform(0.85f));
            });
    player.showEntity(plugin, pickaxeDisplay);

    Material shardMatA = shardMaterial(nextTier, 0);
    Material shardMatB = shardMaterial(nextTier, 1);
    for (int i = 0; i < NUM_SHARDS; i++) {
      double angle = (2 * Math.PI / NUM_SHARDS) * i;
      Location base =
          crystalCenter
              .clone()
              .add(Math.cos(angle) * SHARD_RING, -2.0, Math.sin(angle) * SHARD_RING);
      int fi = i;
      BlockDisplay bd =
          world.spawn(
              base,
              BlockDisplay.class,
              e -> {
                Material mat = (fi % 2 == 0) ? shardMatA : shardMatB;
                e.setVisibleByDefault(false);
                e.setBlock(mat.createBlockData());
                e.setBrightness(new Display.Brightness(15, 15));
                e.setPersistent(false);
                float h = 0.25f + (fi % 3) * 0.08f;
                float w = 0.20f;
                e.setTransformation(
                    new Transformation(
                        new Vector3f(-w / 2f, 0f, -w / 2f),
                        new AxisAngle4f(),
                        new Vector3f(w, h, w),
                        new AxisAngle4f()));
              });
      player.showEntity(plugin, bd);
      shards.add(bd);
    }

    world.playSound(crystalCenter, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.7f, 0.5f);
    world.playSound(crystalCenter, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.6f);

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

  private void tick() {
    World world = player.getWorld();
    shardRotation += 0.055;

    if (currentTick <= GROW_END) {
      double t = (double) currentTick / GROW_END;
      double eased = 1 - Math.pow(1 - t, 3);
      for (int i = 0; i < NUM_SHARDS; i++) {
        BlockDisplay bd = shards.get(i);
        if (bd == null || bd.isDead()) continue;
        double baseAngle = (2 * Math.PI / NUM_SHARDS) * i;
        double rot = baseAngle + shardRotation;
        double rise = -2.0 + eased * 4.5;
        Location pos =
            crystalCenter.clone().add(Math.cos(rot) * SHARD_RING, rise, Math.sin(rot) * SHARD_RING);
        bd.teleport(pos);
        float tilt = (float) (eased * 0.3);
        float w = 0.20f, h = 0.25f + (i % 3) * 0.08f;
        bd.setTransformation(
            new Transformation(
                new Vector3f(-w / 2f, 0f, -w / 2f),
                new AxisAngle4f((float) (-tilt * Math.cos(rot)), 0, 1, 0),
                new Vector3f(w, h, w),
                new AxisAngle4f()));
      }
      if (pickaxeDisplay != null && !pickaxeDisplay.isDead()) {
        Location pos = lerp(pickaxeStartLoc, pickaxeHoverPos, eased * 0.8);
        pickaxeDisplay.teleport(pos);
      }
      if (currentTick % 3 == 0) {
        player.spawnParticle(
            Particle.DUST,
            crystalCenter.clone().add(0, 1.5 * eased, 0),
            3,
            0.3,
            0.3,
            0.3,
            0,
            new Particle.DustOptions(Color.fromRGB(180, 100, 255), 1.5f));
      }
      if (currentTick == 20)
        world.playSound(crystalCenter, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.8f, 0.8f);
    }

    if (currentTick > GROW_END && currentTick <= CONVERGE_END) {
      double t = (double) (currentTick - GROW_END) / (CONVERGE_END - GROW_END);
      double eased = Math.pow(t, 2);
      double radius = SHARD_RING * (1 - eased);
      double riseBase = 2.5 + 0.3 * eased;
      for (int i = 0; i < NUM_SHARDS; i++) {
        BlockDisplay bd = shards.get(i);
        if (bd == null || bd.isDead()) continue;
        double baseAngle = (2 * Math.PI / NUM_SHARDS) * i;
        double rot = baseAngle + shardRotation;
        Location pos =
            crystalCenter.clone().add(Math.cos(rot) * radius, riseBase, Math.sin(rot) * radius);
        bd.teleport(pos);
      }
      if (pickaxeDisplay != null && !pickaxeDisplay.isDead()) {
        Location pos = lerp(pickaxeStartLoc, pickaxeHoverPos, 0.8 + t * 0.2);
        pickaxeDisplay.teleport(pos);
      }
      if (currentTick % 2 == 0) {
        player.spawnParticle(
            Particle.DUST,
            crystalCenter.clone().add(0, 2.5 + eased, 0),
            4,
            0.3,
            0.3,
            0.3,
            0,
            new Particle.DustOptions(Color.fromRGB(200, 120, 255), 1.8f));
        player.spawnParticle(
            Particle.END_ROD, crystalCenter.clone().add(0, 2.5, 0), 2, 0.15, 0.15, 0.15, 0.03);
      }
      if (currentTick == GROW_END + 10)
        world.playSound(crystalCenter, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.1f);
      if (currentTick == CONVERGE_END - 3)
        world.playSound(crystalCenter, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.6f);
    }

    if (currentTick == CALLBACK_TICK && !callbackFired) {
      callbackFired = true;
      callback.run();
    }

    if (currentTick == CONVERGE_END) {
      shards.forEach(
          b -> {
            if (b != null && !b.isDead()) b.remove();
          });
      shards.clear();
      if (pickaxeDisplay != null && !pickaxeDisplay.isDead()) pickaxeDisplay.remove();
      Location burst = crystalCenter.clone().add(0, 3.2, 0);
      world.playSound(burst, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
      world.playSound(burst, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
      world.playSound(burst, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.8f);
      player.spawnParticle(Particle.END_ROD, burst, 80, 0.4, 0.4, 0.4, 0.22);
      player.spawnParticle(
          Particle.DUST,
          burst,
          100,
          0.5,
          0.5,
          0.5,
          0,
          new Particle.DustOptions(Color.fromRGB(200, 100, 255), 2.5f));
      player.spawnParticle(
          Particle.DUST,
          burst,
          60,
          0.5,
          0.5,
          0.5,
          0,
          new Particle.DustOptions(winningRarity.particleColor(), 2.0f));
    }
    if (currentTick > CONVERGE_END && currentTick <= SHATTER_END) {
      player.spawnParticle(
          Particle.DUST,
          crystalCenter.clone().add(0, 3.0, 0),
          5,
          0.6,
          0.6,
          0.6,
          0,
          new Particle.DustOptions(Color.fromRGB(180, 80, 255), 1.5f));
    }

    if (currentTick == SHATTER_END) {
      spawnItems(world);
      world.playSound(crystalCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
      player.playSound(
          net.kyori.adventure.sound.Sound.sound(
              Key.key("minecraft", "ui.toast.challenge_complete"), Source.MASTER, 0.7f, 1.1f));
    }
    if (currentTick > SHATTER_END && currentTick <= EMERGE_END) {
      double t = (double) (currentTick - SHATTER_END) / (EMERGE_END - SHATTER_END);
      double eased = 1 - Math.pow(1 - t, 2);
      scaleItem(exitPickaxeDisplay, (float) eased);
    }
    if (currentTick == EMERGE_END) spawnEvolutionTitle(world);

    if (currentTick > EMERGE_END && currentTick <= FLOAT_END) {
      float dy = (float) (Math.sin(currentTick * 0.17) * 0.065);
      driftItem(exitPickaxeDisplay, exitPickaxeFinalPos, dy);
      if (currentTick % 3 == 0) {
        double rx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
        double ry = ThreadLocalRandom.current().nextDouble() * 2.5;
        double rz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
        player.spawnParticle(Particle.GLOW, crystalCenter.clone().add(rx, ry, rz), 1, 0, 0, 0, 0);
      }
      if (currentTick % 6 == 0) {
        player.spawnParticle(
            Particle.SPORE_BLOSSOM_AIR, crystalCenter.clone().add(0, 1, 0), 5, 1.5, 1.0, 1.5, 0);
      }
      if (currentTick % 5 == 0) {
        player.spawnParticle(
            Particle.DUST,
            exitPickaxeFinalPos.clone().add(0, dy, 0),
            2,
            0.1,
            0.1,
            0.1,
            0,
            new Particle.DustOptions(winningRarity.particleColor(), 1.2f));
      }
    }

    if (currentTick > FLOAT_END && currentTick <= RETURN_END) {
      double t = (double) (currentTick - FLOAT_END) / (RETURN_END - FLOAT_END);
      double eased = Math.pow(t, 2);
      Location chest = player.getLocation().add(0, 1.3, 0);
      moveItem(exitPickaxeDisplay, exitPickaxeFinalPos, chest, eased);
      if (evolutionTitle != null && !evolutionTitle.isDead())
        evolutionTitle.teleport(lerp(titlePos, chest, eased));
    }
    if (currentTick == RETURN_END) deliverItems(world);
  }

  @SuppressWarnings("deprecation")
  private void spawnItems(World world) {
    ItemStack pick = heldPickaxe.clone();
    pick.setType(nextTier.material);
    exitPickaxeDisplay =
        world.spawn(
            exitPickaxeFinalPos,
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
  }

  private void spawnEvolutionTitle(World world) {
    String raw = nextTier.displayName.trim();
    String tierName = (raw.startsWith("&#") && raw.length() >= 8) ? raw.substring(8).trim() : raw;
    TextColor tc = tierTextColor(nextTier);
    Component comp =
        Component.text()
            .append(
                Component.text("⛏  EVOLUTION " + nextTier.level)
                    .color(TextColor.color(0xCC88FF))
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
              e.setTransformation(textTransform(0.75f));
            });
    player.showEntity(plugin, evolutionTitle);
  }

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
      removeItemDisplays();
      return;
    }
    ItemStack stone = EvolutionStone.create(winningRarity, stoneEnchantName);
    Map<Integer, ItemStack> overflow = player.getInventory().addItem(stone);
    overflow.values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
    world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
    world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
    removeItemDisplays();
    if (onComplete != null) onComplete.run();
  }

  private void cleanup() {
    if (finished) return;
    finished = true;
    shards.forEach(
        b -> {
          if (b != null && !b.isDead()) b.remove();
        });
    shards.clear();
    if (pickaxeDisplay != null && !pickaxeDisplay.isDead()) pickaxeDisplay.remove();
    removeItemDisplays();
    if (!callbackFired) {
      callbackFired = true;
      callback.run();
    }
    if (!itemsReturned) deliverItems(player.getWorld());
  }

  private void removeItemDisplays() {
    if (exitPickaxeDisplay != null && !exitPickaxeDisplay.isDead()) exitPickaxeDisplay.remove();
    if (evolutionTitle != null && !evolutionTitle.isDead()) evolutionTitle.remove();
  }

  private void scaleItem(ItemDisplay d, float scale) {
    if (d == null || d.isDead()) return;
    d.setTransformation(itemTransform(scale));
  }

  private static void driftItem(ItemDisplay d, Location base, float dy) {
    if (d == null || d.isDead()) return;
    d.teleport(base.clone().add(0, dy, 0));
  }

  private static void moveItem(ItemDisplay d, Location from, Location to, double t) {
    if (d == null || d.isDead()) return;
    d.teleport(lerp(from, to, t));
  }

  private static Location lerp(Location a, Location b, double t) {
    return a.toVector().multiply(1 - t).add(b.toVector().multiply(t)).toLocation(a.getWorld());
  }

  private static Transformation itemTransform(float scale) {
    return new Transformation(
        new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
  }

  private static Transformation textTransform(float scale) {
    return new Transformation(
        new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
  }

  private static TextColor tierTextColor(EvolutionTier tier) {
    String d = tier.displayName.trim();
    if (d.startsWith("&#") && d.length() >= 8) {
      TextColor tc = TextColor.fromHexString("#" + d.substring(2, 8));
      if (tc != null) return tc;
    }
    return TextColor.color(0xCCCCCC);
  }

  /**
   * Returns the shard block material for the given target tier. index 0 = primary, index 1 =
   * secondary (for alternating even/odd shards).
   */
  private static Material shardMaterial(EvolutionTier tier, int index) {
    return switch (tier) {
      case STONE -> index == 0 ? Material.STONE : Material.COBBLESTONE;
      case COPPER -> index == 0 ? Material.COPPER_BLOCK : Material.EXPOSED_COPPER;
      case IRON -> index == 0 ? Material.IRON_BLOCK : Material.RAW_IRON_BLOCK;
      case DIAMOND -> index == 0 ? Material.DIAMOND_BLOCK : Material.DIAMOND_BLOCK;
      case NETHERITE -> index == 0 ? Material.NETHERITE_BLOCK : Material.ANCIENT_DEBRIS;
      default -> index == 0 ? Material.AMETHYST_BLOCK : Material.BUDDING_AMETHYST;
    };
  }

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
    return all.get(ThreadLocalRandom.current().nextInt(all.size()));
  }
}
