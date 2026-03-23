package org.benski.evolv3.commands;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.RankManager;
import org.benski.evolv3.core.managers.StorageRegistryManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin command for managing permissions attached to ranks in-game.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /rankperm add <rank> <permission>}    — add a permission to a rank</li>
 *   <li>{@code /rankperm remove <rank> <permission>} — remove a permission from a rank</li>
 *   <li>{@code /rankperm list <rank>}                — list permissions for a rank</li>
 * </ul>
 *
 * <p>Works for both prison ranks (A1–F4) and staff ranks (helper, moderator, admin, owner).
 * The rank type is auto-detected. Changes are saved to {@code ranks.yml} immediately and
 * applied to all online players of that rank without a reload.</p>
 */
public class RankPermCommand extends BaseCommand
{
    public RankPermCommand(JavaPlugin plugin)
    {
        super(plugin);
    }

    @Override
    public String getCommandName() { return "rankperm"; }

    @Override
    public @Nullable String getDescription() { return "Manage permissions for ranks in-game"; }

    @Override
    public @Nullable String getUsage() { return "/rankperm <add|remove|list> <rank> [permission]"; }

    @Override
    public @Nullable String getPermission() { return "evolv3.rank.perm"; }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args)
    {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String sub  = args[0].toLowerCase();
        String rank = args[1];

        RankManager rm = RankManager.getInstance();

        // Resolve rank type — prison ranks (e.g. "A1") or staff ranks (e.g. "admin")
        boolean isPrison = rm.isPrisonRank(rank);
        boolean isStaff  = !isPrison && rm.isStaffRank(rank.toLowerCase());

        if (!isPrison && !isStaff) {
            sendError(sender, "Unknown rank '" + rank + "'. Use a prison rank (A1–F4) or staff rank.");
            return true;
        }

        // Normalise: prison ranks are upper-case, staff ranks are lower-case
        String rankId = isPrison ? rank.toUpperCase() : rank.toLowerCase();

        switch (sub) {
            case "add" -> handleAdd(sender, rm, rankId, !isPrison, args);
            case "remove", "rem" -> handleRemove(sender, rm, rankId, !isPrison, args);
            case "list" -> handleList(sender, rm, rankId, !isPrison);
            default -> sendUsage(sender);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private void handleAdd(CommandSender sender, RankManager rm,
                           String rankId, boolean isStaff, String[] args)
    {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /rankperm add <rank> <permission>");
            return;
        }

        String permission = args[2];
        boolean added = rm.addPermission(rankId, isStaff, permission);

        if (added) {
            String type   = isStaff ? "staff rank" : "rank";
            String prefix = isStaff ? rm.getStaffPrefix(rankId) : rm.getRankPrefix(rankId);
            String displayRank = LegacyComponentSerializer.legacySection()
                    .serialize(LegacyComponentSerializer.legacySection()
                            .deserialize(MatrixColorAPI.process(prefix)));
            sendSuccess(sender, "Added §f" + permission + " §ato " + type + " §f" + displayRank + "§a.");
        } else {
            sendError(sender, "§f" + permission + " §cis already on that rank.");
        }
    }

    private void handleRemove(CommandSender sender, RankManager rm,
                              String rankId, boolean isStaff, String[] args)
    {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /rankperm remove <rank> <permission>");
            return;
        }

        String permission = args[2];
        boolean removed = rm.removePermission(rankId, isStaff, permission);

        if (removed) {
            String type   = isStaff ? "staff rank" : "rank";
            String prefix = isStaff ? rm.getStaffPrefix(rankId) : rm.getRankPrefix(rankId);
            String displayRank = LegacyComponentSerializer.legacySection()
                    .serialize(LegacyComponentSerializer.legacySection()
                            .deserialize(MatrixColorAPI.process(prefix)));
            sendSuccess(sender, "Removed §f" + permission + " §afrom " + type + " §f" + displayRank + "§a.");
        } else {
            sendError(sender, "§f" + permission + " §cwas not found on that rank.");
        }
    }

    private void handleList(CommandSender sender, RankManager rm,
                            String rankId, boolean isStaff)
    {
        List<String> perms = rm.getPermissions(rankId, isStaff);

        String type   = isStaff ? "staff rank" : "rank";
        String prefix = isStaff ? rm.getStaffPrefix(rankId) : rm.getRankPrefix(rankId);
        String displayRank = LegacyComponentSerializer.legacySection()
                .serialize(LegacyComponentSerializer.legacySection()
                        .deserialize(MatrixColorAPI.process(prefix)));

        sender.sendMessage(LegacyComponentSerializer.legacySection()
                .deserialize(MatrixColorAPI.process(
                        "&#888888Permissions for " + type + " " + prefix
                                + " &#888888(" + perms.size() + "):")));

        if (perms.isEmpty()) {
            sender.sendMessage("§7  (none)");
        } else {
            for (String perm : perms) {
                sender.sendMessage("§7  - §f" + perm);
            }
        }
    }

    private void sendUsage(CommandSender sender)
    {
        sender.sendMessage("§cUsage: /rankperm <add|remove|list> <rank> [permission]");
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                   @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            return filterPrefix(List.of("add", "remove", "list"), args[0].toLowerCase());
        }

        if (args.length == 2) {
            FileConfiguration cfg = StorageRegistryManager.getInstance().getConfig(ConfigNames.RANKS);
            List<String> allRanks = new ArrayList<>(cfg.getStringList(ConfigPaths.Ranks.ORDER));
            allRanks.addAll(cfg.getStringList(ConfigPaths.Ranks.STAFF_ORDER));
            return filterPrefix(allRanks, args[1]);
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("rem"))) {
            // Suggest the rank's current permissions for easy removal
            String rank = args[1];
            RankManager rm = RankManager.getInstance();
            boolean isPrison = rm.isPrisonRank(rank);
            boolean isStaff  = !isPrison && rm.isStaffRank(rank.toLowerCase());
            if (isPrison || isStaff) {
                String rankId = isPrison ? rank.toUpperCase() : rank.toLowerCase();
                return filterPrefix(rm.getPermissions(rankId, isStaff), args[2]);
            }
        }

        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix)
    {
        String lc = prefix.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lc))
                .toList();
    }
}
