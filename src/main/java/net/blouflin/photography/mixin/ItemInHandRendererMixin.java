package net.blouflin.photography.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyPhoto;
import net.blouflin.photography.client.PhotographyCaptureTask;
import net.blouflin.photography.client.PhotographyPhotoRenderCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Unique
    private static boolean photography$loggedHeldRendererSource;

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void photography$hideHandsDuringCleanCapture(float partialTick, PoseStack poseStack,
                                                         SubmitNodeCollector submitNodeCollector,
                                                         LocalPlayer player, int packedLight,
                                                         CallbackInfo ci) {
        if (PhotographyCaptureTask.isCapturingCleanFrame()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
    private void photography$renderPhotographInsteadOfMap(PoseStack poseStack,
                                                          SubmitNodeCollector submitNodeCollector,
                                                          int packedLight,
                                                          ItemStack stack,
                                                          CallbackInfo ci) {
        if (!PhotographyPhoto.isPhotographyPhoto(stack)) {
            return;
        }
        if (PhotographyPhotoRenderCache.renderHeldPhotograph(stack, poseStack, submitNodeCollector, packedLight)) {
            if (!photography$loggedHeldRendererSource) {
                photography$loggedHeldRendererSource = true;
                Photography.LOGGER.info("[PhotographyCaptureDebug] heldRendererSource=photographyImage primaryPhotoResolution={}x{}",
                        PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE);
            }
            ci.cancel();
        }
    }
}
