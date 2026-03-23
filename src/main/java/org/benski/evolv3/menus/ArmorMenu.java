package org.benski.evolv3.menus;

import org.benski.evolv3.core.abstracts.BaseMenu;
import org.benski.evolv3.core.builders.ItemBuilder;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.types.MenuType;
import org.benski.evolv3.datacenters.ArmorDataCenter;
import org.benski.evolv3.enums.ArmorType;
import org.benski.evolv3.models.armor.ArmorItem;
import org.benski.evolv3.models.armor.ArmorProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

/**
 *  Menu for managing individual armor pieces.
 *  Allows players to toggle armor effects on/off and view stats.
 */
public class ArmorMenu extends BaseMenu
{
    private static final MenuType MENU_TYPE = MenuType.register("armor");
    private static final int TOGGLE_SLOT = 13;

    private final ArmorDataCenter armorDataCenter;
    private final Map<UUID, ArmorType> selectedType = new HashMap<>();

    public ArmorMenu(JavaPlugin plugin)
    {
        super(plugin);
        this.armorDataCenter = DataCenterRegistry.getInstance().getArmorDataCenter();
    }

    /**
     * Opens the armor menu for a specific armor type.
     *
     * @param player    The player to open the menu for
     * @param armorType The armor type to display
     */
    public void openMenu(Player player, ArmorType armorType)
    {
        selectedType.put(player.getUniqueId(), armorType);
        super.openMenu(player);
    }

    @Override
    public void cleanup(Player player)
    {
        selectedType.remove(player.getUniqueId());
        super.cleanup(player);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getMenuTitle(Player player)
    {
        FileConfiguration menus = ConfigRegistry.getInstance().get(ConfigNames.MENUS);
        ArmorType armorType = selectedType.get(player.getUniqueId());
        if (armorType == null) {
            return menus.getString(ConfigPaths.Menus.ArmorMenu.TITLE, "&8Armor");
        }
        return menus.getString(ConfigPaths.Menus.ArmorMenu.TITLE_WITH_TYPE, "&8%type% Settings")
                .replace("%type%", armorType.getDisplayName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getMenuSize()
    {
        return 27;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateMenu(Player player, Inventory inventory)
    {
        ArmorType armorType = selectedType.get(player.getUniqueId());
        ArmorProfile profile = armorDataCenter.getLocal(player);
        if (profile == null || armorType == null) {
            return;
        }

        ArmorItem armor = profile.getArmor(armorType);
        if (armor == null) {
            return;
        }

        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, border);
        }

        Material toggleMaterial = armor.isEnabled() ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String toggleName = armor.isEnabled() ? "&a&lENABLED" : "&c&lDISABLED";
        String toggleAction = armor.isEnabled() ? "&7Click to &cdisable" : "&7Click to &aenable";

        ItemStack toggleItem = new ItemBuilder(toggleMaterial)
                .name(toggleName)
                .lore(
                        "",
                        "&7" + armorType.getDisplayName() + " Effects",
                        "",
                        "&7Level: &f" + armor.getLevel(),
                        "&7Multiplier: &f" + String.format("%.2fx", armor.getMultiplier()),
                        "&7Progress: &f" + armor.getProgressPercentage(),
                        "",
                        toggleAction
                )
                .build();
        inventory.setItem(TOGGLE_SLOT, toggleItem);

        ItemStack armorPreview = new ItemBuilder(armor.getMaterial())
                .name("&b" + armorType.getDisplayName())
                .lore(
                        "",
                        "&7Evolution Tier: &f" + (armor.getEvolutionTier() + 1) + "/5",
                        "&7Next Evolution: &fLevel " + ((armor.getEvolutionTier() + 1) * armor.getEvolutionInterval())
                )
                .build();
        inventory.setItem(4, armorPreview);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean handleClick(Player player, int slot, InventoryClickEvent event)
    {
        if (slot != TOGGLE_SLOT) {
            return true;
        }

        ArmorType armorType = selectedType.get(player.getUniqueId());
        ArmorProfile profile = armorDataCenter.getLocal(player);
        if (profile == null || armorType == null) {
            return true;
        }

        ArmorItem armor = profile.getArmor(armorType);
        if (armor == null) {
            return true;
        }

        armor.setEnabled(!armor.isEnabled());

        if (armorType == ArmorType.BOOTS) {
            profile.updatePlayerSpeed(player);
        }

        var messagesConfig = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
        if (armor.isEnabled()) {
            String msg = messagesConfig.getString(ConfigPaths.Messages.Armor.TOGGLE_ENABLED,
                    "&a&lEnabled &7effects for %armor_piece%");
            player.sendMessage(formatMessage(player, msg.replace("%armor_piece%", armorType.getDisplayName())));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        } else {
            String msg = messagesConfig.getString(ConfigPaths.Messages.Armor.TOGGLE_DISABLED,
                    "&c&lDisabled &7effects for %armor_piece%");
            player.sendMessage(formatMessage(player, msg.replace("%armor_piece%", armorType.getDisplayName())));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }

        refresh(player);

        profile.save(null);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuType getMenuType()
    {
        return MENU_TYPE;
    }
}
