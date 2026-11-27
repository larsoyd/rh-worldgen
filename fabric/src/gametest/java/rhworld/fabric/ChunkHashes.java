package rhworld.fabric;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class ChunkHashes {
    static final int RADIUS = 6;

    private ChunkHashes() {}

    static void dump(WorldTestHook.Ctx ctx) {
        long seed = Long.getLong("rhworld.order", 0);
        int threads = Integer.getInteger("max.bg.threads", -1);
        for (ServerLevel level : ctx.server().getAllLevels()) {
            if (!(level.getChunkSource().getGenerator() instanceof HybridGenerator)) continue;
            var order = new ArrayList<ChunkPos>();
            for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) order.add(new ChunkPos(x, z));
            Collections.shuffle(order, new Random(seed));
            for (ChunkPos pos : order) level.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, true);
            int withBlocks = 0;
            for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
                var lines = ours(level, x, z);
                if (lines.isEmpty()) continue;
                withBlocks++;
                System.out.printf("[rhworld-chunk] level=%s cx=%d cz=%d blocks=%d sha256=%s%n",
                        level.dimension().identifier(), x, z, lines.size(), sha256(String.join("", lines)));
            }
            ctx.report().check(withBlocks > 0, "chunks_with_rhworld_blocks",
                    "level=" + level.dimension().identifier() + " chunks=" + withBlocks + " order_seed=" + seed + " threads=" + threads);
        }
    }

    static List<String> ours(ServerLevel level, int cx, int cz) {
        var lines = new ArrayList<String>();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (key.getNamespace().equals(RhWorldMod.ID)) lines.add(x + " " + y + " " + z + " " + describe(state) + "\n");
            }
        }
        Collections.sort(lines);
        return lines;
    }

    static String describe(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (state.getProperties().isEmpty()) return id;
        return id + state.getValues()
                .map(v -> v.property().getName() + "=" + v.valueName())
                .sorted().collect(Collectors.joining(",", "[", "]"));
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
