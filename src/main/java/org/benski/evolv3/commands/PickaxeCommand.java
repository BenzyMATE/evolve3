package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.managers.OmnitoolManager;
import org.benski.evolv3.menus.PickaxeLimitMenu;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Command for opening the pickaxe management menu.
 * Usage: /pickaxe
 */
public class PickaxeCommand extends BaseCommand
{
    public PickaxeCommand(JavaPlugin plugin)
    {
        super(plugin);
    }

    /** {@inheritDoc} */
    @Override
    public String getCommandName()
    {
        return "pickaxe";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getDescription()
    {
        return "Opens the pickaxe management menu";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String getUsage()
    {
        return "/pickaxe";
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable List<String> getAliases()
    {
        return List.of("pick", "p");
    }

    /** {@inheritDoc} */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
        if (!requirePlayer(sender)) {
            return true;
        }

        Player player = (Player) sender;

        if (OmnitoolManager.getInstance().isInOmnitoolMode(player)) {
            sendError(sender, "You cannot open the pickaxe menu while in an Omnitool zone.");
            return true;
        }

        PickaxeLimitMenu menu = (PickaxeLimitMenu) MenuRegistry.getInstance().get("pickaxe_limit");
        if (menu != null) {
            menu.openMenu(player);
        } else {
            sendError(sender, "Pickaxe menu is not available.");
        }

        return true;
    }
}
