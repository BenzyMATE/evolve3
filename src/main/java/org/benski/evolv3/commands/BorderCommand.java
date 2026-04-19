package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.helpers.WorldEditSelection;
import org.benski.evolv3.core.managers.BorderManager;
import org.benski.evolv3.core.managers.RankManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin command for managing prison zone border walls.
 *
 * <ul>
 *   <li>{@code /e border set <rankId>}    — creates a wall from the WorldEdit selection</li>
 *   <li>{@code /e border remove <rankId>} — removes a wall</li>
 *   <li>{@code /e border list}            — lists all configured walls</li>
 *   <li>{@code /e border reload}          — reloads walls from disk</li>
 * </ul>
 *
 * <p>Uses the player's current WorldEdit selection (pos1 / pos2) via {@code //wand}.
 * The X,Z coordinates of pos1 and pos2 define the wall line endpoints.</p>
 */
public class BorderCommand extends BaseCommand
{
    public BorderCommand(JavaPlugin plugin) { super(plugin); }

    @Override public String getCommandName()    { return "border"; }
    @Override public @Nullable String getDescription() { return "Manage prison zone border walls"; }
    @Override public @Nullable String getUsage()       { return "/e border <set|remove|list|reload>"; }
    @Override public @Nullable String getPermission()  { return "evolv3.admin"; }

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
            case "remove" -> handleRemove(player, args);
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
        if (args.length < 2) {
            sendError(player, "Usage: &#FFFFFF/e border set <rankId> &#888888(e.g. B1, C1)");
            return;
        }

        String rankId = args[1].toUpperCase(Locale.ROOT);
        if (!RankManager.getInstance().isPrisonRank(rankId)) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.UNKNOWN_RANK,
                    "%rank%", rankId);
            return;
        }

        if (!WorldEditSelection.isAvailable()) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.NO_WORLDEDIT);
            return;
        }

        int[][] sel = WorldEditSelection.getSelection(player);
        if (sel == null) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.NO_SELECTION);
            return;
        }

        int slot = BorderManager.getInstance().addBorder(rankId,
                sel[0][0], sel[0][2], sel[1][0], sel[1][2],
                sel[0][1], sel[1][1]);

        if (slot == -1) {
            int max = BorderManager.getInstance().getMaxBordersPerRank();
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.MAX_REACHED,
                    "%rank%", rankId,
                    "%max%", String.valueOf(max));
            return;
        }

        String pos1 = sel[0][0] + ", " + sel[0][2];
        String pos2 = sel[1][0] + ", " + sel[1][2];
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.WALL_SET,
                "%slot%", String.valueOf(slot),
                "%rank%", rankId,
                "%pos1%", pos1,
                "%pos2%", pos2);
    }

    private void handleRemove(Player player, String[] args)
    {
        if (args.length < 2) {
            sendError(player, "Usage: &#FFFFFF/e border remove <rankId> [slot]");
            return;
        }

        String rankId = args[1].toUpperCase(Locale.ROOT);

        if (args.length >= 3) {
            int slot;
            try {
                slot = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sendError(player, "Slot must be a number &#888888(e.g. /e border remove B1 1)");
                return;
            }

            if (BorderManager.getInstance().removeBorder(rankId, slot)) {
                Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.WALL_REMOVED,
                        "%slot%", String.valueOf(slot),
                        "%rank%", rankId);
            } else {
                Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.NO_BORDER_AT_SLOT,
                        "%slot%", String.valueOf(slot),
                        "%rank%", rankId);
            }
        } else {
            if (BorderManager.getInstance().removeBorder(rankId)) {
                Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.ALL_REMOVED,
                        "%rank%", rankId);
            } else {
                Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.NO_BORDERS_FOR_RANK,
                        "%rank%", rankId);
            }
        }
    }

    private void handleList(Player player)
    {
        Map<String, List<BorderManager.BorderWall>> all = BorderManager.getInstance().getAllBorders();
        if (all.isEmpty()) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.NO_BORDERS);
            return;
        }

        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l⚡ Zone Border Walls");
        Messageable.sendRaw(player, "  &#444444&m                                    ");

        for (Map.Entry<String, List<BorderManager.BorderWall>> entry : all.entrySet()) {
            for (BorderManager.BorderWall w : entry.getValue()) {
                Messageable.sendRaw(player, "  &#FFFFFF" + w.rankId()
                        + " &#888888[slot " + w.slot() + "]"
                        + " &#888888| (&#FFFFFF" + (int) w.x1() + ", " + (int) w.z1()
                        + "&#888888) → (&#FFFFFF" + (int) w.x2() + ", " + (int) w.z2() + "&#888888)"
                        + " &#888888Y: &#FFFFFF" + (int) w.minY() + "&#888888-&#FFFFFF" + (int) w.maxY());
            }
        }
        Messageable.sendRaw(player, "");
    }

    private void handleReload(Player player)
    {
        BorderManager.getInstance().loadFromConfig();
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.RELOADED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendUsage(Player player)
    {
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l⚡ Border Commands");
        Messageable.sendRaw(player, "  &#444444&m                                    ");
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#888888/e border set <rankId> &#444444» &#CCCCCCCreate from WorldEdit selection");
        Messageable.sendRaw(player, "  &#888888/e border remove <rankId> [slot] &#444444» &#CCCCCCRemove wall(s)");
        Messageable.sendRaw(player, "  &#888888/e border list &#444444» &#CCCCCCList all walls");
        Messageable.sendRaw(player, "  &#888888/e border reload &#444444» &#CCCCCCReload from config");
        Messageable.sendRaw(player, "");
    }

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            return List.of("set", "remove", "list", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))) {
            FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
            if (ranksCfg != null) return ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
        }
        return List.of();
    }
}
