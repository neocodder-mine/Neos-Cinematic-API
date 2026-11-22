package com.neocodder.darkmirror.cinematics.api.scene;

import com.neocodder.darkmirror.cinematics.api.action.SceneAction;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, reusable definition of a sequential cinematic.
 *
 * @param id namespaced scene identifier
 * @param actions immutable ordered action list
 * @param type gameplay-control mode for this scene
 */
public record CinematicScene(ResourceLocation id, List<SceneAction> actions, SceneType type) {
    /**
     * Keeps the original constructor source-compatible and defaults scenes to interactive mode.
     *
     * @param id namespaced scene identifier
     * @param actions immutable ordered action list
     */
    public CinematicScene(ResourceLocation id, List<SceneAction> actions) {
        this(id, actions, SceneType.INTERACTIVE);
    }

    /** Canonical constructor that snapshots and validates the action list. */
    public CinematicScene {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        actions = List.copyOf(actions);
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("A scene must contain at least one action");
        }
        if (type != SceneType.CINEMATIC && actions.stream()
                .anyMatch(action -> action.requiredFeatures().contains(ClientFeature.CINEMATIC_CAMERA))) {
            throw new IllegalArgumentException("Camera actions require a CINEMATIC scene");
        }
    }

    /**
     * Starts a builder for a scene with the supplied namespaced identifier.
     *
     * @param id namespaced scene identifier
     * @return empty scene builder
     */
    public static SceneBuilder builder(ResourceLocation id) {
        return new SceneBuilder(id);
    }
}
