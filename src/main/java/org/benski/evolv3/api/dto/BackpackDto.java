package org.benski.evolv3.api.dto;

import java.util.ArrayList;
import java.util.List;

public final class BackpackDto {
    public int size;
    public int total;
    public boolean full;
    public List<BackpackItemDto> items = new ArrayList<>();
}
