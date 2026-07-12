package net.blouflin.photography.mixin;

import net.blouflin.photography.client.PhotographyCameraRenderState;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;", at = @At(value = "HEAD"), cancellable = true)
    private static void injected(Avatar player, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (((PlayerIsUsingCamera) player).isUsingPhotographyCamera() && Objects.equals(hand.toString(), ((PlayerIsUsingCamera) player).handUsingPhotographyCamera())) {
            cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void photography$extractCameraRenderState(Avatar player, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        PlayerIsUsingCamera cameraState = (PlayerIsUsingCamera) player;
        ((PhotographyCameraRenderState) state).photography$setCameraState(
                cameraState.isUsingPhotographyCamera(),
                cameraState.handUsingPhotographyCamera(),
                cameraState.isUsingPhotographySelfie());
    }
}
