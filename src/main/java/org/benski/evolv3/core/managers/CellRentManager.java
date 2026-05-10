package org.benski.evolv3.core.managers;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.helpers.Messageable;
import org.benski.evolv3.core.types.CellPermission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages active cell rent state: owner, expiry, members, member permissions, expand count.
 * Checks for expired rents every 60 seconds and triggers reset.
 */
public final class CellRentManager
{
    @Getter
    private static final CellRentManager instance = new CellRentManager();

    public record CellRent(
            String cellName,
            UUID ownerUuid,
            String ownerName,
            long rentExpiry,
            boolean locked,
            List<UUID> members,
            int expandCount,
            Map<UUID, Set<CellPermission>> memberPermissions
    ) {
        /** Returns the permissions for the given member, defaulting to all permissions. */
        public Set<CellPermission> getPermissions(UUID memberUuid) {
            return memberPermissions.getOrDefault(memberUuid, EnumSet.allOf(CellPermission.class));
        }

        public CellRent withLocked(boolean l) {
            return new CellRent(cellName, ownerUuid, ownerName, rentExpiry, l, members, expandCount, memberPermissions);
        }
        public CellRent withMembers(List<UUID> m) {
            return new CellRent(cellName, ownerUuid, ownerName, rentExpiry, locked, m, expandCount, memberPermissions);
        }
        public CellRent withExpandCount(int ec) {
            return new CellRent(cellName, ownerUuid, ownerName, rentExpiry, locked, members, ec, memberPermissions);
        }
        public CellRent withMemberPermissions(Map<UUID, Set<CellPermission>> perms) {
            return new CellRent(cellName, ownerUuid, ownerName, rentExpiry, locked, members, expandCount, perms);
        }
        public CellRent withRentExpiry(long expiry) {
            return new CellRent(cellName, ownerUuid, ownerName, expiry, locked, members, expandCount, memberPermissions);
        }

        public boolean isExpired() {
            return rentExpiry > 0 && System.currentTimeMillis() > rentExpiry;
        }

        public long daysRemaining() {
            if (rentExpiry <= 0) return -1;
            long ms = rentExpiry - System.currentTimeMillis();
            return Math.max(0, ms / 86_400_000L);
        }
    }

    private final Map<String, CellRent> rents      = new HashMap<>();
    private final Map<UUID, String>     playerCell  = new HashMap<>();
    private BukkitTask expiryTask;

    private CellRentManager() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void initialize() {
        loadFromDisk();
        startExpiryTask();
    }

    public void cleanup() {
        if (expiryTask != null) { expiryTask.cancel(); expiryTask = null; }
        rents.clear();
        playerCell.clear();
    }

    // =========================================================================
    // Rent operations
    // =========================================================================

    public boolean hasCell(@NotNull UUID playerUuid) {
        return playerCell.containsKey(playerUuid);
    }

    public @Nullable String getPlayerCellName(@NotNull UUID playerUuid) {
        return playerCell.get(playerUuid);
    }

    public @Nullable CellRent getRent(@NotNull String cellName) {
        return rents.get(cellName.toLowerCase());
    }

    public @Nullable CellRent getPlayerRent(@NotNull UUID playerUuid) {
        String name = playerCell.get(playerUuid);
        return name == null ? null : rents.get(name);
    }

    public boolean isRented(@NotNull String cellName) {
        return rents.containsKey(cellName.toLowerCase());
    }

    public boolean isOwner(@NotNull String cellName, @NotNull UUID playerUuid) {
        CellRent rent = rents.get(cellName.toLowerCase());
        return rent != null && rent.ownerUuid().equals(playerUuid);
    }

    public boolean isMember(@NotNull String cellName, @NotNull UUID playerUuid) {
        CellRent rent = rents.get(cellName.toLowerCase());
        if (rent == null) return false;
        return rent.ownerUuid().equals(playerUuid) || rent.members().contains(playerUuid);
    }

    public boolean hasMemberPermission(@NotNull String cellName, @NotNull UUID playerUuid,
                                        @NotNull CellPermission permission) {
        CellRent rent = rents.get(cellName.toLowerCase());
        if (rent == null) return false;
        if (rent.ownerUuid().equals(playerUuid)) return true;
        if (!rent.members().contains(playerUuid)) return false;
        return rent.getPermissions(playerUuid).contains(permission);
    }

    public @NotNull Collection<CellRent> getAll() {
        return Collections.unmodifiableCollection(rents.values());
    }

    public void createRent(@NotNull String cellName, @NotNull UUID ownerUuid,
                           @NotNull String ownerName, int days) {
        String key = cellName.toLowerCase();
        long expiry = days <= 0 ? -1L : System.currentTimeMillis() + (long) days * 86_400_000L;
        CellRent rent = new CellRent(key, ownerUuid, ownerName, expiry, false,
                new ArrayList<>(), 0, new HashMap<>());
        rents.put(key, rent);
        playerCell.put(ownerUuid, key);
        saveToDisk();
    }

    public void removeRent(@NotNull String cellName) {
        String key = cellName.toLowerCase();
        CellRent rent = rents.remove(key);
        if (rent != null) playerCell.remove(rent.ownerUuid());
        saveToDisk();
    }

    public void updateRent(@NotNull CellRent rent) {
        String key = rent.cellName().toLowerCase();
        rents.put(key, rent);
        playerCell.put(rent.ownerUuid(), key);
        saveToDisk();
    }

    // =========================================================================
    // Member management
    // =========================================================================

    public boolean addMember(@NotNull String cellName, @NotNull UUID memberUuid) {
        CellRent rent = rents.get(cellName.toLowerCase());
        if (rent == null || rent.members().contains(memberUuid)) return false;
        List<UUID> members = new ArrayList<>(rent.members());
        members.add(memberUuid);
        Map<UUID, Set<CellPermission>> perms = new HashMap<>(rent.memberPermissions());
        perms.put(memberUuid, EnumSet.allOf(CellPermission.class));
        updateRent(rent.withMembers(members).withMemberPermissions(perms));
        return true;
    }

    public boolean removeMember(@NotNull String cellName, @NotNull UUID memberUuid) {
        CellRent rent = rents.get(cellName.toLowerCase());
        if (rent == null || !rent.members().contains(memberUuid)) return false;
        List<UUID> members = new ArrayList<>(rent.members());
        members.remove(memberUuid);
        Map<UUID, Set<CellPermission>> perms = new HashMap<>(rent.memberPermissions());
        perms.remove(memberUuid);
        updateRent(rent.withMembers(members).withMemberPermissions(perms));
        return true;
    }

    public boolean setMemberPermission(@NotNull String cellName, @NotNull UUID memberUuid,
                                        @NotNull CellPermission permission, boolean grant) {
        CellRent rent = rents.get(cellName.toLowerCase());
        if (rent == null || !rent.members().contains(memberUuid)) return false;
        Map<UUID, Set<CellPermission>> perms = new HashMap<>(rent.memberPermissions());
        Set<CellPermission> memberPerms = new java.util.HashSet<>(
                perms.getOrDefault(memberUuid, EnumSet.allOf(CellPermission.class)));
        if (grant) memberPerms.add(permission);
        else memberPerms.remove(permission);
        perms.put(memberUuid, memberPerms);
        updateRent(rent.withMemberPermissions(perms));
        return true;
    }

    // =========================================================================
    // Expiry task
    // =========================================================================

    private void startExpiryTask() {
        if (expiryTask != null) return;
        expiryTask = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::checkExpiry, 1200L, 1200L);
    }

    private void checkExpiry() {
        List<String> expired = new ArrayList<>();
        for (CellRent rent : rents.values()) {
            if (rent.isExpired()) expired.add(rent.cellName());
        }
        for (String cellName : expired) {
            CellRent rent = rents.get(cellName);
            if (rent == null) continue;
            expireCell(rent);
        }
    }

    public void expireCell(@NotNull CellRent rent) {
        String cellName = rent.cellName();
        CellBlockBackupManager.getInstance().restore(cellName);
        CellWorldGuardManager.getInstance().deleteRegion(cellName);
        removeRent(cellName);
        CellHologramManager.getInstance().refresh(cellName);

        var player = Bukkit.getPlayer(rent.ownerUuid());
        if (player != null) {
            Messageable.sendError(player, "Your cell &#FFFFFF" + rent.cellName()
                    + " &chas expired and been reset.");
        }
        Bukkit.getLogger().info("[Evolv3] Cell '" + rent.cellName() + "' expired for " + rent.ownerName());
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    public void loadFromDisk() {
        rents.clear();
        playerCell.clear();
        File file = dataFile();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.isConfigurationSection("rents")) return;
        for (String key : cfg.getConfigurationSection("rents").getKeys(false)) {
            try {
                String p = "rents." + key;
                UUID ownerUuid = UUID.fromString(cfg.getString(p + ".owner-uuid", ""));
                List<UUID> members = new ArrayList<>();
                for (String m : cfg.getStringList(p + ".members")) {
                    try { members.add(UUID.fromString(m)); } catch (IllegalArgumentException ignored) {}
                }
                Map<UUID, Set<CellPermission>> memberPermissions = new HashMap<>();
                ConfigurationSection permsSection = cfg.getConfigurationSection(p + ".permissions");
                if (permsSection != null) {
                    for (String uuidStr : permsSection.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            Set<CellPermission> perms = new java.util.HashSet<>();
                            for (String permStr : cfg.getStringList(p + ".permissions." + uuidStr)) {
                                try { perms.add(CellPermission.valueOf(permStr)); }
                                catch (IllegalArgumentException ignored) {}
                            }
                            memberPermissions.put(uuid, perms);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                // Give default permissions to any member without explicit permissions
                for (UUID memberUuid : members) {
                    memberPermissions.computeIfAbsent(memberUuid, u -> EnumSet.allOf(CellPermission.class));
                }
                CellRent rent = new CellRent(
                        key,
                        ownerUuid,
                        cfg.getString(p + ".owner-name", ""),
                        cfg.getLong(p + ".rent-expiry", -1L),
                        cfg.getBoolean(p + ".locked", false),
                        members,
                        cfg.getInt(p + ".expand-count", 0),
                        memberPermissions
                );
                rents.put(key, rent);
                playerCell.put(ownerUuid, key);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Evolv3] Failed to load rent for cell '" + key + "': " + e.getMessage());
            }
        }
    }

    private void saveToDisk() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (CellRent rent : rents.values()) {
            String p = "rents." + rent.cellName();
            cfg.set(p + ".owner-uuid", rent.ownerUuid().toString());
            cfg.set(p + ".owner-name", rent.ownerName());
            cfg.set(p + ".rent-expiry", rent.rentExpiry());
            cfg.set(p + ".locked", rent.locked());
            cfg.set(p + ".members", rent.members().stream().map(UUID::toString).toList());
            cfg.set(p + ".expand-count", rent.expandCount());
            for (Map.Entry<UUID, Set<CellPermission>> entry : rent.memberPermissions().entrySet()) {
                List<String> permList = entry.getValue().stream().map(CellPermission::name).toList();
                cfg.set(p + ".permissions." + entry.getKey(), permList);
            }
        }
        try { cfg.save(dataFile()); }
        catch (IOException e) {
            Bukkit.getLogger().severe("[Evolv3] Failed to save cell-rents.yml: " + e.getMessage());
        }
    }

    private @NotNull File dataFile() {
        File dir = new File(Main.getInstance().getDataFolder(), "localdata");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cell-rents.yml");
    }
}
