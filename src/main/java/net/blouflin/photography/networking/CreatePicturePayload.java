package net.blouflin.photography.networking;

import net.blouflin.image2map.Image2Map;
import net.blouflin.image2map.renderer.MapRenderer;
import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyUtil;
import net.blouflin.photography.client.PhotographyHud;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.concurrent.CompletableFuture;

public record CreatePicturePayload(Integer id, CompoundTag nbtCompound) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CreatePicturePayload> ID = CustomPacketPayload.createType("photography_create_picture");
    public static final StreamCodec<FriendlyByteBuf, CreatePicturePayload> CODEC = StreamCodec.ofMember((value, buf) -> buf.writeInt(value.id).writeNbt(value.nbtCompound), buf -> new CreatePicturePayload(buf.readInt(),buf.readNbt()));
    private static final boolean DEBUG_CAPTURE = Boolean.getBoolean("photography.debugCapture");

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(Minecraft client, Integer id, CompoundTag nbtCompound) {
        debugCapture("received create picture payload for map {}", id);

        CompletableFuture<Void> future = new CompletableFuture<>();

        client.execute(() -> {
            debugCapture("preparing HUD for capture");

            MapItemSavedData mapState = PhotographyUtil.fromNbt(nbtCompound);

            PhotographyHud.renderViewfinderMask = false;

            PhotographyHud.setScreenshotFuture(future);

            future.thenRun(() -> {
                debugCapture("HUD frame completed; starting screenshot capture");

                PhotographyHud.renderViewfinderMask = true;
                PhotographyHud.spyglassFlashOpacity = 1.0f;
                PhotographyHud.isTakingPhoto = false;

                Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), (nativeImage -> {
                    debugCapture("screenshot captured: {}x{}", nativeImage.getWidth(), nativeImage.getHeight());
                    try {
                        debugCapture("crop/scale start");
                        int[][] pixels = cropAndScaleToMapPixels(nativeImage.getPixels(), nativeImage.getWidth(), nativeImage.getHeight());
                        debugCapture("crop/scale complete");

                        debugCapture("map encode start");
                        MapRenderer.render(pixels, Image2Map.DitherMode.FLOYD, id, mapState);
                        debugCapture("map encode complete");

                        debugCapture("map save data serialization start");
                        CompoundTag pictureNbt = PhotographyUtil.writeNbt(nbtCompound, mapState);
                        debugCapture("map save data serialization complete");

                        SpawnPicturePayload payload = new SpawnPicturePayload(id, pictureNbt);
                        debugCapture("sending spawn picture payload");
                        ClientPlayNetworking.send(payload);
                        debugCapture("spawn picture payload sent");

                    } catch (RuntimeException e) {
                        Photography.LOGGER.error("Photography capture failed while creating map {}", id, e);
                    } finally {
                        nativeImage.close();
                    }
                }));
            });
        });
    }

    private static int[][] cropAndScaleToMapPixels(int[] pixels, int width, int height) {
        int cropSize = Math.min(width, height);
        int cropX = (width - cropSize) / 2;
        int cropY = (height - cropSize) / 2;
        int[][] scaledPixels = new int[128][128];

        for (int y = 0; y < 128; y++) {
            int sourceY = cropY + Math.min(cropSize - 1, y * cropSize / 128);
            for (int x = 0; x < 128; x++) {
                int sourceX = cropX + Math.min(cropSize - 1, x * cropSize / 128);
                scaledPixels[y][x] = pixels[sourceX + sourceY * width];
            }
        }

        return scaledPixels;
    }

    private static void debugCapture(String message, Object... args) {
        if (DEBUG_CAPTURE) {
            Photography.LOGGER.info("[capture] " + message, args);
        }
    }
}
