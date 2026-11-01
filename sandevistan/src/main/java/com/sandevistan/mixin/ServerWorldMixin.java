package com.sandevistan.mixin;

import com.sandevistan.time.ServerDilation;
import com.sandevistan.time.WorldDilation;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real time manipulation: decides, for every entity, whether its tick runs, is skipped or is velocity-scaled. */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "tickEntity(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void sandevistan$head(Entity entity, CallbackInfo ci) {
        if (ServerDilation.decide((ServerWorld) (Object) this, entity) == WorldDilation.SKIP) {
            entity.resetPosition(); // keep render interpolation still instead of replaying the last step
            ci.cancel();
        }
    }

    @Inject(method = "tickEntity(Lnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void sandevistan$tail(Entity entity, CallbackInfo ci) {
        ServerDilation.afterTick(entity);
    }
}
