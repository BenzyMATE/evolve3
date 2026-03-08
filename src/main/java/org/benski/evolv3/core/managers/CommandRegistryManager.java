package org.benski.evolv3.core.managers;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core manager that handles command registration and routing.
 * Implements CommandExecutor and TabCompleter to handle all commands centrally.
 */
public class CommandRegistryManager implements CommandExecutor, TabCompleter
{
    private final JavaPlugin plugin;
    private final Map<String, BaseCommand> commandMap = new HashMap<>();
    private final List<Class<? extends BaseCommand>> commandClasses = new ArrayList<>();

    public CommandRegistryManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a command class to be initialized later.
     *
     * @param commandClass the command class to register
     */
    public void registerCommand(Class<? extends BaseCommand> commandClass) {
        commandClasses.add(commandClass);
    }

    /**
     * Initializes all registered commands.
     * Creates instances and registers them with Bukkit.
     * Supports both Bukkit/Spigot (plugin.yml) and Paper (direct registration).
     */
    public void initializeCommands() {
        List<String> names = new ArrayList<>();

        for (Class<? extends BaseCommand> commandClass : commandClasses) {
            try {
                Constructor<? extends BaseCommand> constructor = commandClass.getConstructor(JavaPlugin.class);
                BaseCommand command = constructor.newInstance(plugin);

                String commandName = command.getCommandName();
                commandMap.put(commandName.toLowerCase(), command);

                org.bukkit.command.PluginCommand pluginCommand = null;
                try {
                    pluginCommand = plugin.getCommand(commandName);
                } catch (UnsupportedOperationException ignored) {
                    // Paper plugin — use direct registration
                }

                if (pluginCommand != null) {
                    pluginCommand.setExecutor(this);
                    pluginCommand.setTabCompleter(this);
                    if (command.getDescription() != null) pluginCommand.setDescription(command.getDescription());
                    if (command.getUsage() != null)       pluginCommand.setUsage(command.getUsage());
                    if (command.getPermission() != null)  pluginCommand.setPermission(command.getPermission());
                    if (command.getAliases() != null)     pluginCommand.setAliases(command.getAliases());
                } else {
                    registerPaperCommand(commandName, command);
                }

                command.initialize();
                names.add(commandName);

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register command " + commandClass.getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (!names.isEmpty()) {
            plugin.getLogger().info("Commands: " + String.join(", ", names));
        }
    }

    /**
     * Registers a command directly using Paper's API.
     * This method uses reflection to support both Paper and non-Paper environments.
     */
    private void registerPaperCommand(String commandName, BaseCommand command) {
        try {
            // Create a PluginCommand using Paper's constructor
            // We need to use reflection to avoid compile-time dependency on Paper
            Class<?> pluginCommandClass = Class.forName("org.bukkit.command.PluginCommand");
            Constructor<?> constructor = pluginCommandClass.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            constructor.setAccessible(true);

            org.bukkit.command.PluginCommand pluginCommand =
                    (org.bukkit.command.PluginCommand) constructor.newInstance(commandName, plugin);

            // Set executor and tab completer
            pluginCommand.setExecutor(this);
            pluginCommand.setTabCompleter(this);

            // Set metadata from command
            if (command.getDescription() != null) {
                pluginCommand.setDescription(command.getDescription());
            }
            if (command.getUsage() != null) {
                pluginCommand.setUsage(command.getUsage());
            }
            if (command.getPermission() != null) {
                pluginCommand.setPermission(command.getPermission());
            }
            if (command.getAliases() != null) {
                pluginCommand.setAliases(command.getAliases());
            }

            // Register the command with the server's command map
            org.bukkit.command.CommandMap commandMap = plugin.getServer().getCommandMap();
            commandMap.register(plugin.getName().toLowerCase(), pluginCommand);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register Paper command " + commandName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        BaseCommand baseCommand = commandMap.get(command.getName().toLowerCase());

        if (baseCommand == null) {
            return false;
        }

        // Check permissions
        if (!baseCommand.hasPermission(sender)) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        // Execute the command
        try {
            return baseCommand.execute(sender, command, label, args);
        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred while executing the command!");
            plugin.getLogger().severe("Error executing command " + command.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        BaseCommand baseCommand = commandMap.get(command.getName().toLowerCase());

        if (baseCommand == null) {
            return null;
        }

        // Check permissions for tab completion
        if (!baseCommand.hasPermission(sender)) {
            return null;
        }

        // Get tab completion from the command
        try {
            return baseCommand.getTabCompletion(sender, command, alias, args);
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting tab completion for command " + command.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets a command instance by name.
     *
     * @param commandName the command name
     * @return the command instance, or null if not found
     */
    public BaseCommand getCommand(String commandName) {
        return commandMap.get(commandName.toLowerCase());
    }

    /**
     * Checks if a command is registered.
     *
     * @param commandName the command name
     * @return true if registered
     */
    public boolean isCommandRegistered(String commandName) {
        return commandMap.containsKey(commandName.toLowerCase());
    }

    /**
     * Cleanup all registered commands.
     */
    public void cleanup() {
        for (BaseCommand command : commandMap.values()) {
            try {
                command.cleanup();
            } catch (Exception e) {
                plugin.getLogger().warning("Error cleaning up command: " + e.getMessage());
            }
        }
        commandMap.clear();
        commandClasses.clear();
    }
}