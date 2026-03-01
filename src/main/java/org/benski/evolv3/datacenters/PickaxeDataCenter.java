package org.benski.evolv3.datacenters;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static org.benski.evolv3.core.helpers.Utils.formatMessage;

@ExtensionMethod(ItemStackExtensions.class)
public class PickaxeDataCenter extends BaseDataCenter<PickaxeProfile>
{
    @Override
    protected String getCollectionName() {
        return "Pickaxes";
    }

    @Override
    protected Class<PickaxeProfile> getProfileClass() {
        return PickaxeProfile.class;
    }

    @Override
    protected void initializeProfile(@NotNull PickaxeProfile profile, Player player)
    {
        profile.initialize(player);
    }

    @Override
    protected String getProfileId(@NotNull PickaxeProfile profile) {
        return profile.key;
    }

    @Override
    protected String getLoadingMessagePath() {
        return ConfigPaths.Messages.Pickaxe.LOADING_PROFILE;
    }

    @Override
    protected String getLogPrefix() {
        return "Pickaxe";
    }

    @Override
    public void get(@NotNull Player player, Consumer<PickaxeProfile> callback)
    {
        String pickaxeId = player.getInventory().getItemInMainHand().getData("id");
        if (pickaxeId == null || pickaxeId.isEmpty())
        {
            return;
        }

        if (data.containsKey(pickaxeId))
        {
            PickaxeProfile profile = data.get(pickaxeId);
            profile.initialize(player);
            callback.accept(profile);
        }
    }

    @Override
    public PickaxeProfile getLocal(@NotNull Player player)
    {
        String pickaxeId = player.getInventory().getItemInMainHand().getData("id");
        if (pickaxeId == null || pickaxeId.isEmpty())
        {
            return null;
        }

        PickaxeProfile profile = data.get(pickaxeId);
        if (profile != null)
        {
            profile.initialize(player);
        }
        return profile;
    }

    @Override
    protected void createNewProfile(@NotNull Player player, String id)
    {
        var pickaxeProfile = new PickaxeProfile();
        pickaxeProfile.key = id;
        pickaxeProfile.ownerUuid = player.getUniqueId();

        FileConfiguration defaultConfig = storageManager.getConfig(ConfigNames.DEFAULT);
        pickaxeProfile.level = defaultConfig.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_LEVEL, 1);
        pickaxeProfile.xp = defaultConfig.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_XP, 0);
        pickaxeProfile.target = defaultConfig.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_TARGET, 100);
        pickaxeProfile.evolution = 1;
        pickaxeProfile.currentEssence = defaultConfig.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_CURRENT_ESSENCE, 0);
        pickaxeProfile.targetEssence = defaultConfig.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ESSENCE_TARGET, 125);
        pickaxeProfile.enchantRolls = defaultConfig.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_ENCHANT_ROLLS, 0);
        pickaxeProfile.evolutionAtoms = defaultConfig.getInt(ConfigPaths.Default.Pickaxe.DEFAULT_EVOLUTION_ATOMS, 0);
        pickaxeProfile.atomTarget = defaultConfig.getInt(ConfigPaths.Default.Atoms.DEFAULT_ATOM_TARGET, 100);
        pickaxeProfile.evolutionRolls = 0;

        player.getInventory().getItemInMainHand().setData("id", id);
        this.initializeProfile(pickaxeProfile, player);

        data.put(id, pickaxeProfile);

        pickaxeProfile.save(result -> {
            if (result) {
                player.sendMessage(formatMessage(player,
                        messagesConfig.getString(ConfigPaths.Messages.Pickaxe.PROFILE_CREATED,
                                "&aPickaxe profile created!")));
            }
        });
    }
}
