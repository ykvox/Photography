package net.blouflin.photography.networking;

import net.blouflin.image2map.Image2Map;
import net.blouflin.image2map.renderer.MapRenderer;
import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyPhoto;
import net.blouflin.photography.PhotographyUtil;
import net.blouflin.photography.client.PhotographyCaptureDebug;
import net.blouflin.photography.client.PhotographyCaptureTask;
import net.blouflin.photography.client.PhotographyFlashDynamicLight;
import net.blouflin.photography.client.PhotographyHud;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CreatePicturePayload(Integer id, CompoundTag nbtCompound) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CreatePicturePayload> ID = CustomPacketPayload.createType("photography_create_picture");
    public static final StreamCodec<FriendlyByteBuf, CreatePicturePayload> CODEC = StreamCodec.ofMember((value, buf) -> buf.writeInt(value.id).writeNbt(value.nbtCompound), buf -> new CreatePicturePayload(buf.readInt(),buf.readNbt()));
    private static final boolean DEBUG_CAPTURE = Boolean.getBoolean("photography.debugCapture");
    private static final Identifier EXPOSURE_PALETTE = Identifier.fromNamespaceAndPath("photography", "photo/map_colors_plus.json");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("\"([0-9A-Fa-f]{8})\"");
    private static int[] cachedExposurePalette;
    private static String lastResolvedAutoShutterNotation = "";
    private static float lastAutoExposureMultiplier = 1.0f;
    private static double lastAutoRawAvgLuma;
    private static double lastAutoPreExposureAvgLuma;
    private static double lastAutoPostExposureAvgLuma;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(Minecraft client, Integer id, CompoundTag nbtCompound) {
        debugCapture("received create picture payload for map {}", id);

        client.execute(() -> {
            String captureId = PhotographyHud.activeCaptureId();
            debugCapture("preparing world-only capture");
            PhotographyCaptureDebug.logCaptureStart(captureId);
            PhotographyCaptureDebug.logCaptureContext(
                    captureId,
                    PhotographyHud.cameraStateName(),
                    PhotographyHud.cameraControlsOpen,
                    PhotographyHud.isSelfieEnabled(),
                    PhotographyHud.zoomAmount,
                    PhotographyHud.currentFovMultiplier(),
                    PhotographyHud.currentFocalLengthMm(),
                    PhotographyHud.SETTINGS.shutterSpeed().label(),
                    PhotographyHud.SETTINGS.flashMode().label(),
                    PhotographyHud.activeResolvedFlash(),
                    PhotographyHud.activeShutterFeedbackColor(),
                    PhotographyHud.flashBrightnessMultiplier(),
                    PhotographyHud.shutterSpeedBrightnessMultiplier(),
                    PhotographyHud.totalCaptureBrightnessMultiplier());

            MapItemSavedData mapState = PhotographyUtil.fromNbt(nbtCompound);

            PhotographyCaptureTask.captureBeforeHud(captureId)
                    .thenAccept(nativeImage -> client.execute(() -> processCapturedImage(client, id, nbtCompound, mapState, captureId, nativeImage)))
                    .exceptionally(throwable -> {
                        client.execute(() -> {
                            Photography.LOGGER.error("[PhotographyCaptureDebug] capture id={} failed before map conversion", captureId, throwable);
                            if (PhotographyHud.activeResolvedFlash()) {
                                PhotographyFlashDynamicLight.stopFlashLight();
                            }
                            PhotographyHud.finishCapture();
                        });
                        return null;
                    });
        });
    }

    private static void processCapturedImage(Minecraft client, Integer id, CompoundTag nbtCompound,
                                             MapItemSavedData mapState, String captureId, NativeImage nativeImage) {
        debugCapture("world-only screenshot captured: {}x{}", nativeImage.getWidth(), nativeImage.getHeight());
        if (PhotographyCaptureDebug.DEBUG_CAPTURE_IMAGES) {
            Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} source framebuffer/screenshot size={}x{}",
                    captureId, nativeImage.getWidth(), nativeImage.getHeight());
        }
        PhotographyHud.debugViewfinder("screenshot readback end");
        try {
            int[] rawPixels = nativeImage.getPixels();
            ColorAverages rawAverages = averageColors(rawPixels);
            PhotographyCaptureDebug.logRawReadbackComplete(captureId, rawPixels, nativeImage.getWidth(), nativeImage.getHeight());
            PhotographyCaptureDebug.writeArgb(captureId, "01_raw_screenshot", rawPixels, nativeImage.getWidth(), nativeImage.getHeight());
            if (PhotographyHud.activeResolvedFlash()) {
                PhotographyFlashDynamicLight.stopFlashLight();
                Photography.LOGGER.info("[flash-effect] spawning post-capture particles for captureId={}", captureId);
                ClientPlayNetworking.send(new CameraPhysicalSoundPayload("flash"));
            }

            debugCapture("crop/scale start");
            CroppedImage croppedImage = cropToSquare(captureId, rawPixels, nativeImage.getWidth(), nativeImage.getHeight());
            PhotographyCaptureDebug.writeArgb(captureId, "02_cropped_square", croppedImage.pixels(), croppedImage.size(), croppedImage.size());

            int[] photoPixels = scaleToPixels(captureId, "photo", croppedImage.pixels(), croppedImage.size(), PhotographyPhoto.PHOTO_SIZE);
            applyCaptureBrightness(photoPixels, PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE, rawAverages.brightness());
            Photography.LOGGER.info("[photo-pipeline] stage=pre_dither size={}x{}", PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE);
            PhotographyCaptureDebug.writeArgb(captureId, "03_photo_320_pre_dither", photoPixels, PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE);
            applyPhotoDither(photoPixels, PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE);
            logAutoExposureFinal(photoPixels);
            Photography.LOGGER.info("[photo-pipeline] stage=post_dither size={}x{} ditherAlgorithm=floyd_steinberg_palette palette=exposure_map_colors_plus colors={} diffusion=7/16,3/16,5/16,1/16 colorMetric=rgb_squared exposureOrder=resize_then_exposure_then_dither highlightDiffusion=skip_flat_bright",
                    PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE, exposurePalette().length);
            PhotographyCaptureDebug.writeArgb(captureId, "04_photo_320_dithered", photoPixels, PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE);

            int[][] pixels = toRows(scaleToPixels(captureId, "map fallback", photoPixels, PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.MAP_FALLBACK_SIZE),
                    PhotographyPhoto.MAP_FALLBACK_SIZE, PhotographyPhoto.MAP_FALLBACK_SIZE);
            PhotographyCaptureDebug.writeArgb(captureId, "05_downscaled_128_fallback", flatten(pixels), PhotographyPhoto.MAP_FALLBACK_SIZE, PhotographyPhoto.MAP_FALLBACK_SIZE);
            Photography.LOGGER.info("[PhotographyCaptureDebug] capture id={} rawCaptureSize={}x{} finalCustomPhotoSize={}x{} mapFallbackSize={}x{} archivePngSize={}x{}",
                    captureId,
                    nativeImage.getWidth(),
                    nativeImage.getHeight(),
                    PhotographyPhoto.PHOTO_SIZE,
                    PhotographyPhoto.PHOTO_SIZE,
                    PhotographyPhoto.MAP_FALLBACK_SIZE,
                    PhotographyPhoto.MAP_FALLBACK_SIZE,
                    PhotographyPhoto.PHOTO_SIZE,
                    PhotographyPhoto.PHOTO_SIZE);
            debugCapture("crop/scale complete");

            debugCapture("map encode start");
            MapRenderer.render(pixels, Image2Map.DitherMode.FLOYD, id, mapState);
            PhotographyCaptureDebug.writeMapColors(captureId, "06_map_colors_after_dither", mapState);
            PhotographyCaptureDebug.identifyLikelyBlackEdgeSource(captureId);
            debugCapture("map encode complete");

            debugCapture("map save data serialization start");
            CompoundTag pictureNbt = PhotographyUtil.writeNbt(nbtCompound, mapState);
            debugCapture("map save data serialization complete");

            SpawnPicturePayload payload = new SpawnPicturePayload(id, pictureNbt, createShotMetadata(), createPhotoImage(photoPixels));
            debugCapture("sending spawn picture payload");
            ClientPlayNetworking.send(payload);
            debugCapture("spawn picture payload sent");

        } catch (RuntimeException e) {
            Photography.LOGGER.error("Photography capture failed while creating map {}", id, e);
        } finally {
            if (PhotographyHud.activeResolvedFlash()) {
                PhotographyFlashDynamicLight.stopFlashLight();
            }
            PhotographyHud.finishCapture();
            nativeImage.close();
        }
    }

    private static CroppedImage cropToSquare(String captureId, int[] pixels, int width, int height) {
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

        PhotographyCaptureDebug.logCropSource(captureId, cropX, cropY, cropSize, width, height);
        debugCapture("crop source: x={}, y={}, size={}, framebuffer={}x{}", cropX, cropY, cropSize, width, height);
        return new CroppedImage(croppedPixels, cropSize);
    }

    private static int[] scaleToPixels(String captureId, String label, int[] sourcePixels, int sourceSize, int destinationSize) {
        int[] scaledPixels = new int[destinationSize * destinationSize];
        int firstSourceY = sampleSourceCoordinate(0, sourceSize, destinationSize);
        int lastSourceY = sampleSourceCoordinate(destinationSize - 1, sourceSize, destinationSize);
        int firstSourceX = sampleSourceCoordinate(0, sourceSize, destinationSize);
        int lastSourceX = sampleSourceCoordinate(destinationSize - 1, sourceSize, destinationSize);
        PhotographyCaptureDebug.logSampleBounds(captureId, label + " downscale x", sourceSize, destinationSize, firstSourceX, lastSourceX);
        PhotographyCaptureDebug.logSampleBounds(captureId, label + " downscale y", sourceSize, destinationSize, firstSourceY, lastSourceY);

        for (int y = 0; y < destinationSize; y++) {
            int sourceY = sampleSourceCoordinate(y, sourceSize, destinationSize);
            for (int x = 0; x < destinationSize; x++) {
                int sourceX = sampleSourceCoordinate(x, sourceSize, destinationSize);
                scaledPixels[x + y * destinationSize] = sourcePixels[sourceX + sourceY * sourceSize] | 0xff000000;
            }
        }

        return scaledPixels;
    }

    private static void applyCaptureBrightness(int[] pixels, int width, int height, double rawAvgLuma) {
        ColorAverages averagesBefore = averageColors(pixels);
        RegionAverages regionsBefore = regionAverages(pixels, width, height);
        resolveAutoExposure(averagesBefore.brightness(), regionsBefore.centerLuminance());
        BrightnessPlan brightnessPlan = captureBrightnessPlan(averagesBefore, regionsBefore);
        float multiplier = brightnessPlan.totalMultiplier();
        boolean flashResolved = PhotographyHud.activeResolvedFlash();
        lastAutoRawAvgLuma = rawAvgLuma;
        lastAutoPreExposureAvgLuma = averagesBefore.brightness();
        if (Math.abs(multiplier - 1.0f) < 0.001f) {
            lastAutoPostExposureAvgLuma = averagesBefore.brightness();
            logAutoExposureStage(multiplier, averagesBefore.brightness());
            Photography.LOGGER.info("[flash-capture] avgLuma={} centerLuma={} edgeLuma={} daylight={} mode={} resolved={} imageBoost={} dynamicLight={} multiplier={} stage=post_readback_pre_fallback_dither size={}x{} averageRgbBefore={} averageRgbAfter={}",
                    String.format("%.4f", averagesBefore.brightness()),
                    String.format("%.4f", regionsBefore.centerLuminance()),
                    String.format("%.4f", regionsBefore.edgeLuminance()),
                    brightnessPlan.daylight(),
                    PhotographyHud.SETTINGS.flashMode().label(),
                    flashResolved,
                    String.format("%.3f", brightnessPlan.flashImageBoost()),
                    brightnessPlan.dynamicLight(),
                    multiplier,
                    width,
                    height,
                    averagesBefore.rgbString(),
                    averagesBefore.rgbString());
            return;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                pixels[index] = adjustBrightness(pixels[index], multiplier, flashResolved, flashFalloff(x, y, width, height));
            }
        }
        ColorAverages averagesAfter = averageColors(pixels);
        lastAutoPostExposureAvgLuma = averagesAfter.brightness();
        logAutoExposureStage(multiplier, averagesAfter.brightness());
        RegionAverages regionsAfter = regionAverages(pixels, width, height);
        Photography.LOGGER.info("[flash-capture] avgLuma={} centerLuma={} edgeLuma={} daylight={} mode={} resolved={} imageBoost={} dynamicLight={} multiplier={} stage=post_readback_pre_fallback_dither size={}x{} averageBrightnessAfter={} averageRgbBefore={} averageRgbAfter={} centerLuminanceAfter={} edgeLuminanceAfter={}",
                String.format("%.4f", averagesBefore.brightness()),
                String.format("%.4f", regionsBefore.centerLuminance()),
                String.format("%.4f", regionsBefore.edgeLuminance()),
                brightnessPlan.daylight(),
                PhotographyHud.SETTINGS.flashMode().label(),
                flashResolved,
                String.format("%.3f", brightnessPlan.flashImageBoost()),
                brightnessPlan.dynamicLight(),
                multiplier,
                width,
                height,
                String.format("%.4f", averagesAfter.brightness()),
                averagesBefore.rgbString(),
                averagesAfter.rgbString(),
                String.format("%.4f", regionsAfter.centerLuminance()),
                String.format("%.4f", regionsAfter.edgeLuminance()));
    }

    private static BrightnessPlan captureBrightnessPlan(ColorAverages averages, RegionAverages regions) {
        boolean autoShutter = PhotographyHud.SETTINGS.shutterSpeed() == net.blouflin.photography.client.PhotographyCameraSettings.ShutterSpeed.AUTO;
        float baseMultiplier = autoShutter
                ? lastAutoExposureMultiplier
                : PhotographyHud.shutterSpeedBrightnessMultiplier();
        boolean flashResolved = PhotographyHud.activeResolvedFlash();
        if (!flashResolved) {
            return new BrightnessPlan(MthClamp(baseMultiplier, 0.45f, 1.9f), 0.0f, false, false);
        }

        boolean dynamicLight = FabricLoader.getInstance().isModLoaded("lambdynlights");
        double luma = averages.brightness();
        boolean daylight = luma >= 0.62d || regions.centerLuminance() >= 0.72d;
        double darkness = Math.pow(Math.max(0.0d, 1.0d - luma), 1.55d);
        double centerShadow = Math.max(0.0d, 0.58d - regions.centerLuminance());
        float boost;
        if (daylight) {
            boost = (float) Math.min(0.10d, 0.04d + centerShadow * 0.16d);
        } else if (dynamicLight) {
            boost = (float) Math.min(0.42d, 0.12d + darkness * 0.42d + centerShadow * 0.18d);
        } else {
            boost = (float) Math.min(0.82d, 0.22d + darkness * 0.70d + centerShadow * 0.28d);
        }

        float total = baseMultiplier * (1.0f + boost);
        return new BrightnessPlan(MthClamp(total, 0.45f, daylight ? 1.18f : 2.05f), boost, daylight, dynamicLight);
    }

    private static void resolveAutoExposure(double averageLuminance, double centerLuminance) {
        if (PhotographyHud.SETTINGS.shutterSpeed() != net.blouflin.photography.client.PhotographyCameraSettings.ShutterSpeed.AUTO) {
            lastResolvedAutoShutterNotation = "";
            lastAutoExposureMultiplier = 1.0f;
            return;
        }

        String reason;
        if (PhotographyHud.activeResolvedFlash()) {
            lastResolvedAutoShutterNotation = "1/125";
            lastAutoExposureMultiplier = 1.0f;
            reason = "flash";
        } else if (averageLuminance < 0.20d || centerLuminance < 0.18d) {
            lastResolvedAutoShutterNotation = "1/15";
            lastAutoExposureMultiplier = 1.18f;
            reason = "very_dark";
        } else if (averageLuminance < 0.35d || centerLuminance < 0.30d) {
            lastResolvedAutoShutterNotation = "1/30";
            lastAutoExposureMultiplier = 1.10f;
            reason = "dim";
        } else if (averageLuminance < 0.55d || centerLuminance < 0.50d) {
            lastResolvedAutoShutterNotation = "1/60";
            lastAutoExposureMultiplier = 1.04f;
            reason = "normal_indoor";
        } else if (averageLuminance < 0.75d) {
            lastResolvedAutoShutterNotation = "1/125";
            lastAutoExposureMultiplier = 1.0f;
            reason = "normal";
        } else if (averageLuminance < 0.88d) {
            lastResolvedAutoShutterNotation = "1/250";
            lastAutoExposureMultiplier = 0.99f;
            reason = "bright";
        } else {
            lastResolvedAutoShutterNotation = "1/500";
            lastAutoExposureMultiplier = 0.98f;
            reason = "very_bright";
        }

        Photography.LOGGER.info("[auto-shutter] avgLuma={} centerLuma={} resolved={} reason={}",
                String.format("%.4f", averageLuminance),
                String.format("%.4f", centerLuminance),
                lastResolvedAutoShutterNotation,
                reason);
        Photography.LOGGER.info("[auto-exposure] avgLuma={} resolvedShutter={} exposureMultiplier={}",
                String.format("%.4f", averageLuminance),
                lastResolvedAutoShutterNotation,
                String.format("%.3f", lastAutoExposureMultiplier));
    }

    private static void logAutoExposureStage(float multiplier, double postExposureAvgLuma) {
        if (PhotographyHud.SETTINGS.shutterSpeed() != net.blouflin.photography.client.PhotographyCameraSettings.ShutterSpeed.AUTO) {
            return;
        }
        Photography.LOGGER.info("[auto-exposure] rawAvgLuma={} preExposureAvgLuma={} multiplier={} postExposureAvgLuma={} finalAvgLuma=pending resolvedShutter={}",
                String.format("%.4f", lastAutoRawAvgLuma),
                String.format("%.4f", lastAutoPreExposureAvgLuma),
                String.format("%.3f", multiplier),
                String.format("%.4f", postExposureAvgLuma),
                lastResolvedAutoShutterNotation);
    }

    private static void logAutoExposureFinal(int[] pixels) {
        if (PhotographyHud.SETTINGS.shutterSpeed() != net.blouflin.photography.client.PhotographyCameraSettings.ShutterSpeed.AUTO) {
            return;
        }
        ColorAverages finalAverages = averageColors(pixels);
        Photography.LOGGER.info("[auto-exposure] rawAvgLuma={} preExposureAvgLuma={} multiplier={} postExposureAvgLuma={} finalAvgLuma={} resolvedShutter={}",
                String.format("%.4f", lastAutoRawAvgLuma),
                String.format("%.4f", lastAutoPreExposureAvgLuma),
                String.format("%.3f", lastAutoExposureMultiplier),
                String.format("%.4f", lastAutoPostExposureAvgLuma),
                String.format("%.4f", finalAverages.brightness()),
                lastResolvedAutoShutterNotation);
    }

    private static int adjustBrightness(int argb, float multiplier, boolean flashResolved, double falloff) {
        int alpha = (argb >>> 24) & 0xff;
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;

        int[] adjusted = adjustExposureColor(red, green, blue, multiplier, flashResolved, falloff);
        red = adjusted[0];
        green = adjusted[1];
        blue = adjusted[2];
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int[] adjustExposureColor(int red, int green, int blue, float multiplier, boolean flashResolved, double falloff) {
        if (multiplier < 1.0f) {
            return new int[]{
                    clamp(Math.round(red * multiplier), 0, 255),
                    clamp(Math.round(green * multiplier), 0, 255),
                    clamp(Math.round(blue * multiplier), 0, 255)
            };
        }

        double luminance = (0.2126d * red + 0.7152d * green + 0.0722d * blue) / 255.0d;
        int[] exposed = applyExposureEffect(red, green, blue, multiplier);
        int exposedRed = exposed[0];
        int exposedGreen = exposed[1];
        int exposedBlue = exposed[2];
        if (!flashResolved) {
            return new int[]{exposedRed, exposedGreen, exposedBlue};
        }

        double pointLight = 0.25d + 0.75d * falloff;
        double shadow = Math.pow(1.0d - luminance, 1.2d);
        int neutralLift = clamp((int) Math.round((multiplier - 1.0f) * 34.0d * shadow * pointLight), 0, 54);
        double burn = Math.max(0.0d, falloff - 0.68d) * Math.max(0.0d, multiplier - 1.0f);
        return new int[]{
                burnChannel(exposedRed + neutralLift, burn),
                burnChannel(exposedGreen + neutralLift, burn),
                burnChannel(exposedBlue + neutralLift, burn)
        };
    }

    private static int burnChannel(int value, double burn) {
        double normalized = clamp(value, 0, 255) / 255.0d;
        double burned = normalized + (1.0d - normalized) * Math.min(0.38d, burn * 0.42d);
        return clamp((int) Math.round(burned * 255.0d), 0, 255);
    }

    private static double flashFalloff(int x, int y, int width, int height) {
        double centerX = (width - 1) * 0.5d;
        double centerY = (height - 1) * 0.48d;
        double dx = (x - centerX) / Math.max(1.0d, width * 0.5d);
        double dy = (y - centerY) / Math.max(1.0d, height * 0.5d);
        double distanceSq = dx * dx + dy * dy;
        return 1.0d / (1.0d + distanceSq * 2.4d);
    }

    private static int[] applyExposureEffect(int red, int green, int blue, float brightness) {
        if (Math.abs(brightness - 1.0f) < 0.001f) {
            return new int[]{red, green, blue};
        }

        float lightness = (blue + green + red) / 765.0f;
        float bias;
        if (brightness < 1.0f) {
            bias = (1.0f - lightness) * 0.8f + 0.2f;
        } else {
            float curve = (float) Math.pow(Math.sin(lightness * Math.PI), 2.0d);
            bias = lightness > 0.5f ? curve * 0.8f + 0.2f : curve * 0.5f + 0.5f;
        }

        float adjustedBlue = lerp(bias, blue, blue * brightness);
        float adjustedGreen = lerp(bias, green, green * brightness);
        float adjustedRed = lerp(bias, red, red * brightness);
        int[] redistributed = redistributeExposure(adjustedRed, adjustedGreen, adjustedBlue);
        return new int[]{
                clamp(lerpInt(0.5f, (int) adjustedRed, redistributed[0]), 0, 255),
                clamp(lerpInt(0.5f, (int) adjustedGreen, redistributed[1]), 0, 255),
                clamp(lerpInt(0.5f, (int) adjustedBlue, redistributed[2]), 0, 255)
        };
    }

    private static int[] redistributeExposure(float red, float green, float blue) {
        float threshold = 255.999f;
        float max = Math.max(red, Math.max(green, blue));
        if (max <= threshold) {
            return new int[]{
                    clamp(Math.round(red), 0, 255),
                    clamp(Math.round(green), 0, 255),
                    clamp(Math.round(blue), 0, 255)
            };
        }

        float total = red + green + blue;
        if (total >= 3.0f * threshold) {
            return new int[]{(int) threshold, (int) threshold, (int) threshold};
        }

        float x = (3.0f * threshold - total) / (3.0f * max - total);
        float gray = threshold - x * max;
        return new int[]{
                clamp(Math.round(gray + x * red), 0, 255),
                clamp(Math.round(gray + x * green), 0, 255),
                clamp(Math.round(gray + x * blue), 0, 255)
        };
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static int lerpInt(float delta, int start, int end) {
        return start + Math.round(delta * (end - start));
    }

    private static void applyPhotoDither(int[] pixels, int width, int height) {
        int[] palette = exposurePalette();
        double[] red = new double[pixels.length];
        double[] green = new double[pixels.length];
        double[] blue = new double[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            red[i] = (pixels[i] >>> 16) & 0xff;
            green[i] = (pixels[i] >>> 8) & 0xff;
            blue[i] = pixels[i] & 0xff;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                int oldRed = clamp((int) Math.round(red[index]), 0, 255);
                int oldGreen = clamp((int) Math.round(green[index]), 0, 255);
                int oldBlue = clamp((int) Math.round(blue[index]), 0, 255);
                int newColor = closestPaletteColor(oldRed, oldGreen, oldBlue, palette);
                int newRed = (newColor >>> 16) & 0xff;
                int newGreen = (newColor >>> 8) & 0xff;
                int newBlue = newColor & 0xff;
                pixels[index] = (pixels[index] & 0xff000000) | (newRed << 16) | (newGreen << 8) | newBlue;

                if (!isFlatBrightRegion(oldRed, oldGreen, oldBlue)) {
                    diffuseDitherError(red, width, height, x, y, oldRed - newRed);
                    diffuseDitherError(green, width, height, x, y, oldGreen - newGreen);
                    diffuseDitherError(blue, width, height, x, y, oldBlue - newBlue);
                }
            }
        }
    }

    private static boolean isFlatBrightRegion(int red, int green, int blue) {
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        double luma = (0.2126d * red + 0.7152d * green + 0.0722d * blue) / 255.0d;
        return luma > 0.88d && max - min < 70;
    }

    private static int closestPaletteColor(int red, int green, int blue, int[] palette) {
        int closest = palette[0];
        int closestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int color = palette[i];
            if (((color >>> 24) & 0xff) == 0) {
                continue;
            }
            int dr = red - ((color >>> 16) & 0xff);
            int dg = green - ((color >>> 8) & 0xff);
            int db = blue - (color & 0xff);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = color;
            }
        }
        return closest;
    }

    private static int[] exposurePalette() {
        if (cachedExposurePalette != null) {
            return cachedExposurePalette;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(EXPOSURE_PALETTE);
        if (resource.isPresent()) {
            try (InputStream stream = resource.get().open()) {
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                java.util.ArrayList<Integer> colors = new java.util.ArrayList<>(256);
                Matcher matcher = HEX_COLOR_PATTERN.matcher(json);
                while (matcher.find()) {
                    colors.add((int) Long.parseUnsignedLong(matcher.group(1), 16));
                }
                if (!colors.isEmpty()) {
                    cachedExposurePalette = colors.stream().mapToInt(Integer::intValue).toArray();
                    return cachedExposurePalette;
                }
            } catch (IOException | NumberFormatException e) {
                Photography.LOGGER.warn("[photo-pipeline] failed to load Exposure palette {}", EXPOSURE_PALETTE, e);
            }
        }
        cachedExposurePalette = fallbackRgbPalette();
        return cachedExposurePalette;
    }

    private static int[] fallbackRgbPalette() {
        int levels = 8;
        int[] palette = new int[levels * levels * levels];
        int index = 0;
        for (int r = 0; r < levels; r++) {
            for (int g = 0; g < levels; g++) {
                for (int b = 0; b < levels; b++) {
                    palette[index++] = 0xff000000
                            | (r * 255 / (levels - 1) << 16)
                            | (g * 255 / (levels - 1) << 8)
                            | (b * 255 / (levels - 1));
                }
            }
        }
        return palette;
    }

    private static void diffuseDitherError(double[] channel, int width, int height, int x, int y, double error) {
        addDitherError(channel, width, height, x + 1, y, error * 7.0d / 16.0d);
        addDitherError(channel, width, height, x - 1, y + 1, error * 3.0d / 16.0d);
        addDitherError(channel, width, height, x, y + 1, error * 5.0d / 16.0d);
        addDitherError(channel, width, height, x + 1, y + 1, error * 1.0d / 16.0d);
    }

    private static void addDitherError(double[] channel, int width, int height, int x, int y, double error) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        int index = x + y * width;
        channel[index] = Math.max(0.0d, Math.min(255.0d, channel[index] + error));
    }

    private static ColorAverages averageColors(int[] pixels) {
        long redTotal = 0L;
        long greenTotal = 0L;
        long blueTotal = 0L;
        long count = 0L;
        for (int argb : pixels) {
            int red = (argb >>> 16) & 0xff;
            int green = (argb >>> 8) & 0xff;
            int blue = argb & 0xff;
            redTotal += red;
            greenTotal += green;
            blueTotal += blue;
            count++;
        }
        if (count == 0L) {
            return new ColorAverages(0.0d, 0.0d, 0.0d);
        }
        return new ColorAverages(redTotal / (count * 255.0d), greenTotal / (count * 255.0d), blueTotal / (count * 255.0d));
    }

    private static RegionAverages regionAverages(int[] pixels, int width, int height) {
        double centerTotal = 0.0d;
        double edgeTotal = 0.0d;
        int centerCount = 0;
        int edgeCount = 0;
        double centerX = (width - 1) * 0.5d;
        double centerY = (height - 1) * 0.5d;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[x + y * width];
                double luminance = ((0.2126d * ((argb >>> 16) & 0xff))
                        + (0.7152d * ((argb >>> 8) & 0xff))
                        + (0.0722d * (argb & 0xff))) / 255.0d;
                double dx = Math.abs(x - centerX) / Math.max(1.0d, width * 0.5d);
                double dy = Math.abs(y - centerY) / Math.max(1.0d, height * 0.5d);
                double distance = Math.max(dx, dy);
                if (distance <= 0.25d) {
                    centerTotal += luminance;
                    centerCount++;
                } else if (distance >= 0.72d) {
                    edgeTotal += luminance;
                    edgeCount++;
                }
            }
        }
        return new RegionAverages(centerCount == 0 ? 0.0d : centerTotal / centerCount,
                edgeCount == 0 ? 0.0d : edgeTotal / edgeCount);
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

    private static int[][] toRows(int[] pixels, int width, int height) {
        int[][] rows = new int[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(pixels, y * width, rows[y], 0, width);
        }
        return rows;
    }

    private static CompoundTag createPhotoImage(int[] photoPixels) {
        return PhotographyPhoto.createImageTag(PhotographyPhoto.PHOTO_SIZE, PhotographyPhoto.PHOTO_SIZE, photoPixels);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float MthClamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static CompoundTag createShotMetadata() {
        CompoundTag metadata = new CompoundTag();
        metadata.putInt("focalLengthMm", PhotographyHud.currentFocalLengthMm());
        metadata.putString("configuredShutterSpeed", PhotographyHud.SETTINGS.shutterSpeed().notation());
        metadata.putString("shutterSpeed", !lastResolvedAutoShutterNotation.isEmpty()
                ? lastResolvedAutoShutterNotation
                : PhotographyHud.activeResolvedShutterSpeedNotation());
        metadata.putString("flashMode", PhotographyHud.SETTINGS.flashMode().label());
        metadata.putBoolean("resolvedFlash", PhotographyHud.activeResolvedFlash());
        metadata.putFloat("fovMultiplier", (float) PhotographyHud.currentFovMultiplier());
        return metadata;
    }

    private record CroppedImage(int[] pixels, int size) {
    }

    private record ColorAverages(double red, double green, double blue) {
        private double brightness() {
            return (red + green + blue) / 3.0d;
        }

        private String rgbString() {
            return String.format("%.4f,%.4f,%.4f", red, green, blue);
        }
    }

    private record RegionAverages(double centerLuminance, double edgeLuminance) {
    }

    private record BrightnessPlan(float totalMultiplier, float flashImageBoost, boolean daylight, boolean dynamicLight) {
    }

    private static void debugCapture(String message, Object... args) {
        if (DEBUG_CAPTURE) {
            Photography.LOGGER.info("[capture] " + message, args);
        }
    }
}
