package rhworld.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rhworld.fabric.HybridGenerator;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    // vanilla only builds real noise for its own generator class, so hand it the delegate's settings
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JZLnet/minecraft/world/level/block/state/BlockState;ILnet/minecraft/world/level/levelgen/NoiseRouter;)Lnet/minecraft/world/level/levelgen/RandomState;"))
    private static RandomState rhworld$delegateNoise(HolderGetter<NormalNoise> noises, long seed, boolean legacy,
            BlockState block, int sea, NoiseRouter router, Operation<RandomState> original,
            @Local(argsOnly = true) ChunkGenerator generator) {
        if (generator instanceof HybridGenerator hybrid && hybrid.delegate() instanceof NoiseBasedChunkGenerator noise) {
            return RandomState.create(noises, seed, noise.generatorSettings().value());
        }
        return original.call(noises, seed, legacy, block, sea, router);
    }
}
