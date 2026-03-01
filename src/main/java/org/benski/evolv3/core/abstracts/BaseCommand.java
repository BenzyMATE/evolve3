package org.benski.evolv3.core.abstracts;

import org.benski.evolv3.core.helpers.Messageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for all commands in the plugin.
 * Provides common functionality and structure for command implementations.
 */
public abstract class BaseCommand
{
    protected final JavaPlugin plugin;

    public BaseCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the command name (as defined in plugin.yml).
     *
     * @return the command name
     */
    public abstract String getCommandName();

    /**
     * Gets the command description.
     *
     * @return the description, or null for no description
     */
    public @Nullable String getDescription() {
        return null;
    }

    /**
     * Gets the command usage string.
     *
     * @return the usage string, or null for default
     */
    public @Nullable String getUsage() {
        return null;
    }

    /**
     * Gets the permission required to use this command.
     *
     * @return the permission node, or null for no permission check
     */
    public @Nullable String getPermission() {
        return null;
    }

    /**
     * Gets the command aliases.
     *
     * @return list of aliases, or null for no aliases
     */
    public @Nullable List<String> getAliases() {
        return null;
    }

    /**
     * Checks if the sender has permission to use this command.
     *
     * @param sender the command sender
     * @return true if the sender has permission
     */
    public boolean hasPermission(CommandSender sender) {
        String permission = getPermission();
        return permission == null || sender.hasPermission(permission);
    }

    /**
     * Called when the command is first registered.
     * Override to perform initialization logic.
     */
    public void initialize() {
    }

    /**
     * Executes the command.
     *
     * @param sender the command sender
     * @param command the command object
     * @param label the alias used
     * @param args the command arguments
     * @return true if command executed successfully
     */
    public abstract boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args);

    /**
     * Provides tab completion for the command.
     *
     * @param sender the command sender
     * @param command the command object
     * @param alias the alias used
     * @param args the current arguments
     * @return list of tab completions, or null for default behavior
     */
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return null;
    }

    /**
     * Called when the plugin is disabling.
     * Override to perform cleanup logic.
     */
    public void cleanup() {
    }

    /**
     * Checks if the sender is a player and sends an error message if not.
     *
     * @param sender the command sender
     * @return true if sender is a player
     */
    protected boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return false;
        }
        return true;
    }

    /**
     * Checks if the correct number of arguments were provided.
     *
     * @param sender the command sender
     * @param args the arguments
     * @param required the required number of arguments
     * @param usage the usage message to display if incorrect
     * @return true if argument count is correct
     */
    protected boolean requireArgs(CommandSender sender, String[] args, int required, String usage) {
        if (args.length < required) {
            sender.sendMessage("§cUsage: " + usage);
            return false;
        }
        return true;
    }

    /**
     * Sends a success message to the sender.
     *
     * @param sender the command sender
     * @param message the message to send
     */
    protected void sendSuccess(CommandSender sender, String message) {
        Messageable.sendSuccess(sender, message);
    }

    /**
     * Sends an error message to the sender.
     *
     * @param sender the command sender
     * @param message the message to send
     */
    protected void sendError(CommandSender sender, String message) {
        Messageable.sendError(sender, message);
    }

    /**
     * Sends an info message to the sender.
     *
     * @param sender the command sender
     * @param message the message to send
     */
    protected void sendInfo(CommandSender sender, String message) {
        Messageable.sendInfo(sender, message);
    }
}
