package com.neocodder.darkmirror.cinematics.api.scene;

import com.neocodder.darkmirror.cinematics.api.participant.SceneParticipant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable snapshot of an active scene instance.
 *
 * @param instanceId unique runtime instance ID
 * @param sceneId registered scene definition ID
 * @param state current lifecycle state
 * @param participants current participant UUIDs
 * @param actionIndex zero-based current action index
 * @param message last diagnostic message, if any
 */
public record SceneInstance(
        UUID instanceId,
        ResourceLocation sceneId,
        SceneState state,
        List<SceneParticipant> participants,
        int actionIndex,
        String message) {
    /** Canonical constructor that snapshots participants and validates the action index. */
    public SceneInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(sceneId, "sceneId");
        Objects.requireNonNull(state, "state");
        participants = List.copyOf(participants);
        message = message == null ? "" : message;
        if (actionIndex < 0) {
            throw new IllegalArgumentException("actionIndex cannot be negative");
        }
    }
}
