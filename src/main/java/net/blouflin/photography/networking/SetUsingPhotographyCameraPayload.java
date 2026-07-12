package net.blouflin.photography.networking;

import net.blouflin.photography.player.PlayerIsUsingCamera;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record SetUsingPhotographyCameraPayload(Boolean isUsingPhotographyCamera, String handUsingPhotographyCamera, Boolean selfie) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetUsingPhotographyCameraPayload> ID = CustomPacketPayload.createType("photography_set_using_photography_camera");
    public static final StreamCodec<FriendlyByteBuf, SetUsingPhotographyCameraPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeBoolean(value.isUsingPhotographyCamera).writeUtf(value.handUsingPhotographyCamera).writeBoolean(value.selfie),
            buf -> new SetUsingPhotographyCameraPayload(buf.readBoolean(), buf.readUtf(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(ServerPlayer player, Boolean isUsingPhotographyCamera, String handUsingPhotographyCamera, Boolean selfie) {
        player.level().getServer().execute(() -> {

            ((PlayerIsUsingCamera) player).setUsingPhotographyCamera(isUsingPhotographyCamera, handUsingPhotographyCamera, selfie);
            for (ServerPlayer otherPlayer : player.level().getServer().getPlayerList().getPlayers()) {
                GetUsingPhotographyCameraPayload payload = new GetUsingPhotographyCameraPayload(player.getUUID(), isUsingPhotographyCamera, handUsingPhotographyCamera, selfie);
                ServerPlayNetworking.send(otherPlayer,payload);
            }
        });
    }
}
