package org.benski.evolv3.commands;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.core.managers.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enums.EvolutionTier;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
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

        String arg = args[0].toLowerCase();

        switch (arg) {
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
            case "evolve": {
                DataCenterRegistry dcr = DataCenterRegistry.getInstance();
                PickaxeDataCenter pickaxeDC = dcr.getPickaxeDataCenter();
                PlayerDataCenter playerDC   = dcr.getPlayerDataCenter();

                pickaxeDC.get(player, profile -> {
                    if (profile == null) {
                        sendError(player, "No pickaxe found in hand — hold your pickaxe first.");
                        return;
                    }
                    PlayerProfile playerProfile = playerDC.getLocal(player);
                    if (playerProfile == null) {
                        sendError(player, "Player profile not loaded.");
                        return;
                    }

                    EvolutionTier from = EvolutionTier.fromLevel(profile.evolution);
                    EvolutionTier to   = from.next();
                    if (to == null) {
                        sendError(player, "Pickaxe is already at max tier (" + from.displayName + ").");
                        return;
                    }

                    RarityConfig stone = EnchantAnimationConfig.rollRarity();
                    if (stone == null) {
                        sendError(player, "No rarities loaded — check enchants.yml.");
                        return;
                    }

                    EvolutionTier finalTo = to;
                    EvolutionSelectionManager.getInstance().startEvolution(
                            player, profile.key, from, finalTo, stone,
                            () -> PickaxeManager.getInstance().applyEvolutionResult(
                                    player, profile, playerProfile, finalTo, stone));
                });
                break;
            }
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
            List<String> completions = new ArrayList<>(List.of("test", "info", "menu", "cache", "explosive", "enchant", "evolve"));
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(input));
            return completions;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
    }
}
