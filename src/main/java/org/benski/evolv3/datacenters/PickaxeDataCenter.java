package org.benski.evolv3.datacenters;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.models.PickaxeProfile;
import org.bukkit.entity.Player;

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
    protected void initializeProfile(PickaxeProfile profile, Player player)
    {
        profile.initialize(player);
    }

    @Override
    protected String getProfileId(PickaxeProfile profile) {
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
    public void get(Player player, Consumer<PickaxeProfile> callback)
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
    protected void createNewProfile(Player player, String id)
    {
        var pickaxeProfile = new PickaxeProfile();
        pickaxeProfile.key = id;
        pickaxeProfile.owner = player;
        pickaxeProfile.level = 1;
        pickaxeProfile.xp = 0;
        pickaxeProfile.target = 100;
        pickaxeProfile.evolution = 1;

        player.getInventory().getItemInMainHand().setData("id", id);
        this.initializeProfile(pickaxeProfile, player);

        // Add to cache immediately so it's not recreated on next check
        data.put(id, pickaxeProfile);

        // Save to database
        pickaxeProfile.save(result -> {
            if (result) {
                player.sendMessage(formatMessage(player,
                        messagesConfig.getString(ConfigPaths.Messages.Pickaxe.PROFILE_CREATED,
                                "&aPickaxe profile created!")));
            }
        });
    }
}
