package org.benski.evolv3.core.managers.pickaxe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.pickaxe.PickaxeEnchants;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Core manager for the Pickaxe Prestige system. */
public class PrestigeManager {
  @Getter private static final PrestigeManager instance = new PrestigeManager();

  /** Creates a new PrestigeManager instance. */
  private PrestigeManager() {}

  /**
   * A rarity + bound-enchant pair returned by {@link #rollPrestigeStones}. The stone is created via
   * {@link org.benski.evolv3.items.EvolutionStone#create}.
   */
  public record StoneRoll(RarityConfig rarity, String enchantName) {}

  /**
   * Rolls {@code count} prestige stones, each with a rarity (≥ configured minimum) and a random
   * enchant binding.
   *
   * @param count number of stones to roll (typically 3)
   * @return list of stone rolls, never null
   */
  public List<StoneRoll> rollPrestigeStones(int count) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    int epicBonus = cfg.getInt(ConfigPaths.Default.Prestige.EPIC_BONUS, 10);
    int legendaryBonus = cfg.getInt(ConfigPaths.Default.Prestige.LEGENDARY_BONUS, 10);
    String minRarityName = cfg.getString(ConfigPaths.Default.Prestige.MIN_STONE_RARITY, "Rare");

    List<RarityConfig> allRarities = EnchantAnimationConfig.getRarities();
    if (allRarities.isEmpty()) return List.of();

    int minIndex = 0;
    for (int i = 0; i < allRarities.size(); i++) {
      if (allRarities.get(i).displayName().equalsIgnoreCase(minRarityName)) {
        minIndex = i;
        break;
      }
    }
    List<RarityConfig> rarities = allRarities.subList(minIndex, allRarities.size());

    int[] weights = new int[rarities.size()];
    int total = 0;
    for (int i = 0; i < rarities.size(); i++) {
      RarityConfig r = rarities.get(i);
      int bonus = 0;
      if ("Epic".equalsIgnoreCase(r.displayName())) bonus = epicBonus;
      else if ("Legendary".equalsIgnoreCase(r.displayName())) bonus = legendaryBonus;
      weights[i] = Math.max(0, r.chance() + bonus);
      total += weights[i];
    }

    List<String> enchants = EnchantAnimationConfig.getEnchantOrder();
    List<StoneRoll> results = new ArrayList<>(count);
    for (int n = 0; n < count; n++) {
      RarityConfig rarity = rollFromWeights(rarities, weights, total);
      String enchant =
          enchants.isEmpty()
              ? "Efficiency"
              : enchants.get(ThreadLocalRandom.current().nextInt(enchants.size()));
      results.add(new StoneRoll(rarity, enchant));
    }
    return results;
  }

  /** Rolls from weights. */
  private RarityConfig rollFromWeights(List<RarityConfig> rarities, int[] weights, int total) {
    if (total <= 0) return rarities.get(0);
    int roll = ThreadLocalRandom.current().nextInt(total);
    int cumulative = 0;
    for (int i = 0; i < rarities.size(); i++) {
      cumulative += weights[i];
      if (roll < cumulative) return rarities.get(i);
    }
    return rarities.get(0);
  }

  /**
   * Executes the prestige operation. Socketed evolution stones are returned to the player's
   * inventory before the enchants are cleared.
   *
   * @param player the owning player
   * @param profile the pickaxe profile to reset
   * @param playerProfile the player profile (for saving)
   * @param roll the chosen stone roll (rarity + enchant)
   */
  @SuppressWarnings("deprecation")
  public void executePrestige(
      Player player, Pickaxe profile, PlayerProfile playerProfile, StoneRoll roll) {
    FileConfiguration defaults = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

    returnSocketedStones(player, profile);

    profile.enchants = new PickaxeEnchants();
    profile.level = defaults.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_LEVEL, 1);
    profile.xp = 0;
    profile.target = defaults.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_TARGET, 100);
    profile.evolution = 1;
    profile.atomVessel.current = 0;
    profile.atomVessel.target = defaults.getInt(ConfigPaths.Default.Atoms.DEFAULT_ATOM_TARGET, 100);
    profile.atomVessel.crystals = 0;
    profile.essenceVessel.crystals = 0;
    profile.essenceVessel.current = 0;
    profile.essenceVessel.target =
        defaults.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ESSENCE_TARGET, 100);
    profile.durability = 0;
    profile.prestige++;

    Material woodenPickaxe = EvolutionTier.WOOD.material;
    updateStoredPickaxeMaterial(playerProfile, profile.key, woodenPickaxe);

    ItemStack held = player.getInventory().getItemInMainHand();
    String heldId = org.benski.evolv3.core.helpers.ItemStackExtensions.getData(held, "id");
    if (profile.key.equals(heldId)) {
      held.setType(woodenPickaxe);
      org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
      if (heldMeta instanceof org.bukkit.inventory.meta.Damageable damageable) {
        damageable.setDamage(0);
        held.setItemMeta(heldMeta);
      }
      player.getInventory().setItemInMainHand(held);
    }

    ItemStack stoneItem =
        org.benski.evolv3.items.EvolutionStone.create(roll.rarity(), roll.enchantName());
    var overflow = player.getInventory().addItem(stoneItem);
    overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));

    profile.save(null);
    playerProfile.save(null);
    ItemStack current = player.getInventory().getItemInMainHand();
    PickaxeManager.getInstance()
        .updateLore(player, current.getType() == Material.AIR ? null : current);

    String rarityHex = String.format("%06X", roll.rarity().textColor().value());
    String stoneMsg =
        messages.getString(
            ConfigPaths.Messages.Pickaxe.PRESTIGE_STONE_GAINED,
            "%evolv3_prefix% &#CCCCCCYou received a &#%rarity_color%%rarity% &#CCCCCCEvolution Stone!");
    stoneMsg =
        stoneMsg
            .replace("%rarity_color%", rarityHex)
            .replace("%rarity%", roll.rarity().displayName());
    player.sendMessage(Utils.formatMessage(player, stoneMsg));

    double multiplier = PickaxeManager.getInstance().getPrestigeMultiplier(profile.prestige);
    List<String> successLines =
        messages.getStringList(ConfigPaths.Messages.Pickaxe.PRESTIGE_SUCCESS);
    if (successLines.isEmpty()) {
      successLines = List.of("  &#FFD700&l✦ PRESTIGE %prestige_number% UNLOCKED ✦");
    }
    for (String line : successLines) {
      String processed =
          line.replace("%prestige_number%", String.valueOf(profile.prestige))
              .replace("%multiplier%", String.format("%.1f", multiplier))
              .replace("%stone_color%", "&#" + rarityHex)
              .replace("%stone_rarity%", roll.rarity().displayName());
      player.sendMessage(Utils.formatMessage(player, processed));
    }

    player.playSound(
        player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
  }

  /** Counts how many evolution stones are currently socketed in the player's pickaxe. */
  public int countSocketedStones(Pickaxe profile) {
    if (profile == null || profile.enchants == null) return 0;
    int count = 0;
    int unlocked = EnchantData.getUnlockedSlotCount(profile.prestige);
    for (EnchantData enchant : profile.enchants.items) {
      for (int i = 0; i < unlocked; i++) {
        if (enchant.socketStones[i] != null) count++;
      }
    }
    return count;
  }

  /** Returns {@code true} if the pickaxe meets the criteria to be prestiged. */
  public boolean canPrestige(Pickaxe profile) {
    return PickaxeManager.getInstance().isPrestigeAvailable(profile);
  }

  /**
   * Returns socketed evolution stones to the player's inventory before the enchants are cleared.
   */
  private void returnSocketedStones(Player player, Pickaxe profile) {
    if (profile.enchants == null) return;
    int unlocked = EnchantData.getUnlockedSlotCount(profile.prestige);
    for (EnchantData enchant : profile.enchants.items) {
      String displayName = EnchantAnimationConfig.getDisplayName(enchant.name);
      for (int i = 0; i < unlocked; i++) {
        String rarityName = enchant.socketStones[i];
        if (rarityName == null) continue;
        RarityConfig rarity = EnchantAnimationConfig.getRarityByName(rarityName);
        if (rarity == null) continue;
        ItemStack stone = org.benski.evolv3.items.EvolutionStone.create(rarity, displayName);
        var overflow = player.getInventory().addItem(stone);
        overflow
            .values()
            .forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
      }
    }
  }

  /** Updates stored pickaxe material. */
  private void updateStoredPickaxeMaterial(
      PlayerProfile playerProfile, String pickaxeId, Material material) {
    List<String> stored = playerProfile.storedPickaxes;
    for (int i = 0; i < stored.size(); i++) {
      String entry = stored.get(i);
      if (entry != null && entry.startsWith(pickaxeId + ":")) {
        stored.set(i, pickaxeId + ":" + material.name());
        return;
      }
    }
  }

  /** Returns the currently active pickaxe profile for the player from the local cache, or null. */
  public Pickaxe getLocalProfile(Player player) {
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    return dc != null ? dc.getPickaxeForPlayer(player) : null;
  }
}
