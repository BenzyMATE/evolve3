package org.benski.evolv3.api.dto;

import java.util.ArrayList;
import java.util.List;

public final class EnchantDto {
    public String name;
    public int level;
    public boolean enabled = true;
    public List<String> stones = new ArrayList<>();
}
