package net.blouflin.photography.networking;

import net.blouflin.image2map.Image2Map;
import net.blouflin.image2map.renderer.MapRenderer;
import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyUtil;
import net.blouflin.photography.client.PhotographyCaptureDebug;
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

            PhotographyHud.beginCaptureOverlaySuppression();
            PhotographyHud.requestCleanCaptureFrame(future);

            future.thenRun(() -> {
                debugCapture("HUD frame completed; starting screenshot capture");
                client.execute(() -> {
                    PhotographyHud.debugViewfinder("screenshot readback start");

                    Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), (nativeImage -> {
                        debugCapture("screenshot captured: {}x{}", nativeImage.getWidth(), nativeImage.getHeight());
                        PhotographyHud.debugViewfinder("screenshot readback end");
                        try {
                            int[] rawPixels = nativeImage.getPixels();
                            PhotographyCaptureDebug.writeArgb("01_raw_screenshot", rawPixels, nativeImage.getWidth(), nativeImage.getHeight());

                            debugCapture("crop/scale start");
                            CroppedImage croppedImage = cropToSquare(rawPixels, nativeImage.getWidth(), nativeImage.getHeight());
                            PhotographyCaptureDebug.writeArgb("02_cropped_square", croppedImage.pixels(), croppedImage.size(), croppedImage.size());

                            int[][] pixels = scaleToMapPixels(croppedImage.pixels(), croppedImage.size());
                            PhotographyCaptureDebug.writeArgb("03_downscaled_128", flatten(pixels), 128, 128);
                            debugCapture("crop/scale complete");

                            debugCapture("map encode start");
                            MapRenderer.render(pixels, Image2Map.DitherMode.FLOYD, id, mapState);
                            PhotographyCaptureDebug.writeMapColors("04_map_colors_after_dither", mapState);
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
                            PhotographyHud.restoreOverlayAfterCapture();
                            PhotographyHud.spyglassFlashOpacity = 1.0f;
                            PhotographyHud.isTakingPhoto = false;
                            nativeImage.close();
                        }
                    }));
                });
            });
        });
    }

    private static CroppedImage cropToSquare(int[] pixels, int width, int height) {
        int cropSize = Math.min(width, height);
        int cropX = (width - cropSize) / 2;
        int cropY = (height - cropSize) / 2;
        int[] croppedPixels = new int[cropSize * cropSize];

        for (int y = 0; y < cropSize; y++) {
            int sourceY = clamp(cropY + y, 0, height - 1);
            for (int x = 0; x < cropSize; x++) {
                int sourceX = clamp(cropX + x, 0, width - 1);
                croppedPixels[x + y * cropSize] = pixels[sourceX + sourceY * width] | 0xff000000;
            }
        }

        debugCapture("crop source: x={}, y={}, size={}, framebuffer={}x{}", cropX, cropY, cropSize, width, height);
        return new CroppedImage(croppedPixels, cropSize);
    }

    private static int[][] scaleToMapPixels(int[] croppedPixels, int cropSize) {
        int[][] scaledPixels = new int[128][128];

        for (int y = 0; y < 128; y++) {
            int sourceY = sampleSourceCoordinate(y, cropSize, 128);
            for (int x = 0; x < 128; x++) {
                int sourceX = sampleSourceCoordinate(x, cropSize, 128);
                scaledPixels[y][x] = croppedPixels[sourceX + sourceY * cropSize];
            }
        }

        return scaledPixels;
    }

    private static int sampleSourceCoordinate(int destinationCoordinate, int sourceSize, int destinationSize) {
        double sourcePosition = ((double) destinationCoordinate + 0.5d) * (double) sourceSize / (double) destinationSize;
        return clamp((int) sourcePosition, 0, sourceSize - 1);
    }

    private static int[] flatten(int[][] pixels) {
        int height = pixels.length;
        int width = pixels[0].length;
        int[] flattened = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flattened[x + y * width] = pixels[y][x];
            }
        }
        return flattened;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CroppedImage(int[] pixels, int size) {
    }

    private static void debugCapture(String message, Object... args) {
        if (DEBUG_CAPTURE) {
            Photography.LOGGER.info("[capture] " + message, args);
        }
    }
}
