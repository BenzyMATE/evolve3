package org.benski.evolv3.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PickaxeDto {
  public String id;
  public String name;
  public String ownerUuid;
  public int level = 1;
  public int xp;
  public int target = 100;
  public int prestige;
  public int evolution = 1;
  public int durability;
  public VesselDto essenceVessel;
  public VesselDto atomVessel;
  public List<EnchantDto> enchants = new ArrayList<>();
}
