package link.infra.tinymap;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import link.infra.tinymap.mixin.MinecraftServerAccessor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Bad name
 * Basically an abstraction for getting loaded/unloaded chunks
 * haha
 */
class BlockDigger {
	private final LongSet validRegions = new LongOpenHashSet();
	private final File regionFolder;
	private final ServerWorld world;
	private final ThreadedAnvilChunkStorage tacs;

	public BlockDigger(MinecraftServer server, ServerWorld world) {
		regionFolder = new File(((MinecraftServerAccessor) server).getSession().getWorldDirectory(world.getRegistryKey()), "region");
		this.world = world;
		this.tacs = world.getChunkManager().threadedAnvilChunkStorage;
	}

	// Thread-local session of BlockDigger
	public class Session {
		// Saved in testTileExists - as this data will be read again when rendering the chunk, might as well only read it once
		private final Long2ObjectMap<NbtCompound> unloadedChunkCachedData = new Long2ObjectOpenHashMap<>();

		public boolean testTileExists(int tileX, int tileZ, int zoomShift) {
			int regionSize = TileGenerator.rightShiftButReversible(1, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift);
			if (regionSize < 1) {
				regionSize = 1;
			}

			int regionOriginX = TileGenerator.rightShiftButReversible(tileX, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift);
			int regionOriginZ = TileGenerator.rightShiftButReversible(tileZ, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift);
			boolean regionFound = false;
			synchronized (validRegions) {
				outer:
				for (int regionOffX = 0; regionOffX < regionSize; regionOffX++) {
					for (int regionOffZ = 0; regionOffZ < regionSize; regionOffZ++) {
						// Not really a chunk pos, actually a region pos... :)
						long pos = ChunkPos.toLong(regionOriginX + regionOffX, regionOriginZ + regionOffZ);
						if (validRegions.contains(pos)) {
							regionFound = true;
							break outer;
						}
						if (new File(regionFolder, "r." + (regionOriginX + regionOffX) + "." + (regionOriginZ + regionOffZ) + ".mca").exists()) {
							regionFound = true;
							validRegions.add(pos);
							break outer;
						}
					}
				}
			}
			if (!regionFound) {
				return false;
			}

			// If there is exactly one or more regions in this tile, checking for the regions' existence is enough
			// If there are more than one tiles in this region, we need to check the chunks
			if (TileGenerator.rightShiftButReversible(1, TileGenerator.TILE_TO_REGION_SHIFT - zoomShift) >= 1) {
				return true;
			}

			int chunkSize = TileGenerator.rightShiftButReversible(1, TileGenerator.TILE_TO_CHUNK_SHIFT - zoomShift);
			int chunkOriginX = TileGenerator.rightShiftButReversible(tileX, TileGenerator.TILE_TO_CHUNK_SHIFT - zoomShift);
			int chunkOriginZ = TileGenerator.rightShiftButReversible(tileZ, TileGenerator.TILE_TO_CHUNK_SHIFT - zoomShift);

			for (int chunkOffX = 0; chunkOffX < chunkSize; chunkOffX++) {
				for (int chunkOffZ = 0; chunkOffZ < chunkSize; chunkOffZ++) {
					if (world.isChunkLoaded(chunkOriginX + chunkOffX, chunkOriginZ + chunkOffZ)) {
						return true;
					}

					// First check if the region is valid
					synchronized (validRegions) {
						if (validRegions.contains(ChunkPos.toLong((chunkOriginX + chunkOffX) >> 5, (chunkOriginZ + chunkOffZ) >> 5))) {
							return true;
						}
					}

					// Attempt to get it's NBT
					try {
						ChunkPos pos = new ChunkPos(chunkOriginX + chunkOffX, chunkOriginZ + chunkOffZ);
						NbtCompound chunkTag = tacs.getNbt(pos);
						if (chunkTag != null) {
							unloadedChunkCachedData.put(pos.toLong(), chunkTag);
							return true;
						}
					} catch (IOException e) {
						// TODO: better logging
						e.printStackTrace();
						return false;
					}
				}
			}

			return false;
		}

		public Chunk getChunkView(int x, int z) {
			if (world.isChunkLoaded(x, z)) {
				return world.getChunk(x, z);
			} else {
				ChunkPos pos = new ChunkPos(x, z);
				NbtCompound chunkData = unloadedChunkCachedData.remove(pos.toLong());
				if (chunkData == null) {
					try {
						// TODO: cache??
						chunkData = tacs.getNbt(pos);
					} catch (IOException e) {
						// TODO: better logging
						e.printStackTrace();
						return null;
					}
					if (chunkData == null) {
						return null;
					}
				}

				NbtCompound level = chunkData.getCompound("Level");
				ChunkStatus status = ChunkStatus.byId(level.getString("Status"));
				if (!status.isAtLeast(ChunkStatus.FULL)) {
					return null;
				}
				NbtList sectionList = level.getList("Sections", 10);
				ChunkSection[] sections = new ChunkSection[16];

				for (int i = 0; i < sectionList.size(); ++i) {
					NbtCompound sectionTag = sectionList.getCompound(i);
					int y = sectionTag.getByte("Y");
					if (sectionTag.contains("Palette", 9) && sectionTag.contains("BlockStates", 12)) {
						ChunkSection section = new ChunkSection(y << 4);
						section.getBlockStateContainer().read(sectionTag.getList("Palette", 10), sectionTag.getLongArray("BlockStates"));
						section.calculateCounts();
						if (!section.isEmpty()) {
							sections[y] = section;
						}
					}
				}

				Chunk unloadedChunkView = new UnloadedChunkView(sections);

				NbtCompound heightmaps = level.getCompound("Heightmaps");
				String heightmapName = Heightmap.Type.WORLD_SURFACE.getName();
				if (heightmaps.contains(heightmapName, 12)) {
					unloadedChunkView.setHeightmap(Heightmap.Type.WORLD_SURFACE, heightmaps.getLongArray(heightmapName));
				} else {
					Heightmap.populateHeightmaps(unloadedChunkView, Collections.singleton(Heightmap.Type.WORLD_SURFACE));
				}

				return unloadedChunkView;
			}
		}
	}
}
