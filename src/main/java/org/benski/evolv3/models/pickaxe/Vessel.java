package org.benski.evolv3.models.pickaxe;

import org.benski.evolv3.core.types.VesselType;

/** Tracks progress toward a threshold (essence or atoms) and accumulated crystals. */
public class Vessel {

  public VesselType type;
  public int current;
  public int target;
  public int crystals;

  /** Creates a new Vessel instance. */
  public Vessel() {}

  /** Creates a new Vessel instance. */
  public Vessel(VesselType type, int current, int target, int crystals) {
    this.type = type;
    this.current = current;
    this.target = target;
    this.crystals = crystals;
  }
}
