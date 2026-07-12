package net.blouflin.photography.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.blouflin.photography.PhotographyPhoto;
import net.blouflin.photography.client.PhotographyItemFramePhotoRenderState;
import net.blouflin.photography.client.PhotographyPhotoRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin<T extends ItemFrame> extends EntityRenderer<T, ItemFrameRenderState> {
    protected ItemFrameRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
            at = @At("TAIL"))
    private void photography$extractFramedPhoto(T entity, ItemFrameRenderState state, float partialTick, CallbackInfo ci) {
        ItemStack stack = entity.getItem();
        if (PhotographyPhoto.isPhotographyPhoto(stack)) {
            ((PhotographyItemFramePhotoRenderState) state).photography$setFramedPhoto(stack.copy());
            state.mapId = null;
        } else {
            ((PhotographyItemFramePhotoRenderState) state).photography$setFramedPhoto(ItemStack.EMPTY);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;mapId:Lnet/minecraft/world/level/saveddata/maps/MapId;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0),
            cancellable = true)
    private void photography$renderFramedPhoto(ItemFrameRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                               CameraRenderState cameraRenderState, CallbackInfo ci) {
        ItemStack stack = ((PhotographyItemFramePhotoRenderState) state).photography$getFramedPhoto();
        if (!PhotographyPhoto.isPhotographyPhoto(stack)) {
            return;
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.rotation * 360.0f / 8.0f));
        int packedLight = state.isGlowFrame ? 15728880 : state.lightCoords;
        if (PhotographyPhotoRenderCache.renderItemFramePhotograph(stack, poseStack, collector, packedLight,
                state.isGlowFrame, state.rotation)) {
            poseStack.popPose();
            ci.cancel();
        }
    }
}
