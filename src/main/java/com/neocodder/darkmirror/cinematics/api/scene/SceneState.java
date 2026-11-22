package com.neocodder.darkmirror.cinematics.api.scene;

/** Lifecycle states exposed by active scene snapshots and terminal results. */
public enum SceneState {
    /** Definition is prepared but has not started running. */
    CREATED,
    /** Actions are being evaluated on server ticks. */
    RUNNING,
    /** The action sequence completed normally. */
    COMPLETED,
    /** The instance was stopped or lost all participants. */
    CANCELLED,
    /** A required action or required capability failed. */
    FAILED;

    /**
     * Reports whether this state cannot transition back to a running state.
     *
     * @return true when the state is terminal
     */
    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
