package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.animations.AtomAnimation;
import org.benski.evolv3.animations.DrillAnimation;
import org.benski.evolv3.animations.LazerAnimation;
import org.benski.evolv3.animations.CascadeAnimation;
import org.benski.evolv3.animations.JackhammerAnimation;
import org.benski.evolv3.animations.NuclearShowerAnimation;
import org.benski.evolv3.animations.ThorAnimation;
import org.benski.evolv3.animations.VortexAnimation;
import org.benski.evolv3.animations.EnchantAnimation;
import org.benski.evolv3.animations.EssenceAnimation;
import org.benski.evolv3.animations.ExplosionAnimation;
import org.benski.evolv3.animations.HologramAnimation;
import org.benski.evolv3.animations.ResinAnimation;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.animations.pickup.DissolvePickupAnimation;
import org.benski.evolv3.animations.pickup.TeleportPickupAnimation;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.managers.AnimationRegistryManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * Registry for animations.
 * Delegates all operations to {@link AnimationRegistryManager}.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * animationRegistry.play("essence", location, List.of(player), 5);
 * animationRegistry.play("hologram", location, List.of(player), "&d+5 Essence");
 * animationRegistry.play("explosion", location);
 * }</pre>
 */
public class AnimationRegistry implements Registry<BaseAnimation>
{
    @Getter
    private static final AnimationRegistry instance = new AnimationRegistry();

    @Getter
    private final AnimationRegistryManager manager = new AnimationRegistryManager(Main.getInstance());

    /**
     * Register animations here.
     */
    private final List<Class<? extends BaseAnimation>> animations = List.of(
            ExplosionAnimation.class,
            EnchantAnimation.class,
            TeleportPickupAnimation.class,
            DissolvePickupAnimation.class,
            EssenceAnimation.class,
            AtomAnimation.class,
            HologramAnimation.class,
            ResinAnimation.class,
            DrillAnimation.class,
            LazerAnimation.class,
            ThorAnimation.class,
            VortexAnimation.class,
            CascadeAnimation.class,
            JackhammerAnimation.class,
            NuclearShowerAnimation.class
    );

    @Override
    public void initialize() {
        EnchantAnimationConfig.load();
        animations.forEach(manager::registerAnimation);
        manager.initialize();

        // Apply budget limits from config (avoids hardcoded values in the manager)
        FileConfiguration cfg = ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
        if (cfg != null) {
            manager.setMaxConcurrentAnimations(
                    cfg.getInt(ConfigPaths.Default.Animations.MAX_CONCURRENT, 100));
            manager.setMaxResourceCost(
                    cfg.getInt(ConfigPaths.Default.Animations.MAX_RESOURCE_COST, 500));
        }
    }

    @Override
    public BaseAnimation get(String id) {
        throw new UnsupportedOperationException(
                "AnimationRegistry does not store instances. Use create(id, args) or play(id, location, viewers).");
    }

    @Override
    public void cleanup() {
        manager.cleanup();
    }

    public BaseAnimation create(String identifier, Object... args) {
        return manager.create(identifier, args);
    }

    public void play(String identifier, Location location, Collection<Player> viewers, Object... args) {
        manager.play(identifier, location, viewers, args);
    }

    public BaseAnimation play(String identifier, Location location) {
        return manager.play(identifier, location);
    }

    public BaseAnimation play(String identifier, Location location, Collection<Player> viewers) {
        return manager.play(identifier, location, viewers);
    }

    public void play(BaseAnimation animation, Location location) {
        manager.play(animation, location);
    }

    public boolean play(BaseAnimation animation, Location location, Collection<Player> viewers) {
        return manager.play(animation, location, viewers);
    }

    public boolean canPlay(BaseAnimation animation) {
        return manager.canPlay(animation);
    }

    public void stopAll() {
        manager.stopAll();
    }

    public int getActiveAnimationCount() {
        return manager.getActiveAnimationCount();
    }
}
