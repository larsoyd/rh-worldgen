package rhworld.fabric;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class HubStructure extends Structure {
    public static final MapCodec<HubStructure> CODEC = simpleCodec(HubStructure::new);
    public static final int SHRINE_RADIUS = 48;

    public HubStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        if (!(ctx.chunkGenerator() instanceof HybridGenerator) || ctx.chunkPos().x() != 0 || ctx.chunkPos().z() != 0) {
            return Optional.empty();
        }
        int y = surface(ctx, 0, 0);
        return Optional.of(new GenerationStub(new BlockPos(0, y, 0), pieces -> {
            pieces.addPiece(new HubPieces.Hub(0, y, 0));
            for (int i = 0; i < ShrineBlock.PRIMES.length; i++) {
                double angle = 2 * Math.PI * i / ShrineBlock.PRIMES.length;
                int x = (int) Math.round(SHRINE_RADIUS * Math.cos(angle));
                int z = (int) Math.round(SHRINE_RADIUS * Math.sin(angle));
                pieces.addPiece(new HubPieces.Shrine(x, surface(ctx, x, z), z, i));
            }
        }));
    }

    private static int surface(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());
    }

    @Override
    public StructureType<?> type() {
        return RhWorldMod.HUB_TYPE;
    }
}
