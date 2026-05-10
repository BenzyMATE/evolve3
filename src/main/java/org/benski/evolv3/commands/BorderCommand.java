package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.helpers.WorldEditSelection;
import org.benski.evolv3.core.managers.BeamManager;
import org.benski.evolv3.core.managers.BorderHologramManager;
import org.benski.evolv3.core.managers.BorderManager;
import org.benski.evolv3.core.managers.RankManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Location;
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
    @Override public @Nullable String getUsage()       { return "/e border <set|remove|list|reload|hologram>"; }
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
            case "set"      -> handleSet(player, args);
            case "remove"   -> handleRemove(player, args);
            case "list"     -> handleList(player);
            case "reload"   -> handleReload(player);
            case "hologram" -> handleHologram(player, args);
            default         -> sendUsage(player);
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
        BorderHologramManager.getInstance().refreshAll();
        Messageable.sendConfigMessage(player, ConfigPaths.Messages.Border.RELOADED);
    }

    private void handleHologram(Player player, String[] args)
    {
        // /e border hologram <rankId> <add|list|slot> [subcommand] [value]
        if (args.length < 3) {
            sendHologramUsage(player);
            return;
        }

        String rankId = args[1].toUpperCase(Locale.ROOT);
        if (!RankManager.getInstance().isPrisonRank(rankId)) {
            Messageable.sendError(player, "Unknown rank &#FFFFFF" + rankId);
            return;
        }

        BorderHologramManager mgr = BorderHologramManager.getInstance();
        String third = args[2].toLowerCase(Locale.ROOT);

        // /e border hologram <rankId> add
        if ("add".equals(third)) {
            int slot = mgr.addHologramSlot(rankId);
            Messageable.sendSuccess(player, "Added hologram slot &#FFFFFF" + slot
                    + " &#00FFFFfor &#FFFFFF" + rankId + "&#00FFFF."
                    + " Use &#FFFFFF/e border hologram " + rankId + " " + slot + " setpos&#00FFFF to position it.");
            return;
        }

        // /e border hologram <rankId> list
        if ("list".equals(third)) {
            java.util.Collection<Integer> slots = mgr.getSlots(rankId);
            Messageable.sendRaw(player, "");
            Messageable.sendRaw(player, "  &#00FFFF&l⚡ Holograms for " + rankId);
            Messageable.sendRaw(player, "  &#444444&m                                    ");
            if (slots.isEmpty()) {
                Messageable.sendRaw(player, "  &#888888No configured slots &#444444(default slot 0 auto-spawns)");
            } else {
                for (int s : slots) {
                    Location loc = mgr.getHologramLocation(rankId, s);
                    String pos = loc != null
                            ? String.format("(%.0f, %.0f, %.0f)", loc.getX(), loc.getY(), loc.getZ())
                            : "(no position)";
                    Messageable.sendRaw(player, "  &#FFFFFFSlot " + s + " &#888888» " + pos);
                }
            }
            Messageable.sendRaw(player, "");
            return;
        }

        // /e border hologram <rankId> <slot> <subcommand> [value]
        int slot;
        try {
            slot = Integer.parseInt(third);
        } catch (NumberFormatException e) {
            sendHologramUsage(player);
            return;
        }

        if (args.length < 4) {
            sendHologramUsage(player);
            return;
        }

        String sub = args[3].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "setpos" -> {
                Location loc = player.getLocation();
                mgr.setPosition(rankId, slot, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                Messageable.sendSuccess(player, "Hologram " + slot + " for &#FFFFFF" + rankId
                        + " &#00FFFFmoved to your position.");
            }
            case "resetpos" -> {
                mgr.resetPosition(rankId, slot);
                Messageable.sendSuccess(player, "Hologram " + slot + " for &#FFFFFF" + rankId
                        + " &#00FFFFreset to wall midpoint.");
            }
            case "remove" -> {
                mgr.deleteHologramSlot(rankId, slot);
                Messageable.sendSuccess(player, "Hologram slot &#FFFFFF" + slot
                        + " &#00FFFFfor &#FFFFFF" + rankId + " &#00FFFFremoved.");
            }
            case "scale" -> {
                if (args.length < 5) {
                    Messageable.sendError(player, "Usage: /e border hologram <rankId> <slot> scale <value>");
                    return;
                }
                try {
                    float scale = Float.parseFloat(args[4]);
                    if (scale <= 0) throw new NumberFormatException();
                    mgr.setScale(rankId, slot, scale);
                    Messageable.sendSuccess(player, "Scale for slot " + slot + " of &#FFFFFF" + rankId
                            + " &#00FFFFset to &#FFFFFF" + scale + "&#00FFFF.");
                } catch (NumberFormatException e) {
                    Messageable.sendError(player, "Scale must be a positive number.");
                }
            }
            case "billboard" -> {
                if (args.length < 5) {
                    Messageable.sendError(player, "Usage: /e border hologram <rankId> <slot> billboard <true|false>");
                    return;
                }
                boolean facesPlayer = args[4].equalsIgnoreCase("true");
                mgr.setBillboard(rankId, slot, facesPlayer);
                Messageable.sendSuccess(player, "Billboard for slot " + slot + " of &#FFFFFF" + rankId
                        + (facesPlayer ? " &#00FFFFset to CENTER (faces player)."
                                       : " &#00FFFFset to FIXED (static)."));
            }
            case "setfacing" -> {
                float yaw = (player.getLocation().getYaw() + 180f) % 360f;
                mgr.setFacing(rankId, slot, yaw);
                Messageable.sendSuccess(player, "Facing for slot " + slot + " of &#FFFFFF" + rankId
                        + " &#00FFFFset to face your direction.");
            }
            case "color" -> {
                if (args.length < 5) {
                    sendError(player, "Usage: &#FFFFFF/e border hologram <rankId> <slot> color <purple|yellow|green|red|blue|reset>");
                    return;
                }
                String colorName = args[4].toLowerCase(Locale.ROOT);
                if ("reset".equals(colorName)) {
                    mgr.setColor(rankId, slot, "");
                    Messageable.sendSuccess(player, "Color for slot " + slot + " of &#FFFFFF" + rankId
                            + " &#00FFFFreset to default.");
                } else if (BeamManager.getColorNames().contains(colorName)) {
                    mgr.setColor(rankId, slot, colorName);
                    Messageable.sendSuccess(player, "Color for slot " + slot + " of &#FFFFFF" + rankId
                            + " &#00FFFFset to &#FFFFFF" + colorName + "&#00FFFF.");
                } else {
                    sendError(player, "Unknown colour. Valid: &#FFFFFFpurple&#888888, &#FFFFFFyellow&#888888, &#FFFFFFgreen&#888888, &#FFFFFFred&#888888, &#FFFFFFblue&#888888, &#FFFFFFreset");
                }
            }
            default -> sendHologramUsage(player);
        }
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
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <sub> &#444444» &#CCCCCCEdit border hologram");
        Messageable.sendRaw(player, "");
    }

    private void sendHologramUsage(Player player)
    {
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#00FFFF&l⚡ Border Hologram Commands");
        Messageable.sendRaw(player, "  &#444444&m                                    ");
        Messageable.sendRaw(player, "");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> add &#444444» &#CCCCCCAdd a new hologram slot");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> list &#444444» &#CCCCCCList configured slots");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <slot> setpos &#444444» &#CCCCCCPlace at your location");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <slot> resetpos &#444444» &#CCCCCCReset to wall midpoint");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <slot> scale <n> &#444444» &#CCCCCCSet text size");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <slot> billboard <t|f> &#444444» &#CCCCCCFaces-player toggle");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <slot> setfacing &#444444» &#CCCCCCFace your look direction");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <slot> color <preset|reset> &#444444» &#CCCCCCSet label colour");
        Messageable.sendRaw(player, "  &#888888/e border hologram <rankId> <slot> remove &#444444» &#CCCCCCDelete this slot");
        Messageable.sendRaw(player, "");
    }

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            return List.of("set", "remove", "list", "reload", "hologram");
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("remove") || sub.equals("hologram")) {
                FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
                if (ranksCfg != null) return ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("hologram")) {
            // Offer add/list plus any existing slot numbers
            List<String> opts = new java.util.ArrayList<>(List.of("add", "list"));
            BorderHologramManager.getInstance().getSlots(args[1].toUpperCase(Locale.ROOT))
                    .forEach(s -> opts.add(String.valueOf(s)));
            return opts;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("hologram")) {
            // args[2] is a slot number — offer subcommands
            try { Integer.parseInt(args[2]); }
            catch (NumberFormatException ignored) { return List.of(); }
            return List.of("setpos", "resetpos", "scale", "billboard", "setfacing", "color", "remove");
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("hologram")) {
            String sub = args[3].toLowerCase(Locale.ROOT);
            if ("billboard".equals(sub)) return List.of("true", "false");
            if ("color".equals(sub)) {
                List<String> opts = new java.util.ArrayList<>(BeamManager.getColorNames());
                opts.add("reset");
                return opts;
            }
        }
        return List.of();
    }
}
