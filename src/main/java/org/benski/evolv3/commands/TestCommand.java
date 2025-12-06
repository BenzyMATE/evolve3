package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Example command implementation.
 * Create similar classes for each of your commands.
 */
public class TestCommand extends BaseCommand
{
    public TestCommand(JavaPlugin plugin) {
        super(plugin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommandName() {
        return "example"; // Must match plugin.yml
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getDescription() {
        return "An example command";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getUsage() {
        return "/example <arg>";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getPermission() {
        return "evolv3.command.example";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable List<String> getAliases() {
        return List.of("ex", "exmp");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        // Perform any initialization logic here
        plugin.getLogger().info("Example command initialized!");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Example: require player
        if (!requirePlayer(sender)) {
            return true;
        }

        Player player = (Player) sender;

        // Example: check arguments
        if (!requireArgs(sender, args, 1, getUsage())) {
            return true;
        }

        String arg = args[0];

        switch (arg.toLowerCase()) {
            case "test":
                sendSuccess(player, "Test successful!");
                break;
            case "info":
                sendInfo(player, "This is an example command.");
                break;
            default:
                sendError(player, "Unknown argument: " + arg);
                break;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("test");
            completions.add("info");

            // Filter based on current input
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));

            return completions;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        // Perform any cleanup logic here
        plugin.getLogger().info("Example command cleaned up!");
    }
}