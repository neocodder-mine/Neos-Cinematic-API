package com.neocodder.darkmirror.cinematics.network;

import com.neocodder.darkmirror.cinematics.DarkMirrorCinematics;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client visual instruction containing presentation data only.
 *
 * @param kind one of the presentation command constants
 * @param text bounded presentation string
 * @param durationTicks effect duration in game ticks
 * @param fadeOut true for the fade-to-opaque command
 */
public record PresentationPayload(int kind, String text, int durationTicks, boolean fadeOut)
        implements CustomPacketPayload {
    public static final int FADE = 0;
    public static final int DIALOGUE = 1;
    public static final int CLEAR = 2;
    public static final Type<PresentationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DarkMirrorCinematics.MOD_ID, "presentation"));
    /** Wire codec for bounded server presentation instructions. */
    public static final StreamCodec<ByteBuf, PresentationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PresentationPayload::kind,
            ByteBufCodecs.STRING_UTF8, PresentationPayload::text,
            ByteBufCodecs.VAR_INT, PresentationPayload::durationTicks,
            ByteBufCodecs.BOOL, PresentationPayload::fadeOut,
            PresentationPayload::new);

    /** Validates the command and enforces the wire payload's size and duration limits. */
    public PresentationPayload {
        if (kind < FADE || kind > CLEAR) {
            throw new IllegalArgumentException("Invalid presentation command: " + kind);
        }
        text = text == null ? "" : text;
        if (text.length() > 2048) {
            throw new IllegalArgumentException("Presentation text exceeds 2048 characters");
        }
        if (durationTicks < 0 || durationTicks > 20 * 60 * 10) {
            throw new IllegalArgumentException("Presentation duration is outside the supported range");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
