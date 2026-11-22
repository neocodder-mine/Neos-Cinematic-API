package com.neocodder.darkmirror.cinematics.network;

import com.neocodder.darkmirror.cinematics.DarkMirrorCinematics;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server feature negotiation; the server clamps unknown feature bits.
 *
 * @param featureMask bitwise OR of supported {@code ClientFeature} values
 */
public record ClientHelloPayload(int featureMask) implements CustomPacketPayload {
    public static final Type<ClientHelloPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DarkMirrorCinematics.MOD_ID, "client_hello"));
    /** Wire codec for the client feature mask. */
    public static final StreamCodec<ByteBuf, ClientHelloPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClientHelloPayload::featureMask, ClientHelloPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
