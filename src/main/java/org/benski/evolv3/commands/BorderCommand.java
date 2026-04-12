package org.benski.evolv3.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.BorderManager;
import org.benski.evolv3.core.managers.RankManager;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 *   <li>{@code /border wand}            — gives the zone-selection wand</li>
 *   <li>{@code /border set <rankId>}    — creates a wall from the two wand points</li>
 *   <li>{@code /border remove <rankId>} — removes a wall</li>
 *   <li>{@code /border list}            — lists all configured walls</li>
 *   <li>{@code /border reload}          — reloads walls from disk</li>
 * </ul>
 *
 * <p>The {@code rankId} is the rank that unlocks the wall (e.g. {@code B1} unlocks
 * the B-zone wall, {@code C1} unlocks the C-zone wall, etc.).</p>
 */
public class BorderCommand extends BaseCommand
{
    public BorderCommand(JavaPlugin plugin) { super(plugin); }

    @Override public String getCommandName()    { return "border"; }
    @Override public @Nullable String getDescription() { return "Manage prison zone border walls"; }
    @Override public @Nullable String getUsage()       { return "/border <wand|set|remove|list|reload>"; }
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
            case "wand"   -> handleWand(player);
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

    private void handleWand(Player player)
    {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text("Border Wand")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Left-click » Set Point 1")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click » Set Point 2")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Then: /border set <rankId>")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        sendSuccess(player, "Border Wand added to your inventory.");
    }

    private void handleSet(Player player, String[] args)
    {
        if (args.length < 2) {
            sendError(player, "Usage: /border set <rankId>  (e.g. B1, C1)");
            return;
        }

        String rankId = args[1].toUpperCase(Locale.ROOT);
        if (!RankManager.getInstance().isPrisonRank(rankId)) {
            sendError(player, "Unknown rank: " + rankId);
            return;
        }

        Location p1 = BorderManager.getInstance().getPoint1(player.getUniqueId());
        Location p2 = BorderManager.getInstance().getPoint2(player.getUniqueId());

        if (p1 == null || p2 == null) {
            sendError(player, "Select both points first with the Border Wand.");
            return;
        }

        BorderManager.getInstance().setBorder(rankId,
                p1.getX(), p1.getZ(), p2.getX(), p2.getZ());

        sendSuccess(player, "Border wall set for rank §e" + rankId + "§a ("
                + "§e(" + (int) p1.getX() + ", " + (int) p1.getZ() + ")§a → "
                + "§e(" + (int) p2.getX() + ", " + (int) p2.getZ() + ")§a).");
    }

    private void handleRemove(Player player, String[] args)
    {
        if (args.length < 2) {
            sendError(player, "Usage: /border remove <rankId>");
            return;
        }

        String rankId = args[1].toUpperCase(Locale.ROOT);
        if (BorderManager.getInstance().removeBorder(rankId)) {
            sendSuccess(player, "Border wall for rank §e" + rankId + "§a removed.");
        } else {
            sendError(player, "No border wall exists for rank: " + rankId);
        }
    }

    private void handleList(Player player)
    {
        Map<String, BorderManager.BorderWall> all = BorderManager.getInstance().getAllBorders();
        if (all.isEmpty()) {
            sendInfo(player, "No border walls configured.");
            return;
        }

        player.sendMessage(Component.text("── Zone Border Walls ──")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        for (BorderManager.BorderWall w : all.values()) {
            player.sendMessage(Component.text("  §e" + w.wallId()
                    + " §7| (§f" + (int) w.x1() + ", " + (int) w.z1()
                    + "§7) → (§f" + (int) w.x2() + ", " + (int) w.z2() + "§7)"));
        }
    }

    private void handleReload(Player player)
    {
        BorderManager.getInstance().loadFromConfig();
        sendSuccess(player, "Border walls reloaded from disk.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendUsage(Player player)
    {
        player.sendMessage(Component.text("Usage: /border <wand|set <rankId>|remove <rankId>|list|reload>")
                .color(NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> getTabCompletion(@NotNull CommandSender sender, @NotNull Command command,
                                                    @NotNull String alias, @NotNull String[] args)
    {
        if (args.length == 1) {
            return List.of("wand", "set", "remove", "list", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))) {
            FileConfiguration ranksCfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
            if (ranksCfg != null) return ranksCfg.getStringList(ConfigPaths.Ranks.ORDER);
        }
        return List.of();
    }
}
