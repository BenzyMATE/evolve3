package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.helpers.WorldEditSelection;
import org.benski.evolv3.core.managers.BeamManager;
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
 * Admin command for managing levitation beams.
 *
 * <ul>
 *   <li>{@code /e beam set <name> <color>}  — creates a beam from the WorldEdit selection</li>
 *   <li>{@code /e beam remove <name>}       — removes a beam</li>
 *   <li>{@code /e beam list}                — lists all configured beams</li>
 *   <li>{@code /e beam reload}              — reloads beams from disk</li>
 * </ul>
 *
 * <p>Uses the player's current WorldEdit selection (pos1 / pos2) via {@code //wand}.</p>
 * <p>Available colours: purple, yellow, green, red, blue.</p>
 */
public class BeamCommand extends BaseCommand
{
    public BeamCommand(JavaPlugin plugin) { super(plugin); }

    @Override public String getCommandName()    { return "beam"; }
    @Override public @Nullable String getDescription() { return "Manage levitation beams"; }
    @Override public @Nullable String getUsage()       { return "/e beam <set|remove|list|reload>"; }
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
        if (args.length < 3) {
            sendError(player, "Usage: &#FFFFFF/e beam set <name> <color>");
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.AVAILABLE_COLOURS);
            return;
        }

        String name = args[1].toLowerCase(Locale.ROOT);
        String color = args[2].toLowerCase(Locale.ROOT);

        if (BeamManager.getPresetColor(color) == null) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.UNKNOWN_COLOUR,
                    "%color%", color);
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.AVAILABLE_COLOURS);
            return;
        }

        if (!WorldEditSelection.isAvailable()) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.NO_WORLDEDIT);
            return;
        }

        int[][] sel = WorldEditSelection.getSelection(player);
        if (sel == null) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.NO_SELECTION);
            return;
        }

        BeamManager.getInstance().setBeam(name, color,
                sel[0][0], sel[0][1], sel[0][2],
                sel[1][0], sel[1][1], sel[1][2]);

        String pos1 = sel[0][0] + ", " + sel[0][1] + ", " + sel[0][2];
        String pos2 = sel[1][0] + ", " + sel[1][1] + ", " + sel[1][2];
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.CREATED,
                "%name%", name,
                "%color%", color,
                "%pos1%", pos1,
                "%pos2%", pos2);
    }

    private void handleRemove(Player player, String[] args)
    {
        if (args.length < 2) {
            sendError(player, "Usage: &#FFFFFF/e beam remove <name>");
            return;
        }

        String name = args[1].toLowerCase(Locale.ROOT);
        if (BeamManager.getInstance().removeBeam(name)) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.REMOVED,
                    "%name%", name);
        } else {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.NOT_FOUND,
                    "%name%", name);
        }
    }

    private void handleList(Player player)
    {
        Map<String, BeamManager.Beam> all = BeamManager.getInstance().getAllBeams();
        if (all.isEmpty()) {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.NO_BEAMS);
            return;
        }

        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l↑ Levitation Beams &#888888(&#FFFFFF" + all.size() + "&#888888)");
        Messageable.sendRaw(player, "  &#444444&m                                    ");

        for (BeamManager.Beam b : all.values()) {
            Messageable.sendRaw(player, "  &#FFFFFF" + b.name()
                    + " &#888888[" + b.color() + "]"
                    + " &#888888| (&#FFFFFF" + (int) b.minX() + ", " + (int) b.minY() + ", " + (int) b.minZ()
                    + "&#888888) → (&#FFFFFF" + (int) b.maxX() + ", " + (int) b.maxY() + ", " + (int) b.maxZ() + "&#888888)");
        }
        Messageable.sendRaw(player, "");
    }

    private void handleReload(Player player)
    {
        BeamManager.getInstance().loadFromConfig();
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Beam.RELOADED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendUsage(Player player)
    {
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l↑ Beam Commands");
        Messageable.sendRaw(player, "  &#444444&m                                    ");
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#888888/e beam set <name> <color> &#444444» &#CCCCCCCreate from WorldEdit selection");
        Messageable.sendRaw(player, "  &#888888/e beam remove <name> &#444444» &#CCCCCCRemove a beam");
        Messageable.sendRaw(player, "  &#888888/e beam list &#444444» &#CCCCCCList all beams");
        Messageable.sendRaw(player, "  &#888888/e beam reload &#444444» &#CCCCCCReload from config");
        Messageable.sendRaw(player, "");
    }

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            return List.of("set", "remove", "list", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return List.copyOf(BeamManager.getInstance().getAllBeams().keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return BeamManager.getColorNames();
        }
        return List.of();
    }
}
