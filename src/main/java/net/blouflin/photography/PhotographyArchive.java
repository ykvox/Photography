package net.blouflin.photography;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhotographyArchive {
    private static final boolean SERVER_ARCHIVE_ENABLED = Boolean.parseBoolean(
            System.getProperty("photography.serverArchiveEnabled", "true"));
    private static final String SERVER_ARCHIVE_FOLDER = System.getProperty(
            "photography.serverArchiveFolder", "photography");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH-mm");
    private static final OpenOption[] CREATE_NEW_OPTIONS = {
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
    };
    private static final ExecutorService ARCHIVE_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger threadId = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Photography Archive Writer " + threadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private PhotographyArchive() {
    }

    public static void saveAsync(ServerPlayer player, MapItemSavedData mapState) {
        if (!SERVER_ARCHIVE_ENABLED) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        Path archiveDirectory = resolveArchiveDirectory(server);
        String baseFileName = createBaseFileName(player.getGameProfile().name(), LocalDateTime.now());
        byte[] colors = Arrays.copyOf(mapState.colors, mapState.colors.length);
        String playerName = player.getGameProfile().name();

        Photography.LOGGER.info("Starting archival save of photo for {} in {}", playerName, archiveDirectory);
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(archiveDirectory);
                byte[] png = PngWriter.encodeMapColors(colors);
                Path outputPath = writeWithUniqueName(archiveDirectory, baseFileName, png);
                Photography.LOGGER.info("Saved archival photo for {} to {}", playerName, outputPath);
            } catch (Exception e) {
                Photography.LOGGER.error("Failed to save archival photo for {} in {}", playerName, archiveDirectory, e);
            }
        }, ARCHIVE_EXECUTOR);
    }

    private static Path resolveArchiveDirectory(MinecraftServer server) {
        Path configuredPath = Path.of(SERVER_ARCHIVE_FOLDER);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        return server.getServerDirectory().resolve(configuredPath).normalize();
    }

    static String createBaseFileName(String playerName, LocalDateTime timestamp) {
        return sanitizeFilePart(playerName) + "_" + formatCompactDate(timestamp) + "_" + TIME_FORMAT.format(timestamp);
    }

    static String formatCompactDate(LocalDateTime timestamp) {
        int year = timestamp.getYear() % 100;
        return Integer.toString(year) + timestamp.getMonthValue() + timestamp.getDayOfMonth();
    }

    static String sanitizeFilePart(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "player" : sanitized;
    }

    static Path writeWithUniqueName(Path directory, String baseFileName, byte[] data) throws IOException {
        for (int suffix = 0; suffix < Integer.MAX_VALUE; suffix++) {
            String suffixText = suffix == 0 ? "" : "(" + suffix + ")";
            Path outputPath = directory.resolve(baseFileName + suffixText + ".png");
            try {
                Files.write(outputPath, data, CREATE_NEW_OPTIONS);
                return outputPath;
            } catch (FileAlreadyExistsException ignored) {
            }
        }

        throw new IOException("Could not find an available archive filename for " + baseFileName);
    }
}
