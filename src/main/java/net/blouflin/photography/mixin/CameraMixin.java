package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyHud;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
    private static final float EXPOSURE_SELFIE_CAMERA_DISTANCE = 1.75f;

    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    protected void setRotation(float yRot, float xRot) {
    }

    @ModifyArg(
            method = "alignWithEntity(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
            index = 0)
    private float photography$clampSelfieCameraDistance(float distance) {
        if (PhotographyHud.isUsingPhotographyCamera && PhotographyHud.isSelfieEnabled()) {
            return Math.min(distance, EXPOSURE_SELFIE_CAMERA_DISTANCE);
        }
        return distance;
    }

    @Inject(method = "alignWithEntity(F)V", at = @At("RETURN"))
    private void photography$applySelfieCameraRotation(float partialTick, CallbackInfo ci) {
        if (PhotographyHud.isUsingPhotographyCamera && PhotographyHud.isSelfieEnabled()) {
            setRotation((float) (yRot + PhotographyHud.selfieCameraYRot()), (float) (xRot + PhotographyHud.selfieCameraXRot()));
        }
    }
}
