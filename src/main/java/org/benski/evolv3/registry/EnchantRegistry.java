package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.registry.EnchantRegistryManager;
import org.benski.evolv3.enchants.mining.CascadeEnchant;
import org.benski.evolv3.enchants.mining.DrillEnchant;
import org.benski.evolv3.enchants.mining.ExplosiveEnchant;
import org.benski.evolv3.enchants.mining.FrostbiteEnchant;
import org.benski.evolv3.enchants.mining.JackhammerEnchant;
import org.benski.evolv3.enchants.mining.LazerEnchant;
import org.benski.evolv3.enchants.mining.NuclearShowerEnchant;
import org.benski.evolv3.enchants.mining.ThorEnchant;
import org.benski.evolv3.enchants.mining.VortexEnchant;
import org.benski.evolv3.enchants.passive.AlchemyEnchant;
import org.benski.evolv3.enchants.passive.AutoSellEnchant;
import org.benski.evolv3.enchants.passive.EfficiencyEnchant;
import org.benski.evolv3.enchants.passive.ExtractionEnchant;
import org.benski.evolv3.enchants.passive.FortuneEnchant;
import org.benski.evolv3.enchants.passive.FurnaceEnchant;
import org.benski.evolv3.enchants.passive.MerchantEnchant;
import org.benski.evolv3.enchants.passive.RadioactiveEnchant;
import org.benski.evolv3.enchants.passive.ScavengerEnchant;
import org.bukkit.event.Listener;

/**
 * A registry to register custom enchantment listeners. In Paper 1.21+, enchantments themselves are
 * registered in Evolv3Bootstrap. This registry only handles the event listeners for enchantment
 * functionality.
 */
public class EnchantRegistry implements Registry<Listener> {
  @Getter private static final EnchantRegistry instance = new EnchantRegistry();
  private final EnchantRegistryManager registryManager =
      new EnchantRegistryManager(Main.getInstance());

  /**
   * Register custom enchantment listeners here. Add your custom enchantment listener classes to
   * this list.
   */
  private final List<Class<? extends Listener>> enchantmentListeners =
      List.of(
          ExplosiveEnchant.class,
          EfficiencyEnchant.class,
          FrostbiteEnchant.class,
          FurnaceEnchant.class,
          AlchemyEnchant.class,
          ScavengerEnchant.class,
          MerchantEnchant.class,
          AutoSellEnchant.class,
          ExtractionEnchant.class,
          RadioactiveEnchant.class,
          FortuneEnchant.class,
          DrillEnchant.class,
          LazerEnchant.class,
          ThorEnchant.class,
          VortexEnchant.class,
          CascadeEnchant.class,
          JackhammerEnchant.class,
          NuclearShowerEnchant.class);

  /** {@inheritDoc} */
  @Override
  public void initialize() {
    enchantmentListeners.forEach(registryManager::registerEnchantmentListener);
    registryManager.initializeListeners();
  }

  /** {@inheritDoc} */
  @Override
  public Listener get(String id) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void cleanup() {
    registryManager.cleanup();
  }
}
