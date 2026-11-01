package com.sandevistan.mixin;

import com.sandevistan.client.ClientDilation;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While time is dilated every sound plays lower and slower, as if the world were far away. */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(method = "getAdjustedPitch", at = @At("RETURN"), cancellable = true)
    private void sandevistan$pitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float m = ClientDilation.soundFactor();
        if (m < 0.999f) cir.setReturnValue(cir.getReturnValue() * m);
    }
}
