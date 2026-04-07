package org.benski.evolv3.commands;

import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.MongoManager;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.EvolutionStone;
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
import java.util.UUID;

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
        return "evolve";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getDescription()
    {
        return "Main admin command for the Evolv3 plugin";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getUsage()
    {
        return "/evolve <help|reload|info|resetpickaxes|resetprofile|stone>";
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
        return List.of("e");
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
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
            case "stone" -> handleGiveStone(sender, args);
            case "setbackpack" -> handleSetBackpack(sender, args);
            default -> {
                sendError(sender, "Unknown subcommand: " + args[0]);
                sendInfo(sender, "Use /evolve help for available commands.");
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
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        for (String line : messages.getStringList(ConfigPaths.Messages.Admin.HELP)) {
            sender.sendMessage(formatMessage(sender, line));
        }
    }

    /**
     * Handles the reload subcommand.
     *
     * @param sender The command sender
     * @param args   The command arguments
     */
    private void handleReload(CommandSender sender, String[] args)
    {
        if (args.length == 1) {
            ConfigRegistry.getInstance().cleanup();
            ConfigRegistry.getInstance().initialize();

            // Read from the newly loaded config, not the stale reference
            FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
            String successMsg = messagesConfig.getString(ConfigPaths.Messages.General.RELOAD_SUCCESS,
                    "&aConfiguration reloaded successfully!");
            sender.sendMessage(formatMessage(sender, successMsg));
        } else {
            String configName = args[1];

            ConfigRegistry.getInstance().cleanup();
            ConfigRegistry.getInstance().initialize();

            FileConfiguration messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
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
            sendInfo(sender, "Usage: /evolve backpack model <number> [player]");
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
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        for (String line : messages.getStringList(ConfigPaths.Messages.Admin.INFO)) {
            String processed = line.replace("%version%", plugin.getDescription().getVersion());
            sender.sendMessage(formatMessage(sender, processed));
        }
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
            sendInfo(sender, "Usage: /evolve resetpickaxes <player>");
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
     * Gives an Evolution Stone to the sender or a target player.
     * Usage: /evolve stone <rarity> [enchant] [player]
     */
    private void handleGiveStone(CommandSender sender, String[] args)
    {
        if (args.length < 2) {
            sendInfo(sender, "Usage: /evolve stone <Common|Uncommon|Rare|Epic|Legendary> [enchant] [player]");
            return;
        }

        boolean giveAll = args[1].equalsIgnoreCase("All");

        String rarityArg = args[1].substring(0, 1).toUpperCase() + args[1].substring(1).toLowerCase();
        RarityConfig rarity = giveAll ? null : EnchantAnimationConfig.getRarityByName(rarityArg);
        if (!giveAll && rarity == null) {
            sendError(sender, "Unknown rarity: " + args[1] + " — use Common, Uncommon, Rare, Epic, Legendary, or All.");
            return;
        }

        List<String> allEnchants = EnchantAnimationConfig.getEnchantOrder();

        // Optional enchant arg (args[2]) and optional player arg (last arg if it matches an online player)
        String enchantName = null;
        Player target = null;

        if (args.length >= 3) {
            // Check if last arg is a player name
            Player possiblePlayer = Bukkit.getPlayer(args[args.length - 1]);
            int enchantEndIndex = (possiblePlayer != null && args.length >= 4) ? args.length - 1 : args.length;

            if (possiblePlayer != null && enchantEndIndex == 2) {
                // /evolve stone <rarity> <player> — no enchant specified
                target = possiblePlayer;
            } else {
                String enchantArg = String.join(" ", java.util.Arrays.copyOfRange(args, 2, enchantEndIndex));
                final String search = enchantArg.toLowerCase();
                enchantName = allEnchants.stream()
                        .filter(e -> e.toLowerCase().equals(search))
                        .findFirst().orElse(null);
                if (enchantName == null) {
                    sendError(sender, "Unknown enchant: " + enchantArg);
                    return;
                }
                if (possiblePlayer != null) target = possiblePlayer;
            }
        }

        if (enchantName == null) {
            enchantName = allEnchants.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(allEnchants.size()));
        }

        if (target == null) {
            if (!requirePlayer(sender)) return;
            target = (Player) sender;
        }

        final Player finalTarget = target;
        final String finalEnchant = enchantName;

        if (giveAll) {
            List<String> rarityNames = List.of("Common", "Uncommon", "Rare", "Epic", "Legendary");
            for (String name : rarityNames) {
                RarityConfig rc = EnchantAnimationConfig.getRarityByName(name);
                if (rc == null) continue;
                var overflow = finalTarget.getInventory().addItem(EvolutionStone.create(rc, finalEnchant));
                overflow.values().forEach(item -> finalTarget.getWorld().dropItemNaturally(finalTarget.getLocation(), item));
            }
            sendSuccess(sender, "Given " + finalTarget.getName() + " all 5 Evolution Stones (" + finalEnchant + ").");
        } else {
            var overflow = finalTarget.getInventory().addItem(EvolutionStone.create(rarity, finalEnchant));
            overflow.values().forEach(item -> finalTarget.getWorld().dropItemNaturally(finalTarget.getLocation(), item));
            sendSuccess(sender, "Given " + finalTarget.getName() + " a " + rarity.displayName() + " Evolution Stone (" + finalEnchant + ").");
        }
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
            sendInfo(sender, "Usage: /evolve resetprofile <player>");
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

        // Collect and delete all pickaxes owned by this player
        UUID targetUuid = target.getUniqueId();
        List<String> pickaxeIds = new ArrayList<>();

        // Pickaxes from stored slots (if profile is still in cache)
        PlayerProfile profile = playerDC.getLocal(target);
        if (profile != null) {
            for (String entry : profile.storedPickaxes) {
                if (entry == null || entry.isEmpty()) continue;
                String[] parts = entry.split(":", 2);
                if (!parts[0].isEmpty()) pickaxeIds.add(parts[0]);
            }
        }

        // Any pickaxe cached in PickaxeDataCenter that belongs to this player
        pickaxeDC.getAll().values().stream()
                .filter(p -> targetUuid.equals(p.ownerUuid))
                .map(p -> p.key)
                .filter(k -> k != null && !k.isEmpty())
                .forEach(k -> { if (!pickaxeIds.contains(k)) pickaxeIds.add(k); });

        // Clear ID from any pickaxe the player is currently holding
        for (int i = 0; i < target.getInventory().getContents().length; i++) {
            ItemStack item = target.getInventory().getContents()[i];
            if (item == null || !item.getType().name().contains("PICKAXE")) continue;
            String id = ItemStackExtensions.getData(item, "id");
            if (id != null && !id.isEmpty()) {
                ItemStackExtensions.setData(item, "id", "");
                target.getInventory().setItem(i, item);
            }
        }

        for (String id : pickaxeIds) {
            mongo.deleteAsync("Pickaxes", "id", id);
            pickaxeDC.getAll().remove(id);
        }

        // Delete the target player's profile
        String playerId = targetUuid.toString();
        mongo.deleteAsync("Players", "id", playerId);
        playerDC.getAll().remove(playerId);

        sendSuccess(sender, "Reset " + target.getName() + "'s profile and deleted "
                + pickaxeIds.size() + " pickaxe(s).");
    }

    /**
     * Sets the backpack size limit for the specified player.
     * Usage: /evolve setbackpack <player> <size>
     */
    private void handleSetBackpack(CommandSender sender, String[] args)
    {
        if (args.length < 3) {
            sendInfo(sender, "Usage: /evolve setbackpack <player> <size>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendError(sender, "Player not found or not online: " + args[1]);
            return;
        }

        int size;
        try {
            size = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendError(sender, "Invalid size: " + args[2] + " — must be a whole number.");
            return;
        }

        if (size < 1) {
            sendError(sender, "Size must be at least 1.");
            return;
        }

        PlayerDataCenter playerDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (playerDC == null) {
            sendError(sender, "Player data centre unavailable.");
            return;
        }

        PlayerProfile profile = playerDC.getLocal(target);
        if (profile == null) {
            sendError(sender, "Could not load profile for " + target.getName() + ".");
            return;
        }

        profile.backpack.setMaxSize(size);
        profile.save(null);

        sendSuccess(sender, "Set " + target.getName() + "'s backpack limit to " + size + " items.");
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
            suggestions = Arrays.asList("help", "reload", "info", "backpack", "resetpickaxes", "resetprofile", "stone", "setbackpack");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stone")) {
            suggestions = Arrays.asList("Common", "Uncommon", "Rare", "Epic", "Legendary", "All");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("stone")) {
            // Only show enchants that have stone handlers implemented
            java.util.Set<String> implemented = java.util.Set.of(
                    "efficiency", "unbreaking", "alchemy", "autosell", "cascade", "drill", "explosive", "extraction", "fortune", "frostbite", "furnace", "merchant", "jackhammer", "lazer", "nuclearshower", "radioactive", "scavenger", "thor", "vortex"
            );
            suggestions = EnchantAnimationConfig.getEnchantOrder().stream()
                    .filter(display -> implemented.stream()
                            .anyMatch(internal -> display.equalsIgnoreCase(
                                    EnchantAnimationConfig.getDisplayName(internal))))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("stone")) {
            suggestions = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            suggestions = Arrays.asList("default", "messages", "enchants", "all");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("backpack")) {
            suggestions = List.of("model");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("resetpickaxes")
                || args[0].equalsIgnoreCase("resetprofile")
                || args[0].equalsIgnoreCase("setbackpack"))) {
            suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setbackpack")) {
            suggestions = Arrays.asList("100", "200", "500", "1000");
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
