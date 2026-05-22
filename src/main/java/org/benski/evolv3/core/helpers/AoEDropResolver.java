package org.benski.evolv3.core.helpers;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.managers.FrostbiteBlockManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.enchants.AlchemyEnchant;
import org.benski.evolv3.enchants.AlchemyStoneHandler;
import org.benski.evolv3.enchants.ExtractionEnchant;
import org.benski.evolv3.enchants.FrostbiteEnchant;
import org.benski.evolv3.enchants.FurnaceEnchant;
import org.benski.evolv3.enchants.FurnaceStoneHandler;
import org.benski.evolv3.enchants.MerchantEnchant;
import org.benski.evolv3.enchants.RadioactiveEnchant;
import org.benski.evolv3.enchants.ScavengerEnchant;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Resolves the final drop material for a block broken by an AoE enchant.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>Frostbite frozen blocks: restore original material from metadata and clean up.
 *   <li>Use {@link Block#getDrops(ItemStack)} for the correct vanilla drop (e.g. stone →
 *       cobblestone, iron ore → raw iron).
 *   <li>Apply Alchemy upgrade chance if the player has that enchant.
 *   <li>Apply Furnace smelt chance after alchemy (so alchemy + furnace can chain).
 * </ol>
 */
public final class AoEDropResolver {
  private AoEDropResolver() {}

  /**
   * Materials that AoE enchants must never break, shared by all AoE enchant classes. Includes
   * structural and command blocks that would cause server damage if removed.
   */
  public static final Set<Material> PROTECTED_MATERIALS =
      Set.of(
          Material.BEDROCK,
          Material.BARRIER,
          Material.COMMAND_BLOCK,
          Material.CHAIN_COMMAND_BLOCK,
          Material.REPEATING_COMMAND_BLOCK,
          Material.STRUCTURE_BLOCK,
          Material.STRUCTURE_VOID,
          Material.JIGSAW,
          Material.END_PORTAL_FRAME,
          Material.END_PORTAL,
          Material.NETHER_PORTAL,
          Material.SPAWNER);

  /**
   * Returns {@code true} if the block can be broken by an AoE enchant.
   *
   * <p>Rejects: air, fluids, protected materials, negative-hardness blocks (e.g. bedrock variants),
   * and stale Frostbite-frozen blocks whose original material can no longer be recovered (metadata
   * does not survive server restarts).
   *
   * @param block the block to test (must still be intact at call time)
   * @return true if the block is a valid AoE break target
   */
  public static boolean isBreakable(Block block) {
    Material m = block.getType();
    if (m.isAir() || m == Material.WATER || m == Material.LAVA) return false;
    if (m == Material.PACKED_ICE && !block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY))
      return false;
    if (PROTECTED_MATERIALS.contains(m)) return false;
    if (m.getHardness() < 0) return false;
    return true;
  }

  /**
   * Resolves the final material to add to the backpack for a block broken by an AoE enchant.
   *
   * @param block the block about to be set to AIR (must still be intact at call time)
   * @param tool the player's held tool (used for correct drop computation)
   * @param plugin the plugin instance (needed to remove Frostbite metadata)
   * @param pickaxeProfile the player's pickaxe profile (for Alchemy/Furnace enchant checks)
   * @return the final {@link Material} — never AIR for a breakable, non-frostbite-stale block
   */
  public static Material resolve(
      Block block, ItemStack tool, JavaPlugin plugin, Pickaxe pickaxeProfile) {
    Material mat = resolveBaseDrop(block, tool, plugin);
    mat = applyAlchemy(mat, pickaxeProfile);
    mat = applyFurnace(mat, pickaxeProfile);
    return mat;
  }

  /**
   * Rolls per-block side-effect enchants (Scavenger, Merchant, Extraction, Radioactive) for a block
   * broken by an AoE enchant. When accumulation is active for this player, Extraction and
   * Radioactive are collected silently; otherwise individual holograms are shown per-block.
   *
   * @param player the player who triggered the AoE enchant
   * @param block the block that was just broken (already AIR at this point)
   * @param pickaxeProfile the player's pickaxe profile
   * @param playerProfile the player's player profile
   */
  public static void rollSideEffects(
      Player player, Block block, Pickaxe pickaxeProfile, PlayerProfile playerProfile) {
    int[] acc = accumulation.get(player.getUniqueId());
    if (acc != null) {
      ExtractionEnchant extraction = ExtractionEnchant.getInstance();
      if (extraction != null)
        acc[0] += extraction.tryTriggerForAoESilent(player, block, pickaxeProfile, playerProfile);

      RadioactiveEnchant radioactive = RadioactiveEnchant.getInstance();
      if (radioactive != null)
        acc[1] += radioactive.tryTriggerForAoESilent(player, block, pickaxeProfile, playerProfile);

      MerchantEnchant merchant = MerchantEnchant.getInstance();
      if (merchant != null)
        acc[2] += merchant.tryTriggerForAoESilent(player, block, pickaxeProfile, playerProfile);

      ScavengerEnchant scavenger = ScavengerEnchant.getInstance();
      if (scavenger != null)
        acc[3] += scavenger.tryTriggerForAoESilent(player, block, pickaxeProfile, playerProfile);
    } else {
      ExtractionEnchant extraction = ExtractionEnchant.getInstance();
      if (extraction != null)
        extraction.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);

      RadioactiveEnchant radioactive = RadioactiveEnchant.getInstance();
      if (radioactive != null)
        radioactive.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);

      MerchantEnchant merchant = MerchantEnchant.getInstance();
      if (merchant != null) merchant.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);

      ScavengerEnchant scavenger = ScavengerEnchant.getInstance();
      if (scavenger != null)
        scavenger.tryTriggerForAoE(player, block, pickaxeProfile, playerProfile);
    }
  }

  /**
   * Starts essence/atom accumulation for this player's upcoming AoE proc. While active, {@link
   * #rollSideEffects} silently collects essence and atoms instead of showing per-block holograms.
   */
  public static void startAccumulation(java.util.UUID uuid) {
    accumulation.put(uuid, new int[] {0, 0, 0, 0});
  }

  /**
   * Adds atoms from non-rollSideEffects sources (e.g. Jackhammer Rare dissolution) to the current
   * accumulation buffer. No-op if accumulation is not active.
   */
  public static void addAccumulatedAtoms(java.util.UUID uuid, int count) {
    int[] acc = accumulation.get(uuid);
    if (acc != null) acc[1] += count;
  }

  /**
   * Ends accumulation, calls {@code checkAtoms} if atoms were found, and shows one combined
   * essence/atom hologram centred on {@code origin}.
   */
  public static void flushAccumulation(Player player, Block origin, Pickaxe pickaxeProfile) {
    int[] acc = accumulation.remove(player.getUniqueId());
    if (acc == null) return;
    int totalEssence = acc[0];
    int totalAtoms = acc[1];
    int totalCoins = acc[2];
    int totalSlots = acc[3];

    if (totalAtoms > 0 && pickaxeProfile != null) {
      PickaxeManager.getInstance().checkAtoms(pickaxeProfile, player);
    }

    Location loc = origin.getLocation().add(0.5, 1.5, 0.5);

    double holoY = 1.0;

    if (totalSlots > 0) {
      AnimationRegistry.getInstance()
          .play(
              "hologram",
              origin.getLocation().add(0.5, holoY, 0.5),
              java.util.List.of(player),
              "&#55FF55📦 &#FFFFFF&l+" + Utils.formatNumber(totalSlots) + " &#55FF55Slots");
      holoY += 0.8;
    }

    if (totalCoins > 0) {
      AnimationRegistry.getInstance()
          .play(
              "hologram",
              origin.getLocation().add(0.5, holoY, 0.5),
              java.util.List.of(player),
              "&#FFD700⛁ &#FFFFFF&l+" + Utils.formatNumber(totalCoins) + " &#FFD700Coins");
      holoY += 0.8;
    }

    if (totalAtoms > 0) {
      AnimationRegistry.getInstance().play("atom", loc, java.util.List.of(player), totalAtoms);
      AnimationRegistry.getInstance()
          .play(
              "hologram",
              origin.getLocation().add(0.5, holoY, 0.5),
              java.util.List.of(player),
              "&#FFEE00⚛ &#FFFFFF&l+" + Utils.formatNumber(totalAtoms) + " &#FFEE00Atoms");
      holoY += 0.8;
    }

    if (totalEssence > 0) {
      AnimationRegistry.getInstance().play("essence", loc, java.util.List.of(player), totalEssence);
      AnimationRegistry.getInstance()
          .play(
              "hologram",
              origin.getLocation().add(0.5, holoY, 0.5),
              java.util.List.of(player),
              "&#B366D9🔥 &#FFFFFF&l+" + Utils.formatNumber(totalEssence) + " &#B366D9Essence");
    }
  }

  /** Per-player accumulation context: [0]=essence, [1]=atoms. */
  private static final java.util.Map<java.util.UUID, int[]> accumulation =
      new java.util.concurrent.ConcurrentHashMap<>();

  @SuppressWarnings("deprecation")
  private static Material resolveBaseDrop(Block block, ItemStack tool, JavaPlugin plugin) {
    if (block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) {
      List<MetadataValue> meta = block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY);
      block.removeMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY, plugin);
      FrostbiteBlockManager.getInstance().removeFrozenBlock(block.getLocation());
      if (!meta.isEmpty()) {
        try {
          return Material.valueOf(meta.get(0).asString());
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    Collection<ItemStack> drops = block.getDrops(tool);
    if (!drops.isEmpty()) {
      return drops.iterator().next().getType();
    }
    return block.getType();
  }

  private static Material applyAlchemy(Material mat, Pickaxe pickaxeProfile) {
    if (pickaxeProfile == null || pickaxeProfile.enchants == null) return mat;
    EnchantData data = pickaxeProfile.enchants.getEnchant(Enchants.ALCHEMY);
    if (data == null || !data.enabled || data.level <= 0) return mat;

    double chance = data.getCurrentChance();
    if (AlchemyStoneHandler.hasStone(data, "Uncommon")) chance += 10.0;
    boolean legendary = AlchemyStoneHandler.hasStone(data, "Legendary");
    if (!legendary && !Utils.chance(chance)) return mat;

    AlchemyEnchant alchemy = AlchemyEnchant.getInstance();
    if (alchemy == null) return mat;
    Material upgraded = alchemy.getUpgradeResult(mat);
    return upgraded != null ? upgraded : mat;
  }

  private static Material applyFurnace(Material mat, Pickaxe pickaxeProfile) {
    if (pickaxeProfile == null || pickaxeProfile.enchants == null) return mat;
    EnchantData data = pickaxeProfile.enchants.getEnchant(Enchants.FURNACE);
    if (data == null || !data.enabled || data.level <= 0) return mat;

    boolean legendary = FurnaceStoneHandler.hasStone(data, "Legendary");
    if (!legendary && !Utils.chance(data.getCurrentChance())) return mat;

    FurnaceEnchant furnace = FurnaceEnchant.getInstance();
    if (furnace == null) return mat;
    Material smelted = furnace.getSmeltResult(mat);
    if (smelted == null && legendary) smelted = mat;

    if (smelted == null) return mat;

    if (FurnaceStoneHandler.hasStone(data, "Rare") && Utils.chance(20.0)) {
      Material chained = furnace.getAlchemicalForgeResult(smelted);
      if (chained != null) smelted = chained;
    }

    return smelted;
  }
}
