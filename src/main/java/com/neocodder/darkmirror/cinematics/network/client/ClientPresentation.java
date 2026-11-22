package com.neocodder.darkmirror.cinematics.network.client;

import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.network.ClientHelloPayload;
import com.neocodder.darkmirror.cinematics.network.CameraPayload;
import com.neocodder.darkmirror.cinematics.network.PresentationPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Client-only payload and overlay registration. */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientPresentation {
    private static int fadeTicks;
    private static long fadeStartedAt;
    private static boolean fadingOut;
    private static boolean fadeActive;
    private static String dialogue = "";
    private static long dialogueEndsAt;
    private static boolean cinematicMode;
    private static boolean previousHideGui;
    private static Entity previousCamera;
    private static Marker cinematicCamera;
    private static float lockedYaw;
    private static float lockedPitch;
    private static int cameraMoveTicks;
    private static int cameraMoveDuration;
    private static double cameraStartX;
    private static double cameraStartY;
    private static double cameraStartZ;
    private static double cameraTargetX;
    private static double cameraTargetY;
    private static double cameraTargetZ;
    private static float cameraStartYaw;
    private static float cameraStartPitch;
    private static float cameraTargetYaw;
    private static float cameraTargetPitch;
    private static int followEntityId = -1;
    private static int followTicks;
    private static double followOffsetX;
    private static double followOffsetY;
    private static double followOffsetZ;

    private ClientPresentation() {
    }

    /**
     * Registers runtime client listeners after client startup.
     *
     * @param event physical client setup event
     */
    @SubscribeEvent
    public static void registerClientListeners(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingIn);
            NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
            NeoForge.EVENT_BUS.addListener(ClientEvents::renderOverlay);
            NeoForge.EVENT_BUS.addListener(ClientEvents::clientTick);
            NeoForge.EVENT_BUS.addListener(ClientEvents::onScreenOpening);
            NeoForge.EVENT_BUS.addListener(ClientEvents::onMouseButton);
            NeoForge.EVENT_BUS.addListener(ClientEvents::onMouseScroll);
            NeoForge.EVENT_BUS.addListener(ClientEvents::onInteraction);
        });
    }

    /**
     * Registers the clientbound presentation payload on physical clients only.
     *
     * @param event payload registration event
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(
                PresentationPayload.TYPE,
                PresentationPayload.STREAM_CODEC,
                (payload, context) -> handle(payload));
        registrar.playToClient(
                CameraPayload.TYPE,
                CameraPayload.STREAM_CODEC,
                (payload, context) -> handleCamera(payload));
    }

    private static final class ClientEvents {
        private ClientEvents() {
        }

        private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            if (!NetworkRegistry.hasChannel(event.getConnection(), ConnectionProtocol.PLAY,
                    ClientHelloPayload.TYPE.id())) {
                return;
            }
            int featureMask = ClientFeature.SCREEN_FADE.bit()
                    | ClientFeature.DIALOGUE_OVERLAY.bit()
                    | ClientFeature.CINEMATIC_CAMERA.bit();
            PacketDistributor.sendToServer(new ClientHelloPayload(featureMask));
        }

        private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            stopCinematic(Minecraft.getInstance());
            clearOverlay();
        }

        private static void clientTick(ClientTickEvent.Pre event) {
            if (!cinematicMode) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null || cinematicCamera == null) {
                stopCinematic(minecraft);
                return;
            }
            minecraft.options.hideGui = true;
            lockGameplayKeys(minecraft);
            minecraft.player.setYRot(lockedYaw);
            minecraft.player.setXRot(lockedPitch);
            cinematicCamera.setOldPosAndRot();

            if (followEntityId >= 0) {
                Entity target = minecraft.level.getEntity(followEntityId);
                if (target != null) {
                    cinematicCamera.setPos(target.getX() + followOffsetX,
                            target.getY() + followOffsetY, target.getZ() + followOffsetZ);
                    cinematicCamera.setYRot(target.getYRot());
                    cinematicCamera.setXRot(target.getXRot());
                }
                if (--followTicks <= 0) {
                    followEntityId = -1;
                }
            } else if (cameraMoveDuration > 0) {
                cameraMoveTicks = Math.min(cameraMoveTicks + 1, cameraMoveDuration);
                float progress = (float) cameraMoveTicks / cameraMoveDuration;
                cinematicCamera.setPos(
                        Mth.lerp(progress, cameraStartX, cameraTargetX),
                        Mth.lerp(progress, cameraStartY, cameraTargetY),
                        Mth.lerp(progress, cameraStartZ, cameraTargetZ));
                cinematicCamera.setYRot(Mth.rotLerp(progress, cameraStartYaw, cameraTargetYaw));
                cinematicCamera.setXRot(Mth.lerp(progress, cameraStartPitch, cameraTargetPitch));
                if (cameraMoveTicks >= cameraMoveDuration) {
                    cameraMoveDuration = 0;
                }
            }
        }

        private static void onScreenOpening(ScreenEvent.Opening event) {
            if (cinematicMode && event.getNewScreen() != null) {
                event.setCanceled(true);
            }
        }

        private static void onMouseButton(InputEvent.MouseButton.Pre event) {
            if (cinematicMode) {
                event.setCanceled(true);
            }
        }

        private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            if (cinematicMode) {
                event.setCanceled(true);
            }
        }

        private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
            if (cinematicMode) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }

        private static void renderOverlay(RenderGuiEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            long now = Util.getMillis();
            GuiGraphics graphics = event.getGuiGraphics();
            if (fadeActive) {
                float progress = Math.min(1.0F, (now - fadeStartedAt) / (fadeTicks * 50.0F));
                float alpha = fadingOut ? progress : 1.0F - progress;
                if (!fadingOut && progress >= 1.0F) {
                    fadeActive = false;
                }
                int alphaByte = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255);
                graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight(), alphaByte << 24);
            }
            if (!dialogue.isEmpty()) {
                if (now >= dialogueEndsAt) {
                    dialogue = "";
                } else {
                    int width = minecraft.font.width(dialogue);
                    graphics.drawString(minecraft.font, dialogue,
                            (minecraft.getWindow().getGuiScaledWidth() - width) / 2,
                            minecraft.getWindow().getGuiScaledHeight() - 54, 0xFFFFFFFF, true);
                }
            }
        }
    }

    private static void handleCamera(CameraPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (payload.kind()) {
            case CameraPayload.START -> startCinematic(minecraft);
            case CameraPayload.STOP -> {
                stopCinematic(minecraft);
                clearOverlay();
            }
            case CameraPayload.MOVE -> {
                if (cinematicMode && cinematicCamera != null) {
                    followEntityId = -1;
                    cameraStartX = cinematicCamera.getX();
                    cameraStartY = cinematicCamera.getY();
                    cameraStartZ = cinematicCamera.getZ();
                    cameraStartYaw = cinematicCamera.getYRot();
                    cameraStartPitch = cinematicCamera.getXRot();
                    cameraTargetX = payload.x();
                    cameraTargetY = payload.y();
                    cameraTargetZ = payload.z();
                    cameraTargetYaw = payload.yaw();
                    cameraTargetPitch = payload.pitch();
                    cameraMoveTicks = 0;
                    cameraMoveDuration = payload.durationTicks();
                    if (cameraMoveDuration == 0) {
                        cinematicCamera.setPos(cameraTargetX, cameraTargetY, cameraTargetZ);
                        cinematicCamera.setYRot(cameraTargetYaw);
                        cinematicCamera.setXRot(cameraTargetPitch);
                    }
                }
            }
            case CameraPayload.FOLLOW_ENTITY -> {
                if (cinematicMode) {
                    followEntityId = payload.entityId();
                    followTicks = payload.durationTicks();
                    followOffsetX = payload.offsetX();
                    followOffsetY = payload.offsetY();
                    followOffsetZ = payload.offsetZ();
                    cameraMoveDuration = 0;
                }
            }
            default -> throw new IllegalArgumentException("Unknown camera command: " + payload.kind());
        }
    }

    private static void startCinematic(Minecraft minecraft) {
        if (cinematicMode || minecraft.player == null || minecraft.level == null) {
            return;
        }
        cinematicMode = true;
        previousHideGui = minecraft.options.hideGui;
        previousCamera = minecraft.getCameraEntity();
        lockedYaw = minecraft.player.getYRot();
        lockedPitch = minecraft.player.getXRot();
        cinematicCamera = new Marker(EntityType.MARKER, minecraft.level);
        cinematicCamera.setPos(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ());
        cinematicCamera.setYRot(lockedYaw);
        cinematicCamera.setXRot(lockedPitch);
        cinematicCamera.setOldPosAndRot();
        minecraft.setCameraEntity(cinematicCamera);
        minecraft.options.hideGui = true;
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
    }

    private static void stopCinematic(Minecraft minecraft) {
        if (!cinematicMode) {
            return;
        }
        cinematicMode = false;
        if (minecraft.player != null) {
            minecraft.player.setYRot(lockedYaw);
            minecraft.player.setXRot(lockedPitch);
        }
        if (minecraft.options != null) {
            minecraft.options.hideGui = previousHideGui;
        }
        if (minecraft.player != null) {
            minecraft.setCameraEntity(previousCamera != null ? previousCamera : minecraft.player);
        }
        previousCamera = null;
        cinematicCamera = null;
        cameraMoveTicks = 0;
        cameraMoveDuration = 0;
        followEntityId = -1;
    }

    private static void lockGameplayKeys(Minecraft minecraft) {
        for (KeyMapping key : minecraft.options.keyMappings) {
            key.setDown(false);
        }
    }

    private static void clearOverlay() {
        fadeActive = false;
        dialogue = "";
    }

    private static void handle(PresentationPayload payload) {
        long now = Util.getMillis();
        switch (payload.kind()) {
            case PresentationPayload.FADE -> {
                fadeTicks = Math.max(1, payload.durationTicks());
                fadeStartedAt = now;
                fadingOut = payload.fadeOut();
                fadeActive = true;
            }
            case PresentationPayload.DIALOGUE -> {
                dialogue = payload.text();
                dialogueEndsAt = now + payload.durationTicks() * 50L;
            }
            case PresentationPayload.CLEAR -> clearOverlay();
            default -> throw new IllegalArgumentException("Unknown presentation command: " + payload.kind());
        }
    }
}
