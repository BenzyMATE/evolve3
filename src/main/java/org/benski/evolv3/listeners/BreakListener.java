package org.benski.evolv3.listeners;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.benski.evolv3.animations.pickup.DissolvePickupAnimation;
import org.benski.evolv3.core.abstracts.BaseListener;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.constants.Enchants;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.EnchantDropManager;
import org.benski.evolv3.core.managers.FrostbiteBlockManager;
import org.benski.evolv3.core.managers.MineManager;
import org.benski.evolv3.core.managers.MineManager.Mine;
import org.benski.evolv3.core.managers.OmnitoolManager;
import org.benski.evolv3.core.managers.PickaxeManager;
import org.benski.evolv3.core.managers.PickupAnimationManager;
import org.benski.evolv3.core.managers.WorldGuardManager;
import org.benski.evolv3.core.types.PlayerActivity;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.enchants.EfficiencyStoneHandler;
import org.benski.evolv3.enchants.ExtractionEnchant;
import org.benski.evolv3.enchants.ExtractionStoneHandler;
import org.benski.evolv3.enchants.FortuneEnchant;
import org.benski.evolv3.enchants.FrostbiteEnchant;
import org.benski.evolv3.enchants.FurnaceStoneHandler;
import org.benski.evolv3.enchants.RadioactiveEnchant;
import org.benski.evolv3.enchants.UnbreakingStoneHandler;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerPreferences;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.AnimationRegistry;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles block break events for the pickaxe progression system. Integrates with backpack and
 * enchantment systems.
 *
 * <p>Uses two event handlers at different priorities:
 *
 * <ul>
 *   <li>NORMAL — captures block state and drops while the block is still intact, validates the
 *       break, and cancels early if needed.
 *   <li>HIGHEST — runs after all enchant listeners (Alchemy NORMAL, Furnace HIGH) have had a chance
 *       to set their block metadata, then resolves the final drop, plays the pickup animation, and
 *       updates the backpack.
 * </ul>
 */
public class BreakListener extends BaseListener {
  private final PickupAnimationManager pickupAnimationManager =
      PickupAnimationManager.getInstance();
  private final AnimationRegistry animationRegistry = AnimationRegistry.getInstance();
  private final PickaxeManager pickaxeManager = PickaxeManager.getInstance();

  /**
   * Per-player context captured at NORMAL priority, consumed at HIGHEST. Ensures block type and
   * base drop are read while the block is intact.
   */
  private final Map<UUID, BreakContext> pendingBreaks = new ConcurrentHashMap<>();

  private record BreakContext(
      Material blockType,
      Material xpMaterial,
      ItemStack baseDrop,
      int dropAmount,
      boolean isEnchantDrop,
      Backpack backpack,
      PlayerProfile playerProfile,
      Location animationLocation,
      Location blockLocation) {}

  public BreakListener(JavaPlugin plugin) {
    super(plugin);
  }

  /**
   * NORMAL priority: validates the break and captures all block-state data (block type, drops)
   * while the block is guaranteed to still be intact. Cancels early for invalid conditions (wrong
   * tool, wrong region, full backpack).
   */
  @EventHandler(priority = EventPriority.NORMAL)
  public void onBreakCapture(BlockBreakEvent event) {
    Player player = event.getPlayer();
    ItemStack heldItem = player.getInventory().getItemInMainHand();

    if (!isPickaxe(heldItem.getType())) {
      return;
    }

    if (!WorldGuardManager.getInstance().isWithinMineRegion(event.getBlock().getLocation())) {
      FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
      String message =
          config.getString(
              ConfigPaths.Default.NOT_IN_MINE_REGION_MESSAGE,
              "&c&lYou can only mine in designated mine regions!");
      player.sendActionBar(
          net.kyori.adventure.text.Component.text(Utils.formatMessage(player, message)));
      event.setCancelled(true);
      return;
    }

    Block block = event.getBlock();
    Material blockType = block.getType();

    if (isStalePackedIce(block, blockType)) {
      event.setDropItems(false);
      return;
    }

    if (blockType == Material.SCULK) {
      event.setDropItems(false);
      return;
    }

    event.setDropItems(false);

    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter == null) return;

    PlayerProfile playerProfile = playerDataCenter.getLocal(player);
    if (playerProfile == null) return;

    Backpack backpack = playerProfile.backpack;

    Collection<ItemStack> drops = block.getDrops(heldItem, player);

    Material dropMaterial = resolveDropMaterial(block, drops, blockType);
    if (dropMaterial == null) return;

    if (backpack.isFull()) {
      showBackpackFullTitle(player);
      event.setCancelled(true);
      return;
    }

    Material xpMaterial =
        block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY) ? dropMaterial : blockType;

    ItemStack baseDrop = new ItemStack(dropMaterial);
    int dropAmount = drops.isEmpty() ? 1 : drops.stream().mapToInt(ItemStack::getAmount).sum();
    boolean isEnchantDrop = playerProfile.isEnchanting();

    pendingBreaks.put(
        player.getUniqueId(),
        new BreakContext(
            blockType,
            xpMaterial,
            baseDrop,
            dropAmount,
            isEnchantDrop,
            backpack,
            playerProfile,
            event.getBlock().getLocation().add(0.5, 0.5, 0.5),
            event.getBlock().getLocation()));
  }

  /**
   * HIGHEST priority: runs after all enchant listeners have fired. Reads enchant metadata (Furnace,
   * Alchemy, Frostbite), resolves the final drop, plays the pickup animation with the correct item,
   * and updates the backpack.
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onBreakFinalize(BlockBreakEvent event) {
    Player player = event.getPlayer();
    BreakContext ctx = pendingBreaks.remove(player.getUniqueId());
    if (ctx == null) return;

    if (event.isCancelled()) {
      clearEnchantState(player.getUniqueId());
      return;
    }

    ItemStack finalDrop = handleEnchantDrops(player.getUniqueId(), ctx.baseDrop());
    int fortuneAmount = FortuneEnchant.popDropAmount(player.getUniqueId());
    int finalDropAmount = fortuneAmount > 0 ? fortuneAmount : ctx.dropAmount();
    if (FurnaceStoneHandler.popDoubleSmelt(player.getUniqueId())) finalDropAmount *= 2;
    boolean gemSeeker = FortuneEnchant.popGemSeeker(player.getUniqueId());

    if (finalDrop.getType().isAir()) return;

    if (isLog(ctx.blockType())) {
      handlePickaxeLogBreak(player, ctx, finalDrop);
      return;
    }

    FileConfiguration rollConfig = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double baseEssenceChance =
        rollConfig.getDouble(ConfigPaths.Default.Essence.DISCOVERY_CHANCE, 5.0);
    boolean essenceHit =
        Utils.chance(baseEssenceChance + ExtractionEnchant.popBonus(player.getUniqueId()));
    double baseAtomChance = rollConfig.getDouble(ConfigPaths.Default.Atoms.DISCOVERY_CHANCE, 1.0);
    boolean atomHit =
        Utils.chance(baseAtomChance + RadioactiveEnchant.popBonus(player.getUniqueId()));

    int baseEssenceMultiplier = ExtractionEnchant.popEssenceMultiplier(player.getUniqueId());
    boolean legendaryExtraction = ExtractionEnchant.popLegendaryActive(player.getUniqueId());
    final int finalEssenceMultiplier;
    if (essenceHit
        && legendaryExtraction
        && ExtractionStoneHandler.incrementAndCheckLegendary(player.getUniqueId())) {
      finalEssenceMultiplier = 5;
    } else {
      finalEssenceMultiplier = baseEssenceMultiplier;
    }

    PreferenceType.DropCategory dropCategory =
        essenceHit
            ? PreferenceType.DropCategory.ESSENCE
            : atomHit ? PreferenceType.DropCategory.ATOM : PreferenceType.DropCategory.NORMAL;

    pickupAnimationManager.playPickupAnimation(
        player, ctx.animationLocation(), List.of(finalDrop), ctx.xpMaterial(), dropCategory);

    ctx.backpack().addItem(player, finalDrop.getType(), finalDropAmount, ctx.isEnchantDrop());
    BackpackListener.refresh(player);

    ctx.playerProfile().activity = PlayerActivity.NONE;

    pickaxeManager.addStatistics(player, ctx.xpMaterial(), ctx.playerProfile());

    boolean isDissolve =
        ctx.playerProfile().preferences.pickupEffect == PreferenceType.PickupEffect.DEFAULT;

    boolean showEssence = hasAnyEssenceFx(ctx.playerProfile().preferences);
    boolean showAtom = hasAnyAtomFx(ctx.playerProfile().preferences);

    final Location hologramLoc = ctx.blockLocation().clone().add(0.5, 1.5, 0.5);

    if (isDissolve) {
      long delay = DissolvePickupAnimation.SECONDARY_ANIM_DELAY;
      final Location floatTop =
          ctx.blockLocation().clone().add(0, DissolvePickupAnimation.FLOAT_HEIGHT - 0.5, 0);
      if (essenceHit && showEssence) {
        plugin
            .getServer()
            .getScheduler()
            .runTaskLater(
                plugin,
                () -> {
                  if (!player.isOnline()) return;
                  PlayerDataCenter pdc = DataCenterRegistry.getInstance().getPlayerDataCenter();
                  PlayerProfile freshProfile = pdc != null ? pdc.getLocal(player) : null;
                  if (freshProfile != null)
                    checkEssenceDiscovery(
                        player, freshProfile, floatTop, hologramLoc, finalEssenceMultiplier);
                },
                delay);
      }
      if (atomHit && showAtom) {
        plugin
            .getServer()
            .getScheduler()
            .runTaskLater(
                plugin,
                () -> {
                  if (player.isOnline()) checkAtomDiscovery(player, floatTop, hologramLoc);
                },
                delay);
      }
    } else {
      if (essenceHit && showEssence)
        checkEssenceDiscovery(
            player, ctx.playerProfile(), ctx.blockLocation(), hologramLoc, finalEssenceMultiplier);
      if (atomHit && showAtom) checkAtomDiscovery(player, ctx.blockLocation(), hologramLoc);
    }

    if (gemSeeker && Utils.chance(20.0) && showEssence) {
      checkEssenceDiscovery(player, ctx.playerProfile(), ctx.blockLocation(), hologramLoc, 1);
    }

    pickaxeManager.recordBreak(player.getUniqueId());
    EfficiencyStoneHandler.getInstance().onBlockBreak(player);

    ctx.playerProfile().save(null);
  }

  /**
   * Handles bare-hand log breaks in mine regions. Plays the player's chosen pickup animation for
   * the log drop, then rolls a chance for Ancient Resin — if successful, plays the resin extraction
   * animation which physically drops the item on completion.
   *
   * <p>Pickaxe breaks are handled by {@link #onBreakCapture} / {@link #onBreakFinalize} and are
   * intentionally not affected here.
   *
   * <p>ignoreCancelled = false so this fires even if WorldGuard or another plugin cancelled the
   * event first — we explicitly allow bare-hand log breaks in mine regions.
   */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void onLogBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    Material blockType = block.getType();

    if (!isLog(blockType)) return;

    if (isPickaxe(player.getInventory().getItemInMainHand().getType())) return;

    WorldGuardManager wg = WorldGuardManager.getInstance();
    if (!wg.isWithinMineRegion(block.getLocation())
        && !wg.isWithinOmnitoolAxeRegion(block.getLocation())) return;

    PlayerDataCenter playerDataCenter = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (playerDataCenter == null) return;

    PlayerProfile playerProfile = playerDataCenter.getLocal(player);
    if (playerProfile == null) return;

    Backpack backpack = playerProfile.backpack;

    event.setDropItems(false);

    if (backpack.isFull()) {
      FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
      String fullMessage =
          cfg.getString(ConfigPaths.Default.Backpack.FULL_MESSAGE, "&c&lBACKPACK FULL");
      player.sendActionBar(
          net.kyori.adventure.text.Component.text(Utils.formatMessage(player, fullMessage)));
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
      event.setCancelled(true);
      return;
    }

    Location center = block.getLocation().add(0.5, 0.5, 0.5);

    OmnitoolManager omni = OmnitoolManager.getInstance();
    boolean isOmnitoolAxe =
        omni.isInOmnitoolMode(player) && omni.getForm(player) == OmnitoolManager.Form.AXE;
    if (isOmnitoolAxe) {
      FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
      int omniXp = cfg.getInt("Omnitool.LogBreakXp", 5);
      pickupAnimationManager.playPickupAnimation(
          player,
          center,
          List.of(new ItemStack(blockType)),
          blockType,
          PreferenceType.DropCategory.NORMAL,
          omniXp);
    } else {
      pickupAnimationManager.playPickupAnimation(
          player,
          center,
          List.of(new ItemStack(blockType)),
          blockType,
          PreferenceType.DropCategory.NORMAL);
    }

    backpack.addItem(player, blockType, 1, false);
    BackpackListener.refresh(player);

    Mine mine = MineManager.getInstance().getMineAt(block.getLocation());
    if (mine != null) {
      MineManager.getInstance().onBlockMined(mine.name());
    }

    playerProfile.save(null);

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double dropChance = config.getDouble(ConfigPaths.Default.AncientResin.DROP_CHANCE, 25.0);
    if (!Utils.chance(dropChance)) return;

    animationRegistry.play("resin", center, List.of(player));
  }

  /**
   * Handles a log broken with a pickaxe — mirrors bare-hand log behaviour: plays the pickup
   * animation, adds the log to the backpack, rolls for Ancient Resin, and skips essence/atom
   * discovery entirely.
   */
  private void handlePickaxeLogBreak(Player player, BreakContext ctx, ItemStack finalDrop) {
    pickupAnimationManager.playPickupAnimation(
        player,
        ctx.animationLocation(),
        List.of(finalDrop),
        ctx.blockType(),
        PreferenceType.DropCategory.NORMAL);

    ctx.backpack().addItem(player, finalDrop.getType(), ctx.dropAmount(), false);
    BackpackListener.refresh(player);
    ctx.playerProfile().activity = PlayerActivity.NONE;

    pickaxeManager.addStatistics(player, ctx.blockType(), ctx.playerProfile());

    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    double dropChance = config.getDouble(ConfigPaths.Default.AncientResin.DROP_CHANCE, 25.0);
    if (Utils.chance(dropChance)) {
      animationRegistry.play("resin", ctx.animationLocation(), List.of(player));
    }

    pickaxeManager.recordBreak(player.getUniqueId());
    ctx.playerProfile().save(null);
  }

  /**
   * Applies the Unbreaking enchant: rolls a chance to negate pickaxe durability damage. Chance =
   * (enchantLevel / maxLevel) * maxChance
   */
  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onItemDamage(PlayerItemDamageEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();

    String pickaxeId = ItemStackExtensions.getData(item, "id");
    if (pickaxeId == null || pickaxeId.isEmpty()) return;

    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return;

    Pickaxe profile = dc.getPickaxeByItemId(pickaxeId);
    if (profile == null) return;

    EnchantData unbreaking = profile.enchants.getEnchant(Enchants.UNBREAKING);
    if (unbreaking == null) return;

    if (unbreaking.hasStone("Legendary")) {
      event.setCancelled(true);
      return;
    }

    if (!unbreaking.enabled || unbreaking.level <= 0) return;

    double negateChance = unbreaking.getCurrentChance();
    if (unbreaking.hasStone("Common")) negateChance += 5.0;

    boolean negated = Utils.chance(negateChance);
    if (!negated && unbreaking.hasStone("Rare")) negated = Utils.chance(50.0);

    if (negated) {
      event.setCancelled(true);
      UnbreakingStoneHandler.getInstance().onNegate(player, unbreaking, item);
    }
  }

  private boolean isLog(Material material) {
    String name = material.name();
    return name.endsWith("_LOG") || name.endsWith("_STEM");
  }

  private boolean isPickaxe(Material material) {
    return Utils.isPickaxe(material);
  }

  /**
   * PACKED_ICE that lacks Frostbite metadata is a stale remnant from a previous server session —
   * block metadata does not survive restarts so the original material cannot be recovered.
   */
  private boolean isStalePackedIce(Block block, Material blockType) {
    return blockType == Material.PACKED_ICE
        && !block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY);
  }

  /**
   * Resolves the effective drop material for a broken block. For Frostbite-frozen blocks the real
   * material is read from metadata and the frozen block record is removed. Metadata is
   * intentionally not cleared here so Alchemy (NORMAL) and Furnace (HIGH) enchant listeners can
   * still read it before the block physically breaks. Returns {@code null} when the frozen material
   * cannot be recovered (treat as silent break).
   */
  private Material resolveDropMaterial(
      Block block, Collection<ItemStack> drops, Material blockType) {
    if (!block.hasMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY)) {
      return drops.stream()
          .filter(i -> i != null && !i.getType().isAir())
          .findFirst()
          .map(ItemStack::getType)
          .orElse(blockType);
    }

    String stored =
        block.getMetadata(FrostbiteEnchant.FROSTBITE_METADATA_KEY).getFirst().asString();
    Material parsed = null;
    try {
      parsed = Material.valueOf(stored);
    } catch (IllegalArgumentException ignored) {
    }
    FrostbiteBlockManager.getInstance().removeFrozenBlock(block.getLocation());

    if (parsed == null || parsed.isAir() || parsed == Material.PACKED_ICE) {
      return null;
    }
    return parsed;
  }

  private void showBackpackFullTitle(Player player) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    LegacyComponentSerializer lcs = LegacyComponentSerializer.legacySection();
    player.showTitle(
        Title.title(
            lcs.deserialize(
                Utils.formatMessage(
                    player,
                    config.getString(
                        ConfigPaths.Default.Backpack.FULL_TITLE, "&c&lBackpack Full"))),
            lcs.deserialize(
                Utils.formatMessage(
                    player,
                    config.getString(
                        ConfigPaths.Default.Backpack.FULL_SUBTITLE,
                        "&cSell your items to keep mining."))),
            Title.Times.times(
                Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(400))));
    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
  }

  /**
   * Resolves the final drop after enchants have fired. Furnace overwrites Alchemy in {@link
   * EnchantDropManager} when both trigger, so a single pop always yields the correct result.
   *
   * @param playerUuid the player's UUID
   * @param drop the base drop captured at NORMAL priority
   * @return the resolved final drop item
   */
  private ItemStack handleEnchantDrops(UUID playerUuid, ItemStack drop) {
    Material enchantResult = EnchantDropManager.pop(playerUuid);
    if (enchantResult != null) {
      return new ItemStack(enchantResult);
    }

    return drop;
  }

  /**
   * Clears all enchant state for a player when a break is cancelled. Frostbite metadata is already
   * consumed in onBreakCapture, so only EnchantDropManager and ExtractionEnchant bonus need
   * clearing here.
   */
  private void clearEnchantState(UUID playerUuid) {
    EnchantDropManager.pop(playerUuid);
    ExtractionEnchant.popBonus(playerUuid);
    ExtractionEnchant.popEssenceMultiplier(playerUuid);
    ExtractionEnchant.popLegendaryActive(playerUuid);
    RadioactiveEnchant.popBonus(playerUuid);
    RadioactiveEnchant.popAmountMultiplier(playerUuid);
    FortuneEnchant.popDropAmount(playerUuid);
    FortuneEnchant.popGemSeeker(playerUuid);
  }

  private boolean hasAnyEssenceFx(PlayerPreferences prefs) {
    return prefs.essenceSound
        || prefs.essenceParticles
        || prefs.essenceGlow
        || prefs.essenceHologram;
  }

  private boolean hasAnyAtomFx(PlayerPreferences prefs) {
    return prefs.atomSound || prefs.atomParticles || prefs.atomGlow || prefs.atomHologram;
  }

  /**
   * Applies atom discovery rewards and animation. Caller is responsible for the chance roll.
   *
   * @param animLoc where the atom particle animation originates (float top for dissolve)
   * @param hologramLoc where the hologram text appears (original mine block)
   */
  private void checkAtomDiscovery(Player player, Location animLoc, Location hologramLoc) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);

    int minAmount = config.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MIN_AMOUNT, 1);
    int maxAmount = config.getInt(ConfigPaths.Default.Atoms.DISCOVERY_MAX_AMOUNT, 3);
    int rawMultiplier = RadioactiveEnchant.popAmountMultiplier(player.getUniqueId());
    int baseAmount = Utils.amount(minAmount, maxAmount) * Math.max(1, rawMultiplier);

    PlayerDataCenter atomDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (atomDC == null) return;

    Pickaxe localProfile = atomDC.getPickaxeForPlayer(player);
    double prestigeMultiplier =
        localProfile != null ? pickaxeManager.getPrestigeMultiplier(localProfile.prestige) : 1.0;
    int amount = (int) Math.round(baseAmount * prestigeMultiplier);

    if (localProfile != null) {
      localProfile.atomVessel.current += amount;
      pickaxeManager.checkAtoms(localProfile, player);
      localProfile.save(null);
      EfficiencyStoneHandler.getInstance().onAtomPickup(player);
    }

    animationRegistry.play("atom", animLoc, List.of(player), amount);

    PlayerDataCenter pdcAtom = DataCenterRegistry.getInstance().getPlayerDataCenter();
    PlayerProfile atomProfile = pdcAtom != null ? pdcAtom.getLocal(player) : null;
    boolean atomHologramOn = atomProfile == null || atomProfile.preferences.atomHologram;

    if (atomHologramOn && config.getBoolean(ConfigPaths.Default.Atoms.HOLOGRAM_ENABLED, true)) {
      String text =
          config
              .getString(
                  ConfigPaths.Default.Atoms.HOLOGRAM_TEXT,
                  "&#FFEE00⚛ &#FFFFFF&l+ %amount% &#FFEE00Atoms")
              .replace("%amount%", Utils.formatNumber(amount));
      if (localProfile != null && localProfile.prestige > 0) {
        text += String.format(" &7(x%.1f)", prestigeMultiplier);
      }
      animationRegistry.play("hologram", hologramLoc, List.of(player), text);
    }
  }

  /**
   * Removes any pending break context for the given player.
   *
   * <p>Called from {@link JoinListener#onQuitEvent} to prevent a stale {@link BreakContext} entry
   * lingering in the map when a player disconnects between the NORMAL and HIGHEST break event
   * priorities.
   *
   * @param uuid the player's UUID
   */
  public void clearPendingBreak(java.util.UUID uuid) {
    pendingBreaks.remove(uuid);
  }

  /**
   * Applies essence discovery rewards and animation. Caller is responsible for the chance roll.
   *
   * @param animLoc where the essence particle animation originates (float top for dissolve)
   * @param hologramLoc where the hologram text appears (original mine block)
   * @param multiplier amount multiplier from Extraction stones (1 = normal, 2 = Rare, 3 = Epic, 5 =
   *     Legendary 5th)
   */
  private void checkEssenceDiscovery(
      Player player,
      PlayerProfile playerProfile,
      Location animLoc,
      Location hologramLoc,
      int multiplier) {
    FileConfiguration config = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);

    int minAmount = config.getInt(ConfigPaths.Default.Essence.DISCOVERY_MIN_AMOUNT, 1);
    int maxAmount = config.getInt(ConfigPaths.Default.Essence.DISCOVERY_MAX_AMOUNT, 5);
    int baseAmount = Utils.amount(minAmount, maxAmount) * Math.max(1, multiplier);

    PlayerDataCenter essenceDC = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (essenceDC == null) return;

    Pickaxe localProfile = essenceDC.getPickaxeForPlayer(player);
    double prestigeMultiplier =
        localProfile != null ? pickaxeManager.getPrestigeMultiplier(localProfile.prestige) : 1.0;
    int amount = (int) Math.round(baseAmount * prestigeMultiplier);

    if (localProfile != null) {
      playerProfile.addEssence(amount);
      localProfile.essenceVessel.current += amount;
      pickaxeManager.checkEssence(localProfile, player);
      localProfile.save(null);
    }

    animationRegistry.play("essence", animLoc, List.of(player), amount);

    if (playerProfile.preferences.essenceHologram
        && config.getBoolean(ConfigPaths.Default.Essence.HOLOGRAM_ENABLED, true)) {
      String text =
          config.getString(
              ConfigPaths.Default.Essence.HOLOGRAM_TEXT,
              "&#B366D9🔥 &#FFFFFF&l+ %amount% &#B366D9Essence");
      text = text.replace("%amount%", Utils.formatNumber(amount));
      if (localProfile != null && localProfile.prestige > 0) {
        text += String.format(" &7(x%.1f)", prestigeMultiplier);
      }
      animationRegistry.play("hologram", hologramLoc, List.of(player), text);
    }
  }
}
