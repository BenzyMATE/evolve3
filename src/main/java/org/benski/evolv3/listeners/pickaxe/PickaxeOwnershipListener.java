package org.benski.evolv3.listeners.pickaxe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager;
import org.benski.evolv3.core.managers.pickaxe.PickaxeManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.AncientResin;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Handles pickaxe ownership lifecycle events: - Break: removes the pickaxe from the player's
 * storage slot. - Drop (two-step): first Q-press cancels and sends a chat message; second Q-press
 * releases ownership. - Pickup: transfers ownership to the new player. - ItemSpawn/Despawn:
 * attaches and cleans up a floating TextDisplay that tracks the item as it moves. - ItemDamage:
 * warns the player when pickaxe durability falls below the configured threshold, or disables it
 * outright if the hit would have broken it. - InventoryClick: repairs a pickaxe when Ancient Resin
 * is dragged onto it in the player's own inventory.
 */
@ExtensionMethod(ItemStackExtensions.class)
public class PickaxeOwnershipListener extends BaseListener {
  /** Maps dropped Item entity UUID → floating TextDisplay entity. */
  private final Map<UUID, TextDisplay> itemToDisplay = new ConcurrentHashMap<>();

  /** Maps dropped Item entity UUID → position-tracking task. */
  private final Map<UUID, BukkitTask> itemToTask = new ConcurrentHashMap<>();

  /** Two-step drop: player UUID → confirmation expiry time (ms). */
  private final Map<UUID, Long> dropConfirmationExpiry = new ConcurrentHashMap<>();

  /** Players whose NEXT pickaxe-drop event should release ownership and be allowed through. */
  private final Set<UUID> confirmedDropPlayers = ConcurrentHashMap.newKeySet();

  /** Last durability-warning time per player UUID, to avoid spamming. */
  private final Map<UUID, Long> lastDurabilityWarning = new ConcurrentHashMap<>();

  /** Cooldown for the "no storage slot" pickup-denied message to prevent spam. */
  private final Map<UUID, Long> pickupDeniedCooldown = new ConcurrentHashMap<>();

  private static final long PICKUP_DENIED_COOLDOWN_MS = 3_000;

  /** Minimum ms between repeated durability warnings for the same player. */
  private static final long DURABILITY_WARNING_COOLDOWN_MS = 30_000;

  /** Drop confirmation window in ticks (5 seconds = 100 ticks). */
  private static final long DROP_CONFIRM_TICKS = 100L;

  /** Scale of the floating TextDisplay. */
  private static final float DISPLAY_SCALE = 0.45f;

  /** Y-offset above the item entity's server-side location for the TextDisplay. */
  private static final double DISPLAY_HEIGHT = 0.65;

  /** Creates and registers the PickaxeOwnershipListener. @param plugin the owning plugin */
  public PickaxeOwnershipListener(JavaPlugin plugin) {
    super(plugin);
  }

  /**
   * When a pickaxe breaks, remove it from the player's storage and evict it from the in-memory
   * cache.
   */
  @EventHandler
  public void onPickaxeBreak(PlayerItemBreakEvent event) {
    Player player = event.getPlayer();
    String pickaxeId = event.getBrokenItem().getData("id");
    if (pickaxeId == null || pickaxeId.isEmpty()) return;

    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter == null) return;

    PlayerProfile playerProfile = playerDataCenter.getLocal(player);
    if (playerProfile == null) return;

    if (removeFromStoredPickaxes(playerProfile, pickaxeId)) {
      playerProfile.save(null);
    }

    PlayerProfile owner = playerDataCenter.getPlayerOwningPickaxe(pickaxeId);
    if (owner != null) {
      owner.removePickaxe(pickaxeId);
    }

    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    String msg = messages.getString(ConfigPaths.Messages.Pickaxe.PICKAXE_BROKEN, "");
    if (!msg.isEmpty()) {
      player.sendMessage(Utils.formatMessage(player, msg));
    }
  }

  /**
   * First Q-press: cancel the drop, send a chat message prompting confirmation. Second Q-press
   * (within 5 s): release ownership and allow the drop.
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void onPickaxeDrop(PlayerDropItemEvent event) {
    ItemStack dropped = event.getItemDrop().getItemStack();
    Player player = event.getPlayer();
    UUID playerUuid = player.getUniqueId();

    boolean isOmnitool =
        dropped.getItemMeta() != null
            && dropped
                .getItemMeta()
                .getPersistentDataContainer()
                .has(
                    OmnitoolManager.OMNITOOL_ITEM_KEY,
                    org.bukkit.persistence.PersistentDataType.STRING);

    if (isOmnitool) {
      if (confirmedDropPlayers.remove(playerUuid)) {
        dropConfirmationExpiry.remove(playerUuid);
        return;
      }
      event.setCancelled(true);
      dropConfirmationExpiry.put(playerUuid, System.currentTimeMillis() + 5_000);
      confirmedDropPlayers.add(playerUuid);
      sendDropConfirmation(player, playerUuid, Utils.formatMessage(player, "%evolv3_tool_label%"));
      return;
    }

    String pickaxeId = dropped.getData("id");
    if (pickaxeId == null || pickaxeId.isEmpty()) return;

    if (confirmedDropPlayers.remove(playerUuid)) {
      dropConfirmationExpiry.remove(playerUuid);
      releaseOwnership(player, pickaxeId);
      return;
    }

    event.setCancelled(true);
    dropConfirmationExpiry.put(playerUuid, System.currentTimeMillis() + 5_000);
    confirmedDropPlayers.add(playerUuid);
    sendDropConfirmation(player, playerUuid, "Pickaxe");
  }

  /**
   * When a player picks up a dropped pickaxe, restore it into their storage and transfer ownership
   * if they are not already the owner.
   */
  @EventHandler
  public void onPickaxePickup(EntityPickupItemEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;

    Item item = event.getItem();
    String pickaxeId = item.getItemStack().getData("id");
    if (pickaxeId == null || pickaxeId.isEmpty()) return;

    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter == null) return;

    PlayerProfile playerProfile = playerDataCenter.getLocal(player);
    if (playerProfile == null) return;

    if (!isAlreadyStored(playerProfile, pickaxeId) && !hasAvailableSlot(playerProfile, player)) {
      event.setCancelled(true);
      long now = System.currentTimeMillis();
      Long lastDenied = pickupDeniedCooldown.get(player.getUniqueId());
      if (lastDenied == null || (now - lastDenied) >= PICKUP_DENIED_COOLDOWN_MS) {
        pickupDeniedCooldown.put(player.getUniqueId(), now);
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        String msg = messages.getString(ConfigPaths.Messages.PickaxeLimit.PICKUP_NO_SLOTS, "");
        if (!msg.isEmpty()) {
          player.sendMessage(Utils.formatMessage(player, msg));
        }
      }
      return;
    }

    if (!isAlreadyStored(playerProfile, pickaxeId)) {
      String material = item.getItemStack().getType().name();
      addToStoredPickaxes(playerProfile, pickaxeId, material);
      playerProfile.save(null);
    }

    cleanupItemDisplay(item.getUniqueId());

    Pickaxe pickaxeProfile = playerDataCenter.getPickaxeByItemId(pickaxeId);
    if (pickaxeProfile != null) {
      if (!player.getUniqueId().equals(pickaxeProfile.ownerUuid)) {
        pickaxeProfile.ownerUuid = player.getUniqueId();
        pickaxeProfile.save(null);
        FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        String msg = messages.getString(ConfigPaths.Messages.Pickaxe.PICKUP_NEW_OWNER, "");
        if (!msg.isEmpty()) {
          player.sendMessage(Utils.formatMessage(player, msg));
        }
      }
      Bukkit.getScheduler()
          .runTaskLater(
              plugin,
              () -> {
                if (!player.isOnline()) return;
                for (ItemStack stack : player.getInventory().getContents()) {
                  if (stack == null) continue;
                  String id = stack.getData("id");
                  if (pickaxeId.equals(id)) {
                    PickaxeManager.getInstance().applyLoreAndName(player, pickaxeProfile, stack);
                    break;
                  }
                }
              },
              1L);
    } else {
      playerDataCenter.ensurePickaxe(player, pickaxeId);
    }
  }

  /**
   * Drag-and-drop repair: dropping Ancient Resin from the cursor onto a pickaxe sitting anywhere in
   * the player's own inventory repairs it, without needing to open the pickaxe menu.
   *
   * <p>{@code ignoreCancelled = true} so this only acts on a genuine, unclaimed click — e.g. it
   * won't fire on top of a custom menu that already cancelled bottom-inventory clicks for its own
   * reasons.
   */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onResinDroppedOnPickaxe(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

    ItemStack cursor = event.getCursor();
    if (!AncientResin.isAncientResin(cursor)) return;

    ItemStack clicked = event.getCurrentItem();
    if (clicked == null || !Utils.isPickaxe(clicked.getType())) return;

    event.setCancelled(true);
    PickaxeManager.getInstance()
        .repairPickaxeInSlot(player, event.getClickedInventory(), event.getSlot(), cursor);
  }

  /**
   * Returns true if the player has at least one empty slot within their unlocked slots. Unlocked
   * slots are derived from the highest evolution among the player's pickaxes, defaulting to 1 —
   * matching the same logic used by PickaxeLimitMenu.
   */
  private boolean hasAvailableSlot(PlayerProfile profile, Player player) {
    int maxEvolution =
        playerDataCenter.getAllPickaxes().stream()
            .filter(p -> player.getUniqueId().equals(p.ownerUuid))
            .mapToInt(p -> p.evolution)
            .max()
            .orElse(1);
    int unlockedSlots = Math.max(1, maxEvolution);

    for (int i = 0; i < unlockedSlots && i < profile.storedPickaxes.size(); i++) {
      if (profile.storedPickaxes.get(i).isEmpty()) return true;
    }
    return false;
  }

  /** Returns true if the player's storedPickaxes already contains an entry for this pickaxe id. */
  private boolean isAlreadyStored(PlayerProfile profile, String pickaxeId) {
    for (String entry : profile.storedPickaxes) {
      if (entry != null && entry.startsWith(pickaxeId + ":")) return true;
    }
    return false;
  }

  /**
   * Adds a pickaxe entry to the first available slot in the player's storedPickaxes, respecting
   * their pickaxeLimit. If all limited slots are full, uses any empty slot.
   */
  private void addToStoredPickaxes(PlayerProfile profile, String pickaxeId, String material) {
    String entry = pickaxeId + ":" + material;
    for (int i = 0; i < profile.getPickaxeLimit() && i < profile.storedPickaxes.size(); i++) {
      if (profile.storedPickaxes.get(i).isEmpty()) {
        profile.storedPickaxes.set(i, entry);
        return;
      }
    }
    for (int i = 0; i < profile.storedPickaxes.size(); i++) {
      if (profile.storedPickaxes.get(i).isEmpty()) {
        profile.storedPickaxes.set(i, entry);
        return;
      }
    }
  }

  /**
   * When a registered pickaxe item entity spawns, immediately attach a floating TextDisplay and
   * start a task that keeps it positioned above the item as it moves.
   */
  @EventHandler
  public void onItemSpawn(ItemSpawnEvent event) {
    Item item = event.getEntity();
    String pickaxeId = item.getItemStack().getData("id");
    if (pickaxeId == null || pickaxeId.isEmpty()) return;

    Pickaxe profile = playerDataCenter.getPickaxeByItemId(pickaxeId);
    if (profile == null) return;

    spawnItemDisplay(item, profile);
  }

  /**
   * When a pickaxe item entity despawns (5-minute natural timer), remove its floating TextDisplay
   * and stop the tracking task.
   */
  @EventHandler
  public void onItemDespawn(ItemDespawnEvent event) {
    cleanupItemDisplay(event.getEntity().getUniqueId());
  }

  /**
   * When a pickaxe takes durability damage: if this hit would vanilla-break it, cancel the break
   * and disable it instead (see {@link #disablePickaxe}); otherwise warn the player if remaining
   * durability falls below the configured threshold.
   *
   * <p>Runs at HIGH with {@code ignoreCancelled = true} so it always sees the damage amount after
   * {@link org.benski.evolv3.listeners.mine.BreakListener#onItemDamage} (NORMAL) has had a chance
   * to negate it via the Unbreaking stone roll — otherwise a negated hit could still be misread
   * here as breaking the pickaxe.
   */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onItemDamage(PlayerItemDamageEvent event) {
    String pickaxeId = event.getItem().getData("id");
    if (pickaxeId == null || pickaxeId.isEmpty()) return;

    int maxDurability = event.getItem().getType().getMaxDurability();
    if (maxDurability <= 0) return;

    ItemMeta meta = event.getItem().getItemMeta();
    if (!(meta instanceof Damageable damageable)) return;

    int newDamage = damageable.getDamage() + event.getDamage();

    if (newDamage >= maxDurability) {
      disablePickaxe(event, damageable, pickaxeId, maxDurability);
      return;
    }

    double remainingPercent = (double) (maxDurability - newDamage) / maxDurability * 100.0;

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double threshold =
        config.getDouble(ConfigPaths.Default.Pickaxe.DURABILITY_WARNING_THRESHOLD, 10.0);
    if (remainingPercent > threshold) return;

    Player player = event.getPlayer();
    UUID playerUuid = player.getUniqueId();
    long now = System.currentTimeMillis();
    Long lastWarning = lastDurabilityWarning.get(playerUuid);
    if (lastWarning != null && (now - lastWarning) < DURABILITY_WARNING_COOLDOWN_MS) return;

    lastDurabilityWarning.put(playerUuid, now);

    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    List<String> lines = messages.getStringList(ConfigPaths.Messages.Pickaxe.DURABILITY_WARNING);
    for (String line : lines) {
      player.sendMessage(Utils.formatMessage(player, line));
    }
  }

  /**
   * Instead of letting vanilla destroy the pickaxe at 0 durability, cancels the damage, pins the
   * item just short of breaking, and flags the backing profile as disabled so it can't be used to
   * mine until repaired.
   */
  private void disablePickaxe(
      PlayerItemDamageEvent event, Damageable damageable, String pickaxeId, int maxDurability) {
    event.setCancelled(true);
    damageable.setDamage(maxDurability - 1);
    event.getItem().setItemMeta(damageable);

    Player player = event.getPlayer();

    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe profile =
        playerDataCenter != null ? playerDataCenter.getPickaxeByItemId(pickaxeId) : null;
    if (profile != null && !profile.disabled) {
      profile.disabled = true;
      profile.durability = maxDurability - 1;
      profile.save(null);
      PickaxeManager.getInstance().applyLoreAndName(player, profile, event.getItem());
    }

    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    List<String> lines = messages.getStringList(ConfigPaths.Messages.Pickaxe.PICKAXE_DISABLED);
    for (String line : lines) {
      player.sendMessage(Utils.formatMessage(player, line));
    }
    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 0.7f);
  }

  /** Sends the shared drop confirmation prompt, replacing {tool} with the tool label. */
  private void sendDropConfirmation(Player player, UUID playerUuid, String toolLabel) {
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    List<String> promptLines =
        messages.getStringList(ConfigPaths.Messages.Pickaxe.DROP_CONFIRMATION);
    for (String line : promptLines) {
      player.sendMessage(Utils.formatMessage(player, line.replace("{tool}", toolLabel)));
    }
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              Long expiry = dropConfirmationExpiry.get(playerUuid);
              if (expiry != null && System.currentTimeMillis() >= expiry) {
                dropConfirmationExpiry.remove(playerUuid);
                confirmedDropPlayers.remove(playerUuid);
              }
            },
            DROP_CONFIRM_TICKS);
  }

  /**
   * Releases a player's ownership of a pickaxe by removing it from their stored pickaxes.
   * PickaxeProfile.ownerUuid is left pointing to the old owner and will be overwritten when a new
   * player picks up the item.
   */
  private void releaseOwnership(Player player, String pickaxeId) {
    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter == null) return;

    PlayerProfile playerProfile = playerDataCenter.getLocal(player);
    if (playerProfile == null) return;

    if (removeFromStoredPickaxes(playerProfile, pickaxeId)) {
      playerProfile.save(null);
    }
  }

  /**
   * Scans the player's stored pickaxes list and clears the entry matching the given id.
   *
   * @return true if an entry was found and removed
   */
  private boolean removeFromStoredPickaxes(PlayerProfile profile, String pickaxeId) {
    for (int i = 0; i < profile.storedPickaxes.size(); i++) {
      String entry = profile.storedPickaxes.get(i);
      if (entry != null && entry.startsWith(pickaxeId + ":")) {
        profile.storedPickaxes.set(i, "");
        return true;
      }
    }
    return false;
  }

  /**
   * Spawns a floating TextDisplay at the item's current position and starts a repeating task that
   * continuously repositions it to follow the item.
   */
  private void spawnItemDisplay(Item item, Pickaxe profile) {
    UUID itemId = item.getUniqueId();
    if (itemToDisplay.containsKey(itemId)) return;

    Component displayText = buildDisplayText(profile);

    TextDisplay td =
        item.getWorld()
            .spawn(
                item.getLocation().add(0, DISPLAY_HEIGHT, 0),
                TextDisplay.class,
                display -> {
                  display.text(displayText);
                  display.setBillboard(Display.Billboard.CENTER);
                  display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                  display.setShadowed(true);
                  display.setPersistent(false);
                  display.setTransformation(
                      new Transformation(
                          new Vector3f(0, 0, 0),
                          new AxisAngle4f(0, 0, 1, 0),
                          new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE),
                          new AxisAngle4f(0, 0, 1, 0)));
                });

    itemToDisplay.put(itemId, td);

    BukkitTask task =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  if (!item.isValid() || item.isDead()) {
                    cleanupItemDisplay(itemId);
                    return;
                  }
                  td.teleport(item.getLocation().add(0, DISPLAY_HEIGHT, 0));
                },
                1L,
                2L);

    itemToTask.put(itemId, task);
  }

  /**
   * Builds the Adventure Component text shown in the floating TextDisplay. Template is read from
   * messages.yml to allow server customization.
   */
  private Component buildDisplayText(Pickaxe profile) {
    String template =
        ConfigRegistry.getInstance()
            .get(ConfigNames.MESSAGES)
            .getString(
                ConfigPaths.Messages.Pickaxe.DROPPED_DISPLAY_TEXT,
                "&b⛏ &f&lLevel %level%  &7XP &f%xp%/%target%")
            .replace("%level%", String.valueOf(profile.level))
            .replace("%xp%", String.valueOf(profile.xp))
            .replace("%target%", String.valueOf(profile.target));
    return LegacyComponentSerializer.legacyAmpersand()
        .deserialize(template)
        .decoration(TextDecoration.ITALIC, false);
  }

  /** Removes and despawns the TextDisplay and cancels the tracking task for the given item UUID. */
  private void cleanupItemDisplay(UUID itemId) {
    TextDisplay td = itemToDisplay.remove(itemId);
    if (td != null && td.isValid()) {
      td.remove();
    }
    BukkitTask task = itemToTask.remove(itemId);
    if (task != null) {
      task.cancel();
    }
  }

  /** Cancels all active tracking tasks and removes all TextDisplays on plugin shutdown. */
  @Override
  public void cleanup() {
    itemToTask.values().forEach(BukkitTask::cancel);
    itemToTask.clear();
    itemToDisplay.values().stream()
        .filter(org.bukkit.entity.Entity::isValid)
        .forEach(org.bukkit.entity.Entity::remove);
    itemToDisplay.clear();
    dropConfirmationExpiry.clear();
    confirmedDropPlayers.clear();
    lastDurabilityWarning.clear();
    pickupDeniedCooldown.clear();
  }
}
