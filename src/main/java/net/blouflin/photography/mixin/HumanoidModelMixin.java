package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyCameraRenderState;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void photography$applyExposureCameraPose(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof PhotographyCameraRenderState cameraState) || !cameraState.photography$isUsingCamera()) {
            return;
        }

        HumanoidArm arm = cameraArm(state, cameraState.photography$cameraHand());
        if (cameraState.photography$isSelfie()) {
            applyExposureSelfiePose(arm);
        } else {
            applyExposureHoldingPose(arm);
        }
    }

    private HumanoidArm cameraArm(HumanoidRenderState state, String handName) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        try {
            hand = InteractionHand.valueOf(handName);
        } catch (IllegalArgumentException ignored) {
        }
        return hand == InteractionHand.OFF_HAND ? state.mainArm.getOpposite() : state.mainArm;
    }

    private void applyExposureHoldingPose(HumanoidArm arm) {
        boolean rightHanded = arm == HumanoidArm.RIGHT;
        ModelPart mainHand = rightHanded ? rightArm : leftArm;
        ModelPart offHand = rightHanded ? leftArm : rightArm;

        head.xRot += 0.4f;
        head.xRot = Mth.clamp(head.xRot, -1.0f, 1.15f);

        mainHand.yRot = (rightHanded ? -0.3f : 0.3f) + head.yRot;
        offHand.yRot = (rightHanded ? 0.5f : -0.5f) + head.yRot;
        mainHand.xRot = head.xRot - 1.5f;
        offHand.xRot = head.xRot - 1.5f;
        head.xRot += 0.3f;
        hat.xRot = head.xRot;
        hat.yRot = head.yRot;
        hat.zRot = head.zRot;
    }

    private void applyExposureSelfiePose(HumanoidArm arm) {
        ModelPart cameraArm = arm == HumanoidArm.RIGHT ? rightArm : leftArm;
        cameraArm.xRot = (head.xRot + Math.abs(head.xRot * 0.13f)) - ((float) Math.PI / 2.0f);
        cameraArm.yRot = head.yRot + (arm == HumanoidArm.RIGHT ? -0.25f : 0.25f);
        if (head.xRot <= 0.0f) {
            cameraArm.zRot = (head.xRot * 0.15f) * (arm == HumanoidArm.RIGHT ? -1.0f : 1.0f);
        } else {
            cameraArm.zRot = (head.xRot * 0.22f) * (arm == HumanoidArm.RIGHT ? -1.0f : 1.0f);
        }
    }
}
