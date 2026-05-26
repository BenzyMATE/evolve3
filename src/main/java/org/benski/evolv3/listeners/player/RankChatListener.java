package org.benski.evolv3.listeners.player;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.managers.player.RankManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Formats chat messages with the player's prison rank (and staff rank if set).
 *
 * <p>Chat format: {@code [StaffRank] [PrisonRank] §7PlayerName§8: message}
 *
 * <p>Staff rank players use white names; regular prison-rank players use gray.
 *
 * <p>If no staff rank is set, the staff prefix is omitted.
 */
public class RankChatListener extends BaseListener {
  /** Creates and registers the RankChatListener. @param plugin the owning plugin */
  public RankChatListener(JavaPlugin plugin) {
    super(plugin);
  }

  /** Called when chat occurs. */
  @EventHandler(priority = EventPriority.HIGH)
  public void onChat(AsyncChatEvent event) {
    Player player = event.getPlayer();

    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return;

    PlayerProfile profile = dc.getLocal(player);
    if (profile == null) return;

    RankManager rankManager = RankManager.getInstance();
    String staffRankId = profile.getStaffRank();
    boolean hasStaffRank = staffRankId != null && !staffRankId.isEmpty();

    Component prefixComponent = Component.empty();

    if (hasStaffRank) {
      String staffPrefixRaw = rankManager.getStaffPrefix(staffRankId);
      Component staffComponent =
          LegacyComponentSerializer.legacySection()
              .deserialize(MatrixColorAPI.process(staffPrefixRaw));
      prefixComponent = prefixComponent.append(staffComponent).append(Component.text(" "));
    }

    String rankPrefixRaw =
        rankManager.getRankPrefix(
            profile.getRank() != null ? profile.getRank() : rankManager.getDefaultRank());
    Component rankComponent =
        LegacyComponentSerializer.legacySection()
            .deserialize(MatrixColorAPI.process(rankPrefixRaw));
    prefixComponent = prefixComponent.append(rankComponent);

    NamedTextColor nameColor = hasStaffRank ? NamedTextColor.WHITE : NamedTextColor.GRAY;
    Component nameComponent = Component.text(player.getName(), nameColor);

    final Component finalPrefix = prefixComponent;
    final Component finalName = nameComponent;

    event.renderer(
        (source, sourceDisplayName, message, viewer) ->
            finalPrefix
                .append(Component.text(" "))
                .append(finalName)
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(message));
  }
}
