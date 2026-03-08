package org.benski.evolv3.commands;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.registry.MenuRegistry;
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
        return "example";
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
        plugin.getLogger().info("Example command initialized!");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }

        Player player = (Player) sender;

        if (!requireArgs(sender, args, 1, getUsage())) {
            return true;
        }

        String arg = args[0];

        switch (arg.toLowerCase()) {
            case "menu":
                menuRegistry.get("example_config").openMenu(player);
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
                ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);

                if (!pickaxe.addCustomEnchantment("explosive", 3)) {
                    sendError(player, "Explosive enchantment not found! Make sure the server started correctly.");
                    break;
                }

                player.getInventory().addItem(pickaxe);
                sendSuccess(player, "Given you a Diamond Pickaxe with Explosive III!");
                break;
            case "enchant":
                if (EnchantSelectionManager.getInstance().hasActiveSelection(player)) {
                    sendError(player, "You already have an enchant selection active!");
                    break;
                }
                boolean started = EnchantSelectionManager.getInstance().startSelection(
                    player,
                    (p, option) -> p.sendMessage("\u00A7a\u00A7l[Enchanted!] \u00A7eYou selected: \u00A7b"
                        + option.name() + " \u00A77(" + option.rarity().displayName() + ")")
                );
                if (!started) {
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
        plugin.getLogger().info("Example command cleaned up!");
    }
}
