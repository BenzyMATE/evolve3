package org.benski.evolv3.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.SkinRenderer;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantDropManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.registry.ListenerRegistry;
import org.benski.evolv3.core.managers.EnchantSelectionManager;
import org.benski.evolv3.core.managers.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.RankManager;
import org.benski.evolv3.enchants.AutoSellStoneHandler;
import org.benski.evolv3.enchants.EfficiencyStoneHandler;
import org.benski.evolv3.enchants.FortuneStoneHandler;
import org.benski.evolv3.enchants.FrostbiteStoneHandler;
import org.benski.evolv3.enchants.FurnaceStoneHandler;
import org.benski.evolv3.enchants.LazerStoneHandler;
import org.benski.evolv3.enchants.MerchantStoneHandler;
import org.benski.evolv3.enchants.ExplosiveStoneHandler;
import org.benski.evolv3.enchants.ExtractionStoneHandler;
import org.benski.evolv3.enchants.ExtractionEnchant;
import org.benski.evolv3.enchants.FortuneEnchant;
import org.benski.evolv3.enchants.RadioactiveEnchant;
import org.benski.evolv3.enchants.RadioactiveStoneHandler;
import org.benski.evolv3.enchants.ScavengerStoneHandler;
import org.benski.evolv3.enchants.ThorStoneHandler;
import org.benski.evolv3.enchants.VortexStoneHandler;
import org.benski.evolv3.enchants.UnbreakingStoneHandler;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Handles player join and quit events.
 * Responsible for loading and unloading player profiles.
 */
public class JoinListener extends BaseListener
{
    /**
     * Constructor for JoinListener.
     *
     * @param plugin The plugin instance.
     */
    public JoinListener(JavaPlugin plugin)
    {
        super(plugin);
    }

    /**
     * Handles player join events.
     * Loads the player's profile from the database or creates a new one.
     *
     * @param event The player join event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinEvent(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();

        PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
        ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();

        if (playerDataCenter != null) {
            playerDataCenter.loadOrCreate(player, playerId);
        }

        if (armorDataCenter != null) {
            armorDataCenter.loadOrCreate(player, playerId);
        }

        // Restore any in-progress enchant selection (same options, pickaxe returned)
        EnchantSelectionManager.getInstance().handlePlayerJoin(player);

        // Restore any in-progress evolution animation (same rarity, items delivered)
        EvolutionSelectionManager.getInstance().handlePlayerJoin(player);

        // Apply rank permissions once the profile has had a tick to load from cache.
        // The profile is loaded synchronously from cache on the same tick for returning
        // players; a 2-tick delay covers the async DB load path for new/first joins.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerProfile profile = playerDataCenter != null ? playerDataCenter.getLocal(player) : null;
            if (profile != null) {
                RankManager.getInstance().applyPermissions(player, profile);
            }
        }, 2L);

        // Apply pickaxe lore regardless of which slot the pickaxe is in.
        // A small delay ensures the pickaxe profile has loaded from cache before we read it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) PickaxeManager.getInstance().updateLoreFromInventory(player);
        }, 5L);

        // Send skin join message — slight delay so the player's client is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendJoinMessage(player), 20L);
    }

    private void sendJoinMessage(Player player)
    {
        if (!player.isOnline()) return;

        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        if (!messages.getBoolean(ConfigPaths.Messages.JoinMessage.ENABLED, true)) return;

        String padding = messages.getString(ConfigPaths.Messages.JoinMessage.SKIN_PADDING, "  ");
        List<String> textLines = messages.getStringList(ConfigPaths.Messages.JoinMessage.LINES);

        SkinRenderer.renderAsync(player, plugin, skinRows -> {
            if (!player.isOnline()) return;

            player.sendMessage(Component.empty());

            for (int i = 0; i < skinRows.size(); i++) {
                // Left-indent the skin, then fixed gap to text
                Component line = Component.text(padding)
                        .append(skinRows.get(i));

                if (i < textLines.size() && !textLines.get(i).isEmpty()) {
                    // MatrixColorAPI outputs §-codes, so use legacySection to deserialize
                    String formatted = Utils.formatMessage(player, textLines.get(i));
                    Component text = LegacyComponentSerializer.legacySection()
                            .deserialize(formatted)
                            .decoration(TextDecoration.ITALIC, false);
                    line = line
                            .append(Component.text(padding))
                            .append(text);
                }

                player.sendMessage(line);
            }

            player.sendMessage(Component.empty());
        });
    }

    /**
     * Handles player quit events.
     * Saves and unloads the player's profile from cache.
     *
     * @param event The player quit event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitEvent(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();

        // Revoke rank permissions before the player fully disconnects
        RankManager.getInstance().revokePermissions(player);

        // Save enchant animation state first, while the player is still technically online
        EnchantSelectionManager.getInstance().handlePlayerQuit(player);

        // Save evolution animation state — must be called before profile unload
        EvolutionSelectionManager.getInstance().handlePlayerQuit(player);

        ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
        PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();

        if (armorDataCenter != null) {
            armorDataCenter.unloadProfile(player);
        }

        if (playerDataCenter != null) {
            playerDataCenter.unloadProfile(player);
        }

        // Clean up any stale per-player entries in static enchant maps.
        // These are normally consumed during block breaks but may linger if the player
        // disconnects mid-event or if a break was cancelled after NORMAL priority wrote them.
        java.util.UUID uuid = player.getUniqueId();

        // Clear any in-progress BreakContext captured at NORMAL priority but not yet
        // consumed at HIGHEST (player disconnected between the two event passes).
        var breakListener = ListenerRegistry.getInstance().get("break");
        if (breakListener instanceof BreakListener bl) {
            bl.clearPendingBreak(uuid);
        }

        EnchantDropManager.pop(uuid);
        ExtractionEnchant.popBonus(uuid);
        RadioactiveEnchant.popBonus(uuid);
        FortuneEnchant.popDropAmount(uuid);
        EfficiencyStoneHandler.getInstance().cleanup(uuid);
        RadioactiveStoneHandler.cleanup(uuid);
        FortuneStoneHandler.cleanup(uuid);
        FrostbiteStoneHandler.cleanup(uuid);
        FurnaceStoneHandler.cleanup(uuid);
        MerchantStoneHandler.cleanup(uuid);
        LazerStoneHandler.cleanup(uuid);
        UnbreakingStoneHandler.getInstance().cleanup(uuid);
        AutoSellStoneHandler.getInstance().cleanup(uuid);
        ExplosiveStoneHandler.cleanup(uuid);
        ExtractionStoneHandler.cleanup(uuid);
        ScavengerStoneHandler.cleanup(uuid);
        ThorStoneHandler.cleanup(uuid);
        VortexStoneHandler.cleanup(uuid);
    }
}
