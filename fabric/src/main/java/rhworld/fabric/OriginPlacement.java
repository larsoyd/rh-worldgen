package rhworld.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class OriginPlacement implements StructurePlacement {
    public static final OriginPlacement INSTANCE = new OriginPlacement();
    public static final MapCodec<OriginPlacement> CODEC = MapCodec.unit(INSTANCE);

    private OriginPlacement() {}

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return chunkX == 0 && chunkZ == 0;
    }

    @Override
    public MapCodec<? extends StructurePlacement> codec() {
        return CODEC;
    }
}
