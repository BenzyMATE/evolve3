package org.benski.evolv3.listeners.player;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.SkinRenderer;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantDropManager;
import org.benski.evolv3.core.managers.enchant.EnchantSelectionManager;
import org.benski.evolv3.core.managers.enchant.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.core.managers.player.RankManager;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.mining.ExplosiveStoneHandler;
import org.benski.evolv3.enchants.mining.FrostbiteStoneHandler;
import org.benski.evolv3.enchants.mining.LazerStoneHandler;
import org.benski.evolv3.enchants.mining.ThorStoneHandler;
import org.benski.evolv3.enchants.mining.VortexStoneHandler;
import org.benski.evolv3.enchants.passive.AutoSellStoneHandler;
import org.benski.evolv3.enchants.passive.EfficiencyStoneHandler;
import org.benski.evolv3.enchants.passive.ExtractionEnchant;
import org.benski.evolv3.enchants.passive.ExtractionStoneHandler;
import org.benski.evolv3.enchants.passive.FortuneEnchant;
import org.benski.evolv3.enchants.passive.FortuneStoneHandler;
import org.benski.evolv3.enchants.passive.FurnaceStoneHandler;
import org.benski.evolv3.enchants.passive.MerchantStoneHandler;
import org.benski.evolv3.enchants.passive.RadioactiveEnchant;
import org.benski.evolv3.enchants.passive.RadioactiveStoneHandler;
import org.benski.evolv3.enchants.passive.ScavengerStoneHandler;
import org.benski.evolv3.enchants.passive.UnbreakingStoneHandler;
import org.benski.evolv3.listeners.mine.BreakListener;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.benski.evolv3.registry.ListenerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Handles player join and quit events. Responsible for loading and unloading player profiles. */
public class JoinListener extends BaseListener {
  /**
   * Constructor for JoinListener.
   *
   * @param plugin The plugin instance.
   */
  public JoinListener(JavaPlugin plugin) {
    super(plugin);
  }

  /**
   * Handles player join events. Loads the player's profile from the database or creates a new one.
   *
   * @param event The player join event.
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onJoinEvent(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    String playerId = player.getUniqueId().toString();
    boolean firstJoin = !player.hasPlayedBefore();

    for (int i = 0; i < player.getInventory().getSize(); i++) {
      ItemStack item = player.getInventory().getItem(i);
      if (item == null || item.getType().isAir()) continue;
      ItemMeta im = item.getItemMeta();
      if (im != null
          && im.getPersistentDataContainer()
              .has(OmnitoolManager.OMNITOOL_ITEM_KEY, PersistentDataType.STRING)) {
        player.getInventory().setItem(i, null);
      }
    }

    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();

    if (playerDataCenter != null) {
      playerDataCenter.loadOrCreate(player, playerId);
    }

    if (armorDataCenter != null) {
      armorDataCenter.loadOrCreate(player, playerId);
    }

    EnchantSelectionManager.getInstance().handlePlayerJoin(player);

    EvolutionSelectionManager.getInstance().handlePlayerJoin(player);

    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (!player.isOnline()) return;
              PlayerProfile profile =
                  playerDataCenter != null ? playerDataCenter.getLocal(player) : null;
              if (profile != null) {
                RankManager.getInstance().applyPermissions(player, profile);
              }
            },
            2L);

    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (player.isOnline()) PickaxeManager.getInstance().updateLoreFromInventory(player);
            },
            5L);

    Bukkit.getScheduler().runTaskLater(plugin, () -> sendJoinMessage(player, firstJoin), 20L);
  }

  /** Sends join message. */
  private void sendJoinMessage(Player player, boolean firstJoin) {
    if (!player.isOnline()) return;

    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    if (!messages.getBoolean(ConfigPaths.Messages.JoinMessage.ENABLED, true)) return;

    String padding = messages.getString(ConfigPaths.Messages.JoinMessage.SKIN_PADDING, "  ");
    List<String> firstJoinLines =
        firstJoin
            ? messages.getStringList(ConfigPaths.Messages.JoinMessage.FIRST_JOIN_LINES)
            : List.of();
    List<String> textLines =
        !firstJoinLines.isEmpty()
            ? firstJoinLines
            : messages.getStringList(ConfigPaths.Messages.JoinMessage.LINES);

    SkinRenderer.renderAsync(
        player,
        plugin,
        skinRows -> {
          if (!player.isOnline()) return;

          player.sendMessage(Component.empty());

          for (int i = 0; i < skinRows.size(); i++) {
            Component line = Component.text(padding).append(skinRows.get(i));

            if (i < textLines.size() && !textLines.get(i).isEmpty()) {
              String formatted = Utils.formatMessage(player, textLines.get(i));
              Component text =
                  LegacyComponentSerializer.legacySection()
                      .deserialize(formatted)
                      .decoration(TextDecoration.ITALIC, false);
              line = line.append(Component.text(padding)).append(text);
            }

            player.sendMessage(line);
          }

          player.sendMessage(Component.empty());
        });
  }

  /**
   * Handles player quit events. Saves and unloads the player's profile from cache.
   *
   * @param event The player quit event.
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onQuitEvent(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    RankManager.getInstance().revokePermissions(player);

    EnchantSelectionManager.getInstance().handlePlayerQuit(player);

    EvolutionSelectionManager.getInstance().handlePlayerQuit(player);

    ArmorDataCenter armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();

    if (armorDataCenter != null) {
      armorDataCenter.unloadProfile(player);
    }

    if (playerDataCenter != null) {
      playerDataCenter.unloadProfile(player);
    }

    java.util.UUID uuid = player.getUniqueId();

    var breakListener = ListenerRegistry.getInstance().get("break");
    if (breakListener instanceof BreakListener bl) {
      bl.clearPendingBreak(uuid);
    }

    PickaxeManager.getInstance().cleanupPlayer(uuid);

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
