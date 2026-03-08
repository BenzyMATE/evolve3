package org.benski.evolv3.commands;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.managers.PickaxeManager;
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

import java.util.List;

/**
 * Admin command to forcibly set a custom enchant on the held pickaxe.
 * Usage: /enchantpickaxe <enchant_name> <level>
 */
@ExtensionMethod(ItemStackExtensions.class)
public class EnchantPickaxeCommand extends BaseCommand
{
    public EnchantPickaxeCommand(JavaPlugin plugin) {
        super(plugin);
    }

    @Override public String getCommandName()            { return "enchantpickaxe"; }
    @Override public @Nullable String getDescription()  { return "Set a custom enchant on your held pickaxe"; }
    @Override public @Nullable String getUsage()        { return "/enchantpickaxe <enchant> <level>"; }
    @Override public @Nullable String getPermission()   { return "evolv3.admin"; }
    @Override public @Nullable List<String> getAliases(){ return List.of("epickaxe", "ep"); }
    @Override public void initialize() {}
    @Override public void cleanup() {}

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args)
    {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;

        if (!requireArgs(sender, args, 2, getUsage())) return true;

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendError(player, "Level must be a number.");
            return true;
        }

        if (level < 1) {
            sendError(player, "Level must be at least 1.");
            return true;
        }

        String displayName = resolveDisplayName(args[0]);
        if (displayName == null) {
            sendError(player, "Unknown enchant: " + args[0]
                    + ". Valid names: " + String.join(", ", EnchantAnimationConfig.getEnchantOrder()));
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
        if (dc == null) { sendError(player, "Pickaxe data centre unavailable."); return true; }

        PickaxeProfile profile = dc.getAll().get(pickaxeId);
        if (profile == null) { sendError(player, "Could not find a profile for this pickaxe."); return true; }

        String internalName  = displayName.toLowerCase();
        double maxChance     = EnchantAnimationConfig.getEnchantMaxChance(displayName);
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
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return EnchantAnimationConfig.getEnchantOrder().stream()
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .toList();
        }
        if (args.length == 2) {
            String displayName = resolveDisplayName(args[0]);
            if (displayName != null) {
                int max = EnchantAnimationConfig.getEnchantMaxLevel(displayName);
                return List.of("1", String.valueOf(max / 2), String.valueOf(max));
            }
        }
        return null;
    }

    private @Nullable String resolveDisplayName(String input)
    {
        for (String name : EnchantAnimationConfig.getEnchantOrder()) {
            if (name.equalsIgnoreCase(input)) return name;
        }
        return null;
    }
}
