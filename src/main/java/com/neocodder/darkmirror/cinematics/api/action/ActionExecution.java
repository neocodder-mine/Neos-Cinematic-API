package com.neocodder.darkmirror.cinematics.api.action;

import com.neocodder.darkmirror.cinematics.api.context.SceneContext;

/** Mutable, per-scene execution state for an immutable action definition. */
@FunctionalInterface
public interface ActionExecution {
    /**
     * Advances this execution once on the logical server thread.
     *
     * @param context live context for this scene tick; do not retain it
     * @return the state transition requested by this tick
     */
    ActionResult tick(SceneContext context);

    /**
     * Called once when the containing scene is cancelled or fails.
     *
     * @param context live context for cleanup
     */
    default void cancel(SceneContext context) {
    }
}
