package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.commands.BackpackCommand;
import org.benski.evolv3.commands.CellPlayerCommand;
import org.benski.evolv3.commands.IslandCommand;
import org.benski.evolv3.commands.BalCommand;
import org.benski.evolv3.commands.EnchantPickaxeCommand;
import org.benski.evolv3.commands.Evolv3Command;
import org.benski.evolv3.commands.PickaxeCommand;
import org.benski.evolv3.commands.PickupAnimationCommand;
import org.benski.evolv3.commands.PreferencesCommand;
import org.benski.evolv3.commands.RankupCommand;
import org.benski.evolv3.commands.SkinCommand;
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
            PickupAnimationCommand.class,
            Evolv3Command.class,
            PickaxeCommand.class,
            BackpackCommand.class,
            PreferencesCommand.class,
            BalCommand.class,
            EnchantPickaxeCommand.class,
            SkinCommand.class,
            RankupCommand.class,
            CellPlayerCommand.class,
            IslandCommand.class
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