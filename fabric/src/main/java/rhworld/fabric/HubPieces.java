package rhworld.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class HubPieces {
    public static final int PAD = 9;
    public static final int HEADROOM = 6;
    public static final int GATE_X = 6;

    private HubPieces() {}

    abstract static class Base extends StructurePiece {
        final int ox, oy, oz;

        Base(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType type, int x, int y, int z, int r) {
            super(type, 0, new BoundingBox(x - r, y - 1, z - r, x + r, y + HEADROOM, z + r));
            ox = x;
            oy = y;
            oz = z;
        }

        Base(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            ox = tag.getIntOr("ox", 0);
            oy = tag.getIntOr("oy", 0);
            oz = tag.getIntOr("oz", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("ox", ox);
            tag.putInt("oy", oy);
            tag.putInt("oz", oz);
        }

        void put(WorldGenLevel level, BoundingBox chunkBox, int x, int y, int z, BlockState state) {
            var pos = new BlockPos(x, y, z);
            if (chunkBox.isInside(pos)) level.setBlock(pos, state, 2);
        }

        void pad(WorldGenLevel level, BoundingBox chunkBox, int r) {
            for (int x = ox - r; x <= ox + r; x++) for (int z = oz - r; z <= oz + r; z++) {
                put(level, chunkBox, x, oy - 1, z, RhBlocks.HUB_STONE.defaultBlockState());
                for (int y = oy; y <= oy + HEADROOM; y++) put(level, chunkBox, x, y, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    public static final class Hub extends Base {
        public Hub(int x, int y, int z) {
            super(RhWorldMod.HUB_PIECE, x, y, z, PAD);
        }

        public Hub(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(RhWorldMod.HUB_PIECE, tag);
        }

        public BlockPos origin() {
            return new BlockPos(ox, oy, oz);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos origin) {
            pad(level, chunkBox, PAD);
            gate(level, chunkBox, ox - GATE_X, RhBlocks.GATE_WARM.defaultBlockState());
            gate(level, chunkBox, ox + GATE_X, RhBlocks.GATE_COLD.defaultBlockState());
            put(level, chunkBox, ox, oy, oz + GATE_X, RhBlocks.SIGIL.defaultBlockState());
        }

        private void gate(WorldGenLevel level, BoundingBox chunkBox, int x, BlockState portal) {
            for (int z = oz - 2; z <= oz + 2; z++) for (int y = oy; y <= oy + 4; y++) {
                boolean frame = z == oz - 2 || z == oz + 2 || y == oy || y == oy + 4;
                put(level, chunkBox, x, y, z, frame ? RhBlocks.HUB_STONE.defaultBlockState() : portal);
            }
        }
    }

    public static final class Shrine extends Base {
        final int index;

        public Shrine(int x, int y, int z, int index) {
            super(RhWorldMod.SHRINE_PIECE, x, y, z, 1);
            this.index = index;
        }

        public Shrine(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(RhWorldMod.SHRINE_PIECE, tag);
            index = tag.getIntOr("prime_index", 0);
        }

        public BlockPos origin() {
            return new BlockPos(ox, oy, oz);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super.addAdditionalSaveData(ctx, tag);
            tag.putInt("prime_index", index);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos origin) {
            pad(level, chunkBox, 1);
            put(level, chunkBox, ox, oy, oz, RhBlocks.SHRINE.defaultBlockState().setValue(ShrineBlock.PRIME_INDEX, index));
        }
    }
}
