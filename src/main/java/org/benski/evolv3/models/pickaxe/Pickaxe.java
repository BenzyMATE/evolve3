package org.benski.evolv3.models.pickaxe;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.types.VesselType;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Represents a player-owned pickaxe. Embedded within {@link PlayerProfile}. */
public final class Pickaxe {

  private static final Logger LOGGER = Main.getInstance().getLogger();

  public String key;
  public String name;
  public UUID ownerUuid;
  public int level;
  public int xp;
  public int target;
  public int evolution;
  public Vessel essenceVessel = new Vessel(VesselType.ESSENCE, 0, 100, 0);
  public Vessel atomVessel = new Vessel(VesselType.ATOMS, 0, 100, 0);
  public PickaxeEnchants enchants = new PickaxeEnchants();
  public int prestige = 0;
  public int durability = 0;

  /**
   * True once the pickaxe has taken enough damage that it would vanilla-break. Instead of being
   * destroyed, it becomes unusable for mining until repaired via {@link
   * org.benski.evolv3.core.managers.pickaxe.PickaxeManager#repairPickaxe}.
   */
  public boolean disabled = false;

  /** Creates a new pickaxe with default values from configuration. */
  public static Pickaxe createDefault(Player player, String id) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    Pickaxe pickaxe = new Pickaxe();

    pickaxe.key = id;
    pickaxe.ownerUuid = player.getUniqueId();
    pickaxe.name = config.getString(ConfigPaths.Default.Pickaxe.NAME, "&bPickaxe");
    pickaxe.level = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_LEVEL, 1);
    pickaxe.xp = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_XP, 0);
    pickaxe.target = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_TARGET, 100);
    pickaxe.evolution = 1;

    int essenceTarget = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ESSENCE_TARGET, 100);
    int enchantCrystals = config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ENCHANT_ROLLS, 0);
    pickaxe.essenceVessel =
        new Vessel(
            VesselType.ESSENCE,
            config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_CURRENT_ESSENCE, 0),
            essenceTarget,
            enchantCrystals);

    int atomTarget = config.getInt(ConfigPaths.Default.Atoms.DEFAULT_ATOM_TARGET, 100);
    pickaxe.atomVessel =
        new Vessel(
            VesselType.ATOMS,
            config.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_EVOLUTION_ATOMS, 0),
            atomTarget,
            0);

    pickaxe.enchants = new PickaxeEnchants();
    return pickaxe;
  }

  /** Returns the online player who owns this pickaxe, or null if they are offline. */
  public Player getOwner() {
    return Bukkit.getPlayer(ownerUuid);
  }

  /** Returns a formatted progress bar string for the current XP vs target. */
  public String getProgressBar() {
    return Utils.generateProgressBar(this.xp, this.target);
  }

  /** Returns the current XP progress as a percentage string (e.g. "42%"). */
  public String getPercentage() {
    if (target <= 0) return "0%";
    return Math.round((double) xp / target * 100) + "%";
  }

  /** Returns the current XP progress as a value between 0.0 and 1.0. */
  public double getProgress() {
    if (target <= 0) return 0.0;
    return (double) xp / target;
  }

  /**
   * Upserts this pickaxe into its parent profile's in-memory list. The actual API persist happens
   * on player quit or server shutdown. The callback is invoked immediately with {@code true}.
   *
   * @param callback receives {@code true} on success, {@code false} if the parent profile is not
   *     cached; may be {@code null}
   */
  public void save(Consumer<Boolean> callback) {
    var pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (pdc == null) {
      LOGGER.warning("Pickaxe.save: PlayerDataCenter not available");
      if (callback != null) callback.accept(false);
      return;
    }
    PlayerProfile parent = pdc.getLocalById(ownerUuid.toString());
    if (parent == null) {
      LOGGER.warning("Pickaxe.save: parent PlayerProfile not cached for " + ownerUuid);
      if (callback != null) callback.accept(false);
      return;
    }
    parent.upsertPickaxe(this);
    if (callback != null) callback.accept(true);
  }

  /** Convenience overload of {@link #save(java.util.function.Consumer)} with no callback. */
  public void save() {
    save(null);
  }
}
