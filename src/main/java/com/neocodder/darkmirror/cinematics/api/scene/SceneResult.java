package com.neocodder.darkmirror.cinematics.api.scene;

import java.util.Objects;
import java.util.UUID;

/**
 * Terminal result for one running scene instance.
 *
 * @param instanceId unique runtime instance ID
 * @param state terminal lifecycle state
 * @param message completion or failure message
 */
public record SceneResult(UUID instanceId, SceneState state, String message) {
    /** Canonical constructor; only terminal lifecycle states are valid results. */
    public SceneResult {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(state, "state");
        if (!state.terminal()) {
            throw new IllegalArgumentException("A scene result must have a terminal state");
        }
        message = message == null ? "" : message;
    }
}
