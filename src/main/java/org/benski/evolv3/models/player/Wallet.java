package org.benski.evolv3.models.player;

/** Stores a player's currency balances: coins and essence. */
public class Wallet {
  public double coins;
  public double essence;

  /** Creates a new Wallet instance. */
  public Wallet() {}

  /** Creates a new Wallet instance. */
  public Wallet(double coins, double essence) {
    this.coins = coins;
    this.essence = essence;
  }
}
