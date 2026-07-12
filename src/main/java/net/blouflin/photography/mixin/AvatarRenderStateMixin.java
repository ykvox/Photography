package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyCameraRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements PhotographyCameraRenderState {
    @Unique
    private boolean photography$usingCamera;
    @Unique
    private String photography$cameraHand = "MAIN_HAND";
    @Unique
    private boolean photography$selfie;

    @Override
    public boolean photography$isUsingCamera() {
        return photography$usingCamera;
    }

    @Override
    public String photography$cameraHand() {
        return photography$cameraHand;
    }

    @Override
    public boolean photography$isSelfie() {
        return photography$selfie;
    }

    @Override
    public void photography$setCameraState(boolean usingCamera, String hand, boolean selfie) {
        photography$usingCamera = usingCamera;
        photography$cameraHand = hand == null ? "MAIN_HAND" : hand;
        photography$selfie = selfie;
    }
}
