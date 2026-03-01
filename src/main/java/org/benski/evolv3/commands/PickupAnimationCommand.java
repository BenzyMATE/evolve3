package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to switch between different pickup animations.
 * Allows players to test and select their preferred animation style.
 *
 * Usage:
 * - /pickupanimation - Shows current animation and available options
 * - /pickupanimation list - Lists all available animations
 * - /pickupanimation set <animation> - Sets the animation
 * - /pickupanimation next - Cycles to the next animation
 */
public class PickupAnimationCommand extends BaseCommand
{
    public PickupAnimationCommand(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCommandName() {
        return "pickupanimation";
    }

    @Override
    public @Nullable String getDescription() {
        return "Switch between pickup animations";
    }

    @Override
    public @Nullable String getUsage() {
        return "/pickupanimation [list|set <animation>|next]";
    }

    @Override
    public @Nullable String getPermission() {
        return "evolv3.command.pickupanimation";
    }

    @Override
    public @Nullable List<String> getAliases() {
        return List.of("pa", "pickup", "panim");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!requirePlayer(sender)) {
            return true;
        }

        Player player = (Player) sender;
        PickupAnimationManager manager = PickupAnimationManager.getInstance();
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

        // No arguments - show current animation
        if (args.length == 0) {
            showCurrentAnimation(player, manager, messages);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> showAnimationList(player, manager, messages);
            case "set" -> {
                if (args.length < 2) {
                    String usage = messages.getString("pickup-animation.usage-set",
                        "&cUsage: /pickupanimation set <animation>");
                    player.sendMessage(colorize(usage));
                    return true;
                }
                setAnimation(player, manager, messages, args[1]);
            }
            case "next", "cycle" -> cycleAnimation(player, manager, messages);
            case "others", "toggle" -> toggleOthersAnimations(player, manager, messages);
            default -> {
                // Try to interpret as animation name directly
                if (manager.isValidAnimation(subCommand)) {
                    setAnimation(player, manager, messages, subCommand);
                } else {
                    String unknown = messages.getString("pickup-animation.unknown-subcommand",
                        "&cUnknown option. Use: list, set <animation>, next, or others");
                    player.sendMessage(colorize(unknown));
                }
            }
        }

        return true;
    }

    private void showCurrentAnimation(Player player, PickupAnimationManager manager, FileConfiguration messages) {
        String currentId = manager.getPlayerAnimation(player);
        String[] info = manager.getAnimationInfo(currentId);

        String header = messages.getString("pickup-animation.current-header",
            "&6&l[Pickup Animation]");
        String current = messages.getString("pickup-animation.current",
            "&7Current: &a%name% &8- &7%description%");

        current = current.replace("%name%", info != null ? info[0] : currentId)
                         .replace("%description%", info != null ? info[1] : "Unknown");

        player.sendMessage(colorize(header));
        player.sendMessage(colorize(current));

        String hint = messages.getString("pickup-animation.hint",
            "&7Use &e/pa list &7to see all options, or &e/pa next &7to cycle.");
        player.sendMessage(colorize(hint));
    }

    private void showAnimationList(Player player, PickupAnimationManager manager, FileConfiguration messages) {
        String header = messages.getString("pickup-animation.list-header",
            "&6&l[Available Pickup Animations]");
        player.sendMessage(colorize(header));

        String currentId = manager.getPlayerAnimation(player);

        for (String id : manager.getAnimationIds()) {
            String[] info = manager.getAnimationInfo(id);
            boolean isCurrent = id.equalsIgnoreCase(currentId);

            String format;
            if (isCurrent) {
                format = messages.getString("pickup-animation.list-item-selected",
                    " &a> &b%name% &8- &7%description% &a(selected)");
            } else {
                format = messages.getString("pickup-animation.list-item",
                    " &8> &e%name% &8- &7%description%");
            }

            format = format.replace("%name%", info != null ? info[0] : id)
                          .replace("%id%", id)
                          .replace("%description%", info != null ? info[1] : "Unknown");

            player.sendMessage(colorize(format));
        }

        String footer = messages.getString("pickup-animation.list-footer",
            "&7Use &e/pa set <name> &7to select an animation.");
        player.sendMessage(colorize(footer));
    }

    private void setAnimation(Player player, PickupAnimationManager manager, FileConfiguration messages, String animationId) {
        if (manager.setPlayerAnimation(player, animationId)) {
            String[] info = manager.getAnimationInfo(animationId.toLowerCase());
            String success = messages.getString("pickup-animation.set-success",
                "&aPickup animation set to: &b%name%");
            success = success.replace("%name%", info != null ? info[0] : animationId)
                            .replace("%id%", animationId);
            player.sendMessage(colorize(success));
        } else {
            String error = messages.getString("pickup-animation.invalid-animation",
                "&cInvalid animation: %name%. Use /pa list to see available options.");
            error = error.replace("%name%", animationId);
            player.sendMessage(colorize(error));
        }
    }

    private void cycleAnimation(Player player, PickupAnimationManager manager, FileConfiguration messages) {
        String nextId = manager.cycleNextAnimation(player);
        String[] info = manager.getAnimationInfo(nextId);

        String cycled = messages.getString("pickup-animation.cycled",
            "&aSwitched to: &b%name% &8- &7%description%");
        cycled = cycled.replace("%name%", info != null ? info[0] : nextId)
                      .replace("%id%", nextId)
                      .replace("%description%", info != null ? info[1] : "Unknown");

        player.sendMessage(colorize(cycled));
    }

    private void toggleOthersAnimations(Player player, PickupAnimationManager manager, FileConfiguration messages) {
        boolean newState = manager.toggleShowOthersAnimations(player);

        String msgKey = newState ? "pickup-animation.others-enabled" : "pickup-animation.others-disabled";
        String msg = messages.getString(msgKey,
            newState ? "&aYou can now see other players' pickup animations."
                     : "&cYou will no longer see other players' pickup animations.");

        player.sendMessage(colorize(msg));
    }

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands and animation names
            List<String> options = new ArrayList<>(List.of("list", "set", "next", "others"));
            options.addAll(PickupAnimationManager.getInstance().getAnimationIds());

            String prefix = args[0].toLowerCase();
            completions.addAll(options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            // Second argument after "set" - animation names
            String prefix = args[1].toLowerCase();
            completions.addAll(PickupAnimationManager.getInstance().getAnimationIds().stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList()));
        }

        return completions;
    }

    /**
     * Converts color codes in a string.
     *
     * @param message the message with & color codes
     * @return the colorized message
     */
    private String colorize(String message) {
        if (message == null) return "";
        return message.replace("&", "\u00A7");
    }
}
