package net.blouflin.photography.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PhotographyAlbumOpenPayload(String hand) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PhotographyAlbumOpenPayload> ID =
            CustomPacketPayload.createType("photography_album_open");
    public static final StreamCodec<FriendlyByteBuf, PhotographyAlbumOpenPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeUtf(value.hand),
            buf -> new PhotographyAlbumOpenPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
