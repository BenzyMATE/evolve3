package org.benski.evolv3.commands;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Example command implementation.
 * Create similar classes for each of your commands.
 */
@ExtensionMethod(ItemStackExtensions.class)
public class TestCommand extends BaseCommand
{
    private final MenuRegistry menuRegistry = MenuRegistry.getInstance();
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
        return List.of("ex", "exp");
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
            case "menu":
                menuRegistry.get("profile").openMenu(player);
                sendInfo(player, "Opening profile menu...");
                break;
            case "test":
                sendSuccess(player, "Test successful!");
                break;
            case "cache":
                String pickaxeId = player.getInventory().getItemInMainHand().getData("id");
                if (pickaxeId != null && !pickaxeId.isEmpty()) {
                    sendInfo(player, "Pickaxe ID: " + pickaxeId);
                } else {
                    sendError(player, "No pickaxe ID found on held item!");
                }
                break;
            case "info":
                sendInfo(player, "This is an example command.");
                break;
            case "explosive":
                // Create a diamond pickaxe with Explosive III
                ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);

                // Use extension method to add the custom enchantment
                if (!pickaxe.addCustomEnchantment("explosive", 3)) {
                    sendError(player, "Explosive enchantment not found! Make sure the server started correctly.");
                    break;
                }

                // Give it to the player
                player.getInventory().addItem(pickaxe);
                sendSuccess(player, "Given you a Diamond Pickaxe with Explosive III!");
                break;
            case "enchant":
                // Check if player already has an active selection
                if (EnchantSelectionManager.getInstance().hasActiveSelection(player)) {
                    sendError(player, "You already have an enchant selection active!");
                    break;
                }

                // Check if player is holding a tool
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem.getType() == Material.AIR) {
                    sendError(player, "You must be holding an item to enchant!");
                    break;
                }

                // Start the enchant selection animation
                List<String> enchantOptions = List.of("⠌", "⠌", "⠌");
                boolean started = EnchantSelectionManager.getInstance().startSelection(
                    player,
                    enchantOptions,
                    (p, selected) -> {
                        // This callback is called when the player selects an enchant
                        p.sendMessage("§a§l[Enchanted!] §eYou selected: §b" + selected);
                        // TODO: Actually apply the enchant to the item
                    }
                );

                if (started) {
                    sendSuccess(player, "Look at an enchant and LEFT-CLICK to select!");
                } else {
                    sendError(player, "Could not start enchant selection (animation budget exceeded)!");
                }
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
            completions.add("menu");
            completions.add("cache");
            completions.add("explosive");
            completions.add("enchant");

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