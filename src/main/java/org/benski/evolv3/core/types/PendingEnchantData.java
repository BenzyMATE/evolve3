package org.benski.evolv3.core.types;

import java.util.List;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Holds the state of an in-progress or interrupted enchant selection.
 *
 * <p>Created when a selection animation begins and kept alive across player disconnects so that:
 *
 * <ul>
 *   <li>The player's pickaxe can be restored on rejoin.
 *   <li>The same enchant options are shown again — preventing players from leaving and rejoining to
 *       fish for better rarities.
 * </ul>
 *
 * <p>The entry is removed from {@link org.benski.evolv3.core.managers.EnchantSelectionManager} only
 * when the player successfully makes a selection (callback fires) or the selection is explicitly
 * cancelled by the server.
 */
public class PendingEnchantData {
  private final List<EnchantOption> options;
  private final BiConsumer<Player, EnchantOption> onSelect;

  /** The item taken from the player's hand when the animation started. */
  private ItemStack originalItem;

  /**
   * The pickaxe's item-data key. Non-null when the selection was started with {@link
   * org.benski.evolv3.core.managers.EnchantSelectionManager#startSelection(Player, String,
   * BiConsumer)}, which enables full MongoDB persistence across server restarts.
   */
  private String pickaxeKey;

  /**
   * @param options the fixed enchant options generated for this roll
   * @param onSelect the callback to apply the enchant once selected
   */
  public PendingEnchantData(
      List<EnchantOption> options, BiConsumer<Player, EnchantOption> onSelect) {
    this.options = options;
    this.onSelect = onSelect;
  }

  /**
   * @param options the fixed enchant options generated for this roll
   * @param onSelect the callback to apply the enchant once selected (may be {@code null} when
   *     loaded from DB)
   * @param pickaxeKey the pickaxe's item-data key, or {@code null} for non-persistent sessions
   */
  public PendingEnchantData(
      List<EnchantOption> options, BiConsumer<Player, EnchantOption> onSelect, String pickaxeKey) {
    this.options = options;
    this.onSelect = onSelect;
    this.pickaxeKey = pickaxeKey;
  }

  public List<EnchantOption> getOptions() {
    return options;
  }

  public BiConsumer<Player, EnchantOption> getOnSelect() {
    return onSelect;
  }

  public ItemStack getOriginalItem() {
    return originalItem;
  }

  public void setOriginalItem(ItemStack item) {
    this.originalItem = item;
  }

  public String getPickaxeKey() {
    return pickaxeKey;
  }

  public void setPickaxeKey(String key) {
    this.pickaxeKey = key;
  }
}
