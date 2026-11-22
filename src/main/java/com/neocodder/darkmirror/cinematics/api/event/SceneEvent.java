package com.neocodder.darkmirror.cinematics.api.event;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Fired on the logical server thread for scene lifecycle and participant changes. */
public final class SceneEvent extends Event {
    private final Type type;
    private final UUID instanceId;
    private final ResourceLocation sceneId;
    private final int actionIndex;
    private final UUID playerId;
    private final String message;

    /**
     * Creates an event emitted by the internal server scene lifecycle.
     *
     * @param type lifecycle transition
     * @param instanceId runtime scene instance UUID
     * @param sceneId registered scene ID
     * @param actionIndex zero-based action index, or -1
     * @param playerId affected participant UUID, or null
     * @param message associated diagnostic text
     */
    public SceneEvent(Type type, UUID instanceId, ResourceLocation sceneId,
                      int actionIndex, UUID playerId, String message) {
        this.type = Objects.requireNonNull(type, "type");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
        this.actionIndex = actionIndex;
        this.playerId = playerId;
        this.message = message == null ? "" : message;
    }

    /** {@return the lifecycle transition represented by this event} */
    public Type type() {
        return type;
    }

    /** {@return the UUID of this scene instance} */
    public UUID instanceId() {
        return instanceId;
    }

    /** {@return the registered scene ID} */
    public ResourceLocation sceneId() {
        return sceneId;
    }

    /** {@return the zero-based action index or -1 for a scene-level event} */
    public int actionIndex() {
        return actionIndex;
    }

    /** {@return the affected participant UUID or {@code null} if none applies} */
    public UUID playerId() {
        return playerId;
    }

    /** {@return the diagnostic or cancellation message, or an empty string} */
    public String message() {
        return message;
    }

    /** Lifecycle transitions observable by addon event listeners. */
    public enum Type {
        /** The scene instance entered the running state. */
        STARTED,
        /** An action began. */
        ACTION_STARTED,
        /** An action completed successfully. */
        ACTION_COMPLETED,
        /** An action failed, including an optional action that was skipped. */
        ACTION_FAILED,
        /** All scene actions completed normally. */
        COMPLETED,
        /** The scene was stopped or lost its final participant. */
        CANCELLED,
        /** A required action or capability caused the scene to fail. */
        FAILED,
        /** An online player joined an active instance. */
        PLAYER_JOINED,
        /** A player left or disconnected from an active instance. */
        PLAYER_LEFT
    }
}
