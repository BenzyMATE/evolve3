package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.abstracts.BaseSchedule;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.ScheduleRegistryManager;
import org.benski.evolv3.schedules.PickaxeCheckSchedule;

import java.util.List;

/**
 * A registry to register scheduled/repeating tasks.
 */
public class ScheduleRegistry implements Registry<BaseSchedule>
{
    @Getter
    private static final ScheduleRegistry instance = new ScheduleRegistry();
    private final ScheduleRegistryManager registryManager = new ScheduleRegistryManager(Main.getInstance());

    /**
     * Register schedules here.
     * Add your schedule classes to this list.
     */
    private final List<Class<? extends BaseSchedule>> schedules = List.of(
            PickaxeCheckSchedule.class
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        schedules.forEach(registryManager::registerSchedule);
        registryManager.initializeSchedules();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BaseSchedule get(String id) {
        return registryManager.getSchedule(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        registryManager.cleanup();
    }

}
