package org.benski.evolv3.datacenters;

import lombok.experimental.ExtensionMethod;
import org.benski.evolv3.core.abstracts.BaseDataCenter;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.ItemStackExtensions;
import org.benski.evolv3.core.managers.OmnitoolManager;
import org.benski.evolv3.models.pickaxe.PickaxeProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    protected String getLogPrefix() {
        return "Pickaxe";
    }

    @Override
    public void get(@NotNull Player player, Consumer<PickaxeProfile> callback)
    {
        callback.accept(getLocal(player));
    }

    @Override
    public PickaxeProfile getLocal(@NotNull Player player)
    {
        String pickaxeId = player.getInventory().getItemInMainHand().getData("id");
        if (pickaxeId == null || pickaxeId.isEmpty())
        {
            // In omnitool mode the real pickaxe is stored away — look it up there
            ItemStack stored = OmnitoolManager.getInstance().getStoredPickaxe(player);
            if (stored != null) {
                pickaxeId = stored.getData("id");
            }
            if (pickaxeId == null || pickaxeId.isEmpty()) return null;
        }

        return data.get(pickaxeId);
    }

    /**
     * Returns the cached {@link PickaxeProfile} for the given item, or {@code null} if not cached.
     * Useful when the item is not currently in the player's hand (e.g. during an animation that
     * has temporarily replaced the player's held item).
     *
     * @param item the pickaxe item stack to look up
     * @return the cached profile, or {@code null}
     */
    public PickaxeProfile getLocalFromItem(@NotNull ItemStack item)
    {
        String pickaxeId = item.getData("id");
        if (pickaxeId == null || pickaxeId.isEmpty()) return null;
        return data.get(pickaxeId);
    }

    /**
     * Silently loads a pickaxe profile into the cache if it is not already present.
     * Used at player join to pre-warm stored pickaxes so the /pickaxe menu shows
     * full lore immediately without a "not loaded" fallback.
     *
     * @param player     the owning player (used for {@code initialize})
     * @param pickaxeId  the pickaxe UUID string to load
     */
    public void tryPreload(Player player, String pickaxeId) {
        if (pickaxeId == null || pickaxeId.isEmpty() || data.containsKey(pickaxeId)) return;
        mongoManager.getByIdAsync("Pickaxes", "id", pickaxeId, PickaxeProfile.class)
                .thenAcceptAsync(profile -> {
                    if (profile == null) return;
                    profile.initialize(player);
                    data.put(pickaxeId, profile);
                }, org.benski.evolv3.core.managers.TaskManager.getInstance()::runSync)
                .exceptionally(e -> {
                    org.benski.evolv3.Main.getInstance().getLogger()
                            .warning("Failed to preload pickaxe " + pickaxeId + ": " + e.getMessage());
                    return null;
                });
    }

    /**
     * Saves all cached pickaxe profiles and blocks until every save completes (or times out).
     * Should be called during plugin disable to prevent data loss on shutdown.
     * Waits up to 10 seconds for all saves to finish before returning.
     */
    public void saveAll()
    {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PickaxeProfile profile : data.values()) {
            futures.add(mongoManager.upsertAsync(profile));
        }
        if (futures.isEmpty()) return;
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            org.benski.evolv3.Main.getInstance().getLogger()
                    .severe("Timed out waiting for pickaxe profile saves on shutdown: " + e.getMessage());
        }
    }

    @Override
    protected void createNewProfile(@NotNull Player player, String id)
    {
        PickaxeProfile pickaxeProfile = PickaxeProfile.createDefault(player, id);

        player.getInventory().getItemInMainHand().setData("id", id);

        data.put(id, pickaxeProfile);

        pickaxeProfile.save(result -> {
            if (result) {
                player.sendMessage(formatMessage(player,
                        ConfigRegistry.getInstance().get(ConfigNames.MESSAGES)
                                .getString(ConfigPaths.Messages.Pickaxe.PROFILE_CREATED,
                                        "&aPickaxe profile created!")));
            }
        });
    }
}
