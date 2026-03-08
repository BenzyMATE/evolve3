package org.benski.evolv3.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.helpers.SkinRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /skin [player] — renders the target's 8×8 skin face in chat.
 */
public class SkinCommand extends BaseCommand
{
    private static final TextColor ACCENT = TextColor.color(0x00FFFF);
    private static final TextColor MUTED  = TextColor.color(0x888888);

    public SkinCommand(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCommandName() {
        return "skin";
    }

    @Override
    public @Nullable String getDescription() {
        return "Renders a player's skin face in chat.";
    }

    @Override
    public @Nullable String getUsage() {
        return "/skin [player]";
    }

    @Override
    public @Nullable String getPermission() {
        return "evolv3.command.skin";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args)
    {
        if (!requirePlayer(sender)) return true;
        Player viewer = (Player) sender;

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                viewer.sendMessage(Component.text("Player not found: ")
                        .color(NamedTextColor.RED)
                        .append(Component.text(args[0]).color(NamedTextColor.WHITE)));
                return true;
            }
        } else {
            target = viewer;
        }

        final Player finalTarget = target;

        SkinRenderer.renderAsync(target, plugin, rows -> {
            // Header
            viewer.sendMessage(Component.empty());
            viewer.sendMessage(Component.text(finalTarget.getName())
                    .color(ACCENT)
                    .decoration(TextDecoration.BOLD, true)
                    .append(Component.text("'s Skin").color(MUTED).decoration(TextDecoration.BOLD, false)));

            // Send each row as its own message — 8 rows × 8 chars = 8×8 grid
            for (Component row : rows) {
                viewer.sendMessage(row);
            }

            viewer.sendMessage(Component.empty());
        });

        return true;
    }

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .toList();
        }
        return null;
    }
}
