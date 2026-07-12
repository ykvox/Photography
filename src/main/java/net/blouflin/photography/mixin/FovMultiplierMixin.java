package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class FovMultiplierMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void injected(CallbackInfoReturnable<Float> cir) {
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson() && PhotographyHud.isUsingPhotographyCamera) {
            cir.setReturnValue((float) PhotographyHud.currentFovMultiplier());
        }
    }
}
