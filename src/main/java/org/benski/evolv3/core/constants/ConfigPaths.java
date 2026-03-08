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

            // Durability warning threshold (% remaining durability, e.g. 10 = warn below 10%)
            public static final String DURABILITY_WARNING_THRESHOLD = "Pickaxe.DurabilityWarningThreshold";

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
            public static final String DEFAULT_COINS = "Player.Defaults.Coins";
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
         * Ancient Resin configuration paths.
         */
        public static final class AncientResin
        {
            private AncientResin() {}

            /** Percent chance (0–100) that breaking any log drops one Ancient Resin. */
            public static final String DROP_CHANCE  = "AncientResin.DropChance";

            /** Number of Ancient Resin required to fully repair a pickaxe. */
            public static final String REPAIR_COST  = "AncientResin.RepairCost";
        }

        /**
         * Pickaxe storage (limit) configuration paths.
         */
        public static final class PickaxeStorage
        {
            private PickaxeStorage() {}

            public static final String MAX_SLOTS      = "PickaxeStorage.MaxSlots";
            public static final String BASE_COST      = "PickaxeStorage.BaseCost";
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
        public static final String PREFIX = "prefix";

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

            // Ownership / durability messages
            public static final String PICKAXE_BROKEN          = "pickaxe.pickaxe-broken";
            public static final String DROP_CONFIRMATION        = "pickaxe.drop-confirmation";
            public static final String PICKUP_NEW_OWNER         = "pickaxe.pickup-new-owner";
            public static final String DURABILITY_WARNING       = "pickaxe.durability-warning";

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
            public static final String ENCHANT_TOGGLE_ENABLED = "pickaxe.enchant.toggle-enabled";
            public static final String ENCHANT_TOGGLE_DISABLED = "pickaxe.enchant.toggle-disabled";

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

            public static final String FROSTBITE_TRIGGERED          = "enchants.frostbite.triggered";
            public static final String FROSTBITE_TRIGGERED_TITLE    = "enchants.frostbite.triggered-title";
            public static final String FROSTBITE_TRIGGERED_SUBTITLE = "enchants.frostbite.triggered-subtitle";

            public static final String FURNACE_TRIGGERED          = "enchants.furnace.triggered";
            public static final String FURNACE_TRIGGERED_TITLE    = "enchants.furnace.triggered-title";
            public static final String FURNACE_TRIGGERED_SUBTITLE = "enchants.furnace.triggered-subtitle";

            public static final String ALCHEMY_TRIGGERED          = "enchants.alchemy.triggered";
            public static final String ALCHEMY_TRIGGERED_TITLE    = "enchants.alchemy.triggered-title";
            public static final String ALCHEMY_TRIGGERED_SUBTITLE = "enchants.alchemy.triggered-subtitle";
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
         * Join message configuration.
         */
        public static final class JoinMessage
        {
            private JoinMessage() {}

            public static final String ENABLED      = "join-message.enabled";
            public static final String SKIN_PADDING = "join-message.skin-padding";
            public static final String LINES        = "join-message.lines";
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
            public static final String ALL_SLOTS_UNLOCKED = "pickaxe-limit.all-slots-unlocked";
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
            public static final String REPAIR_SUCCESS = "pickaxe-limit.repair-success";
            public static final String REPAIR_INSUFFICIENT_WOOD = "pickaxe-limit.repair-insufficient-wood";
            public static final String REPAIR_ALREADY_FULL = "pickaxe-limit.repair-already-full";
            public static final String CLAIM_RATE_LIMITED = "pickaxe-limit.claim-rate-limited";
            public static final String PICKUP_NO_SLOTS = "pickaxe-limit.pickup-no-slots";
        }
    }

    /**
     * Paths for the menus configuration file.
     */
    public static final class Menus
    {
        private Menus() {}

        /**
         * Pickaxe limit menu slot item paths.
         * Tokens supported in lore: {cost}, {wood}, {slot}, {amount_colored}
         */
        public static final class PickaxeLimitMenu
        {
            private PickaxeLimitMenu() {}

            public static final String TITLE                    = "Pickaxe Limit Menu.Title";
            public static final String CLAIM_NAME               = "Pickaxe Limit Menu.ClaimSlot.Name";
            public static final String CLAIM_LORE               = "Pickaxe Limit Menu.ClaimSlot.Lore";
            public static final String BLOCKED_NAME             = "Pickaxe Limit Menu.BlockedSlot.Name";
            public static final String BLOCKED_LORE             = "Pickaxe Limit Menu.BlockedSlot.Lore";
            public static final String LOCKED_NAME              = "Pickaxe Limit Menu.LockedSlot.Name";
            public static final String LOCKED_LORE              = "Pickaxe Limit Menu.LockedSlot.Lore";
            public static final String ACTIVE_NAME              = "Pickaxe Limit Menu.ActiveSlot.Name";
            public static final String PURCHASE_NAME_AFFORDABLE    = "Pickaxe Limit Menu.PurchaseButton.NameAffordable";
            public static final String PURCHASE_NAME_TOO_EXPENSIVE = "Pickaxe Limit Menu.PurchaseButton.NameTooExpensive";
            public static final String PURCHASE_LORE            = "Pickaxe Limit Menu.PurchaseButton.Lore";
            public static final String PURCHASE_ACTION_AFFORDABLE    = "Pickaxe Limit Menu.PurchaseButton.ActionAffordable";
            public static final String PURCHASE_ACTION_TOO_EXPENSIVE = "Pickaxe Limit Menu.PurchaseButton.ActionTooExpensive";
            public static final String MAXED_NAME               = "Pickaxe Limit Menu.MaxedButton.Name";
            public static final String MAXED_LORE               = "Pickaxe Limit Menu.MaxedButton.Lore";
        }

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
            public static final String STORAGE_ACTION_LINE = "Pickaxe Menu.StorageAction";
            public static final String REPAIR_ACTION_LINE  = "Pickaxe Menu.RepairAction";
        }

        /**
         * Preferences menu item paths.
         */
        public static final class PreferencesMenu
        {
            private PreferencesMenu() {}

            public static final String ANIMATIONS_MATERIAL = "Preferences Menu.Animations.Material";
            public static final String ANIMATIONS_NAME     = "Preferences Menu.Animations.Name";
            public static final String ANIMATIONS_LORE     = "Preferences Menu.Animations.Lore";
            public static final String ENCHANTS_MATERIAL   = "Preferences Menu.Enchants.Material";
            public static final String ENCHANTS_NAME       = "Preferences Menu.Enchants.Name";
            public static final String ENCHANTS_LORE       = "Preferences Menu.Enchants.Lore";
            public static final String MESSAGES_MATERIAL   = "Preferences Menu.Messages.Material";
            public static final String MESSAGES_NAME       = "Preferences Menu.Messages.Name";
            public static final String MESSAGES_LORE       = "Preferences Menu.Messages.Lore";
            public static final String CLOSE_MATERIAL      = "Preferences Menu.Close.Material";
            public static final String CLOSE_NAME          = "Preferences Menu.Close.Name";
            public static final String CLOSE_LORE          = "Preferences Menu.Close.Lore";
        }

        /**
         * Enchants preferences menu item paths.
         */
        public static final class EnchantsMenu
        {
            private EnchantsMenu() {}

            public static final String BACK_MATERIAL      = "Enchants Menu.Back.Material";
            public static final String BACK_NAME          = "Enchants Menu.Back.Name";
            public static final String BACK_LORE          = "Enchants Menu.Back.Lore";
            public static final String PREV_MATERIAL      = "Enchants Menu.Prev.Material";
            public static final String PREV_NAME          = "Enchants Menu.Prev.Name";
            public static final String PREV_LORE          = "Enchants Menu.Prev.Lore";
            public static final String NEXT_MATERIAL      = "Enchants Menu.Next.Material";
            public static final String NEXT_NAME          = "Enchants Menu.Next.Name";
            public static final String NEXT_LORE          = "Enchants Menu.Next.Lore";
            public static final String PAGE_INFO_MATERIAL = "Enchants Menu.PageInfo.Material";
            public static final String PAGE_INFO_NAME     = "Enchants Menu.PageInfo.Name";
            public static final String ENCHANT_ITEM_LORE  = "Enchants Menu.EnchantItem.Lore";
        }

        /**
         * Enchant option sub-menu item paths.
         */
        public static final class EnchantOptionMenu
        {
            private EnchantOptionMenu() {}

            public static final String BACK_MATERIAL     = "Enchant Option Menu.Back.Material";
            public static final String BACK_NAME         = "Enchant Option Menu.Back.Name";
            public static final String BACK_LORE         = "Enchant Option Menu.Back.Lore";
            public static final String ENABLED_MATERIAL  = "Enchant Option Menu.EnabledMaterial";
            public static final String DISABLED_MATERIAL = "Enchant Option Menu.DisabledMaterial";
            public static final String MESSAGE_NAME      = "Enchant Option Menu.Message.Name";
            public static final String MESSAGE_LORE      = "Enchant Option Menu.Message.Lore";
            public static final String TITLE_NAME        = "Enchant Option Menu.Title.Name";
            public static final String TITLE_LORE        = "Enchant Option Menu.Title.Lore";
            public static final String SOUND_NAME        = "Enchant Option Menu.Sound.Name";
            public static final String SOUND_LORE        = "Enchant Option Menu.Sound.Lore";
            public static final String HOLOGRAM_NAME     = "Enchant Option Menu.Hologram.Name";
            public static final String HOLOGRAM_LORE     = "Enchant Option Menu.Hologram.Lore";
            public static final String PARTICLES_NAME    = "Enchant Option Menu.Particles.Name";
            public static final String PARTICLES_LORE    = "Enchant Option Menu.Particles.Lore";
        }

        /**
         * Animations preferences sub-menu item paths.
         */
        public static final class AnimationsMenu
        {
            private AnimationsMenu() {}

            public static final String BACK_MATERIAL = "Animations Menu.Back.Material";
            public static final String BACK_NAME     = "Animations Menu.Back.Name";
            public static final String BACK_LORE     = "Animations Menu.Back.Lore";

            public static final String PICKUP_LABEL_MATERIAL = "Animations Menu.PickupAnimation.Label.Material";
            public static final String PICKUP_LABEL_NAME     = "Animations Menu.PickupAnimation.Label.Name";
            public static final String PICKUP_LABEL_LORE     = "Animations Menu.PickupAnimation.Label.Lore";
            public static final String PICKUP_MATERIAL          = "Animations Menu.PickupAnimation.Material";
            public static final String PICKUP_SELECTED_MATERIAL = "Animations Menu.PickupAnimation.SelectedMaterial";
            public static final String PICKUP_DEFAULT_NAME   = "Animations Menu.PickupAnimation.Default.Name";
            public static final String PICKUP_DEFAULT_LORE   = "Animations Menu.PickupAnimation.Default.Lore";
            public static final String PICKUP_SIMPLE_NAME    = "Animations Menu.PickupAnimation.Simple.Name";
            public static final String PICKUP_SIMPLE_LORE    = "Animations Menu.PickupAnimation.Simple.Lore";
            public static final String PICKUP_DISABLED_NAME  = "Animations Menu.PickupAnimation.Disabled.Name";
            public static final String PICKUP_DISABLED_LORE  = "Animations Menu.PickupAnimation.Disabled.Lore";

            public static final String SPEED_LABEL_MATERIAL = "Animations Menu.EnchantSpeed.Label.Material";
            public static final String SPEED_LABEL_NAME     = "Animations Menu.EnchantSpeed.Label.Name";
            public static final String SPEED_LABEL_LORE     = "Animations Menu.EnchantSpeed.Label.Lore";
            public static final String SPEED_MATERIAL          = "Animations Menu.EnchantSpeed.Material";
            public static final String SPEED_SELECTED_MATERIAL = "Animations Menu.EnchantSpeed.SelectedMaterial";
            public static final String SPEED_OPTION_LORE    = "Animations Menu.EnchantSpeed.OptionLore";

            public static final String ATOM_LABEL_MATERIAL   = "Animations Menu.AtomFX.Label.Material";
            public static final String ATOM_LABEL_NAME       = "Animations Menu.AtomFX.Label.Name";
            public static final String ATOM_LABEL_LORE       = "Animations Menu.AtomFX.Label.Lore";
            public static final String ATOM_ENABLED_MATERIAL  = "Animations Menu.AtomFX.EnabledMaterial";
            public static final String ATOM_DISABLED_MATERIAL = "Animations Menu.AtomFX.DisabledMaterial";
            public static final String ATOM_SOUND_NAME       = "Animations Menu.AtomFX.Sound.Name";
            public static final String ATOM_SOUND_LORE       = "Animations Menu.AtomFX.Sound.Lore";
            public static final String ATOM_PARTICLES_NAME   = "Animations Menu.AtomFX.Particles.Name";
            public static final String ATOM_PARTICLES_LORE   = "Animations Menu.AtomFX.Particles.Lore";
            public static final String ATOM_HOLOGRAM_NAME    = "Animations Menu.AtomFX.Hologram.Name";
            public static final String ATOM_HOLOGRAM_LORE    = "Animations Menu.AtomFX.Hologram.Lore";
            public static final String ATOM_GLOW_NAME        = "Animations Menu.AtomFX.Glow.Name";
            public static final String ATOM_GLOW_LORE        = "Animations Menu.AtomFX.Glow.Lore";

            public static final String ESSENCE_LABEL_MATERIAL   = "Animations Menu.EssenceFX.Label.Material";
            public static final String ESSENCE_LABEL_NAME       = "Animations Menu.EssenceFX.Label.Name";
            public static final String ESSENCE_LABEL_LORE       = "Animations Menu.EssenceFX.Label.Lore";
            public static final String ESSENCE_ENABLED_MATERIAL  = "Animations Menu.EssenceFX.EnabledMaterial";
            public static final String ESSENCE_DISABLED_MATERIAL = "Animations Menu.EssenceFX.DisabledMaterial";
            public static final String ESSENCE_SOUND_NAME       = "Animations Menu.EssenceFX.Sound.Name";
            public static final String ESSENCE_SOUND_LORE       = "Animations Menu.EssenceFX.Sound.Lore";
            public static final String ESSENCE_PARTICLES_NAME   = "Animations Menu.EssenceFX.Particles.Name";
            public static final String ESSENCE_PARTICLES_LORE   = "Animations Menu.EssenceFX.Particles.Lore";
            public static final String ESSENCE_HOLOGRAM_NAME    = "Animations Menu.EssenceFX.Hologram.Name";
            public static final String ESSENCE_HOLOGRAM_LORE    = "Animations Menu.EssenceFX.Hologram.Lore";
            public static final String ESSENCE_GLOW_NAME        = "Animations Menu.EssenceFX.Glow.Name";
            public static final String ESSENCE_GLOW_LORE        = "Animations Menu.EssenceFX.Glow.Lore";
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

        /** Whether this enchant is passive (no triggered notification/effects). */
        public static final String ENCHANT_PASSIVE = "Passive";

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
