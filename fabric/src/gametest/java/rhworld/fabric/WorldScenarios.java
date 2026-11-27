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
            var pieces = start.getPieces().stream().map(net.minecraft.world.level.levelgen.structure.StructurePiece::getBoundingBox).toList();
            var placed = new java.util.TreeMap<Integer, Integer>();
            int wrong = 0;
            for (var piece : start.getPieces()) {
                if (!(piece instanceof HubPieces.Shrine sh)) continue;
                var state = level.getBlockState(sh.origin());
                int got = state.is(RhBlocks.SHRINE) ? state.getValue(ShrineBlock.PRIME_INDEX) : -1;
                placed.merge(sh.index(), 1, Integer::sum);
                if (got != sh.index()) {
                    wrong++;
                    if (wrong <= 3) System.out.println("[rhworld-test] shrine_mismatch level=" + id + " piece=" + sh.index()
                            + " at=" + sh.origin().toShortString() + " block_index=" + got);
                }
            }
            boolean eachOnce = placed.size() == 25 && placed.values().stream().allMatch(c -> c == 1)
                    && placed.firstKey() == 0 && placed.lastKey() == 24;
            ctx.report().check(wrong == 0 && eachOnce, "shrine_piece_holds_its_prime", "level=" + id + " wrong=" + wrong + " pieces=" + placed.size());
            int foreign = foreignStarts(level, HubStructure.CLEAR_CHUNKS);
            ctx.report().check(foreign == 0, "no_vanilla_structures_near_hub", "level=" + id + " radius=" + HubStructure.CLEAR_CHUNKS + " found=" + foreign);
            int stray = strayBlocks(level, pieces, 8);
            ctx.report().check(stray == 0, "nothing_outside_hub", "level=" + id + " chunks=17x17 found=" + stray);
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

    static int strayBlocks(ServerLevel level, java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes, int radius) {
        int stray = 0;
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int cx = -radius; cx <= radius; cx++) for (int cz = -radius; cz <= radius; cz++) {
            level.getChunk(cx, cz);
            for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                    pos.set(x, y, z);
                    if (boxes.stream().anyMatch(b -> b.isInside(pos))) continue;
                    var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                    if (key.getNamespace().equals(RhWorldMod.ID)) stray++;
                }
            }
        }
        return stray;
    }

    static int foreignStarts(ServerLevel level, int radius) {
        int found = 0;
        for (int cx = -radius; cx <= radius; cx++) for (int cz = -radius; cz <= radius; cz++) {
            var chunk = level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS, true);
            for (var e : chunk.getAllStarts().entrySet()) {
                if (!e.getValue().isValid() || e.getKey() instanceof HubStructure) continue;
                found++;
                System.out.println("[rhworld-test] foreign_start level=" + level.dimension().identifier() + " cx=" + cx + " cz=" + cz
                        + " structure=" + level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey()));
            }
        }
        return found;
    }
}
