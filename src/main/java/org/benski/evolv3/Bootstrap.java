package org.benski.evolv3;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;

/**
 * Bootstrap class for Evolv3 plugin. Handles custom enchantment registration during server
 * bootstrap phase.
 */
@SuppressWarnings("UnstableApiUsage")
public class Bootstrap implements PluginBootstrap {

  @Override
  public void bootstrap(@NotNull BootstrapContext context) {
    context
        .getLifecycleManager()
        .registerEventHandler(
            RegistryEvents.ENCHANTMENT.compose().newHandler(this::registerCustomEnchantments));
  }

  /**
   * Registers all custom enchantments during the compose phase.
   *
   * @param event The registry compose event
   */
  private void registerCustomEnchantments(
      RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.@NotNull Builder> event) {
    event
        .registry()
        .register(
            TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("evolv3", "efficiency")),
            builder ->
                builder
                    .description(Component.text("Efficiency"))
                    .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING))
                    .anvilCost(1)
                    .maxLevel(100)
                    .weight(10)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(1, 5))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(50, 5))
                    .activeSlots(EquipmentSlotGroup.MAINHAND));

    event
        .registry()
        .register(
            TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("evolv3", "frostbite")),
            builder ->
                builder
                    .description(Component.text("Frostbite"))
                    .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING))
                    .anvilCost(3)
                    .maxLevel(100)
                    .weight(5)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(10, 7))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(60, 7))
                    .activeSlots(EquipmentSlotGroup.MAINHAND));

    event
        .registry()
        .register(
            TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("evolv3", "furnace")),
            builder ->
                builder
                    .description(Component.text("Furnace"))
                    .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING))
                    .anvilCost(3)
                    .maxLevel(100)
                    .weight(5)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(10, 7))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(60, 7))
                    .activeSlots(EquipmentSlotGroup.MAINHAND));

    event
        .registry()
        .register(
            TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("evolv3", "alchemy")),
            builder ->
                builder
                    .description(Component.text("Alchemy"))
                    .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING))
                    .anvilCost(4)
                    .maxLevel(100)
                    .weight(3)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(15, 8))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(65, 8))
                    .activeSlots(EquipmentSlotGroup.MAINHAND));
  }
}
