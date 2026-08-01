package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.commands.admin.EnchantPickaxeCommand;
import org.benski.evolv3.commands.admin.Evolv3Command;
import org.benski.evolv3.commands.player.BackpackCommand;
import org.benski.evolv3.commands.player.BalCommand;
import org.benski.evolv3.commands.player.CellPlayerCommand;
import org.benski.evolv3.commands.player.EnchantStyleCommand;
import org.benski.evolv3.commands.player.IslandCommand;
import org.benski.evolv3.commands.player.PickaxeCommand;
import org.benski.evolv3.commands.player.PickupAnimationCommand;
import org.benski.evolv3.commands.player.PreferencesCommand;
import org.benski.evolv3.commands.player.RankupCommand;
import org.benski.evolv3.commands.player.SkinCommand;
import org.benski.evolv3.commands.player.TestCommand;
import org.benski.evolv3.core.abstracts.BaseCommand;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.registry.CommandRegistryManager;

/** A registry to register commands. */
public class CommandRegistry implements Registry<BaseCommand> {
  @Getter private static final CommandRegistry instance = new CommandRegistry();
  private final CommandRegistryManager registryManager =
      new CommandRegistryManager(Main.getInstance());

  /** Register commands. */
  private final List<Class<? extends BaseCommand>> commands =
      List.of(
          PickupAnimationCommand.class,
          EnchantStyleCommand.class,
          Evolv3Command.class,
          PickaxeCommand.class,
          BackpackCommand.class,
          PreferencesCommand.class,
          BalCommand.class,
          EnchantPickaxeCommand.class,
          SkinCommand.class,
          RankupCommand.class,
          CellPlayerCommand.class,
          IslandCommand.class,
          TestCommand.class);

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    commands.forEach(registryManager::registerCommand);
    registryManager.initializeCommands();
  }

  /** {@inheritDoc} */
  @Override
  public BaseCommand get(String id) {
    return registryManager.getCommand(id);
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    registryManager.cleanup();
  }
}
