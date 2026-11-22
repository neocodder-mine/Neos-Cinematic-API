package com.neocodder.darkmirror.cinematics.api.participant;

import java.util.Objects;
import java.util.UUID;

/**
 * A participant is represented by identity only; player objects are resolved on demand.
 *
 * @param playerId UUID of the participant
 */
public record SceneParticipant(UUID playerId) {
    /** Rejects a participant without a stable player identity. */
    public SceneParticipant {
        Objects.requireNonNull(playerId, "playerId");
    }
}
