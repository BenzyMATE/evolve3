package org.benski.evolv3.core.types;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

/**
 * Immutable configuration for a single enchant rarity tier.
 *
 * <p>Instances are created by {@link org.benski.evolv3.core.managers.EnchantAnimationConfig#load()}
 * from the {@code Rarities} section in {@code enchants.yml}. The {@code displayName} is the YAML
 * key (e.g. {@code "Common"}, {@code "Legendary"}) and doubles as the human-readable label shown in
 * animations.
 *
 * @param displayName YAML key and human-readable label (e.g. "Common")
 * @param textColor Adventure {@link TextColor} parsed from the hex {@code Color} field
 * @param particleColor Bukkit {@link Color} parsed from the hex {@code Color} field
 * @param concreteMaterial Concrete block material used for the CS:GO-style strip
 * @param chance Relative weight for weighted-random rarity rolling
 * @param cumulativeChance Running total of all preceding rarity weights plus this one
 */
public record RarityConfig(
    String displayName,
    TextColor textColor,
    Color particleColor,
    Material concreteMaterial,
    int chance,
    int cumulativeChance) {}
