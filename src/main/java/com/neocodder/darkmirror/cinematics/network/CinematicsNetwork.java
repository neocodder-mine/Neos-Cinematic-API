package com.neocodder.darkmirror.cinematics.network;

import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.core.CapabilityTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Internal common-side networking helpers; no client rendering classes are referenced here. */
public final class CinematicsNetwork {
    private CinematicsNetwork() {
    }

    /**
     * Registers the serverbound capability negotiation payload.
     *
     * @param event NeoForge payload registration event
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(
                ClientHelloPayload.TYPE,
                ClientHelloPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    int features = payload.featureMask();
                    if (!NetworkRegistry.hasChannel(player.connection, PresentationPayload.TYPE.id())) {
                        features &= ~(ClientFeature.SCREEN_FADE.bit() | ClientFeature.DIALOGUE_OVERLAY.bit());
                    }
                    if (!NetworkRegistry.hasChannel(player.connection, CameraPayload.TYPE.id())) {
                        features &= ~ClientFeature.CINEMATIC_CAMERA.bit();
                    }
                    CapabilityTracker.set(player.getServer(), player.getUUID(), features);
                });
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            registrar.playToClient(
                    PresentationPayload.TYPE,
                    PresentationPayload.STREAM_CODEC,
                    (payload, context) -> {
                    });
            registrar.playToClient(
                    CameraPayload.TYPE,
                    CameraPayload.STREAM_CODEC,
                    (payload, context) -> {
                    });
        }
    }

    /** Sends camera setup or cleanup to one participant. */
    public static void sendCameraMode(ServerPlayer player, boolean active) {
        PacketDistributor.sendToPlayer(player,
                emptyCameraPayload(active ? CameraPayload.START : CameraPayload.STOP));
    }

    /** Sends a smooth camera movement to one participant. */
    public static void sendCameraMove(ServerPlayer player, double x, double y, double z,
                                      float yaw, float pitch, int durationTicks) {
        PacketDistributor.sendToPlayer(player, new CameraPayload(CameraPayload.MOVE, durationTicks,
                x, y, z, yaw, pitch, 0, 0.0, 0.0, 0.0));
    }

    /** Sends an entity-follow instruction to one participant. */
    public static void sendCameraFollow(ServerPlayer player, int entityId, double offsetX, double offsetY,
                                        double offsetZ, int durationTicks) {
        PacketDistributor.sendToPlayer(player, new CameraPayload(CameraPayload.FOLLOW_ENTITY, durationTicks,
                0.0, 0.0, 0.0, 0.0F, 0.0F, entityId, offsetX, offsetY, offsetZ));
    }

    private static CameraPayload emptyCameraPayload(int kind) {
        return new CameraPayload(kind, 0, 0.0, 0.0, 0.0, 0.0F, 0.0F, 0, 0.0, 0.0, 0.0);
    }

    /**
     * Sends a screen fade only to the addressed player.
     *
     * @param player destination player
     * @param fadeOut true to fade to opaque, false to reveal
     * @param durationTicks fade duration
     */
    public static void sendFade(ServerPlayer player, boolean fadeOut, int durationTicks) {
        PacketDistributor.sendToPlayer(player,
                new PresentationPayload(PresentationPayload.FADE, "", durationTicks, fadeOut));
    }

    /**
     * Sends dialogue overlay data to the addressed player.
     *
     * @param player destination player
     * @param text bounded dialogue text
     * @param durationTicks display duration
     */
    public static void sendDialogue(ServerPlayer player, String text, int durationTicks) {
        PacketDistributor.sendToPlayer(player,
                new PresentationPayload(PresentationPayload.DIALOGUE, text, durationTicks, false));
    }

    /**
     * Clears transient client presentation state after cancellation or failure.
     *
     * @param player destination player with negotiated presentation support
     */
    public static void clearPresentation(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new PresentationPayload(PresentationPayload.CLEAR, "", 0, false));
    }
}
