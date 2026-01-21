package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.commands.PickupAnimationCommand;
import org.benski.evolv3.commands.TestCommand;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.CommandRegistryManager;

import java.util.List;

/**
 * A registry to register commands.
 */
public class CommandRegistry implements Registry<BaseCommand>
{
    @Getter
    private static final CommandRegistry instance = new CommandRegistry();
    private final CommandRegistryManager registryManager = new CommandRegistryManager(Main.getInstance());

    /**
     * Register commands.
     */
    private final List<Class<? extends BaseCommand>> commands = List.of(
            TestCommand.class,
            PickupAnimationCommand.class
    );

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        commands.forEach(registryManager::registerCommand);
        registryManager.initializeCommands();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BaseCommand get(String id) {
        return registryManager.getCommand(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        registryManager.cleanup();
    }

}