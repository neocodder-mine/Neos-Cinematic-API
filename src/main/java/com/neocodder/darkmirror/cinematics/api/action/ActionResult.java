package com.neocodder.darkmirror.cinematics.api.action;

import java.util.Objects;

/**
 * Result of one action execution tick.
 *
 * @param status execution state after the current tick
 * @param message diagnostic text, required only for {@link Status#FAILED}
 */
public record ActionResult(Status status, String message) {
    private static final ActionResult RUNNING = new ActionResult(Status.RUNNING, "");
    private static final ActionResult COMPLETED = new ActionResult(Status.COMPLETED, "");
    private static final ActionResult FINISH_SCENE = new ActionResult(Status.FINISH_SCENE, "");

    /** Validates the terminal and diagnostic fields of this result. */
    public ActionResult {
        Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
        if (status == Status.FAILED && message.isBlank()) {
            throw new IllegalArgumentException("A failed action must include a reason");
        }
    }

    /**
     * Creates a result that keeps the current action active.
     *
     * @return running action result
     */
    public static ActionResult running() {
        return RUNNING;
    }

    /**
     * Creates a result that advances the sequence to the next action.
     *
     * @return completed action result
     */
    public static ActionResult completed() {
        return COMPLETED;
    }

    /**
     * Creates a result that completes the containing scene.
     *
     * @return scene-finish action result
     */
    public static ActionResult finishScene() {
        return FINISH_SCENE;
    }

    /**
     * Marks this action as failed with a non-empty diagnostic message.
     *
     * @param message useful failure detail
     * @return failed action result
     */
    public static ActionResult failed(String message) {
        return new ActionResult(Status.FAILED, message);
    }

    /** Possible outcomes from one action tick. */
    public enum Status {
        /** The action must be ticked again. */
        RUNNING,
        /** The action completed and the sequence may advance. */
        COMPLETED,
        /** The entire scene must complete immediately. */
        FINISH_SCENE,
        /** The action failed and carries a diagnostic message. */
        FAILED
    }
}
