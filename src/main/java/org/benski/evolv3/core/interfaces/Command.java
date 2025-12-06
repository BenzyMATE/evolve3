package org.benski.evolv3.core.interfaces;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for all Evolve plugin commands, providing standardized command handling
 * and tab completion functionality.
 */
public interface Command
{
    /**
     * Executes the command logic.
     * This method should handle all command execution including validation,
     * permissions checking, and response to the command sender.
     *
     * @param sender the command sender (player, console, etc.)
     * @param command the command object
     * @param label the command label used
     * @param args command arguments
     * @return true if command was handled successfully, false otherwise
     */
    boolean executeCommand(CommandSender sender, Command command, String label, String[] args);

    /**
     * Provides tab completion for the command.
     * Returns a list of possible completions for the current argument.
     * Return null or empty list if no completions are available.
     *
     * @param sender the command sender
     * @param command the command object
     * @param alias the command alias used
     * @param args current command arguments
     * @return list of possible completions, or null/empty if none
     */
    default List<String> getTabCompletion(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }

    /**
     * Gets the permission required to execute this command.
     * Return null if no permission is required.
     *
     * @return the permission string, or null if no permission required
     */
    default String getRequiredPermission() {
        return null;
    }

    /**
     * Checks if the command sender has permission to execute this command.
     * Default implementation checks the permission returned by getRequiredPermission().
     *
     * @param sender the command sender to check
     * @return true if sender has permission, false otherwise
     */
    default boolean hasPermission(CommandSender sender) {
        String permission = getRequiredPermission();
        return permission == null || sender.hasPermission(permission);
    }

    /**
     * Gets a description of what this command does.
     * Used for help messages and command registration.
     *
     * @return command description
     */
    String getDescription();

    /**
     * Gets the usage string for this command.
     * Should show the proper syntax including arguments.
     *
     * @return usage string (e.g., "/evolve <config|sql> [args...]")
     */
    String getUsage();

    /**
     * Called when the command is registered with the CommandManager.
     * Use this for any initialization that needs to happen after construction.
     */
    default void initialize() {
        // Default implementation does nothing
    }

    /**
     * Called when the plugin is disabling to clean up any resources.
     */
    default void cleanup() {
        // Default implementation does nothing
    }
}