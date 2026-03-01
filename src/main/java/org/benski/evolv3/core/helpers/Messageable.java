package org.benski.evolv3.core.helpers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Static utility for sending formatted messages to players and command senders.
 * Centralizes message formatting to avoid duplicate implementations across commands and menus.
 */
public final class Messageable
{
    private Messageable() {}

    /**
     * Sends a success message to the sender.
     *
     * @param sender  the command sender
     * @param message the message to send
     */
    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage("§a" + message);
    }

    /**
     * Sends an error message to the sender.
     *
     * @param sender  the command sender
     * @param message the message to send
     */
    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage("§c" + message);
    }

    /**
     * Sends an info message to the sender.
     *
     * @param sender  the command sender
     * @param message the message to send
     */
    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage("§e" + message);
    }

    /**
     * Sends a success message to the player.
     *
     * @param player  the player
     * @param message the message to send
     */
    public static void sendSuccess(Player player, String message) {
        player.sendMessage("§a" + message);
    }

    /**
     * Sends an error message to the player.
     *
     * @param player  the player
     * @param message the message to send
     */
    public static void sendError(Player player, String message) {
        player.sendMessage("§c" + message);
    }

    /**
     * Sends an info message to the player.
     *
     * @param player  the player
     * @param message the message to send
     */
    public static void sendInfo(Player player, String message) {
        player.sendMessage("§e" + message);
    }
}
