package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.configuration.file.FileConfiguration;
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
        PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);

        if (args.length >= 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                String msg = messages.getString(ConfigPaths.Messages.Bal.PLAYER_NOT_FOUND,
                        "&c&lPlayer Not Found &7— &f%name% &7is not online.");
                sender.sendMessage(formatSender(sender, msg.replace("%name%", args[0])));
                return true;
            }

            PlayerProfile targetProfile = dc != null ? dc.getLocal(target) : null;
            double balance = targetProfile != null ? targetProfile.coins : 0.0;
            String msg = messages.getString(ConfigPaths.Messages.Bal.OTHER_BALANCE,
                    "&#FFD700⛁ &#FFD700&l%name% &#888888has &#FFD700⛁ &#FFD700&l%balance% &#888888Coins");
            sender.sendMessage(formatSender(sender, msg
                    .replace("%name%", target.getName())
                    .replace("%balance%", formatCoins(balance))));
        } else {
            if (!requirePlayer(sender)) return true;
            Player player = (Player) sender;

            PlayerProfile profile = dc != null ? dc.getLocal(player) : null;
            double balance = profile != null ? profile.coins : 0.0;
            String msg = messages.getString(ConfigPaths.Messages.Bal.OWN_BALANCE,
                    "&#FFD700⛁ &#FFD700&lYour Balance: &#FFD700⛁ &#FFD700&l%balance% &#888888Coins");
            player.sendMessage(formatMessage(player, msg.replace("%balance%", formatCoins(balance))));
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
        return message.replaceAll("&#[0-9A-Fa-f]{6}", "").replaceAll("&[0-9a-fk-or]", "");
    }
}
