package org.benski.evolv3.core.constants;

/**
 * Constants for configuration file paths.
 * Use these instead of hardcoded strings to prevent typos and improve refactoring.
 * Organized by configuration file for easy navigation.
 */
public final class ConfigPaths
{
    private ConfigPaths() {
        // Prevent instantiation
    }

    /**
     * Paths for the default configuration file.
     */
    public static final class Default
    {
        private Default() {}

        /**
         * MongoDB connection string path.
         * Example: "mongodb+srv://user:pass@cluster.mongodb.net/"
         */
        public static final String DATABASE_MONGODB = "Database.MongoDB";

        /**
         * Schedule interval configuration paths.
         * Intervals are in ticks (20 ticks = 1 second).
         */
        public static final class Schedules
        {
            private Schedules() {}

            /**
             * Pickaxe check schedule interval in ticks.
             * Default: 100 ticks (5 seconds)
             */
            public static final String PICKAXE_CHECK_INTERVAL = "Schedules.PickaxeCheck.IntervalTicks";
        }

        /**
         * Pickaxe configuration paths.
         */
        public static final class Pickaxe
        {
            private Pickaxe() {}

            // Default values
            public static final String DEFAULT_LEVEL = "Pickaxe.Defaults.Level";
            public static final String DEFAULT_XP = "Pickaxe.Defaults.XP";
            public static final String DEFAULT_TARGET = "Pickaxe.Defaults.Target";
            public static final String DEFAULT_ESSENCE_TARGET = "Pickaxe.Defaults.EssenceTarget";
            public static final String DEFAULT_CURRENT_ESSENCE = "Pickaxe.Defaults.CurrentEssence";
            public static final String DEFAULT_EVOLUTION_INTERVAL = "Pickaxe.Defaults.EvolutionInterval";
            public static final String DEFAULT_ENCHANT_ROLLS = "Pickaxe.Defaults.EnchantRolls";
            public static final String DEFAULT_EVOLUTION_ATOMS = "Pickaxe.Defaults.EvolutionAtoms";
            public static final String XP_MULTIPLIER      = "Pickaxe.Defaults.XPMultiplier";
            public static final String ESSENCE_MULTIPLIER = "Pickaxe.Defaults.EssenceMultiplier";
            public static final String ESSENCE_TARGET_CAP = "Pickaxe.Defaults.EssenceTargetCap";

            // Display settings
            public static final String NAME = "Pickaxe.Name";
            public static final String ACTION_BAR = "Pickaxe.ActionBar";
            public static final String LORE = "Pickaxe.Lore";
            public static final String LORE_FOOTER = "Pickaxe.LoreFooter";

            // Progress bar symbols
            public static final String PROGRESSBAR_SYMBOL_FILLED = "Pickaxe.Progressbar Symbol 1";
            public static final String PROGRESSBAR_SYMBOL_EMPTY = "Pickaxe.Progressbar Symbol 2";

            // Hologram settings
            public static final String HOLOGRAM_ENABLED = "Pickaxe.Hologram.Enabled";
            public static final String HOLOGRAM_TEXT = "Pickaxe.Hologram.Text";
        }

        /**
         * Essence configuration paths.
         */
        public static final class Essence
        {
            private Essence() {}

            public static final String DISCOVERY_CHANCE = "Essence.DiscoveryChance";
            public static final String DISCOVERY_MIN_AMOUNT = "Essence.DiscoveryMinAmount";
            public static final String DISCOVERY_MAX_AMOUNT = "Essence.DiscoveryMaxAmount";
            public static final String TIER_INCREMENT = "Essence.LevelUpRewards.TierIncrement";
            public static final String MAX_CAP = "Essence.LevelUpRewards.MaxCap";

            // Hologram settings
            public static final String HOLOGRAM_ENABLED = "Essence.Hologram.Enabled";
            public static final String HOLOGRAM_TEXT = "Essence.Hologram.Text";
        }

        /**
         * Atom configuration paths.
         */
        public static final class Atoms
        {
            private Atoms() {}

            public static final String DISCOVERY_CHANCE     = "Atoms.DiscoveryChance";
            public static final String DISCOVERY_MIN_AMOUNT = "Atoms.DiscoveryMinAmount";
            public static final String DISCOVERY_MAX_AMOUNT = "Atoms.DiscoveryMaxAmount";
            public static final String LEVEL_UP_REWARD      = "Atoms.LevelUpReward";
            public static final String DEFAULT_ATOM_TARGET  = "Atoms.DefaultTarget";
            public static final String ATOM_TARGET_MULTIPLIER = "Atoms.TargetMultiplier";

            // Hologram settings
            public static final String HOLOGRAM_ENABLED = "Atoms.Hologram.Enabled";
            public static final String HOLOGRAM_TEXT     = "Atoms.Hologram.Text";
        }

        /**
         * Block XP configuration path prefix.
         * Usage: BLOCK_DEFAULTS + "." + Material.name() + ".XP"
         * Usage: BLOCK_DEFAULTS + "." + Material.name() + ".Sell"
         */
        public static final String BLOCK_DEFAULTS = "BlockDefaults";

        /**
         * Mine region keyword for WorldGuard integration.
         * Only regions containing this keyword will allow pickaxe progression.
         */
        public static final String MINE_REGION = "MineRegion";

        /**
         * Action bar message shown when mining outside mine region.
         */
        public static final String NOT_IN_MINE_REGION_MESSAGE = "NotInMineRegionMessage";

        /**
         * Player configuration paths.
         */
        public static final class Player
        {
            private Player() {}

            public static final String DEFAULT_PICKAXE_LIMIT = "Player.Defaults.PickaxeLimit";
            public static final String DEFAULT_ESSENCE = "Player.Defaults.Essence";
        }

        /**
         * Armor configuration paths.
         */
        public static final class Armor
        {
            private Armor() {}

            // Shared defaults
            public static final String DEFAULT_LEVEL = "Armor.Defaults.Level";
            public static final String DEFAULT_XP = "Armor.Defaults.XP";
            public static final String DEFAULT_TARGET = "Armor.Defaults.Target";
            public static final String DEFAULT_MULTIPLIER = "Armor.Defaults.Multiplier";
            public static final String XP_MULTIPLIER = "Armor.Defaults.XPMultiplier";
            public static final String MULTIPLIER_INCREMENT = "Armor.Defaults.MultiplierIncrement";
            public static final String EVOLUTION_INTERVAL = "Armor.Defaults.EvolutionInterval";

            // Per-piece display settings
            public static final String HELMET_NAME = "Armor.Helmet.Name";
            public static final String HELMET_LORE = "Armor.Helmet.Lore";
            public static final String CHESTPLATE_NAME = "Armor.Chestplate.Name";
            public static final String CHESTPLATE_LORE = "Armor.Chestplate.Lore";
            public static final String LEGGINGS_NAME = "Armor.Leggings.Name";
            public static final String LEGGINGS_LORE = "Armor.Leggings.Lore";
            public static final String BOOTS_NAME = "Armor.Boots.Name";
            public static final String BOOTS_LORE = "Armor.Boots.Lore";
        }

        /**
         * Backpack configuration paths.
         */
        public static final class Backpack
        {
            private Backpack() {}

            public static final String SIZE = "Backpack.Size";
            public static final String NAME = "Backpack.Name";
            public static final String FULL_MESSAGE = "Backpack.FullMessage";
            public static final String LORE = "Backpack.Lore";
            public static final String CUSTOM_MODEL_DATA = "Backpack.CustomModelData";

            // Hologram settings
            public static final String HOLOGRAM_ENABLED = "Backpack.Hologram.Enabled";
            public static final String HOLOGRAM_TEXT = "Backpack.Hologram.Text";
        }

        /**
         * Pickaxe storage (limit) configuration paths.
         */
        public static final class PickaxeStorage
        {
            private PickaxeStorage() {}

            public static final String BASE_COST = "PickaxeStorage.BaseCost";
            public static final String COST_MULTIPLIER = "PickaxeStorage.CostMultiplier";
        }
    }

    /**
     * Paths for the messages configuration file.
     * Organized by category for easy navigation.
     */
    public static final class Messages
    {
        private Messages() {}

        /**
         * Top-level prefix path.
         */
        public static final String PREFIX = "Prefix";

        /**
         * Pickaxe-related messages.
         */
        public static final class Pickaxe
        {
            private Pickaxe() {}

            public static final String LOADING_PROFILE = "pickaxe.loading-profile";
            public static final String PROFILE_CREATED = "pickaxe.profile-created";
            public static final String NO_PICKAXE = "pickaxe.no-pickaxe";
            public static final String ENCHANT_ROLL_GAINED = "pickaxe.enchant-roll-gained";

            // Level up messages (list)
            public static final String LEVEL_UP = "pickaxe.level-up";

            // Level up title messages
            public static final String LEVEL_UP_TITLE = "pickaxe.level-up-title.title";
            public static final String LEVEL_UP_SUBTITLE = "pickaxe.level-up-title.subtitle";

            // Enchant messages
            public static final String ENCHANT_INSUFFICIENT_ESSENCE = "pickaxe.enchant.insufficient-essence";
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

            // Menu messages
            public static final String MENU_EVOLUTION_COMING_SOON = "pickaxe.menu.evolution-coming-soon";

            // Evolution messages
            public static final String EVOLVE_NOT_AVAILABLE = "pickaxe.evolve.not-available";
            public static final String EVOLVE_INSUFFICIENT_SPINS = "pickaxe.evolve.insufficient-spins";
            public static final String EVOLVE_SUCCESS = "pickaxe.evolve.success";
        }

        /**
         * General plugin messages.
         */
        public static final class General
        {
            private General() {}

            public static final String PREFIX = "general.prefix";
            public static final String NO_PERMISSION = "general.no-permission";
            public static final String PLAYER_ONLY = "general.player-only";
            public static final String UNKNOWN_COMMAND = "general.unknown-command";
            public static final String RELOAD_SUCCESS = "general.reload-success";
            public static final String RELOAD_FAILED = "general.reload-failed";
        }

        /**
         * Error messages.
         */
        public static final class Errors
        {
            private Errors() {}

            public static final String DATABASE_ERROR = "errors.database-error";
            public static final String INTERNAL_ERROR = "errors.internal-error";
            public static final String INVALID_ARGUMENTS = "errors.invalid-arguments";
        }

        /**
         * Enchantment trigger messages.
         */
        public static final class Enchants
        {
            private Enchants() {}

            public static final String EFFICIENCY_APPLIED = "enchants.efficiency.applied";
            public static final String FROSTBITE_TRIGGERED = "enchants.frostbite.triggered";
            public static final String FURNACE_TRIGGERED = "enchants.furnace.triggered";
            public static final String ALCHEMY_TRIGGERED = "enchants.alchemy.triggered";
        }

        /**
         * Player-related messages.
         */
        public static final class Player
        {
            private Player() {}

            public static final String LOADING_PROFILE = "player.loading-profile";
            public static final String PROFILE_CREATED = "player.profile-created";
            public static final String PROFILE_LOADED = "player.profile-loaded";
        }

        /**
         * Armor-related messages.
         */
        public static final class Armor
        {
            private Armor() {}

            public static final String LOADING_PROFILE = "armor.loading-profile";
            public static final String PROFILE_CREATED = "armor.profile-created";
            public static final String EVOLUTION = "armor.evolution";

            // Per-piece level up chat messages (lists)
            public static final String LEVEL_UP_HELMET = "armor.level-up.helmet";
            public static final String LEVEL_UP_CHESTPLATE = "armor.level-up.chestplate";
            public static final String LEVEL_UP_LEGGINGS = "armor.level-up.leggings";
            public static final String LEVEL_UP_BOOTS = "armor.level-up.boots";

            // Per-piece level up title messages
            public static final String LEVEL_UP_TITLE_HELMET = "armor.level-up-title.helmet.title";
            public static final String LEVEL_UP_TITLE_HELMET_SUB = "armor.level-up-title.helmet.subtitle";
            public static final String LEVEL_UP_TITLE_CHESTPLATE = "armor.level-up-title.chestplate.title";
            public static final String LEVEL_UP_TITLE_CHESTPLATE_SUB = "armor.level-up-title.chestplate.subtitle";
            public static final String LEVEL_UP_TITLE_LEGGINGS = "armor.level-up-title.leggings.title";
            public static final String LEVEL_UP_TITLE_LEGGINGS_SUB = "armor.level-up-title.leggings.subtitle";
            public static final String LEVEL_UP_TITLE_BOOTS = "armor.level-up-title.boots.title";
            public static final String LEVEL_UP_TITLE_BOOTS_SUB = "armor.level-up-title.boots.subtitle";

            // Toggle messages
            public static final String TOGGLE_ENABLED = "armor.toggle.enabled";
            public static final String TOGGLE_DISABLED = "armor.toggle.disabled";

            // Armor removal
            public static final String REMOVE_BLOCKED = "armor.remove.blocked";

            // Drop/ownership messages
            public static final String DROP_CONFIRMATION = "armor.drop.confirmation";
            public static final String DROP_NEW_OWNER = "armor.drop.new-owner";
            public static final String DROP_NO_SLOTS = "armor.drop.no-slots";
        }

        /**
         * Backpack-related messages.
         */
        public static final class Backpack
        {
            private Backpack() {}

            public static final String FULL = "backpack.full";
            public static final String ITEM_ADDED = "backpack.item-added";
            public static final String SELL_ITEMS = "backpack.sell-items";
        }

        /**
         * Pickaxe limit/storage messages.
         */
        public static final class PickaxeLimit
        {
            private PickaxeLimit() {}

            public static final String PROFILE_LOAD_ERROR = "pickaxe-limit.profile-load-error";
            public static final String ALREADY_HAS_PICKAXE = "pickaxe-limit.already-has-pickaxe";
            public static final String COOLDOWN_ACTIVE = "pickaxe-limit.cooldown-active";
            public static final String COOLDOWN_CLICK = "pickaxe-limit.cooldown-click";
            public static final String BLOCKED_CLICK = "pickaxe-limit.blocked-click";
            public static final String SLOT_PURCHASED = "pickaxe-limit.slot-purchased";
            public static final String INSUFFICIENT_ESSENCE = "pickaxe-limit.insufficient-essence";
            public static final String PURCHASE_ERROR = "pickaxe-limit.purchase-error";
            public static final String PICKAXE_DEPOSITED = "pickaxe-limit.pickaxe-deposited";
            public static final String PICKAXE_NOT_FOUND = "pickaxe-limit.pickaxe-not-found";
            public static final String MUST_DEPOSIT_BEFORE_WITHDRAW = "pickaxe-limit.must-deposit-before-withdraw";
            public static final String INVENTORY_FULL = "pickaxe-limit.inventory-full";
            public static final String PICKAXE_WITHDRAWN = "pickaxe-limit.pickaxe-withdrawn";
            public static final String MUST_DEPOSIT_BEFORE_CLAIM = "pickaxe-limit.must-deposit-before-claim";
            public static final String PICKAXE_CLAIMED = "pickaxe-limit.pickaxe-claimed";
            public static final String MUST_DEPOSIT_BEFORE_CRAFT = "pickaxe-limit.must-deposit-before-craft";
            public static final String INSUFFICIENT_WOOD = "pickaxe-limit.insufficient-wood";
            public static final String WOOD_CONSUMPTION_ERROR = "pickaxe-limit.wood-consumption-error";
            public static final String PICKAXE_CRAFTED = "pickaxe-limit.pickaxe-crafted";
            public static final String CRAFT_SAVE_ERROR = "pickaxe-limit.craft-save-error";
        }
    }

    /**
     * Paths for the menus configuration file.
     */
    public static final class Menus
    {
        private Menus() {}

        /**
         * Pickaxe menu panel item paths.
         */
        public static final class PickaxeMenu
        {
            private PickaxeMenu() {}

            public static final String ESSENCE_NAME = "Pickaxe Menu.Essence.Name";
            public static final String ESSENCE_LORE = "Pickaxe Menu.Essence.Lore";
            public static final String EVOLVE_NAME  = "Pickaxe Menu.Evolve.Name";
            public static final String EVOLVE_LORE  = "Pickaxe Menu.Evolve.Lore";

            // Locked enchant slot config
            public static final String ENCHANT_LOCKED_NAME = "Pickaxe Menu.LockedEnchant.Name";
            public static final String ENCHANT_LOCKED_LORE = "Pickaxe Menu.LockedEnchant.Lore";
        }
    }

    /**
     * Paths for the enchants configuration file.
     */
    public static final class Enchants
    {
        private Enchants() {}

        /**
         * Display text when pickaxe has no enchants.
         */
        public static final String NO_ENCHANTS = "NoEnchants";

        /**
         * Display text when enchant is at max level.
         */
        public static final String MAX_ENCHANT = "MaxEnchant";

        // ---- Rarity section ----

        /** Root key for the Rarities section. */
        public static final String RARITIES_SECTION = "Rarities";

        /** Block/concrete material for a rarity tier (child of a rarity entry). */
        public static final String RARITY_ITEM = "Item";

        /** Hex colour string for a rarity tier (child of a rarity entry). */
        public static final String RARITY_COLOR = "Color";

        /** Relative weight for weighted-random rarity rolling (child of a rarity entry). */
        public static final String RARITY_CHANCE = "Chance";

        // ---- Selection pool section ----

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

        /** Lore lines for the enchant menu item. Supports {level}, {maxLevel}, {cost}, {chance}, {rarity}, {status} tokens. */
        public static final String ENCHANT_LORE = "Lore";

        /**
         * Efficiency enchantment configuration.
         */
        public static final class Efficiency
        {
            private Efficiency() {}

            public static final String NAME = "Efficiency.Name";
            public static final String RARITY = "Efficiency.Rarity";
            public static final String ITEM = "Efficiency.Item";
            public static final String MAX_LEVEL = "Efficiency.MaxLevel";
            public static final String SPEED_PER_LEVEL = "Efficiency.SpeedPerLevel";
        }

        /**
         * Frostbite enchantment configuration.
         */
        public static final class Frostbite
        {
            private Frostbite() {}

            public static final String NAME = "Frostbite.Name";
            public static final String RARITY = "Frostbite.Rarity";
            public static final String ITEM = "Frostbite.Item";
            public static final String MAX_LEVEL = "Frostbite.MaxLevel";
            public static final String MAX_CHANCE = "Frostbite.MaxChance";
            public static final String RADIUS = "Frostbite.Radius";
            public static final String HOLOGRAM_ENABLED = "Frostbite.Hologram.Enabled";
            public static final String HOLOGRAM_MESSAGE = "Frostbite.Hologram.Message";
        }

        /**
         * Furnace enchantment configuration.
         */
        public static final class Furnace
        {
            private Furnace() {}

            public static final String NAME = "Furnace.Name";
            public static final String RARITY = "Furnace.Rarity";
            public static final String ITEM = "Furnace.Item";
            public static final String MAX_LEVEL = "Furnace.MaxLevel";
            public static final String MAX_CHANCE = "Furnace.MaxChance";
            public static final String HOLOGRAM_ENABLED = "Furnace.Hologram.Enabled";
            public static final String HOLOGRAM_MESSAGE = "Furnace.Hologram.Message";
            public static final String RECIPES = "Furnace.Recipes";
        }

        /**
         * Alchemy enchantment configuration.
         */
        public static final class Alchemy
        {
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
    }
}
