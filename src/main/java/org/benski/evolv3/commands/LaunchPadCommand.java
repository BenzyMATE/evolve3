package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.LaunchPadManager;
import org.benski.evolv3.core.managers.LaunchPadManager.LaunchPad;
import org.benski.evolv3.listeners.LaunchPadListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin command for managing launch pads.
 *
 * <ul>
 *   <li>{@code /e launchpad set <forward> <upward>} — enter set mode, right-click plates to register</li>
 *   <li>{@code /e launchpad remove}                 — enter remove mode, right-click pads to delete</li>
 *   <li>{@code /e launchpad cancel}                 — exit selection mode</li>
 *   <li>{@code /e launchpad list}                   — list all configured pads</li>
 *   <li>{@code /e launchpad reload}                 — reload pads from disk</li>
 * </ul>
 */
public class LaunchPadCommand extends BaseCommand
{
    public LaunchPadCommand(JavaPlugin plugin) { super(plugin); }

    @Override public String getCommandName()    { return "launchpad"; }
    @Override public @Nullable String getDescription() { return "Manage launch pads"; }
    @Override public @Nullable String getUsage()       { return "/e launchpad <set|remove|cancel|list|reload>"; }
    @Override public @Nullable String getPermission()  { return "evolv3.admin"; }
    @Override public @Nullable List<String> getAliases() { return List.of("lp", "lpad"); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args)
    {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set"    -> handleSet(player, args);
            case "remove" -> handleRemove(player);
            case "cancel" -> handleCancel(player);
            case "list"   -> handleList(player);
            case "reload" -> handleReload(player);
            default       -> sendUsage(player);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    private void handleSet(Player player, String[] args)
    {
        if (args.length < 3) {
            sendError(player, "Usage: &#FFFFFF/e launchpad set <forward-power> <upward-power>");
            Messageable.sendRaw(player, "  &#888888Example: &#FFFFFF/e launchpad set 2.0 1.2");
            return;
        }

        double forward;
        double upward;
        try {
            forward = Double.parseDouble(args[1]);
            upward = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sendError(player, "Forward and upward power must be numbers.");
            return;
        }

        if (forward < 0 || forward > 10) {
            sendError(player, "Forward power must be between &#FFFFFF0 &cand &#FFFFFF10&c.");
            return;
        }
        if (upward < 0 || upward > 10) {
            sendError(player, "Upward power must be between &#FFFFFF0 &cand &#FFFFFF10&c.");
            return;
        }

        LaunchPadListener.startSetMode(player.getUniqueId(), forward, upward);
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.SET_MODE);
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.SET_POWER,
                "%forward%", String.valueOf(forward),
                "%upward%", String.valueOf(upward));
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.CANCEL_HINT);
    }

    private void handleRemove(Player player)
    {
        LaunchPadListener.startRemoveMode(player.getUniqueId());
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.REMOVE_MODE);
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.CANCEL_HINT);
    }

    private void handleCancel(Player player)
    {
        if (LaunchPadListener.cancelMode(player.getUniqueId())) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.CANCELLED);
        } else {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.NOT_IN_MODE);
        }
    }

    private void handleList(Player player)
    {
        Map<String, LaunchPad> all = LaunchPadManager.getInstance().getAllPads();
        if (all.isEmpty()) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.NO_PADS);
            return;
        }

        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l🚀 Launch Pads &#888888(&#FFFFFF" + all.size() + "&#888888)");
        Messageable.sendRaw(player, "  &#444444&m                                    ");

        int i = 1;
        for (LaunchPad pad : all.values()) {
            Messageable.sendRaw(player, "  &#FFFFFF" + i + ". &#888888"
                    + pad.world() + " (&#FFFFFF" + pad.x() + ", " + pad.y() + ", " + pad.z() + "&#888888)"
                    + " | Forward: &#FFFFFF" + pad.forwardPower()
                    + " &#888888| Upward: &#FFFFFF" + pad.upwardPower());
            i++;
        }
        Messageable.sendRaw(player, "");
    }

    private void handleReload(Player player)
    {
        LaunchPadManager.getInstance().loadFromConfig();
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.LaunchPad.RELOADED,
                "%count%", String.valueOf(LaunchPadManager.getInstance().getAllPads().size()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendUsage(Player player)
    {
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l🚀 Launch Pad Commands");
        Messageable.sendRaw(player, "  &#444444&m                                    ");
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#888888/e launchpad set <forward> <upward> &#444444» &#CCCCCCEnter set mode");
        Messageable.sendRaw(player, "  &#888888/e launchpad remove &#444444» &#CCCCCCEnter remove mode");
        Messageable.sendRaw(player, "  &#888888/e launchpad cancel &#444444» &#CCCCCCExit selection mode");
        Messageable.sendRaw(player, "  &#888888/e launchpad list &#444444» &#CCCCCCList all pads");
        Messageable.sendRaw(player, "  &#888888/e launchpad reload &#444444» &#CCCCCCReload from config");
        Messageable.sendRaw(player, "");
    }

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            return List.of("set", "remove", "cancel", "list", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("1.0", "1.5", "2.0", "2.5", "3.0");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return List.of("0.8", "1.0", "1.2", "1.5", "2.0");
        }
        return List.of();
    }
}
