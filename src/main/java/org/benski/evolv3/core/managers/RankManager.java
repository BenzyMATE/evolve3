package org.benski.evolv3.core.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the prison rank system.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Apply and revoke Bukkit {@link PermissionAttachment}s per player session.
 *   <li>Handle rankup validation (coin cost, progression order).
 *   <li>Provide formatted rank prefixes for chat and scoreboard.
 *   <li>Admin set-rank and set-staff operations.
 * </ul>
 *
 * <p>All rank data is read from {@code ranks.yml} via {@link StorageRegistryManager}. Permissions
 * are reapplied on every join and revoked on every quit.
 */
public final class RankManager {
  @Getter private static final RankManager instance = new RankManager();

  /** Bukkit PermissionAttachments keyed by player UUID â€” one per online player. */
  private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

  private RankManager() {}

  /**
   * Applies the player's rank (and staff rank) permissions via a fresh {@link
   * PermissionAttachment}. Any previous attachment is revoked first.
   *
   * @param player the online player
   * @param profile their loaded profile
   */
  public void applyPermissions(@NotNull Player player, @NotNull PlayerProfile profile) {
    revokePermissions(player);

    PermissionAttachment attachment = player.addAttachment(org.benski.evolv3.Main.getInstance());
    attachments.put(player.getUniqueId(), attachment);

    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);

    String rankId = profile.getRank() != null ? profile.getRank() : getDefaultRank(cfg);
    applyRankPermissions(attachment, cfg, rankId);

    String staffRankId = profile.getStaffRank();
    if (staffRankId != null && !staffRankId.isEmpty()) {
      applyStaffPermissions(attachment, cfg, staffRankId);
    }
  }

  /**
   * Removes the player's {@link PermissionAttachment}, reverting all dynamically granted
   * permissions. Call this on player quit.
   *
   * @param player the player to revoke permissions for
   */
  public void revokePermissions(@NotNull Player player) {
    PermissionAttachment old = attachments.remove(player.getUniqueId());
    if (old != null) {
      try {
        player.removeAttachment(old);
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Attempts to rank up the player one step.
   *
   * <p>Validates that the player is not at max rank and has enough coins. On success, deducts the
   * coin cost, advances the rank, reapplies permissions, saves the profile, and notifies the
   * player.
   *
   * @param player the player ranking up
   * @param profile their loaded profile
   */
  public void rankUp(@NotNull Player player, @NotNull PlayerProfile profile) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    List<String> order = cfg.getStringList(ConfigPaths.Ranks.ORDER);

    String currentRank = profile.getRank() != null ? profile.getRank() : getDefaultRank(cfg);
    int currentIndex = order.indexOf(currentRank);

    if (currentIndex == -1 || currentIndex >= order.size() - 1) {
      String msg =
          cfg.getString(
              ConfigPaths.Ranks.Messages.RANKUP_AT_MAX,
              "&#FFAA00You are already at the highest rank!");
      player.sendMessage(
          LegacyComponentSerializer.legacySection().deserialize(MatrixColorAPI.process(msg)));
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
      return;
    }

    String nextRank = order.get(currentIndex + 1);
    double cost =
        cfg.getDouble(ConfigPaths.Ranks.DATA_PREFIX + nextRank + ConfigPaths.Ranks.COST_SUFFIX, 0);

    if (!profile.removeCoins(cost)) {
      String prefix = MatrixColorAPI.process(getRankPrefix(cfg, nextRank));
      String msg =
          cfg.getString(
                  ConfigPaths.Ranks.Messages.RANKUP_INSUFFICIENT,
                  "&#FF5555&lInsufficient Coins! &#CCCCCCYou need &#FFD700&l%cost% &#FFD700â› &#CCCCCCto rank up to %rank%&#CCCCCC.")
              .replace("%cost%", formatCost(cost))
              .replace("%rank%", prefix);
      player.sendMessage(
          LegacyComponentSerializer.legacySection().deserialize(MatrixColorAPI.process(msg)));
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
      return;
    }

    profile.setRank(nextRank);
    applyPermissions(player, profile);
    profile.save(null);
    BorderManager.getInstance().updatePlayerBorder(player, nextRank);
    EvolveScoreboardManager.getInstance().resetRankAnimation(player);

    String prefix = MatrixColorAPI.process(getRankPrefix(cfg, nextRank));
    String msg =
        cfg.getString(
                ConfigPaths.Ranks.Messages.RANKUP_SUCCESS,
                "&#55FF55&lRANK UP! &#CCCCCCYou are now rank %rank%&#CCCCCC!")
            .replace("%rank%", prefix);
    player.sendMessage(
        LegacyComponentSerializer.legacySection().deserialize(MatrixColorAPI.process(msg)));
    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
  }

  /**
   * Sets a target player's prison rank directly (admin command).
   *
   * @param target the player whose rank is being set
   * @param profile their loaded profile
   * @param rankId the rank id to assign (e.g. "C2")
   * @param admin the admin performing the action (receives confirmation)
   */
  public void setRank(
      @NotNull Player target,
      @NotNull PlayerProfile profile,
      @NotNull String rankId,
      @NotNull Player admin) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    List<String> order = cfg.getStringList(ConfigPaths.Ranks.ORDER);

    if (!order.contains(rankId)) {
      admin.sendMessage(
          LegacyComponentSerializer.legacySection()
              .deserialize(MatrixColorAPI.process("&#FF5555Unknown rank: &#FFFFFF" + rankId)));
      return;
    }

    profile.setRank(rankId);
    applyPermissions(target, profile);
    profile.save(null);
    if (target.isOnline()) {
      BorderManager.getInstance().updatePlayerBorder(target, rankId);
    }

    String prefix = MatrixColorAPI.process(getRankPrefix(cfg, rankId));

    String adminMsg =
        cfg.getString(
                ConfigPaths.Ranks.Messages.SET_RANK_SUCCESS,
                "&#55FF55Set &#FFFFFF%player%&#55FF55's rank to %rank%&#55FF55.")
            .replace("%player%", target.getName())
            .replace("%rank%", prefix);
    admin.sendMessage(
        LegacyComponentSerializer.legacySection().deserialize(MatrixColorAPI.process(adminMsg)));
    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);

    if (target.isOnline()) {
      String playerMsg =
          cfg.getString(
                  ConfigPaths.Ranks.Messages.SET_RANK_PLAYER_NOTIFIED,
                  "&#55FF55Your rank has been set to %rank% &#55FF55by an admin.")
              .replace("%rank%", prefix);
      target.sendMessage(
          LegacyComponentSerializer.legacySection().deserialize(MatrixColorAPI.process(playerMsg)));
      target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }
  }

  /**
   * Sets a target player's staff rank (admin command). Pass {@code null} to clear the staff rank.
   *
   * @param target the player whose staff rank is being changed
   * @param profile their loaded profile
   * @param rankId the staff rank id (e.g. "admin"), or null to clear
   * @param admin the admin performing the action
   */
  public void setStaffRank(
      @NotNull Player target,
      @NotNull PlayerProfile profile,
      @Nullable String rankId,
      @NotNull Player admin) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);

    if (rankId != null) {
      List<String> staffOrder = cfg.getStringList(ConfigPaths.Ranks.STAFF_ORDER);
      if (!staffOrder.contains(rankId)) {
        admin.sendMessage(
            LegacyComponentSerializer.legacySection()
                .deserialize(
                    MatrixColorAPI.process("&#FF5555Unknown staff rank: &#FFFFFF" + rankId)));
        return;
      }
    }

    profile.setStaffRank(rankId);
    applyPermissions(target, profile);
    profile.save(null);

    if (rankId != null) {
      String staffPrefix = MatrixColorAPI.process(getStaffPrefix(cfg, rankId));

      String adminMsg =
          cfg.getString(
                  ConfigPaths.Ranks.Messages.SET_STAFF_SUCCESS,
                  "&#55FF55Set &#FFFFFF%player%&#55FF55's staff rank to &#FFFFFF%rank%&#55FF55.")
              .replace("%player%", target.getName())
              .replace("%rank%", staffPrefix);
      admin.sendMessage(
          LegacyComponentSerializer.legacySection().deserialize(MatrixColorAPI.process(adminMsg)));
      admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);

      if (target.isOnline()) {
        String playerMsg =
            cfg.getString(
                    ConfigPaths.Ranks.Messages.SET_STAFF_PLAYER_NOTIFIED,
                    "&#55FF55Your staff rank has been set to &#FFFFFF%rank%&#55FF55 by an admin.")
                .replace("%rank%", staffPrefix);
        target.sendMessage(
            LegacyComponentSerializer.legacySection()
                .deserialize(MatrixColorAPI.process(playerMsg)));
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
      }
    } else {
      String adminMsg =
          cfg.getString(
                  ConfigPaths.Ranks.Messages.SET_STAFF_CLEARED,
                  "&#55FF55Cleared &#FFFFFF%player%&#55FF55's staff rank.")
              .replace("%player%", target.getName());
      admin.sendMessage(
          LegacyComponentSerializer.legacySection().deserialize(MatrixColorAPI.process(adminMsg)));
      admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);

      if (target.isOnline()) {
        String playerMsg =
            cfg.getString(
                ConfigPaths.Ranks.Messages.SET_STAFF_PLAYER_CLEARED,
                "&#CCCCCCYour staff rank has been removed by an admin.");
        target.sendMessage(
            LegacyComponentSerializer.legacySection()
                .deserialize(MatrixColorAPI.process(playerMsg)));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
      }
    }
  }

  /**
   * Returns the formatted rank prefix for the given rank id. Falls back to the raw rank id if not
   * configured.
   *
   * @param rankId the rank id (e.g. "A1")
   * @return the formatted prefix string (e.g. "&#AAAAAA[A1]")
   */
  @NotNull
  public String getRankPrefix(@NotNull String rankId) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    return getRankPrefix(cfg, rankId);
  }

  /**
   * Returns the formatted staff rank prefix for the given staff rank id. Falls back to the raw rank
   * id if not configured.
   *
   * @param staffRankId the staff rank id (e.g. "admin")
   * @return the formatted prefix string (e.g. "&#FF5555[Admin]")
   */
  @NotNull
  public String getStaffPrefix(@NotNull String staffRankId) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    return getStaffPrefix(cfg, staffRankId);
  }

  /**
   * Returns the next rank id in progression after the given rank, or {@code null} if already at the
   * maximum rank.
   *
   * @param rankId the current rank id
   * @return the next rank id, or null if at max
   */
  @Nullable
  public String getNextRank(@NotNull String rankId) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    List<String> order = cfg.getStringList(ConfigPaths.Ranks.ORDER);
    int idx = order.indexOf(rankId);
    if (idx == -1 || idx >= order.size() - 1) return null;
    return order.get(idx + 1);
  }

  /** Returns the default starting rank id as configured, or "A1" as a hard fallback. */
  @NotNull
  public String getDefaultRank() {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    return getDefaultRank(cfg);
  }

  /**
   * Adds a permission node to a rank, persists the change to {@code ranks.yml}, and immediately
   * reapplies permissions for all online players of that rank.
   *
   * @param rankId the rank id (e.g. "A1" or "admin")
   * @param isStaff true if this is a staff rank
   * @param permission the permission node to add
   * @return true if the permission was added; false if it was already present
   */
  public boolean addPermission(
      @NotNull String rankId, boolean isStaff, @NotNull String permission) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    String path = permissionsPath(rankId, isStaff);

    List<String> perms = new ArrayList<>(cfg.getStringList(path));
    if (perms.contains(permission)) return false;

    perms.add(permission);
    cfg.set(path, perms);
    StorageRegistryManager.getInstance().saveConfig(ConfigNames.RANKS);
    reapplyPermissionsForRank(rankId, isStaff);
    return true;
  }

  /**
   * Removes a permission node from a rank, persists the change to {@code ranks.yml}, and
   * immediately reapplies permissions for all online players of that rank.
   *
   * @param rankId the rank id (e.g. "A1" or "admin")
   * @param isStaff true if this is a staff rank
   * @param permission the permission node to remove
   * @return true if the permission was removed; false if it was not present
   */
  public boolean removePermission(
      @NotNull String rankId, boolean isStaff, @NotNull String permission) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    String path = permissionsPath(rankId, isStaff);

    List<String> perms = new ArrayList<>(cfg.getStringList(path));
    if (!perms.remove(permission)) return false;

    cfg.set(path, perms);
    StorageRegistryManager.getInstance().saveConfig(ConfigNames.RANKS);
    reapplyPermissionsForRank(rankId, isStaff);
    return true;
  }

  /**
   * Returns the current permission list for the given rank.
   *
   * @param rankId the rank id
   * @param isStaff true if this is a staff rank
   * @return unmodifiable list of permission nodes
   */
  @NotNull
  public List<String> getPermissions(@NotNull String rankId, boolean isStaff) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    return List.copyOf(cfg.getStringList(permissionsPath(rankId, isStaff)));
  }

  /** Returns true if {@code rankId} exists in the prison rank order list. */
  public boolean isPrisonRank(@NotNull String rankId) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    return cfg.getStringList(ConfigPaths.Ranks.ORDER).contains(rankId);
  }

  /** Returns true if {@code rankId} exists in the staff rank order list. */
  public boolean isStaffRank(@NotNull String rankId) {
    FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    return cfg.getStringList(ConfigPaths.Ranks.STAFF_ORDER).contains(rankId);
  }

  private void applyRankPermissions(
      PermissionAttachment attachment, FileConfiguration cfg, String rankId) {
    List<String> permissions =
        cfg.getStringList(
            ConfigPaths.Ranks.DATA_PREFIX + rankId + ConfigPaths.Ranks.PERMISSIONS_SUFFIX);
    for (String perm : permissions) {
      if (perm != null && !perm.isEmpty()) {
        attachment.setPermission(perm, true);
      }
    }
  }

  private void applyStaffPermissions(
      PermissionAttachment attachment, FileConfiguration cfg, String staffRankId) {
    List<String> permissions =
        cfg.getStringList(
            ConfigPaths.Ranks.STAFF_DATA_PREFIX
                + staffRankId
                + ConfigPaths.Ranks.PERMISSIONS_SUFFIX);
    for (String perm : permissions) {
      if (perm == null || perm.isEmpty()) continue;
      if ("*".equals(perm)) {
        attachment.setPermission("*", true);
      } else {
        attachment.setPermission(perm, true);
      }
    }
  }

  private String getRankPrefix(FileConfiguration cfg, String rankId) {
    return cfg.getString(
        ConfigPaths.Ranks.DATA_PREFIX + rankId + ConfigPaths.Ranks.PREFIX_SUFFIX, rankId);
  }

  private String getStaffPrefix(FileConfiguration cfg, String staffRankId) {
    return cfg.getString(
        ConfigPaths.Ranks.STAFF_DATA_PREFIX + staffRankId + ConfigPaths.Ranks.PREFIX_SUFFIX,
        staffRankId);
  }

  private String getDefaultRank(FileConfiguration cfg) {
    return cfg.getString(ConfigPaths.Ranks.DEFAULT_RANK, "A1");
  }

  private String permissionsPath(String rankId, boolean isStaff) {
    return (isStaff ? ConfigPaths.Ranks.STAFF_DATA_PREFIX : ConfigPaths.Ranks.DATA_PREFIX)
        + rankId
        + ConfigPaths.Ranks.PERMISSIONS_SUFFIX;
  }

  /**
   * Re-applies permissions for every online player whose rank matches {@code rankId}. Called after
   * any in-game permission add/remove so changes take effect immediately.
   */
  private void reapplyPermissionsForRank(@NotNull String rankId, boolean isStaff) {
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return;

    for (Player online : Bukkit.getOnlinePlayers()) {
      PlayerProfile profile = dc.getLocal(online);
      if (profile == null) continue;

      boolean affected =
          isStaff
              ? rankId.equalsIgnoreCase(profile.getStaffRank())
              : rankId.equalsIgnoreCase(profile.getRank());

      if (affected) applyPermissions(online, profile);
    }
  }

  /** Formats a coin cost for display (e.g. 1500000 â†’ "1,500,000"). */
  private String formatCost(double cost) {
    if (cost == Math.floor(cost) && cost <= Long.MAX_VALUE) {
      return String.format("%,.0f", cost);
    }
    return String.format("%,.2f", cost);
  }
}
