package org.benski.evolv3.core.managers.pickaxe;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.enchant.EnchantSelectionManager;
import org.benski.evolv3.core.managers.enchant.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.player.EvolveScoreboardManager;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enums.ArmorType;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.items.AncientResin;
import org.benski.evolv3.models.armor.ArmorItem;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Facade for all pickaxe-related operations. Handles progression (XP, level, essence, atoms),
 * enchant application, evolution, and prestige. Lore rendering is delegated to {@link
 * PickaxeLoreRenderer}.
 */
@ExtensionMethod(ItemStackExtensions.class)
public class PickaxeManager {
  @Getter private static final PickaxeManager instance = new PickaxeManager();

  private final Map<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();

  private static final long MINING_COOLDOWN_MS = 2000;

  /** Creates a new PickaxeManager instance. */
  private PickaxeManager() {}

  /**
   * Records that a player just broke a block, used to debounce lore updates mid-swing.
   *
   * @param uuid the player's UUID
   */
  public void recordBreak(UUID uuid) {
    lastBreakTime.put(uuid, System.currentTimeMillis());
  }

  /** Removes all per-player tracking state. Call on player quit. */
  public void cleanupPlayer(UUID uuid) {
    lastBreakTime.remove(uuid);
  }

  /**
   * Returns true if the player broke a block within the last {@value MINING_COOLDOWN_MS} ms.
   *
   * @param uuid the player's UUID
   * @return true if actively mining
   */
  public boolean isActiveMining(UUID uuid) {
    Long last = lastBreakTime.get(uuid);
    return last != null && (System.currentTimeMillis() - last) < MINING_COOLDOWN_MS;
  }

  /** Default config. */
  private FileConfiguration defaultConfig() {
    return ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
  }

  /** Messages config. */
  private FileConfiguration messagesConfig() {
    return ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
  }

  /**
   * Returns the configured maximum prestige level.
   *
   * @return the max prestige (default 5)
   */
  public int getMaxPrestige() {
    return defaultConfig().getInt(ConfigPaths.Default.Prestige.MAX_PRESTIGE, 5);
  }

  /**
   * Returns the level cap for the given prestige tier. At max prestige returns 0 (unlimited).
   *
   * @param prestige the current prestige level
   * @return the level cap, or 0 if at max prestige
   */
  public int getLevelCap(int prestige) {
    if (prestige >= getMaxPrestige()) return 0;
    int levelPerPrestige =
        defaultConfig().getInt(ConfigPaths.Default.Prestige.LEVEL_PER_PRESTIGE, 100);
    return (prestige + 1) * levelPerPrestige;
  }

  /**
   * Returns the essence/atom discovery multiplier for the given prestige level.
   *
   * @param prestige the pickaxe prestige level
   * @return the total multiplier (always &gt;= 1.0)
   */
  public double getPrestigeMultiplier(int prestige) {
    if (prestige <= 0) return 1.0;
    double perPrestige =
        defaultConfig().getDouble(ConfigPaths.Default.Prestige.MULTIPLIER_PER_PRESTIGE, 1.0);
    return 1.0 + (prestige * perPrestige);
  }

  /**
   * Returns {@code true} if the player is currently eligible to prestige.
   *
   * @param profile the pickaxe profile to test
   * @return true when prestige &lt; max and level &gt;= level cap
   */
  public boolean isPrestigeAvailable(Pickaxe profile) {
    int maxPrestige = getMaxPrestige();
    if (profile.prestige >= maxPrestige) return false;
    int cap = getLevelCap(profile.prestige);
    return cap > 0 && profile.level >= cap;
  }

  /**
   * Adds statistics to a player's pickaxe when mining a block. Handles XP gain with armor
   * multipliers, level checks, and action bar updates.
   *
   * @param player the player who mined
   * @param blockType the type of block mined
   * @param playerProfile the player's profile for armor multipliers (optional)
   */
  public void addStatistics(Player player, Material blockType, PlayerProfile playerProfile) {
    if (blockType == Material.AIR) return;

    PlayerDataCenter dataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dataCenter == null) return;
    Pickaxe profile = dataCenter.getPickaxeForPlayer(player);
    if (profile == null) return;

    String xpPath = ConfigPaths.Default.BLOCK_DEFAULTS + "." + blockType.name() + ".XP";
    int xpToAdd = defaultConfig().getInt(xpPath, 0);
    if (xpToAdd <= 0) return;

    xpToAdd = (int) Math.round(xpToAdd * leggingsMultiplier(player));
    profile.xp += xpToAdd;

    checkLevel(profile, player, playerProfile);
    sendMiningActionBar(player, profile);
  }

  /** Sends mining action bar. */
  private void sendMiningActionBar(Player player, Pickaxe profile) {
    if (isPrestigeAvailable(profile)) {
      String prestigeBar =
          messagesConfig()
              .getString(
                  ConfigPaths.Messages.Pickaxe.PRESTIGE_ACTION_BAR,
                  "&5&l⚡ PRESTIGE AVAILABLE &5&l⚡");
      player.sendActionBar(
          LegacyComponentSerializer.legacySection()
              .deserialize(Utils.formatMessage(player, prestigeBar)));
    } else {
      String actionBar = defaultConfig().getString(ConfigPaths.Default.Pickaxe.ACTION_BAR, "");
      if (!actionBar.isEmpty()) {
        player.sendActionBar(
            LegacyComponentSerializer.legacySection()
                .deserialize(Utils.formatMessage(player, actionBar)));
      }
    }
  }

  /**
   * Adds XP directly to a player's pickaxe profile, bypassing block lookups.
   *
   * @param player the player
   * @param amount the XP amount to add
   */
  public void addXp(Player player, int amount) {
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return;
    Pickaxe profile = dc.getPickaxeForPlayer(player);
    if (profile == null) return;
    profile.xp += amount;
    checkLevel(profile, player);
  }

  /** Leggings multiplier. */
  private double leggingsMultiplier(Player player) {
    ArmorDataCenter dc = DataCenterRegistry.getInstance().getArmorDataCenter();
    ArmorProfile armor = dc.getLocal(player);
    return armor != null ? armor.getMiningMultiplier() : 1.0;
  }

  /** Chestplate multiplier. */
  private double chestplateMultiplier(Player player) {
    ArmorDataCenter dc = DataCenterRegistry.getInstance().getArmorDataCenter();
    ArmorProfile armor = dc.getLocal(player);
    return armor != null ? armor.getEssenceMultiplier() : 1.0;
  }

  /**
   * Checks if the player should level up and handles the level up process.
   *
   * @param profile the pickaxe profile
   * @param player the owner player
   */
  public void checkLevel(Pickaxe profile, Player player) {
    checkLevel(profile, player, null);
  }

  /**
   * Checks if the player should level up and handles the level up process.
   *
   * @param profile the pickaxe profile
   * @param player the owner player
   * @param playerProfile the player's profile for armor integration (optional)
   */
  public void checkLevel(Pickaxe profile, Player player, PlayerProfile playerProfile) {
    int levelCap = getLevelCap(profile.prestige);
    if (levelCap > 0 && profile.level >= levelCap) {
      profile.xp = profile.target - 1;
      profile.save(null);
      return;
    }

    if (profile.xp < profile.target) return;

    double prestigeMultiplier = getPrestigeMultiplier(profile.prestige);

    double xpMultiplier =
        defaultConfig().getDouble(ConfigPaths.Default.Pickaxe.XP_MULTIPLIER, 1.15);
    while (profile.xp >= profile.target) {
      if (levelCap > 0 && profile.level >= levelCap) {
        profile.xp = profile.target - 1;
        break;
      }

      int oldLevel = profile.level;
      profile.level++;
      profile.xp -= profile.target;
      profile.target = Math.max(1, (int) (profile.target * xpMultiplier));

      int essenceReward = calculateEssenceReward(profile.level);
      essenceReward =
          (int) Math.round(essenceReward * chestplateMultiplier(player) * prestigeMultiplier);

      profile.essenceVessel.current += essenceReward;

      if (playerProfile != null) {
        playerProfile.addEssence(essenceReward);
        awardChestplateXp(player, essenceReward);
      }

      checkEssence(profile, player);

      int atomReward =
          (int)
              Math.round(
                  defaultConfig().getInt(ConfigPaths.Default.Atoms.LEVEL_UP_REWARD, 2)
                      * prestigeMultiplier);
      profile.atomVessel.current += atomReward;

      checkAtoms(profile, player);

      player.playSound(player.getLocation(), "custom.levelup", 1.0f, 1.0f);

      EvolveScoreboardManager.getInstance().triggerLevelUpAnimation(player, profile.level);
      sendLevelUpMessages(player, profile, oldLevel, essenceReward, atomReward);

      updateLore(player, null);
    }

    profile.save(null);
  }

  /** Awards chestplate xp to the player. */
  private void awardChestplateXp(Player player, int essenceReward) {
    ArmorDataCenter dc = DataCenterRegistry.getInstance().getArmorDataCenter();
    ArmorProfile armorProfile = dc.getLocal(player);
    if (armorProfile == null) return;
    ArmorItem chestplate = armorProfile.getArmor(ArmorType.CHESTPLATE);
    if (chestplate != null && chestplate.isEnabled()) {
      chestplate.addXp(essenceReward);
    }
  }

  /** Sends level up messages. */
  private void sendLevelUpMessages(
      Player player, Pickaxe profile, int oldLevel, int essenceReward, int atomReward) {
    List<String> levelUpLines =
        messagesConfig().getStringList(ConfigPaths.Messages.Pickaxe.LEVEL_UP);
    if (levelUpLines.isEmpty()) {
      Main.getInstance()
          .getLogger()
          .warning("Missing messages.yml key: " + ConfigPaths.Messages.Pickaxe.LEVEL_UP);
      levelUpLines = List.of("Level up! Now level %level%.");
    }
    for (String line : levelUpLines) {
      String processed =
          line.replace("%old_level%", String.valueOf(oldLevel))
              .replace("%level%", String.valueOf(profile.level))
              .replace("%essence%", String.valueOf(essenceReward))
              .replace("%atoms%", String.valueOf(atomReward));
      player.sendMessage(Utils.formatMessage(player, processed));
    }
  }

  /**
   * Checks if the essence threshold is met and awards an enchant roll if so.
   *
   * @param profile the pickaxe profile
   * @param player the owner player
   */
  public void checkEssence(Pickaxe profile, Player player) {
    if (profile.essenceVessel.current < profile.essenceVessel.target) return;

    double multiplier =
        defaultConfig().getDouble(ConfigPaths.Default.Pickaxe.ESSENCE_MULTIPLIER, 1.5);
    int cap = defaultConfig().getInt(ConfigPaths.Default.Pickaxe.ESSENCE_TARGET_CAP, 10000);

    while (profile.essenceVessel.current >= profile.essenceVessel.target) {
      profile.essenceVessel.crystals++;
      profile.essenceVessel.current -= profile.essenceVessel.target;
      profile.essenceVessel.target =
          Math.min(cap, Math.max(1, (int) (profile.essenceVessel.target * multiplier)));
      player.playSound(player.getLocation(), "custom.vesselfull", 1.0f, 1.0f);
      showPickaxeTitle(
          player,
          ConfigPaths.Messages.Pickaxe.ENCHANT_ROLL_TITLE,
          ConfigPaths.Messages.Pickaxe.ENCHANT_ROLL_SUBTITLE,
          "&#B366D9&lEnchant Shard",
          "&#CCCCCCYou earned an Enchant Shard");
    }
  }

  /**
   * Checks if the atom threshold has been reached and awards an evolution roll if so.
   *
   * @param profile the pickaxe profile
   * @param player the owner player
   */
  public void checkAtoms(Pickaxe profile, Player player) {
    if (profile.atomVessel.current < profile.atomVessel.target) return;

    double multiplier =
        defaultConfig().getDouble(ConfigPaths.Default.Atoms.ATOM_TARGET_MULTIPLIER, 1.5);

    while (profile.atomVessel.current >= profile.atomVessel.target) {
      profile.atomVessel.crystals++;
      profile.atomVessel.current -= profile.atomVessel.target;
      profile.atomVessel.target = Math.max(1, (int) (profile.atomVessel.target * multiplier));
      player.playSound(player.getLocation(), "custom.vesselfull", 1.0f, 1.0f);
      showPickaxeTitle(
          player,
          ConfigPaths.Messages.Pickaxe.EVOLVE_SUCCESS_TITLE,
          ConfigPaths.Messages.Pickaxe.EVOLVE_SUCCESS_SUBTITLE,
          "&#FFD700&lEvolution Shard",
          "&#CCCCCCYou earned an Evolution Shard");
    }
  }

  /** Shows pickaxe title to the player. */
  private void showPickaxeTitle(
      Player player,
      String titlePath,
      String subtitlePath,
      String defaultTitle,
      String defaultSubtitle) {
    FileConfiguration messages = messagesConfig();
    Component title =
        LegacyComponentSerializer.legacySection()
            .deserialize(Utils.formatMessage(player, messages.getString(titlePath, defaultTitle)))
            .decoration(TextDecoration.ITALIC, false);
    Component subtitle =
        LegacyComponentSerializer.legacySection()
            .deserialize(
                Utils.formatMessage(player, messages.getString(subtitlePath, defaultSubtitle)))
            .decoration(TextDecoration.ITALIC, false);
    player.showTitle(
        Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
  }

  /**
   * Validates whether a player can evolve their pickaxe.
   *
   * @param player the player attempting to evolve
   * @param profile the pickaxe profile to check
   * @return true if evolution is possible, false if blocked
   */
  public boolean validateEvolution(
      @org.jetbrains.annotations.NotNull Player player,
      @org.jetbrains.annotations.NotNull Pickaxe profile) {
    FileConfiguration messages = messagesConfig();
    EvolutionTier current = EvolutionTier.fromLevel(profile.evolution);

    if (current.isMax()) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.EVOLVE_MAX_TIER,
              "%evolv3_prefix% &#CCCCCCYour pickaxe is already at the highest tier.");
      player.sendMessage(Utils.formatMessage(player, msg));
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
      return false;
    }

    if (profile.atomVessel.crystals <= 0) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.EVOLVE_INSUFFICIENT_SPINS,
              "%evolv3_prefix% &#CCCCCCNot enough Evolution Rolls.");
      player.sendMessage(Utils.formatMessage(player, msg));
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return false;
    }

    return true;
  }

  /**
   * Validates and starts an evolution for the player, deducts the roll, rolls the reward stone
   * rarity, then hands off to {@link EvolutionSelectionManager}'s animation. Shared by every menu
   * that exposes an evolution button (currently the pickaxe menu and the stone socket menu) so the
   * eligibility/roll logic isn't duplicated per-menu.
   *
   * @param player the player evolving
   * @param profile the player's pickaxe profile
   * @param playerProfile the player's overall profile, needed for the essence reward on completion
   * @param onMenuClose invoked right before the animation starts, so the calling menu can close its
   *     own inventory first (the animation requires an unobstructed view)
   */
  public void startEvolution(
      @org.jetbrains.annotations.NotNull Player player,
      @org.jetbrains.annotations.NotNull Pickaxe profile,
      @org.jetbrains.annotations.NotNull PlayerProfile playerProfile,
      @org.jetbrains.annotations.NotNull Runnable onMenuClose) {
    if (!validateEvolution(player, profile)) return;

    EvolutionTier current = EvolutionTier.fromLevel(profile.evolution);
    EvolutionTier next = current.next();
    if (next == null) return;

    profile.atomVessel.crystals--;
    profile.save(null);

    org.benski.evolv3.core.types.RarityConfig stone = EnchantAnimationConfig.rollRarity();
    if (stone == null) return;

    onMenuClose.run();

    EvolutionTier finalNext = next;
    org.benski.evolv3.core.types.RarityConfig finalStone = stone;

    EvolutionSelectionManager.getInstance()
        .startEvolution(
            player,
            profile.key,
            current,
            finalNext,
            finalStone,
            () -> applyEvolutionResult(player, profile, playerProfile, finalNext, finalStone));
  }

  /**
   * Applies the result of an evolution after the animation completes.
   *
   * @param player the owning player
   * @param profile the pickaxe profile to update
   * @param playerProfile the player's profile (holds stored-pickaxe entries)
   * @param next the tier the pickaxe is advancing to
   * @param stone the evolution stone rarity awarded
   */
  @SuppressWarnings("deprecation")
  public void applyEvolutionResult(
      @org.jetbrains.annotations.NotNull Player player,
      @org.jetbrains.annotations.NotNull Pickaxe profile,
      @org.jetbrains.annotations.NotNull PlayerProfile playerProfile,
      @org.jetbrains.annotations.NotNull EvolutionTier next,
      @org.jetbrains.annotations.NotNull org.benski.evolv3.core.types.RarityConfig stone) {
    profile.evolution = next.level;

    updateStoredPickaxeMaterial(playerProfile, profile.key, next.material);

    ItemStack held = player.getInventory().getItemInMainHand();
    String heldId = ItemStackExtensions.getData(held, "id");
    if (profile.key.equals(heldId)) {
      held.setType(next.material);
      player.getInventory().setItemInMainHand(held);
    }

    int essenceReward =
        messagesConfig().getInt(ConfigPaths.Messages.Pickaxe.EVOLVE_ESSENCE_REWARD, 250);
    profile.essenceVessel.current += essenceReward;
    playerProfile.addEssence(essenceReward);
    checkEssence(profile, player);

    profile.save(null);
    playerProfile.save(null);
    updateLore(player, null);

    sendEvolutionResultMessages(player, profile, playerProfile, next, stone, essenceReward);
  }

  /** Sends evolution result messages. */
  private void sendEvolutionResultMessages(
      Player player,
      Pickaxe profile,
      PlayerProfile playerProfile,
      EvolutionTier next,
      org.benski.evolv3.core.types.RarityConfig stone,
      int essenceReward) {
    String rarityHex = String.format("%06X", stone.textColor().value());
    String stoneColor = "&#" + rarityHex;

    FileConfiguration messages = messagesConfig();
    List<String> resultLines = messages.getStringList(ConfigPaths.Messages.Pickaxe.EVOLVE_RESULT);
    if (resultLines.isEmpty()) {
      String fallback =
          "%evolv3_prefix% &#FFD700&l[Evolution] &#CCCCCCPickaxe evolved to %tier%&#CCCCCC!";
      player.sendMessage(Utils.formatMessage(player, fallback.replace("%tier%", next.displayName)));
      return;
    }
    for (String line : resultLines) {
      String formatted =
          line.replace("%tier%", next.displayName)
              .replace("%evolution_level%", String.valueOf(next.level))
              .replace("%essence%", String.valueOf(essenceReward))
              .replace("%stone_color%", stoneColor)
              .replace("%stone_rarity%", stone.displayName());
      player.sendMessage(Utils.formatMessage(player, formatted));
    }
  }

  /** Updates stored pickaxe material. */
  private void updateStoredPickaxeMaterial(
      @org.jetbrains.annotations.NotNull PlayerProfile playerProfile,
      @org.jetbrains.annotations.NotNull String pickaxeId,
      @org.jetbrains.annotations.NotNull Material material) {
    List<String> stored = playerProfile.storedPickaxes;
    for (int i = 0; i < stored.size(); i++) {
      String entry = stored.get(i);
      if (entry != null && entry.startsWith(pickaxeId + ":")) {
        stored.set(i, pickaxeId + ":" + material.name());
        return;
      }
    }
  }

  /** Calculates essence reward. */
  private int calculateEssenceReward(int level) {
    int increment = defaultConfig().getInt(ConfigPaths.Default.Essence.TIER_INCREMENT, 10);
    int maxCap = defaultConfig().getInt(ConfigPaths.Default.Essence.MAX_CAP, 1000);
    int minAmount = Math.min(((level - 1) * increment) + 1, maxCap);
    int maxAmount = Math.min(level * increment, maxCap);
    return ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
  }

  /**
   * Validates and starts an enchant-roll selection for the player: rejects it if the player already
   * has an active enchant selection or evolution in progress, or doesn't have a roll to spend;
   * otherwise deducts the roll and hands off to {@link EnchantSelectionManager}'s animation. Shared
   * by every menu that exposes a roll button (currently the pickaxe menu and the stone socket menu)
   * so the eligibility/roll logic isn't duplicated per-menu.
   *
   * @param player the player rolling
   * @param profile the player's pickaxe profile
   * @param onMenuClose invoked right before the animation starts, so the calling menu can close its
   *     own inventory first (the animation requires an unobstructed view)
   */
  public void startEnchantRoll(
      @org.jetbrains.annotations.NotNull Player player,
      @org.jetbrains.annotations.NotNull Pickaxe profile,
      @org.jetbrains.annotations.NotNull Runnable onMenuClose) {
    FileConfiguration messages = messagesConfig();

    if (EnchantSelectionManager.getInstance().hasActiveSelection(player)) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_SELECTION_ALREADY_ACTIVE,
              "&cYou already have an enchant selection active!");
      player.sendMessage(Utils.formatMessage(player, msg));
      return;
    }

    if (EvolutionSelectionManager.getInstance().hasActiveEvolution(player)) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_SELECTION_ALREADY_ACTIVE,
              "&cAn evolution is already in progress!");
      player.sendMessage(Utils.formatMessage(player, msg));
      return;
    }

    if (profile.essenceVessel.crystals <= 0) {
      String msg =
          messages.getString(
              ConfigPaths.Messages.Pickaxe.ENCHANT_NO_SPINS,
              "%evolv3_prefix% &cYou need at least 1 enchant spin to use this.");
      player.sendMessage(Utils.formatMessage(player, msg));
      return;
    }

    profile.essenceVessel.crystals--;
    profile.save();

    onMenuClose.run();

    EnchantSelectionManager.getInstance()
        .startSelection(
            player,
            profile.key,
            (p, selected) -> {
              PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
              if (dc == null) return;
              Pickaxe updatedProfile = dc.getPickaxeForPlayer(p);
              if (updatedProfile == null) return;
              applyEnchantFromSelection(p, selected, updatedProfile);
            });
  }

  /**
   * Applies an enchant from the selection animation to a pickaxe. Upgrades an existing enchant or
   * unlocks a new one at level 1.
   *
   * @param player the player who selected the enchant
   * @param selected the enchant option returned by the animation
   * @param profile the player's current pickaxe profile
   */
  public void applyEnchantFromSelection(Player player, EnchantOption selected, Pickaxe profile) {
    String internalName = selected.name().toLowerCase().replace(" ", "");
    int maxLevel = EnchantAnimationConfig.getEnchantMaxLevel(selected.name());

    EnchantData existing = profile.enchants.getEnchant(internalName);

    if (existing != null) {
      if (existing.isMaxLevel()) {
        String msg =
            messagesConfig()
                .getString(
                    ConfigPaths.Messages.Pickaxe.ENCHANT_MAX_LEVEL,
                    "%evolv3_prefix% &7Your enchant is already at maximum level.")
                .replace("%enchant_name%", selected.name())
                .replace("%max_level%", String.valueOf(existing.maxLevel));
        player.sendMessage(Utils.formatMessage(player, msg));
      } else {
        existing.level++;
      }
    } else {
      double maxChance = EnchantAnimationConfig.getEnchantMaxChance(selected.name());
      profile.enchants.setEnchant(new EnchantData(internalName, 1, maxLevel, maxChance));
    }

    profile.save(null);
    updateLore(player, null);
  }

  /**
   * Upgrades an enchant by spending essence from the player's profile.
   *
   * @param player the player upgrading the enchant
   * @param profile the player's pickaxe profile
   * @param playerProfile the player's overall profile (essence source)
   * @param displayName the enchant's display name
   * @return {@code true} if the upgrade succeeded
   */
  public boolean upgradeEnchantWithEssence(
      Player player, Pickaxe profile, PlayerProfile playerProfile, String displayName) {
    String internalName = displayName.toLowerCase().replace(" ", "");
    EnchantData enchant = profile.enchants.getEnchant(internalName);
    FileConfiguration messages = messagesConfig();

    if (enchant == null) {
      player.sendMessage(
          Utils.formatMessage(
              player,
              messages.getString(
                  ConfigPaths.Messages.Pickaxe.ENCHANT_NOT_UNLOCKED,
                  "%evolv3_prefix% &cThis enchant has not been unlocked yet.")));
      return false;
    }

    if (enchant.isMaxLevel()) {
      String msg =
          messages
              .getString(
                  ConfigPaths.Messages.Pickaxe.ENCHANT_MAX_LEVEL,
                  "%evolv3_prefix% &7%enchant_name% is already at maximum level %max_level%.")
              .replace("%enchant_name%", displayName)
              .replace("%max_level%", String.valueOf(enchant.maxLevel));
      player.sendMessage(Utils.formatMessage(player, msg));
      return false;
    }

    int cost = EnchantAnimationConfig.getEnchantCost(displayName) * enchant.level;

    if (playerProfile.wallet.essence < cost) {
      String msg =
          messages
              .getString(
                  ConfigPaths.Messages.Pickaxe.ENCHANT_INSUFFICIENT_ESSENCE,
                  "%evolv3_prefix% &7You need &5🔥 &d%required_essence% &7essence to upgrade &f%enchant_name%&7.")
              .replace("%required_essence%", String.valueOf(cost))
              .replace("%enchant_name%", displayName);
      player.sendMessage(Utils.formatMessage(player, msg));
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return false;
    }

    playerProfile.wallet.essence -= cost;
    enchant.level++;

    profile.save(null);
    playerProfile.save(null);
    updateLore(player, null);

    String msg =
        messages
            .getString(
                ConfigPaths.Messages.Pickaxe.ENCHANT_UPGRADE_SUCCESS,
                "%evolv3_prefix% &7Successfully upgraded &f%enchant_name% &7to level &e%new_level%&7!")
            .replace("%enchant_name%", displayName)
            .replace("%new_level%", String.valueOf(enchant.level));
    player.sendMessage(Utils.formatMessage(player, msg));
    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

    return true;
  }

  /**
   * Repairs the pickaxe held in the player's main hand, spending Ancient Resin from their
   * inventory. Shared by every menu that exposes a repair action (currently the pickaxe menu and
   * the stone socket menu) so the validation/cost logic isn't duplicated per-menu.
   *
   * @param player the player repairing their held pickaxe
   * @param onMenuClose invoked on a successful repair, so the calling menu can close its own
   *     inventory the same way the pickaxe menu always has
   */
  public void repairPickaxe(
      @org.jetbrains.annotations.NotNull Player player,
      @org.jetbrains.annotations.NotNull Runnable onMenuClose) {
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType() == Material.AIR || !Utils.isPickaxe(held.getType())) {
      String msg =
          messagesConfig()
              .getString(
                  ConfigPaths.Messages.Pickaxe.NO_PICKAXE,
                  "%evolv3_prefix% &cYou must be holding a pickaxe to repair it.");
      player.sendMessage(Utils.formatMessage(player, msg));
      return;
    }

    ItemMeta heldMeta = held.getItemMeta();
    if (!(heldMeta instanceof Damageable dmg)) return;

    if (dmg.getDamage() == 0) {
      sendRepairMessageList(player, ConfigPaths.Messages.PickaxeLimit.REPAIR_ALREADY_FULL);
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return;
    }

    int repairCost = defaultConfig().getInt(ConfigPaths.Default.AncientResin.REPAIR_COST, 5);

    if (countAncientResinInInventory(player) < repairCost) {
      sendRepairMessageList(
          player,
          ConfigPaths.Messages.PickaxeLimit.REPAIR_INSUFFICIENT_WOOD,
          "%required%",
          String.valueOf(repairCost));
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return;
    }

    removeAncientResinFromInventory(player, repairCost);

    dmg.setDamage(0);
    held.setItemMeta(dmg);
    player.getInventory().setItemInMainHand(held);

    String pickaxeId = ItemStackExtensions.getData(held, "id");
    if (pickaxeId != null && !pickaxeId.isEmpty()) {
      PlayerDataCenter repairPdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
      Pickaxe repairProfile = repairPdc != null ? repairPdc.getPickaxeByItemId(pickaxeId) : null;
      if (repairProfile != null) {
        repairProfile.durability = 0;
        repairProfile.save(null);
      }
    }

    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    sendRepairMessageList(player, ConfigPaths.Messages.PickaxeLimit.REPAIR_SUCCESS);
    onMenuClose.run();
  }

  /** Sends a config-driven message list to the player, applying token replacements. */
  private void sendRepairMessageList(Player player, String path, String... replacements) {
    List<String> lines = messagesConfig().getStringList(path);
    for (String line : lines) {
      for (int i = 0; i + 1 < replacements.length; i += 2) {
        line = line.replace(replacements[i], replacements[i + 1]);
      }
      player.sendMessage(Utils.formatMessage(player, line));
    }
  }

  /** Counts Ancient Resin across the player's entire inventory. */
  private int countAncientResinInInventory(Player player) {
    int total = 0;
    for (ItemStack item : player.getInventory().getContents()) {
      if (AncientResin.isAncientResin(item)) total += item.getAmount();
    }
    return total;
  }

  /** Removes the given amount of Ancient Resin from the player's inventory. */
  private void removeAncientResinFromInventory(Player player, int amount) {
    int remaining = amount;
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && remaining > 0; i++) {
      ItemStack item = contents[i];
      if (!AncientResin.isAncientResin(item)) continue;
      if (item.getAmount() <= remaining) {
        remaining -= item.getAmount();
        player.getInventory().setItem(i, new ItemStack(Material.AIR));
      } else {
        item.setAmount(item.getAmount() - remaining);
        remaining = 0;
      }
    }
  }

  /**
   * @see PickaxeLoreRenderer#buildLoreComponents(Player, Pickaxe, List)
   */
  public java.util.List<net.kyori.adventure.text.Component> buildLoreComponents(
      Player player, Pickaxe profile, java.util.List<String> footerLines) {
    return PickaxeLoreRenderer.getInstance().buildLoreComponents(player, profile, footerLines);
  }

  /**
   * @see PickaxeLoreRenderer#buildLoreComponents(Player, Pickaxe, List, String)
   */
  public java.util.List<net.kyori.adventure.text.Component> buildLoreComponents(
      Player player, Pickaxe profile, java.util.List<String> footerLines, String durabilityBar) {
    return PickaxeLoreRenderer.getInstance()
        .buildLoreComponents(player, profile, footerLines, durabilityBar);
  }

  /**
   * @see PickaxeLoreRenderer#updateLore(Player, ItemStack)
   */
  public void updateLore(Player player, ItemStack itemStack) {
    PickaxeLoreRenderer.getInstance().updateLore(player, itemStack);
  }

  /**
   * @see PickaxeLoreRenderer#updateLoreFromInventory(Player)
   */
  public void updateLoreFromInventory(Player player) {
    PickaxeLoreRenderer.getInstance().updateLoreFromInventory(player);
  }

  /**
   * @see PickaxeLoreRenderer#applyLoreAndName(Player, Pickaxe, ItemStack)
   */
  public void applyLoreAndName(Player player, Pickaxe profile, ItemStack item) {
    PickaxeLoreRenderer.getInstance().applyLoreAndName(player, profile, item);
  }
}
