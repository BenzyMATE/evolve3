package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.managers.MineHologramManager;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.MineManager.Mine;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin command for managing mines.
 *
 * <ul>
 *   <li>{@code /e mine create <name>}                       — create from WorldEdit selection</li>
 *   <li>{@code /e mine remove <name>}                       — delete a mine</li>
 *   <li>{@code /e mine reset <name>}                        — force-reset a mine</li>
 *   <li>{@code /e mine list}                                — list all mines</li>
 *   <li>{@code /e mine info <name>}                         — detailed mine info</li>
 *   <li>{@code /e mine setspawn <name>}                     — set spawn to your location</li>
 *   <li>{@code /e mine addcontent <name> <material> <weight>} — add/update block composition</li>
 *   <li>{@code /e mine removecontent <name> <material>}     — remove from block composition</li>
 *   <li>{@code /e mine set <name> <setting> <value>}        — modify a mine setting</li>
 *   <li>{@code /e mine reload}                              — reload from config</li>
 * </ul>
 */
public class MineCommand extends BaseCommand
{
    public MineCommand(JavaPlugin plugin) { super(plugin); }

    @Override public String getCommandName() { return "mine"; }
    @Override public @Nullable String getDescription() { return "Manage mines"; }
    @Override public @Nullable String getUsage() { return "/e mine <create|remove|reset|list|info|setspawn|addcontent|removecontent|set|reload>"; }
    @Override public @Nullable String getPermission() { return "evolv3.admin"; }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args)
    {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase(Locale.ROOT))
        {
            case "create"        -> handleCreate(player, args);
            case "remove"        -> handleRemove(player, args);
            case "reset"         -> handleReset(player, args);
            case "list"          -> handleList(player);
            case "info"          -> handleInfo(player, args);
            case "setspawn"      -> handleSetSpawn(player, args);
            case "addcontent"    -> handleAddContent(player, args);
            case "removecontent" -> handleRemoveContent(player, args);
            case "set"           -> handleSet(player, args);
            case "reload"        -> handleReload(player);
            default              -> sendHelp(player);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    private void handleCreate(Player player, String[] args)
    {
        if (args.length < 2) { sendError(player, "Usage: &#FFFFFF/e mine create <name>"); return; }
        String name = args[1].toLowerCase(Locale.ROOT);

        Mine mine = MineManager.getInstance().createMine(name, player);
        if (mine == null)
        {
            if (MineManager.getInstance().getMine(name) != null)
                Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.ALREADY_EXISTS,
                        "%mine%", name);
            else
                Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.NO_SELECTION);
            return;
        }

        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.MINE_CREATED,
                "%mine%", name);
    }

    private void handleRemove(Player player, String[] args)
    {
        if (args.length < 2) { sendError(player, "Usage: &#FFFFFF/e mine remove <name>"); return; }
        String name = args[1].toLowerCase(Locale.ROOT);

        if (MineManager.getInstance().removeMine(name))
        {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.MINE_REMOVED,
                    "%mine%", name);
        }
        else
        {
            sendMineNotFound(player, name);
        }
    }

    private void handleReset(Player player, String[] args)
    {
        if (args.length < 2) { sendError(player, "Usage: &#FFFFFF/e mine reset <name>"); return; }
        String name = args[1].toLowerCase(Locale.ROOT);

        if (MineManager.getInstance().getMine(name) == null) { sendMineNotFound(player, name); return; }

        MineManager.getInstance().resetMine(name);
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.RESETTING,
                "%mine%", name);
    }

    private void handleList(Player player)
    {
        Map<String, Mine> all = MineManager.getInstance().getAllMines();
        if (all.isEmpty())
        {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.NO_MINES);
            return;
        }

        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l⛏ Mines &#888888(&#FFFFFF" + all.size() + "&#888888)");
        Messageable.sendRaw(player, "  &#444444&m                                    ");

        for (Mine m : all.values())
        {
            double pct = MineManager.getInstance().getRemainingPercentage(m.name());
            int players = MineManager.getInstance().getPlayerCount(m.name());
            Messageable.sendRaw(player, "  &#FFFFFF" + m.name()
                    + " &#888888| &#CCCCCC" + m.totalBlocks() + " blocks"
                    + " &#888888| &#00FFFF" + String.format("%.1f%%", pct) + " remaining"
                    + " &#888888| &#B366D9" + players + " players"
                    + " &#888888| Reset: &#FFFFFF" + m.resetInterval() + "m &#888888/ &#FFFFFF" + m.resetPercentage() + "%");
        }
        Messageable.sendRaw(player, "");
    }

    private void handleInfo(Player player, String[] args)
    {
        if (args.length < 2) { sendError(player, "Usage: &#FFFFFF/e mine info <name>"); return; }
        String name = args[1].toLowerCase(Locale.ROOT);

        Mine mine = MineManager.getInstance().getMine(name);
        if (mine == null) { sendMineNotFound(player, name); return; }

        double pct = MineManager.getInstance().getRemainingPercentage(name);
        int players = MineManager.getInstance().getPlayerCount(name);

        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l⛏ Mine: &#FFFFFF&l" + mine.name());
        Messageable.sendRaw(player, "  &#444444&m                                    ");
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#888888World  &#FFFFFF" + mine.world());
        Messageable.sendRaw(player, "  &#888888Region  &#FFFFFF(" + mine.minX() + ", " + mine.minY() + ", " + mine.minZ()
                + ") &#888888→ &#FFFFFF(" + mine.maxX() + ", " + mine.maxY() + ", " + mine.maxZ() + ")");
        Messageable.sendRaw(player, "  &#888888Total blocks  &#FFFFFF" + mine.totalBlocks());
        Messageable.sendRaw(player, "  &#888888Remaining  &#00FFFF" + String.format("%.2f%%", pct));
        Messageable.sendRaw(player, "  &#888888Players inside  &#B366D9" + players);
        Messageable.sendRaw(player, "  &#888888Spawn  &#FFFFFF(" + String.format("%.1f, %.1f, %.1f",
                mine.spawnX(), mine.spawnY(), mine.spawnZ()) + ")");
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#888888Reset interval  &#FFFFFF" + mine.resetInterval() + " min");
        Messageable.sendRaw(player, "  &#888888Reset at  &#FFFFFF" + mine.resetPercentage() + "%");
        Messageable.sendRaw(player, "  &#888888Warning  &#FFFFFF" + mine.resetWarning() + "s &#888888before reset");
        Messageable.sendRaw(player, "  &#888888Teleport on reset  &#FFFFFF" + mine.teleportOnReset());
        Messageable.sendRaw(player, "  &#888888Cave count  &#FFFFFF" + mine.caveCount());
        Messageable.sendRaw(player, "  &#888888Cave min distance  &#FFFFFF" + mine.caveMinDistance());

        if (!mine.contents().isEmpty())
        {
            Messageable.sendRaw(player, "");
            Messageable.sendRaw(player, "  &#FFFFFF&lBlock Composition");
            int totalWeight = mine.contents().values().stream().mapToInt(i -> i).sum();
            for (Map.Entry<String, Integer> entry : mine.contents().entrySet())
            {
                double chance = totalWeight > 0 ? (double) entry.getValue() / totalWeight * 100 : 0;
                Messageable.sendRaw(player, "  &#888888● &#FFFFFF" + entry.getKey()
                        + " &#888888→ weight &#FFFFFF" + entry.getValue()
                        + " &#888888(&#00FFFF" + String.format("%.1f%%", chance) + "&#888888)");
            }
        }
        Messageable.sendRaw(player, "");
    }

    private void handleSetSpawn(Player player, String[] args)
    {
        if (args.length < 2) { sendError(player, "Usage: &#FFFFFF/e mine setspawn <name>"); return; }
        String name = args[1].toLowerCase(Locale.ROOT);

        if (MineManager.getInstance().getMine(name) == null) { sendMineNotFound(player, name); return; }

        MineManager.getInstance().setSpawn(name, player.getLocation());
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.SPAWN_SET,
                "%mine%", name);
    }

    private void handleAddContent(Player player, String[] args)
    {
        if (args.length < 4)
        {
            sendError(player, "Usage: &#FFFFFF/e mine addcontent <name> <material> <weight>");
            return;
        }

        String name = args[1].toLowerCase(Locale.ROOT);
        if (MineManager.getInstance().getMine(name) == null) { sendMineNotFound(player, name); return; }

        Material mat = Material.matchMaterial(args[2]);
        if (mat == null || !mat.isBlock())
        {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.INVALID_MATERIAL,
                    "%material%", args[2]);
            return;
        }

        int weight;
        try { weight = Integer.parseInt(args[3]); }
        catch (NumberFormatException e) { sendError(player, "Weight must be a number."); return; }
        if (weight <= 0) { sendError(player, "Weight must be positive."); return; }

        MineManager.getInstance().setContent(name, mat, weight);

        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.CONTENTS_ADDED,
                "%material%", mat.name(),
                "%weight%", String.valueOf(weight),
                "%mine%", name);
    }

    private void handleRemoveContent(Player player, String[] args)
    {
        if (args.length < 3)
        {
            sendError(player, "Usage: &#FFFFFF/e mine removecontent <name> <material>");
            return;
        }

        String name = args[1].toLowerCase(Locale.ROOT);
        if (MineManager.getInstance().getMine(name) == null) { sendMineNotFound(player, name); return; }

        Material mat = Material.matchMaterial(args[2]);
        if (mat == null)
        {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.INVALID_MATERIAL,
                    "%material%", args[2]);
            return;
        }

        if (MineManager.getInstance().removeContent(name, mat))
        {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.CONTENTS_REMOVED,
                    "%material%", mat.name(),
                    "%mine%", name);
        }
        else
        {
            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.NOT_IN_COMPOSITION,
                    "%material%", mat.name(),
                    "%mine%", name);
        }
    }

    private void handleSet(Player player, String[] args)
    {
        if (args.length < 4)
        {
            sendError(player, "Usage: &#FFFFFF/e mine set <name> <setting> <value>");
            Messageable.sendRaw(player, "  &#888888Settings: &#FFFFFF reset-interval&#888888, &#FFFFFFreset-percentage&#888888, "
                    + "&#FFFFFFreset-warning&#888888, &#FFFFFFteleport&#888888, &#FFFFFFcave-count&#888888, &#FFFFFFcave-min-distance&#888888, &#FFFFFFhologram");
            return;
        }

        String name = args[1].toLowerCase(Locale.ROOT);
        Mine mine = MineManager.getInstance().getMine(name);
        if (mine == null) { sendMineNotFound(player, name); return; }

        String setting = args[2].toLowerCase(Locale.ROOT);
        String value = args[3];

        try
        {
            String displaySetting = "";
            String displayValue = "";

            switch (setting)
            {
                case "reset-interval" ->
                {
                    MineManager.getInstance().setResetInterval(name, Integer.parseInt(value));
                    displaySetting = "Reset interval";
                    displayValue = value + " minutes";
                }
                case "reset-percentage" ->
                {
                    MineManager.getInstance().setResetPercentage(name, Integer.parseInt(value));
                    displaySetting = "Reset percentage";
                    displayValue = value + "%";
                }
                case "reset-warning" ->
                {
                    Mine updated = new Mine(mine.name(), mine.world(),
                            mine.minX(), mine.minY(), mine.minZ(),
                            mine.maxX(), mine.maxY(), mine.maxZ(),
                            mine.spawnX(), mine.spawnY(), mine.spawnZ(),
                            mine.resetInterval(), mine.resetPercentage(), Integer.parseInt(value),
                            mine.teleportOnReset(), mine.caveCount(), mine.caveMinDistance(),
                            mine.hologram(), mine.contents());
                    MineManager.getInstance().updateMine(updated);
                    displaySetting = "Reset warning";
                    displayValue = value + "s";
                }
                case "teleport" ->
                {
                    boolean tp = Boolean.parseBoolean(value);
                    Mine updated = new Mine(mine.name(), mine.world(),
                            mine.minX(), mine.minY(), mine.minZ(),
                            mine.maxX(), mine.maxY(), mine.maxZ(),
                            mine.spawnX(), mine.spawnY(), mine.spawnZ(),
                            mine.resetInterval(), mine.resetPercentage(), mine.resetWarning(),
                            tp, mine.caveCount(), mine.caveMinDistance(),
                            mine.hologram(), mine.contents());
                    MineManager.getInstance().updateMine(updated);
                    displaySetting = "Teleport on reset";
                    displayValue = String.valueOf(tp);
                }
                case "cave-count" ->
                {
                    Mine updated = new Mine(mine.name(), mine.world(),
                            mine.minX(), mine.minY(), mine.minZ(),
                            mine.maxX(), mine.maxY(), mine.maxZ(),
                            mine.spawnX(), mine.spawnY(), mine.spawnZ(),
                            mine.resetInterval(), mine.resetPercentage(), mine.resetWarning(),
                            mine.teleportOnReset(), Integer.parseInt(value), mine.caveMinDistance(),
                            mine.hologram(), mine.contents());
                    MineManager.getInstance().updateMine(updated);
                    displaySetting = "Cave count";
                    displayValue = value;
                }
                case "cave-min-distance" ->
                {
                    Mine updated = new Mine(mine.name(), mine.world(),
                            mine.minX(), mine.minY(), mine.minZ(),
                            mine.maxX(), mine.maxY(), mine.maxZ(),
                            mine.spawnX(), mine.spawnY(), mine.spawnZ(),
                            mine.resetInterval(), mine.resetPercentage(), mine.resetWarning(),
                            mine.teleportOnReset(), mine.caveCount(), Integer.parseInt(value),
                            mine.hologram(), mine.contents());
                    MineManager.getInstance().updateMine(updated);
                    displaySetting = "Cave min distance";
                    displayValue = value + " blocks";
                }
                case "hologram" ->
                {
                    boolean holo = Boolean.parseBoolean(value);
                    Mine updated = new Mine(mine.name(), mine.world(),
                            mine.minX(), mine.minY(), mine.minZ(),
                            mine.maxX(), mine.maxY(), mine.maxZ(),
                            mine.spawnX(), mine.spawnY(), mine.spawnZ(),
                            mine.resetInterval(), mine.resetPercentage(), mine.resetWarning(),
                            mine.teleportOnReset(), mine.caveCount(), mine.caveMinDistance(),
                            holo, mine.contents());
                    MineManager.getInstance().updateMine(updated);

                    // Spawn or remove the hologram based on the new setting
                    if (holo)
                    {
                        MineHologramManager.getInstance().spawnHologram(updated);
                    }
                    else
                    {
                        MineHologramManager.getInstance().removeHologram(mine.name());
                    }

                    displaySetting = "Hologram";
                    displayValue = String.valueOf(holo);
                }
                default -> { sendError(player, "Unknown setting: &#FFFFFF" + setting); return; }
            }

            Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.SETTING_UPDATED,
                    "%setting%", displaySetting,
                    "%mine%", name,
                    "%value%", displayValue);
        }
        catch (NumberFormatException e)
        {
            sendError(player, "Invalid number: &#FFFFFF" + value);
        }
    }

    private void handleReload(Player player)
    {
        MineManager.getInstance().loadFromConfig();
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.RELOADED,
                "%count%", String.valueOf(MineManager.getInstance().getAllMines().size()));
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private void sendHelp(Player player)
    {
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l⛏ Mine Commands");
        Messageable.sendRaw(player, "  &#444444&m                                    ");
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#888888/e mine create <name> &#444444» &#CCCCCCCreate from WorldEdit selection");
        Messageable.sendRaw(player, "  &#888888/e mine remove <name> &#444444» &#CCCCCCDelete a mine");
        Messageable.sendRaw(player, "  &#888888/e mine reset <name> &#444444» &#CCCCCCForce-reset a mine");
        Messageable.sendRaw(player, "  &#888888/e mine list &#444444» &#CCCCCCList all mines");
        Messageable.sendRaw(player, "  &#888888/e mine info <name> &#444444» &#CCCCCCDetailed mine info");
        Messageable.sendRaw(player, "  &#888888/e mine setspawn <name> &#444444» &#CCCCCCSet spawn to your location");
        Messageable.sendRaw(player, "  &#888888/e mine addcontent <name> <material> <weight> &#444444» &#CCCCCCAdd block");
        Messageable.sendRaw(player, "  &#888888/e mine removecontent <name> <material> &#444444» &#CCCCCCRemove block");
        Messageable.sendRaw(player, "  &#888888/e mine set <name> <setting> <value> &#444444» &#CCCCCCEdit settings");
        Messageable.sendRaw(player, "  &#888888/e mine reload &#444444» &#CCCCCCReload from config");
        Messageable.sendRaw(player, "");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendMineNotFound(Player player, String name)
    {
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Mine.MINE_NOT_FOUND,
                "%mine%", name);
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "remove", "reset", "list", "info", "setspawn",
            "addcontent", "removecontent", "set", "reload");

    private static final List<String> SETTINGS = List.of(
            "reset-interval", "reset-percentage", "reset-warning",
            "teleport", "cave-count", "cave-min-distance", "hologram");

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1)
        {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> mineNames = List.copyOf(MineManager.getInstance().getAllMines().keySet());

        // Arg 2: mine name for most subcommands
        if (args.length == 2 && List.of("remove", "reset", "info", "setspawn",
                "addcontent", "removecontent", "set").contains(sub))
        {
            return mineNames.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        // Arg 3: material for addcontent/removecontent, setting name for set
        if (args.length == 3)
        {
            if ("addcontent".equals(sub))
            {
                String prefix = args[2].toUpperCase(Locale.ROOT);
                return Arrays.stream(Material.values())
                        .filter(Material::isBlock)
                        .map(Material::name)
                        .filter(n -> n.startsWith(prefix))
                        .limit(20)
                        .collect(Collectors.toList());
            }
            if ("removecontent".equals(sub))
            {
                Mine mine = MineManager.getInstance().getMine(args[1]);
                if (mine != null)
                {
                    return mine.contents().keySet().stream()
                            .filter(s -> s.startsWith(args[2].toUpperCase(Locale.ROOT)))
                            .collect(Collectors.toList());
                }
            }
            if ("set".equals(sub))
            {
                return SETTINGS.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        // Arg 4: value suggestions
        if (args.length == 4 && "addcontent".equals(sub))
        {
            return List.of("1", "5", "10", "20", "50", "100");
        }

        return List.of();
    }
}
