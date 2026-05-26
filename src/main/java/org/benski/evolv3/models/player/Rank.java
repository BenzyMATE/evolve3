package org.benski.evolv3.models.player;

import org.benski.evolv3.core.types.RankType;

/** Stores a player's current rank prefix and rank type (prison or staff). */
public class Rank {
  public String prefix;
  public RankType type;

  /** Creates a new Rank instance. */
  public Rank() {}

  /** Creates a new Rank instance. */
  public Rank(String prefix, RankType type) {
    this.prefix = prefix;
    this.type = type;
  }
}
