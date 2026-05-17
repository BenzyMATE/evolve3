package org.benski.evolv3.commands;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.SkinRenderer;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** /skin [player] — renders the target's 8×8 skin face in chat. */
public class SkinCommand extends BaseCommand {
  private static final TextColor ACCENT = TextColor.color(0x00FFFF);
  private static final TextColor MUTED = TextColor.color(0x888888);

  public SkinCommand(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public String getCommandName() {
    return "skin";
  }

  @Override
  public @Nullable String getDescription() {
    return "Renders a player's skin face in chat.";
  }

  @Override
  public @Nullable String getUsage() {
    return "/skin [player]";
  }

  @Override
  public @Nullable String getPermission() {
    return "evolv3.command.skin";
  }

  @Override
  public boolean execute(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!requirePlayer(sender)) return true;
    Player viewer = (Player) sender;

    Player target;
    if (args.length >= 1) {
      target = Bukkit.getPlayer(args[0]);
      if (target == null) {
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        String msg =
            messages.getString(
                ConfigPaths.Messages.Skin.PLAYER_NOT_FOUND,
                "&c&lPlayer Not Found &7— &f%name% &7is not online.");
        viewer.sendMessage(formatMessage(viewer, msg.replace("%name%", args[0])));
        return true;
      }
    } else {
      target = viewer;
    }

    final Player finalTarget = target;

    SkinRenderer.renderAsync(
        target,
        plugin,
        rows -> {
          viewer.sendMessage(Component.empty());
          viewer.sendMessage(
              Component.text(finalTarget.getName())
                  .color(ACCENT)
                  .decoration(TextDecoration.BOLD, true)
                  .append(
                      Component.text("'s Skin")
                          .color(MUTED)
                          .decoration(TextDecoration.BOLD, false)));

          for (Component row : rows) {
            viewer.sendMessage(row);
          }

          viewer.sendMessage(Component.empty());
        });

    return true;
  }

  @Override
  public @Nullable List<String> getTabCompletion(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      String input = args[0].toLowerCase();
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(name -> name.toLowerCase().startsWith(input))
          .toList();
    }
    return null;
  }
}
