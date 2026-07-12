package net.blouflin.photography.networking;

import net.blouflin.photography.Photography;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record CameraPhysicalSoundPayload(String action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CameraPhysicalSoundPayload> ID = CustomPacketPayload.createType("photography_camera_physical_sound");
    public static final StreamCodec<FriendlyByteBuf, CameraPhysicalSoundPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeUtf(value.action),
            buf -> new CameraPhysicalSoundPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(ServerPlayer player, String action) {
        player.level().getServer().execute(() -> {
            if ("viewfinder_open".equals(action)) {
                PhotographyPhysicalSounds.play(player, Photography.CAMERA_VIEWFINDER_OPEN, 0.35f, 0.9f);
            } else if ("viewfinder_close".equals(action)) {
                PhotographyPhysicalSounds.play(player, Photography.CAMERA_VIEWFINDER_CLOSE, 0.35f, 0.9f);
            } else if ("flash".equals(action)) {
                PhotographyPhysicalSounds.playFlash(player);
            }
        });
    }
}
