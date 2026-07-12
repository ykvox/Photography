package net.blouflin.photography.mixin;

import net.blouflin.photography.PhotographyCamera;
import net.blouflin.photography.client.PhotographyHud;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Shadow
    public abstract void updateForTopItem(ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed);

    @Inject(method = "updateForLiving", at = @At("HEAD"), cancellable = true)
    private void photography$resolveCameraModelForRenderedPlayer(ItemStackRenderState state, ItemStack stack,
                                                                 ItemDisplayContext displayContext, LivingEntity entity,
                                                                 CallbackInfo ci) {
        if (!PhotographyCamera.isPhotographyCamera(stack)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean usingCamera = false;
        boolean selfie = false;
        if (entity instanceof Player player && entity instanceof PlayerIsUsingCamera cameraState) {
            usingCamera = cameraState.isUsingPhotographyCamera() && isCameraHand(player, stack, cameraState.handUsingPhotographyCamera());
            selfie = usingCamera && cameraState.isUsingPhotographySelfie();
        }
        boolean localSelfieView = PhotographyHud.isUsingPhotographyCamera
                && PhotographyHud.isSelfieEnabled()
                && entity == minecraft.getCameraEntity();
        Identifier model = PhotographyCamera.modelForState(usingCamera || localSelfieView, selfie || localSelfieView, localSelfieView);
        Identifier currentModel = stack.get(DataComponents.ITEM_MODEL);
        if (!usingCamera && !localSelfieView && model.equals(currentModel)) {
            return;
        }

        ItemStack renderStack = stack.copy();
        renderStack.set(DataComponents.ITEM_MODEL, model);
        if (PhotographyHud.isDebugViewfinderEnabled()) {
            net.blouflin.photography.Photography.LOGGER.info(
                    "[camera-model-resolve] player={} local={} state={} requestedModel={} resolved={}",
                    entity.getName().getString(),
                    entity == minecraft.getCameraEntity(),
                    !usingCamera && !localSelfieView ? "closed" : (selfie || localSelfieView) ? "selfie" : "open",
                    model,
                    model);
        }
        updateForTopItem(state, renderStack, displayContext, entity.level(), entity, entity.getId() + displayContext.ordinal());
        ci.cancel();
    }

    private boolean isCameraHand(Player player, ItemStack stack, String handName) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        try {
            hand = InteractionHand.valueOf(handName);
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }
        return player.getItemInHand(hand) == stack || PhotographyCamera.isPhotographyCamera(player.getItemInHand(hand));
    }
}
