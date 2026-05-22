package org.benski.evolv3.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PlayerProfileDto {
  public String id;
  public String username;
  public String skin;
  public int activity = 0;
  public WalletDto wallet;
  public List<RankDto> ranks = new ArrayList<>();
  public BackpackDto backpack;
  public List<PickaxeDto> pickaxes = new ArrayList<>();
  public List<PreferenceDto> preferences = new ArrayList<>();
}
