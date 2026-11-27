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
}
