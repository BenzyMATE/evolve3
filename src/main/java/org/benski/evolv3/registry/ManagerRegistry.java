package org.benski.evolv3.registry;

import lombok.Getter;
import org.benski.evolv3.core.managers.cell.*;
import org.benski.evolv3.core.managers.enchant.EnchantSelectionManager;
import org.benski.evolv3.core.managers.enchant.EvolutionSelectionManager;
import org.benski.evolv3.core.managers.mine.CaveManager;
import org.benski.evolv3.core.managers.mine.MineHologramManager;
import org.benski.evolv3.core.managers.mine.MineManager;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager;
import org.benski.evolv3.core.managers.world.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Initializes and cleans up all feature managers in dependency order.
 */
public class ManagerRegistry {
    @Getter
    private static final ManagerRegistry instance = new ManagerRegistry();

    /**
     * Holds the init and cleanup runnable for one feature group.
     */
    private record ManagerEntry(String name, Runnable init, Runnable cleanup) {
    }

    private final List<ManagerEntry> managers = List.of(
            new ManagerEntry(
                    "Blackholes",
                    () -> {
                        BlackholeManager.getInstance().initialize();
                        BlackholeHologramManager.getInstance().initialize();
                    },
                    () -> {
                        BlackholeHologramManager.getInstance().cleanup();
                        BlackholeManager.getInstance().cleanup();
                    }),
            new ManagerEntry(
                    "ZoneBorders",
                    () -> {
                        BorderManager.getInstance().initialize();
                        BorderHologramManager.getInstance().initialize();
                    },
                    () -> {
                        BorderHologramManager.getInstance().cleanup();
                        BorderManager.getInstance().cleanup();
                    }),
            new ManagerEntry(
                    "WorldFeatures",
                    () -> {
                        BeamManager.getInstance().initialize();
                        LaunchPadManager.getInstance().initialize();
                    },
                    () -> {
                        BeamManager.getInstance().cleanup();
                        LaunchPadManager.getInstance().cleanup();
                    }),
            new ManagerEntry(
                    "Mines",
                    () -> {
                        CaveManager.getInstance().initialize();
                        MineManager.getInstance().initialize();
                        MineHologramManager.getInstance().initialize();
                    },
                    () -> {
                        MineHologramManager.getInstance().cleanup();
                        CaveManager.getInstance().cleanup();
                        MineManager.getInstance().cleanup();
                    }),
            new ManagerEntry(
                    "Spawners",
                    () -> {
                        SpawnerManager.getInstance().initialize();
                        SpawnerHologramManager.getInstance().initialize();
                    },
                    () -> {
                        SpawnerHologramManager.getInstance().cleanup();
                        SpawnerManager.getInstance().cleanup();
                    }),
            new ManagerEntry(
                    "Selections",
                    () -> {
                        EnchantSelectionManager.getInstance().loadPendingFromDatabase();
                        EvolutionSelectionManager.getInstance().loadPendingFromDatabase();
                    },
                    () -> {
                        EvolutionSelectionManager.getInstance().cleanup();
                        EnchantSelectionManager.getInstance().cleanup();
                    }),
            new ManagerEntry(
                    "Cells",
                    () -> {
                        CellWorldGuardManager.getInstance().initialize();
                        CellManager.getInstance().initialize();
                        CellRentManager.getInstance().initialize();
                        IslandManager.getInstance().initialize();
                        IslandWorldManager.getInstance().initialize();
                        CellHologramManager.getInstance().initialize();
                    },
                    () -> {
                        CellHologramManager.getInstance().cleanup();
                        CellRentManager.getInstance().cleanup();
                        IslandWorldManager.getInstance().cleanup();
                        IslandManager.getInstance().cleanup();
                        CellManager.getInstance().cleanup();
                    }),
            new ManagerEntry("Pickaxe", () -> {
            }, () -> OmnitoolManager.getInstance().cleanup()));

    /**
     * Initializes all feature managers in dependency order.
     */
    public void initialize() {
        managers.forEach(e -> e.init().run());
    }

    /**
     * Cleans up all feature managers in reverse initialization order.
     */
    public void cleanup() {
        List<ManagerEntry> reversed = new ArrayList<>(managers);
        Collections.reverse(reversed);
        reversed.forEach(e -> e.cleanup().run());
    }
}
