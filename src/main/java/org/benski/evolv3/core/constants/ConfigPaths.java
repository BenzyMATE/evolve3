package org.benski.evolv3.core.constants;

/**
 * Constants for configuration file paths. Use these instead of hardcoded strings to prevent typos
 * and improve refactoring. Organized by configuration file for easy navigation.
 */
public final class ConfigPaths {
  /** Creates a new ConfigPaths instance. */
  private ConfigPaths() {}

  /** Paths for the default configuration file. */
  public static final class Default {
    private Default() {}

    /** Base URL for the Evolve HTTP API. Example: "https://evolvemc-xxx.azurewebsites.net" */
    public static final String API_URL = "Database.ApiUrl";

    /** When false, SSL certificate verification is skipped (local development only). */
    public static final String API_SSL_VERIFY = "Database.SslVerify";

    /**
     * @deprecated Replaced by {@link #API_URL}. Will be removed once MongoDB is fully stripped.
     */
    @Deprecated public static final String DATABASE_MONGODB = "Database.MongoDB";

    /** Schedule interval configuration paths. Intervals are in ticks (20 ticks = 1 second). */
    public static final class Schedules {
      private Schedules() {}

      /** Pickaxe check schedule interval in ticks. Default: 100 ticks (5 seconds) */
      public static final String PICKAXE_CHECK_INTERVAL = "Schedules.PickaxeCheck.IntervalTicks";
    }

    /** Pickaxe configuration paths. */
    public static final class Pickaxe {
      private Pickaxe() {}

      public static final String DEFAULT_LEVEL = "Pickaxe.Defaults.Level";
      public static final String DEFAULT_XP = "Pickaxe.Defaults.XP";
      public static final String DEFAULT_TARGET = "Pickaxe.Defaults.Target";
      public static final String DEFAULT_ESSENCE_TARGET = "Pickaxe.Defaults.EssenceTarget";
      public static final String DEFAULT_CURRENT_ESSENCE = "Pickaxe.Defaults.CurrentEssence";
      public static final String DEFAULT_EVOLUTION_INTERVAL = "Pickaxe.Defaults.EvolutionInterval";
      public static final String DEFAULT_ENCHANT_ROLLS = "Pickaxe.Defaults.EnchantRolls";
      public static final String DEFAULT_EVOLUTION_ATOMS = "Pickaxe.Defaults.EvolutionAtoms";
      public static final String XP_MULTIPLIER = "Pickaxe.Defaults.XPMultiplier";
      public static final String ESSENCE_MULTIPLIER = "Pickaxe.Defaults.EssenceMultiplier";
      public static final String ESSENCE_TARGET_CAP = "Pickaxe.Defaults.EssenceTargetCap";

      public static final String NAME = "Pickaxe.Name";
      public static final String ACTION_BAR = "Pickaxe.ActionBar";
      public static final String LORE = "Pickaxe.Lore";
      public static final String LORE_FOOTER = "Pickaxe.LoreFooter";
      public static final String OMNITOOL_LORE_TAG = "Omnitool.LoreTag";

      public static final String PROGRESSBAR_SYMBOL_FILLED = "Pickaxe.Progressbar Symbol 1";
      public static final String PROGRESSBAR_SYMBOL_EMPTY = "Pickaxe.Progressbar Symbol 2";

      public static final String DURABILITY_WARNING_THRESHOLD =
          "Pickaxe.DurabilityWarningThreshold";

      public static final String DURABILITY_LABEL = "Pickaxe.DurabilityLabel";
      public static final String DURABILITY_BAR_PREFIX = "Pickaxe.DurabilityBarPrefix";

      public static final String HOLOGRAM_ENABLED = "Pickaxe.Hologram.Enabled";
      public static final String HOLOGRAM_TEXT = "Pickaxe.Hologram.Text";
    }

    /** Essence configuration paths. */
    public static final class Essence {
      private Essence() {}

      public static final String DISCOVERY_CHANCE = "Essence.DiscoveryChance";
      public static final String DISCOVERY_MIN_AMOUNT = "Essence.DiscoveryMinAmount";
      public static final String DISCOVERY_MAX_AMOUNT = "Essence.DiscoveryMaxAmount";
      public static final String TIER_INCREMENT = "Essence.LevelUpRewards.TierIncrement";
      public static final String MAX_CAP = "Essence.LevelUpRewards.MaxCap";

      public static final String HOLOGRAM_ENABLED = "Essence.Hologram.Enabled";
      public static final String HOLOGRAM_TEXT = "Essence.Hologram.Text";
    }

    /** Atom configuration paths. */
    public static final class Atoms {
      private Atoms() {}

      public static final String DISCOVERY_CHANCE = "Atoms.DiscoveryChance";
      public static final String DISCOVERY_MIN_AMOUNT = "Atoms.DiscoveryMinAmount";
      public static final String DISCOVERY_MAX_AMOUNT = "Atoms.DiscoveryMaxAmount";
      public static final String LEVEL_UP_REWARD = "Atoms.LevelUpReward";
      public static final String DEFAULT_ATOM_TARGET = "Atoms.DefaultTarget";
      public static final String ATOM_TARGET_MULTIPLIER = "Atoms.TargetMultiplier";

      public static final String HOLOGRAM_ENABLED = "Atoms.Hologram.Enabled";
      public static final String HOLOGRAM_TEXT = "Atoms.Hologram.Text";
    }

    /**
     * Block XP configuration path prefix. Usage: BLOCK_DEFAULTS + "." + Material.name() + ".XP"
     * Usage: BLOCK_DEFAULTS + "." + Material.name() + ".Sell"
     */
    public static final String BLOCK_DEFAULTS = "BlockDefaults";

    /**
     * Mine region keyword for WorldGuard integration. Only regions containing this keyword will
     * allow pickaxe progression.
     */
    public static final String MINE_REGION = "MineRegion";

    /** Action bar message shown when mining outside mine region. */
    public static final String NOT_IN_MINE_REGION_MESSAGE = "NotInMineRegionMessage";

    /** Animation budget configuration paths. */
    public static final class Animations {
      private Animations() {}

      /** Maximum number of animations that may be active concurrently. */
      public static final String MAX_CONCURRENT = "Animations.MaxConcurrentAnimations";

      /** Maximum sum of resource costs across all active animations. */
      public static final String MAX_RESOURCE_COST = "Animations.MaxResourceCost";
    }

    /** Player configuration paths. */
    public static final class Player {
      private Player() {}

      public static final String DEFAULT_PICKAXE_LIMIT = "Player.Defaults.PickaxeLimit";
      public static final String DEFAULT_ESSENCE = "Player.Defaults.Essence";
      public static final String DEFAULT_COINS = "Player.Defaults.Coins";
    }

    /** Armor configuration paths. */
    public static final class Armor {
      private Armor() {}

      public static final String DEFAULT_LEVEL = "Armor.Defaults.Level";
      public static final String DEFAULT_XP = "Armor.Defaults.XP";
      public static final String DEFAULT_TARGET = "Armor.Defaults.Target";
      public static final String DEFAULT_MULTIPLIER = "Armor.Defaults.Multiplier";
      public static final String XP_MULTIPLIER = "Armor.Defaults.XPMultiplier";
      public static final String MULTIPLIER_INCREMENT = "Armor.Defaults.MultiplierIncrement";
      public static final String EVOLUTION_INTERVAL = "Armor.Defaults.EvolutionInterval";

      public static final String HELMET_NAME = "Armor.Helmet.Name";
      public static final String HELMET_LORE = "Armor.Helmet.Lore";
      public static final String CHESTPLATE_NAME = "Armor.Chestplate.Name";
      public static final String CHESTPLATE_LORE = "Armor.Chestplate.Lore";
      public static final String LEGGINGS_NAME = "Armor.Leggings.Name";
      public static final String LEGGINGS_LORE = "Armor.Leggings.Lore";
      public static final String BOOTS_NAME = "Armor.Boots.Name";
      public static final String BOOTS_LORE = "Armor.Boots.Lore";
    }

    /** Backpack configuration paths. */
    public static final class Backpack {
      private Backpack() {}

      public static final String SIZE = "Backpack.Size";
      public static final String NAME = "Backpack.Name";
      public static final String FULL_MESSAGE = "Backpack.FullMessage";
      public static final String FULL_TITLE = "Backpack.FullTitle";
      public static final String FULL_SUBTITLE = "Backpack.FullSubtitle";
      public static final String LORE = "Backpack.Lore";
      public static final String CUSTOM_MODEL_DATA = "Backpack.CustomModelData";

      /**
       * Prefix for per-tier slot caps. Append the CustomModelData value (e.g. "Backpack.Tiers.2").
       */
      public static final String TIERS_PREFIX = "Backpack.Tiers.";

      public static final String HOLOGRAM_ENABLED = "Backpack.Hologram.Enabled";
      public static final String HOLOGRAM_TEXT = "Backpack.Hologram.Text";
    }

    /** Ancient Resin configuration paths. */
    public static final class AncientResin {
      private AncientResin() {}

      /** Percent chance (0–100) that breaking any log drops one Ancient Resin. */
      public static final String DROP_CHANCE = "AncientResin.DropChance";

      /** Number of Ancient Resin required to fully repair a pickaxe. */
      public static final String REPAIR_COST = "AncientResin.RepairCost";
    }

    /** Custom hunger system configuration paths. */
    public static final class Hunger {
      private Hunger() {}

      /** Blocks mined inside a mine region before hunger drops by 1 point. */
      public static final String BLOCKS_PER_DRAIN = "Hunger.BlocksPerDrain";

      /**
       * Prefix for per-material hunger restore values. Full path: {@code
       * Hunger.FoodRestore.MATERIAL_NAME} Fallback: {@code Hunger.FoodRestore.DEFAULT}
       */
      public static final String FOOD_RESTORE_PREFIX = "Hunger.FoodRestore";
    }

    /** Scoreboard configuration paths. */
    public static final class Scoreboard {
      private Scoreboard() {}

      public static final String ENABLED = "Scoreboard.Enabled";
      public static final String TITLE = "Scoreboard.Title";
      public static final String UPDATE_INTERVAL_TICKS = "Scoreboard.UpdateIntervalTicks";
      public static final String LINES = "Scoreboard.Lines";

      /** Template shown on the evo line during the atoms phase. Supports PAPI. */
      public static final String EVO_ATOM_TOGGLE_TEXT = "Scoreboard.EvoAtomToggleText";

      /** Seconds each phase is displayed before animating to the other. */
      public static final String EVO_ATOM_TOGGLE_SECONDS = "Scoreboard.EvoAtomToggleSeconds";

      /** Base text for the pickaxe-loading indicator (dots appended dynamically). */
      public static final String LOADING_TEXT = "Scoreboard.LoadingText";

      /** Label used in the level-up flash animation (e.g. "Level"). */
      public static final String LEVEL_UP_LABEL = "Scoreboard.LevelUpLabel";

      /** Text shown on the evo line while the enchant animation is rolling. */
      public static final String ENCHANT_ROLLING_TEXT = "Scoreboard.EnchantRollingText";

      /** Text shown on the evo line while the player is selecting an enchant. */
      public static final String ENCHANT_SELECTING_TEXT = "Scoreboard.EnchantSelectingText";

      /** Text shown on the rank line when the player can afford to rankup (animates in/out). */
      public static final String RANKUP_TOGGLE_TEXT = "Scoreboard.RankupToggleText";
    }

    /** Prestige configuration paths. */
    public static final class Prestige {
      private Prestige() {}

      public static final String MAX_PRESTIGE = "Prestige.MaxPrestige";
      public static final String LEVEL_PER_PRESTIGE = "Prestige.LevelRequirementPerPrestige";
      public static final String MULTIPLIER_PER_PRESTIGE = "Prestige.MultiplierPerPrestige";
      public static final String MAX_SOCKET_SLOTS = "Prestige.MaxSocketSlots";
      public static final String EPIC_BONUS = "Prestige.StoneBoost.EpicBonus";
      public static final String LEGENDARY_BONUS = "Prestige.StoneBoost.LegendaryBonus";
      public static final String MIN_STONE_RARITY = "Prestige.StoneBoost.MinRarity";
    }

    /** Pickaxe storage (limit) configuration paths. */
    public static final class PickaxeStorage {
      private PickaxeStorage() {}

      public static final String MAX_SLOTS = "PickaxeStorage.MaxSlots";
      public static final String BASE_COST = "PickaxeStorage.BaseCost";
      public static final String COST_MULTIPLIER = "PickaxeStorage.CostMultiplier";

      /** String list of Material names, one per slot index. */
      public static final String SLOT_MATERIALS = "PickaxeStorage.Slots";

      /** Log cost for a full pickaxe repair. */
      public static final String REPAIR_COST = "PickaxeStorage.RepairCost";

      /** Maximum number of pickaxes a player may claim within the rate-limit window. */
      public static final String MAX_CLAIMS_PER_WINDOW = "PickaxeStorage.MaxClaimsPerWindow";

      /** Duration of the rate-limit window in minutes. */
      public static final String CLAIM_WINDOW_MINUTES = "PickaxeStorage.ClaimWindowMinutes";
    }
  }

  /** Paths for the messages configuration file. Organized by category for easy navigation. */
  public static final class Messages {
    private Messages() {}

    /** Top-level prefix path. */
    public static final String PREFIX = "prefix";

    /** Pickaxe-related messages. */
    public static final class Pickaxe {
      private Pickaxe() {}

      public static final String LOADING_PROFILE = "pickaxe.loading-profile";
      public static final String PROFILE_CREATED = "pickaxe.profile-created";
      public static final String NO_PICKAXE = "pickaxe.no-pickaxe";
      public static final String ENCHANT_ROLL_GAINED = "pickaxe.enchant-roll-gained";
      public static final String ENCHANT_ROLL_TITLE = "pickaxe.enchant-roll-title";
      public static final String ENCHANT_ROLL_SUBTITLE = "pickaxe.enchant-roll-subtitle";

      public static final String LEVEL_UP = "pickaxe.level-up";

      public static final String LEVEL_UP_TITLE = "pickaxe.level-up-title.title";
      public static final String LEVEL_UP_SUBTITLE = "pickaxe.level-up-title.subtitle";

      public static final String PICKAXE_BROKEN = "pickaxe.pickaxe-broken";
      public static final String DROP_CONFIRMATION = "pickaxe.drop-confirmation";
      public static final String OMNITOOL_DROP_CONFIRMATION = "pickaxe.omnitool-drop-confirmation";
      public static final String PICKUP_NEW_OWNER = "pickaxe.pickup-new-owner";
      public static final String DURABILITY_WARNING = "pickaxe.durability-warning";

      /**
       * Template for the floating TextDisplay shown above a dropped pickaxe. Supports %level%,
       * %xp%, %target% placeholders.
       */
      public static final String DROPPED_DISPLAY_TEXT = "pickaxe.dropped-display-text";

      public static final String ENCHANT_INSUFFICIENT_ESSENCE =
          "pickaxe.enchant.insufficient-essence";
      public static final String ENCHANT_UPGRADE_SUCCESS = "pickaxe.enchant.upgrade-success";
      public static final String ENCHANT_MAX_LEVEL = "pickaxe.enchant.max-level";
      public static final String ENCHANT_ALL_MAXED = "pickaxe.enchant.all-maxed";
      public static final String ENCHANT_NOT_AVAILABLE = "pickaxe.enchant.not-available";
      public static final String ENCHANT_UNLOCKED = "pickaxe.enchant.unlocked";
      public static final String ENCHANT_NOT_UNLOCKED = "pickaxe.enchant.not-unlocked";
      public static final String ENCHANT_NO_SPINS = "pickaxe.enchant.no-spins";
      public static final String ENCHANT_AREA_SECURED = "pickaxe.enchant.area-secured";
      public static final String ENCHANT_ACCESS_DENIED = "pickaxe.enchant.access-denied";
      public static final String ENCHANT_CONFIRM_SELECTION = "pickaxe.enchant.confirm-selection";
      public static final String ENCHANT_TOGGLE_ENABLED = "pickaxe.enchant.toggle-enabled";
      public static final String ENCHANT_TOGGLE_DISABLED = "pickaxe.enchant.toggle-disabled";

      public static final String MENU_EVOLUTION_COMING_SOON = "pickaxe.menu.evolution-coming-soon";

      public static final String EVOLVE_NOT_AVAILABLE = "pickaxe.evolve.not-available";
      public static final String EVOLVE_INSUFFICIENT_SPINS = "pickaxe.evolve.insufficient-spins";
      public static final String EVOLVE_MAX_TIER = "pickaxe.evolve.max-tier";
      public static final String EVOLVE_SUCCESS = "pickaxe.evolve.success";
      public static final String EVOLVE_SUCCESS_TITLE = "pickaxe.evolve.success-title";
      public static final String EVOLVE_SUCCESS_SUBTITLE = "pickaxe.evolve.success-subtitle";
      public static final String EVOLVE_TIER_SUCCESS = "pickaxe.evolve.evolve-success";
      public static final String EVOLVE_STONE_PLACEHOLDER = "pickaxe.evolve.stone-placeholder";
      public static final String EVOLVE_RESULT = "pickaxe.evolve.result";
      public static final String EVOLVE_ESSENCE_REWARD = "Pickaxe.Defaults.EvolutionEssenceReward";

      public static final String ENCHANT_SELECTION_ALREADY_ACTIVE =
          "enchant-selection.already-active";

      public static final String PRESTIGE_ACTION_BAR = "pickaxe.prestige.action-bar";
      public static final String PRESTIGE_LORE_LINE = "pickaxe.prestige.lore-line";
      public static final String PRESTIGE_SCOREBOARD_LINE = "pickaxe.prestige.scoreboard-line";
      public static final String PRESTIGE_SUCCESS = "pickaxe.prestige.success";
      public static final String PRESTIGE_STONE_GAINED = "pickaxe.prestige.stone-gained";
      public static final String PRESTIGE_CONFIRM_TITLE = "pickaxe.prestige.confirm-title";
      public static final String PRESTIGE_STONE_SELECT_TITLE =
          "pickaxe.prestige.stone-select-title";
      public static final String PRESTIGE_ALREADY_MAX = "pickaxe.prestige.already-max";
      public static final String PRESTIGE_NOT_ELIGIBLE = "pickaxe.prestige.not-eligible";
      public static final String PRESTIGE_ERROR = "pickaxe.prestige.error";
    }

    /** General plugin messages. */
    public static final class General {
      private General() {}

      public static final String PREFIX = "general.prefix";
      public static final String NO_PERMISSION = "general.no-permission";
      public static final String PLAYER_ONLY = "general.player-only";
      public static final String UNKNOWN_COMMAND = "general.unknown-command";
      public static final String RELOAD_SUCCESS = "general.reload-success";
      public static final String RELOAD_FAILED = "general.reload-failed";
    }

    /** Error messages. */
    public static final class Errors {
      private Errors() {}

      public static final String DATABASE_ERROR = "errors.database-error";
      public static final String INTERNAL_ERROR = "errors.internal-error";
      public static final String INVALID_ARGUMENTS = "errors.invalid-arguments";

      /** Sent when a menu type is not registered. Supports %menu% placeholder. */
      public static final String MENU_NOT_FOUND = "errors.menu-not-found";
    }

    /** Statistic command messages (/e statistic). */
    public static final class Statistic {
      private Statistic() {}

      public static final String SUCCESS = "statistic.success";
      public static final String PLAYER_NOT_FOUND = "statistic.player-not-found";
      public static final String PROFILE_NOT_LOADED = "statistic.profile-not-loaded";
      public static final String UNKNOWN_TYPE = "statistic.unknown-type";
      public static final String UNKNOWN_STAT = "statistic.unknown-stat";
      public static final String INVALID_AMOUNT = "statistic.invalid-amount";
    }

    /** Admin command messages (/evolv3 help and /evolv3 info). */
    public static final class Admin {
      private Admin() {}

      /** Multi-line list shown by /evolv3 help. */
      public static final String HELP = "admin.help";

      /** Multi-line list shown by /evolv3 info. */
      public static final String INFO = "admin.info";
    }

    /** Balance command messages (/bal). Placeholders: %name%, %balance% */
    public static final class Bal {
      private Bal() {}

      public static final String PLAYER_NOT_FOUND = "bal.player-not-found";
      public static final String OTHER_BALANCE = "bal.other-balance";
      public static final String OWN_BALANCE = "bal.own-balance";
    }

    public static final class Skin {
      private Skin() {}

      public static final String PLAYER_NOT_FOUND = "skin.player-not-found";
    }

    /** Mine-related messages. */
    public static final class Mine {
      private Mine() {}

      public static final String RESET_BROADCAST = "mine.reset-broadcast";
      public static final String RESET_WARNING = "mine.reset-warning";
      public static final String MINE_CREATED = "mine.mine-created";
      public static final String MINE_REMOVED = "mine.mine-removed";
      public static final String MINE_NOT_FOUND = "mine.mine-not-found";
      public static final String CONTENTS_ADDED = "mine.contents-added";
      public static final String CONTENTS_REMOVED = "mine.contents-removed";
      public static final String CAVE_ENTRY = "mine.cave-entry";
      public static final String CAVE_REWARD = "mine.cave-reward";
      public static final String CAVE_ENTRY_SUBTITLE = "mine.cave-entry-subtitle";
      public static final String CAVE_EXIT = "mine.cave-exit";
      public static final String CAVE_EXIT_SUBTITLE = "mine.cave-exit-subtitle";
      public static final String CAVE_LOOTED = "mine.cave-looted";
      public static final String CAVE_LOOTED_SUBTITLE = "mine.cave-looted-subtitle";
      public static final String CAVE_DISCOVERED_BROADCAST = "mine.cave-discovered-broadcast";
      public static final String SPAWN_SET = "mine.spawn-set";
      public static final String RESETTING = "mine.resetting";
      public static final String RELOADED = "mine.reloaded";
      public static final String ALREADY_EXISTS = "mine.already-exists";
      public static final String NO_SELECTION = "mine.no-selection";
      public static final String SETTING_UPDATED = "mine.setting-updated";
      public static final String INVALID_MATERIAL = "mine.invalid-material";
      public static final String NOT_IN_COMPOSITION = "mine.not-in-composition";
      public static final String NO_MINES = "mine.no-mines";
      public static final String HOLOGRAM_POS_SET = "mine.hologram-pos-set";
      public static final String HOLOGRAM_POS_RESET = "mine.hologram-pos-reset";
      public static final String HOLOGRAM_SCALE_SET = "mine.hologram-scale-set";
      public static final String HOLOGRAM_BILLBOARD_SET = "mine.hologram-billboard-set";
      public static final String HOLOGRAM_FACING_SET = "mine.hologram-facing-set";
      public static final String HOLOGRAM_COLOR_SET = "mine.hologram-color-set";
      public static final String HOLOGRAM_COLOR_RESET = "mine.hologram-color-reset";
      public static final String HOLOGRAM_ARROW_SET = "mine.hologram-arrow-set";
      public static final String HOLOGRAM_ICON_SET = "mine.hologram-icon-set";
    }

    /** Scroll item messages. */
    public static final class Scroll {
      private Scroll() {}

      public static final String REDEEMED = "scroll.redeemed";
      public static final String COMBINED = "scroll.combined";
      public static final String APPLIED_XP = "scroll.applied-xp";
      public static final String APPLIED_ATOMS = "scroll.applied-atoms";
      public static final String USE_ON_PICKAXE = "scroll.use-on-pickaxe";
      public static final String PICKAXE_NOT_LOADED = "scroll.pickaxe-not-loaded";
      public static final String GIVEN = "scroll.given";
      public static final String LIST_HEADER = "scroll.list-header";
      public static final String LIST_ENTRY = "scroll.list-entry";
    }

    /** Border-related messages. */
    public static final class Border {
      private Border() {}

      public static final String WALL_SET = "border.wall-set";
      public static final String WALL_REMOVED = "border.wall-removed";
      public static final String ALL_REMOVED = "border.all-removed";
      public static final String MAX_REACHED = "border.max-reached";
      public static final String UNKNOWN_RANK = "border.unknown-rank";
      public static final String NO_BORDER_AT_SLOT = "border.no-border-at-slot";
      public static final String NO_BORDERS_FOR_RANK = "border.no-borders-for-rank";
      public static final String NO_BORDERS = "border.no-borders";
      public static final String RELOADED = "border.reloaded";
      public static final String NO_SELECTION = "border.no-selection";
      public static final String NO_WORLDEDIT = "border.no-worldedit";
    }

    /** Beam-related messages. */
    public static final class Beam {
      private Beam() {}

      public static final String CREATED = "beam.created";
      public static final String REMOVED = "beam.removed";
      public static final String NOT_FOUND = "beam.not-found";
      public static final String UNKNOWN_COLOUR = "beam.unknown-colour";
      public static final String NO_BEAMS = "beam.no-beams";
      public static final String RELOADED = "beam.reloaded";
      public static final String NO_SELECTION = "beam.no-selection";
      public static final String NO_WORLDEDIT = "beam.no-worldedit";
      public static final String AVAILABLE_COLOURS = "beam.available-colours";
    }

    /** Launch pad-related messages. */
    public static final class LaunchPad {
      private LaunchPad() {}

      public static final String SET_MODE = "launchpad.set-mode";
      public static final String SET_POWER = "launchpad.set-power";
      public static final String REMOVE_MODE = "launchpad.remove-mode";
      public static final String CANCEL_HINT = "launchpad.cancel-hint";
      public static final String CANCELLED = "launchpad.cancelled";
      public static final String NOT_IN_MODE = "launchpad.not-in-mode";
      public static final String PAD_CREATED = "launchpad.pad-created";
      public static final String PAD_REMOVED = "launchpad.pad-removed";
      public static final String ALREADY_EXISTS = "launchpad.already-exists";
      public static final String NOT_PRESSURE_PLATE = "launchpad.not-pressure-plate";
      public static final String NO_PAD_AT_LOCATION = "launchpad.no-pad-at-location";
      public static final String CREATE_FAILED = "launchpad.create-failed";
      public static final String NO_PADS = "launchpad.no-pads";
      public static final String RELOADED = "launchpad.reloaded";
    }

    /** Enchantment trigger messages. */
    public static final class Enchants {
      private Enchants() {}

      public static final String EFFICIENCY_APPLIED = "enchants.efficiency.applied";

      public static final String EXPLOSIVE_TRIGGERED = "enchants.explosive.triggered";
      public static final String EXPLOSIVE_TRIGGERED_TITLE = "enchants.explosive.triggered-title";
      public static final String EXPLOSIVE_TRIGGERED_SUBTITLE =
          "enchants.explosive.triggered-subtitle";

      public static final String FROSTBITE_TRIGGERED = "enchants.frostbite.triggered";
      public static final String FROSTBITE_TRIGGERED_TITLE = "enchants.frostbite.triggered-title";
      public static final String FROSTBITE_TRIGGERED_SUBTITLE =
          "enchants.frostbite.triggered-subtitle";

      public static final String FURNACE_TRIGGERED = "enchants.furnace.triggered";
      public static final String FURNACE_TRIGGERED_TITLE = "enchants.furnace.triggered-title";
      public static final String FURNACE_TRIGGERED_SUBTITLE = "enchants.furnace.triggered-subtitle";

      public static final String ALCHEMY_TRIGGERED = "enchants.alchemy.triggered";
      public static final String ALCHEMY_TRIGGERED_TITLE = "enchants.alchemy.triggered-title";
      public static final String ALCHEMY_TRIGGERED_SUBTITLE = "enchants.alchemy.triggered-subtitle";

      public static final String SCAVENGER_TRIGGERED = "enchants.scavenger.triggered";
      public static final String SCAVENGER_TRIGGERED_TITLE = "enchants.scavenger.triggered-title";
      public static final String SCAVENGER_TRIGGERED_SUBTITLE =
          "enchants.scavenger.triggered-subtitle";

      public static final String MERCHANT_TRIGGERED = "enchants.merchant.triggered";
      public static final String MERCHANT_TRIGGERED_TITLE = "enchants.merchant.triggered-title";
      public static final String MERCHANT_TRIGGERED_SUBTITLE =
          "enchants.merchant.triggered-subtitle";

      public static final String EXTRACTION_TRIGGERED = "enchants.extraction.triggered";
      public static final String EXTRACTION_TRIGGERED_TITLE = "enchants.extraction.triggered-title";
      public static final String EXTRACTION_TRIGGERED_SUBTITLE =
          "enchants.extraction.triggered-subtitle";

      public static final String AUTO_SELL_TRIGGERED = "enchants.autosell.triggered";
      public static final String AUTO_SELL_TRIGGERED_TITLE = "enchants.autosell.triggered-title";
      public static final String AUTO_SELL_TRIGGERED_SUBTITLE =
          "enchants.autosell.triggered-subtitle";

      public static final String DRILL_TRIGGERED = "enchants.drill.triggered";
      public static final String DRILL_TRIGGERED_TITLE = "enchants.drill.triggered-title";
      public static final String DRILL_TRIGGERED_SUBTITLE = "enchants.drill.triggered-subtitle";

      public static final String LAZER_TRIGGERED = "enchants.lazer.triggered";
      public static final String LAZER_TRIGGERED_TITLE = "enchants.lazer.triggered-title";
      public static final String LAZER_TRIGGERED_SUBTITLE = "enchants.lazer.triggered-subtitle";

      public static final String THOR_TRIGGERED = "enchants.thor.triggered";
      public static final String THOR_TRIGGERED_TITLE = "enchants.thor.triggered-title";
      public static final String THOR_TRIGGERED_SUBTITLE = "enchants.thor.triggered-subtitle";

      public static final String VORTEX_TRIGGERED = "enchants.vortex.triggered";
      public static final String VORTEX_TRIGGERED_TITLE = "enchants.vortex.triggered-title";
      public static final String VORTEX_TRIGGERED_SUBTITLE = "enchants.vortex.triggered-subtitle";

      public static final String CASCADE_TRIGGERED = "enchants.cascade.triggered";
      public static final String CASCADE_TRIGGERED_TITLE = "enchants.cascade.triggered-title";
      public static final String CASCADE_TRIGGERED_SUBTITLE = "enchants.cascade.triggered-subtitle";

      public static final String JACKHAMMER_TRIGGERED = "enchants.jackhammer.triggered";
      public static final String JACKHAMMER_TRIGGERED_TITLE = "enchants.jackhammer.triggered-title";
      public static final String JACKHAMMER_TRIGGERED_SUBTITLE =
          "enchants.jackhammer.triggered-subtitle";

      public static final String NUCLEAR_SHOWER_TRIGGERED = "enchants.nuclearshower.triggered";
      public static final String NUCLEAR_SHOWER_TRIGGERED_TITLE =
          "enchants.nuclearshower.triggered-title";
      public static final String NUCLEAR_SHOWER_TRIGGERED_SUBTITLE =
          "enchants.nuclearshower.triggered-subtitle";
    }

    /** Player-related messages. */
    public static final class Player {
      private Player() {}

      public static final String LOADING_PROFILE = "player.loading-profile";
      public static final String PROFILE_CREATED = "player.profile-created";
      public static final String PROFILE_LOADED = "player.profile-loaded";
    }

    /** Join message configuration. */
    public static final class JoinMessage {
      private JoinMessage() {}

      public static final String ENABLED = "join-message.enabled";
      public static final String SKIN_PADDING = "join-message.skin-padding";
      public static final String LINES = "join-message.lines";
      public static final String FIRST_JOIN_LINES = "join-message.first-join-lines";
    }

    /** Armor-related messages. */
    public static final class Armor {
      private Armor() {}

      public static final String LOADING_PROFILE = "armor.loading-profile";
      public static final String PROFILE_CREATED = "armor.profile-created";
      public static final String EVOLUTION = "armor.evolution";

      public static final String LEVEL_UP_HELMET = "armor.level-up.helmet";
      public static final String LEVEL_UP_CHESTPLATE = "armor.level-up.chestplate";
      public static final String LEVEL_UP_LEGGINGS = "armor.level-up.leggings";
      public static final String LEVEL_UP_BOOTS = "armor.level-up.boots";

      public static final String LEVEL_UP_TITLE_HELMET = "armor.level-up-title.helmet.title";
      public static final String LEVEL_UP_TITLE_HELMET_SUB = "armor.level-up-title.helmet.subtitle";
      public static final String LEVEL_UP_TITLE_CHESTPLATE =
          "armor.level-up-title.chestplate.title";
      public static final String LEVEL_UP_TITLE_CHESTPLATE_SUB =
          "armor.level-up-title.chestplate.subtitle";
      public static final String LEVEL_UP_TITLE_LEGGINGS = "armor.level-up-title.leggings.title";
      public static final String LEVEL_UP_TITLE_LEGGINGS_SUB =
          "armor.level-up-title.leggings.subtitle";
      public static final String LEVEL_UP_TITLE_BOOTS = "armor.level-up-title.boots.title";
      public static final String LEVEL_UP_TITLE_BOOTS_SUB = "armor.level-up-title.boots.subtitle";

      public static final String TOGGLE_ENABLED = "armor.toggle.enabled";
      public static final String TOGGLE_DISABLED = "armor.toggle.disabled";

      public static final String REMOVE_BLOCKED = "armor.remove.blocked";

      public static final String DROP_CONFIRMATION = "armor.drop.confirmation";
      public static final String DROP_NEW_OWNER = "armor.drop.new-owner";
      public static final String DROP_NO_SLOTS = "armor.drop.no-slots";
    }

    /** Backpack-related messages. */
    public static final class Backpack {
      private Backpack() {}

      public static final String FULL = "backpack.full";
      public static final String ITEM_ADDED = "backpack.item-added";
      public static final String SELL_ITEMS = "backpack.sell-items";
      public static final String NOTHING_SELECTED = "backpack.nothing-selected";
    }

    /** Pickaxe limit/storage messages. */
    public static final class PickaxeLimit {
      private PickaxeLimit() {}

      public static final String PROFILE_LOAD_ERROR = "pickaxe-limit.profile-load-error";
      public static final String ALREADY_HAS_PICKAXE = "pickaxe-limit.already-has-pickaxe";
      public static final String COOLDOWN_ACTIVE = "pickaxe-limit.cooldown-active";
      public static final String COOLDOWN_CLICK = "pickaxe-limit.cooldown-click";
      public static final String BLOCKED_CLICK = "pickaxe-limit.blocked-click";
      public static final String ALL_SLOTS_UNLOCKED = "pickaxe-limit.all-slots-unlocked";
      public static final String SLOT_PURCHASED = "pickaxe-limit.slot-purchased";
      public static final String INSUFFICIENT_ESSENCE = "pickaxe-limit.insufficient-essence";
      public static final String PURCHASE_ERROR = "pickaxe-limit.purchase-error";
      public static final String PICKAXE_DEPOSITED = "pickaxe-limit.pickaxe-deposited";
      public static final String PICKAXE_NOT_FOUND = "pickaxe-limit.pickaxe-not-found";
      public static final String MUST_DEPOSIT_BEFORE_WITHDRAW =
          "pickaxe-limit.must-deposit-before-withdraw";
      public static final String INVENTORY_FULL = "pickaxe-limit.inventory-full";
      public static final String INVENTORY_FULL_TITLE = "pickaxe-limit.inventory-full-title";
      public static final String INVENTORY_FULL_SUBTITLE = "pickaxe-limit.inventory-full-subtitle";
      public static final String PICKAXE_WITHDRAWN = "pickaxe-limit.pickaxe-withdrawn";
      public static final String MUST_DEPOSIT_BEFORE_CLAIM =
          "pickaxe-limit.must-deposit-before-claim";
      public static final String PICKAXE_CLAIMED = "pickaxe-limit.pickaxe-claimed";
      public static final String MUST_DEPOSIT_BEFORE_CRAFT =
          "pickaxe-limit.must-deposit-before-craft";
      public static final String INSUFFICIENT_WOOD = "pickaxe-limit.insufficient-wood";
      public static final String WOOD_CONSUMPTION_ERROR = "pickaxe-limit.wood-consumption-error";
      public static final String PICKAXE_CRAFTED = "pickaxe-limit.pickaxe-crafted";
      public static final String CRAFT_SAVE_ERROR = "pickaxe-limit.craft-save-error";
      public static final String REPAIR_SUCCESS = "pickaxe-limit.repair-success";
      public static final String REPAIR_INSUFFICIENT_WOOD =
          "pickaxe-limit.repair-insufficient-wood";
      public static final String REPAIR_ALREADY_FULL = "pickaxe-limit.repair-already-full";
      public static final String CLAIM_RATE_LIMITED = "pickaxe-limit.claim-rate-limited";
      public static final String PICKUP_NO_SLOTS = "pickaxe-limit.pickup-no-slots";
    }

    /** Enchant animation action bar messages. */
    public static final class EnchantAnimation {
      private EnchantAnimation() {}

      /** Action bar shown when the selection is ready. */
      public static final String SELECTION_PROMPT = "enchant-animation.selection-prompt";

      /** Action bar shown briefly when an enchant is unlocked (phase A). */
      public static final String ENCHANT_UNLOCKED = "enchant-animation.enchant-unlocked";
    }

    public static final class PickupAnimation {
      private PickupAnimation() {}

      public static final String USAGE_SET = "pickup-animation.usage-set";
      public static final String UNKNOWN_SUBCOMMAND = "pickup-animation.unknown-subcommand";
      public static final String CURRENT_HEADER = "pickup-animation.current-header";
      public static final String CURRENT = "pickup-animation.current";
      public static final String HINT = "pickup-animation.hint";
      public static final String LIST_HEADER = "pickup-animation.list-header";
      public static final String LIST_ITEM_SELECTED = "pickup-animation.list-item-selected";
      public static final String LIST_ITEM = "pickup-animation.list-item";
      public static final String LIST_FOOTER = "pickup-animation.list-footer";
      public static final String SET_SUCCESS = "pickup-animation.set-success";
      public static final String INVALID_ANIMATION = "pickup-animation.invalid-animation";
      public static final String CYCLED = "pickup-animation.cycled";
      public static final String OTHERS_ENABLED = "pickup-animation.others-enabled";
      public static final String OTHERS_DISABLED = "pickup-animation.others-disabled";
    }

    /** Furnace smelt menu messages. */
    public static final class FurnaceSmelt {
      private FurnaceSmelt() {}

      /** Lore tag appended to smeltable items in the backpack menu. Supports %result%. */
      public static final String SMELTABLE_TAG = "furnace-smelt.smeltable-tag";
    }

    /** Hunger system message paths. */
    public static final class Hunger {
      private Hunger() {}

      /** Message sent when a player consumes a food item. Supports %food_name%, %hunger%. */
      public static final String CONSUME_MESSAGE = "hunger.consume-message";
    }
  }

  /** Paths for the menus configuration file. */
  public static final class Menus {
    private Menus() {}

    /**
     * Pickaxe limit menu slot item paths. Tokens supported in lore: {cost}, {wood}, {slot},
     * {amount_colored}
     */
    public static final class PickaxeLimitMenu {
      private PickaxeLimitMenu() {}

      public static final String TITLE = "Pickaxe Limit Menu.Title";
      public static final String CLAIM_NAME = "Pickaxe Limit Menu.ClaimSlot.Name";
      public static final String CLAIM_LORE = "Pickaxe Limit Menu.ClaimSlot.Lore";
      public static final String BLOCKED_NAME = "Pickaxe Limit Menu.BlockedSlot.Name";
      public static final String BLOCKED_LORE = "Pickaxe Limit Menu.BlockedSlot.Lore";
      public static final String LOCKED_NAME = "Pickaxe Limit Menu.LockedSlot.Name";
      public static final String LOCKED_LORE = "Pickaxe Limit Menu.LockedSlot.Lore";
      public static final String ACTIVE_NAME = "Pickaxe Limit Menu.ActiveSlot.Name";
      public static final String PURCHASE_NAME_AFFORDABLE =
          "Pickaxe Limit Menu.PurchaseButton.NameAffordable";
      public static final String PURCHASE_NAME_TOO_EXPENSIVE =
          "Pickaxe Limit Menu.PurchaseButton.NameTooExpensive";
      public static final String PURCHASE_LORE = "Pickaxe Limit Menu.PurchaseButton.Lore";
      public static final String PURCHASE_ACTION_AFFORDABLE =
          "Pickaxe Limit Menu.PurchaseButton.ActionAffordable";
      public static final String PURCHASE_ACTION_TOO_EXPENSIVE =
          "Pickaxe Limit Menu.PurchaseButton.ActionTooExpensive";
      public static final String MAXED_NAME = "Pickaxe Limit Menu.MaxedButton.Name";
      public static final String MAXED_LORE = "Pickaxe Limit Menu.MaxedButton.Lore";
    }

    /** Pickaxe menu panel item paths. */
    public static final class PickaxeMenu {
      private PickaxeMenu() {}

      public static final String ESSENCE_NAME = "Pickaxe Menu.Essence.Name";
      public static final String ESSENCE_LORE = "Pickaxe Menu.Essence.Lore";
      public static final String EVOLVE_NAME = "Pickaxe Menu.Evolve.Name";
      public static final String EVOLVE_LORE = "Pickaxe Menu.Evolve.Lore";

      public static final String ENCHANT_LOCKED_NAME = "Pickaxe Menu.LockedEnchant.Name";
      public static final String ENCHANT_LOCKED_LORE = "Pickaxe Menu.LockedEnchant.Lore";
      public static final String STORAGE_ACTION_LINE = "Pickaxe Menu.StorageAction";
      public static final String REPAIR_ACTION_LINE = "Pickaxe Menu.RepairAction";

      public static final String STONES_HEADER = "Pickaxe Menu.Stones.Header";
      public static final String STONES_FILLED_LINE = "Pickaxe Menu.Stones.FilledLine";
      public static final String STONES_EMPTY_LINE = "Pickaxe Menu.Stones.EmptyLine";
      public static final String STONES_ACTION_LINE = "Pickaxe Menu.Stones.ActionLine";
    }

    /** Preferences menu item paths. */
    public static final class PreferencesMenu {
      private PreferencesMenu() {}

      public static final String TITLE = "Preferences Menu.Title";
      public static final String ANIMATIONS_MATERIAL = "Preferences Menu.Animations.Material";
      public static final String ANIMATIONS_NAME = "Preferences Menu.Animations.Name";
      public static final String ANIMATIONS_LORE = "Preferences Menu.Animations.Lore";
      public static final String ENCHANTS_MATERIAL = "Preferences Menu.Enchants.Material";
      public static final String ENCHANTS_NAME = "Preferences Menu.Enchants.Name";
      public static final String ENCHANTS_LORE = "Preferences Menu.Enchants.Lore";
      public static final String MESSAGES_MATERIAL = "Preferences Menu.Messages.Material";
      public static final String MESSAGES_NAME = "Preferences Menu.Messages.Name";
      public static final String MESSAGES_LORE = "Preferences Menu.Messages.Lore";
      public static final String EXPERIMENTAL_MATERIAL = "Preferences Menu.Experimental.Material";
      public static final String EXPERIMENTAL_NAME = "Preferences Menu.Experimental.Name";
      public static final String EXPERIMENTAL_LORE = "Preferences Menu.Experimental.Lore";
      public static final String CLOSE_MATERIAL = "Preferences Menu.Close.Material";
      public static final String CLOSE_NAME = "Preferences Menu.Close.Name";
      public static final String CLOSE_LORE = "Preferences Menu.Close.Lore";
    }

    /** Experimental preferences sub-menu item paths. */
    public static final class ExperimentalMenu {
      private ExperimentalMenu() {}

      public static final String TITLE = "Experimental Menu.Title";
      public static final String BACK_MATERIAL = "Experimental Menu.Back.Material";
      public static final String BACK_NAME = "Experimental Menu.Back.Name";
      public static final String BACK_LORE = "Experimental Menu.Back.Lore";
      public static final String PICKAXE_ANIM_ENABLED_MAT =
          "Experimental Menu.PickaxeAnimation.EnabledMaterial";
      public static final String PICKAXE_ANIM_DISABLED_MAT =
          "Experimental Menu.PickaxeAnimation.DisabledMaterial";
      public static final String PICKAXE_ANIM_NAME = "Experimental Menu.PickaxeAnimation.Name";
      public static final String PICKAXE_ANIM_LORE = "Experimental Menu.PickaxeAnimation.Lore";
    }

    /** Enchants preferences menu item paths. */
    public static final class EnchantsMenu {
      private EnchantsMenu() {}

      public static final String TITLE = "Enchants Menu.Title";
      public static final String BACK_MATERIAL = "Enchants Menu.Back.Material";
      public static final String BACK_NAME = "Enchants Menu.Back.Name";
      public static final String BACK_LORE = "Enchants Menu.Back.Lore";
      public static final String PREV_MATERIAL = "Enchants Menu.Prev.Material";
      public static final String PREV_NAME = "Enchants Menu.Prev.Name";
      public static final String PREV_LORE = "Enchants Menu.Prev.Lore";
      public static final String NEXT_MATERIAL = "Enchants Menu.Next.Material";
      public static final String NEXT_NAME = "Enchants Menu.Next.Name";
      public static final String NEXT_LORE = "Enchants Menu.Next.Lore";
      public static final String PAGE_INFO_MATERIAL = "Enchants Menu.PageInfo.Material";
      public static final String PAGE_INFO_NAME = "Enchants Menu.PageInfo.Name";
      public static final String ENCHANT_ITEM_LORE = "Enchants Menu.EnchantItem.Lore";
    }

    /** Enchant option sub-menu item paths. */
    public static final class EnchantOptionMenu {
      private EnchantOptionMenu() {}

      /** Menu title. Supports %enchant% placeholder. */
      public static final String MENU_TITLE = "Enchant Option Menu.MenuTitle";

      public static final String BACK_MATERIAL = "Enchant Option Menu.Back.Material";
      public static final String BACK_NAME = "Enchant Option Menu.Back.Name";
      public static final String BACK_LORE = "Enchant Option Menu.Back.Lore";
      public static final String ENABLED_MATERIAL = "Enchant Option Menu.EnabledMaterial";
      public static final String DISABLED_MATERIAL = "Enchant Option Menu.DisabledMaterial";
      public static final String MESSAGE_NAME = "Enchant Option Menu.Message.Name";
      public static final String MESSAGE_LORE = "Enchant Option Menu.Message.Lore";
      public static final String TITLE_NAME = "Enchant Option Menu.Title.Name";
      public static final String TITLE_LORE = "Enchant Option Menu.Title.Lore";
      public static final String SOUND_NAME = "Enchant Option Menu.Sound.Name";
      public static final String SOUND_LORE = "Enchant Option Menu.Sound.Lore";
      public static final String HOLOGRAM_NAME = "Enchant Option Menu.Hologram.Name";
      public static final String HOLOGRAM_LORE = "Enchant Option Menu.Hologram.Lore";
      public static final String PARTICLES_NAME = "Enchant Option Menu.Particles.Name";
      public static final String PARTICLES_LORE = "Enchant Option Menu.Particles.Lore";
    }

    /** Animations preferences sub-menu item paths. */
    public static final class AnimationsMenu {
      private AnimationsMenu() {}

      public static final String TITLE = "Animations Menu.Title";
      public static final String BACK_MATERIAL = "Animations Menu.Back.Material";
      public static final String BACK_NAME = "Animations Menu.Back.Name";
      public static final String BACK_LORE = "Animations Menu.Back.Lore";

      public static final String PICKUP_LABEL_MATERIAL =
          "Animations Menu.PickupAnimation.Label.Material";
      public static final String PICKUP_LABEL_NAME = "Animations Menu.PickupAnimation.Label.Name";
      public static final String PICKUP_LABEL_LORE = "Animations Menu.PickupAnimation.Label.Lore";
      public static final String PICKUP_MATERIAL = "Animations Menu.PickupAnimation.Material";
      public static final String PICKUP_SELECTED_MATERIAL =
          "Animations Menu.PickupAnimation.SelectedMaterial";
      public static final String PICKUP_DEFAULT_NAME =
          "Animations Menu.PickupAnimation.Default.Name";
      public static final String PICKUP_DEFAULT_LORE =
          "Animations Menu.PickupAnimation.Default.Lore";
      public static final String PICKUP_SIMPLE_NAME = "Animations Menu.PickupAnimation.Simple.Name";
      public static final String PICKUP_SIMPLE_LORE = "Animations Menu.PickupAnimation.Simple.Lore";
      public static final String PICKUP_DISABLED_NAME =
          "Animations Menu.PickupAnimation.Disabled.Name";
      public static final String PICKUP_DISABLED_LORE =
          "Animations Menu.PickupAnimation.Disabled.Lore";

      public static final String SPEED_LABEL_MATERIAL =
          "Animations Menu.EnchantSpeed.Label.Material";
      public static final String SPEED_LABEL_NAME = "Animations Menu.EnchantSpeed.Label.Name";
      public static final String SPEED_LABEL_LORE = "Animations Menu.EnchantSpeed.Label.Lore";
      public static final String SPEED_MATERIAL = "Animations Menu.EnchantSpeed.Material";
      public static final String SPEED_SELECTED_MATERIAL =
          "Animations Menu.EnchantSpeed.SelectedMaterial";
      public static final String SPEED_OPTION_LORE = "Animations Menu.EnchantSpeed.OptionLore";

      public static final String ATOM_LABEL_MATERIAL = "Animations Menu.AtomFX.Label.Material";
      public static final String ATOM_LABEL_NAME = "Animations Menu.AtomFX.Label.Name";
      public static final String ATOM_LABEL_LORE = "Animations Menu.AtomFX.Label.Lore";
      public static final String ATOM_ENABLED_MATERIAL = "Animations Menu.AtomFX.EnabledMaterial";
      public static final String ATOM_DISABLED_MATERIAL = "Animations Menu.AtomFX.DisabledMaterial";
      public static final String ATOM_SOUND_NAME = "Animations Menu.AtomFX.Sound.Name";
      public static final String ATOM_SOUND_LORE = "Animations Menu.AtomFX.Sound.Lore";
      public static final String ATOM_PARTICLES_NAME = "Animations Menu.AtomFX.Particles.Name";
      public static final String ATOM_PARTICLES_LORE = "Animations Menu.AtomFX.Particles.Lore";
      public static final String ATOM_HOLOGRAM_NAME = "Animations Menu.AtomFX.Hologram.Name";
      public static final String ATOM_HOLOGRAM_LORE = "Animations Menu.AtomFX.Hologram.Lore";
      public static final String ATOM_GLOW_NAME = "Animations Menu.AtomFX.Glow.Name";
      public static final String ATOM_GLOW_LORE = "Animations Menu.AtomFX.Glow.Lore";

      public static final String ESSENCE_LABEL_MATERIAL =
          "Animations Menu.EssenceFX.Label.Material";
      public static final String ESSENCE_LABEL_NAME = "Animations Menu.EssenceFX.Label.Name";
      public static final String ESSENCE_LABEL_LORE = "Animations Menu.EssenceFX.Label.Lore";
      public static final String ESSENCE_ENABLED_MATERIAL =
          "Animations Menu.EssenceFX.EnabledMaterial";
      public static final String ESSENCE_DISABLED_MATERIAL =
          "Animations Menu.EssenceFX.DisabledMaterial";
      public static final String ESSENCE_SOUND_NAME = "Animations Menu.EssenceFX.Sound.Name";
      public static final String ESSENCE_SOUND_LORE = "Animations Menu.EssenceFX.Sound.Lore";
      public static final String ESSENCE_PARTICLES_NAME =
          "Animations Menu.EssenceFX.Particles.Name";
      public static final String ESSENCE_PARTICLES_LORE =
          "Animations Menu.EssenceFX.Particles.Lore";
      public static final String ESSENCE_HOLOGRAM_NAME = "Animations Menu.EssenceFX.Hologram.Name";
      public static final String ESSENCE_HOLOGRAM_LORE = "Animations Menu.EssenceFX.Hologram.Lore";
      public static final String ESSENCE_GLOW_NAME = "Animations Menu.EssenceFX.Glow.Name";
      public static final String ESSENCE_GLOW_LORE = "Animations Menu.EssenceFX.Glow.Lore";
    }

    /** Backpack menu title paths. */
    public static final class BackpackMenu {
      private BackpackMenu() {}

      /** Title shown when the backpack has no pages or is on a single page. */
      public static final String TITLE = "Backpack Menu.Title";

      /** Title format for multi-page view. Supports %page% and %total% placeholders. */
      public static final String TITLE_PAGED = "Backpack Menu.TitlePaged";
    }

    /** Armor menu title paths. */
    public static final class ArmorMenu {
      private ArmorMenu() {}

      /** Title when no armor type is selected. */
      public static final String TITLE = "Armor Menu.Title";

      /** Title template for a specific armor type. Supports %type% placeholder. */
      public static final String TITLE_WITH_TYPE = "Armor Menu.TitleWithType";
    }

    /**
     * Furnace smelt menu item paths. Ticks per item controlled via {@link #SMELT_TICKS_PER_ITEM}.
     */
    public static final class FurnaceSmeltMenu {
      private FurnaceSmeltMenu() {}

      public static final String TITLE = "Furnace Smelt Menu.Title";

      /** Server ticks to smelt one item. Vanilla = 200. Default = 20 (10× faster). */
      public static final String SMELT_TICKS_PER_ITEM = "Furnace Smelt Menu.SmeltTicksPerItem";
    }

    /**
     * Prestige confirmation menu item paths. Tokens: {next_prestige}, {current_prestige},
     * {required_level}, {new_cap}, {mult}, {count}, {plural}
     */
    public static final class PrestigeConfirm {
      private PrestigeConfirm() {}

      public static final String CONFIRM_NAME = "Prestige Confirm Menu.Confirm.Name";
      public static final String CONFIRM_LORE = "Prestige Confirm Menu.Confirm.Lore";
      public static final String INFO_NAME = "Prestige Confirm Menu.Info.Name";
      public static final String INFO_LORE = "Prestige Confirm Menu.Info.Lore";
      public static final String INFO_FINAL_LINE = "Prestige Confirm Menu.Info.FinalPrestigeLine";
      public static final String CANCEL_NAME = "Prestige Confirm Menu.Cancel.Name";
      public static final String SOCKET_WARNING = "Prestige Confirm Menu.SocketWarning";
    }
  }

  /** Paths for the enchants configuration file. */
  public static final class Enchants {
    private Enchants() {}

    /** Display text when pickaxe has no enchants. */
    public static final String NO_ENCHANTS = "NoEnchants";

    /** Display text when enchant is at max level. */
    public static final String MAX_ENCHANT = "MaxEnchant";

    /** Root key for the Rarities section. */
    public static final String RARITIES_SECTION = "Rarities";

    /** Block/concrete material for a rarity tier (child of a rarity entry). */
    public static final String RARITY_ITEM = "Item";

    /** Hex colour string for a rarity tier (child of a rarity entry). */
    public static final String RARITY_COLOR = "Color";

    /** Relative weight for weighted-random rarity rolling (child of a rarity entry). */
    public static final String RARITY_CHANCE = "Chance";

    /** Root key for the SelectionPool section. */
    public static final String SELECTION_POOL_SECTION = "SelectionPool";

    /** Display name field within a selection pool entry. */
    public static final String ENCHANT_NAME = "Name";

    /** Rarity key field within a selection pool or functional enchant entry. */
    public static final String ENCHANT_RARITY = "Rarity";

    /** Icon item material field within a selection pool or functional enchant entry. */
    public static final String ENCHANT_ITEM = "Item";

    /** Maximum level field within a selection pool entry. */
    public static final String ENCHANT_MAX_LEVEL = "MaxLevel";

    /** Essence cost per level field within a selection pool entry. */
    public static final String ENCHANT_COST = "Cost";

    /** Skull texture URL field within a selection pool entry. */
    public static final String ENCHANT_TEXTURE = "Texture";

    /** Maximum trigger chance at max level (0 = passive). */
    public static final String ENCHANT_MAX_CHANCE = "MaxChance";

    /** Whether this enchant is passive (no triggered notification/effects). */
    public static final String ENCHANT_PASSIVE = "Passive";

    /** Whether this enchant should be hidden from the per-enchant preferences menu. */
    public static final String ENCHANT_PREFERENCES_HIDDEN = "PreferencesHidden";

    /** Brief one-line description shown on hover during enchant selection. */
    public static final String ENCHANT_DESCRIPTION = "Description";

    /**
     * Lore lines for the enchant menu item. Supports {level}, {maxLevel}, {cost}, {chance},
     * {rarity}, {status} tokens.
     */
    public static final String ENCHANT_LORE = "Lore";

    /** Efficiency enchantment configuration. */
    public static final class Efficiency {
      private Efficiency() {}

      public static final String NAME = "Efficiency.Name";
      public static final String RARITY = "Efficiency.Rarity";
      public static final String ITEM = "Efficiency.Item";
      public static final String MAX_LEVEL = "Efficiency.MaxLevel";
      public static final String SPEED_PER_LEVEL = "Efficiency.SpeedPerLevel";
    }

    /** Frostbite enchantment configuration. */
    public static final class Frostbite {
      private Frostbite() {}

      public static final String NAME = "Frostbite.Name";
      public static final String RARITY = "Frostbite.Rarity";
      public static final String ITEM = "Frostbite.Item";
      public static final String MAX_LEVEL = "Frostbite.MaxLevel";
      public static final String MAX_CHANCE = "Frostbite.MaxChance";
      public static final String RADIUS = "Frostbite.Radius";
      public static final String HOLOGRAM_ENABLED = "Frostbite.Hologram.Enabled";
      public static final String HOLOGRAM_MESSAGE = "Frostbite.Hologram.Message";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Frostbite.Hologram.DefaultEnabled";
    }

    /** Furnace enchantment configuration. */
    public static final class Furnace {
      private Furnace() {}

      public static final String NAME = "Furnace.Name";
      public static final String RARITY = "Furnace.Rarity";
      public static final String ITEM = "Furnace.Item";
      public static final String MAX_LEVEL = "Furnace.MaxLevel";
      public static final String MAX_CHANCE = "Furnace.MaxChance";
      public static final String HOLOGRAM_ENABLED = "Furnace.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Furnace.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Furnace.Hologram.Message";
      public static final String RECIPES = "Furnace.Recipes";
      public static final String ALCHEMICAL_FORGE_CHAIN = "Furnace.Stones.AlchemicalForge";
    }

    /** Alchemy enchantment configuration. */
    public static final class Alchemy {
      private Alchemy() {}

      public static final String NAME = "Alchemy.Name";
      public static final String RARITY = "Alchemy.Rarity";
      public static final String ITEM = "Alchemy.Item";
      public static final String MAX_LEVEL = "Alchemy.MaxLevel";
      public static final String MAX_CHANCE = "Alchemy.MaxChance";
      public static final String HOLOGRAM_ENABLED = "Alchemy.Hologram.Enabled";
      public static final String HOLOGRAM_MESSAGE = "Alchemy.Hologram.Message";
      public static final String UPGRADES = "Alchemy.Upgrades";
    }

    /** Scavenger enchantment configuration. */
    public static final class Scavenger {
      private Scavenger() {}

      public static final String NAME = "Scavenger.Name";
      public static final String RARITY = "Scavenger.Rarity";
      public static final String ITEM = "Scavenger.Item";
      public static final String MAX_LEVEL = "Scavenger.MaxLevel";
      public static final String MAX_CHANCE = "Scavenger.MaxChance";
      public static final String SLOTS_MIN = "Scavenger.SlotsMin";
      public static final String SLOTS_MAX = "Scavenger.SlotsMax";
      public static final String MAX_TOTAL_SLOTS = "Scavenger.MaxTotalSlots";
      public static final String HOLOGRAM_ENABLED = "Scavenger.Hologram.Enabled";
      public static final String HOLOGRAM_MESSAGE = "Scavenger.Hologram.Message";
      public static final String MIXED_BAG_MESSAGE = "Scavenger.MixedBagMessage";
    }

    /** Merchant enchantment configuration. */
    public static final class Merchant {
      private Merchant() {}

      public static final String NAME = "Merchant.Name";
      public static final String MAX_LEVEL = "Merchant.MaxLevel";
      public static final String MAX_CHANCE = "Merchant.MaxChance";
      public static final String COINS_MIN = "Merchant.CoinsMin";
      public static final String COINS_MAX = "Merchant.CoinsMax";
      public static final String HOLOGRAM_ENABLED = "Merchant.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Merchant.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Merchant.Hologram.Message";
    }

    /** Fortune enchantment configuration. */
    public static final class Fortune {
      private Fortune() {}

      public static final String NAME = "Fortune.Name";
      public static final String MAX_LEVEL = "Fortune.MaxLevel";
    }

    /** AutoSell enchantment configuration. */
    public static final class AutoSell {
      private AutoSell() {}

      public static final String NAME = "AutoSell.Name";
      public static final String MAX_LEVEL = "AutoSell.MaxLevel";
      public static final String MAX_CHANCE = "AutoSell.MaxChance";
      public static final String HOLOGRAM_ENABLED = "AutoSell.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "AutoSell.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "AutoSell.Hologram.Message";
    }

    /** Radioactive enchantment configuration. */
    public static final class Radioactive {
      private Radioactive() {}

      public static final String NAME = "Radioactive.Name";
      public static final String MAX_LEVEL = "Radioactive.MaxLevel";
      public static final String MAX_CHANCE = "Radioactive.MaxChance";
    }

    /** Extraction enchantment configuration. */
    public static final class Extraction {
      private Extraction() {}

      public static final String NAME = "Extraction.Name";
      public static final String MAX_LEVEL = "Extraction.MaxLevel";
      public static final String MAX_CHANCE = "Extraction.MaxChance";
      public static final String ESSENCE_MIN = "Extraction.EssenceMin";
      public static final String ESSENCE_MAX = "Extraction.EssenceMax";
      public static final String HOLOGRAM_ENABLED = "Extraction.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Extraction.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Extraction.Hologram.Message";
    }

    /** Explosive enchantment configuration. */
    public static final class Explosive {
      private Explosive() {}

      public static final String NAME = "Explosive.Name";
      public static final String MAX_LEVEL = "Explosive.MaxLevel";
      public static final String MAX_CHANCE = "Explosive.MaxChance";
      public static final String MAX_RADIUS = "Explosive.MaxRadius";
      public static final String HOLOGRAM_ENABLED = "Explosive.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Explosive.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Explosive.Hologram.Message";
    }

    /** Drill enchantment configuration. */
    public static final class Drill {
      private Drill() {}

      public static final String NAME = "Drill.Name";
      public static final String MAX_LEVEL = "Drill.MaxLevel";
      public static final String MAX_CHANCE = "Drill.MaxChance";
      public static final String DEPTH = "Drill.MaxDepth";
      public static final String HOLOGRAM_ENABLED = "Drill.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Drill.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Drill.Hologram.Message";
    }

    /** Lazer enchantment configuration. */
    public static final class Lazer {
      private Lazer() {}

      public static final String NAME = "Lazer.Name";
      public static final String MAX_LEVEL = "Lazer.MaxLevel";
      public static final String MAX_CHANCE = "Lazer.MaxChance";
      public static final String MAX_RANGE = "Lazer.MaxRange";
      public static final String HOLOGRAM_ENABLED = "Lazer.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Lazer.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Lazer.Hologram.Message";
    }

    /** Thor enchantment configuration. */
    public static final class Thor {
      private Thor() {}

      public static final String NAME = "Thor.Name";
      public static final String MAX_LEVEL = "Thor.MaxLevel";
      public static final String MAX_CHANCE = "Thor.MaxChance";
      public static final String MAX_COLUMN_HEIGHT = "Thor.MaxColumnHeight";
      public static final String HOLOGRAM_ENABLED = "Thor.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Thor.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Thor.Hologram.Message";
    }

    /** Vortex enchantment configuration. */
    public static final class Vortex {
      private Vortex() {}

      public static final String NAME = "Vortex.Name";
      public static final String MAX_LEVEL = "Vortex.MaxLevel";
      public static final String MAX_CHANCE = "Vortex.MaxChance";
      public static final String MAX_RADIUS = "Vortex.MaxRadius";
      public static final String HOLOGRAM_ENABLED = "Vortex.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Vortex.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Vortex.Hologram.Message";
    }

    /** Cascade enchantment configuration. */
    public static final class Cascade {
      private Cascade() {}

      public static final String NAME = "Cascade.Name";
      public static final String MAX_LEVEL = "Cascade.MaxLevel";
      public static final String MAX_CHANCE = "Cascade.MaxChance";
      public static final String MAX_CHAIN_LENGTH = "Cascade.MaxChainLength";
      public static final String STEP_DISTANCE = "Cascade.StepDistance";
      public static final String EXPLOSION_RADIUS = "Cascade.ExplosionRadius";
      public static final String MAX_RADIUS_BONUS = "Cascade.MaxRadiusBonus";
      public static final String MAX_CHAINS = "Cascade.MaxChains";
      public static final String HOLOGRAM_ENABLED = "Cascade.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Cascade.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Cascade.Hologram.Message";
    }

    /** Jackhammer enchantment configuration. */
    public static final class Jackhammer {
      private Jackhammer() {}

      public static final String NAME = "Jackhammer.Name";
      public static final String MAX_LEVEL = "Jackhammer.MaxLevel";
      public static final String MAX_CHANCE = "Jackhammer.MaxChance";
      public static final String MAX_SCAN_RADIUS = "Jackhammer.MaxScanRadius";
      public static final String HOLOGRAM_ENABLED = "Jackhammer.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "Jackhammer.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "Jackhammer.Hologram.Message";
    }

    /** Nuclear Shower enchantment configuration. */
    public static final class NuclearShower {
      private NuclearShower() {}

      public static final String NAME = "NuclearShower.Name";
      public static final String MAX_LEVEL = "NuclearShower.MaxLevel";
      public static final String MAX_CHANCE = "NuclearShower.MaxChance";
      public static final String MAX_STREAMS = "NuclearShower.MaxStreams";
      public static final String MAX_DEPTH_PER_STREAM = "NuclearShower.MaxDepthPerStream";
      public static final String BATCH_SIZE = "NuclearShower.BatchSize";
      public static final String MAX_SCAN_RADIUS = "NuclearShower.MaxScanRadius";
      public static final String HOLOGRAM_ENABLED = "NuclearShower.Hologram.Enabled";
      public static final String HOLOGRAM_DEFAULT_ENABLED = "NuclearShower.Hologram.DefaultEnabled";
      public static final String HOLOGRAM_MESSAGE = "NuclearShower.Hologram.Message";
    }
  }

  /** Paths for the ranks configuration file (ranks.yml). */
  public static final class Ranks {
    private Ranks() {}

    /** The rank id new players start at (e.g. "A1"). */
    public static final String DEFAULT_RANK = "Ranks.DefaultRank";

    /** Ordered list of rank ids defining rankup progression. */
    public static final String ORDER = "Ranks.Order";

    /** Prefix for per-rank data sections. Append the rank id (e.g. "Ranks.Data.A1"). */
    public static final String DATA_PREFIX = "Ranks.Data.";

    /** Ordered list of staff rank ids. */
    public static final String STAFF_ORDER = "StaffRanks.Order";

    /**
     * Prefix for per-staff-rank data sections. Append the rank id (e.g. "StaffRanks.Data.helper").
     */
    public static final String STAFF_DATA_PREFIX = "StaffRanks.Data.";

    /** Cost (in coins) to rank up TO this rank. Append to DATA_PREFIX + rankId. */
    public static final String COST_SUFFIX = ".Cost";

    /** Color-formatted tag shown in chat and scoreboard. Append to DATA_PREFIX + rankId. */
    public static final String PREFIX_SUFFIX = ".Prefix";

    /** Hex color applied to the player's name in chat. Append to DATA_PREFIX + rankId. */
    public static final String CHAT_COLOR_SUFFIX = ".ChatColor";

    /** The prison block letter this rank belongs to (A–F). Append to DATA_PREFIX + rankId. */
    public static final String BLOCK_SUFFIX = ".Block";

    /** Extra permissions granted when reaching this rank. Append to DATA_PREFIX + rankId. */
    public static final String PERMISSIONS_SUFFIX = ".Permissions";

    /** Rankup message paths (inside ranks.yml under Messages.*). */
    public static final class Messages {
      private Messages() {}

      public static final String RANKUP_SUCCESS = "Messages.Rankup.Success";
      public static final String RANKUP_INSUFFICIENT = "Messages.Rankup.Insufficient";
      public static final String RANKUP_AT_MAX = "Messages.Rankup.AtMaxRank";

      public static final String SET_RANK_SUCCESS = "Messages.SetRank.Success";
      public static final String SET_RANK_PLAYER_NOTIFIED = "Messages.SetRank.PlayerNotified";

      public static final String SET_STAFF_SUCCESS = "Messages.SetStaff.Success";
      public static final String SET_STAFF_CLEARED = "Messages.SetStaff.Cleared";
      public static final String SET_STAFF_PLAYER_NOTIFIED = "Messages.SetStaff.PlayerNotified";
      public static final String SET_STAFF_PLAYER_CLEARED = "Messages.SetStaff.PlayerCleared";
    }
  }

  /**
   * Paths for the stones configuration file (stones.yml). Tokens: {hex}, {rarity}, {enchant},
   * {effect}, {index}
   */
  public static final class Stones {
    private Stones() {}

    /** Evolution Stone item display strings. */
    public static final class Item {
      private Item() {}

      public static final String NAME = "Item.Name";

      /** Rarity + "Evolution Stone" line shown as first lore entry. */
      public static final String RARITY_LINE = "Item.RarityLine";

      public static final String BOUND_TO = "Item.BoundTo";
      public static final String EFFECT_HEADER = "Item.EffectHeader";
      public static final String EFFECT_LINE = "Item.EffectLine";

      /** String list — each entry is one apply-hint line. */
      public static final String APPLY_HINT = "Item.ApplyHint";
    }

    /** Socket Menu display strings for the StoneSocketMenu. */
    public static final class SocketMenu {
      private SocketMenu() {}

      public static final String FILLED_NAME = "Socket Menu.Filled Stone.Name";
      public static final String FILLED_RARITY_LINE = "Socket Menu.Filled Stone.RarityLine";
      public static final String FILLED_EFFECT_HEADER = "Socket Menu.Filled Stone.EffectHeader";
      public static final String FILLED_EFFECT_LINE = "Socket Menu.Filled Stone.EffectLine";
      public static final String FILLED_REMOVE_HINT = "Socket Menu.Filled Stone.RemoveHint";

      public static final String EMPTY_NAME = "Socket Menu.Empty Socket.Name";
      public static final String EMPTY_HOW_TO_HEADER = "Socket Menu.Empty Socket.HowToHeader";

      /** String list — each entry is one how-to line. */
      public static final String EMPTY_HOW_TO_LINES = "Socket Menu.Empty Socket.HowToLines";

      public static final String INFO_NAME = "Socket Menu.Info Button.Name";

      /** String list — each entry is one lore line. */
      public static final String INFO_LORE = "Socket Menu.Info Button.Lore";
    }
  }
}
