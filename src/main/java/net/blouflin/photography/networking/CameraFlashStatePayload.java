package net.blouflin.photography.networking;

import net.blouflin.photography.client.PhotographyFlashDynamicLight;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record CameraFlashStatePayload(UUID playerId, boolean active, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CameraFlashStatePayload> ID = CustomPacketPayload.createType("photography_camera_flash_state");
    public static final StreamCodec<FriendlyByteBuf, CameraFlashStatePayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeUUID(value.playerId).writeBoolean(value.active).writeInt(value.ticks),
            buf -> new CameraFlashStatePayload(buf.readUUID(), buf.readBoolean(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(Minecraft client, UUID playerId, boolean active, int ticks) {
        client.execute(() -> PhotographyFlashDynamicLight.setFlashActiveForPlayer(playerId, active, ticks));
    }
}
