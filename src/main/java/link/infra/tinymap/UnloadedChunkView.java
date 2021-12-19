package link.infra.tinymap;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TickScheduler;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.feature.StructureFeature;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Skeleton implementation of Chunk to represent an unloaded chunk view
 */
class UnloadedChunkView implements Chunk {
	private final ChunkSection[] sections;

	UnloadedChunkView(ChunkSection[] sections) {
		this.sections = sections;
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		if (y >= 0 && y >> 4 < this.sections.length) {
			ChunkSection chunkSection = this.sections[y >> 4];
			if (!ChunkSection.isEmpty(chunkSection)) {
				return chunkSection.getBlockState(x & 15, y & 15, z & 15);
			}
		}

		return Blocks.AIR.getDefaultState();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		if (y >= 0 && y >> 4 < this.sections.length) {
			ChunkSection chunkSection = this.sections[y >> 4];
			if (!ChunkSection.isEmpty(chunkSection)) {
				return chunkSection.getFluidState(x & 15, y & 15, z & 15);
			}
		}

		return Fluids.EMPTY.getDefaultState();
	}

	@Override
	public @Nullable BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
		return null;
	}

	@Override
	public void setBlockEntity(BlockPos pos, BlockEntity blockEntity) {}

	@Override
	public void addEntity(Entity entity) {}

	@Override
	public Set<BlockPos> getBlockEntityPositions() {
		return null;
	}

	@Override
	public ChunkSection[] getSectionArray() {
		return sections;
	}

	private final Heightmap worldSurfaceHeightmap = new Heightmap(this, Heightmap.Type.WORLD_SURFACE);

	@Override
	public Collection<Map.Entry<Heightmap.Type, Heightmap>> getHeightmaps() {
		return Collections.singletonList(new Map.Entry<Heightmap.Type, Heightmap>() {
			@Override
			public Heightmap.Type getKey() {
				return Heightmap.Type.WORLD_SURFACE;
			}

			@Override
			public Heightmap getValue() {
				return worldSurfaceHeightmap;
			}

			@Override
			public Heightmap setValue(Heightmap value) {
				return worldSurfaceHeightmap;
			}
		});
	}

	@Override
	public void setHeightmap(Heightmap.Type type, long[] heightmap) {
		worldSurfaceHeightmap.setTo(heightmap);
	}

	@Override
	public Heightmap getHeightmap(Heightmap.Type type) {
		return worldSurfaceHeightmap;
	}

	@Override
	public int sampleHeightmap(Heightmap.Type type, int x, int z) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ChunkPos getPos() {
		return null;
	}

	@Override
	public void setLastSaveTime(long lastSaveTime) {

	}

	@Override
	public Map<StructureFeature<?>, StructureStart<?>> getStructureStarts() {
		return null;
	}

	@Override
	public void setStructureStarts(Map<StructureFeature<?>, StructureStart<?>> structureStarts) {

	}

	@Override
	public @Nullable BiomeArray getBiomeArray() {
		return null;
	}

	@Override
	public void setShouldSave(boolean shouldSave) {

	}

	@Override
	public boolean needsSaving() {
		return false;
	}

	@Override
	public ChunkStatus getStatus() {
		return null;
	}

	@Override
	public void removeBlockEntity(BlockPos pos) {

	}

	@Override
	public ShortList[] getPostProcessingLists() {
		return new ShortList[0];
	}

	@Override
	public @Nullable NbtCompound getBlockEntityNbt(BlockPos pos) {
		return null;
	}

	@Override
	public @Nullable NbtCompound getPackedBlockEntityNbt(BlockPos pos) {
		return null;
	}

	@Override
	public Stream<BlockPos> getLightSourcesStream() {
		return null;
	}

	@Override
	public TickScheduler<Block> getBlockTickScheduler() {
		return null;
	}

	@Override
	public TickScheduler<Fluid> getFluidTickScheduler() {
		return null;
	}

	@Override
	public UpgradeData getUpgradeData() {
		return null;
	}

	@Override
	public void setInhabitedTime(long inhabitedTime) {

	}

	@Override
	public long getInhabitedTime() {
		return 0;
	}

	@Override
	public boolean isLightOn() {
		return false;
	}

	@Override
	public void setLightOn(boolean lightOn) {

	}

	@Override
	public @Nullable StructureStart<?> getStructureStart(StructureFeature<?> structure) {
		return null;
	}

	@Override
	public void setStructureStart(StructureFeature<?> structure, StructureStart<?> start) {

	}

	@Override
	public LongSet getStructureReferences(StructureFeature<?> structure) {
		return null;
	}

	@Override
	public void addStructureReference(StructureFeature<?> structure, long reference) {

	}

	@Override
	public Map<StructureFeature<?>, LongSet> getStructureReferences() {
		return null;
	}

	@Override
	public void setStructureReferences(Map<StructureFeature<?>, LongSet> structureReferences) {

	}
}
