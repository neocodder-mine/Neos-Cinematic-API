package com.neocodder.darkmirror.cinematics.core;

import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.api.action.ActionExecution;
import com.neocodder.darkmirror.cinematics.api.action.ActionResult;
import com.neocodder.darkmirror.cinematics.api.action.SceneAction;
import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import com.neocodder.darkmirror.cinematics.api.context.SceneActorRegistry;
import com.neocodder.darkmirror.cinematics.api.participant.SceneParticipant;
import com.neocodder.darkmirror.cinematics.api.scene.CinematicScene;
import com.neocodder.darkmirror.cinematics.api.scene.SceneInstance;
import com.neocodder.darkmirror.cinematics.api.scene.SceneResult;
import com.neocodder.darkmirror.cinematics.api.scene.SceneState;
import com.neocodder.darkmirror.cinematics.api.scene.SceneType;
import com.neocodder.darkmirror.cinematics.network.CinematicsNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.neocodder.darkmirror.cinematics.api.event.SceneEvent;

final class RunningScene {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_INSTANT_ACTIONS_PER_TICK = 64;

    private final UUID instanceId = UUID.randomUUID();
    private final CinematicScene definition;
    private final LinkedHashMap<UUID, SceneParticipant> participants = new LinkedHashMap<>();
    private final SceneActorRegistry actors = new SceneActorRegistry();
    private SceneState state = SceneState.CREATED;
    private int actionIndex;
    private ActionExecution execution;
    private String message = "";

    RunningScene(CinematicScene definition, List<UUID> participantIds) {
        this.definition = definition;
        participantIds.forEach(id -> participants.put(id, new SceneParticipant(id)));
    }

    UUID instanceId() {
        return instanceId;
    }

    boolean hasParticipant(UUID playerId) {
        return participants.containsKey(playerId);
    }

    boolean cinematic() {
        return definition.type() == SceneType.CINEMATIC;
    }

    boolean terminal() {
        return state.terminal();
    }

    List<UUID> participantIds() {
        return List.copyOf(participants.keySet());
    }

    int participantCount() {
        return participants.size();
    }

    boolean addParticipant(MinecraftServer server, UUID playerId) {
        if (state != SceneState.RUNNING || participants.containsKey(playerId)
                || server.getPlayerList().getPlayer(playerId) == null) {
            return false;
        }
        if (cinematic() && !CapabilityTracker.supports(server, playerId, ClientFeature.CINEMATIC_CAMERA)) {
            return false;
        }
        participants.put(playerId, new SceneParticipant(playerId));
        post(SceneEvent.Type.PLAYER_JOINED, -1, playerId, "");
        return true;
    }

    boolean removeParticipant(MinecraftServer server, UUID playerId) {
        if (terminal() || participants.remove(playerId) == null) {
            return false;
        }
        post(SceneEvent.Type.PLAYER_LEFT, -1, playerId, "");
        if (participants.isEmpty()) {
            terminate(server, SceneState.CANCELLED, "All participants left the scene");
        }
        return true;
    }

    void start(MinecraftServer server) {
        state = SceneState.RUNNING;
        post(SceneEvent.Type.STARTED, -1, null, "");
        tick(server);
    }

    void tick(MinecraftServer server) {
        if (state != SceneState.RUNNING) {
            return;
        }
        for (UUID playerId : new ArrayList<>(participants.keySet())) {
            if (server.getPlayerList().getPlayer(playerId) == null) {
                removeParticipant(server, playerId);
            }
        }
        if (state != SceneState.RUNNING) {
            return;
        }
        if (participants.isEmpty()) {
            terminate(server, SceneState.CANCELLED, "All participants disconnected");
            return;
        }

        int budget = MAX_INSTANT_ACTIONS_PER_TICK;
        while (state == SceneState.RUNNING && budget-- > 0) {
            if (actionIndex >= definition.actions().size()) {
                terminate(server, SceneState.COMPLETED, "");
                return;
            }
            SceneAction action = definition.actions().get(actionIndex);
            if (execution == null) {
                if (unsupportedRequiredFeature(server, action)) {
                    if (action.optional()) {
                        LOGGER.warn("Skipping optional action {} in scene {} because a participant lacks a required client feature",
                                actionIndex, definition.id());
                        actionIndex++;
                        continue;
                    }
                    terminate(server, SceneState.FAILED,
                            "Participant does not support required features for action " + actionIndex);
                    return;
                }
                try {
                    ActionExecution created = Objects.requireNonNull(action.createExecution(),
                            "Action returned a null execution");
                    if (terminal()) {
                        cancelExecution(server, created);
                        return;
                    }
                    execution = created;
                    post(SceneEvent.Type.ACTION_STARTED, actionIndex, null, "");
                } catch (RuntimeException exception) {
                    failAction(server, action, "Could not create action execution", exception);
                    continue;
                }
            }
            if (terminal()) {
                return;
            }

            ActionResult result;
            try {
                result = Objects.requireNonNull(execution.tick(context(server)), "Action returned a null result");
            } catch (RuntimeException exception) {
                failAction(server, action, "Action threw an exception", exception);
                continue;
            }
            if (terminal()) {
                return;
            }

            switch (result.status()) {
                case RUNNING -> {
                    return;
                }
                case COMPLETED -> {
                    execution = null;
                    post(SceneEvent.Type.ACTION_COMPLETED, actionIndex++, null, "");
                }
                case FINISH_SCENE -> terminate(server, SceneState.COMPLETED, "");
                case FAILED -> failAction(server, action, result.message(), null);
            }
        }
    }

    void stop(MinecraftServer server, String reason) {
        terminate(server, SceneState.CANCELLED, reason == null ? "Cancelled by caller" : reason);
    }

    SceneInstance snapshot() {
        return new SceneInstance(instanceId, definition.id(), state,
                new ArrayList<>(participants.values()), actionIndex, message);
    }

    SceneResult result() {
        return new SceneResult(instanceId, state, message);
    }

    private boolean unsupportedRequiredFeature(MinecraftServer server, SceneAction action) {
        for (ClientFeature feature : action.requiredFeatures()) {
            for (UUID playerId : participants.keySet()) {
                if (!CapabilityTracker.supports(server, playerId, feature)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void failAction(MinecraftServer server, SceneAction action, String reason, RuntimeException exception) {
        if (exception != null) {
            LOGGER.error("Action {} in scene {} failed", actionIndex, definition.id(), exception);
        } else {
            LOGGER.error("Action {} in scene {} failed: {}", actionIndex, definition.id(), reason);
        }
        if (terminal()) {
            return;
        }
        String failure = "Action " + actionIndex + ": " + reason;
        post(SceneEvent.Type.ACTION_FAILED, actionIndex, null, failure);
        if (terminal()) {
            return;
        }
        if (action.optional()) {
            cancelExecution(server, takeExecution());
            if (!terminal()) {
                actionIndex++;
            }
            return;
        }
        terminate(server, SceneState.FAILED, failure);
    }

    private void terminate(MinecraftServer server, SceneState terminalState, String terminalMessage) {
        if (terminal()) {
            return;
        }
        state = terminalState;
        message = terminalMessage;
        cancelExecution(server, takeExecution());
        actors.cleanup();
        if (terminalState == SceneState.CANCELLED || terminalState == SceneState.FAILED) {
            for (UUID playerId : participants.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && (CapabilityTracker.supports(server, playerId, ClientFeature.SCREEN_FADE)
                        || CapabilityTracker.supports(server, playerId, ClientFeature.DIALOGUE_OVERLAY))) {
                    CinematicsNetwork.clearPresentation(player);
                }
            }
        }
        SceneEvent.Type eventType = switch (terminalState) {
            case COMPLETED -> SceneEvent.Type.COMPLETED;
            case CANCELLED -> SceneEvent.Type.CANCELLED;
            case FAILED -> SceneEvent.Type.FAILED;
            default -> throw new IllegalArgumentException("Not a terminal scene state: " + terminalState);
        };
        post(eventType, -1, null, terminalMessage);
    }

    private ActionExecution takeExecution() {
        ActionExecution active = execution;
        execution = null;
        return active;
    }

    private void cancelExecution(MinecraftServer server, ActionExecution active) {
        if (active == null) {
            return;
        }
        try {
            active.cancel(context(server));
        } catch (RuntimeException exception) {
            LOGGER.error("Action cancellation failed in scene {}", definition.id(), exception);
        }
    }

    private SceneContext context(MinecraftServer server) {
        return new SceneContext(server, definition.id(), List.copyOf(participants.values()), actors);
    }

    private void post(SceneEvent.Type type, int actionIndex, UUID playerId, String eventMessage) {
        try {
            NeoForge.EVENT_BUS.post(new SceneEvent(type, instanceId, definition.id(), actionIndex, playerId, eventMessage));
        } catch (RuntimeException exception) {
            LOGGER.error("Listener failed for {} in scene {}", type, definition.id(), exception);
        }
    }
}
