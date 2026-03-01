package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.hooks.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 * Overrides /bal to display a player's coin balance using the ⛁ symbol.
 * Reads the balance from Vault — no separate currency system required.
 * Usage: /bal [player]
 */
public class BalCommand extends BaseCommand
{
    public BalCommand(JavaPlugin plugin)
    {
        super(plugin);
    }

    /** {@inheritDoc} */
    @Override
    public String getCommandName()
    {
        return "bal";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getDescription()
    {
        return "View your coin balance";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getUsage()
    {
        return "/bal [player]";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getAliases()
    {
        return List.of("balance", "coins", "money");
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
        VaultHook vault = VaultHook.getInstance();

        if (args.length >= 1) {
            // Viewing another player's balance
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(formatSender(sender, "&c&lPlayer Not Found &7— &f" + args[0] + " &7is not online."));
                return true;
            }

            double balance = vault.getBalance(target);
            sender.sendMessage(formatSender(sender, "&#00AFA3⛁ &#FFFFFF&l" + target.getName()
                    + " &#888888has &#00AFA3⛁ &#FFFFFF&l" + formatCoins(balance) + " &#888888Coins"));
        } else {
            // Viewing own balance
            if (!requirePlayer(sender)) return true;
            Player player = (Player) sender;

            double balance = vault.getBalance(player);
            player.sendMessage(formatMessage(player, "&#00AFA3⛁ &#FFFFFF&lYour Balance: &#00AFA3⛁ &#FFFFFF&l"
                    + formatCoins(balance) + " &#888888Coins"));
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(current))
                    .toList();
        }
        return List.of();
    }

    /**
     * Formats a coin amount with comma separators.
     * Shows no decimals if the amount is a whole number, otherwise 2 decimal places.
     */
    private String formatCoins(double amount)
    {
        if (amount == Math.floor(amount)) {
            return NumberFormat.getIntegerInstance(Locale.US).format((long) amount);
        }
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        return fmt.format(amount);
    }

    /**
     * Formats a message for any CommandSender — processes color codes for players,
     * strips formatting codes for console.
     */
    private String formatSender(CommandSender sender, String message)
    {
        if (sender instanceof Player player) {
            return formatMessage(player, message);
        }
        return message.replaceAll("&#[0-9A-Fa-f]{6}", "").replace("&[0-9a-fk-or]", "");
    }
}
