package rhworld.fabric.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rhworld.fabric.HubStructure;
import rhworld.fabric.HybridGenerator;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    // other structures near the hub would overwrite it in an order that depends on chunk scheduling
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void rhworld$keepHubClear(StructureSet.StructureSelectionEntry entry, net.minecraft.world.level.StructureManager structures,
            net.minecraft.core.RegistryAccess registries, net.minecraft.world.level.levelgen.RandomState random,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates, long seed,
            net.minecraft.world.level.chunk.ChunkAccess chunk, ChunkPos pos, net.minecraft.resources.ResourceKey<?> level,
            net.minecraft.world.level.biome.Climate.Sampler sampler, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof HybridGenerator && !(entry.structure().value() instanceof HubStructure)
                && Math.abs(pos.x()) <= HubStructure.CLEAR_CHUNKS && Math.abs(pos.z()) <= HubStructure.CLEAR_CHUNKS) {
            cir.setReturnValue(false);
        }
    }
}
