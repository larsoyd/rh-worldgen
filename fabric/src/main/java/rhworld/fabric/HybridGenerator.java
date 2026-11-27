package rhworld.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

public final class HybridGenerator extends ChunkGenerator {
    static final Codec<BigInteger> DECIMAL = Codec.STRING.comapFlatMap(HybridGenerator::parseDecimal, BigInteger::toString);

    public static final MapCodec<HybridGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChunkGenerator.CODEC.fieldOf("delegate").forGetter(HybridGenerator::delegate),
            Realm.CODEC.fieldOf("realm").forGetter(HybridGenerator::realm),
            DECIMAL.optionalFieldOf("modulus").forGetter(HybridGenerator::modulus)
    ).apply(i, HybridGenerator::new));

    private final ChunkGenerator delegate;
    private final Realm realm;
    private final Optional<BigInteger> modulus;

    public HybridGenerator(ChunkGenerator delegate, Realm realm, Optional<BigInteger> modulus) {
        super(delegate.getBiomeSource());
        this.delegate = delegate;
        this.realm = realm;
        this.modulus = modulus;
    }

    public ChunkGenerator delegate() { return delegate; }
    public Realm realm() { return realm; }
    public Optional<BigInteger> modulus() { return modulus; }

    public String keyVerdict() {
        return modulus.isEmpty() ? "refused: no modulus" : "ok";
    }

    static DataResult<BigInteger> parseDecimal(String s) {
        if (s.isEmpty() || s.length() > 4096 || !s.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return DataResult.error(() -> "modulus is not a decimal integer");
        }
        return DataResult.success(new BigInteger(s));
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public void spawnOriginalMobs(WorldGenRegion region) { delegate.spawnOriginalMobs(region); }
    @Override public int getGenDepth() { return delegate.getGenDepth(); }
    @Override public int getSeaLevel() { return delegate.getSeaLevel(); }
    @Override public int getMinY() { return delegate.getMinY(); }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState rs, Blender b, StructureManager sm, ChunkAccess c) {
        return delegate.createBiomes(rs, b, sm, c);
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess c, Blender b, RandomState rs, StructureManager sm,
            BiomeManager bm, WorldGenRegion r, Set<Holder<Biome>> biomes) {
        return delegate.buildTerrain(c, b, rs, sm, bm, r, biomes);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor h, RandomState rs) {
        return delegate.getBaseHeight(x, z, type, h, rs);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor h, RandomState rs) {
        return delegate.getBaseColumn(x, z, h, rs);
    }

    @Override
    public void addDebugScreenInfo(List<String> out, RandomState rs, BlockPos pos, SamplerContext ctx) {
        delegate.addDebugScreenInfo(out, rs, pos, ctx);
    }
}
