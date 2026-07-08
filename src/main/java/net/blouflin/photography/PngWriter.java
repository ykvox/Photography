package net.blouflin.photography;

import net.minecraft.world.level.material.MapColor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class PngWriter {
    private static final OpenOption[] CREATE_OR_REPLACE_OPTIONS = {
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
    };

    private PngWriter() {
    }

    public static void writeArgb(Path outputPath, int[] pixels, int width, int height) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, encodeArgb(pixels, width, height), CREATE_OR_REPLACE_OPTIONS);
    }

    public static byte[] encodeMapColors(byte[] mapColors) throws IOException {
        int size = (int) Math.sqrt(mapColors.length);
        if (size * size != mapColors.length) {
            throw new IOException("Expected square map color data, got " + mapColors.length + " pixels");
        }

        int[] pixels = new int[mapColors.length];
        for (int i = 0; i < mapColors.length; i++) {
            pixels[i] = MapColor.getColorFromPackedId(Byte.toUnsignedInt(mapColors[i]));
        }
        return encodeArgb(pixels, size, size);
    }

    public static byte[] encodeArgb(int[] pixels, int width, int height) throws IOException {
        if (pixels.length != width * height) {
            throw new IOException("Expected " + width * height + " pixels, got " + pixels.length);
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        writeChunk(png, "IHDR", createIhdr(width, height));
        writeChunk(png, "IDAT", deflate(createImageData(pixels, width, height)));
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static byte[] createIhdr(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(13);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeByte(8);
            output.writeByte(6);
            output.writeByte(0);
            output.writeByte(0);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] createImageData(int[] pixels, int width, int height) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream((width * 4 + 1) * height);
        for (int y = 0; y < height; y++) {
            raw.write(0);
            for (int x = 0; x < width; x++) {
                int argb = pixels[x + y * width];
                raw.write((argb >>> 16) & 0xff);
                raw.write((argb >>> 8) & 0xff);
                raw.write(argb & 0xff);
                raw.write((argb >>> 24) & 0xff);
            }
        }
        return raw.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();

        byte[] buffer = new byte[8192];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            output.write(buffer, 0, count);
        }
        deflater.end();
        return output.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream png, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        try (DataOutputStream output = new DataOutputStream(png)) {
            output.writeInt(data.length);
            output.write(typeBytes);
            output.write(data);
            output.writeInt(crc(typeBytes, data));
        }
    }

    private static int crc(byte[] type, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        return (int) crc.getValue();
    }
}
