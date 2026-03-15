package org.benski.evolv3.commands;

import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.menus.SoundTestMenu;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Temporary command for testing Minecraft sounds.
 * Opens a paginated GUI showing all sounds — click any to play it.
 * Remove this class and SoundTestMenu when no longer needed.
 */
public class SoundCommand extends BaseCommand implements Listener
{
    public SoundCommand(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getCommandName() { return "sound"; }

    @Override
    public @Nullable String getDescription() { return "Opens the sound testing GUI."; }

    @Override
    public @Nullable String getUsage() { return "/sound"; }

    @Override
    public @Nullable String getPermission() { return "evolv3.command.sound"; }

    @Override
    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        player.openInventory(new SoundTestMenu(0).getInventory());
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SoundTestMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot < 45) {
            Sound sound = SoundTestMenu.getSoundAt(menu.getPage(), slot);
            if (sound == null) return;
            player.playSound(player.getLocation(), sound, 1f, 1f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("▶ " + sound.name())
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        } else if (slot == 45 && menu.getPage() > 0) {
            player.openInventory(new SoundTestMenu(menu.getPage() - 1).getInventory());
        } else if (slot == 53 && menu.getPage() < SoundTestMenu.getTotalPages() - 1) {
            player.openInventory(new SoundTestMenu(menu.getPage() + 1).getInventory());
        }
    }
}
