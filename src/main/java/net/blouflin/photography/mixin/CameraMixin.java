package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyHud;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public class CameraMixin {
    @ModifyArg(
            method = "alignWithEntity(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
            index = 0)
    private float photography$extendSelfieCamera(float distance) {
        if (PhotographyHud.isUsingPhotographyCamera && PhotographyHud.isSelfieEnabled()) {
            return Math.max(distance, 7.0f);
        }
        return distance;
    }
}
