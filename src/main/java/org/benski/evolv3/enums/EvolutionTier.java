package org.benski.evolv3.enums;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the six evolution tiers a pickaxe can reach.
 *
 * <p>Each tier maps to a Minecraft pickaxe material and a colour-formatted display name. The
 * progression is linear: Wood → Stone → Copper → Iron → Diamond → Netherite.
 *
 * <p>The {@code level} field matches the {@code evolution} integer stored on {@link
 * org.benski.evolv3.models.pickaxe.Pickaxe}, so tier lookup is always a simple {@link
 * #fromLevel(int)} call.
 */
public enum EvolutionTier {
  WOOD(1, Material.WOODEN_PICKAXE, "&#888888Wood"),
  STONE(2, Material.STONE_PICKAXE, "&#AAAAAAStone"),
  COPPER(3, Material.COPPER_PICKAXE, "&#B87333Copper"),
  IRON(4, Material.IRON_PICKAXE, "&#CCCCCCIron"),
  DIAMOND(5, Material.DIAMOND_PICKAXE, "&#00FFFFDiamond"),
  NETHERITE(6, Material.NETHERITE_PICKAXE, "&#9B59B6Netherite");

  /** The numeric level stored on {@code PickaxeProfile.evolution}. */
  public final int level;

  /** The Minecraft item material for this tier. */
  public final Material material;

  /** Colour-formatted display name (e.g. {@code "&#00FFFF Diamond"}). */
  public final String displayName;

  EvolutionTier(int level, Material material, String displayName) {
    this.level = level;
    this.material = material;
    this.displayName = displayName;
  }

  /** Returns true when this is the maximum tier (Netherite). */
  public boolean isMax() {
    return this == NETHERITE;
  }

  /** Returns the next tier in progression, or {@code null} if already at max. */
  @Nullable
  public EvolutionTier next() {
    return isMax() ? null : fromLevel(this.level + 1);
  }

  /**
   * Looks up the tier by its numeric level. Returns {@link #WOOD} as a safe fallback if the level
   * is out of range.
   *
   * @param level the evolution level (1–6)
   * @return the matching tier, never null
   */
  @NotNull
  public static EvolutionTier fromLevel(int level) {
    for (EvolutionTier tier : values()) {
      if (tier.level == level) return tier;
    }
    return WOOD;
  }

  /**
   * Looks up the tier by its pickaxe material name.
   *
   * @param material the material to look up
   * @return the matching tier, or {@link #WOOD} if not found
   */
  @NotNull
  public static EvolutionTier fromMaterial(@NotNull Material material) {
    for (EvolutionTier tier : values()) {
      if (tier.material == material) return tier;
    }
    return WOOD;
  }
}
