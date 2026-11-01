package com.sandevistan.mixin;

import com.sandevistan.client.ClientDilation;
import com.sandevistan.time.WorldDilation;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client mirrors the server's schedule so predicted entities (arrows, items...) slow down too. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "tickEntity(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void sandevistan$head(Entity entity, CallbackInfo ci) {
        if (ClientDilation.decide(entity) == WorldDilation.SKIP) {
            entity.resetPosition();
            if (entity instanceof LivingEntity le) le.limbAnimator.updateLimbs(0f, 1f); // no leg shimmer while frozen
            ci.cancel();
        }
    }

    @Inject(method = "tickEntity(Lnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void sandevistan$tail(Entity entity, CallbackInfo ci) {
        ClientDilation.afterTick(entity);
    }
}
