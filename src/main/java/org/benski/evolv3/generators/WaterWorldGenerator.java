package org.benski.evolv3.generators;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Chunk generator for the evolv3_islands world. Produces an ocean floor (bedrock + sand) with water
 * up to sea level (Y=62) and air above. No vanilla terrain, caves, decorations, or structures.
 */
public final class WaterWorldGenerator extends ChunkGenerator {
  private static final int SEA_LEVEL = 62;

  /** Generate noise. */
  @Override
  public void generateNoise(
      @NotNull WorldInfo worldInfo,
      @NotNull Random random,
      int chunkX,
      int chunkZ,
      @NotNull ChunkData chunkData) {
    int floor = worldInfo.getMinHeight();
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        chunkData.setBlock(x, floor, z, Material.BEDROCK);
        chunkData.setBlock(x, floor + 1, z, Material.SAND);
        for (int y = floor + 2; y <= SEA_LEVEL; y++) {
          chunkData.setBlock(x, y, z, Material.WATER);
        }
      }
    }
  }

  /** Should generate noise. */
  @Override
  public boolean shouldGenerateNoise() {
    return false;
  }

  /** Should generate surface. */
  @Override
  public boolean shouldGenerateSurface() {
    return false;
  }

  /** Should generate caves. */
  @Override
  public boolean shouldGenerateCaves() {
    return false;
  }

  /** Should generate decorations. */
  @Override
  public boolean shouldGenerateDecorations() {
    return false;
  }

  /** Should generate mobs. */
  @Override
  public boolean shouldGenerateMobs() {
    return false;
  }

  /** Should generate structures. */
  @Override
  public boolean shouldGenerateStructures() {
    return false;
  }
}
