package org.benski.evolv3.models.player;

import org.benski.evolv3.core.types.RankType;

public class Rank {
  public String prefix;
  public RankType type;

  public Rank() {}

  public Rank(String prefix, RankType type) {
    this.prefix = prefix;
    this.type = type;
  }
}
