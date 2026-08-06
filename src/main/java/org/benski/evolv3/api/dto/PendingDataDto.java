package org.benski.evolv3.api.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified DTO for all pending animation states (enchant selection, evolution, prestige). The {@link
 * #type} field identifies which fields are populated. Fields irrelevant to the type are left null /
 * default.
 */
public final class PendingDataDto {

    /**
     * Identifies this record's animation type.
     */
    public PendingType type;

    /**
     * The player's UUID string — composite primary key with {@link #type}.
     */
    public String playerUuid;

    /**
     * Base64-encoded serialized pickaxe ItemStack. Populated by all types.
     */
    public String originalItemBase64;

    /**
     * The pickaxe's item-data key. Populated by all types.
     */
    public String pickaxeKey;

    // --- ENCHANT / PRESTIGE fields ---

    /**
     * For ENCHANT: display names of the three enchant options. For PRESTIGE: stone rolls encoded as
     * {@code "rarityName|enchantName"}.
     */
    public List<String> optionNames = new ArrayList<>();

    // --- PRESTIGE fields ---

    /**
     * Index of the stone the player selected (0–2), or {@code -1} if not yet chosen. PRESTIGE only.
     */
    public int selectedStoneIndex = -1;

    // --- EVOLUTION fields ---

    /**
     * {@link org.benski.evolv3.enums.EvolutionTier#level} before evolution. EVOLUTION only.
     */
    public int currentTierLevel;

    /**
     * {@link org.benski.evolv3.enums.EvolutionTier#level} after evolution. EVOLUTION only.
     */
    public int nextTierLevel;

    /**
     * Display name of the rolled rarity stone. EVOLUTION only.
     */
    public String rarityName;

    /**
     * {@code true} if the animation callback (evolution tier change / prestige execute) already fired
     * before the player disconnected. EVOLUTION and PRESTIGE.
     */
    public boolean callbackAlreadyApplied;
}
