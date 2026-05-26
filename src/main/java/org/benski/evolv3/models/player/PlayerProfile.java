package org.benski.evolv3.models.player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.PlayerActivity;
import org.benski.evolv3.core.types.RankType;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Root aggregate for all player data. Single source of truth — loaded once on join via API. */
public final class PlayerProfile {

  public String id;
  public UUID playerUuid;
  public PlayerActivity activity = PlayerActivity.NONE;
  public Wallet wallet = new Wallet();
  public PlayerPreferences preferences = new PlayerPreferences();
  public Backpack backpack = new Backpack();
  public List<String> storedPickaxes = new ArrayList<>();
  public List<Rank> ranks = new ArrayList<>();

  /** All pickaxes owned by this player (embedded in the API document). */
  public List<Pickaxe> pickaxes = new ArrayList<>();

  /** Creates a new profile for the given player using default values from {@code default.yml}. */
  public static PlayerProfile createDefault(Player player) {
    PlayerProfile profile = new PlayerProfile();
    profile.initialize(player);

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    profile.activity = PlayerActivity.NONE;
    profile.wallet =
        new Wallet(
            config.getDouble(ConfigPaths.Default.Player.DEFAULT_COINS, 0.0),
            config.getInt(ConfigPaths.Default.Player.DEFAULT_ESSENCE, 0));
    profile.preferences = new PlayerPreferences();
    profile.backpack = Backpack.createDefault();
    profile.storedPickaxes = new ArrayList<>();
    for (int i = 0; i < 9; i++) profile.storedPickaxes.add("");

    String defaultRank = config.getString(ConfigPaths.Ranks.DEFAULT_RANK, "A1");
    profile.ranks = new ArrayList<>();
    profile.ranks.add(new Rank(defaultRank, RankType.DEFAULT));

    return profile;
  }

  /** Binds this profile to a player by setting their UUID and string ID. */
  public void initialize(Player player) {
    this.playerUuid = player.getUniqueId();
    this.id = player.getUniqueId().toString();
  }

  /** Returns the online player for this profile, or null if they are offline. */
  public Player getPlayer() {
    return Bukkit.getPlayer(playerUuid);
  }

  /**
   * Returns the highest evolution level across all of this player's pickaxes, defaulting to {@code
   * 1} when no pickaxes exist.
   */
  public int getHighestEvolution() {
    return pickaxes.stream().mapToInt(p -> p.evolution).max().orElse(1);
  }

  /**
   * Returns the maximum number of pickaxes this player may own, sourced from {@code
   * Default.Player.PickaxeLimit} in {@code default.yml}.
   */
  public int getPickaxeLimit() {
    return ConfigRegistry.getInstance()
        .get(ConfigNames.DEFAULT)
        .getInt(ConfigPaths.Default.Player.DEFAULT_PICKAXE_LIMIT, 1);
  }

  /** Returns {@code true} if the player may create another pickaxe given their current count. */
  public boolean canCreatePickaxe(int currentPickaxeCount) {
    return currentPickaxeCount < getPickaxeLimit();
  }

  /** Finds a pickaxe by its UUID key, or {@code null} if this player does not own it. */
  @Nullable
  public Pickaxe getPickaxe(String pickaxeId) {
    if (pickaxeId == null || pickaxeId.isEmpty()) return null;
    for (Pickaxe p : pickaxes) {
      if (pickaxeId.equals(p.key)) return p;
    }
    return null;
  }

  /** Adds or replaces a pickaxe in the embedded list. */
  public void upsertPickaxe(Pickaxe pickaxe) {
    pickaxes.removeIf(p -> pickaxe.key.equals(p.key));
    pickaxes.add(pickaxe);
  }

  /** Removes the pickaxe with the given key from the embedded list. */
  public void removePickaxe(String pickaxeId) {
    pickaxes.removeIf(p -> pickaxeId.equals(p.key));
  }

  /** Returns the player's current default rank prefix, or null if none is set. */
  @Nullable
  public String getRank() {
    return ranks.stream()
        .filter(r -> r.type == RankType.DEFAULT)
        .map(r -> r.prefix)
        .findFirst()
        .orElse(null);
  }

  /** Replaces the player's default rank with the given prefix, or removes it if null/empty. */
  public void setRank(String prefix) {
    ranks.removeIf(r -> r.type == RankType.DEFAULT);
    if (prefix != null && !prefix.isEmpty()) ranks.add(new Rank(prefix, RankType.DEFAULT));
  }

  /** Returns the player's staff rank prefix, or null if they have no staff rank. */
  @Nullable
  public String getStaffRank() {
    return ranks.stream()
        .filter(r -> r.type == RankType.STAFF)
        .map(r -> r.prefix)
        .findFirst()
        .orElse(null);
  }

  /** Replaces the player's staff rank with the given prefix, or removes it if null/empty. */
  public void setStaffRank(@Nullable String prefix) {
    ranks.removeIf(r -> r.type == RankType.STAFF);
    if (prefix != null && !prefix.isEmpty()) ranks.add(new Rank(prefix, RankType.STAFF));
  }

  /** Returns {@code true} if the player is currently in the enchanting activity state. */
  public boolean isEnchanting() {
    return activity == PlayerActivity.ENCHANTING;
  }

  /** Returns {@code true} if the player is currently in the evolving activity state. */
  public boolean isEvolving() {
    return activity == PlayerActivity.EVOLVING;
  }

  /**
   * Marks this profile as modified. The callback is invoked immediately with {@code true} since
   * in-memory state is authoritative. The actual API persist happens on player quit or server
   * shutdown.
   *
   * @param callback receives {@code true}; may be {@code null}
   */
  public void save(Consumer<Boolean> callback) {
    if (callback != null) callback.accept(true);
  }

  /** No-op — profile is persisted on player quit or server shutdown. */
  public void save() {}

  /** Adds the given amount to the player's essence balance. */
  public void addEssence(int amount) {
    this.wallet.essence += amount;
  }

  /** Deducts essence if the player has enough. Returns {@code false} if insufficient. */
  public boolean removeEssence(int amount) {
    if (this.wallet.essence < amount) return false;
    this.wallet.essence -= amount;
    return true;
  }

  /** Returns {@code true} if the player has at least {@code amount} essence. */
  public boolean hasEssence(int amount) {
    return this.wallet.essence >= amount;
  }

  /** Adds the given amount to the player's coin balance. */
  public void addCoins(double amount) {
    this.wallet.coins += amount;
  }

  /** Deducts coins if the player has enough. Returns {@code false} if insufficient. */
  public boolean removeCoins(double amount) {
    if (this.wallet.coins < amount) return false;
    this.wallet.coins -= amount;
    return true;
  }
}
