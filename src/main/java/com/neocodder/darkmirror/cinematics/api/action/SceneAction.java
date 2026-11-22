package com.neocodder.darkmirror.cinematics.api.action;

import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import java.util.Set;

/**
 * An immutable action definition that runs on the logical server thread.
 * Create mutable state in {@link #createExecution()} so a scene definition can
 * safely be reused by concurrent instances.
 */
public interface SceneAction {
    /**
     * Creates state isolated to one scene instance.
     *
     * @return a fresh execution, never {@code null}
     */
    ActionExecution createExecution();

    /**
     * Optional actions that fail are logged and skipped rather than failing the scene.
     *
     * @return whether this action may be skipped on failure
     */
    default boolean optional() {
        return false;
    }

    /**
     * Client capabilities required by this action. Empty means server-only or
     * vanilla-compatible. Every participant must support all returned features.
     *
     * @return an immutable or stable set of requirements
     */
    default Set<ClientFeature> requiredFeatures() {
        return Set.of();
    }
}
