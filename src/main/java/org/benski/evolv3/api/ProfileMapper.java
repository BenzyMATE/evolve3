package org.benski.evolv3.api;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.benski.evolv3.api.dto.*;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.types.PlayerActivity;
import org.benski.evolv3.core.types.PreferenceType;
import org.benski.evolv3.core.types.RankType;
import org.benski.evolv3.core.types.VesselType;
import org.benski.evolv3.models.backpack.Backpack;
import org.benski.evolv3.models.backpack.BackpackItem;
import org.benski.evolv3.models.pickaxe.EnchantData;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.pickaxe.PickaxeEnchants;
import org.benski.evolv3.models.pickaxe.Vessel;
import org.benski.evolv3.models.player.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Converts between API DTOs and Java domain models. */
public final class ProfileMapper {

  // ── Preference key constants ───────────────────────────────────────────────
  private static final String PREF_ANIMATION = "animation";
  private static final String PREF_MESSAGE = "message";
  private static final String PREF_ENCHANT_STYLE = "enchantStyle";
  private static final String PREF_EVOLUTION_STYLE = "evolutionStyle";
  private static final String PREF_PICKUP_EFFECT = "pickupEffect";
  private static final String PREF_ENCHANT_SPEED = "enchantAnimationSpeed";
  private static final String PREF_ESSENCE_SOUND = "essenceSound";
  private static final String PREF_ESSENCE_PARTICLES = "essenceParticles";
  private static final String PREF_ESSENCE_HOLOGRAM = "essenceHologram";
  private static final String PREF_ESSENCE_GLOW = "essenceGlow";
  private static final String PREF_ATOM_SOUND = "atomSound";
  private static final String PREF_ATOM_PARTICLES = "atomParticles";
  private static final String PREF_ATOM_HOLOGRAM = "atomHologram";
  private static final String PREF_ATOM_GLOW = "atomGlow";
  private static final String PREF_PICKAXE_MENU_ANIMATION = "pickaxeMenuAnimation";
  private static final String PREF_STORED_PICKAXE_PREFIX = "storedPickaxe_";
  private static final String PREF_ENCHANT_PREF_PREFIX = "ep_";

  // ── DTO → Domain ──────────────────────────────────────────────────────────

  /** Maps a full API response DTO to a {@link PlayerProfile} domain object. */
  public static PlayerProfile fromDto(PlayerProfileDto dto, Player player) {
    PlayerProfile profile = new PlayerProfile();
    profile.playerUuid = UUID.fromString(dto.id);
    profile.id = dto.id;

    profile.wallet = walletFromDto(dto.wallet);
    profile.ranks = ranksFromDto(dto.ranks);
    profile.activity = safeEnum(dto.activity, PlayerActivity.class, PlayerActivity.NONE);
    profile.backpack = backpackFromDto(dto.backpack);
    profile.pickaxes = pickaxesFromDto(dto.pickaxes);

    applyPreferences(profile, dto.preferences);

    if (player != null) {
      profile.initialize(player);
    }

    return profile;
  }

  /** Wallet from dto. */
  private static Wallet walletFromDto(WalletDto dto) {
    if (dto == null) return new Wallet();
    return new Wallet(dto.coins, (int) dto.essence);
  }

  /** Ranks from dto. */
  private static List<Rank> ranksFromDto(List<RankDto> dtos) {
    List<Rank> ranks = new ArrayList<>();
    if (dtos == null) return ranks;
    for (RankDto dto : dtos) {
      RankType type = safeEnum(dto.type, RankType.class, RankType.DEFAULT);
      ranks.add(new Rank(dto.prefix, type));
    }
    return ranks;
  }

  /** Backpack from dto. */
  private static Backpack backpackFromDto(BackpackDto dto) {
    Backpack backpack = Backpack.createDefault();
    if (dto == null) return backpack;
    backpack.setMaxSize(dto.total);
    if (dto.items != null) {
      for (BackpackItemDto itemDto : dto.items) {
        try {
          Material material = Material.valueOf(itemDto.material.toUpperCase());
          BackpackItem item = BackpackItem.create();
          item.setMaterial(material);
          item.setAmount(itemDto.amount);
          backpack.getItems().add(item);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    return backpack;
  }

  /** Pickaxes from dto. */
  private static List<Pickaxe> pickaxesFromDto(List<PickaxeDto> dtos) {
    List<Pickaxe> pickaxes = new ArrayList<>();
    if (dtos == null) return pickaxes;
    for (PickaxeDto dto : dtos) {
      pickaxes.add(pickaxeFromDto(dto));
    }
    return pickaxes;
  }

  /** Maps a single {@link PickaxeDto} to a {@link Pickaxe} domain object. */
  public static Pickaxe pickaxeFromDto(PickaxeDto dto) {
    Pickaxe pickaxe = new Pickaxe();
    pickaxe.key = dto.id;
    pickaxe.name = dto.name;
    pickaxe.ownerUuid = UUID.fromString(dto.ownerUuid);
    pickaxe.level = dto.level;
    pickaxe.xp = dto.xp;
    pickaxe.target = dto.target;
    pickaxe.prestige = dto.prestige;
    pickaxe.evolution = dto.evolution;
    pickaxe.durability = dto.durability;
    pickaxe.essenceVessel = vesselFromDto(dto.essenceVessel, VesselType.ESSENCE);
    pickaxe.atomVessel = vesselFromDto(dto.atomVessel, VesselType.ATOMS);
    pickaxe.enchants = enchantsFromDto(dto.enchants);
    return pickaxe;
  }

  /** Vessel from dto. */
  private static Vessel vesselFromDto(VesselDto dto, VesselType defaultType) {
    if (dto == null) return new Vessel(defaultType, 0, 100, 0);
    VesselType type = safeEnum(dto.type, VesselType.class, defaultType);
    return new Vessel(type, dto.current, dto.target, dto.crystals);
  }

  /** Enchants from dto. */
  private static PickaxeEnchants enchantsFromDto(List<EnchantDto> dtos) {
    PickaxeEnchants enchants = new PickaxeEnchants();
    if (dtos == null) return enchants;
    for (EnchantDto dto : dtos) {
      EnchantData data = new EnchantData();
      data.name = dto.name;
      data.level = dto.level;
      data.enabled = dto.enabled;
      if (dto.stones != null) {
        for (int i = 0; i < EnchantData.MAX_SOCKET_COUNT && i < dto.stones.size(); i++) {
          String stone = dto.stones.get(i);
          data.socketStones[i] = (stone == null || stone.isEmpty()) ? null : stone;
        }
      }
      hydrateEnchantConfig(data);
      enchants.setEnchant(data);
    }
    return enchants;
  }

  /** Hydrate enchant config. */
  private static void hydrateEnchantConfig(EnchantData enchant) {
    String displayName =
        EnchantAnimationConfig.getDisplayName(enchant.name != null ? enchant.name : "");
    enchant.maxLevel = EnchantAnimationConfig.getEnchantMaxLevel(displayName);
    enchant.maxChance = EnchantAnimationConfig.getEnchantMaxChance(displayName);
  }

  /** Applies preferences. */
  private static void applyPreferences(PlayerProfile profile, List<PreferenceDto> prefs) {
    if (prefs == null || prefs.isEmpty()) return;

    for (PreferenceDto pref : prefs) {
      if (pref.option == null || pref.value == null) continue;
      String key = pref.option;
      String val = pref.value;

      switch (key) {
        case PREF_ANIMATION ->
            profile.preferences.animation =
                safeEnum(val, PreferenceType.Animation.class, PreferenceType.Animation.ENABLED);
        case PREF_MESSAGE ->
            profile.preferences.message =
                safeEnum(val, PreferenceType.Message.class, PreferenceType.Message.CHAT);
        case PREF_ENCHANT_STYLE ->
            profile.preferences.enchantStyle =
                safeEnum(
                    val,
                    PreferenceType.EnchantStyle.class,
                    PreferenceType.EnchantStyle.PORTAL_RIFT);
        case PREF_EVOLUTION_STYLE ->
            profile.preferences.evolutionStyle =
                safeEnum(
                    val,
                    PreferenceType.EvolutionStyle.class,
                    PreferenceType.EvolutionStyle.SUPERNOVA);
        case PREF_PICKUP_EFFECT ->
            profile.preferences.pickupEffect =
                safeEnum(
                    val, PreferenceType.PickupEffect.class, PreferenceType.PickupEffect.DEFAULT);
        case PREF_ENCHANT_SPEED ->
            profile.preferences.enchantAnimationSpeed =
                safeEnum(
                    val, PreferenceType.EnchantSpeed.class, PreferenceType.EnchantSpeed.NORMAL);
        case PREF_ESSENCE_SOUND -> profile.preferences.essenceSound = Boolean.parseBoolean(val);
        case PREF_ESSENCE_PARTICLES ->
            profile.preferences.essenceParticles = Boolean.parseBoolean(val);
        case PREF_ESSENCE_HOLOGRAM ->
            profile.preferences.essenceHologram = Boolean.parseBoolean(val);
        case PREF_ESSENCE_GLOW -> profile.preferences.essenceGlow = Boolean.parseBoolean(val);
        case PREF_ATOM_SOUND -> profile.preferences.atomSound = Boolean.parseBoolean(val);
        case PREF_ATOM_PARTICLES -> profile.preferences.atomParticles = Boolean.parseBoolean(val);
        case PREF_ATOM_HOLOGRAM -> profile.preferences.atomHologram = Boolean.parseBoolean(val);
        case PREF_ATOM_GLOW -> profile.preferences.atomGlow = Boolean.parseBoolean(val);
        case PREF_PICKAXE_MENU_ANIMATION ->
            profile.preferences.pickaxeMenuAnimation = Boolean.parseBoolean(val);
        default -> {
          if (key.startsWith(PREF_STORED_PICKAXE_PREFIX)) {
            try {
              int slot = Integer.parseInt(key.substring(PREF_STORED_PICKAXE_PREFIX.length()));
              while (profile.storedPickaxes.size() <= slot) profile.storedPickaxes.add("");
              profile.storedPickaxes.set(slot, val);
            } catch (NumberFormatException ignored) {
            }
          } else if (key.startsWith(PREF_ENCHANT_PREF_PREFIX)) {
            applyEnchantPref(
                profile.preferences, key.substring(PREF_ENCHANT_PREF_PREFIX.length()), val);
          }
        }
      }
    }

    while (profile.storedPickaxes.size() < 9) profile.storedPickaxes.add("");
  }

  /** Applies enchant pref. */
  private static void applyEnchantPref(PlayerPreferences prefs, String subKey, String val) {
    int lastUnderscore = subKey.lastIndexOf('_');
    if (lastUnderscore == -1) return;
    String enchantName = subKey.substring(0, lastUnderscore);
    String field = subKey.substring(lastUnderscore + 1);
    EnchantPreference ep =
        prefs.enchantPrefs.computeIfAbsent(enchantName, k -> new EnchantPreference());
    boolean b = Boolean.parseBoolean(val);
    switch (field) {
      case "hologram" -> ep.hologram = b;
      case "particles" -> ep.particles = b;
      case "sound" -> ep.sound = b;
      case "glow" -> ep.glow = b;
      default -> {}
    }
  }

  // ── Domain → DTO ──────────────────────────────────────────────────────────

  /** Maps a {@link PlayerProfile} domain object to the API DTO for persistence. */
  public static PlayerProfileDto toDto(PlayerProfile profile) {
    PlayerProfileDto dto = new PlayerProfileDto();

    dto.id = profile.id;
    if (profile.getPlayer() != null) {
      org.bukkit.entity.Player p = profile.getPlayer();
      dto.username = p.getName();
      URL skinUrl = p.getPlayerProfile().getTextures().getSkin();
      dto.skin = skinUrl != null ? skinUrl.toString() : null;
    }

    dto.wallet = walletToDto(profile.wallet);
    dto.ranks = ranksToDto(profile.ranks);
    dto.activity = (profile.activity != null ? profile.activity : PlayerActivity.NONE).name();
    dto.backpack = backpackToDto(profile.backpack);
    dto.pickaxes = pickaxesToDto(profile.pickaxes);
    dto.preferences = preferencesToDto(profile);

    return dto;
  }

  /** Wallet to dto. */
  private static WalletDto walletToDto(Wallet wallet) {
    WalletDto dto = new WalletDto();
    if (wallet == null) return dto;
    dto.coins = wallet.coins;
    dto.essence = wallet.essence;
    return dto;
  }

  /** Ranks to dto. */
  private static List<RankDto> ranksToDto(List<Rank> ranks) {
    List<RankDto> dtos = new ArrayList<>();
    if (ranks == null) return dtos;
    for (Rank rank : ranks) {
      RankDto dto = new RankDto();
      dto.prefix = rank.prefix;
      dto.type = (rank.type != null ? rank.type : RankType.DEFAULT).name();
      dtos.add(dto);
    }
    return dtos;
  }

  /** Backpack to dto. */
  private static BackpackDto backpackToDto(Backpack backpack) {
    BackpackDto dto = new BackpackDto();
    if (backpack == null) return dto;
    dto.total = backpack.getMaxSize();
    dto.size = backpack.getTotalItems();
    dto.full = backpack.isFull();
    for (BackpackItem item : backpack.getItems()) {
      if (item.markedForDeletion) continue;
      BackpackItemDto itemDto = new BackpackItemDto();
      itemDto.material = item.getMaterial().name();
      itemDto.amount = item.getAmount();
      dto.items.add(itemDto);
    }
    return dto;
  }

  /** Pickaxes to dto. */
  private static List<PickaxeDto> pickaxesToDto(List<Pickaxe> pickaxes) {
    List<PickaxeDto> dtos = new ArrayList<>();
    if (pickaxes == null) return dtos;
    for (Pickaxe pickaxe : pickaxes) {
      dtos.add(pickaxeToDto(pickaxe));
    }
    return dtos;
  }

  /** Maps a {@link Pickaxe} domain object to a {@link PickaxeDto} for API persistence. */
  public static PickaxeDto pickaxeToDto(Pickaxe pickaxe) {
    PickaxeDto dto = new PickaxeDto();
    dto.id = pickaxe.key;
    dto.name = pickaxe.name;
    dto.ownerUuid = pickaxe.ownerUuid.toString();
    dto.level = pickaxe.level;
    dto.xp = pickaxe.xp;
    dto.target = pickaxe.target;
    dto.prestige = pickaxe.prestige;
    dto.evolution = pickaxe.evolution;
    dto.durability = pickaxe.durability;
    dto.essenceVessel = vesselToDto(pickaxe.essenceVessel);
    dto.atomVessel = vesselToDto(pickaxe.atomVessel);
    dto.enchants = enchantsToDto(pickaxe.enchants);
    return dto;
  }

  /** Vessel to dto. */
  private static VesselDto vesselToDto(Vessel vessel) {
    VesselDto dto = new VesselDto();
    if (vessel == null) return dto;
    dto.type = (vessel.type != null ? vessel.type : VesselType.ESSENCE).name();
    dto.current = vessel.current;
    dto.target = vessel.target;
    dto.crystals = vessel.crystals;
    return dto;
  }

  /** Enchants to dto. */
  private static List<EnchantDto> enchantsToDto(PickaxeEnchants enchants) {
    List<EnchantDto> dtos = new ArrayList<>();
    if (enchants == null) return dtos;
    for (EnchantData data : enchants.items) {
      EnchantDto dto = new EnchantDto();
      dto.name = data.name;
      dto.level = data.level;
      dto.enabled = data.enabled;
      List<String> stones = new ArrayList<>();
      for (int i = 0; i < EnchantData.MAX_SOCKET_COUNT; i++) {
        stones.add(data.socketStones[i] != null ? data.socketStones[i] : "");
      }
      dto.stones = stones;
      dtos.add(dto);
    }
    return dtos;
  }

  /** Preferences to dto. */
  private static List<PreferenceDto> preferencesToDto(PlayerProfile profile) {
    List<PreferenceDto> prefs = new ArrayList<>();
    PlayerPreferences p = profile.preferences;

    prefs.add(pref(PREF_ANIMATION, p.animation.name()));
    prefs.add(pref(PREF_MESSAGE, p.message.name()));
    prefs.add(pref(PREF_ENCHANT_STYLE, p.enchantStyle.name()));
    prefs.add(pref(PREF_EVOLUTION_STYLE, p.evolutionStyle.name()));
    prefs.add(pref(PREF_PICKUP_EFFECT, p.pickupEffect.name()));
    prefs.add(pref(PREF_ENCHANT_SPEED, p.enchantAnimationSpeed.name()));
    prefs.add(pref(PREF_ESSENCE_SOUND, String.valueOf(p.essenceSound)));
    prefs.add(pref(PREF_ESSENCE_PARTICLES, String.valueOf(p.essenceParticles)));
    prefs.add(pref(PREF_ESSENCE_HOLOGRAM, String.valueOf(p.essenceHologram)));
    prefs.add(pref(PREF_ESSENCE_GLOW, String.valueOf(p.essenceGlow)));
    prefs.add(pref(PREF_ATOM_SOUND, String.valueOf(p.atomSound)));
    prefs.add(pref(PREF_ATOM_PARTICLES, String.valueOf(p.atomParticles)));
    prefs.add(pref(PREF_ATOM_HOLOGRAM, String.valueOf(p.atomHologram)));
    prefs.add(pref(PREF_ATOM_GLOW, String.valueOf(p.atomGlow)));
    prefs.add(pref(PREF_PICKAXE_MENU_ANIMATION, String.valueOf(p.pickaxeMenuAnimation)));

    for (int i = 0; i < profile.storedPickaxes.size(); i++) {
      prefs.add(pref(PREF_STORED_PICKAXE_PREFIX + i, profile.storedPickaxes.get(i)));
    }

    for (Map.Entry<String, EnchantPreference> entry : p.enchantPrefs.entrySet()) {
      String enchantName = entry.getKey();
      EnchantPreference ep = entry.getValue();
      prefs.add(
          pref(PREF_ENCHANT_PREF_PREFIX + enchantName + "_hologram", String.valueOf(ep.hologram)));
      prefs.add(
          pref(
              PREF_ENCHANT_PREF_PREFIX + enchantName + "_particles", String.valueOf(ep.particles)));
      prefs.add(pref(PREF_ENCHANT_PREF_PREFIX + enchantName + "_sound", String.valueOf(ep.sound)));
      prefs.add(pref(PREF_ENCHANT_PREF_PREFIX + enchantName + "_glow", String.valueOf(ep.glow)));
    }

    return prefs;
  }

  /** Pref. */
  private static PreferenceDto pref(String option, String value) {
    PreferenceDto dto = new PreferenceDto();
    dto.option = option;
    dto.value = value;
    return dto;
  }

  // ── Enum helpers ──────────────────────────────────────────────────────────

  /** Safe enum. */
  private static <E extends Enum<E>> E safeEnum(String value, Class<E> type, E fallback) {
    if (value == null) return fallback;
    try {
      return Enum.valueOf(type, value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }
}
