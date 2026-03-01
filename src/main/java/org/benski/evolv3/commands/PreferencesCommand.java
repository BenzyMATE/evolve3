package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command for managing player preferences.
 * Usage: /preferences [setting] [value]
 */
public class PreferencesCommand extends BaseCommand
{
    private final PlayerDataCenter playerDataCenter;

    public PreferencesCommand(JavaPlugin plugin)
    {
        super(plugin);
        this.playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    }

    /** {@inheritDoc} */
    @Override
    public String getCommandName()
    {
        return "preferences";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getDescription()
    {
        return "Manage your personal preferences";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getUsage()
    {
        return "/preferences [animation|message|enchantstyle] [value]";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getAliases()
    {
        return List.of("prefs", "settings");
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
        if (!requirePlayer(sender)) {
            return true;
        }

        Player player = (Player) sender;
        PlayerProfile profile = playerDataCenter.getLocal(player);

        if (profile == null) {
            sendError(sender, "Your profile is not loaded. Please wait and try again.");
            return true;
        }

        PlayerPreferences prefs = profile.preferences;

        if (args.length == 0) {
            showCurrentPreferences(player, prefs);
            return true;
        }

        String setting = args[0].toLowerCase();

        if (args.length == 1) {
            showPreferenceSetting(player, prefs, setting);
        } else {
            String value = args[1].toUpperCase();
            setPreferenceSetting(player, profile, prefs, setting, value);
        }

        return true;
    }

    /**
     * Shows all current preference settings.
     *
     * @param player The player
     * @param prefs  The player's preferences
     */
    private void showCurrentPreferences(Player player, PlayerPreferences prefs)
    {
        player.sendMessage("§6§l[Preferences] §eYour Settings:");
        player.sendMessage("");
        player.sendMessage("§7Animation: §f" + prefs.animation.getDisplayName());
        player.sendMessage("  §8" + prefs.animation.getDescription());
        player.sendMessage("");
        player.sendMessage("§7Messages: §f" + prefs.message.getDisplayName());
        player.sendMessage("  §8" + prefs.message.getDescription());
        player.sendMessage("");
        player.sendMessage("§7Enchant Style: §f" + prefs.enchantStyle.getDisplayName());
        player.sendMessage("  §8" + prefs.enchantStyle.getDescription());
        player.sendMessage("");
        player.sendMessage("§7Use §e/preferences <setting> <value> §7to change.");
    }

    /**
     * Shows the current value and options for a specific setting.
     *
     * @param player  The player
     * @param prefs   The player's preferences
     * @param setting The setting name
     */
    private void showPreferenceSetting(Player player, PlayerPreferences prefs, String setting)
    {
        switch (setting) {
            case "animation", "animations" -> {
                player.sendMessage("§6§l[Animation] §eCurrent: §f" + prefs.animation.getDisplayName());
                player.sendMessage("§7Options:");
                for (PreferenceType.Animation opt : PreferenceType.Animation.values()) {
                    String marker = opt == prefs.animation ? "§a► " : "§7  ";
                    player.sendMessage(marker + "§e" + opt.name() + " §7- " + opt.getDescription());
                }
            }
            case "message", "messages" -> {
                player.sendMessage("§6§l[Messages] §eCurrent: §f" + prefs.message.getDisplayName());
                player.sendMessage("§7Options:");
                for (PreferenceType.Message opt : PreferenceType.Message.values()) {
                    String marker = opt == prefs.message ? "§a► " : "§7  ";
                    player.sendMessage(marker + "§e" + opt.name() + " §7- " + opt.getDescription());
                }
            }
            case "enchantstyle", "enchant" -> {
                player.sendMessage("§6§l[Enchant Style] §eCurrent: §f" + prefs.enchantStyle.getDisplayName());
                player.sendMessage("§7Options:");
                for (PreferenceType.EnchantStyle opt : PreferenceType.EnchantStyle.values()) {
                    String marker = opt == prefs.enchantStyle ? "§a► " : "§7  ";
                    player.sendMessage(marker + "§e" + opt.name() + " §7- " + opt.getDescription());
                }
            }
            default -> sendError(player, "Unknown setting: " + setting + ". Use: animation, message, enchantstyle");
        }
    }

    /**
     * Sets a preference value.
     *
     * @param player  The player
     * @param profile The player's profile
     * @param prefs   The player's preferences
     * @param setting The setting name
     * @param value   The new value
     */
    private void setPreferenceSetting(Player player, PlayerProfile profile, PlayerPreferences prefs, String setting, String value)
    {
        try {
            switch (setting) {
                case "animation", "animations" -> {
                    PreferenceType.Animation newValue = PreferenceType.Animation.valueOf(value);
                    prefs.animation = newValue;
                    sendSuccess(player, "Animation set to: " + newValue.getDisplayName());
                }
                case "message", "messages" -> {
                    PreferenceType.Message newValue = PreferenceType.Message.valueOf(value);
                    prefs.message = newValue;
                    sendSuccess(player, "Messages set to: " + newValue.getDisplayName());
                }
                case "enchantstyle", "enchant" -> {
                    PreferenceType.EnchantStyle newValue = PreferenceType.EnchantStyle.valueOf(value);
                    prefs.enchantStyle = newValue;
                    sendSuccess(player, "Enchant style set to: " + newValue.getDisplayName());
                }
                default -> {
                    sendError(player, "Unknown setting: " + setting);
                    return;
                }
            }

            profile.save(null);

        } catch (IllegalArgumentException e) {
            sendError(player, "Invalid value: " + value);
            sendInfo(player, "Use /preferences " + setting + " to see available options.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args)
    {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions = Arrays.asList("animation", "message", "enchantstyle");
        } else if (args.length == 2) {
            String setting = args[0].toLowerCase();
            switch (setting) {
                case "animation", "animations" -> {
                    for (PreferenceType.Animation opt : PreferenceType.Animation.values()) {
                        suggestions.add(opt.name());
                    }
                }
                case "message", "messages" -> {
                    for (PreferenceType.Message opt : PreferenceType.Message.values()) {
                        suggestions.add(opt.name());
                    }
                }
                case "enchantstyle", "enchant" -> {
                    for (PreferenceType.EnchantStyle opt : PreferenceType.EnchantStyle.values()) {
                        suggestions.add(opt.name());
                    }
                }
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .toList();
    }
}
