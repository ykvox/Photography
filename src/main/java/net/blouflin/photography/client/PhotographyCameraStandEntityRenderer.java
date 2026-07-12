package net.blouflin.photography.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyCameraStandEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PhotographyCameraStandEntityRenderer extends EntityRenderer<PhotographyCameraStandEntity, PhotographyCameraStandEntityRenderer.RenderState> {
    private final ItemModelResolver itemModelResolver;

    public PhotographyCameraStandEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
        shadowRadius = 0.0f;
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(PhotographyCameraStandEntity entity, RenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        state.xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        state.hasCamera = !entity.getCamera().isEmpty();
        itemModelResolver.updateForNonLiving(state.stand, new ItemStack(Photography.CAMERA_STAND_ITEM), ItemDisplayContext.NONE, entity);
        if (state.hasCamera) {
            itemModelResolver.updateForNonLiving(state.camera, entity.getCamera(), ItemDisplayContext.NONE, entity);
        } else {
            state.camera.clear();
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        super.submit(state, poseStack, collector, cameraState);
        poseStack.pushPose();
        poseStack.translate(0.0, 0.75, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot + 180.0f));
        poseStack.scale(0.85f, 0.85f, 0.85f);
        state.stand.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        if (state.hasCamera) {
            poseStack.pushPose();
            poseStack.translate(0.0, 1.125, 0.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot + 180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-state.xRot));
            poseStack.translate(0.0, 0.25, 0.0);
            poseStack.scale(0.9f, 0.9f, 0.9f);
            state.camera.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    public static class RenderState extends EntityRenderState {
        public final ItemStackRenderState stand = new ItemStackRenderState();
        public final ItemStackRenderState camera = new ItemStackRenderState();
        public float yRot;
        public float xRot;
        public boolean hasCamera;
    }
}
