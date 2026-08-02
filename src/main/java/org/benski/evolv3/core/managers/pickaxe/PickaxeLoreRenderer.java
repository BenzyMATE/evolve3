package org.benski.evolv3.core.managers.pickaxe;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.ItemTagHelper;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.passive.EfficiencyEnchant;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and applies lore, display names, and enchant decorators to pickaxe item stacks. Extracted
 * from {@link PickaxeManager} as a single-concern rendering layer. All methods are pure rendering
 * (no progression state mutations).
 */
@ExtensionMethod(ItemStackExtensions.class)
public final class PickaxeLoreRenderer {
  @Getter private static final PickaxeLoreRenderer instance = new PickaxeLoreRenderer();

  /** Creates a new PickaxeLoreRenderer instance. */
  private PickaxeLoreRenderer() {}

  /** Default config. */
  private FileConfiguration defaultConfig() {
    return ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
  }

  /** Enchants config. */
  private FileConfiguration enchantsConfig() {
    return ConfigRegistry.getInstance().get(ConfigNames.ENCHANTS);
  }

  /** Messages config. */
  private FileConfiguration messagesConfig() {
    return ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
  }

  /**
   * Fetches the player's pickaxe profile and updates the held item's lore, or the given item if
   * non-null.
   *
   * @param player the player
   * @param itemStack the item to update, or null to use main hand
   */
  public void updateLore(Player player, ItemStack itemStack) {
    PlayerDataCenter dataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dataCenter == null) return;
    Pickaxe profile = dataCenter.getPickaxeForPlayer(player);
    if (profile == null) return;
    ItemStack item = itemStack != null ? itemStack : player.getInventory().getItemInMainHand();
    if (item.getType() == Material.AIR) return;
    applyLoreAndName(player, profile, item);
  }

  /**
   * Scans the player's full inventory for a pickaxe and updates its lore. Used on join when the
   * player may not be holding the pickaxe in main hand.
   */
  public void updateLoreFromInventory(Player player) {
    PlayerDataCenter dataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dataCenter == null) return;
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      ItemStack item = player.getInventory().getItem(slot);
      if (item == null || item.getType() == Material.AIR) continue;
      if (!item.getType().name().endsWith("_PICKAXE")) continue;
      Pickaxe profile = dataCenter.getPickaxeForPlayer(player);
      if (profile == null) return;
      applyLoreAndName(player, profile, item);
      return; // NOPMD - intentional find-first loop
    }
  }

  /**
   * Builds Adventure {@link Component} lore for a specific profile, using optional footer lines
   * instead of the config footer. Used when rendering stored (off-hand) pickaxes.
   *
   * @param player the viewing player (for color formatting)
   * @param profile the pickaxe profile to render
   * @param footerLines lines to append after the main lore template
   * @return ready-to-set lore components
   */
  public List<Component> buildLoreComponents(
      Player player, Pickaxe profile, List<String> footerLines) {
    return buildLoreComponents(player, profile, footerLines, "");
  }

  /**
   * Builds Adventure {@link Component} lore for a specific profile with an explicit durability bar
   * string.
   */
  public List<Component> buildLoreComponents(
      Player player, Pickaxe profile, List<String> footerLines, String durabilityBar) {
    List<String> loreTemplate = defaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE);
    List<String> rawLore = buildFormattedLoreStrings(player, profile, loreTemplate, durabilityBar);

    for (String line : footerLines) {
      rawLore.add(
          Utils.formatMessage(player, substitutePlaceholders(line, profile, durabilityBar)));
    }

    return toComponents(rawLore);
  }

  /**
   * Directly applies lore, display name, and enchant modifiers to an item using a known profile.
   * Unlike {@link #updateLore}, this does not require the item to be in the player's main hand.
   *
   * @param player the player (used for placeholder resolution)
   * @param profile the pickaxe profile to read stats from
   * @param item the item to update in place
   */
  @SuppressWarnings("deprecation")
  public void applyLoreAndName(Player player, Pickaxe profile, ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;

    int damage = meta instanceof Damageable dmg ? dmg.getDamage() : 0;
    String durabilityBar = Utils.buildDurabilityBar(damage, item.getType().getMaxDurability());

    List<String> loreTemplate = defaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE);
    List<String> loreFooter =
        defaultConfig().getStringList(ConfigPaths.Default.Pickaxe.LORE_FOOTER);

    List<String> formattedLore =
        buildFormattedLoreStrings(player, profile, loreTemplate, durabilityBar);

    String artifactGlyph = ItemTagHelper.getRarityGlyph("artifact");
    String omnitoolGlyph = ItemTagHelper.getTypeGlyph("omnitool");
    String tagLine =
        "&f "
            + (artifactGlyph != null ? artifactGlyph : "")
            + (omnitoolGlyph != null ? omnitoolGlyph : "");
    formattedLore.add(0, Utils.formatMessage(player, tagLine));

    formattedLore.add("");
    String durabilityLabel =
        defaultConfig()
            .getString(ConfigPaths.Default.Pickaxe.DURABILITY_LABEL, " &#00FFFFDurability:");
    formattedLore.add(Utils.formatMessage(player, durabilityLabel));
    String barPrefix =
        defaultConfig().getString(ConfigPaths.Default.Pickaxe.DURABILITY_BAR_PREFIX, " ");
    formattedLore.add(Utils.formatMessage(player, barPrefix + durabilityBar));

    for (String line : loreFooter) {
      formattedLore.add(
          Utils.formatMessage(player, substitutePlaceholders(line, profile, durabilityBar)));
    }

    meta.lore(toComponents(formattedLore));

    String name = defaultConfig().getString(ConfigPaths.Default.Pickaxe.NAME, "&bPickaxe");
    meta.displayName(
        LegacyComponentSerializer.legacySection()
            .deserialize(Utils.formatMessage(player, name))
            .decoration(TextDecoration.ITALIC, false));

    EnchantData efficiency = profile.enchants.getEnchant(Enchants.EFFICIENCY);
    if (efficiency != null && efficiency.level > 0) {
      EfficiencyEnchant.applySpeedModifier(meta, efficiency.level, efficiency);
    } else {
      EfficiencyEnchant.removeSpeedModifier(meta);
    }

    Enchantment glowEnchant = ItemStackExtensions.getEnchantmentByKey("minecraft", "unbreaking");
    if (glowEnchant != null) {
      if (!profile.enchants.isEmpty()) {
        meta.addEnchant(glowEnchant, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      } else {
        meta.removeEnchant(glowEnchant);
      }
    }

    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    item.setItemMeta(meta);
    ItemTagHelper.applyTooltipStyle(item, "artifact");
  }

  /** Builds formatted lore strings. */
  private List<String> buildFormattedLoreStrings(
      Player player, Pickaxe profile, List<String> template, String durabilityBar) {
    List<String> result = new ArrayList<>();
    for (String line : template) {
      if (line.contains("%evolv3_pickaxe_enchants%")) {
        appendEnchantsLore(profile, result, player);
      } else if (line.contains("%evolv3_pickaxe_prestige_lore%")) {
        appendPrestigeLore(player, profile, durabilityBar, result);
      } else {
        result.add(
            Utils.formatMessage(player, substitutePlaceholders(line, profile, durabilityBar)));
      }
    }
    return result;
  }

  /** Append prestige lore. */
  private void appendPrestigeLore(
      Player player, Pickaxe profile, String durabilityBar, List<String> result) {
    if (profile.prestige <= 0) return;
    String prestigeLine =
        messagesConfig()
            .getString(ConfigPaths.Messages.Pickaxe.PRESTIGE_LORE_LINE, " &5Prestige &d%prestige%");
    double mult = PickaxeManager.getInstance().getPrestigeMultiplier(profile.prestige);
    prestigeLine =
        prestigeLine
            .replace("%prestige%", String.valueOf(profile.prestige))
            .replace("%multiplier%", String.format("%.1f", mult));
    result.add(
        Utils.formatMessage(player, substitutePlaceholders(prestigeLine, profile, durabilityBar)));
  }

  /** Append enchants lore. */
  private void appendEnchantsLore(Pickaxe profile, List<String> newLore, Player player) {
    if (profile.enchants == null || profile.enchants.isEmpty()) {
      String noEnchants = enchantsConfig().getString(ConfigPaths.Enchants.NO_ENCHANTS, "&cNone");
      newLore.add(Utils.formatMessage(player, " " + noEnchants));
      return;
    }

    List<EnchantData> sorted =
        profile.enchants.items.stream()
            .sorted(
                java.util.Comparator.comparingInt(
                    e ->
                        EnchantAnimationConfig.getEnchantRarityIndex(
                            EnchantAnimationConfig.getDisplayName(e.name))))
            .toList();

    for (EnchantData enchant : sorted) {
      newLore.add(buildEnchantLoreLine(enchant, profile, player));
    }
  }

  /** Builds enchant lore line. */
  private String buildEnchantLoreLine(EnchantData enchant, Pickaxe profile, Player player) {
    String displayName = EnchantAnimationConfig.getDisplayName(enchant.name);
    var option = EnchantAnimationConfig.getOption(displayName);
    String rarityColor =
        option != null ? "&" + option.rarity().textColor().asHexString().toUpperCase() : "&#CCCCCC";

    String levelDisplay =
        enchant.isMaxLevel()
            ? enchantsConfig().getString(ConfigPaths.Enchants.MAX_ENCHANT, "&6&lMAX")
            : String.valueOf(enchant.level);

    String stoneIcons = buildStoneIcons(enchant, profile.prestige);
    return Utils.formatMessage(
        player, " " + stoneIcons + rarityColor + displayName + " " + levelDisplay);
  }

  /**
   * Builds one grey (empty) or rarity-coloured (filled) socket dot per unlocked socket, always
   * shown regardless of whether any are actually filled — so a bare enchant still shows how many
   * socket slots it has available.
   */
  private String buildStoneIcons(EnchantData enchant, int prestige) {
    int unlockedSockets = EnchantData.getUnlockedSlotCount(prestige);
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < unlockedSockets; i++) {
      String stoneRarity = enchant.socketStones[i];
      if (stoneRarity != null) {
        RarityConfig stoneRarConfig = EnchantAnimationConfig.getRarityByName(stoneRarity);
        String hex =
            stoneRarConfig != null
                ? String.format("%06X", stoneRarConfig.textColor().value())
                : "FFFFFF";
        builder.append("&#").append(hex).append("◆");
      } else {
        builder.append("&#444444◆");
      }
    }

    return builder.length() > 0 ? builder + " " : "";
  }

  /** Substitute placeholders. */
  private String substitutePlaceholders(String line, Pickaxe profile, String durabilityBar) {
    Player owner = profile.getOwner();
    String ownerName = owner != null ? owner.getName() : "";
    return line.replace("%evolv3_player_name%", ownerName)
        .replace("%evolv3_pickaxe_level%", String.valueOf(profile.level))
        .replace("%evolv3_pickaxe_xp%", Utils.formatNumber(profile.xp))
        .replace("%evolv3_pickaxe_target%", Utils.formatNumber(profile.target))
        .replace("%evolv3_pickaxe_percentage%", profile.getPercentage())
        .replace("%evolv3_pickaxe_progressbar%", profile.getProgressBar())
        .replace("%evolv3_pickaxe_durability_bar%", durabilityBar)
        .replace("%evolv3_pickaxe_essence%", Utils.formatNumber(profile.essenceVessel.current))
        .replace(
            "%evolv3_pickaxe_target_essence%", Utils.formatNumber(profile.essenceVessel.target))
        .replace(
            "%evolv3_pickaxe_enchant_rolls%", Utils.formatNumber(profile.essenceVessel.crystals))
        .replace("%evolv3_pickaxe_evolution_atoms%", Utils.formatNumber(profile.atomVessel.current))
        .replace("%evolv3_pickaxe_atom_target%", Utils.formatNumber(profile.atomVessel.target))
        .replace(
            "%evolv3_pickaxe_evolution_rolls%", Utils.formatNumber(profile.atomVessel.crystals))
        .replace("%evolv3_pickaxe_prestige%", String.valueOf(profile.prestige));
  }

  /** To components. */
  private List<Component> toComponents(List<String> lines) {
    LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
    List<Component> components = new ArrayList<>(lines.size());
    for (String line : lines) {
      if (line.isEmpty()) {
        components.add(Component.empty());
      } else {
        components.add(serializer.deserialize(line).decoration(TextDecoration.ITALIC, false));
      }
    }
    return components;
  }
}
