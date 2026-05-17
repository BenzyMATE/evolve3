package org.benski.evolv3.commands;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Admin command to forcibly set a custom enchant on the held pickaxe. Usage: /enchantpickaxe
 * <enchant_name> <level>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class EnchantPickaxeCommand extends BaseCommand {
  public EnchantPickaxeCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "enchantpickaxe";
  }

  @Override
  public @Nullable String getDescription() {
    return "Set a custom enchant on your held pickaxe";
  }

  @Override
  public @Nullable String getUsage() {
    return "/enchantpickaxe <enchant> <level>";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.admin";
  }

  @Override
  public @Nullable List<String> getAliases() {
    return List.of("epickaxe", "ep");
  }

  @Override
  public void initialize() {}

  @Override
  public void cleanup() {}

  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    Player player = (Player) sender;

    if (!requireArgs(sender, args, 2, getUsage())) return true;

    int level;
    try {
      level = Integer.parseInt(args[args.length - 1]);
    } catch (NumberFormatException e) {
      sendError(player, "Level must be a number.");
      return true;
    }

    if (level < 1) {
      sendError(player, "Level must be at least 1.");
      return true;
    }

    String enchantInput = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1));
    String displayName = resolveDisplayName(enchantInput);
    if (displayName == null) {
      sendError(
          player,
          "Unknown enchant: "
              + args[0]
              + ". Valid names: "
              + String.join(", ", EnchantAnimationConfig.getEnchantOrder()));
      return true;
    }

    int maxLevel = EnchantAnimationConfig.getEnchantMaxLevel(displayName);
    if (level > maxLevel) {
      sendError(player, "Max level for " + displayName + " is " + maxLevel + ".");
      return true;
    }

    ItemStack held = player.getInventory().getItemInMainHand();
    String pickaxeId = held.getData("id");
    if (pickaxeId == null || pickaxeId.isEmpty()) {
      sendError(player, "You must be holding a registered pickaxe.");
      return true;
    }

    PickaxeDataCenter dc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
    if (dc == null) {
      sendError(player, "Pickaxe data centre unavailable.");
      return true;
    }

    PickaxeProfile profile = dc.getAll().get(pickaxeId);
    if (profile == null) {
      sendError(player, "Could not find a profile for this pickaxe.");
      return true;
    }

    String internalName = displayName.toLowerCase().replace(" ", "");
    double maxChance = EnchantAnimationConfig.getEnchantMaxChance(displayName);
    EnchantData existing = profile.enchants.getEnchant(internalName);

    if (existing != null) {
      existing.level = level;
    } else {
      profile.enchants.setEnchant(new EnchantData(internalName, level, maxLevel, maxChance));
    }

    profile.save(null);
    PickaxeManager.getInstance().updateLore(player, null);
    sendSuccess(player, "Set " + displayName + " to level " + level + " on your pickaxe.");
    return true;
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    String currentArg = args[args.length - 1].toLowerCase();
    String typedBefore =
        args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1)) : "";

    if (!typedBefore.isEmpty()) {
      String resolved = resolveDisplayName(typedBefore);
      if (resolved != null) {
        int max = EnchantAnimationConfig.getEnchantMaxLevel(resolved);
        return List.of("1", String.valueOf(max / 2), String.valueOf(max)).stream()
            .filter(s -> s.startsWith(currentArg))
            .toList();
      }
    }

    String prefix = typedBefore.isEmpty() ? currentArg : typedBefore + " " + currentArg;
    return EnchantAnimationConfig.getEnchantOrder().stream()
        .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
        .map(name -> name.split(" ")[typedBefore.isEmpty() ? 0 : typedBefore.split(" ").length])
        .distinct()
        .toList();
  }

  private @Nullable String resolveDisplayName(String input) {
    for (String name : EnchantAnimationConfig.getEnchantOrder()) {
      if (name.equalsIgnoreCase(input)) return name;
    }
    return null;
  }
}
