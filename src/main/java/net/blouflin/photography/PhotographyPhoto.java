package net.blouflin.photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.net.URI;
import java.net.URL;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class PhotographyPhoto {
    public static final String SHOT_METADATA_TAG = "photographyShot";
    public static final String IMAGE_TAG = "photographyImage";
    private static final String IMAGE_WIDTH_TAG = "width";
    private static final String IMAGE_HEIGHT_TAG = "height";
    private static final String IMAGE_HASH_TAG = "hash";
    private static final String IMAGE_PIXELS_TAG = "pixels";
    private static final String IMAGE_RGB_COMPRESSED_TAG = "rgbCompressed";
    private static final String IMAGE_URL_TAG = "url";
    private static final String IMAGE_FILE_TAG = "file";
    public static final int PHOTO_SIZE = 320;
    public static final int MAP_FALLBACK_SIZE = 128;

    private PhotographyPhoto() {
    }

    public static boolean isPhotographyPhoto(ItemStack stack) {
        if (!stack.is(Items.FILLED_MAP)) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CustomData photoData = stack.get(Photography.PHOTO_COMPONENT);
        if ((customData == null || customData.isEmpty()) && (photoData == null || photoData.isEmpty())) {
            return false;
        }
        CompoundTag tag = photoRoot(stack);
        return tag.contains(SHOT_METADATA_TAG) || tag.contains(IMAGE_TAG);
    }

    public static ImageData getImage(ItemStack stack) {
        CompoundTag imageTag = photoRoot(stack).getCompoundOrEmpty(IMAGE_TAG);
        if (imageTag.isEmpty()) {
            imageTag = photoRoot(stack);
        }
        if ((imageTag.contains(IMAGE_URL_TAG) || imageTag.contains(IMAGE_FILE_TAG))
                && !imageTag.contains(IMAGE_RGB_COMPRESSED_TAG)
                && !imageTag.contains(IMAGE_PIXELS_TAG)) {
            ImageData resolved = resolveExternalImage(imageTag);
            if (!resolved.isEmpty()) {
                putImage(stack, resolved);
            }
            return resolved;
        }
        return imageFromTag(imageTag);
    }

    public static void putImage(ItemStack stack, ImageData image) {
        if (image.isEmpty()) {
            return;
        }
        CompoundTag root = photoRoot(stack);
        root.put(IMAGE_TAG, createImageTag(image.width(), image.height(), image.pixels()));
        CustomData component = CustomData.of(root);
        stack.set(Photography.PHOTO_COMPONENT, component);
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> customData.update(tag -> {
            tag.put(IMAGE_TAG, createImageTag(image.width(), image.height(), image.pixels()));
            if (root.contains(SHOT_METADATA_TAG)) {
                tag.put(SHOT_METADATA_TAG, root.getCompoundOrEmpty(SHOT_METADATA_TAG));
            }
        }));
    }

    public static ImageData imageFromTag(CompoundTag imageTag) {
        int width = imageTag.getIntOr(IMAGE_WIDTH_TAG, 0);
        int height = imageTag.getIntOr(IMAGE_HEIGHT_TAG, 0);
        int[] pixels = readCompactPixels(imageTag, width, height);
        if (pixels.length == 0) {
            pixels = imageTag.getIntArray(IMAGE_PIXELS_TAG).orElse(new int[0]);
        }
        int hash = imageTag.getIntOr(IMAGE_HASH_TAG, Arrays.hashCode(pixels));
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            return ImageData.empty();
        }
        return new ImageData(width, height, pixels, hash);
    }

    public static CompoundTag createImageTag(int width, int height, int[] pixels) {
        CompoundTag image = new CompoundTag();
        if (width <= 0 || height <= 0 || pixels == null || pixels.length != width * height) {
            return image;
        }
        image.putInt(IMAGE_WIDTH_TAG, width);
        image.putInt(IMAGE_HEIGHT_TAG, height);
        image.putInt(IMAGE_HASH_TAG, Arrays.hashCode(pixels));
        image.putByteArray(IMAGE_RGB_COMPRESSED_TAG, compressRgb(pixels));
        return image;
    }

    public static void compactImageInPlace(ItemStack stack) {
        ImageData image = getImage(stack);
        if (image.isEmpty()) {
            return;
        }
        putImage(stack, image);
    }

    private static CompoundTag photoRoot(ItemStack stack) {
        CustomData photoData = stack.get(Photography.PHOTO_COMPONENT);
        if (photoData != null && !photoData.isEmpty()) {
            return photoData.copyTag();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null || customData.isEmpty() ? new CompoundTag() : customData.copyTag();
    }

    private static ImageData resolveExternalImage(CompoundTag imageTag) {
        String file = imageTag.getStringOr(IMAGE_FILE_TAG, "");
        String url = imageTag.getStringOr(IMAGE_URL_TAG, "");
        try (NativeImage nativeImage = !file.isBlank()
                ? NativeImage.read(new FileInputStream(file))
                : NativeImage.read(openUrl(url))) {
            return processExternalImage(nativeImage);
        } catch (Exception e) {
            Photography.LOGGER.warn("[photo-component] failed to resolve external photograph source file={} url={}", file, url, e);
            return ImageData.empty();
        }
    }

    private static java.io.InputStream openUrl(String urlText) throws IOException {
        if (urlText == null || urlText.isBlank()) {
            throw new IOException("blank photograph URL");
        }
        URL url = URI.create(urlText).toURL();
        return url.openStream();
    }

    private static ImageData processExternalImage(NativeImage nativeImage) {
        int sourceWidth = nativeImage.getWidth();
        int sourceHeight = nativeImage.getHeight();
        int cropSize = Math.min(sourceWidth, sourceHeight);
        int cropX = (sourceWidth - cropSize) / 2;
        int cropY = (sourceHeight - cropSize) / 2;
        int[] pixels = new int[PHOTO_SIZE * PHOTO_SIZE];
        for (int y = 0; y < PHOTO_SIZE; y++) {
            int sourceY = cropY + y * cropSize / PHOTO_SIZE;
            for (int x = 0; x < PHOTO_SIZE; x++) {
                int sourceX = cropX + x * cropSize / PHOTO_SIZE;
                pixels[x + y * PHOTO_SIZE] = nativeImage.getPixel(sourceX, sourceY) | 0xff000000;
            }
        }
        applySimplePhotoDither(pixels, PHOTO_SIZE, PHOTO_SIZE);
        return new ImageData(PHOTO_SIZE, PHOTO_SIZE, pixels);
    }

    private static void applySimplePhotoDither(int[] pixels, int width, int height) {
        int levels = 32;
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int red = quantize((pixel >> 16) & 0xff, levels);
            int green = quantize((pixel >> 8) & 0xff, levels);
            int blue = quantize(pixel & 0xff, levels);
            pixels[i] = 0xff000000 | red << 16 | green << 8 | blue;
        }
    }

    private static int quantize(int value, int levels) {
        return Math.max(0, Math.min(255, Math.round(Math.round(value * (levels - 1) / 255.0f) * (255.0f / (levels - 1)))));
    }

    private static int[] readCompactPixels(CompoundTag imageTag, int width, int height) {
        byte[] compressed = imageTag.getByteArray(IMAGE_RGB_COMPRESSED_TAG).orElse(new byte[0]);
        if (width <= 0 || height <= 0 || compressed.length == 0) {
            return new int[0];
        }
        int expectedBytes = width * height * 3;
        byte[] rgb = new byte[expectedBytes];
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            int offset = 0;
            while (offset < rgb.length) {
                int read = inflater.read(rgb, offset, rgb.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset != rgb.length) {
                return new int[0];
            }
        } catch (IOException e) {
            return new int[0];
        }
        int[] pixels = new int[width * height];
        for (int index = 0, offset = 0; index < pixels.length; index++) {
            int r = rgb[offset++] & 0xff;
            int g = rgb[offset++] & 0xff;
            int b = rgb[offset++] & 0xff;
            pixels[index] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
        return pixels;
    }

    private static byte[] compressRgb(int[] pixels) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(32, pixels.length));
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) {
                byte[] row = new byte[Math.min(4096 * 3, pixels.length * 3)];
                int offset = 0;
                while (offset < pixels.length) {
                    int count = Math.min(row.length / 3, pixels.length - offset);
                    int byteIndex = 0;
                    for (int i = 0; i < count; i++) {
                        int pixel = pixels[offset + i];
                        row[byteIndex++] = (byte) ((pixel >> 16) & 0xff);
                        row[byteIndex++] = (byte) ((pixel >> 8) & 0xff);
                        row[byteIndex++] = (byte) (pixel & 0xff);
                    }
                    deflater.write(row, 0, byteIndex);
                    offset += count;
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            byte[] rgb = new byte[pixels.length * 3];
            int offset = 0;
            for (int pixel : pixels) {
                rgb[offset++] = (byte) ((pixel >> 16) & 0xff);
                rgb[offset++] = (byte) ((pixel >> 8) & 0xff);
                rgb[offset++] = (byte) (pixel & 0xff);
            }
            return rgb;
        }
    }

    public record ImageData(int width, int height, int[] pixels, int hash) {
        private static final ImageData EMPTY = new ImageData(0, 0, new int[0], 0);

        public ImageData(int width, int height, int[] pixels) {
            this(width, height, pixels, Arrays.hashCode(pixels));
        }

        public static ImageData empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return pixels.length == 0;
        }
    }
}
