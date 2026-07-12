package net.blouflin.photography;

import net.blouflin.photography.client.PhotographyHud;
import net.blouflin.photography.client.PhotographyAlbumScreen;
import net.blouflin.photography.client.PhotographyCameraStandEntityRenderer;
import net.blouflin.photography.client.PhotographyPhotoClientTooltip;
import net.blouflin.photography.client.PhotographyPhotoTooltip;
import net.blouflin.photography.client.PhotographyCaptureTask;
import net.blouflin.photography.client.PhotographyCaptureDebug;
import net.blouflin.photography.client.PhotographyFlashDynamicLight;
import net.blouflin.photography.networking.CameraFlashStatePayload;
import net.blouflin.photography.networking.CreatePicturePayload;
import net.blouflin.photography.networking.GetUsingPhotographyCameraPayload;
import net.blouflin.photography.networking.PhotographyAlbumOpenPayload;
import net.blouflin.photography.networking.PlayCameraShutterSoundPayload;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class PhotographyClient implements ClientModInitializer {
    public static final KeyMapping.Category PHOTOGRAPHY_KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("photography", "photography"));
    public static KeyMapping SHOOT_KEY;

    @Override
    public void onInitializeClient() {

        SHOOT_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.photography.shoot",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_ENTER,
                PHOTOGRAPHY_KEY_CATEGORY));
        Photography.LOGGER.info("[PhotographyDebug] debugCaptureImages={}", PhotographyCaptureDebug.DEBUG_CAPTURE_IMAGES);
        Photography.LOGGER.info("[PhotographyDebug] debugViewfinder={}", PhotographyHud.isDebugViewfinderEnabled());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PhotographyHud.tickClient();
            PhotographyCaptureTask.tickClient(client);
            PhotographyFlashDynamicLight.tickClient();
        });

        ClientPlayNetworking.registerGlobalReceiver(CreatePicturePayload.ID, (payload, handler) -> CreatePicturePayload.receive(handler.client(), payload.id(), payload.nbtCompound()));
        ClientPlayNetworking.registerGlobalReceiver(CameraFlashStatePayload.ID, (payload, handler) -> CameraFlashStatePayload.receive(handler.client(), payload.playerId(), payload.active(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(GetUsingPhotographyCameraPayload.ID, (payload, handler) -> GetUsingPhotographyCameraPayload.receive(handler.client(), payload.player(), payload.isUsingPhotographyCamera(), payload.handUsingPhotographyCamera(), payload.selfie()));
        ClientPlayNetworking.registerGlobalReceiver(PlayCameraShutterSoundPayload.ID, (payload, handler) -> PlayCameraShutterSoundPayload.receive(handler.client(), payload.globalPos()));
        ClientPlayNetworking.registerGlobalReceiver(PhotographyAlbumOpenPayload.ID,
                (payload, handler) -> handler.client().execute(() ->
                        handler.client().setScreenAndShow(new PhotographyAlbumScreen(payload.hand()))));

        //HudRenderCallback.EVENT.register(this::onHudRender);
        LevelRenderEvents.END_MAIN.register(context -> PhotographyCaptureTask.extractCleanCapture("level_end_main_before_hand"));
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("photography", "capture_before_hud"), PhotographyCaptureTask::extractEarlyHudCapture);
        HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP, Identifier.fromNamespaceAndPath("photography", "after_sleep"), this::onHudRender);
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof PhotographyPhotoTooltip photoTooltip
                ? new PhotographyPhotoClientTooltip(photoTooltip)
                : null);
        EntityRendererRegistry.register(Photography.CAMERA_STAND_ENTITY, PhotographyCameraStandEntityRenderer::new);

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (((PlayerIsUsingCamera) player).isUsingPhotographyCamera()) {
                player.getItemInHand(InteractionHand.valueOf(PhotographyHud.handUsingPhotographyCamera)).use(world, player, InteractionHand.valueOf(PhotographyHud.handUsingPhotographyCamera));
                return InteractionResult.FAIL;
            } else if (PhotographyCamera.isPhotographyCamera(player.getItemInHand(hand))) {
                player.getItemInHand(hand).use(world, player, hand);
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;


        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (((PlayerIsUsingCamera) player).isUsingPhotographyCamera()) {
                player.getItemInHand(InteractionHand.valueOf(PhotographyHud.handUsingPhotographyCamera)).use(world, player, InteractionHand.valueOf(PhotographyHud.handUsingPhotographyCamera));
                return InteractionResult.FAIL;
            } else if (PhotographyCamera.isPhotographyCamera(player.getItemInHand(hand))) {
                player.getItemInHand(hand).use(world, player, hand);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack itemStack = player.getItemInHand(hand);

            if (((PlayerIsUsingCamera) player).isUsingPhotographyCamera()) {
                player.getItemInHand(InteractionHand.valueOf(PhotographyHud.handUsingPhotographyCamera)).use(world, player, InteractionHand.valueOf(PhotographyHud.handUsingPhotographyCamera));
                return InteractionResult.FAIL;
            } else if (PhotographyCamera.isPhotographyCamera(itemStack)) {
                itemStack.use(world, player, hand);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private void onHudRender(GuiGraphicsExtractor context, DeltaTracker renderTickCounter) {
        if (PhotographyHud.isUsingPhotographyCamera) {
            PhotographyHud.renderPhotographyCameraOverlay(context);
        }
    }
}
