package net.blouflin.photography.client;

import net.blouflin.photography.Photography;
import net.blouflin.photography.PngWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.IOException;
import java.nio.file.Path;

public final class PhotographyCaptureDebug {
    public static final boolean DEBUG_CAPTURE_IMAGES = Boolean.getBoolean("photography.debugCaptureImages");

    private PhotographyCaptureDebug() {
    }

    public static void writeArgb(String stage, int[] pixels, int width, int height) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        Path outputPath = debugDirectory().resolve(stage + ".png");
        try {
            PngWriter.writeArgb(outputPath, pixels, width, height);
            Photography.LOGGER.info("[capture-images] wrote {}", outputPath);
        } catch (IOException e) {
            Photography.LOGGER.error("[capture-images] failed to write {}", outputPath, e);
        }
    }

    public static void writeMapColors(String stage, MapItemSavedData mapState) {
        if (!DEBUG_CAPTURE_IMAGES) {
            return;
        }

        int size = (int) Math.sqrt(mapState.colors.length);
        if (size * size != mapState.colors.length) {
            Photography.LOGGER.warn("[capture-images] cannot dump non-square map color array: {}", mapState.colors.length);
            return;
        }

        int[] pixels = new int[mapState.colors.length];
        for (int i = 0; i < mapState.colors.length; i++) {
            pixels[i] = MapColor.getColorFromPackedId(Byte.toUnsignedInt(mapState.colors[i]));
        }
        writeArgb(stage, pixels, size, size);
    }

    private static Path debugDirectory() {
        Minecraft client = Minecraft.getInstance();
        return client.gameDirectory.toPath().resolve("photography_debug");
    }
}
