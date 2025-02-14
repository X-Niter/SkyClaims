package net.mohron.skyclaims.world;

import com.flowpowered.math.vector.Vector3i;
import net.mohron.skyclaims.SkyClaims;
import net.mohron.skyclaims.SkyClaimsTimings;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.TileEntityCarrier;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.ChunkRegenerateFlags;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.biome.BiomeTypes;

import java.util.Objects;

public class RegenerateChunkTask implements Runnable {

  private static final SkyClaims PLUGIN = SkyClaims.getInstance();

  private final World world;
  private final Vector3i position;
  private final BlockState[] blocks;
  private final Location<World> spawn;

  public RegenerateChunkTask(World world, Vector3i position, BlockState[] blocks, Location<World> spawn) {
    this.world = world;
    this.position = position;
    this.blocks = blocks;
    this.spawn = spawn;
  }


  @Override
  public void run() {
    SkyClaimsTimings.CLEAR_ISLAND.startTimingIfSync();

    final Chunk chunk = world.loadChunk(position, true).orElse(null);

    if (chunk == null) {
      PLUGIN.getLogger().error("Failed to load chunk {}", position.toString());
      return;
    }

    PLUGIN.getLogger().debug("Began regenerating chunk {}", position.toString());
    // Teleport any players to world spawn
    chunk.getEntities(e -> e instanceof Player).forEach(e -> e.setLocationSafely(spawn));
    // Clear all tile entities that have an inventory, this helps remove item lag if the blocks have a bunch of items.
    chunk.getTileEntities(e -> e instanceof TileEntityCarrier).forEach(e -> ((TileEntityCarrier) e).getInventory().clear());
    // Set the blocks
    for (int by = chunk.getBlockMin().getY(); by <= chunk.getBlockMax().getY(); by++) {
      for (int bx = chunk.getBlockMin().getX(); bx <= chunk.getBlockMax().getX(); bx++) {
        for (int bz = chunk.getBlockMin().getZ(); bz <= chunk.getBlockMax().getZ(); bz++) {
          world.setBiome(position, BiomeTypes.FOREST);
          world.getLocation(bx, by, bz).setBlockType(BlockTypes.AIR);
          world.regenerateChunk(bx, 72, bz, ChunkRegenerateFlags.ALL);
        }
      }
    }
    // Remove any remaining entities to include dropped items or somehow an entity the spawned last minute.
    chunk.getEntities(e -> !(e instanceof Player)).forEach(Entity::remove);
    chunk.unloadChunk();
    PLUGIN.getLogger().debug("Finished regenerating chunk {}", position.toString());

    SkyClaimsTimings.CLEAR_ISLAND.stopTimingIfSync();
  }
}
