package com.neocodder.darkmirror.cinematics.network;

import com.neocodder.darkmirror.cinematics.DarkMirrorCinematics;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server instruction for the isolated client-side cinematic camera. */
public record CameraPayload(
        int kind,
        int durationTicks,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        int entityId,
        double offsetX,
        double offsetY,
        double offsetZ) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int STOP = 1;
    public static final int MOVE = 2;
    public static final int FOLLOW_ENTITY = 3;
    public static final Type<CameraPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DarkMirrorCinematics.MOD_ID, "camera"));
    public static final StreamCodec<ByteBuf, CameraPayload> STREAM_CODEC = StreamCodec.of(
            CameraPayload::encode,
            CameraPayload::decode);

    public CameraPayload {
        if (kind < START || kind > FOLLOW_ENTITY) {
            throw new IllegalArgumentException("Invalid camera command: " + kind);
        }
        if (durationTicks < 0 || durationTicks > 20 * 60 * 10) {
            throw new IllegalArgumentException("Camera duration is outside the supported range");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || !Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(offsetZ)) {
            throw new IllegalArgumentException("Camera coordinates and rotation must be finite");
        }
        if (entityId < 0) {
            throw new IllegalArgumentException("Entity ID cannot be negative");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(ByteBuf buffer, CameraPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.kind);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.durationTicks);
        ByteBufCodecs.DOUBLE.encode(buffer, payload.x);
        ByteBufCodecs.DOUBLE.encode(buffer, payload.y);
        ByteBufCodecs.DOUBLE.encode(buffer, payload.z);
        ByteBufCodecs.FLOAT.encode(buffer, payload.yaw);
        ByteBufCodecs.FLOAT.encode(buffer, payload.pitch);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.entityId);
        ByteBufCodecs.DOUBLE.encode(buffer, payload.offsetX);
        ByteBufCodecs.DOUBLE.encode(buffer, payload.offsetY);
        ByteBufCodecs.DOUBLE.encode(buffer, payload.offsetZ);
    }

    private static CameraPayload decode(ByteBuf buffer) {
        return new CameraPayload(
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer),
                ByteBufCodecs.DOUBLE.decode(buffer));
    }
}
