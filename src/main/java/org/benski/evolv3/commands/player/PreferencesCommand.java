package org.benski.evolv3.commands.player;

import java.util.List;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.registry.DialogRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Command for managing player preferences. Usage: /preferences */
public class PreferencesCommand extends BaseCommand {
  /** Creates and registers the PreferencesCommand. @param plugin the owning plugin */
  public PreferencesCommand(JavaPlugin plugin) {
    super(plugin);
  }

  /** {@inheritDoc} */
  @Override
  public String getCommandName() {
    return "preferences";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getDescription() {
    return "Manage your personal preferences";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable String getUsage() {
    return "/preferences";
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getAliases() {
    return List.of("prefs", "settings");
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
    DialogRegistry.getInstance().open((Player) sender, "preferences");
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    return List.of();
  }
}
