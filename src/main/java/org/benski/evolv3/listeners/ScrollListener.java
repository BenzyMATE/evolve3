package org.benski.evolv3.listeners;

import lombok.experimental.ExtensionMethod;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.items.Scroll;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Handles Scroll item interactions: - Right-click in air → redeem Coins/Essence/Atoms/Backpack
 * scrolls - Drag (cursor) a Pickaxe XP Scroll onto a pickaxe → apply XP to that pickaxe - Drag
 * (cursor) a Coin Scroll onto another Coin Scroll → combine amounts
 */
@ExtensionMethod(ItemStackExtensions.class)
public class ScrollListener extends BaseListener {
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  public ScrollListener(JavaPlugin plugin) {
    super(plugin);
  }

  @Override
  public void initialize() {}

  @Override
  public void cleanup() {}

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getAction() != Action.RIGHT_CLICK_AIR
        && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

    Player player = event.getPlayer();
    ItemStack item = player.getInventory().getItemInMainHand();
    if (!Scroll.isScroll(item)) return;

    String type = Scroll.getType(item);
    if (Scroll.PICKAXE_XP.equals(type) || Scroll.ATOMS.equals(type)) {
      event.setCancelled(true);
      sendMsg(
          player,
          ConfigPaths.Messages.Scroll.USE_ON_PICKAXE,
          "  %evolv3_prefix% &#888888Drag this scroll onto a pickaxe to apply it.");
      return;
    }

    event.setCancelled(true);

    int amount = Scroll.getAmount(item);
    applyScroll(player, type, amount);

    if (item.getAmount() > 1) {
      item.setAmount(item.getAmount() - 1);
    } else {
      player.getInventory().setItemInMainHand(null);
    }

    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    sendRedeemMsg(player, type, amount);
    playScrollAnimation(player, type, amount);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;

    ItemStack cursor = event.getCursor();
    ItemStack current = event.getCurrentItem();

    if (!Scroll.isScroll(cursor)) return;

    String cursorType = Scroll.getType(cursor);

    if ((Scroll.PICKAXE_XP.equals(cursorType) || Scroll.ATOMS.equals(cursorType))
        && isPickaxe(current)) {
      event.setCancelled(true);

      String currentId = current != null ? current.getData("id") : null;
      Pickaxe profile = currentId != null ? playerDataCenter.getPickaxeByItemId(currentId) : null;
      if (profile == null) {
        sendMsg(
            player,
            ConfigPaths.Messages.Scroll.PICKAXE_NOT_LOADED,
            "  %evolv3_prefix% &cThat pickaxe is not loaded. Hold it in your main hand first.");
        return;
      }

      int amount = Scroll.getAmount(cursor);
      if (Scroll.PICKAXE_XP.equals(cursorType)) {
        profile.xp += amount;
        PickaxeManager.getInstance().checkLevel(profile, player);
      } else {
        profile.atomVessel.current += amount;
        PickaxeManager.getInstance().checkAtoms(profile, player);
      }
      profile.save(null);

      Bukkit.getScheduler()
          .runTask(
              plugin,
              () -> {
                player.setItemOnCursor(null);
                PickaxeManager.getInstance().updateLore(player, null);
                player.updateInventory();
              });

      player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
      if (Scroll.PICKAXE_XP.equals(cursorType)) {
        sendXpMsg(player, amount);
      } else {
        sendAtomsAppliedMsg(player, amount);
      }
      playScrollAnimation(player, cursorType, amount);
      return;
    }

    if (cursorType != null && cursorType.equals(Scroll.getType(current))) {
      event.setCancelled(true);

      int total = Scroll.getAmount(cursor) + Scroll.getAmount(current);
      ItemStack combined = Scroll.withAmount(current, total);

      Bukkit.getScheduler()
          .runTask(
              plugin,
              () -> {
                event.getView().setItem(event.getRawSlot(), combined);
                player.setItemOnCursor(null);
                player.updateInventory();
              });

      player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.4f);
      sendCombineMsg(player, cursorType, total);
      playScrollAnimation(player, cursorType, total);
    }
  }

  private void applyScroll(@NotNull Player player, @NotNull String type, int amount) {
    switch (type) {
      case Scroll.COINS -> {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return;
        PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return;
        profile.addCoins(amount);
        profile.save(null);
      }
      case Scroll.ESSENCE -> {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return;
        PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return;
        profile.addEssence(amount);
        profile.save(null);
      }
      case Scroll.BACKPACK_SLOTS -> {
        PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
        if (pdc == null) return;
        PlayerProfile profile = pdc.getLocal(player);
        if (profile == null) return;
        profile.backpack.setMaxSize(profile.backpack.getMaxSize() + amount);
        profile.save(null);
      }
      default -> plugin.getLogger().warning("[Evolv3] Unknown scroll type: " + type);
    }
  }

  private static boolean isPickaxe(ItemStack item) {
    if (item == null || item.getType().isAir()) return false;
    String id = item.getData("id");
    return id != null && !id.isEmpty();
  }

  private void sendRedeemMsg(Player player, String type, int amount) {
    String label =
        switch (type) {
          case Scroll.COINS -> "&#FFD700+" + amount + " Coins";
          case Scroll.ESSENCE -> "&#B366D9+" + amount + " Essence";
          case Scroll.ATOMS -> "&#FFEE00+" + amount + " Atoms";
          case Scroll.BACKPACK_SLOTS ->
              "&#00FFFF+" + amount + " Backpack Slot" + (amount == 1 ? "" : "s");
          default -> "+" + amount;
        };
    sendMsg(
        player,
        ConfigPaths.Messages.Scroll.REDEEMED,
        "  %evolv3_prefix% &#CCCCCCRedeemed %reward%&#CCCCCC!",
        "%reward%",
        label);
  }

  private void sendXpMsg(Player player, int amount) {
    sendMsg(
        player,
        ConfigPaths.Messages.Scroll.APPLIED_XP,
        "  %evolv3_prefix% &#00FFFFApplied &#FFFFFF+" + amount + " XP &#00FFFFto your pickaxe.",
        "%amount%",
        String.valueOf(amount));
  }

  private void sendAtomsAppliedMsg(Player player, int amount) {
    sendMsg(
        player,
        ConfigPaths.Messages.Scroll.APPLIED_ATOMS,
        "  %evolv3_prefix% &#FFEE00Applied &#FFFFFF+" + amount + " Atoms &#FFEE00to your pickaxe.",
        "%amount%",
        String.valueOf(amount));
  }

  private void sendCombineMsg(Player player, String type, int total) {
    String typeLabel =
        switch (type) {
          case Scroll.COINS -> "&#FFD700Coins";
          case Scroll.ESSENCE -> "&#B366D9Essence";
          case Scroll.ATOMS -> "&#FFEE00Atoms";
          case Scroll.PICKAXE_XP -> "&#00FFFFPickaxe XP";
          case Scroll.BACKPACK_SLOTS -> "&#00FFFFBackpack Slots";
          default -> type;
        };
    sendMsg(
        player,
        ConfigPaths.Messages.Scroll.COMBINED,
        "  %evolv3_prefix% &#FFD700Combined into &#FFFFFF" + total + " " + typeLabel + "&#FFD700.",
        "%amount%",
        String.valueOf(total),
        "%type%",
        typeLabel);
  }

  private void playScrollAnimation(@NotNull Player player, @NotNull String type, int amount) {
    Location loc = player.getLocation().add(0, 1.0, 0);

    Color color =
        switch (type) {
          case Scroll.COINS -> Color.fromRGB(255, 215, 0);
          case Scroll.ESSENCE -> Color.fromRGB(179, 102, 217);
          case Scroll.ATOMS -> Color.fromRGB(255, 238, 0);
          case Scroll.PICKAXE_XP -> Color.fromRGB(0, 255, 255);
          case Scroll.BACKPACK_SLOTS -> Color.fromRGB(64, 200, 255);
          default -> Color.WHITE;
        };

    Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
    player.spawnParticle(Particle.DUST, loc, 18, 0.25, 0.25, 0.25, 0.0, dust);
    player.spawnParticle(Particle.END_ROD, loc, 4, 0.15, 0.15, 0.15, 0.02);

    String label =
        switch (type) {
          case Scroll.COINS -> "&#FFD700+" + amount + " Coins";
          case Scroll.ESSENCE -> "&#B366D9+" + amount + " Essence";
          case Scroll.ATOMS -> "&#FFEE00+" + amount + " Atoms";
          case Scroll.PICKAXE_XP -> "&#00FFFF+" + amount + " XP";
          case Scroll.BACKPACK_SLOTS -> "&#00FFFF+" + amount + " Slot" + (amount == 1 ? "" : "s");
          default -> "+" + amount;
        };
    player.sendActionBar(LEGACY.deserialize(MatrixColorAPI.process(label)));
  }

  private void sendMsg(Player player, String path, String fallback, String... replacements) {
    FileConfiguration messages = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    if (messages == null) return;
    String prefix = messages.getString("prefix", "&3&lE &8➧ ");
    String raw = messages.getString(path, fallback);
    raw = raw.replace("%evolv3_prefix%", prefix);
    for (int i = 0; i + 1 < replacements.length; i += 2) {
      raw = raw.replace(replacements[i], replacements[i + 1]);
    }
    player.sendMessage(LEGACY.deserialize(MatrixColorAPI.process(raw)));
  }
}
