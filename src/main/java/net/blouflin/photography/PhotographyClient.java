package net.blouflin.photography;

import net.blouflin.photography.client.PhotographyHud;
import net.blouflin.photography.client.PhotographyCaptureDebug;
import net.blouflin.photography.networking.CreatePicturePayload;
import net.blouflin.photography.networking.GetUsingPhotographyCameraPayload;
import net.blouflin.photography.networking.PlayCameraShutterSoundPayload;
import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import static net.blouflin.photography.Photography.CAMERA_SHUTTER;
import static net.blouflin.photography.Photography.CAMERA_SHUTTER_SOUND;

@Environment(EnvType.CLIENT)
public class PhotographyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        Registry.register(BuiltInRegistries.SOUND_EVENT, CAMERA_SHUTTER_SOUND, CAMERA_SHUTTER);
        Photography.LOGGER.info("[PhotographyDebug] debugCaptureImages={}", PhotographyCaptureDebug.DEBUG_CAPTURE_IMAGES);
        Photography.LOGGER.info("[PhotographyDebug] debugViewfinder={}", PhotographyHud.isDebugViewfinderEnabled());

        ClientPlayNetworking.registerGlobalReceiver(CreatePicturePayload.ID, (payload, handler) -> CreatePicturePayload.receive(handler.client(), payload.id(), payload.nbtCompound()));
        ClientPlayNetworking.registerGlobalReceiver(GetUsingPhotographyCameraPayload.ID, (payload, handler) -> GetUsingPhotographyCameraPayload.receive(handler.client(), payload.player(), payload.isUsingPhotographyCamera(), payload.handUsingPhotographyCamera()));
        ClientPlayNetworking.registerGlobalReceiver(PlayCameraShutterSoundPayload.ID, (payload, handler) -> PlayCameraShutterSoundPayload.receive(handler.client(), payload.globalPos()));

        //HudRenderCallback.EVENT.register(this::onHudRender);
        HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP, Identifier.fromNamespaceAndPath("photography", "after_sleep"), this::onHudRender);

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
