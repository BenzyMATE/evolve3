package org.benski.evolv3.commands;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main admin command for the Evolv3 plugin.
 * Provides configuration management, help, and testing utilities.
 * Usage: /evolv3 <subcommand> [args]
 */
public class Evolv3Command extends BaseCommand
{
    public Evolv3Command(JavaPlugin plugin)
    {
        super(plugin);
    }

    /** {@inheritDoc} */
    @Override
    public String getCommandName()
    {
        return "evolv3";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getDescription()
    {
        return "Main admin command for Evolv3 plugin";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getUsage()
    {
        return "/evolv3 <help|reload|info|resetpickaxes|resetprofile>";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getPermission()
    {
        return "evolv3.admin";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getAliases()
    {
        return List.of("ev3", "e3");
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
        FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help" -> showHelp(sender);
            case "reload" -> handleReload(sender, args);
            case "info" -> showInfo(sender);
            case "backpack" -> handleBackpack(sender, args);
            case "resetpickaxes" -> handleResetPickaxes(sender, args);
            case "resetprofile" -> handleResetProfile(sender, args);
            default -> {
                sendError(sender, "Unknown subcommand: " + args[0]);
                sendInfo(sender, "Use /evolv3 help for available commands.");
            }
        }

        return true;
    }

    /**
     * Shows help information.
     *
     * @param sender The command sender
     */
    private void showHelp(CommandSender sender)
    {
        sender.sendMessage("§6§l[Evolv3] §eAvailable Commands:");
        sender.sendMessage("§7- §e/evolv3 help §7- Show this help message");
        sender.sendMessage("§7- §e/evolv3 reload [config] §7- Reload configuration files");
        sender.sendMessage("§7- §e/evolv3 info §7- Show plugin information");
        sender.sendMessage("§7- §e/evolv3 backpack model <number> [player] §7- Set backpack model data");
        sender.sendMessage("§7- §e/evolv3 resetpickaxes <player> §7- Delete all of a player's pickaxe data");
        sender.sendMessage("§7- §e/evolv3 resetprofile <player> §7- Delete a player's profile and held pickaxe");
        sender.sendMessage("");
        sender.sendMessage("§7Player Commands:");
        sender.sendMessage("§7- §e/pickaxe §7- Open pickaxe management menu");
        sender.sendMessage("§7- §e/backpack §7- Open backpack menu");
        sender.sendMessage("§7- §e/preferences §7- Open preferences menu");
    }

    /**
     * Handles the reload subcommand.
     *
     * @param sender The command sender
     * @param args   The command arguments
     */
    private void handleReload(CommandSender sender, String[] args)
    {
        FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

        if (args.length == 1) {
            ConfigRegistry.getInstance().cleanup();
            ConfigRegistry.getInstance().initialize();

            String successMsg = messagesConfig.getString(ConfigPaths.Messages.General.RELOAD_SUCCESS,
                    "&aConfiguration reloaded successfully!");
            sender.sendMessage(formatMessage(sender, successMsg));
        } else {
            String configName = args[1];

            ConfigRegistry.getInstance().cleanup();
            ConfigRegistry.getInstance().initialize();

            String successMsg = messagesConfig.getString(ConfigPaths.Messages.General.RELOAD_SUCCESS,
                    "&aConfiguration reloaded successfully!");
            sender.sendMessage(formatMessage(sender, successMsg.replace("%config%", configName)));
        }
    }

    /**
     * Handles the backpack subcommand.
     * Usage: /evolv3 backpack model <number> [player]
     *
     * @param sender The command sender
     * @param args   The command arguments
     */
    private void handleBackpack(CommandSender sender, String[] args)
    {
        if (args.length < 3 || !args[1].equalsIgnoreCase("model")) {
            sendInfo(sender, "Usage: /evolv3 backpack model <number> [player]");
            return;
        }

        int modelData;
        try {
            modelData = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendError(sender, "Invalid number: " + args[2]);
            return;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayer(args[3]);
            if (target == null) {
                sendError(sender, "Player not found: " + args[3]);
                return;
            }
        } else {
            if (!requirePlayer(sender)) {
                return;
            }
            target = (Player) sender;
        }

        ItemStack offHand = target.getInventory().getItemInOffHand();
        NamespacedKey backpackKey = new NamespacedKey(Main.getInstance(), "backpack");

        if (offHand.getType() != Material.STICK || !offHand.hasItemMeta()
                || !offHand.getItemMeta().getPersistentDataContainer().has(backpackKey, PersistentDataType.BYTE)) {
            sendError(sender, target.getName() + " does not have a backpack item in their offhand.");
            return;
        }

        ItemMeta meta = offHand.getItemMeta();
        meta.setCustomModelData(modelData);
        offHand.setItemMeta(meta);

        sendSuccess(sender, "Set " + target.getName() + "'s backpack model data to " + modelData + ".");
    }

    /**
     * Shows plugin information.
     *
     * @param sender The command sender
     */
    private void showInfo(CommandSender sender)
    {
        sender.sendMessage("§6§l[Evolv3] §ePlugin Information");
        sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Author: §fBenski");
        sender.sendMessage("§7Platform: §fPaper 1.21.1");
        sender.sendMessage("");
        sender.sendMessage("§7Registered Systems:");
        sender.sendMessage("§7- §aPickaxe Progression");
        sender.sendMessage("§7- §aArmor Multipliers");
        sender.sendMessage("§7- §aBackpack System");
        sender.sendMessage("§7- §aEnchantment System");
    }

    /**
     * Removes all pickaxe profiles for the specified player.
     * Clears stored slots, deletes pickaxe documents from MongoDB, and removes them from cache.
     * Also clears the ID from any pickaxe the target is currently holding.
     *
     * @param sender The command sender
     * @param args   The command arguments (args[1] = target player name)
     */
    private void handleResetPickaxes(CommandSender sender, String[] args)
    {
        if (args.length < 2) {
            sendInfo(sender, "Usage: /evolv3 resetpickaxes <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendError(sender, "Player not found or not online: " + args[1]);
            return;
        }

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        MongoManager mongo = MongoManager.getInstance();

        PlayerProfile profile = playerDC.getLocal(target);
        if (profile == null) {
            sendError(sender, target.getName() + "'s profile is not loaded.");
            return;
        }

        // Collect pickaxe IDs from all stored slots
        List<String> pickaxeIds = new ArrayList<>();
        for (String entry : profile.storedPickaxes) {
            if (entry == null || entry.isEmpty()) continue;
            String[] parts = entry.split(":", 2);
            if (!parts[0].isEmpty()) {
                pickaxeIds.add(parts[0]);
            }
        }

        // Also handle any currently held registered pickaxe
        ItemStack heldItem = target.getInventory().getItemInMainHand();
        String heldId = ItemStackExtensions.getData(heldItem, "id");
        if (heldId != null && !heldId.isEmpty()) {
            pickaxeIds.add(heldId);
            ItemStackExtensions.setData(heldItem, "id", "");
            target.getInventory().setItemInMainHand(heldItem);
        }

        // Delete from MongoDB and remove from cache
        for (String id : pickaxeIds) {
            mongo.deleteAsync("Pickaxes", "id", id);
            pickaxeDC.getAll().remove(id);
        }

        // Clear stored slot entries and reset pickaxe slot limit
        for (int i = 0; i < profile.storedPickaxes.size(); i++) {
            profile.storedPickaxes.set(i, "");
        }
        profile.pickaxeLimit = 0;
        profile.save(null);

        sendSuccess(sender, "Removed " + pickaxeIds.size() + " pickaxe(s) from " + target.getName() + " and reset their slot limit.");
    }

    /**
     * Deletes the specified player's profile from MongoDB and cache.
     * If the admin is a player holding a registered pickaxe, that pickaxe profile
     * is also deleted and its ID cleared from the item.
     *
     * @param sender The command sender
     * @param args   The command arguments (args[1] = target player name)
     */
    private void handleResetProfile(CommandSender sender, String[] args)
    {
        if (args.length < 2) {
            sendInfo(sender, "Usage: /evolv3 resetprofile <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendError(sender, "Player not found or not online: " + args[1]);
            return;
        }

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        PickaxeDataCenter pickaxeDC = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        MongoManager mongo = MongoManager.getInstance();

        // Delete the target player's profile
        String playerId = target.getUniqueId().toString();
        mongo.deleteAsync("Players", "id", playerId);
        playerDC.getAll().remove(playerId);
        sendSuccess(sender, "Deleted " + target.getName() + "'s player profile.");

        // Delete the admin's held pickaxe profile if applicable
        if (sender instanceof Player admin) {
            ItemStack heldItem = admin.getInventory().getItemInMainHand();
            String pickaxeId = ItemStackExtensions.getData(heldItem, "id");
            if (pickaxeId != null && !pickaxeId.isEmpty()) {
                mongo.deleteAsync("Pickaxes", "id", pickaxeId);
                pickaxeDC.getAll().remove(pickaxeId);
                ItemStackExtensions.setData(heldItem, "id", "");
                admin.getInventory().setItemInMainHand(heldItem);
                sendSuccess(sender, "Deleted held pickaxe profile (" + pickaxeId + ").");
            } else {
                sendInfo(sender, "No registered pickaxe held — pickaxe profile not deleted.");
            }
        }
    }

    /**
     * Formats a message for the sender.
     *
     * @param sender  The command sender
     * @param message The message to format
     * @return The formatted message
     */
    private String formatMessage(CommandSender sender, String message)
    {
        if (sender instanceof Player player) {
            return Utils.formatMessage(player, message);
        }
        return message.replace("&", "§");
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args)
    {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions = Arrays.asList("help", "reload", "info", "backpack", "resetpickaxes", "resetprofile");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            suggestions = Arrays.asList("default", "messages", "enchants", "all");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("backpack")) {
            suggestions = List.of("model");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("resetpickaxes")
                || args[0].equalsIgnoreCase("resetprofile"))) {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
        } else if (args.length == 3 && args[0].equalsIgnoreCase("backpack") && args[1].equalsIgnoreCase("model")) {
            suggestions = Arrays.asList("1", "2", "3", "4", "5");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("backpack") && args[1].equalsIgnoreCase("model")) {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
        }

        String current = args[args.length - 1].toLowerCase();
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .toList();
    }
}
