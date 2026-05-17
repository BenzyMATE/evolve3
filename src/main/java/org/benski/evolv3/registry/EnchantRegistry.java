package org.benski.evolv3.registry;

import java.util.List;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.benski.evolv3.core.interfaces.Registry;
import org.benski.evolv3.core.managers.EnchantRegistryManager;
import org.benski.evolv3.enchants.AlchemyEnchant;
import org.benski.evolv3.enchants.AutoSellEnchant;
import org.benski.evolv3.enchants.CascadeEnchant;
import org.benski.evolv3.enchants.DrillEnchant;
import org.benski.evolv3.enchants.EfficiencyEnchant;
import org.benski.evolv3.enchants.ExplosiveEnchant;
import org.benski.evolv3.enchants.ExtractionEnchant;
import org.benski.evolv3.enchants.FortuneEnchant;
import org.benski.evolv3.enchants.FrostbiteEnchant;
import org.benski.evolv3.enchants.FurnaceEnchant;
import org.benski.evolv3.enchants.JackhammerEnchant;
import org.benski.evolv3.enchants.LazerEnchant;
import org.benski.evolv3.enchants.MerchantEnchant;
import org.benski.evolv3.enchants.NuclearShowerEnchant;
import org.benski.evolv3.enchants.RadioactiveEnchant;
import org.benski.evolv3.enchants.ScavengerEnchant;
import org.benski.evolv3.enchants.ThorEnchant;
import org.benski.evolv3.enchants.VortexEnchant;
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
