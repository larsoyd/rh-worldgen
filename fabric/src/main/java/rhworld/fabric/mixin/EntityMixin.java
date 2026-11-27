package rhworld.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rhworld.fabric.PlayerData;

@Mixin(Entity.class)
abstract class EntityMixin {
    // every player save writes the live season state first, so autosave, logout and stop all persist it
    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void rhworld$flushLivePlayer(ValueOutput out, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) PlayerData.flush(player);
    }
}
