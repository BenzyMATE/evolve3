package org.benski.evolv3.commands;

import java.util.List;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.menus.BackpackMenu;
import org.benski.evolv3.registry.MenuRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Command for opening the backpack menu. Usage: /backpack */
public class BackpackCommand extends BaseCommand {
  public BackpackCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public String getCommandName() {
    return "backpack";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getDescription() {
    return "Opens your backpack inventory";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getUsage() {
    return "/backpack";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getAliases() {
    return List.of("bp", "bag");
  }

  /** {@inheritDoc} */
  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) {
      return true;
    }

    Player player = (Player) sender;

    BackpackMenu menu = (BackpackMenu) MenuRegistry.getInstance().get("backpack");
    if (menu != null) {
      menu.openMenu(player);
    } else {
      sendError(sender, "Backpack menu is not available.");
    }

    return true;
  }
}
