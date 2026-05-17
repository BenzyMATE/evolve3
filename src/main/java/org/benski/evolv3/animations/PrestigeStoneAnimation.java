package org.benski.evolv3.animations;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.PrestigeManager;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.items.EvolutionStone;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * A 27-slot inventory animation for the prestige stone selection.
 *
 * <h3>Timeline</h3>
 *
 * <ol>
 *   <li><b>Slot machine</b> (ticks 0–52): the three stone slots cycle through random rarity
 *       colours, slowing down in the final 4 ticks before each landing.
 *   <li><b>Land</b> (ticks 53 / 60 / 67): each stone slot locks in sequentially. The eight
 *       surrounding slots burst with the stone's rarity colour for 5 ticks.
 *   <li><b>Done</b> (tick 82): callback fires; player can now click.
 * </ol>
 */
public final class PrestigeStoneAnimation {
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  /** All 8 surrounding slots for each stone slot — the full 3×3 square minus the stone itself. */
  private static final int[][] RIPPLE_NEIGHBOURS = {
    {0, 1, 2, 9, 11, 18, 19, 20},
    {3, 4, 5, 12, 14, 21, 22, 23},
    {6, 7, 8, 15, 17, 24, 25, 26},
  };

  private static final int LAND_0 = 53;
  private static final int LAND_1 = 60;
  private static final int LAND_2 = 67;
  private static final int RIPPLE_DURATION = 5;
  private static final int TOTAL = 82;

  private PrestigeStoneAnimation() {}

  public static void play(
      Player player,
      Inventory inv,
      List<PrestigeManager.StoneRoll> rolls,
      int[] stoneSlots,
      Runnable onDone) {
    new AnimRunnable(player, inv, rolls, stoneSlots, onDone)
        .runTaskTimer(Main.getInstance(), 0L, 1L);
  }

  private static final class AnimRunnable extends BukkitRunnable {
    private final Player player;
    private final Inventory inv;
    private final List<PrestigeManager.StoneRoll> rolls;
    private final int[] stoneSlots;
    private final Runnable onDone;
    private final List<RarityConfig> allRarities;

    private int tick = 0;

    /** End tick for each slot's ripple burst; 0 = not rippling. */
    private final int[] rippleEnd = new int[27];

    /** Whether each stone slot has landed. */
    private final boolean[] landed = new boolean[3];

    AnimRunnable(
        Player player,
        Inventory inv,
        List<PrestigeManager.StoneRoll> rolls,
        int[] stoneSlots,
        Runnable onDone) {
      this.player = player;
      this.inv = inv;
      this.rolls = rolls;
      this.stoneSlots = stoneSlots;
      this.onDone = onDone;
      this.allRarities = EnchantAnimationConfig.getRarities();
    }

    @Override
    public void run() {
      if (!player.isOnline()) {
        cancel();
        return;
      }

      int interval = cycleInterval();
      if (tick % interval == 0) {
        for (int i = 0; i < stoneSlots.length; i++) {
          if (!landed[i]) inv.setItem(stoneSlots[i], randomCycleItem());
        }
      }

      checkLand(0, LAND_0);
      checkLand(1, LAND_1);
      checkLand(2, LAND_2);

      for (int s = 0; s < 27; s++) {
        if (rippleEnd[s] > 0 && tick >= rippleEnd[s]) {
          rippleEnd[s] = 0;
          if (!isStoneSlot(s)) inv.setItem(s, darkPane());
        }
      }

      if (tick == 0) {
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.3f);
      }

      if (tick >= TOTAL) {
        cancel();
        if (onDone != null) onDone.run();
        return;
      }
      tick++;
    }

    private void checkLand(int stoneIndex, int landTick) {
      if (landed[stoneIndex] || tick < landTick) return;
      landed[stoneIndex] = true;

      inv.setItem(
          stoneSlots[stoneIndex],
          EvolutionStone.create(
              rolls.get(stoneIndex).rarity(), rolls.get(stoneIndex).enchantName()));

      RarityConfig rarity = rolls.get(stoneIndex).rarity();
      for (int neighbour : RIPPLE_NEIGHBOURS[stoneIndex]) {
        rippleEnd[neighbour] = tick + RIPPLE_DURATION;
        inv.setItem(neighbour, rarityPane(rarity));
      }

      player.playSound(
          player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.9f, 1.2f + stoneIndex * 0.2f);
      player.playSound(
          player.getLocation(),
          Sound.BLOCK_AMETHYST_CLUSTER_PLACE,
          0.6f,
          1.4f + stoneIndex * 0.15f);
      if (stoneIndex == 2) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
      }
    }

    private int cycleInterval() {
      if (tick < LAND_0 - 4) return 2;
      return 4;
    }

    private boolean isStoneSlot(int s) {
      for (int ss : stoneSlots) if (s == ss) return true;
      return false;
    }

    private ItemStack randomCycleItem() {
      if (allRarities.isEmpty()) return new ItemStack(Material.NETHER_STAR);
      RarityConfig r = allRarities.get(ThreadLocalRandom.current().nextInt(allRarities.size()));
      String hex = String.format("%06X", r.textColor().value());
      ItemStack item = new ItemStack(Material.NETHER_STAR);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(lc("&#" + hex + "&l" + r.displayName()));
        meta.lore(List.of());
        item.setItemMeta(meta);
      }
      return item;
    }

    private static ItemStack darkPane() {
      ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
      }
      return item;
    }

    /**
     * Returns the stained glass pane whose colour is the closest Minecraft match to the rarity
     * colour.
     */
    private static ItemStack rarityPane(RarityConfig rarity) {
      int argb = rarity.textColor().value();
      int r = (argb >> 16) & 0xFF;
      int g = (argb >> 8) & 0xFF;
      int b = argb & 0xFF;
      ItemStack item = new ItemStack(closestGlassPane(r, g, b));
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.displayName(Component.empty());
        meta.lore(List.of());
        item.setItemMeta(meta);
      }
      return item;
    }

    /**
     * Picks the stained glass pane material whose RGB is nearest to the given colour in Euclidean
     * space.
     */
    private static Material closestGlassPane(int r, int g, int b) {
      record C(int r, int g, int b, Material mat) {}
      C[] palette = {
        new C(209, 209, 209, Material.WHITE_STAINED_GLASS_PANE),
        new C(224, 97, 27, Material.ORANGE_STAINED_GLASS_PANE),
        new C(187, 49, 159, Material.MAGENTA_STAINED_GLASS_PANE),
        new C(103, 138, 197, Material.LIGHT_BLUE_STAINED_GLASS_PANE),
        new C(177, 166, 39, Material.YELLOW_STAINED_GLASS_PANE),
        new C(65, 174, 56, Material.LIME_STAINED_GLASS_PANE),
        new C(235, 101, 137, Material.PINK_STAINED_GLASS_PANE),
        new C(54, 55, 58, Material.GRAY_STAINED_GLASS_PANE),
        new C(124, 124, 115, Material.LIGHT_GRAY_STAINED_GLASS_PANE),
        new C(21, 137, 145, Material.CYAN_STAINED_GLASS_PANE),
        new C(100, 32, 156, Material.PURPLE_STAINED_GLASS_PANE),
        new C(45, 47, 143, Material.BLUE_STAINED_GLASS_PANE),
        new C(96, 60, 31, Material.BROWN_STAINED_GLASS_PANE),
        new C(73, 91, 36, Material.GREEN_STAINED_GLASS_PANE),
        new C(142, 34, 34, Material.RED_STAINED_GLASS_PANE),
        new C(7, 8, 15, Material.BLACK_STAINED_GLASS_PANE),
      };
      Material best = Material.WHITE_STAINED_GLASS_PANE;
      long minDist = Long.MAX_VALUE;
      for (C c : palette) {
        long d =
            (long) (r - c.r) * (r - c.r)
                + (long) (g - c.g) * (g - c.g)
                + (long) (b - c.b) * (b - c.b);
        if (d < minDist) {
          minDist = d;
          best = c.mat;
        }
      }
      return best;
    }

    private static Component lc(String text) {
      return LEGACY
          .deserialize(MatrixColorAPI.process(text))
          .decoration(TextDecoration.ITALIC, false);
    }
  }
}
