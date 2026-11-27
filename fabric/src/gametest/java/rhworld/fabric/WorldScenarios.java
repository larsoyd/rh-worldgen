package rhworld.fabric;

import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

final class WorldScenarios {
    static final Set<String> REALM_LEVELS = Set.of("rhworld:ember", "rhworld:mist", "rhworld:frost");

    private WorldScenarios() {}

    static void realms(WorldTestHook.Ctx ctx) {
        for (String id : REALM_LEVELS) {
            ServerLevel level = ctx.server().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(id)));
            ctx.report().check(level != null, "realm_loaded", "level=" + id);
            if (level != null) {
                var gen = level.getChunkSource().getGenerator();
                ctx.report().check(gen instanceof HybridGenerator h && ("rhworld:" + h.realm().getSerializedName()).equals(id),
                        "realm_generator", "level=" + id + " generator=" + gen.getClass().getSimpleName());
            }
        }
    }

    static void vanilla(WorldTestHook.Ctx ctx) {
        for (ServerLevel level : ctx.server().getAllLevels()) {
            String id = level.dimension().identifier().toString();
            ctx.report().check(!id.startsWith("rhworld:") && !(level.getChunkSource().getGenerator() instanceof HybridGenerator),
                    "no_rhworld_level", "level=" + id);
        }
    }

    static void terrain(WorldTestHook.Ctx ctx) {
        int levels = 0;
        for (ServerLevel level : ctx.server().getAllLevels()) {
            if (!(level.getChunkSource().getGenerator() instanceof HybridGenerator gen)) continue;
            if (!(gen.delegate() instanceof NoiseBasedChunkGenerator noise)) continue;
            levels++;
            RandomState ref = RandomState.create(ctx.server().registryAccess().lookupOrThrow(Registries.NOISE),
                    level.getSeed(), noise.generatorSettings().value());
            RandomState live = level.getChunkSource().randomState();
            var refBiomes = gen.getBiomeSource().createUncachedResolver(ref);
            var liveBiomes = gen.getBiomeSource().createUncachedResolver(live);
            int heightDiff = 0, biomeDiff = 0, samples = 0;
            for (int x = -4096; x <= 4096; x += 512) {
                for (int z = -4096; z <= 4096; z += 512) {
                    samples++;
                    int want = noise.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, ref);
                    int got = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, live);
                    if (want != got) heightDiff++;
                    var wantBiome = refBiomes.getNoiseBiome(x >> 2, 16, z >> 2);
                    var gotBiome = liveBiomes.getNoiseBiome(x >> 2, 16, z >> 2);
                    if (!wantBiome.equals(gotBiome)) biomeDiff++;
                }
            }
            String id = level.dimension().identifier().toString();
            ctx.report().check(heightDiff == 0, "terrain_matches_delegate", "level=" + id + " samples=" + samples + " differ=" + heightDiff);
            ctx.report().check(biomeDiff == 0, "biomes_match_delegate", "level=" + id + " samples=" + samples + " differ=" + biomeDiff);
        }
        ctx.report().check(levels == 4, "hybrid_levels", "count=" + levels);
    }

    static final int[] PRIMES_TO_97 = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97};

    static void hubs(WorldTestHook.Ctx ctx) {
        var structures = ctx.server().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var hub = structures.getValue(RhWorldMod.id("hub"));
        ctx.report().check(hub != null, "hub_registered", "id=rhworld:hub");
        if (hub == null) return;
        int levels = 0;
        for (ServerLevel level : ctx.server().getAllLevels()) {
            String id = level.dimension().identifier().toString();
            boolean hybrid = level.getChunkSource().getGenerator() instanceof HybridGenerator;
            if (hybrid) levels++;
            var start = level.getChunk(0, 0).getStartForStructure(hub);
            boolean present = start != null && start.isValid();
            ctx.report().check(present == hybrid, "hub_start", "level=" + id + " hybrid=" + hybrid + " present=" + present);
            if (!present) {
                ctx.report().check(countOurs(level, -48, 48, -48, 48).total == 0, "no_rhworld_blocks", "level=" + id);
                continue;
            }
            var box = start.getBoundingBox();
            var inside = countOurs(level, box.minX(), box.maxX(), box.minZ(), box.maxZ());
            ctx.report().check(inside.warm == 9 && inside.cold == 9 && inside.sigil == 1, "hub_blocks",
                    "level=" + id + " warm=" + inside.warm + " cold=" + inside.cold + " sigil=" + inside.sigil);
            var want = java.util.Arrays.stream(PRIMES_TO_97).boxed().collect(java.util.stream.Collectors.toSet());
            ctx.report().check(inside.shrinePrimes.equals(want) && inside.shrines == 25, "hub_shrines",
                    "level=" + id + " shrines=" + inside.shrines + " distinct=" + inside.shrinePrimes.size());
            var outside = countOurs(level, box.maxX() + 1, box.maxX() + 48, box.minZ(), box.maxZ());
            ctx.report().check(outside.total == 0, "nothing_outside_hub", "level=" + id + " found=" + outside.total);
        }
        ctx.report().check(levels == 4 || levels == 0, "hub_levels", "count=" + levels);
    }

    static final class Count {
        int total, warm, cold, sigil, shrines;
        final java.util.Set<Integer> shrinePrimes = new java.util.TreeSet<>();
    }

    static Count countOurs(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        var c = new Count();
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                var state = level.getBlockState(pos.set(x, y, z));
                var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (!key.getNamespace().equals(RhWorldMod.ID)) continue;
                c.total++;
                switch (key.getPath()) {
                    case "gate_warm" -> c.warm++;
                    case "gate_cold" -> c.cold++;
                    case "sigil" -> c.sigil++;
                    case "shrine" -> {
                        c.shrines++;
                        c.shrinePrimes.add(state.getValue(ShrineBlock.PRIME_INDEX) < PRIMES_TO_97.length
                                ? PRIMES_TO_97[state.getValue(ShrineBlock.PRIME_INDEX)] : -1);
                    }
                    default -> { }
                }
            }
        }
        return c;
    }
}
