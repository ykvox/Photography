package net.blouflin.photography.networking;

import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record GetUsingPhotographyCameraPayload(UUID player, Boolean isUsingPhotographyCamera, String handUsingPhotographyCamera, Boolean selfie) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GetUsingPhotographyCameraPayload> ID = CustomPacketPayload.createType("photography_get_using_photography_camera");
    public static final StreamCodec<FriendlyByteBuf, GetUsingPhotographyCameraPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeUUID(value.player).writeBoolean(value.isUsingPhotographyCamera).writeUtf(value.handUsingPhotographyCamera).writeBoolean(value.selfie),
            buf -> new GetUsingPhotographyCameraPayload(buf.readUUID(), buf.readBoolean(), buf.readUtf(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(Minecraft client, UUID player, Boolean isUsingPhotographyCamera, String handUsingPhotographyCamera, Boolean selfie) {
        client.execute(() -> {

            if (client.level == null || client.level.getPlayerByUUID(player) == null) {
                return;
            }
            Player remotePlayer = client.level.getPlayerByUUID(player);
            ((PlayerIsUsingCamera) remotePlayer).setUsingPhotographyCamera(isUsingPhotographyCamera, handUsingPhotographyCamera, selfie);
        });
    }
}
