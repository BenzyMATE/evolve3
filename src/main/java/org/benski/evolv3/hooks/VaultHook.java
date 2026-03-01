package org.benski.evolv3.hooks;

import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.benski.evolv3.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Hook for Vault economy integration.
 * Provides safe access to economy operations with graceful fallback if Vault is not installed.
 */
public class VaultHook
{
    @Getter
    private static final VaultHook instance = new VaultHook();

    private Economy economy;
    private boolean enabled;
    private final Logger logger = Main.getInstance().getLogger();

    private VaultHook() {}

    /**
     * Initializes the Vault hook by attempting to connect to the economy provider.
     * Should be called during plugin enable.
     *
     * @return true if Vault economy was successfully hooked
     */
    public boolean initialize()
    {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.warning("Vault not found. Economy features will be disabled.");
            enabled = false;
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("No economy provider found. Economy features will be disabled.");
            enabled = false;
            return false;
        }

        economy = rsp.getProvider();
        enabled = true;
        logger.info("Vault economy hooked successfully: " + economy.getName());
        return true;
    }

    /**
     * Checks if Vault economy is available and enabled.
     *
     * @return true if economy operations are available
     */
    public boolean isEnabled()
    {
        return enabled && economy != null;
    }

    /**
     * Gets the player's current balance.
     *
     * @param player The player to check
     * @return The player's balance, or 0 if economy is not available
     */
    public double getBalance(Player player)
    {
        if (!isEnabled() || player == null) return 0.0;
        return economy.getBalance(player);
    }

    /**
     * Deposits money into a player's account.
     *
     * @param player The player to deposit to
     * @param amount The amount to deposit
     * @return true if the deposit was successful
     */
    public boolean deposit(Player player, double amount)
    {
        if (!isEnabled() || player == null || amount <= 0) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Withdraws money from a player's account.
     *
     * @param player The player to withdraw from
     * @param amount The amount to withdraw
     * @return true if the withdrawal was successful
     */
    public boolean withdraw(Player player, double amount)
    {
        if (!isEnabled() || player == null || amount <= 0) return false;
        if (!hasBalance(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Checks if a player has at least the specified amount.
     *
     * @param player The player to check
     * @param amount The amount to check for
     * @return true if the player has enough balance
     */
    public boolean hasBalance(Player player, double amount)
    {
        if (!isEnabled() || player == null) return false;
        return economy.has(player, amount);
    }

    /**
     * Formats the amount according to the economy plugin's settings.
     *
     * @param amount The amount to format
     * @return The formatted string, or a basic format if economy is not available
     */
    public String format(double amount)
    {
        if (!isEnabled()) return String.format("$%.2f", amount);
        return economy.format(amount);
    }

    /**
     * Gets the currency name (singular).
     *
     * @return The currency name, or "dollar" if economy is not available
     */
    public String getCurrencyName()
    {
        if (!isEnabled()) return "dollar";
        return economy.currencyNameSingular();
    }

    /**
     * Gets the currency name (plural).
     *
     * @return The currency name plural, or "dollars" if economy is not available
     */
    public String getCurrencyNamePlural()
    {
        if (!isEnabled()) return "dollars";
        return economy.currencyNamePlural();
    }
}
