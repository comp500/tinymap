package link.infra.tinymap.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;

@Mixin(ThreadedAnvilChunkStorage.class)
public interface ThreadedAnvilChunkStorageMixin {
    @Invoker
    NbtCompound callGetUpdatedChunkNbt (ChunkPos pos) throws IOException;
}
